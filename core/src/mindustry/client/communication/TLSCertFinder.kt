package mindustry.client.communication

import mindustry.client.utils.buffer
import mindustry.client.utils.remainingBytes
import mindustry.client.utils.toBytes
import java.math.BigInteger
import kotlin.random.Random

class TLSCertFinder : Transmission {
    override var id = Random.nextLong()
    val serialNum: BigInteger
    val response: Boolean

    constructor(input: ByteArray, id: Long) {
        this.id = id
        val buf = input.buffer()
        response = buf.get() != 0.toByte()
        serialNum = BigInteger(buf.remainingBytes())
    }

    constructor(serialNum: BigInteger, response: Boolean) {
        this.serialNum = serialNum
        this.response = response
    }

    override fun serialize(): ByteArray = byteArrayOf(if (response) 1.toByte() else 0.toByte()) + serialNum.toByteArray()
}