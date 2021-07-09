package mindustry.client.communication

import mindustry.client.utils.int
import mindustry.client.utils.toBytes
import kotlin.random.Random

class TLSRequest : Transmission {
    override var id = Random.nextLong()
    val destination: Int

    constructor(input: ByteArray, id: Long) {
        this.id = id
        destination = input.int()
    }

    constructor(destination: Int) {
        this.destination = destination
    }

    override fun serialize() = destination.toBytes()
}