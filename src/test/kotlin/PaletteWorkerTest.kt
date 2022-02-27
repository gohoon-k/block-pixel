import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import kiwi.hoonkun.plugins.pixel.ClientRegionFiles
import kiwi.hoonkun.plugins.pixel.RegionLocation
import kiwi.hoonkun.plugins.pixel.nbt.tag.CompoundTag
import kiwi.hoonkun.plugins.pixel.nbt.tag.ListTag
import kiwi.hoonkun.plugins.pixel.nbt.tag.LongArrayTag
import kiwi.hoonkun.plugins.pixel.worker.PaletteWorker.Companion.pack
import kiwi.hoonkun.plugins.pixel.worker.PaletteWorker.Companion.unpack
import kiwi.hoonkun.plugins.pixel.worker.RegionWorker.Companion.readClientRegions
import kiwi.hoonkun.plugins.pixel.worker.RegionWorker.Companion.toRegions
import java.io.File

class PaletteWorkerTest: StringSpec() {

    init {

        val testRegionLocation = RegionLocation(0, 0)

        val uri = this::class.java.classLoader.getResource("r.0.0.mca")?.toURI() ?: throw Exception("cannot find region file.")
        val regionFile = File(uri)
        val testRegions = arrayOf(regionFile)

        val chunks = ClientRegionFiles(testRegions).readClientRegions().toRegions().get[testRegionLocation]!!

        "unpack and pack block_states.data" {

            chunks.map { chunk -> chunk.nbt["sections"]!!.getAs<ListTag>().value }
                .flatten()
                .forEach { tag ->
                    val blockStates = tag.getAs<CompoundTag>()["block_states"]!!.getAs<CompoundTag>()

                    val data = blockStates["data"]?.getAs<LongArrayTag>() ?: return@forEach

                    val paletteSize = blockStates["palette"]!!.getAs<ListTag>().value.size

                    val unpacked1 = data.value.unpack(paletteSize)

                    unpacked1.size shouldBe 4096
                    unpacked1.filter { it >= paletteSize }.size shouldBe 0

                    val packed1 = unpacked1.pack(paletteSize)

                    packed1 shouldBe data.value

                    val unpacked2 = packed1.unpack(paletteSize)

                    unpacked2 shouldBe unpacked1
                }

        }

    }

}