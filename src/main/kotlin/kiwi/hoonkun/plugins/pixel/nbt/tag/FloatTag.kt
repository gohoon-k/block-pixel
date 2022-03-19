package kiwi.hoonkun.plugins.pixel.nbt.tag

import kiwi.hoonkun.plugins.pixel.nbt.AnyTag
import kiwi.hoonkun.plugins.pixel.nbt.Tag
import kiwi.hoonkun.plugins.pixel.nbt.TagType.TAG_FLOAT
import java.nio.ByteBuffer

class FloatTag private constructor(name: String? = null, parent: AnyTag?): Tag<Float>(TAG_FLOAT, name, parent) {

    override val sizeInBytes get() = Float.SIZE_BYTES

    constructor(value: Float, name: String? = null, parent: AnyTag?): this(name, parent) {
        this.value = value
    }

    constructor(buffer: ByteBuffer, name: String? = null, parent: AnyTag?): this(name, parent) {
        read(buffer)
    }

    override fun read(buffer: ByteBuffer) {
        value = buffer.float
    }

    override fun write(buffer: ByteBuffer) {
        buffer.putFloat(value)
    }

    override fun clone(name: String?) = FloatTag(value, name, parent)

}