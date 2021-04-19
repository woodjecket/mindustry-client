package mindustry.client.storage

import kotlin.reflect.KClass

interface Storable {
    val id: Long

    fun serialize(): ByteArray

    fun deserialize(input: ByteArray)

    object StorableRegistrator {
        data class RegisteredStorable<T : Storable>(val clazz: KClass<T>, val provider: (id: Long) -> T)

        private val registered = mutableListOf<RegisteredStorable<*>>()

        fun <T : Storable> register(clazz: KClass<T>, provider: (id: Long) -> T) {
            registered.add(RegisteredStorable(clazz, provider))
        }

        fun id(storable: Storable) = registered.indexOfFirst { it.clazz == storable::class }

        fun instantiate(type: Int, storableID: Long) = registered[type].provider(storableID)
    }
}
