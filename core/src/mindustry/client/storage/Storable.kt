package mindustry.client.storage

interface Storable {
    val type: Int

    fun serialize(): ByteArray

    fun deserialize(input: ByteArray)
}
