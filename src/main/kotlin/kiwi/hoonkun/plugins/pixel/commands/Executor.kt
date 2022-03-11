package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import kiwi.hoonkun.plugins.pixel.utils.TextWidthUtils.Companion.ellipsizeChat
import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.ChatColor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.eclipse.jgit.api.errors.GitAPIException

abstract class Executor(val parent: Entry) {

    companion object {

        const val ROOT_NAME = "pixel"

        val ARGS_LIST_EMPTY = mutableListOf<String>()

        val ARGS_LIST_COMMIT_CONFIRM = mutableListOf("< all_change_committed >")

        private var globalSender: CommandSender? = null

        fun sendTitle(message: String) {
            val localSender = globalSender ?: return
            if (localSender is Player) {
                localSender.spigot().sendMessage(ChatMessageType.ACTION_BAR, *TextComponent.fromLegacyText(message))
            }
        }

        val g = ChatColor.GRAY
        val w = ChatColor.WHITE
        val r = ChatColor.RED
        val y = ChatColor.YELLOW

        val RESULT_NO_COMMIT_CONFIRM =
            CommandExecuteResult(false, "you must specify that you have committed all uncommitted changes before checkout.\nif yes, pass 'true' to last argument.")

        val RESULT_UNCOMMITTED =
            CommandExecuteResult(false, "you specified that you didn't committed changes. please commit them first.")

        val RESULT_REPOSITORY_NOT_INITIALIZED =
            CommandExecuteResult(false, "repository with given world is not initialized!\nplease run '/pixel init' first.")

        const val MESSAGE_DETACHED_HEAD = "world's repository HEAD seems detached from any other branches."

    }

    data class CommandExecuteResult(
        val success: Boolean,
        val message: String,
        val recordTime: Boolean = true
    )

    abstract val usage: String
    abstract val description: String

    private val commandRoot: String = "/$ROOT_NAME "

    private val help: String get() = "$y${"$commandRoot$usage".ellipsizeChat()}\n$g$description"

    fun createGitApiFailedResult(operation: String, exception: GitAPIException): CommandExecuteResult =
        CommandExecuteResult(false, "failed to $operation because of exception\n${exception.message}")

    fun createUnknownWorldResult(e: UnknownWorldException): CommandExecuteResult =
        CommandExecuteResult(false, e.message!!)

    fun createUnknownWorldResult(worldName: String): CommandExecuteResult =
        CommandExecuteResult(false, "unknown world '$worldName'")

    fun isValidWorld(worldName: String): Boolean = parent.server.worlds.map { it.name }.contains(worldName)

    fun worlds(arg: String): List<String> =
        if (parent.repositoryKeys.contains(arg)) listOf(arg)
        else throw UnknownWorldException(arg)

    fun worldsWithAll(arg: String, repository: Boolean = true): List<String> =
        if (arg == "all") {
            if (repository) parent.repositoryKeys else parent.availableWorldNames
        } else if (parent.repositoryKeys.contains(arg)) {
            listOf(arg)
        } else throw UnknownWorldException(arg)

    suspend fun doIt(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        globalSender = sender

        if (args.isEmpty()) return CommandExecuteResult(true, help, recordTime = false)

        val result = exec(sender, args)

        globalSender = null

        return result
    }

    abstract suspend fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult

    abstract fun autoComplete(args: List<String>): MutableList<String>

    class UnknownWorldException(worldName: String): Exception("unknown world '$worldName'")

}