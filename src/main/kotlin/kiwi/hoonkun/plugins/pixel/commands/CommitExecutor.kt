package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import kiwi.hoonkun.plugins.pixel.worker.WriteWorker
import org.bukkit.command.CommandSender

class CommitExecutor: Executor() {

    override fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        if (args.isEmpty()) {
            return CommandExecuteResult(false, "cannot commit if message is not specified.")
        }

        WriteWorker.client2versioned(listOf("overworld", "nether", "the_end"))

        val add = spawn(listOf("git", "add", "."), Entry.versionedFolder!!)
            .handle(
                "<UNUSED_MESSAGE>",
                "failed to add region files to vcs. aborting..."
            )

        if (!add.success) return add

        return spawn(listOf("git", "commit", "-m", args.joinToString(" ")), Entry.versionedFolder!!)
            .handle(
                "successfully committed. to find out commit hash, use /pixel list commits.",
                "failed to commit. check out the generated log file."
            )
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return mutableListOf()
    }

}