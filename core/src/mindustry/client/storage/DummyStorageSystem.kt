package mindustry.client.storage

class DummyStorageSystem : StorageSystem() {

    override var locked = false

    override fun getByte(index: Int) = memory[index]

    override fun setByte(index: Int, value: Byte) { memory[index] = value }

    override fun getRange(range: IntRange) = memory.sliceArray(range)

    override fun setRange(range: IntRange, value: ByteArray) {
        for ((index, location) in range.withIndex()) {
            if (index >= value.size) return
            memory[location] = value[index]
        }
    }

    private var memory = ByteArray(2048 + 1024)

    override fun lock(timeout: Int) {
        locked = true // todo: timeout
    }

    override fun unlock() {
        locked = false
    }
}
