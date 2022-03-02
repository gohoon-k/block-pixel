import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.StringSpec
import kiwi.hoonkun.plugins.pixel.ClientRegionFiles
import kiwi.hoonkun.plugins.pixel.RegionLocation
import kiwi.hoonkun.plugins.pixel.Regions
import kiwi.hoonkun.plugins.pixel.findChunk
import kiwi.hoonkun.plugins.pixel.nbt.tag.IntTag
import kiwi.hoonkun.plugins.pixel.worker.RegionWorker.Companion.readClientRegions
import kiwi.hoonkun.plugins.pixel.worker.RegionWorker.Companion.toClientRegions
import kiwi.hoonkun.plugins.pixel.worker.RegionWorker.Companion.toRegions
import java.io.File

class RegionWorkerTest: StringSpec() {

    init {

        val testRegionLocation = RegionLocation(0, 0)

        val uri = this::class.java.classLoader.getResource("r.0.0.mca")?.toURI() ?: throw Exception("cannot find region file.")
        val regionFile = File(uri)
        val testRegions = arrayOf(regionFile)

        "convert between region and client region" {
            val regions1 = ClientRegionFiles(testRegions).readClientRegions().toRegions()

            val version1 = getDataVersion(regions1)

            version1 shouldNotBe null

            val clientRegions = regions1.toClientRegions()

            clientRegions.get[testRegionLocation]!!.size % 4096 shouldBe 0

            val regions2 = clientRegions.toRegions()

            val version2 = getDataVersion(regions2)

            version2 shouldNotBe null

            version2 shouldBe version1
        }

    }

    private fun getDataVersion(from: Regions): Int {
        return from.get[RegionLocation(0, 0)]!!
            .findChunk(0, 0)!!
            .nbt["DataVersion"]!!.getAs<IntTag>().value
    }

}