package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import kiwi.hoonkun.plugins.pixel.worker.PixelWorker
import org.bukkit.command.CommandSender
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.lib.PersonIdent

class CommitExecutor(private val plugin: Entry): Executor() {

    companion object {

        val COMPLETE_LIST = mutableListOf("<commit_message>")

    }

    override suspend fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        val repo = Entry.repository ?: return invalidRepositoryResult

        if (args.isEmpty())
            return CommandExecuteResult(false, "missing arguments. commit target and messages are must be specified.")

        if (args.size == 1)
            return CommandExecuteResult(false, "missing arguments. commit message must be specified.")

        val head = repo.refDatabase.findRef("HEAD")
        if (head.target.name == "HEAD")
            return CommandExecuteResult(false, "it seems that head is detached from any other branches.\nplease create new branch here, before commit.")

        try {
            PixelWorker.addToVersionControl(plugin, dimensions(args[0]))
        } catch (exception: UnknownDimensionException) {
            return createDimensionExceptionResult(exception)
        }

        val git = Git(repo)

        return try {
            git.add()
                .addFilepattern(".")
                .call()

            val commit = git.commit()
                .setMessage(args.slice(1 until args.size).joinToString(" "))
                .setCommitter(PersonIdent(repo))
                .call()

            CommandExecuteResult(true, "${g}successfully committed with hash '$w${commit.name.substring(0 until 7)}$g'")
        } catch (exception: GitAPIException) {
            return createGitApiFailedResult("commit", exception)
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