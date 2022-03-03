package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import org.bukkit.command.CommandSender
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import java.text.SimpleDateFormat
import java.util.*

class ListExecutor: Executor() {

    companion object {

        val COMPLETE_LIST_0 = mutableListOf("commits", "branches")
        val COMPLETE_LIST_1_WHEN_COMMIT = mutableListOf("<page>")

    }

    private val pageSize = 8

    override suspend fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        if (args.isEmpty())
            return CommandExecuteResult(false, "argument is missing. please specify what to list up.")

        val what = args[0]
        if (what != "commits" && what != "branches")
            return CommandExecuteResult(false, "invalid argument. only 'commits' and 'branches' are allowed to list up.")

        val page = if (args.size == 2) {
            args[0].toIntOrNull() ?: return CommandExecuteResult(false, "invalid argument. page argument must be integer.")
        } else {
            0
        }

        val repo = Entry.repository ?: return invalidRepositoryResult
        val git = Git(repo)

        when (what) {
            "commits" -> printCommits(git, page, sender)
            "branches" -> printBranches(git, sender)
        }

        return CommandExecuteResult(true, "successfully queried list of '${args[0]}'")
    }

    private fun printCommits(git: Git, page: Int = 0, sender: CommandSender?) {
        val commits = git.log().call().toList()

        if (commits.isEmpty()) {
            "there are no commits in this repository yet.".also {
                if (sender != null) sender.sendMessage(it)
                else println(it)
            }
            return
        }

        val branch = git.repository.branch
        val header = "[${page * pageSize + 1}-${((page + 1) * pageSize).coerceAtMost(commits.size)} of ${commits.size} commits in branch '$branch']"
        val commitsString = commits.chunked(pageSize)[page].joinToString("\n") {
            "${SimpleDateFormat("yy.MM.dd HH:mm:ss").format(Date(it.commitTime * 1000L))}  ${it.name.substring(0, 7)}  ${it.shortMessage}"
        }

        val message = "$header\n$commitsString"

        if (sender != null) sender.sendMessage(message)
        else println(message)
    }

    private fun printBranches(git: Git, sender: CommandSender?) {
        val branches = git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call()
        val list = branches.joinToString("\n") {
            val indexes = it.name.findIndexes('/')
            it.name.substring(indexes[1] + 1, it.name.length)
        }

        val message = "[total ${branches.size} branches]\n$list"

        if (sender != null) sender.sendMessage(message)
        else println(message)
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