import io.kotlintest.specs.StringSpec
import kiwi.hoonkun.plugins.pixel.Entry
import kiwi.hoonkun.plugins.pixel.commands.CommitExecutor
import kiwi.hoonkun.plugins.pixel.commands.InitializeExecutor
import java.io.File

class ExecutorTest: StringSpec() {

    init {

        val dataFolder = "../_main/plugins/pixel"
        Entry.dataFolder = File(dataFolder)
        Entry.versionedFolder = File("$dataFolder/versioned")
        Entry.clientFolder = File("$dataFolder/../..")
        Entry.levelName = "world"
        Entry.logFolder = File("$dataFolder/logs")

        "git init + commit" {
            InitializeExecutor().exec(null, listOf())

            CommitExecutor().exec(null, listOf("initial commit."))
        }

    }

}