package client

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mindustry.client.communication.*
import org.junit.jupiter.api.*
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.random.Random

class TunneledPacketTests {

    private class BufferStreamPair {
        private val buffer = mutableListOf<Byte>()
        val input = BufInpStr(this)
        val output = BufOutStr(this)
        val lock = ReentrantLock()

        class BufInpStr(val pair: BufferStreamPair) : InputStream() {
            override fun read(): Int {
                return pair.lock.withLock { pair.buffer.removeLast() + 128 }
            }

            override fun readNBytes(len: Int): ByteArray {
                return pair.lock.withLock { pair.buffer.dropLast(len).toByteArray() }
            }

            override fun available(): Int {
                return pair.lock.withLock { pair.buffer.size }
            }

            fun readAvailable(): ByteArray {
                val bytes: ByteArray
                pair.lock.withLock {
                    bytes = pair.buffer.toByteArray()
                    pair.buffer.clear()
                }
                return bytes
            }
        }

        class BufOutStr(val pair: BufferStreamPair) : OutputStream() {
            override fun write(b: Int) {
                pair.lock.withLock { pair.buffer.add((b - 128).toByte()) }
            }

            override fun write(b: ByteArray) {
                pair.lock.withLock {
                    pair.buffer.addAll(b.toList())
                }
            }
        }
    }

    @Test
    fun testSending() {
        val oneToTwo = BufferStreamPair()
        val twoToOne = BufferStreamPair()

        runBlocking {

            val copier = launch(Dispatchers.IO) {
                while (true) {
                    twoToOne.output.write(twoToOne.input.readAvailable())
                    oneToTwo.output.write(oneToTwo.input.readAvailable())
                }
            }

            launch {
                delay(2000L)
                copier.cancel()
            }

            val client1 = Packets.CommunicationClient(TunneledCommunicationSystem(2048, 0f, oneToTwo.input, oneToTwo.output))
            val client2 = Packets.CommunicationClient(TunneledCommunicationSystem(2048, 0f, twoToOne.input, twoToOne.output))

            val transmission1 = DummyTransmission(Random.nextBytes(10))
            val transmission2 = DummyTransmission(Random.nextBytes(10))
            val transmission3 = DummyTransmission(Random.nextBytes(10))

            var output1: ByteArray? = null
            var output2: ByteArray? = null
            var output3: ByteArray? = null

            val listener = { t: Transmission, _: Int ->
                if (t is DummyTransmission) {
                    when (t.id) {
                        transmission1.id -> output1 = t.content
                        transmission2.id -> output2 = t.content
                        transmission3.id -> output3 = t.content
                    }
                }
            }

            client1.listeners.add(listener)
            client2.listeners.add(listener)

            client1.send(transmission1)
            delay(500)
            client1.send(transmission2)
            delay(500)
            client2.send(transmission3)
            delay(500)

            for (i in 0..150) {
                client1.update()
                client2.update()
                delay(10)
            }

            Assertions.assertArrayEquals(transmission1.content, output1)
            Assertions.assertArrayEquals(transmission2.content, output2)
            Assertions.assertArrayEquals(transmission3.content, output3)
        }
    }
}
