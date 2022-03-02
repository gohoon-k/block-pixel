package kiwi.hoonkun.plugins.pixel.commands

import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.eclipse.jgit.api.errors.GitAPIException

abstract class Executor {

    companion object {

        val COMPLETE_LIST_EMPTY = mutableListOf<String>()

        val COMPLETE_LIST_DIMENSIONS = mutableListOf("all", "overworld", "nether", "the_end")

        private var globalSender: CommandSender? = null

        fun sendTitle(message: String) {
            val localSender = globalSender ?: return
            if (localSender is Player) {
                localSender.spigot().sendMessage(ChatMessageType.ACTION_BAR, *TextComponent.fromLegacyText(message))
            }
        }

    }

    data class CommandExecuteResult(
        val success: Boolean,
        val message: String
    )

    val invalidRepositoryResult = CommandExecuteResult(false, "repository is not initialized!\nplease run '/pixel init'.")

    fun createGitApiFailedResult(exception: GitAPIException) =
        CommandExecuteResult(false, "operation failed with exception: ${exception.message}")

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