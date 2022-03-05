package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import org.bukkit.command.CommandSender
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException

class BranchExecutor: Executor() {

    companion object {

        val COMPLETE_LIST_0 = mutableListOf("<branch_name>", "-d")
        val COMPLETE_LIST_1 = mutableListOf("<branch_name>")

    }

    override suspend fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        val repo = Entry.repository ?: return invalidRepositoryResult

        if (args.isEmpty()) {
            return CommandExecuteResult(true, "${g}you are currently in '$w${repo.branch}$g' branch", false)
        }

        if (args[0] == "-d") {
            if (args.size == 1) return CommandExecuteResult(false, "missing arguments. delete target branch must be specified.")

            val target = args[1]

            try {
                Git(repo).branchDelete()
                    .setBranchNames(target)
                    .setForce(true)
                    .call()
            } catch (e: GitAPIException) {
                return createGitApiFailedResult("delete branch", e)
            }

            return CommandExecuteResult(true, "${g}successfully deleted branch '$w${target}$g'", false)
        }

        try {
            Git(repo).checkout()
                .setName(args[0])
                .setCreateBranch(true)
                .call()
        } catch (exception: GitAPIException) {
            return createGitApiFailedResult("branch", exception)
        }

        return CommandExecuteResult(true, "${g}successfully created new branch '$w${args[0]}$g'", false)
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return when (args.size) {
            1 -> COMPLETE_LIST_0
            2 -> if (args[0] == "-d") COMPLETE_LIST_1 else COMPLETE_LIST_EMPTY
            else -> COMPLETE_LIST_EMPTY
        }
    }

}