package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.ClientRegionFiles
import kiwi.hoonkun.plugins.pixel.Entry
import kiwi.hoonkun.plugins.pixel.VersionedRegions
import kiwi.hoonkun.plugins.pixel.worker.RegionWorker.Companion.readClientRegions
import kiwi.hoonkun.plugins.pixel.worker.RegionWorker.Companion.toVersionedRegions
import org.bukkit.command.CommandSender
import java.io.File

class CommitExecutor: Executor() {

    override fun exec(sender: CommandSender?, args: List<String>): Boolean {
        if (args.isEmpty()) {
            return returnMessage(sender, "cannot commit if message is not specified.")
        }

        val clientFolder = Entry.clientFolder!!
        val clientFolderPath = clientFolder.absolutePath

        val overworldRegions = File("$clientFolderPath/${Entry.levelName}/region").listFiles()
        val netherRegions = File("$clientFolderPath/${Entry.levelName}_nether/DIM-1/region").listFiles()
        val theEndRegions = File("$clientFolderPath/${Entry.levelName}_the_end/DIM1/region").listFiles()

        if (overworldRegions == null || netherRegions == null || theEndRegions == null) {
            return returnMessage(sender, "cannot find world directory.")
        }

        val overworldVersioned = ClientRegionFiles(overworldRegions).readClientRegions().toVersionedRegions()
        val netherVersioned = ClientRegionFiles(netherRegions).readClientRegions().toVersionedRegions()
        val theEndVersioned = ClientRegionFiles(theEndRegions).readClientRegions().toVersionedRegions()

        saveVersionedFile("overworld", overworldVersioned)
        saveVersionedFile("nether", netherVersioned)
        saveVersionedFile("the_end", theEndVersioned)

        val add = spawn(listOf("git", "add", "."), Entry.versionedFolder!!)
            .handle(
                sender,
                "pixel_commit_add",
                "successfully added region files to vcs. committing...",
                "failed to add region files to vcs. aborting..."
            )

        if (!add) return true

        spawn(listOf("git", "commit", "-m", args.joinToString(" ")), Entry.versionedFolder!!)
            .handle(
                sender,
                "pixel_commit",
                "successfully committed. to find out commit hash, use /pixel list commits.",
                "failed to commit. check out the generated log file."
            )

        return true
    }

    private fun saveVersionedFile(dimension: String, versioned: VersionedRegions) {
        versioned.get.entries.forEach { (location, region) ->
            val outputDirectory = File("${Entry.versionedFolder!!.absolutePath}/$dimension")
            if (!outputDirectory.exists()) outputDirectory.mkdirs()

            val outputDataFile = File("${outputDirectory.absolutePath}/r.${location.x}.${location.z}.mca.d")
            val outputTypesFile = File("${outputDirectory.absolutePath}/r.${location.x}.${location.z}.mca.t")

            outputDataFile.writeBytes(region.data.toByteArray())
            outputTypesFile.writeBytes(region.types.toByteArray())
        }
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return mutableListOf()
    }

}