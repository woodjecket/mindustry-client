package mindustry.client.networking

import kotlinx.coroutines.*
import mindustry.client.networking.Networking.ConnectionState.*
import mindustry.client.utils.toBytes
import mindustry.client.utils.int
import mindustry.client.utils.get
import mindustry.client.utils.long

class Networking(val address: Address, val provider: NetworkingBackend) {
    var state: ConnectionState = LISTENING
        set(value) {
            stateListeners.forEach { it(field, value) }
            field = value
        }
    val stateListeners = mutableListOf<(previous: ConnectionState, new: ConnectionState) -> Unit>()

    init {
        scope.launch {

        }
    }

    companion object {
        val scope = CoroutineScope(Dispatchers.Default)

        const val CONNECT_TYPE = 0.toByte()
        const val ACK_TYPE = 1.toByte()
        const val DATA = 2.toByte()
    }

    data class Address(val num: Long) {
        fun bytes() = num.toBytes()
    }

    interface NetworkingBackend {
        fun send(bytes: ByteArray)

        fun read(): ByteArray
    }

    class Header(val sourceAddress: Address, val destinationAddress: Address, val sourcePort: Int, val destinationPort: Int, val type: Byte) {
        constructor(inp: ByteArray) : this(Address(inp.long()), Address(inp.long(8)), inp.int(16), inp.int(20), inp[25])

        val serialized = sourceAddress.bytes() + destinationAddress.bytes() + sourcePort.toBytes() + destinationPort.toBytes() + type
    }

    suspend fun connect(address: Address, dstPort: Int, srcPort: Int) {
        provider.send(Header(address, address, srcPort, dstPort, CONNECT_TYPE).serialized)
        state = WAITING_FOR_ACK
        waitUntil { header, _ ->
            header.destinationAddress == this.address && header.sourceAddress == address && header.sourcePort == dstPort && header.destinationPort == srcPort && header.type == ACK_TYPE
        }
        state = READY
    }

    private val waiting = mutableListOf<(Header, ByteArray) -> Boolean>()

    private suspend fun waitUntil(predicate: (Header, ByteArray) -> Boolean) {
        waiting.add(predicate)
        delay(Long.MAX_VALUE)
    }

    fun gotBytes(bytes: ByteArray) {
        val header = Header(bytes[0 until 25])
        val data = bytes[25 until bytes.size]
        waiting.removeAll { it(header, data) }

        if (state == LISTENING && header.destinationAddress == address) {
            provider.send(Header(address, header.sourceAddress, header.destinationPort, header.sourcePort, ACK_TYPE).serialized)
            state = READY
        }
    }

    enum class ConnectionState {
        LISTENING,  // Listening for incoming connections
        WAITING_FOR_ACK,  // Sent connect packet, waiting for ack
        READY,
        CLOSING,
        CLOSED
    }
}
