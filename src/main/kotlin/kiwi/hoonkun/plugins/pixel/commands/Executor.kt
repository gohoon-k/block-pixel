package kiwi.hoonkun.plugins.pixel.commands

import org.bukkit.command.CommandSender
import org.eclipse.jgit.api.errors.GitAPIException

abstract class Executor {

    companion object {

        val COMPLETE_LIST_EMPTY = mutableListOf<String>()

        val COMPLETE_LIST_DIMENSIONS = mutableListOf("all", "overworld", "nether", "the_end")

    }

    data class CommandExecuteResult(
        val success: Boolean,
        val message: String
    )

    val invalidRepositoryResult = CommandExecuteResult(false, "repository is not initialized!\nplease run '/pixel init'.")

    fun createGitApiFailedResult(exception: GitAPIException) =
        CommandExecuteResult(false, "operation failed with exception: ${exception.message}")

    abstract fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult

    abstract fun autoComplete(args: List<String>): MutableList<String>

}