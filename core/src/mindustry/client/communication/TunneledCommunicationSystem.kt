package mindustry.client.communication

import kotlinx.coroutines.*
import mindustry.client.utils.buffer
import mindustry.client.utils.remainingBytes
import mindustry.client.utils.toBytes
import java.io.InputStream
import java.io.OutputStream
import java.lang.ref.WeakReference
import kotlin.random.Random

/** Be careful when using this class!  Make sure that the [inp]'s available() method is exact (i.e. [java.io.ByteArrayInputStream]). */
class TunneledCommunicationSystem(override val MAX_LENGTH: Int, override val RATE: Float, private val inp: InputStream, private val out: OutputStream) : CommunicationSystem() {
    override val listeners: MutableList<(input: ByteArray, sender: Int) -> Unit> = mutableListOf()
    override val id = Random.nextInt()

    init {
        active.add(WeakReference(this))
    }

    private companion object {
        val active = mutableListOf<WeakReference<TunneledCommunicationSystem>>()

        init {
            MainScope().launch(Dispatchers.IO) {
                while (true) {
                    for (item in active) {
                        if (item.isEnqueued || item.get() == null) {
                            println("removing $item")
                            active.remove(item)
                            continue
                        }

                        val gotten = item.get() ?: continue
                        if (gotten.inp.available() >= 0) {
                            val buf = gotten.inp.readBytes().copyOf().apply { println("G: ${contentToString()}") }.buffer()
                            val sender = buf.int
                            val content = buf.remainingBytes()
                            gotten.listeners.forEach { it(content, sender) }
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
