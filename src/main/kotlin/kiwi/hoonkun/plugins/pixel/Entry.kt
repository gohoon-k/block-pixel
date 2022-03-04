package kiwi.hoonkun.plugins.pixel

import kiwi.hoonkun.plugins.pixel.commands.*
import kotlinx.coroutines.*
import org.bukkit.*
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
        "commit" to CommitExecutor(this),
        "discard" to DiscardExecutor(this),
        "reset" to ResetExecutor(this),
        "branch" to BranchExecutor(),
        "checkout" to CheckoutExecutor(this),
        "merge" to MergeExecutor(this),
        "list" to ListExecutor(),
        "undo" to UndoExecutor(),
        "tp" to TeleportExecutor(this),
        "whereis" to WhereIsExecutor(this)
    )

    lateinit var void: World
    lateinit var overworld: World

    private lateinit var scope: CoroutineScope
    private val job = Job()

    private var scopeRunning = false

    override fun onEnable() {
        super.onEnable()

        val dataFolderPath = dataFolder.absolutePath

        Entry.dataFolder = dataFolder
        logFolder = File("$dataFolderPath/logs")
        versionedFolder = File("$dataFolderPath/versioned")
        clientFolder = File(dataFolderPath).parentFile.parentFile

        val properties = String(File("${clientFolder.absolutePath}/server.properties").readBytes())
        levelName = properties.split("\n")
            .map { it.split("=") }
            .associate { Pair(it[0], if (it.size == 1) null else it[1]) }["level-name"] ?: throw Exception("no 'level-name' property found in server.properties!!")

        val gitDir = File("${versionedFolder.absolutePath}/.git")
        if (gitDir.exists()) {
            val repositoryBuilder = FileRepositoryBuilder()
            repositoryBuilder.gitDir = gitDir
            repository = repositoryBuilder.build()
            logger.log(Level.INFO, "find local git repository, initialized.")
        }

        void = if (File("${clientFolder.absolutePath}/__void__").exists()) {
            logger.log(Level.INFO, "creating void world from existing file")
            server.createWorld(WorldCreator("__void__")) ?: throw Exception("cannot create void world")
        } else {
            logger.log(Level.INFO, "creating new void world")
            WorldCreator("__void__")
                .type(WorldType.FLAT)
                .generateStructures(false)
                .generatorSettings("""{"structures": {"structures": {}}, "layers": [{"block": "air", "height": 1}], "biome":"plains"}""")
                .createWorld() ?: throw Exception("cannot create void world")
        }

        val overworldFolder = File("${clientFolder.absolutePath}/${levelName}_overworld")
        if (!overworldFolder.exists()) {
            val dummyFiles = File("${clientFolder.absolutePath}/$levelName").listFiles()?.toMutableList() ?: throw Exception("main world not exists")
            val excludedFolders = arrayOf("advancements", "datapacks", "playerdata", "stats", "session.lock", "uid.dat")
            dummyFiles.removeIf { excludedFolders.contains(it.name) }
            dummyFiles.forEach {
                if (it.isDirectory) it.copyRecursively(File("${overworldFolder.absolutePath}/${it.name}"))
                else it.copyTo(File("${overworldFolder.absolutePath}/${it.name}"))
            }
        }

        overworld = server.createWorld(WorldCreator("${levelName}_overworld")) ?: throw Exception("cannot create overworld")

        server.onlinePlayers.forEach {
            it.setGravity(true)
            it.teleport(Location(server.getWorld(levelName)!!, it.location.x, it.location.y, it.location.z))
        }

        scope = CoroutineScope(job + Dispatchers.IO)

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

        if (scopeRunning) {
            sender.sendMessage(ChatColor.YELLOW + "other command is running. please wait until previous command finishes...")
            return true
        }

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
                scopeRunning = false
                e.printStackTrace()
            }

            scopeRunning = false
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

        if (args.size == 1) return executors.keys.toMutableList()

        return executors[args[0]]?.autoComplete(args.slice(1 until args.size))
    }

    operator fun ChatColor.plus(other: String): String = "" + this + other

}