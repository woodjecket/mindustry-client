package mindustry.client.communication

import kotlinx.coroutines.*
import mindustry.client.utils.buffer
import mindustry.client.utils.remainingBytes
import mindustry.client.utils.toBytes
import java.io.InputStream
import java.io.OutputStream
import java.lang.ref.WeakReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.random.Random

/** Be careful when using this class!  Make sure that the [inp]'s available() method is exact (i.e. [java.io.ByteArrayInputStream]). */
class TunneledCommunicationSystem(override val MAX_LENGTH: Int, override val RATE: Float, private val inp: InputStream, private val out: OutputStream) : CommunicationSystem() {
    override val listeners: MutableList<(input: ByteArray, sender: Int) -> Unit> = mutableListOf()
    override val id = Random.nextInt()

    init {
        lock.withLock {
            active.add(WeakReference(this))
        }
    }

    private companion object {
        val lock = ReentrantLock()
        val active = mutableListOf<WeakReference<TunneledCommunicationSystem>>()

        init {
            CoroutineScope(Dispatchers.Default).launch(Dispatchers.IO) {
                while (true) {
                    lock.withLock {
                        for (item in active) {
                            if (item.isEnqueued || item.get() == null) {
                                println("removing $item")
                                active.remove(item)
                                continue
                            }

                            val gotten = item.get() ?: continue
                            val bytes = gotten.inp.readBytes()
                            if (bytes.size >= 4) {
                                val buf = bytes.apply { println("G: ${contentToString()}") }.buffer()
                                val sender = buf.int
                                val content = buf.remainingBytes()
                                gotten.listeners.forEach { it(content, sender) }
                            }
                        }
                    }
                    delay(10)
                }
            }
        }
    }

    override fun send(bytes: ByteArray) {
        println("sending ${bytes.size} bytes...")
        println("S: ${bytes.contentToString()}")
        out.write(id.toBytes() + bytes)
    }
}
