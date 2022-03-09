import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

import kiwi.hoonkun.plugins.pixel.AnvilLocation
import kiwi.hoonkun.plugins.pixel.Terrain
import kiwi.hoonkun.plugins.pixel.nbt.tag.CompoundTag
import kiwi.hoonkun.plugins.pixel.nbt.tag.ListTag
import kiwi.hoonkun.plugins.pixel.nbt.tag.LongArrayTag
import kiwi.hoonkun.plugins.pixel.worker.MinecraftAnvilWorker.Companion.read
import kiwi.hoonkun.plugins.pixel.worker.MinecraftAnvilWorker.Companion.toAnvil
import kiwi.hoonkun.plugins.pixel.worker.PaletteWorker.Companion.pack
import kiwi.hoonkun.plugins.pixel.worker.PaletteWorker.Companion.unpack

import java.io.File

class PaletteWorkerTest: StringSpec() {

    init {

        val testRegionLocation = AnvilLocation(0, 0)

        val uri = this::class.java.classLoader.getResource("r.0.0.mca")?.toURI() ?: throw Exception("cannot find region file.")
        val regionFile = File(uri)
        val testAnvilFiles = arrayOf(regionFile)

        val chunks = testAnvilFiles.read().toAnvil { _, timestamp, nbt -> Terrain(timestamp, nbt) }[testRegionLocation]!!

        "unpack and pack block_states.data" {

            chunks.map { chunk -> chunk.nbt["sections"]!!.getAs<ListTag>().value }
                .flatten()
                .forEach { tag ->
                    val blockStates = tag.getAs<CompoundTag>()["block_states"]!!.getAs<CompoundTag>()

                    val data = blockStates["data"]?.getAs<LongArrayTag>() ?: return@forEach

                    val paletteSize = blockStates["palette"]!!.getAs<ListTag>().value.size

                    val unpacked1 = data.value.unpack(paletteSize)

                    (unpacked1.size == 4096 || unpacked1.isEmpty()) shouldBe true
                    unpacked1.filter { it >= paletteSize }.size shouldBe 0

                    val packed1 = unpacked1.pack(paletteSize)

                    packed1 shouldBe data.value

                    val unpacked2 = packed1.unpack(paletteSize)

                    unpacked2 shouldBe unpacked1
                }

        }

    }

}