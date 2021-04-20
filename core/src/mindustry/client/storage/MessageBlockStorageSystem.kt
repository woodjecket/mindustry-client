package mindustry.client.storage

import arc.Events
import mindustry.Vars
import mindustry.client.ClientVars
import mindustry.client.antigrief.ConfigRequest
import mindustry.client.crypto.Base32768Coder
import mindustry.client.utils.base32678
import mindustry.game.EventType
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.world.blocks.logic.LogicBlock

object MessageBlockStorageSystem : StorageSystem() {
    private var processorIndex = mutableListOf<LogicBlock.LogicBuild>()
    private const val STORAGE_PROCESSOR_PREFIX = "end\nprint \"data storage, do not modify\"\n"
    private const val STORAGE_PROCESSOR_FIRST = "print \"num 0\"\n"
    private var cache = byteArrayOf()

    init {
        Events.on(EventType.WorldLoadEvent::class.java) {
            load()
        }

        Events.on(EventType.ConfigEvent::class.java) {
            if (it.player == Vars.player) return@on
            if ((it.tile as? LogicBlock.LogicBuild)?.code?.startsWith(STORAGE_PROCESSOR_PREFIX) == true) {
                println("Found tile")
                load()
            }
        }
    }

    private fun updatePositions() {
        processorIndex.clear()
        for (block in Groups.build) {
            val b = block as? LogicBlock.LogicBuild ?: continue
            if (b.code.startsWith(STORAGE_PROCESSOR_PREFIX + STORAGE_PROCESSOR_FIRST.removeSuffix("\n"))) {
                processorIndex.add(b)
                break
            }
        }
        var last = processorIndex.getOrNull(0)
        while (last != null) {
            val link = last.links.toList().getOrNull(0) ?: break
            last = Vars.world.tile(link.x, link.y).build as? LogicBlock.LogicBuild ?: return
            processorIndex.add(last)
        }
    }

    fun load() {
        updatePositions()
        if (processorIndex.isEmpty()) return
        val size = Base32768Coder.availableBytes(995 * processorIndex.size * 34) - metadataBytes.size
        cache = processorIndex.joinToString("") {
            it.code.removePrefix(STORAGE_PROCESSOR_PREFIX).removePrefix(STORAGE_PROCESSOR_FIRST)
        }.base32678() ?: ByteArray(size).also { metadataBytes = StorageSystemByteSection(this, 0, 1024) }
        cache = cache.copyOf(size)
        mainBytes = StorageSystemByteSection(this, metadataBytes.size + 1, size - metadataBytes.size)
    }

    fun save() {
        updatePositions()
        val values = cache.base32678().chunked(34).map { "print \"$it\"" }.chunked(995).mapIndexed { index, s -> STORAGE_PROCESSOR_PREFIX + (if (index == 0) STORAGE_PROCESSOR_FIRST else "") + s.joinToString("\n") }
        for ((index, value) in values.withIndex()) {
            println("configuring message block $index with value $value")
            Call.tileConfig(Vars.player, processorIndex[index], value)
        }
    }

    fun allocate(build: LogicBlock.LogicBuild) {
        updatePositions()
        if (processorIndex.isEmpty()) {
            ClientVars.configs.add(ConfigRequest(build.tileX(), build.tileY(), STORAGE_PROCESSOR_PREFIX + STORAGE_PROCESSOR_FIRST))
            return
        }
        val previous = processorIndex.last()
        ClientVars.configs.add(ConfigRequest(build.tileX(), build.tileY(), STORAGE_PROCESSOR_PREFIX))
        ClientVars.configs.add(ConfigRequest(previous.tileX(), previous.tileY(), build.pos()))
    }

    override fun getByte(index: Int): Byte {
        return cache[index]
    }

    override fun setByte(index: Int, value: Byte) {
        cache[index] = value
    }

    override fun getRange(range: IntRange): ByteArray {
        return cache.sliceArray(range)
    }

    override fun setRange(range: IntRange, value: ByteArray) {
        for ((index, item) in range.withIndex()) {
            cache[item] = value[index]
        }
    }

    override fun lock(timeout: Int) {
        super.lock(timeout)
        println("Locking for $timeout seconds")
        save()
    }

    override fun unlock() {
        super.unlock()
        println("Unlocking")
        save()
    }
}
