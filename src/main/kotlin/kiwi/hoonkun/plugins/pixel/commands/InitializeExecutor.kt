package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import org.bukkit.ChatColor
import org.bukkit.command.CommandSender
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.io.File

class InitializeExecutor: Executor() {

    companion object {
        val FIRST_ARGS_LIST = mutableListOf("< force >")
    }

    override suspend fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        if (!Entry.versionedFolder.exists()) Entry.versionedFolder.mkdirs()

        val gitDir = File("${Entry.versionedFolder.absolutePath}/.git")
        if (gitDir.exists() && args.isEmpty()) {
            return CommandExecuteResult(
                false,
                "local repository already exists! if you want to delete and recreate local repository, add 'force' argument."
            )
        } else if (gitDir.exists() && args[0] == "force") {
            gitDir.deleteRecursively()
        } else if (gitDir.exists()) {
            return CommandExecuteResult(false, "invalid argument '${args[0]}'")
        }

        val repository = FileRepositoryBuilder.create(gitDir)
        repository.create()

        Entry.repository = repository

        return CommandExecuteResult(true, "${ChatColor.DARK_GREEN}successfully init repository!")
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return when(args.size) {
            1 -> FIRST_ARGS_LIST
            else -> ARGS_LIST_EMPTY
        }
    }

}