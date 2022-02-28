package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import kiwi.hoonkun.plugins.pixel.worker.WriteWorker
import org.bukkit.command.CommandSender
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.lib.PersonIdent

class CommitExecutor(private val plugin: Entry): Executor() {

    companion object {

        val COMPLETE_LIST = mutableListOf("<commit_message>")

    }

    override fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        if (args.isEmpty())
            return CommandExecuteResult(false, "missing arguments. commit target and messages are must be specified.")

        if (args.size == 1)
            return CommandExecuteResult(false, "missing arguments. commit message must be specified.")

        val dimensions = if (args[0] == "all") listOf("overworld", "nether", "the_end") else listOf(args[0])

        val writeResult = WriteWorker.client2versioned(plugin, dimensions)

        if (writeResult != WriteWorker.RESULT_OK) return CommandExecuteResult(false, writeResult)

        val repo = Entry.repository ?: return invalidRepositoryResult

        val git = Git(repo)

        return try {
            git.add()
                .addFilepattern(".")
                .call()

            val commit = git.commit()
                .setMessage(args.slice(1 until args.size).joinToString(" "))
                .setCommitter(PersonIdent(repo))
                .call()

            CommandExecuteResult(true, "successfully committed with hash '${commit.name}'")
        } catch (exception: GitAPIException) {
            createGitApiFailedResult(exception)
        }
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return when {
            args.size == 1 -> COMPLETE_LIST_DIMENSIONS
            args.size > 1 -> COMPLETE_LIST
            else -> COMPLETE_LIST_EMPTY
        }
    }

}