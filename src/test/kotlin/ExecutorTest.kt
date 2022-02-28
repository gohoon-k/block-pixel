import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import kiwi.hoonkun.plugins.pixel.Entry
import kiwi.hoonkun.plugins.pixel.commands.*
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.io.File

class Environment {

    companion object {

        fun setup() {
            val dataFolder = "../_main/plugins/pixel"
            Entry.dataFolder = File(dataFolder)
            Entry.versionedFolder = File("$dataFolder/versioned")
            Entry.clientFolder = File("$dataFolder/../..")
            Entry.levelName = "world"
            Entry.logFolder = File("$dataFolder/logs")

            val gitDir = File("${Entry.versionedFolder.absolutePath}/.git")
            if (gitDir.exists()) {
                val repositoryBuilder = FileRepositoryBuilder()
                repositoryBuilder.gitDir = gitDir
                Entry.repository = repositoryBuilder.build()
            }
        }

    }

}

class PixelInitializeTest: StringSpec() {

    init {
        Environment.setup()

        "/pixel init" {
            val result = InitializeExecutor().exec(null, listOf())
            println(result.message)
            result.success shouldBe true
        }
    }

}

class PixelCommitTest: StringSpec() {

    init {
        Environment.setup()

        "/pixel commit" {
            val result = CommitExecutor().exec(null, listOf("test commit in ${System.currentTimeMillis()}"))
            println(result.message)
            result.success shouldBe true
        }
    }

}

class PixelBranchTest: StringSpec() {

    init {
        Environment.setup()

        "/pixel branch" {
            val result = BranchExecutor().exec(null, listOf("struct/library"))
            println(result.message)
            result.success shouldBe true
        }
    }

}

class PixelCheckoutTest: StringSpec() {

    init {
        Environment.setup()

        "/pixel checkout" {
            val result = CheckoutExecutor().exec(null, listOf("master"))
            println(result.message)
            result.success shouldBe true
        }
    }

}

class PixelDiscardTest: StringSpec() {

    init {
        Environment.setup()

        "/pixel discard" {
            val result = DiscardExecutor().exec(null, listOf())
            println(result.message)
            result.success shouldBe true
        }
    }

}

class PixelResetTest: StringSpec() {

    init {
        Environment.setup()

        "/pixel reset" {
            val result = ResetExecutor().exec(null, listOf("2"))
            println(result.message)
            result.success shouldBe true
        }
    }

}

class PixelListTest: StringSpec() {

    init {
        Environment.setup()

        "/pixel list commits" {
            val result = ListExecutor().exec(null, listOf("commits"))
            println(result.message)
            result.success shouldBe true
        }

        "/pixel list branches" {
            val result = ListExecutor().exec(null, listOf("branches"))
            println(result.message)
            result.success shouldBe true
        }
    }

}