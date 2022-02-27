package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import org.bukkit.command.CommandSender

class InitializeExecutor: Executor() {

    override fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        val versionedFolder = Entry.versionedFolder!!
        if (!versionedFolder.exists()) versionedFolder.mkdirs()

        return spawn(listOf("git", "init"), versionedFolder)
            .handle(
                "successfully initialized local git repository",
                "failed to initialize local git repository. check out the generated log file."
            )
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return mutableListOf()
    }

}