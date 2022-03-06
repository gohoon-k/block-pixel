import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.StringSpec
import kiwi.hoonkun.plugins.pixel.*
import kiwi.hoonkun.plugins.pixel.nbt.tag.IntTag
import kiwi.hoonkun.plugins.pixel.worker.MinecraftAnvilWorker.Companion.read
import kiwi.hoonkun.plugins.pixel.worker.MinecraftAnvilWorker.Companion.toAnvilFormat
import kiwi.hoonkun.plugins.pixel.worker.MinecraftAnvilWorker.Companion.toNBT
import java.io.File

class RegionWorkerTest: StringSpec() {

    init {

        val testRegionLocation = AnvilLocation(0, 0)

        val uri = this::class.java.classLoader.getResource("r.0.0.mca")?.toURI() ?: throw Exception("cannot find region file.")
        val regionFile = File(uri)
        val testAnvilFiles = arrayOf(regionFile)

        "convert between region and client region" {
            val regions1 = testAnvilFiles.read().toNBT { timestamp, nbt -> Chunk(timestamp, nbt) }

            val version1 = getDataVersion(regions1)

            version1 shouldNotBe null

            val clientRegions = regions1.toAnvilFormat()

            clientRegions[testRegionLocation]!!.size % 4096 shouldBe 0

            val regions2 = clientRegions.toNBT { timestamp, nbt -> Chunk(timestamp, nbt) }

            val version2 = getDataVersion(regions2)

            version2 shouldNotBe null

            version2 shouldBe version1
        }

    }

    private fun getDataVersion(from: NBT<Chunk>): Int {
        return from[AnvilLocation(0, 0)]!!
            .findChunk(0, 0)!!
            .nbt["DataVersion"]!!.getAs<IntTag>().value
    }

}