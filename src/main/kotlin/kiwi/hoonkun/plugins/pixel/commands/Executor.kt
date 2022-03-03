package kiwi.hoonkun.plugins.pixel.commands

import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.ChatColor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.eclipse.jgit.api.errors.GitAPIException

abstract class Executor {

    companion object {

        val COMPLETE_LIST_EMPTY = mutableListOf<String>()

        val COMPLETE_LIST_DIMENSIONS = mutableListOf("all", "overworld", "nether", "the_end")

        val COMPLETE_LIST_COMMITTED = mutableListOf("<all_change_committed>")

        private var globalSender: CommandSender? = null

        fun sendTitle(message: String) {
            val localSender = globalSender ?: return
            if (localSender is Player) {
                localSender.spigot().sendMessage(ChatMessageType.ACTION_BAR, *TextComponent.fromLegacyText(message))
            }
        }

        val g = ChatColor.GRAY
        val w = ChatColor.WHITE

    }

    data class CommandExecuteResult(
        val success: Boolean,
        val message: String
    )

    val invalidRepositoryResult = CommandExecuteResult(false, "repository is not initialized!\nplease run '/pixel init'.")
    val uncommittedChangesResult = CommandExecuteResult(false, "you specified that you didn't committed changes. please commit them first.")

    fun createGitApiFailedResult(operation: String, exception: GitAPIException) =
        CommandExecuteResult(false, "failed to $operation because of exception\n${exception.message}")

    fun dimensions(arg: String): List<String> = if (arg == "all") listOf("overworld", "nether", "the_end") else listOf(arg)

    suspend fun doIt(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        globalSender = sender
        val result = exec(sender, args)
        globalSender = null

        return result
    }

    abstract suspend fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult

    abstract fun autoComplete(args: List<String>): MutableList<String>

}