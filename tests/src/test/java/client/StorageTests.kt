package client

import mindustry.client.storage.DummyStorable
import mindustry.client.storage.DummyStorageSystem
import mindustry.client.storage.Storable
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import kotlin.random.Random

class StorageTests {
    val stored = mutableMapOf<Long, DummyStorable>()

    companion object {
        val fs = DummyStorageSystem()

        @BeforeAll
        @JvmStatic
        fun init() {
            fs.init()
        }
    }

    @RepeatedTest(3)
    fun test() {
        val data = Random.nextBytes(500)
        val storable = DummyStorable(data)
        stored[storable.id] = storable

        fs.store(storable)

        for (item in stored) {
            Assertions.assertArrayEquals(item.value.array, (fs.getById(item.key) as DummyStorable).array)
        }
    }
}
