package kiwi.hoonkun.plugins.pixel.nbt.tag

import kiwi.hoonkun.plugins.pixel.nbt.Tag
import kiwi.hoonkun.plugins.pixel.nbt.TagType.TAG_SHORT
import java.nio.ByteBuffer

class ShortTag private constructor(name: String? = null): Tag<Short>(TAG_SHORT, name) {

    override val sizeInBytes get() = Short.SIZE_BYTES

    constructor(value: Short, name: String? = null): this(name) {
        this.value = value
    }

    constructor(buffer: ByteBuffer, name: String? = null): this(name) {
        read(buffer)
    }

    override fun read(buffer: ByteBuffer) {
        value = buffer.short
    }

    override fun write(buffer: ByteBuffer) {
        buffer.putShort(value)
    }

    override fun clone(name: String?) = ShortTag(value, name)

}