package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import org.bukkit.command.CommandSender
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException

class BranchExecutor(parent: Entry): Executor(parent) {

    companion object {

        val SECOND_ARGS_LIST = mutableListOf("< new_branch_name >", "-d")
        val THIRD_ARGS_LIST = mutableListOf("< existing_branch_name >")

        val RESULT_NO_TARGET_BRANCH_TO_DELETE =
            CommandExecuteResult(false, "missing arguments. delete target branch must be specified.")

    }

    override val usage: String = "branch [ new_branch_name | \"-d\" ] [ existing_branch_name ]"
    override val description: String = "creates new branch to given world's repository."

    override suspend fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        val targetWorld = args[0]

        if (!isValidWorld(targetWorld))
            return createUnknownWorldResult(targetWorld)

        val repo = parent.repositories[targetWorld] ?: return RESULT_REPOSITORY_NOT_INITIALIZED

        if (args.size == 1)
            return CommandExecuteResult(true, "${g}you are currently in '$w${repo.branch}$g' branch in world '$targetWorld' repository.", false)

        if (args[1] == "-d") {
            if (args.size == 2) return RESULT_NO_TARGET_BRANCH_TO_DELETE

            val target = args[2]

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

        val target = args[1]

        try {
            Git(repo).checkout()
                .setName(target)
                .setCreateBranch(true)
                .call()
        } catch (exception: GitAPIException) {
            return createGitApiFailedResult("branch", exception)
        }

        return CommandExecuteResult(true, "${g}successfully created new branch '$w${target}$g'", false)
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return when (args.size) {
            1 -> parent.repositoryKeys
            2 -> SECOND_ARGS_LIST
            3 -> if (args[1] == "-d") THIRD_ARGS_LIST else ARGS_LIST_EMPTY
            else -> ARGS_LIST_EMPTY
        }
    }

}