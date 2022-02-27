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
import kiwi.hoonkun.plugins.pixel.worker.RegionWorker.Companion.toVersionedRegions
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

        "convert between region and versioned region" {
            val regions1 = ClientRegionFiles(testRegions).readClientRegions().toRegions()

            val versionedRegions1 = regions1.toVersionedRegions()

            val regions2 = versionedRegions1.toRegions(ClientRegionFiles(testRegions))

            val versionedRegions2 = regions2.toVersionedRegions()

            versionedRegions2.get[testRegionLocation]!!.data shouldBe versionedRegions1.get[testRegionLocation]!!.data
        }

        "convert between client region and versioned region" {
            val regions = ClientRegionFiles(testRegions).readClientRegions().toRegions()

            val regions1 = Regions(mapOf(testRegionLocation to listOf(regions.get[testRegionLocation]!![0])))

            val client1 = regions1.toClientRegions()
            val versioned1 = client1.toVersionedRegions()
            val client2 = versioned1.toClientRegions(ClientRegionFiles(testRegions))
            val versioned2 = client2.toVersionedRegions()
            val client3 = versioned2.toClientRegions(ClientRegionFiles(testRegions))
            val versioned3 = client3.toVersionedRegions()

            versioned1.get[testRegionLocation]!!.data shouldBe versioned3.get[testRegionLocation]!!.data
            versioned1.get[testRegionLocation]!!.data shouldBe versioned2.get[testRegionLocation]!!.data
            versioned2.get[testRegionLocation]!!.data shouldBe versioned3.get[testRegionLocation]!!.data
            
            client2.get[testRegionLocation] shouldBe client3.get[testRegionLocation]
        }

    }

    private fun getDataVersion(from: Regions): Int {
        return from.get[RegionLocation(0, 0)]!!
            .findChunk(0, 0)!!
            .nbt["DataVersion"]!!.getAs<IntTag>().value
    }

}