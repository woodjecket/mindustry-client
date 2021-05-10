package mindustry.client.crypto

import mindustry.client.crypto.Base32768Coder.BITS
import mindustry.client.utils.*
import java.io.IOException
import java.lang.Integer.toBinaryString
import java.math.BigInteger
import java.util.*
import kotlin.experimental.and
import kotlin.experimental.or

/** You've heard of base64, now get ready for... base32768.  Encodes 15 bits of data into each unicode character,
 * which so far has not caused any problems.  If it turns out to break stuff, the [BITS] constant can be changed
 * to a more sensible value.  Note that it is not just a base conversion, it also has a length prefix.
 * TODO: maybe move to arbitrary base?  It sucks that it can't be 16 bit just because it has to avoid a couple chars.
 */
object Base32768Coder {
    private const val BITS = 15

    fun availableBytes(length: Int) = ((length * BITS) / 8.0).floor()

    fun encodedLengthOf(bytes: Int) = ((bytes * 8.0) / BITS).ceil()

    @OptIn(ExperimentalUnsignedTypes::class)
    fun encode(input: ByteArray): String {
        val out = CharArray(encodedLengthOf(input.size))
        val originalBits = BitSet.valueOf(input)
        val newBits = BitSet()

        var idx = 0
        for (index in 0 until out.size - 1) {
            val first = input[index]
            val second = input.getOrElse(index + 1) { 0 }
            val offset = (index * BITS) % 8
            val aaa = (first.toUInt() shr offset)
            for (i in 0 until 16) {
                newBits[idx++] = (aaa and (1u shl i)) == 1u
            }
            out[index] = ((first.toUInt() shr offset) or (second.toUInt() shr (offset + 8))).toInt().toChar() + 128
        }

        println("O: $originalBits")
        println("N: $newBits")

        return String(charArrayOf(input.size.toChar() + 128)) + out.concatToString()
    }

//    @OptIn(ExperimentalUnsignedTypes::class)
//    private fun ByteArray.bits(range: IntRange): ByteArray {
//        val startingByte = range.first / 8
//        val startingIndex = range.first % 8
//
//        val endByte = range.last / 8
//        val endIndex = range.last % 8
//
//        val output = ByteArray((range.size / 8.0).ceil())
//
//        for ((outputIndex, index) in (startingByte until endByte).withIndex()) {
//            val current = get(index).toUInt()
//            val next = get(index + 1).toUInt()
//            output[outputIndex] = ((current shl startingIndex) or ((next or ones(startingIndex).toUInt()) shr (8 - startingIndex))).toByte()
//        }
//        output[output.size - 1] = get(endByte) or ones(endIndex)
//        return output
//    }
//
//    @OptIn(ExperimentalUnsignedTypes::class)
//    private fun ByteArray.setBits(range: IntRange, bits: ByteArray) {
//        val startingByte = range.first / 8
//        val startingIndex = range.first % 8
//
//        val endByte = range.last / 8
//        val endIndex = range.last % 8
//
//        val bigint = BigInteger(sliceArray(startingByte..endByte))
//        bigint.shiftLeft(startingIndex).and()
//    }

    @Throws(IOException::class)
    fun decode(input: String): ByteArray {
        val size = input.first().toInt() - 128
        val output = ByteArray(size)

        for ((index, c) in input.withIndex()) {
            if (index == 0) continue

            val firstByteIndex = ((index - 1) * BITS) / 8
            val secondByteIndex = firstByteIndex + 1
            val shifted = c.toInt().toUInt() shr ((index - 1) % BITS)
            output[firstByteIndex] = (output[firstByteIndex].toUInt() or (shifted and 0b11111111.toUInt())).toByte()
            if (secondByteIndex >= output.size) continue
            output[secondByteIndex] = ((output[secondByteIndex].toUInt() or (shifted and 0b1111111100000000.toUInt())) shr 8).toByte()
        }

        return output
    }

    fun encode(string: String): String {
        return encode(string.toByteArray(Charsets.UTF_8))
    }

    @Throws(IOException::class)
    fun decodeString(input: String): String {
        val decoded = decode(input)
        return String(decoded, Charsets.UTF_8)
    }
}
