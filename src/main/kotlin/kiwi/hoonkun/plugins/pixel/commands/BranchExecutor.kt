package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import org.bukkit.command.CommandSender
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.lib.Repository

class BranchExecutor(parent: Entry): Executor(parent) {

    companion object {

        val SECOND_ARGS_LIST = mutableListOf("-d")

    }

    override val usage: String = "branch < world > < new_branch | \"-d\" > [ branch_to_delete ]"
    override val description: String = "creates new branch to given world's repository.\nif \"-d\" is set, 'branch_to_delete' must be specified."

    override suspend fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        val targetWorld = args[0]

        if (!isValidWorld(targetWorld))
            return createUnknownWorldResult(targetWorld)

        val repo = parent.repositories[targetWorld] ?: return RESULT_REPOSITORY_NOT_INITIALIZED

        if (args.size == 1)
            return CommandExecuteResult(true, "${g}you are currently in '$w${repo.branch}$g' branch in world '$targetWorld' repository.", false)

        val second = args[1]

        if (second == "-d")
            return deleteBranch(args, repo)

        return createBranch(args, repo)
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return when (args.size) {
            1 -> parent.repositoryKeys
            2 -> SECOND_ARGS_LIST
            3 -> {
                if (args[1] == "-d")
                    parent.branches[args[0]]
                        ?.toMutableList()
                        ?.apply { remove(parent.branch[args[0]]) }
                            ?: mutableListOf()
                else
                    ARGS_LIST_EMPTY
            }
            else -> ARGS_LIST_EMPTY
        }
    }

    private fun createBranch(args: List<String>, repo: Repository): CommandExecuteResult {
        val target = args[1]

        try {
            Git(repo).checkout()
                .setName(target)
                .setCreateBranch(true)
                .call()

            parent.updateBranches()
            parent.updateBranch()
        } catch (exception: GitAPIException) {
            return createGitApiFailedResult("branch", exception)
        }

        return CommandExecuteResult(true, "${g}successfully created new branch '$w${target}$g'", false)
    }

    private fun deleteBranch(args: List<String>, repo: Repository): CommandExecuteResult {
        if (args.size == 2)
            return createNotEnoughArgumentsResult(listOf(3), args.size)

        val toDelete = args[2]

        try {
            Git(repo).branchDelete()
                .setBranchNames(toDelete)
                .setForce(true)
                .call()
        } catch (e: GitAPIException) {
            return createGitApiFailedResult("delete branch", e)
        }

        parent.updateBranches()

        return CommandExecuteResult(true, "${g}successfully deleted branch '$w${toDelete}$g'", false)
    }

}