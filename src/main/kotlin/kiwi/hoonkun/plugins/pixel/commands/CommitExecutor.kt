package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import kiwi.hoonkun.plugins.pixel.worker.WriteWorker
import org.bukkit.command.CommandSender
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.lib.PersonIdent

class CommitExecutor: Executor() {

    override fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        if (args.isEmpty())
            return CommandExecuteResult(false, "cannot commit if message is not specified.")

        WriteWorker.client2versioned(listOf("overworld", "nether", "the_end"))

        val repo = Entry.repository ?: return invalidRepositoryResult

        val git = Git(repo)

        return try {
            git.add()
                .addFilepattern(".")
                .call()

            val commit = git.commit()
                .setMessage(args[0])
                .setCommitter(PersonIdent(repo))
                .call()

            CommandExecuteResult(true, "successfully committed with hash '${commit.name}'")
        } catch (exception: GitAPIException) {
            createGitApiFailedResult(exception)
        }
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return mutableListOf()
    }

}