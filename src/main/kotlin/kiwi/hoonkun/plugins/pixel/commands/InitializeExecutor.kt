package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import org.bukkit.command.CommandSender
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.io.File

class InitializeExecutor(parent: Entry): Executor(parent) {

    companion object {
        val SECOND_ARGS_LIST = mutableListOf("true")
    }

    override val usage: String = "init < world > [ force ]"
    override val description: String = "initializes new repository of given world"

    override suspend fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        if (!Entry.versionedFolder.exists()) Entry.versionedFolder.mkdirs()

        val world = args[0]

        if (!isValidWorld(world) && world != "all")
            return createUnknownWorldResult(world)

        val targets = worldsWithAll(args[0], false).toMutableList()
        val skipped = mutableListOf<String>()

        targets.forEach {

            val gitDir = File("${Entry.versionedFolder.absolutePath}/$it/.git")
            if (gitDir.exists() && args.size == 1) {
                skipped.add(it)
                return@forEach
            } else if (gitDir.exists() && args[1] == "true") {
                gitDir.deleteRecursively()
            } else if (gitDir.exists()) {
                return CommandExecuteResult(false, "invalid <force> argument '${args[0]}'")
            }

            val repository = FileRepositoryBuilder.create(gitDir)
            repository.create()

            parent.repositories[it] = repository

        }

        targets.removeAll(skipped)

        val skippedMessage = "${r}repositories [${skipped.joinToString(", ")}] are already exists! if you want to delete and recreate repository, add 'true' to force argument."

        if (targets.size == 0)
            return CommandExecuteResult(false, skippedMessage)
        else if (skipped.size != 0)
            sender?.sendMessage(skippedMessage)

        return CommandExecuteResult(true, "${g}successfully init repository of worlds [$w${targets.joinToString("$g, $w")}$g] !!")
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return when(args.size) {
            1 -> parent.availableWorldNames.apply { add("all") }
            2 -> SECOND_ARGS_LIST
            else -> ARGS_LIST_EMPTY
        }
    }

}