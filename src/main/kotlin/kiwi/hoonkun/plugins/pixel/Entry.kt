package kiwi.hoonkun.plugins.pixel

import kiwi.hoonkun.plugins.pixel.chunk.generator.VoidChunkGenerator
import kiwi.hoonkun.plugins.pixel.commands.*
import kiwi.hoonkun.plugins.pixel.listener.PlayerPortalListener
import kiwi.hoonkun.plugins.pixel.listener.PlayerSpawnListener
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

    private var job: Job? = null

    private lateinit var managers: Set<String>

    lateinit var repositories: MutableMap<String, Repository?>

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

        server.pluginManager.registerEvents(PlayerPortalListener(this), this)
        server.pluginManager.registerEvents(PlayerSpawnListener(this), this)

        logger.log(Level.INFO, "pixel.minecraft-git plugin is enabled.")
    }

    override fun onDisable() {
        super.onDisable()

        job?.cancel()

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
            return false
        }

        if (args[0] == "allow" || args[0] == "deny") {
            if (sender !is ConsoleCommandSender) {
                sender.sendMessage(ChatColor.RED + "unknown command '${args[0]}'")
            } else if (args.size == 1) {
                sender.sendMessage("you have to specify who to ${args[0]} pixel commands.")
            } else {
                if (args[0] == "allow") allowPlayerAsManager(args[1])
                else denyPlayerAsManager(args[1])
            }
            return true
        }

        if (!managers.contains(sender.name)) {
            sender.sendMessage("you don't have permissions to run this command.")
            return true
        }

        val remainingArgs = args.slice(1 until args.size)

        val executor = executors[args[0]] ?: run {
            sender.sendMessage(ChatColor.RED + "unknown command '${args[0]}'")
            return true
        }

        val merger = (executors["merge"] as MergeExecutor)
        val joinedArgs = args.joinToString(" ")

        if (joinedArgs == "merge abort" && merger.state > 0) {
            job?.cancel()
            Executor.sendTitle(" ")
            return true
        } else if (joinedArgs == "merge abort" && merger.state < 0) {
            when (merger.state) {
                MergeExecutor.IDLE -> sender.sendMessage(ChatColor.RED + "merger is not merging any commits")
                MergeExecutor.APPLYING_LIGHTS -> sender.sendMessage(ChatColor.RED + "cannot abort merge operation while updating lights of world")
                MergeExecutor.RELOADING_WORLDS -> sender.sendMessage(ChatColor.RED + "cannot abort merge operation while world is loading")
                MergeExecutor.COMMITTING -> sender.sendMessage(ChatColor.RED + "cannot abort merge operation while committing merged data into local repository")
            }
            return true
        }

        if (job?.isActive == true) {
            sender.sendMessage(ChatColor.YELLOW + "other command is running. please wait until previous command finishes...")
            return true
        }

        job = CoroutineScope(Dispatchers.IO).launch {
            try {
                val startTime = System.currentTimeMillis()

                val result = executor.doIt(sender, remainingArgs)

                val endTime = System.currentTimeMillis()

                if (result.success) sender.sendMessage("${result.message}${if (result.recordTime) "${ChatColor.DARK_GRAY}, in ${endTime - startTime}ms" else ""}")
                else sender.sendMessage(ChatColor.RED + result.message)

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
        if (command.name != "pixel") return super.onTabComplete(sender, command, alias, args)

        val merger = (executors["merge"] as MergeExecutor)

        val isJobActive = job?.isActive == true

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

    private fun allowPlayerAsManager(playerName: String) {
        managers = managers.toMutableSet().apply {
            remove("")
            add(playerName)
        }.toSet()
        saveManagerFile()
        server.logger.log(Level.INFO, "allowed $playerName as pixel manager")
    }

    private fun denyPlayerAsManager(playerName: String) {
        managers = managers.toMutableSet().apply {
            remove("")
            remove(playerName)
        }.toSet()
        saveManagerFile()
        server.logger.log(Level.INFO, "denied $playerName from pixel manager")
    }

    private fun saveManagerFile() {
        File("$dataFolder/pixel.managers").writeBytes(managers.joinToString("\n").toByteArray())
    }

    operator fun ChatColor.plus(other: String): String = "" + this + other

}