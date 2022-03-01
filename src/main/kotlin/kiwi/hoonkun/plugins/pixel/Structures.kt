package kiwi.hoonkun.plugins.pixel

import kiwi.hoonkun.plugins.pixel.nbt.tag.CompoundTag
import kiwi.hoonkun.plugins.pixel.nbt.tag.IntTag

import java.io.File

data class RegionLocation(val x: Int, val z: Int)

@JvmInline
value class ClientRegionFiles(val get: Array<File>)

@JvmInline
value class VersionedRegionFiles(val get: Array<File>)


@JvmInline
value class ClientRegions(val get: Map<RegionLocation, ByteArray>)

@JvmInline
value class VersionedRegions(val get: Map<RegionLocation, VersionedRegion>)
data class VersionedRegion(val data: String, val types: String)
data class MutableVersionedRegion(var data: String = "", var types: String = "")
fun MutableVersionedRegion.toVersionedRegion() = VersionedRegion(data, types)

data class VersionedChunk(
    val X: Int,
    val Z: Int,
    val sections: List<VersionedSection>,
    val block_entities: List<CompoundTag>,
    val types: Map<String, Byte>
)
data class VersionedSection(
    val Y: Int,
    val palette: List<VersionedPalette>,
    val data: List<Int>
)
data class VersionedPalette(
    val Name: String,
    val Properties: CompoundTag?
)

@JvmInline
value class Regions(val get: Map<RegionLocation, List<Chunk>>)
typealias MutableRegions = MutableMap<RegionLocation, List<Chunk>>
fun Regions.toMutableRegions() = get.toMutableMap()
fun MutableRegions.toImmutableRegions() = Regions(toMap())
typealias BlocksRaw = LongArray
typealias Blocks = List<Int>

data class Chunk(val timestamp: Int, val nbt: CompoundTag)
fun List<Chunk>.findChunk(x: Int, z: Int): Chunk? = find {
    it.nbt.value["xPos"]!!.getAs<IntTag>().value == x && it.nbt["zPos"]!!.getAs<IntTag>().value == z
}
