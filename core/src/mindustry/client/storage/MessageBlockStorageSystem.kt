package mindustry.client.storage

import arc.Core
import arc.Events
import arc.struct.Seq
import mindustry.Vars
import mindustry.client.ClientVars
import mindustry.client.antigrief.ConfigRequest
import mindustry.client.crypto.Base32768Coder
import mindustry.client.utils.age
import mindustry.client.utils.base32678
import mindustry.client.utils.print
import mindustry.game.EventType
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.world.blocks.logic.LogicBlock
import java.time.Instant

object MessageBlockStorageSystem : StorageSystem() {
    private var processorIndex = mutableListOf<LogicBlock.LogicBuild>()
    private const val STORAGE_PROCESSOR_PREFIX = "end\nprint \"data storage, do not modify\"\n"
    private const val STORAGE_PROCESSOR_INDEX = "set num %d"
    private var cache = byteArrayOf()
    private var lastConfig = Instant.EPOCH

    init {
        Events.on(EventType.WorldLoadEvent::class.java) {
            println("Load")
            cache = byteArrayOf()
            load()
        }

        Events.on(EventType.ConfigEvent::class.java) {
            if (it.player == Vars.player && lastConfig.age() < 1) return@on
            if (matches((it.tile as? LogicBlock.LogicBuild)?.code)) {
                println("Found tile")
                load()
            }
        }

        Events.on(EventType.BlockBuildEndEvent::class.java) {
            if (it.tile.block() is LogicBlock) {
                println("Updating positions")
                updatePositions()
            } else if (it.breaking && processorIndex.any { item -> item.pos() == it.tile.pos() }) {
                println("Broken, loading")
                load()
            }
        }

        Events.on(EventType.BlockDestroyEvent::class.java) {
            if (it.tile.block() is LogicBlock) {
                Core.app.post {
                    updatePositions()
                }
            }
        }
    }

    override fun init() {
        super.init()
        cache = ByteArray(size)
    }

    private fun updatePositions() {
        processorIndex.clear()
        for (block in Groups.build) {
            val b = block as? LogicBlock.LogicBuild ?: continue
            if (matches(b.code)) {
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
        if (processorIndex.isEmpty()) {
            cache = ByteArray(size)
        }
        cache = processorIndex.joinToString("") {
            removePrefix(it.code)?.split("\n")?.joinToString("") { item -> item.removeSurrounding("print \"", "\"") } ?: run { println("AAAAAAAA"); "" }
        }.base32678() ?: ByteArray(size)
        cache = cache.copyOf(size)
        mainBytes = StorageSystemByteSection(this, metadataBytes.size + 1, size - metadataBytes.size)
    }

    fun save() {
        updatePositions()
        val values = cache.base32678().chunked(34).map { "print \"$it\"" }.chunked(995).mapIndexed { index, s -> STORAGE_PROCESSOR_PREFIX + STORAGE_PROCESSOR_INDEX.format(index) + "\n" + s.joinToString("\n") }
        for ((index, value) in values.withIndex()) {
            if (processorIndex[index].code != value) config(value, processorIndex[index], listOf())
        }
    }

    fun allocate(build: LogicBlock.LogicBuild) {
        updatePositions()
        if (processorIndex.isEmpty()) {
            config(STORAGE_PROCESSOR_INDEX + STORAGE_PROCESSOR_INDEX.format(0), build, listOf())
            return
        }
        val previous = processorIndex.last()
        config(STORAGE_PROCESSOR_PREFIX + STORAGE_PROCESSOR_INDEX.format((getIndex(previous) ?: return) + 1), build, listOf())
    }

    fun allocate(): String {
        updatePositions()
        Core.app.post {
            updatePositions()
        }
        if (processorIndex.isEmpty()) {
            return STORAGE_PROCESSOR_PREFIX + STORAGE_PROCESSOR_INDEX.format(0)
        }
        return STORAGE_PROCESSOR_PREFIX + STORAGE_PROCESSOR_INDEX.format((getIndex(processorIndex.last()) ?: -1) + 1)
    }

    private fun config(string: String, build: LogicBlock.LogicBuild, links: List<LogicBlock.LogicLink> = build.links.toList()) {
        Call.tileConfig(Vars.player, build, LogicBlock.compress(string, Seq.with(links)))
        lastConfig = Instant.now()
//        ClientVars.configs.add(ConfigRequest(build.tileX(), build.tileY(), LogicBlock.compress(string, Seq.with(links))))
    }

//    private val regex = "end\\nprint \"data storage, do not modify\"\\nset num \\d+".toRegex()
    private val regex = (STORAGE_PROCESSOR_PREFIX + STORAGE_PROCESSOR_INDEX.replace("%d", "\\d+")).print().toRegex()

    private fun matches(string: String?): Boolean {
        string ?: return false
        return regex.containsMatchIn(string)
    }

    private fun getIndex(build: LogicBlock.LogicBuild): Int? {
        if (matches(build.code)) {
            return build.code.split("\n")[2].filter { it in '0'..'9' }.toInt()
        }
        return null
    }

    private fun removePrefix(string: String): String? {
        if (matches(string)) {
            return regex.replace(string, "").removePrefix("\n")
        }
        return null
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
