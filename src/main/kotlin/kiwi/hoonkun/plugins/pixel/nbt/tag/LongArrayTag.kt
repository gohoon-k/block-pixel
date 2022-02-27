package kiwi.hoonkun.plugins.pixel.nbt.tag

import kiwi.hoonkun.plugins.pixel.nbt.Tag
import kiwi.hoonkun.plugins.pixel.nbt.TagType.*
import java.nio.ByteBuffer

class LongArrayTag private constructor(name: String? = null): Tag<LongArray>(TAG_LONG_ARRAY, name) {

    override val sizeInBytes get() = Long.SIZE_BYTES + value.size * Long.SIZE_BYTES

    constructor(value: LongArray, name: String? = null): this(name) {
        this.value = value
    }

    constructor(buffer: ByteBuffer, name: String? = null): this(name) {
        read(buffer)
    }

    override fun read(buffer: ByteBuffer) {
        value = LongArray(buffer.int) { buffer.long }
    }

    override fun write(buffer: ByteBuffer) {
        buffer.putInt(value.size)
        value.forEach { buffer.putLong(it) }
    }

    override fun clone(name: String?) = LongArrayTag(value, name)

    override fun valueToString(): String = "[ ${value.joinToString(", ")} ]"

}