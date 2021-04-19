package mindustry.client.storage

import mindustry.client.utils.buffer

interface ByteSection : Iterable<Byte> {
    val size: Int

    operator fun get(index: Int): Byte

    operator fun set(index: Int, byte: Byte)

    operator fun get(range: IntRange): ByteArray

    operator fun set(range: IntRange, value: ByteArray)

    fun all(): ByteArray

    fun buffer() = all().buffer()
}
