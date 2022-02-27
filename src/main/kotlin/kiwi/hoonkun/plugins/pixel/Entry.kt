package kiwi.hoonkun.plugins.pixel

import kiwi.hoonkun.plugins.pixel.commands.*
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Level

class Entry: JavaPlugin() {

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

        return executors[args[0]]?.exec(sender, remainingArgs) ?: false
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

}