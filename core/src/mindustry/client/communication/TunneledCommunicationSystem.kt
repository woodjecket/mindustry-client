package mindustry.client.communication

import kotlinx.coroutines.*
import mindustry.client.utils.buffer
import mindustry.client.utils.remainingBytes
import mindustry.client.utils.toBytes
import java.io.InputStream
import java.io.OutputStream
import kotlin.random.Random

@OptIn(DelicateCoroutinesApi::class)
class TunneledCommunicationSystem(override val MAX_LENGTH: Int, override val RATE: Float, private val inp: InputStream, private val out: OutputStream) : CommunicationSystem() {
    override val listeners: MutableList<(input: ByteArray, sender: Int) -> Unit> = mutableListOf()
    override val id = Random.nextInt()

    private suspend fun update() {
        coroutineScope {
            launch(Dispatchers.IO) {
                while (true) {
                    if (inp.available() > 4) {
                        val buf = inp.readBytes().buffer()
                        val sender = buf.int
                        val content = buf.remainingBytes()
                        listeners.forEach { it(content, sender) }
                    }
                    delay(10L)
                }
            }
        }
    }

    override fun send(bytes: ByteArray) {
        out.write(id.toBytes() + bytes)
    }
}
