package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import org.bukkit.command.CommandSender
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.io.File

class InitializeExecutor: Executor() {

    override fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        val versionedFolder = Entry.versionedFolder!!
        if (!versionedFolder.exists()) versionedFolder.mkdirs()

        val repository = FileRepositoryBuilder.create(File("${versionedFolder.absolutePath}/.git"))
        repository.create()

        Entry.repository = repository

        return CommandExecuteResult(true, "successfully init repository!")
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return mutableListOf()
    }

}