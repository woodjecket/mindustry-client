package mindustry.client.communication

import mindustry.client.utils.buffer
import mindustry.client.utils.remainingBytes
import mindustry.client.utils.toBytes
import kotlin.random.Random

class TLSTransmission : Transmission {
    override var id = Random.nextLong()
    val destination: Int
    val content: ByteArray

    constructor(input: ByteArray, id: Long) {
        this.id = id
        val buf = input.buffer()
        destination = buf.int
        content = buf.remainingBytes()
    }

    constructor(destination: Int, content: ByteArray) {
        this.destination = destination
        this.content = content
    }

    override fun serialize() = destination.toBytes() + content
}