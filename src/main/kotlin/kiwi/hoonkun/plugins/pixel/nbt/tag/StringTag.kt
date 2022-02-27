package kiwi.hoonkun.plugins.pixel.nbt.tag

import kiwi.hoonkun.plugins.pixel.nbt.Tag
import kiwi.hoonkun.plugins.pixel.nbt.TagType.TAG_STRING
import kiwi.hoonkun.plugins.pixel.nbt.extensions.putString
import kiwi.hoonkun.plugins.pixel.nbt.extensions.string
import java.nio.ByteBuffer

class StringTag private constructor(name: String? = null): Tag<String>(TAG_STRING, name) {

    override val sizeInBytes get() = Short.SIZE_BYTES * value.toByteArray().size

    constructor(value: String, name: String? = null): this(name) {
        this.value = value
    }

    constructor(buffer: ByteBuffer, name: String? = null): this(name) {
        read(buffer)
    }

    override fun read(buffer: ByteBuffer) {
        value = buffer.string
    }

    override fun write(buffer: ByteBuffer) {
        buffer.putString(value)
    }

    override fun clone(name: String?): Tag<String> = StringTag(value, name)

    override fun valueToString(): String = "\"$value\""

}