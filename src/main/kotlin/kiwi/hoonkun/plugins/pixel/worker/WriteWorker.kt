package kiwi.hoonkun.plugins.pixel.worker

import kiwi.hoonkun.plugins.pixel.*
import kiwi.hoonkun.plugins.pixel.worker.RegionWorker.Companion.readClientRegions
import kiwi.hoonkun.plugins.pixel.worker.RegionWorker.Companion.readVersionedRegions
import kiwi.hoonkun.plugins.pixel.worker.RegionWorker.Companion.toClientRegions
import kiwi.hoonkun.plugins.pixel.worker.RegionWorker.Companion.toVersionedRegions
import java.io.File

class WriteWorker {

    companion object {

        private val clientDimensions get() = mapOf(
            "overworld" to "${Entry.clientFolder.absolutePath}/${Entry.levelName}/region",
            "nether" to "${Entry.clientFolder.absolutePath}/${Entry.levelName}_nether/DIM-1/region",
            "the_end" to "${Entry.clientFolder.absolutePath}/${Entry.levelName}_the_end/DIM1/region"
        )

        fun client2versioned(dimensions: List<String>): Boolean {
            dimensions.forEach { dimension ->
                val path = clientDimensions[dimension] ?: return false
                val regions = File(path).listFiles() ?: return false
                val versioned = ClientRegionFiles(regions).readClientRegions().toVersionedRegions()
                saveVersioned(dimension, versioned)
            }

            return true
        }

        fun versioned2client(dimensions: List<String>): Boolean {
            val versionedPath = Entry.versionedFolder.absolutePath

            dimensions.forEach { dimension ->
                val clientDimension = clientDimensions[dimension] ?: return false
                val versioned = File("$versionedPath/$dimension").listFiles() ?: return false
                val original = File(clientDimension).listFiles() ?: return false
                val client = VersionedRegionFiles(versioned)
                    .readVersionedRegions()
                    .toClientRegions(ClientRegionFiles(original))

                replaceClient(clientDimension, client)
            }

            return true
        }

        private fun saveVersioned(dimension: String, versioned: VersionedRegions) {
            versioned.get.entries.forEach { (location, region) ->
                val outputDirectory = File("${Entry.versionedFolder.absolutePath}/$dimension")
                if (!outputDirectory.exists()) outputDirectory.mkdirs()

                val outputDataFile = File("${outputDirectory.absolutePath}/r.${location.x}.${location.z}.mca.d")
                val outputTypesFile = File("${outputDirectory.absolutePath}/r.${location.x}.${location.z}.mca.t")

                outputDataFile.writeBytes(region.data.toByteArray())
                outputTypesFile.writeBytes(region.types.toByteArray())
            }
        }

        private fun replaceClient(path: String, regions: ClientRegions) {
            regions.get.entries.forEach { (location, bytes) ->
                val outputDirectory = File(path)

                val outputFile = File("${outputDirectory.absolutePath}/r.${location.x}.${location.z}.mca")

                outputFile.writeBytes(bytes)
            }
        }

    }

}