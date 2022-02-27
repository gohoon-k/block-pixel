package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import org.bukkit.command.CommandSender
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException

class BranchExecutor: Executor() {

    override fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        if (args.isEmpty())
            return CommandExecuteResult(false, "argument is missing, branch name must be specified.")

        val repo = Entry.repository ?: return invalidRepositoryResult

        try {
            Git(repo).checkout()
                .addPath(args[0])
                .setCreateBranch(true)
                .call()
        } catch (exception: GitAPIException) {
            return createGitApiFailedResult(exception)
        }

        return CommandExecuteResult(true, "successfully created new branch '${args[0]}'")
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return mutableListOf()
    }

}