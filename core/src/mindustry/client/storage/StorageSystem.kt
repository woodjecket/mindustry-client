package mindustry.client.storage

import mindustry.client.utils.buffer
import java.nio.ByteBuffer

interface StorageSystem {
    companion object {
        class INode {
            companion object {
                const val SIZE_BYTES = 4 * Int.SIZE_BYTES + Long.SIZE_BYTES
            }
            val address: IntRange
            val type: Int
            val version: Int
            val id: Long

            constructor(address: IntRange, type: Int, version: Int, id: Long) {
                this.address = address
                this.type = type
                this.version = version
                this.id = id
            }

            constructor(buffer: ByteBuffer) {
                this.address = buffer.int..buffer.int
                this.type = buffer.int
                this.version = buffer.int
                this.id = buffer.long
            }

            fun serialize(): ByteArray {
                val buf = ByteBuffer.allocate(SIZE_BYTES)
                buf.putInt(address.first)
                buf.putInt(address.last)
                buf.putInt(type)
                buf.putInt(version)
                buf.putLong(id)
                return buf.array()
            }
        }
    }

    val locked: Boolean

    fun getInodeBytes(): ByteArray

    fun setInodeBytes(addresses: IntRange, content: ByteArray)

    fun inodes(): List<INode> {
        val bytes = getInodeBytes()
        val buffer = bytes.buffer()
        val output = mutableListOf<INode>()
        for (range in 0 until bytes.size / INode.SIZE_BYTES) {
            output.add(INode(buffer))
        }
        return output
    }

    fun saveINode(node: INode) {
        val bytes = getInodeBytes()
    }

    operator fun set(addresses: IntRange, bytes: ByteArray)

    operator fun get(addresses: IntRange): ByteArray

    fun lock(timeout: Int)

    fun unlock()
}
