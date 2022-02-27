package kiwi.hoonkun.plugins.pixel

import kiwi.hoonkun.plugins.pixel.commands.*
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.logging.Level

class Entry: JavaPlugin() {

    companion object {

        var dataFolder: File? = null

        var logFolder: File? = null
        var versionedFolder: File? = null
        var clientFolder: File? = null

        var levelName: String? = null

    }

    private val executors: Map<String, Executor> = mapOf(
        "init" to InitializeExecutor(),
        "commit" to CommitExecutor(),
        "discard" to DiscardExecutor(),
        "reset" to ResetExecutor(),
        "revert" to RevertExecutor(),
        "branch" to BranchExecutor(),
        "checkout" to CheckoutExecutor(),
        "merge" to MergeExecutor(),
        "list" to ListExecutor(),
        "undo" to UndoExecutor()
    )

    override fun onEnable() {
        super.onEnable()

        val dataFolderPath = dataFolder.absolutePath

        Entry.dataFolder = dataFolder
        logFolder = File("$dataFolderPath/logs")
        versionedFolder = File("$dataFolderPath/versioned")
        clientFolder = File("$dataFolderPath/../..")

        val properties = String(File("$dataFolderPath/../../server.properties").readBytes())
        levelName = properties.split("\n")
            .map { it.split("=") }
            .associate { Pair(it[0], it[1]) }["level-name"]

        logger.log(Level.INFO, "pixel.minecraft-git plugin is enabled.")
    }

    override fun onDisable() {
        super.onDisable()

        logger.log(Level.INFO, "pixel.minecraft-git plugin is disabled.")
    }

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (command.name != "pixel") return false

        if (args.isEmpty()) {
            sender.sendMessage("pixel.minecraft-git main command.")
            sender.sendMessage("type '/pixel help' to print some useful messages.")
            return false
        }

        val remainingArgs = args.slice(1 until args.size)

        val executor = executors[args[0]] ?: run {
            sender.sendMessage(ChatColor.RED + "unknown command '${args[0]}'")
            return true
        }
        val result = executor.exec(sender, remainingArgs)

        sender.sendMessage(result.message)

        return result.success
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String>? {
        if (command.name != "pixel") return super.onTabComplete(sender, command, alias, args)

        if (args.size == 1) return executors.keys.toMutableList()

        return executors[args[0]]?.autoComplete(args.slice(1 until args.size))
    }

    operator fun ChatColor.plus(other: String): String = "" + this + other

}