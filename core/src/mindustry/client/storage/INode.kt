package mindustry.client.storage

import java.nio.ByteBuffer

class INode {
    val type: Int
    val version: Int
    val id: Long
    val addressCount: Int
    val address: List<IntRange>

    companion object {
        fun sizeWithAddresses(n: Int) = (n * 2 * Int.SIZE_BYTES) + Int.SIZE_BYTES * 3 + Long.SIZE_BYTES
    }

    constructor(address: List<IntRange>, type: Int, version: Int, id: Long) {
        this.address = address
        this.type = type
        this.version = version
        this.id = id
        this.addressCount = address.size
    }

    constructor(buffer: ByteBuffer) {
        addressCount = buffer.int
        type = buffer.int
        version = buffer.int
        id = buffer.long
        val list = mutableListOf<IntRange>()
        for (i in 0 until addressCount) {
            list.add(buffer.int..buffer.int)
        }
        address = list
//        buffer.int
    }

    fun serialize(): ByteArray {
        val buf = ByteBuffer.allocate(sizeWithAddresses(addressCount))
        buf.putInt(addressCount)
        buf.putInt(type)
        buf.putInt(version)
        buf.putLong(id)
        for (item in address) {
            buf.putInt(item.first)
            buf.putInt(item.last)
        }
//        buf.putInt(addressCount)
        return buf.array()
    }
}
