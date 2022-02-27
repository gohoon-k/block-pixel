package kiwi.hoonkun.plugins.pixel.nbt.tag

import kiwi.hoonkun.plugins.pixel.nbt.Tag
import kiwi.hoonkun.plugins.pixel.nbt.TagType.TAG_LONG
import java.nio.ByteBuffer

class LongTag private constructor(name: String? = null): Tag<Long>(TAG_LONG, name) {

    override val sizeInBytes get() = Long.SIZE_BYTES

    constructor(value: Long, name: String? = null): this(name) {
        this.value = value
    }

    constructor(buffer: ByteBuffer, name: String? = null): this(name) {
        read(buffer)
    }

    override fun read(buffer: ByteBuffer) {
        value = buffer.long
    }

    override fun write(buffer: ByteBuffer) {
        buffer.putLong(value)
    }

    override fun clone(name: String?) = LongTag(value, name)

}