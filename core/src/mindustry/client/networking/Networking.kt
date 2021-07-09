package mindustry.client.networking

import kotlinx.coroutines.*
import mindustry.client.communication.CommunicationSystem
import mindustry.client.networking.Networking.ConnectionState.*
import mindustry.client.utils.*
import kotlin.Pair

/**
 * finishme move to this?
 * Represents a networking host.  Has 65535 ports and an address.  Uses [provider] to send and receive bytes.
 * [connect] opens a connection to another host.
 */
class Networking(val provider: NetworkingBackend, val address: Address) {
    val scope = CoroutineScope(Dispatchers.Default)

    val openConnections = mutableListOf<Connection>()

    operator fun get(port: Short) = openConnections.find { it.srcPort == port }

    enum class PacketType {
        CONNECT, ACK, DATA, CLOSE;

        val num: Byte = ordinal.toByte()
    }

    data class Address(val num: Long) {
        fun bytes() = num.toBytes()
    }

    interface NetworkingBackend {
        val MAX_LENGTH: Int
        val RATE: Float

        suspend fun send(bytes: ByteArray)

        // Blocks until a set of bytes are gotten
        suspend fun read(): ByteArray
    }

    class Header(
        val sourceAddress: Address,
        val destinationAddress: Address,
        val sourcePort: Short,
        val destinationPort: Short,
        val type: Byte
    ) {
        constructor(inp: ByteArray) : this(
            Address(inp.long()),
            Address(inp.long(8)),
            inp.short(16),
            inp.short(18),
            inp[20]
        )

        val serialized =
            sourceAddress.bytes() + destinationAddress.bytes() + sourcePort.toBytes() + destinationPort.toBytes() + type

        override fun toString(): String {
            return "Header(sourceAddress=$sourceAddress, destinationAddress=$destinationAddress, sourcePort=$sourcePort, destinationPort=$destinationPort, type=$type)"
        }
    }

    enum class ConnectionState {
        WAITING_FOR_ACK,  // Sent connect packet, waiting for ack
        READY
    }

    suspend fun connect(dstAddress: Address, dstPort: Short, srcPort: Short): Connection {
        provider.send(Header(address, dstAddress, srcPort, dstPort, PacketType.CONNECT.num).serialized)
        val con = Connection(dstAddress, srcPort, dstPort, WAITING_FOR_ACK)
        waitUntil { header, _ ->
            header.destinationAddress == address &&
                    header.sourceAddress == dstAddress &&
                    header.sourcePort == dstPort &&
                    header.destinationPort == srcPort &&
                    header.type == PacketType.ACK.num
        }
        con.state = READY
        openConnections.add(con)
        return con
    }

    private val waiting: MutableList<Pair<(header: Header, data: ByteArray) -> Boolean, Job>> = mutableListOf()

    private suspend fun waitUntil(lambda: (header: Header, data: ByteArray) -> Boolean) {
        coroutineScope {
            val job = launch { delay(Long.MAX_VALUE) }
            waiting.add(Pair(lambda, job))
            job.join()
        }
    }

    private suspend fun gotBytes(bytes: ByteArray) {
        val header = Header(bytes[0 until 22])
        val data = if (bytes.size >= 22) bytes[21 until bytes.size] else byteArrayOf()
        waiting.filter { it.first(header, data) }.map { waiting.remove(it); it.second.cancel() }

        if (header.destinationAddress != address) return

        when (header.type) {
            PacketType.CONNECT.num -> {
                val con = Connection(header.sourceAddress, header.destinationPort, header.sourcePort, READY)
                provider.send(
                    Header(
                        address,
                        header.sourceAddress,
                        header.destinationPort,
                        header.sourcePort,
                        PacketType.ACK.num
                    ).serialized
                )
                con.state = READY
                openConnections.add(con)
            }
            // ack is handled in connect()
            PacketType.DATA.num -> {
                openConnections.find { it.destination == header.sourceAddress && it.dstPort == header.sourcePort && it.srcPort == header.destinationPort }?.gotBytes(data)
            }
            PacketType.CLOSE.num -> {
                openConnections.remove(openConnections.find { it.destination == header.sourceAddress && it.dstPort == header.sourcePort && it.srcPort == header.destinationPort }.apply { this?.dispose() })
            }
        }
    }

    //todo disposal
    init {
        scope.launch {
            while (true) {
                gotBytes(provider.read())
            }
        }
    }

    inner class Connection(val destination: Address, val srcPort: Short, val dstPort: Short, var state: ConnectionState) {
        val listeners = mutableListOf<(ByteArray) -> Unit>()
        var disposed: Boolean = false
        val communicationSystem: CommunicationSystem = NetworkedCommunicationSystem(this, destination.num.toInt())

        fun dispose() {
            disposed = true
        }

        fun gotBytes(bytes: ByteArray) {
            if (disposed) return
            listeners.forEach { it(bytes) }
        }

        suspend fun send(bytes: ByteArray) {
            if (disposed) return
            provider.send(Header(address, destination, srcPort, dstPort, PacketType.DATA.num).serialized + bytes + 0)  // extra byte is needed for whatever reason
        }

        suspend fun close() {
            if (disposed) return
            provider.send(Header(address, destination, srcPort, dstPort, PacketType.CLOSE.num).serialized)
            openConnections.remove(this)
            dispose()
        }
    }

    private inner class NetworkedCommunicationSystem(val connection: Connection, override val id: Int) : CommunicationSystem() {
        override val listeners = mutableListOf<(input: ByteArray, sender: Int) -> Unit>()
        override val MAX_LENGTH = provider.MAX_LENGTH
        override val RATE = provider.RATE

        init {
            connection.listeners.add { array ->
                listeners.forEach { it(array, connection.destination.num.toInt()) }
            }
        }

        override fun send(bytes: ByteArray) {
            runBlocking {
                connection.send(bytes)
            }
        }
    }
}
