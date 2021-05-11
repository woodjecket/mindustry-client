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
        // Create output array
        val out = CharArray(encodedLengthOf(input.size))
        // Create bit stream from input
        val buffer = RandomAccessInputStream(input.plus(listOf(0, 0)))

        for (index in out.indices) {
            // Get [BITS] bits out of the stream and add 128 to avoid ASCII control chars
            out[index] = buffer.readBits(BITS).toChar() + 128
        }

        // Include encoded length as 4 chars each representing 1 byte
        val lengthEncoded = String(input.size.toBytes().map { it.toChar() + 128 }.toCharArray())
        return lengthEncoded + out.concatToString()
    }

    @Throws(IOException::class)
    fun decode(input: String): ByteArray {
        if (input.length < 4) throw IOException("String does not have length prefix!")
        try {
            // Extract length
            val size = input.slice(0 until 4).toCharArray().map { (it - 128).toByte() }.toByteArray().toInt()
            // Create output
            val array = ByteArray(size)
            // Create bit stream leading to output array
            val buffer = RandomAccessOutputStream(array)

            for (c in input.drop(4)) {
                // Take each char, reverse the transform, and add it to the bit stream
                buffer.writeBits((c.toInt() - 128), BITS)
            }

            return array
        } catch (e: Exception) {
            throw IOException(e)
        }
    }
}
