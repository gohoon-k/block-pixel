package kiwi.hoonkun.plugins.pixel.utils

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.lib.Repository

class BranchUtils {

    companion object {

        fun get(repository: Repository): MutableList<String> {
            val git = Git(repository)
            return git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call().map {
                shorten(it.name)
            }.toMutableList()
        }

        private fun shorten(name: String): String {
            val indexes = name.findIndexes('/')
            return if (indexes.size < 2) name else name.substring(indexes[1] + 1, name.length)
        }

        fun String.findIndexes(char: Char): List<Int> {
            val result = mutableListOf<Int>()
            var index = 0
            split(char).forEach {
                index += (it.length)
                result.add(index)
                index++
            }
            return result
        }

    }

}