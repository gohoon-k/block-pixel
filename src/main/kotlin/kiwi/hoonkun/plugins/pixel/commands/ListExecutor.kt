package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import kiwi.hoonkun.plugins.pixel.utils.TextWidthUtils.Companion.ellipsizeChat
import org.bukkit.ChatColor
import org.bukkit.command.CommandSender
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.api.errors.NoHeadException
import java.text.SimpleDateFormat
import java.util.*

class ListExecutor: Executor() {

    companion object {

        val COMPLETE_LIST_0 = mutableListOf("commits", "branches")
        val COMPLETE_LIST_1_WHEN_COMMIT = mutableListOf("<page>")

    }

    private val pageSize = 9

    override suspend fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        val repo = Entry.repository ?: return invalidRepositoryResult

        if (args.isEmpty())
            return CommandExecuteResult(false, "argument is missing. please specify what to list up.")

        val what = args[0]
        if (what != "commits" && what != "branches")
            return CommandExecuteResult(false, "invalid argument. only 'commits' and 'branches' are allowed to list up.")

        val page = if (args.size == 2) {
            args[1].toIntOrNull() ?: return CommandExecuteResult(false, "invalid argument. page argument must be integer.")
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

    private fun String.findIndexes(char: Char): List<Int> {
        val result = mutableListOf<Int>()
        var index = 0
        split(char).forEach {
            index += it.length
            result.add(index)
        }
        return result
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return when (args.size) {
            1 -> COMPLETE_LIST_0
            2 -> if (args[0] == "commits") COMPLETE_LIST_1_WHEN_COMMIT else COMPLETE_LIST_EMPTY
            else -> COMPLETE_LIST_EMPTY
        }
    }

}