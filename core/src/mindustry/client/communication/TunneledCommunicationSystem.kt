package mindustry.client.communication

import kotlinx.coroutines.*
import mindustry.client.Main
import mindustry.client.crypto.Base32768Coder
import mindustry.client.utils.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.experimental.and
import kotlin.experimental.inv
import kotlin.experimental.or
import kotlin.experimental.xor
import kotlin.random.Random

/** don't use me This class will keep a thread around until you close the input stream. */
class TunneledCommunicationSystem(override val RATE: Float, private val inp: InputStream, private val out: OutputStream, override val id: Int, val otherId: Int) : CommunicationSystem() {
    override val listeners: MutableList<(input: ByteArray, sender: Int) -> Unit> = mutableListOf()
    private val incoming = mutableListOf<Byte>()
    override val MAX_LENGTH = 0b01111110  // Any more and the length byte will run out of room/be ambiguous

    init {
        active.add(TunnelRef(this))
    }

    override fun send(bytes: ByteArray) {
        out.write((Converter.encode(bytes) + 0.toByte().inv()))  // The converter splits it into 7 bit chunks
    }

    private class TunnelRef(system: TunneledCommunicationSystem) : WeakReference<TunneledCommunicationSystem>(system) {
        fun cleanup() {
            println("Cleaning up tunnel...")
            get()?.job?.cancel()
            active.remove(this)
            println("Finished, job canceled")
        }
    }

    private companion object {
        val active = mutableListOf<TunnelRef>()
        init {
            Main.mainScope.launch(Dispatchers.IO) {
                val queue = ReferenceQueue<TunneledCommunicationSystem>()
                while (true) {
                    val gotten = queue.remove() as? TunnelRef ?: continue
                    gotten.cleanup()
                }
            }
        }
    }

    private val job = Main.mainScope.launch(Dispatchers.IO) {
        while (true) {
            try {
                incoming.addAll(inp.readBytes().toList().map { it xor 0b10000000.toByte() /* I have become one with the mystery jank */ })
                if (incoming.lastOrNull() == 0.toByte().inv()) {
                    val bytes = Converter.decode(incoming.dropLast(1).toByteArray())
                    incoming.clear()
                    listeners.forEach { it(bytes, otherId) }
                }
            } catch (e: IOException) {
                cancel()
                break
            }
        }
    }

    private object Converter {
        private const val BITS = 7

        fun availableBytes(length: Int) = ((length * BITS) / 8.0).floor()

        fun encodedLengthOf(bytes: Int) = ((bytes * 8.0) / BITS).ceil()

        fun encode(input: ByteArray): ByteArray {
            // Create output array
            val out = ByteArray(encodedLengthOf(input.size))
            // Create bit stream from input
            val buffer = RandomAccessInputStream(input + 0)

            for (index in out.indices) {
                out[index] = buffer.readBits(BITS).toByte()
            }
            return byteArrayOf(input.size.toByte() or 0b10000000.toByte()) + out
        }

        @Throws(IOException::class)
        fun decode(input: ByteArray): ByteArray {
            try {
                // Extract length
                val size = (input[0] and 0b01111111).toInt()
                // Create output
                val array = ByteArray(size)
                // Create bit stream leading to output array
                val buffer = RandomAccessOutputStream(array)

                for (c in input.drop(1)) {
                    // Take each char, reverse the transform, and add it to the bit stream
                    buffer.writeBits(c.toInt(), BITS)
                }

                return array
            } catch (e: Exception) {
                throw IOException(e)
            }
        }
    }
}
