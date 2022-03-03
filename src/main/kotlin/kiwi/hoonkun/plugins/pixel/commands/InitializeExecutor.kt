package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import org.bukkit.ChatColor
import org.bukkit.command.CommandSender
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.io.File

class InitializeExecutor: Executor() {

    override suspend fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        if (!Entry.versionedFolder.exists()) Entry.versionedFolder.mkdirs()

        val repository = FileRepositoryBuilder.create(File("${Entry.versionedFolder.absolutePath}/.git"))
        repository.create()

        Entry.repository = repository

        return CommandExecuteResult(true, "${ChatColor.DARK_GREEN}successfully init repository!")
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return mutableListOf()
    }

}