package kiwi.hoonkun.plugins.pixel.nbt.tag

import kiwi.hoonkun.plugins.pixel.nbt.Tag
import kiwi.hoonkun.plugins.pixel.nbt.TagType.*
import java.nio.ByteBuffer

class ByteArrayTag private constructor(name: String? = null): Tag<ByteArray>(TAG_BYTE_ARRAY, name) {

    override val sizeInBytes get() = Int.SIZE_BYTES + value.size

    constructor(value: ByteArray, name: String? = null): this(name) {
        this.value = value
    }

    constructor(buffer: ByteBuffer, name: String? = null): this(name) {
        read(buffer)
    }

    override fun read(buffer: ByteBuffer) {
        val length = buffer.int

        value = ByteArray(length)
        buffer.get(value, 0, length)
    }

    override fun write(buffer: ByteBuffer) {
        buffer.putInt(value.size)
        buffer.put(value)
    }

    override fun clone(name: String?) = ByteArrayTag(value, name)

    override fun valueToString(): String = "[ ${value.joinToString(", ")} ]"

}