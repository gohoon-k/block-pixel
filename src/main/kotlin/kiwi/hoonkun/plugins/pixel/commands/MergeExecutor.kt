package kiwi.hoonkun.plugins.pixel.commands

class MergeExecutor: Executor() {

    override fun exec(args: List<String>): Boolean {
        return true
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return mutableListOf()
    }

}