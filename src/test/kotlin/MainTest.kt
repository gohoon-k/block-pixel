import io.kotlintest.specs.StringSpec
import kiwi.hoonkun.plugins.pixel.ClientRegionFiles
import kiwi.hoonkun.plugins.pixel.VersionedRegionFiles

import kiwi.hoonkun.plugins.pixel.worker.RegionWorker.Companion.readVersionedRegions
import kiwi.hoonkun.plugins.pixel.worker.RegionWorker.Companion.toVersionedRegions
import kiwi.hoonkun.plugins.pixel.worker.RegionWorker.Companion.readClientRegions
import kiwi.hoonkun.plugins.pixel.worker.RegionWorker.Companion.toClientRegions

import java.io.File

class MainTest: StringSpec() {

    init {

        val workingDirectory = "../_test"
        val originalPath = "$workingDirectory/original"
        val versionedPath = "$workingDirectory/versioned"
        val clientPath = "$workingDirectory/client"

        val playableFiles = File(originalPath).listFiles() ?: throw Exception("cannot find original data directory")

        val versionedFiles = File(versionedPath).listFiles() ?: throw Exception("cannot find versioned data directory")

        "convert client regions to versioned regions, and save into file" {

            val git = ClientRegionFiles(playableFiles).readClientRegions().toVersionedRegions()

            git.get.entries.forEach { (location, data) ->
                val dataFile = File("$versionedPath/r.${location.x}.${location.z}.v.mca")
                val typesFile = File("$versionedPath/r.${location.x}.${location.z}.t.mca")

                if (dataFile.exists()) dataFile.delete()
                if (typesFile.exists()) typesFile.delete()

                dataFile.createNewFile()
                typesFile.createNewFile()

                dataFile.writeBytes(data.data.toByteArray())
                typesFile.writeBytes(data.types.toByteArray())
            }

        }

        "convert versioned regions to client regions, and save into file" {

            val playable = VersionedRegionFiles(versionedFiles).readVersionedRegions().toClientRegions(ClientRegionFiles(playableFiles))

            playable.get.entries.forEach { (location, data) ->
                val regionFile = File("$clientPath/r.${location.x}.${location.z}.mca")

                if (regionFile.exists()) regionFile.delete()

                regionFile.createNewFile()

                regionFile.writeBytes(data)
            }

        }

    }

}