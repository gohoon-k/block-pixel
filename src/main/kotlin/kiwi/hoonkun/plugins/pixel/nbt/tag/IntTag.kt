package kiwi.hoonkun.plugins.pixel.nbt.tag

import kiwi.hoonkun.plugins.pixel.nbt.Tag
import kiwi.hoonkun.plugins.pixel.nbt.TagType.TAG_INT
import java.nio.ByteBuffer

class IntTag private constructor(name: String? = null): Tag<Int>(TAG_INT, name) {

    override val sizeInBytes get() = Int.SIZE_BYTES

    constructor(value: Int, name: String? = null): this(name) {
        this.value = value
    }

    constructor(buffer: ByteBuffer, name: String? = null): this(name) {
        read(buffer)
    }

    override fun read(buffer: ByteBuffer) {
        value = buffer.int
    }

    override fun write(buffer: ByteBuffer) {
        buffer.putInt(value)
    }

    override fun clone(name: String?) = IntTag(value, name)

}