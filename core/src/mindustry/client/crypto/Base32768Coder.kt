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

    fun encode(input: ByteArray): String {
        val out = CharArray(encodedLengthOf(input.size))

        val bitset = BitSet.valueOf(input)
        for (index in 0 until out.size - 1) {
            out[index] = bitset[index * BITS, (index + 1) * BITS].toByteArray().padded(2).toChar() + 128
        }
        out[out.size - 1] = 123.toChar()

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
        println("size = $size")
        val output = BitSet(size * 8)

        for ((index, c) in input.substring(2 until input.length).withIndex()) {
            for ((b, i) in ((index * BITS) until ((index + 1) * BITS)).withIndex()) {
                val char = (c - 128).toInt()
//                println("Setting bits $i to ${i + BITS} to ${toBinaryString(char)}")
//                for (bit in 15 downTo 0) {
//                }
                output[i] = ((char and (1 shl b)) shr b) == 1
            }
        }

        return output.toByteArray().apply { println(contentToString()) }.sliceArray(0 until size)
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
