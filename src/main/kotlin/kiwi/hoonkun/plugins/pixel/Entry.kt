package kiwi.hoonkun.plugins.pixel

import kiwi.hoonkun.plugins.pixel.chunk.generator.VoidChunkGenerator
import kiwi.hoonkun.plugins.pixel.commands.*
import kiwi.hoonkun.plugins.pixel.listener.PlayerPortalListener
import kiwi.hoonkun.plugins.pixel.listener.PlayerSpawnListener
import kiwi.hoonkun.plugins.pixel.utils.BranchUtils
import kiwi.hoonkun.plugins.pixel.utils.ChatUtils.Companion.removeChatColor
import kiwi.hoonkun.plugins.pixel.worker.WorldLoader
import kotlinx.coroutines.*
import org.bukkit.*
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.plugin.java.JavaPlugin
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.io.File
import java.util.logging.Level

class Entry: JavaPlugin() {

    companion object {

        lateinit var versionedFolder: File
        lateinit var clientFolder: File

        lateinit var levelName: String

        const val VOID_WORLD_NAME = "__void__"

    }

    private val pExecutors: Map<String, Executor> = mapOf(
        "allow" to AllowExecutor(this),
        "deny" to DenyExecutor(this)
    )

    private val executors: Map<String, Executor> = mapOf(
        "init" to InitializeExecutor(this),
        "commit" to CommitExecutor(this),
        "discard" to DiscardExecutor(this),
        "reset" to ResetExecutor(this),
        "branch" to BranchExecutor(this),
        "checkout" to CheckoutExecutor(this),
        "merge" to MergeExecutor(this),
        "list" to ListExecutor(this),
        "tp" to TeleportExecutor(this),
        "whereis" to WhereIsExecutor(this)
    )

    var job: Job? = null

    lateinit var managers: Set<String>

    lateinit var repositories: MutableMap<String, Repository> private set
    lateinit var branches: MutableMap<String, MutableList<String>> private set
    lateinit var branch: MutableMap<String, String> private set

    val repositoryKeys get() = repositories.keys.toMutableList()
    val availableWorldNames get() = server.worlds.map { it.name }.filter { it != VOID_WORLD_NAME && it != levelName }.toMutableList()

    override fun onEnable() {
        super.onEnable()

        if (!dataFolder.exists()) dataFolder.mkdirs()

        versionedFolder = File("${dataFolder.absolutePath}/repositories")
        clientFolder = dataFolder.absoluteFile.parentFile.parentFile

        managers = File("${dataFolder.absolutePath}/pixel.managers")
            .apply { if (!exists()) createNewFile() }
            .let { String(it.readBytes()).split("\n").toSet() }

        levelName = File("${clientFolder.absolutePath}/server.properties")
            .readBytes()
            .let { String(it) }
            .split("\n")
            .map { it.split("=") }
            .associate { it[0] to if (it.size == 1) null else it[1] }["level-name"]
                ?: throw Exception("no 'level-name' property found in server.properties")

        File("${clientFolder.absolutePath}/${levelName}_overworld").let { overworld ->
            if (overworld.exists()) return@let
            overworld.mkdirs()

            val excluded = arrayOf("advancements", "datapacks", "playerdata", "stats", "session.lock", "uid.dat")
            File("${clientFolder.absolutePath}/$levelName").listFiles()!!
                .filter { !excluded.contains(it.name) }
                .forEach {
                    val dest = File("${overworld.absolutePath}/${it.name}")
                    if (it.isDirectory) it.copyRecursively(dest)
                    else it.copyTo(dest)
                }
        }

        WorldCreator("${levelName}_overworld").createWorld()
            ?: throw Exception("cannot create overworld")

        WorldCreator(VOID_WORLD_NAME)
            .generator(VoidChunkGenerator())
            .environment(World.Environment.NORMAL)
            .type(WorldType.FLAT)
            .createWorld()
            ?.apply {
                setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
                time = 13000L
            }
            ?: throw Exception("cannot create void world")

        repositories = server.worlds
            .map { File("${versionedFolder.absolutePath}/${it.name}/.git") }
            .filter { it.exists() }
            .associate { it.absoluteFile.parentFile.name to FileRepositoryBuilder().apply { gitDir = it }.build() }
            .toMutableMap()

        branches = repositories
            .map { (key, value) -> key to BranchUtils.get(value) }
            .toMap()
            .toMutableMap()

        branch = repositories
            .map { (key, value) -> key to value.branch }
            .toMap()
            .toMutableMap()

        WorldLoader.enable()

        server.pluginManager.registerEvents(PlayerPortalListener(this), this)
        server.pluginManager.registerEvents(PlayerSpawnListener(this), this)

        logger.log(Level.INFO, "pixel.minecraft-git plugin is enabled.")
    }

    override fun onDisable() {
        super.onDisable()

        job?.cancel()

        WorldLoader.disable()

        logger.log(Level.INFO, "pixel.minecraft-git plugin is disabled.")
    }

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (command.name != Executor.ROOT_NAME) return false

        if (args.isEmpty()) {
            sender.sendMessage("pixel.minecraft-git main command.")
            return false
        }

        val operation = args[0]
        val remainingArgs = args.slice(1 until args.size)

        val isP = pExecutors.keys.contains(operation)

        if (isP && sender is ConsoleCommandSender) {
            job = CoroutineScope(Dispatchers.IO).launch {
                pExecutors.getValue(operation).doIt(sender, remainingArgs).also { sender.sendMessage(it.message) }
            }
            return true
        } else if (isP || !executors.containsKey(operation) || !managers.contains(sender.name)) {
            sender.sendMessage("${ChatColor.RED}unknown command '$operation'")
            return true
        }

        if (args.joinToString(" ") == "merge abort") {
            CoroutineScope(Dispatchers.IO).launch {
                executors["merge"]!!.doIt(sender, remainingArgs)
            }
            return true
        }

        if (job?.isActive == true) {
            val message = "other command is running. please wait until previous command finishes..."
            sender.sendMessage("${ChatColor.YELLOW}$message")
            server.logger.log(Level.WARNING, message.removeChatColor())
            return true
        }

        job = CoroutineScope(Dispatchers.IO).launch {
            try {
                val startTime = System.currentTimeMillis()
                val result = executors.getValue(operation).doIt(sender, remainingArgs)
                val endTime = System.currentTimeMillis()

                if (result.success) sender.sendMessage("${result.message}${if (result.recordTime) "${ChatColor.DARK_GRAY}, in ${endTime - startTime}ms" else ""}")
                else sender.sendMessage("${ChatColor.RED}${result.message}")

                server.logger.log(if (result.success) Level.INFO else Level.WARNING, result.message.removeChatColor())

                Executor.sendTitle(" ")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String>? {
        if (command.name != Executor.ROOT_NAME) return super.onTabComplete(sender, command, alias, args)

        val isJobActive = job?.isActive == true

        val merger = (executors["merge"] as MergeExecutor)

        if (args.size == 1) {
            return if (repositories.values.isNotEmpty() && !isJobActive) {
                executors.keys.toMutableList()
            } else if (repositories.values.isNotEmpty()) {
                if (merger.state > 0) mutableListOf("merge")
                else mutableListOf()
            } else {
                mutableListOf("init", "whereis", "tp")
            }
        }

        val remainingArgs = args.slice(1 until args.size)

        return if (isJobActive && merger.state > 0) {
            merger.autoComplete(remainingArgs)
        } else if (isJobActive) {
            mutableListOf()
        } else {
            executors[args[0]]?.autoComplete(remainingArgs)
        }
    }

}