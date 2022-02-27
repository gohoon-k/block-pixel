import io.kotlintest.specs.StringSpec
import kiwi.hoonkun.plugins.pixel.Entry
import kiwi.hoonkun.plugins.pixel.commands.InitializeExecutor
import java.io.File

class ExecutorTest: StringSpec() {

    init {

        "git init" {
            val workingPath = "../_test"

            Entry.logFolder = File("$workingPath/logs")
            Entry.versionedFolder = File("$workingPath/versioned")

            InitializeExecutor().exec(null, listOf())
        }

    }

}