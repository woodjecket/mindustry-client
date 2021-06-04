package mindustry.client.communication

import mindustry.client.utils.buffer
import mindustry.client.utils.remainingBytes
import mindustry.client.utils.toBytes
import java.math.BigInteger
import kotlin.random.Random

class TLSRequest : Transmission {
    override var id = Random.nextLong()
    val destination: Int
    val serialNum: BigInteger

    constructor(input: ByteArray, id: Long) {
        this.id = id
        val buf = input.buffer()
        destination = buf.int
        serialNum = BigInteger(buf.remainingBytes())
    }

    constructor(destination: Int, serialNum: BigInteger) {
        this.destination = destination
         this.serialNum = serialNum
    }

    override fun serialize() = destination.toBytes() + serialNum.toByteArray()
}