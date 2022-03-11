package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import kiwi.hoonkun.plugins.pixel.utils.BranchUtils.Companion.findIndexes
import kiwi.hoonkun.plugins.pixel.utils.TextWidthUtils.Companion.ellipsizeChat
import org.bukkit.ChatColor
import org.bukkit.command.CommandSender
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.api.errors.NoHeadException
import java.text.SimpleDateFormat
import java.util.*

class ListExecutor(parent: Entry): Executor(parent) {

    companion object {

        val FIRST_ARGS_LIST = mutableListOf("commits", "branches")

        val RESULT_NO_TARGET_WORLD =
            CommandExecuteResult(false, "argument is missing. target world is must be specified.")

        val RESULT_INVALID_LIST_TYPE =
            CommandExecuteResult(false, "invalid argument. only 'commits' and 'branches' are allowed to list up.")

        val RESULT_INVALID_PAGE =
            CommandExecuteResult(false, "invalid argument. page argument must be integer.")

    }

    override val usage: String = "list < what > < world > [ page ]"
    override val description: String = "list up commits of current branch or branches of given world's repository."

    private val pageSize = 9

    override suspend fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        if (args.size == 1)
            return RESULT_NO_TARGET_WORLD

        if (!isValidWorld(args[1]))
            return createUnknownWorldResult(args[1])

        val repo = parent.repositories[args[1]] ?: return RESULT_REPOSITORY_NOT_INITIALIZED

        val what = args[0]
        if (what != "commits" && what != "branches")
            return RESULT_INVALID_LIST_TYPE

        val page = if (args.size == 3) {
            args[2].toIntOrNull() ?: return RESULT_INVALID_PAGE
        } else {
            0
        }

        val git = Git(repo)

        val lists = when (what) {
            "commits" -> printCommits(git, page)
            "branches" -> printBranches(git)
            else -> ""
        }

        return CommandExecuteResult(true, lists, false)
    }

    private fun printCommits(git: Git, page: Int = 0): String {
        val noCommitsMessage = "${g}there are no commits in this repository yet."

        val commits = try {
            git.log().call().toList()
        } catch (e: NoHeadException) {
            return noCommitsMessage
        }

        if (commits.isEmpty()) {
            return noCommitsMessage
        }

        val branch = git.repository.branch

        val start = page * pageSize + 1
        val end = ((page + 1) * pageSize).coerceAtMost(commits.size)
        val header = "$g[$w$start-$end of ${commits.size} commits ${g}in branch $w'$branch'$g]"
        val commitsString = commits.chunked(pageSize)[page].joinToString("\n") {
            val date = SimpleDateFormat("MM.dd HH:mm").format(Date(it.commitTime * 1000L))
            val hash = it.name.substring(0, 7)
            val msg = it.shortMessage
            "${ChatColor.YELLOW}$date ${ChatColor.GOLD}$hash ${ChatColor.WHITE}$msg".ellipsizeChat()
        }

        return "$header\n$commitsString"
    }

    private fun printBranches(git: Git): String {
        val branches = git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call()
        val list = branches.joinToString("\n") {
            val indexes = it.name.findIndexes('/')
            if (indexes.isEmpty()) it.name else it.name.substring(indexes[1] + 1, it.name.length)
        }

        return "$g[total $w${branches.size} ${g}branches]$w\n$list"
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return when (args.size) {
            1 -> FIRST_ARGS_LIST
            2 -> parent.repositoryKeys
            else -> ARGS_LIST_EMPTY
        }
    }

}