package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import org.bukkit.command.CommandSender
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import java.text.SimpleDateFormat
import java.util.*

class ListExecutor: Executor() {

    override fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
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
        val branch = git.repository.fullBranch
        val commits = git.log().addPath(branch).call()
        val header = "[${page * 10 + 1}-${(page + 1) * 10} of ${commits.toList().size} commits in branch '$branch']"
        val commitsString = commits.chunked(10)[page].joinToString("\n") {
            "${SimpleDateFormat("yyyy.MM.dd HH:mm:ss").format(Date(it.commitTime * 1000L))}   ${it.name.substring(0, 8)}   ${it.shortMessage}"
        }

        val message = "$header\n$commitsString"

        if (sender != null) sender.sendMessage(message)
        else println(message)
    }

    private fun printBranches(git: Git, sender: CommandSender?) {
        val branches = git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call()
        val list = branches.chunked(5).joinToString("\n") {
            it.joinToString("") { ref -> ref.name.format("%10s") }
        }

        val message = "[total ${branches.size} branches]\n$list"

        if (sender != null) sender.sendMessage(message)
        else println(message)
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return mutableListOf()
    }

}