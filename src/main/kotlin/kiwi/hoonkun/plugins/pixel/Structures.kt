package kiwi.hoonkun.plugins.pixel

import kiwi.hoonkun.plugins.pixel.nbt.TagType
import kiwi.hoonkun.plugins.pixel.nbt.tag.*

import java.io.File

data class NBTLocation(val x: Int, val z: Int)
data class AnvilLocation(val x: Int, val z: Int)

typealias AnvilFiles = Array<File>
typealias Anvils = Map<AnvilLocation, ByteArray>
typealias NBT<T/* :NBTData */> = Map<AnvilLocation, List<T>>

typealias PackedBlocks = LongArray
typealias Blocks = List<Int>

enum class AnvilType(val path: String) {
    REGION("region"), POI("poi"), ENTITY("entity")
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

}

class Entity(timestamp: Int, nbt: CompoundTag): NBTData(timestamp, nbt) {
    private val position = nbt["Position"]!!.getAs<IntArrayTag>().value

    private val xPos = position[0]
    private val zPos = position[1]

    override val location: NBTLocation = NBTLocation(xPos, zPos)
}

fun List<Chunk>.findChunk(x: Int, z: Int): Chunk? = find {
    it.nbt.value["xPos"]!!.getAs<IntTag>().value == x && it.nbt["zPos"]!!.getAs<IntTag>().value == z
}
