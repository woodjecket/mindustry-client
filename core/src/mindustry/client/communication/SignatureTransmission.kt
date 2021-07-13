package mindustry.client.communication

import mindustry.client.utils.buffer
import mindustry.client.utils.bytes
import mindustry.client.utils.remainingBytes
import mindustry.client.utils.toBytes
import org.bouncycastle.math.ec.rfc8032.Ed25519
import java.math.BigInteger
import java.nio.ByteBuffer
import kotlin.random.Random

class SignatureTransmission : Transmission {

    override var id = Random.nextLong()
    val signature: ByteArray
    val timeSentMillis: Long
    val certSN: BigInteger

    constructor(signature: ByteArray, timeSentMillis: Long, certSN: BigInteger) {
        this.signature = signature
        this.timeSentMillis = timeSentMillis
        this.certSN = certSN
    }

    constructor(input: ByteArray, id: Long) {
        this.id = id

        val buf = input.buffer()
        signature = buf.bytes(Ed25519.SIGNATURE_SIZE)
        timeSentMillis = buf.long
        certSN = BigInteger(buf.remainingBytes())
    }

    override fun serialize(): ByteArray {
        val snBytes = certSN.toByteArray()
        val buf = ByteBuffer.allocate(Ed25519.SIGNATURE_SIZE + Long.SIZE_BYTES + snBytes.size)
        buf.put(signature)
        buf.putLong(timeSentMillis)
        buf.put(snBytes)
        return buf.array()
    }

    companion object {
        fun format(msg: ByteArray, timeMillis: Long, certSN: BigInteger) = msg + timeMillis.toBytes() + certSN.toByteArray()
    }
}
