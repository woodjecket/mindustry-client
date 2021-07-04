package client

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mindustry.client.communication.DummyTransmission
import mindustry.client.communication.Packets
import mindustry.client.communication.TunneledCommunicationSystem
import mindustry.client.networking.Networking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import java.io.InputStream
import java.io.OutputStream
import java.util.NoSuchElementException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.experimental.inv
import kotlin.random.Random

class TunneledPacketTests {

    class DummyNetworkingBackend(private val pool: MutableList<DummyNetworkingBackend>, override val RATE: Float, override val MAX_LENGTH: Int) : Networking.NetworkingBackend {
        private var gotten = mutableListOf<ByteArray>()

        init {
            pool.add(this)
        }

        override suspend fun send(bytes: ByteArray) {
            pool.minus(this).forEach { it.gotten.add(bytes) }
        }

        override suspend fun read(): ByteArray {
            while (gotten.isEmpty()) {
                delay(10)
            }
            return gotten.removeFirst()
        }
    }

    private class BufferStreamPair {
        private val buffer = LinkedBlockingQueue<Byte>()
        val input = BufInpStr(this)
        val output = BufOutStr(this)

        class BufInpStr(val pair: BufferStreamPair) : InputStream() {
            override fun read(): Int {
                return try { pair.buffer.remove().toInt() + 128 } catch (e: NoSuchElementException) { -1 }
            }

            override fun readNBytes(len: Int): ByteArray {
                return pair.buffer.take(len).toByteArray()
            }

            override fun available(): Int {
                return pair.buffer.size
            }

            fun readAvailable(): ByteArray {
                val list = mutableListOf<Byte>()
                pair.buffer.drainTo(list)
                return list.toByteArray()
            }
        }

        class BufOutStr(val pair: BufferStreamPair) : OutputStream() {
            override fun write(b: Int) {
                pair.buffer.add(b.toByte())
            }

            override fun write(b: ByteArray) {
                pair.buffer.addAll(b.toList())
            }
        }
    }

    @RepeatedTest(32)
    fun test() {

        val client1toclient2 = BufferStreamPair()
        val client2toclient1 = BufferStreamPair()

        val client1 = TunneledCommunicationSystem(0f, client2toclient1.input, client1toclient2.output, 1, 2)
        val client2 = TunneledCommunicationSystem(0f, client1toclient2.input, client2toclient1.output, 2, 1)

        val gotten = mutableListOf<ByteArray>()

        client1.addListener { input, _ -> gotten.add(input) }

        val array = Random.nextBytes(0b01111110)  // the maximum length
        client2.send(array)

        Thread.sleep(100)

        Assertions.assertArrayEquals(array, gotten.first())
    }

    @Test
    fun testnetworking() {
        val pool = mutableListOf<DummyNetworkingBackend>()

        val client1backend = DummyNetworkingBackend(pool, 0f, 1024)
        val client2backend = DummyNetworkingBackend(pool, 0f, 1024)

        val client1addr = Networking.Address(1)
        val client2addr = Networking.Address(2)

        val client1 = Networking(client1backend, client1addr)
        val client2 = Networking(client2backend, client2addr)

        runBlocking { client1.connect(client2addr, 12.toShort(), 5.toShort()) }

        runBlocking {
            val sent = Random.nextBytes(512)
            var fired = false
            client2[12]!!.listeners.add { Assertions.assertArrayEquals(it, sent); fired = true }

            client1[5]!!.send(sent)

            delay(1000)  // Give it a second to send everything
            Assertions.assertTrue(fired)  // Make sure it actually happened

            // New connection to not fire the previous listener
            client1.connect(client2addr, 123, 321)

            fired = false
            val sent2 = Random.nextBytes(512)
            val client1comms = Packets.CommunicationClient(client1[321]!!.communicationSystem)
            val client2comms = Packets.CommunicationClient(client2[123]!!.communicationSystem)
            client2comms.addListener { transmission, senderId -> Assertions.assertArrayEquals((transmission as DummyTransmission).content, sent2); Assertions.assertEquals(senderId, client1addr.num.toInt()); fired = true }
            client1comms.send(DummyTransmission(sent2))
            for (i in 0 until 100) {
                client1comms.update()
                client2comms.update()
                delay(10)
            }
            Assertions.assertTrue(fired)
        }
    }
}
