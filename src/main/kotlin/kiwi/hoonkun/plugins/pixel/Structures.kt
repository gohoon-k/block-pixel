package kiwi.hoonkun.plugins.pixel

import kiwi.hoonkun.plugins.pixel.nbt.TagType
import kiwi.hoonkun.plugins.pixel.nbt.tag.*

data class AnvilLocation(val x: Int, val z: Int)

typealias WorldAnvilFormat = Map<AnvilType, AnvilFormat>
typealias AnvilFormat = Map<AnvilLocation, ByteArray>

data class NBTLocation(val x: Int, val z: Int)

typealias NBT<T/* :NBTData */> = Map<AnvilLocation, List<T>>
typealias MutableNBT<T/* :NBTData */> = MutableMap<AnvilLocation, MutableList<T>>

typealias WorldNBTs = Map<String, WorldNBT>

data class WorldNBT(
    val chunk: NBT<Chunk>,
    val entity: NBT<Entity>,
    val poi: NBT<Poi>
)

typealias PackedBlocks = LongArray
typealias Blocks = List<Int>

enum class AnvilType(val path: String) {
    CHUNK("region"), POI("poi"), ENTITY("entities")
}

abstract class NBTData(val timestamp: Int, val nbt: CompoundTag) {
    abstract val location: NBTLocation
}

class Chunk(timestamp: Int, nbt: CompoundTag): NBTData(timestamp, nbt) {
    override val location = NBTLocation(xPos, zPos)

    private val xPos get() = nbt["xPos"]!!.getAs<IntTag>().value
    private val zPos get() = nbt["zPos"]!!.getAs<IntTag>().value

    val sections = nbt["sections"]!!.getAs<ListTag>().value.map { Section(it.getAs()) }
    var blockEntities
        get() = nbt["block_entities"]!!.getAs<ListTag>().value.map { BlockEntity(it.getAs()) }
        set(value) {
            nbt["block_entities"] = ListTag(TagType.TAG_COMPOUND, value.map { it.nbt }, true, "block_entities")
        }
}

data class Section(private val nbt: CompoundTag) {
    val y: Byte get() {
        val yTag = nbt["Y"]
        if (yTag is IntTag) {
            nbt["Y"] = ByteTag(yTag.value.toByte(), "Y")
            return yTag.value.toByte()
        }
        return yTag!!.getAs<ByteTag>().value
    }
    val blockStates = BlockStates(nbt["block_states"]!!.getAs())
}

data class BlockStates(private val nbt: CompoundTag) {
    var palette
        get() = nbt["palette"]!!.getAs<ListTag>().value.map {
            Palette(it.getAs())
        }
        set(value) {
            nbt["palette"] = ListTag(TagType.TAG_COMPOUND, value.map { it.nbt }, true, "palette")
        }
    var data: PackedBlocks
        get() = nbt["data"]?.getAs<LongArrayTag>()?.value ?: LongArray(0)
        set(value) {
            nbt["data"] = LongArrayTag(value, "data")
        }
}

data class Palette(val nbt: CompoundTag) {
    val name = nbt["Name"]!!.getAs<StringTag>().value
    private val properties = nbt["Properties"]?.getAs<CompoundTag>()?.value?.map { it.key to it.value.getAs<StringTag>().value }?.toMap()

    override fun equals(other: Any?): Boolean {
        return name == (other as? Palette)?.name && properties == (other as? Palette)?.properties
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + properties.hashCode()
        return result
    }
}

data class BlockEntity(val nbt: CompoundTag) {
    val x = nbt["x"]!!.getAs<IntTag>().value
    val y = nbt["y"]!!.getAs<IntTag>().value
    val z = nbt["z"]!!.getAs<IntTag>().value
    val id = nbt["id"]!!.getAs<StringTag>().value
}

class Poi(override val location: NBTLocation, timestamp: Int, nbt: CompoundTag): NBTData(timestamp, nbt) {
    var sections: Map<Int, PoiSection>
        get() = nbt["Sections"]!!.getAs<CompoundTag>().value.entries.associate { (k, v) -> k.toInt() to PoiSection(v.getAs()) }
        set(value) {
            nbt["Sections"] = CompoundTag(value.entries.associate { it.key.toString() to it.value.nbt }.toMutableMap(), "Sections")
        }
    var dataVersion: Int
        get() = nbt["DataVersion"]!!.getAs<IntTag>().value
        set(value) {
            nbt["DataVersion"] = IntTag(value, "DataVersion")
        }
}

class MutablePoi(override val location: NBTLocation, timestamp: Int, nbt: CompoundTag): NBTData(timestamp, nbt) {
    var sections: MutableMap<Int, MutablePoiSection>? = null
    var dataVersion: Int? = null

    fun toPoi(): Poi = Poi(location, timestamp, nbt).apply {
        sections = this@MutablePoi.sections!!.map { it.key to it.value.toPoiSection() }.toMap()
        dataVersion = this@MutablePoi.dataVersion!!
    }
}

data class PoiSection(val nbt: CompoundTag) {
    var valid: Byte
        get() = nbt["Valid"]!!.getAs<ByteTag>().value
        set(value) {
            nbt["Valid"] = ByteTag(value, "Valid")
        }
    var records: List<PoiRecord>
        get() = nbt["Records"]!!.getAs<ListTag>().value.map { PoiRecord(it.getAs()) }
        set(value) {
            nbt["Records"] = ListTag(TagType.TAG_COMPOUND, value.map { it.nbt }, true, "Records")
        }
}

data class MutablePoiSection(val nbt: CompoundTag) {
    var valid: Byte? = null
    var records: MutableList<PoiRecord>? = null

    fun toPoiSection(): PoiSection = PoiSection(nbt).apply {
        valid = this@MutablePoiSection.valid!!
        records = this@MutablePoiSection.records!!
    }
}

data class PoiRecord(val nbt: CompoundTag) {
    val pos = nbt["pos"]!!.getAs<IntArrayTag>().value
    var freeTickets: Int
        get() = nbt["free_tickets"]!!.getAs<IntTag>().value
        set(value) {
            nbt["free_tickets"] = IntTag(value, "free_tickets")
        }
    val type = nbt["type"]!!.getAs<StringTag>().value
}

class Entity(timestamp: Int, nbt: CompoundTag): NBTData(timestamp, nbt) {
    var position: IntArray
        get() = nbt["Position"]!!.getAs<IntArrayTag>().value
        set(value) {
            nbt["Position"] = IntArrayTag(value, "Position")
        }

    private val xPos get() = position[0]
    private val zPos get() = position[1]

    override val location: NBTLocation get() = NBTLocation(xPos, zPos)

    var entities: List<EntityEach>
        get() = nbt["Entities"]!!.getAs<ListTag>().value.map { EntityEach(it.getAs()) }
        set(value) {
            nbt["Entities"] = ListTag(TagType.TAG_COMPOUND, value.map { it.nbt }, true, "Entities")
        }

    var dataVersion: Int
        get() = nbt["DataVersion"]!!.getAs<IntTag>().value
        set(value) {
            nbt["DataVersion"] = IntTag(value, "DataVersion")
        }
}

class MutableEntity(override val location: NBTLocation, timestamp: Int, nbt: CompoundTag): NBTData(timestamp, nbt) {
    var position: IntArray? = intArrayOf(location.x, location.z)

    var entities: MutableList<EntityEach>? = null
    var dataVersion: Int? = null

    fun toEntity(): Entity = Entity(timestamp, nbt).apply {
        position = this@MutableEntity.position!!
        entities = this@MutableEntity.entities!!
        dataVersion = this@MutableEntity.dataVersion!!
    }
}

data class EntityEach(val nbt: CompoundTag) {
    val id = nbt["id"]!!.getAs<StringTag>().value
    val uuid = nbt["UUID"]!!.getAs<IntArrayTag>().value
    val brain = nbt["Brain"]?.getAs<CompoundTag>()?.let { EntityBrain(it) }
    val pos = nbt["Pos"]!!.getAs<ListTag>().value.map { it.getAs<DoubleTag>().value }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EntityEach) return false

        if (uuid.contentEquals(other.uuid)) return true

        return false
    }

    override fun hashCode(): Int {
        return uuid.contentHashCode()
    }

}

data class EntityBrain(private val nbt: CompoundTag) {
    val memories = EntityMemories(nbt["memories"]!!.getAs())
}

data class EntityMemories(private val nbt: CompoundTag) {
    var home: EntityMemoryValue?
        get() = nbt["minecraft:home"]?.getAs<CompoundTag>()?.let { EntityMemoryValue(it) }
        set(value) {
            if (value != null) return
            nbt.value.remove("minecraft:home")
        }
    var jobSite: EntityMemoryValue?
        get() = nbt["minecraft:job_site"]?.getAs<CompoundTag>()?.let { EntityMemoryValue(it) }
        set(value) {
            if (value != null) return
            nbt.value.remove("minecraft:job_site")
        }

    var meetingPoint: EntityMemoryValue?
        get() = nbt["minecraft:meeting_point"]?.getAs<CompoundTag>()?.let { EntityMemoryValue(it) }
        set(value) {
            if (value != null) return
            nbt.value.remove("minecraft:meeting_point")
        }
}

data class EntityMemoryValue(private val nbt: CompoundTag) {
    private val value = nbt["value"]!!.getAs<CompoundTag>()
    val pos = value["pos"]!!.getAs<IntArrayTag>().value
    val dimension = value["dimension"]!!.getAs<StringTag>().value

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EntityMemoryValue) return false

        if (pos.contentEquals(other.pos) && dimension == other.dimension) return true

        return false
    }

    override fun hashCode(): Int {
        return super.hashCode()
    }
}

fun List<Chunk>.findChunk(x: Int, z: Int): Chunk? = find {
    it.nbt.value["xPos"]!!.getAs<IntTag>().value == x && it.nbt["zPos"]!!.getAs<IntTag>().value == z
}
