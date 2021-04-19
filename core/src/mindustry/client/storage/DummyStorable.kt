package mindustry.client.storage

import kotlin.random.Random

class DummyStorable : Storable {
    override val id: Long
    var array: ByteArray

    constructor(id: Long, array: ByteArray) {
        this.id = id
        this.array = array
    }

    constructor(array: ByteArray) {
        this.array = array
        id = Random.nextLong()
    }

    override fun serialize() = array

    override fun deserialize(input: ByteArray) {
        array = input
    }
}
