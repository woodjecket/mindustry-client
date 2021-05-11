package mindustry.client.crypto

import arc.util.Reflect
import mindustry.client.crypto.Base32768Coder.BITS
import mindustry.client.utils.*
import java.io.IOException
import java.lang.Integer.toBinaryString
import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.*
import kotlin.experimental.and
import kotlin.experimental.or
import kotlin.math.ceil

/** You've heard of base64, now get ready for... base32768.  Encodes 15 bits of data into each unicode character,
 * which so far has not caused any problems.  If it turns out to break stuff, the [BITS] constant can be changed
 * to a more sensible value.  Note that it is not just a base conversion, it also has a length prefix.
 * TODO: maybe move to arbitrary base?  It sucks that it can't be 16 bit just because it has to avoid a couple chars.
 */
object Base32768Coder {
    private const val BITS = 15

    fun availableBytes(length: Int) = ((length * BITS) / 8.0).floor()

    fun encodedLengthOf(bytes: Int) = ((bytes * 8.0) / BITS).ceil()

    fun encode(input: ByteArray): String {
//        val bitSize = input.size * 8
//        val charSize = ceil(bitSize.toDouble() / BITS).toInt()
//        val newSize = (charSize * BITS) / 8
        val out = CharArray(encodedLengthOf(input.size) + 5) { 128.toChar() }
        val buffer = RandomAccessInputStream(input.plus(0).plus(0))

        for (index in out.indices) {
            if (buffer.available() < 1) break
            out[index] = buffer.readBits(BITS).toChar() + 128
        }

        return String(input.size.toBytes().map { it.toChar() + 128 }.toCharArray()) + out.concatToString()
    }

    @Throws(IOException::class)
    fun decode(input: String): ByteArray {
        val size = input.slice(0 until 4).toCharArray().map { (it - 128).toByte() }.toByteArray().toInt()
        val array = ByteArray(size)
        val buffer = RandomAccessOutputStream(array)

        for (c in input.drop(4)) {
            buffer.writeBits((c.toInt() - 128), BITS)
        }

        return array
    }
}
