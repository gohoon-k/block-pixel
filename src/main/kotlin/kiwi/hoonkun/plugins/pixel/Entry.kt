package kiwi.hoonkun.plugins.pixel

import kiwi.hoonkun.plugins.pixel.commands.*
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.io.File
import java.util.logging.Level

class Entry: JavaPlugin() {

    companion object {

        lateinit var dataFolder: File

        lateinit var logFolder: File
        lateinit var versionedFolder: File
        lateinit var clientFolder: File

        lateinit var levelName: String

        var repository: Repository? = null

    }

    private val executors: Map<String, Executor> = mapOf(
        "init" to InitializeExecutor(),
        "commit" to CommitExecutor(),
        "discard" to DiscardExecutor(),
        "reset" to ResetExecutor(),
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

        val properties = String(File("${clientFolder.absolutePath}/server.properties").readBytes())
        levelName = properties.split("\n")
            .map { it.split("=") }
            .associate { Pair(it[0], it[1]) }["level-name"] ?: throw Exception("no 'level-name' property found in server.properties!!")

        val gitDir = File("${versionedFolder.absolutePath}/.git")
        if (gitDir.exists()) {
            val repositoryBuilder = FileRepositoryBuilder()
            repositoryBuilder.gitDir = gitDir
            repository = repositoryBuilder.build()
        }

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

        if (result.success) sender.sendMessage(result.message)
        else sender.sendMessage(ChatColor.RED + result.message)

        return true
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