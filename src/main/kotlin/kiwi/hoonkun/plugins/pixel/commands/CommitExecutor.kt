package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import kiwi.hoonkun.plugins.pixel.worker.WriteWorker
import org.bukkit.command.CommandSender
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.lib.PersonIdent

class CommitExecutor(private val plugin: Entry): Executor() {

    companion object {

        val COMPLETE_LIST_0 = mutableListOf("<commit_message>")

    }

    override fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        if (args.isEmpty())
            return CommandExecuteResult(false, "cannot commit if message is not specified.")

        val writeResult = WriteWorker.client2versioned(plugin, listOf("overworld", "nether", "the_end"))

        if (writeResult != WriteWorker.RESULT_OK) return CommandExecuteResult(false, writeResult)

        val repo = Entry.repository ?: return invalidRepositoryResult

        val git = Git(repo)

        return try {
            git.add()
                .addFilepattern(".")
                .call()

            val commit = git.commit()
                .setMessage(args.joinToString(" "))
                .setCommitter(PersonIdent(repo))
                .call()

            CommandExecuteResult(true, "successfully committed with hash '${commit.name}'")
        } catch (exception: GitAPIException) {
            createGitApiFailedResult(exception)
        }
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return when (args.size) {
            1 -> COMPLETE_LIST_0
            else -> COMPLETE_LIST_EMPTY
        }
    }

}