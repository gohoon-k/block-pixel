package kiwi.hoonkun.plugins.pixel.nbt.tag

import kiwi.hoonkun.plugins.pixel.nbt.Tag
import kiwi.hoonkun.plugins.pixel.nbt.TagType.*

import java.nio.ByteBuffer

class DoubleTag private constructor(name: String? = null): Tag<Double>(TAG_DOUBLE, name) {

    override val sizeInBytes get() = Double.SIZE_BYTES

    constructor(value: Double, name: String? = null): this(name) {
        this.value = value
    }

    constructor(buffer: ByteBuffer, name: String? = null): this(name) {
        read(buffer)
    }

    override fun read(buffer: ByteBuffer) {
        value = buffer.double
    }

    override fun write(buffer: ByteBuffer) {
        buffer.putDouble(value)
    }

    override fun clone(name: String?) = DoubleTag(value, name)

}