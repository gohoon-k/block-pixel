package kiwi.hoonkun.plugins.pixel.commands

abstract class Executor {

    abstract fun exec(args: List<String>): Boolean

    abstract fun autoComplete(args: List<String>): MutableList<String>

}