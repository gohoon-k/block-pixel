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

    private lateinit var job: CompletableJob

    private var scopeRunning = false

    private lateinit var managers: Set<String>

    lateinit var repositories: MutableMap<String, Repository>

    val repositoryKeys get() = repositories.keys.toMutableList()
    val availableWorldNames get() = server.worlds.filter { it.name != VOID_WORLD_NAME && it.name != levelName }.map { it.name }.toMutableList()

    override fun onEnable() {
        super.onEnable()

        val dataFolderPath = dataFolder.absolutePath
        val dataFolderFile = File(dataFolderPath)

        if (!dataFolderFile.exists()) dataFolderFile.mkdirs()

        versionedFolder = File("$dataFolderPath/repositories")
        clientFolder = File(dataFolderPath).parentFile.parentFile

        val managerFile = File("$dataFolder/pixel.managers")
        if (!managerFile.exists())
            managerFile.createNewFile()

        managers = String(managerFile.readBytes()).split("\n").toSet()

        val properties = String(File("${clientFolder.absolutePath}/server.properties").readBytes())
        levelName = properties.split("\n")
            .map { it.split("=") }
            .associate { Pair(it[0], if (it.size == 1) null else it[1]) }["level-name"] ?: throw Exception("no 'level-name' property found in server.properties!!")

        val void = if (File("${clientFolder.absolutePath}/$VOID_WORLD_NAME").exists()) {
            logger.log(Level.INFO, "creating void world from existing file")
            server.createWorld(WorldCreator(VOID_WORLD_NAME)) ?: throw Exception("cannot create void world")
        } else {
            logger.log(Level.INFO, "creating new void world")
            WorldCreator(VOID_WORLD_NAME)
                .generator(VoidChunkGenerator())
                .environment(World.Environment.NORMAL)
                .type(WorldType.FLAT)
                .createWorld() ?: throw Exception("cannot create void world")
        }

        void.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
        void.time = 13000L

        val overworldFolder = File("${clientFolder.absolutePath}/${levelName}_overworld")
        if (!overworldFolder.exists()) {
            overworldFolder.mkdirs()
            val dummyFiles = File("${clientFolder.absolutePath}/$levelName").listFiles()?.toMutableList() ?: throw Exception("main world not exists")
            val excludedFolders = arrayOf("advancements", "datapacks", "playerdata", "stats", "session.lock", "uid.dat")
            dummyFiles.removeIf { excludedFolders.contains(it.name) }
            dummyFiles.forEach {
                if (it.isDirectory) it.copyRecursively(File("${overworldFolder.absolutePath}/${it.name}"))
                else it.copyTo(File("${overworldFolder.absolutePath}/${it.name}"))
            }
        }

        server.createWorld(WorldCreator("${levelName}_overworld")) ?: throw Exception("cannot create overworld")

        repositories = mutableMapOf<String, Repository>()
            .apply {
                server.worlds.map { it.name }.forEach {
                    val gitDir = File("${versionedFolder.absolutePath}/$it/.git")
                    if (!gitDir.exists()) return@forEach

                    val builder = FileRepositoryBuilder()
                    builder.gitDir = gitDir

                    set(it, builder.build())
                }
            }

        server.onlinePlayers.forEach {
            it.setGravity(true)
            it.teleport(Location(server.getWorld(levelName)!!, it.location.x, it.location.y, it.location.z))
        }

        server.pluginManager.registerEvents(PlayerPortalListener(this), this)
        server.pluginManager.registerEvents(PlayerSpawnListener(this), this)

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
            job.cancel()
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

        if (scopeRunning) {
            sender.sendMessage(ChatColor.YELLOW + "other command is running. please wait until previous command finishes...")
            return true
        }

        job = Job()
        val scope = CoroutineScope(job + Dispatchers.IO)
        scope.launch {
            scopeRunning = true

            try {
                val startTime = System.currentTimeMillis()

                val result = executor.doIt(sender, remainingArgs)

                val endTime = System.currentTimeMillis()

                if (result.success) sender.sendMessage("${result.message}${if (result.recordTime) "${ChatColor.DARK_GRAY}, in ${endTime - startTime}ms" else ""}")
                else sender.sendMessage(ChatColor.RED + result.message)

                Executor.sendTitle(" ")
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (job.isCancelled) job.complete()
                scopeRunning = false
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

        if (args.size == 1) {
            return if (repositories.values.isNotEmpty() && !scopeRunning) {
                executors.keys.toMutableList()
            } else if (repositories.values.isNotEmpty()) {
                if (merger.state > 0) mutableListOf("merge")
                else mutableListOf()
            } else {
                mutableListOf("init", "whereis", "tp")
            }
        }

        val remainingArgs = args.slice(1 until args.size)

        return if (scopeRunning && merger.state > 0) {
            merger.autoComplete(remainingArgs)
        } else if (scopeRunning) {
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