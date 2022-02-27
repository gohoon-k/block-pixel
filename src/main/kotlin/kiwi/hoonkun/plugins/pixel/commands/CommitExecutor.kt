package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import kiwi.hoonkun.plugins.pixel.worker.WriteWorker
import org.bukkit.command.CommandSender

class CommitExecutor: Executor() {

    override fun exec(sender: CommandSender?, args: List<String>): Boolean {
        if (args.isEmpty()) {
            return returnMessage(sender, "cannot commit if message is not specified.")
        }

        WriteWorker.client2versioned(listOf("overworld", "nether", "the_end"))

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

    override fun autoComplete(args: List<String>): MutableList<String> {
        return mutableListOf()
    }

}