package kiwi.hoonkun.plugins.pixel.nbt

import kiwi.hoonkun.plugins.pixel.nbt.TagType.*
import kiwi.hoonkun.plugins.pixel.nbt.tag.*

import java.nio.ByteBuffer

typealias AnyTag = Tag<out Any>

abstract class Tag<T: Any> protected constructor(val type: TagType, val name: String?) {

    lateinit var value: T

    abstract val sizeInBytes: Int

    inline fun <reified T: AnyTag?> getAs() = this as? T ?: throw Exception("Tag is not a ${T::class.java.simpleName}")

    fun ensureName(name: String?) = if (this.name == name) this else clone(name)

    abstract fun read(buffer: ByteBuffer)

    abstract fun write(buffer: ByteBuffer)

    abstract fun clone(name: String? = this.name): Tag<T>

    open fun prefix() = if (name.isNullOrEmpty()) "" else "\"${name}\": "

    open fun valueToString() = "$value"

    override fun toString(): String = prefix() + valueToString()

    companion object {

        fun read(tagType: TagType, buffer: ByteBuffer, name: String? = null) = when(tagType) {
            TAG_END -> EndTag()
            TAG_BYTE -> ByteTag(buffer, name)
            TAG_SHORT -> ShortTag(buffer, name)
            TAG_INT -> IntTag(buffer, name)
            TAG_LONG -> LongTag(buffer, name)
            TAG_FLOAT -> FloatTag(buffer, name)
            TAG_DOUBLE -> DoubleTag(buffer, name)
            TAG_BYTE_ARRAY -> ByteArrayTag(buffer, name)
            TAG_STRING -> StringTag(buffer, name)
            TAG_LIST -> ListTag(buffer, name)
            TAG_COMPOUND -> CompoundTag(buffer, name)
            TAG_INT_ARRAY -> IntArrayTag(buffer, name)
            TAG_LONG_ARRAY -> LongArrayTag(buffer, name)
        }

    }

}

enum class TagType(val id: Byte) {
    TAG_END(0),
    TAG_BYTE(1),
    TAG_SHORT(2),
    TAG_INT(3),
    TAG_LONG(4),
    TAG_FLOAT(5),
    TAG_DOUBLE(6),
    TAG_BYTE_ARRAY(7),
    TAG_STRING(8),
    TAG_LIST(9),
    TAG_COMPOUND(10),
    TAG_INT_ARRAY(11),
    TAG_LONG_ARRAY(12);

    companion object {
        private val reversed = values().associateBy { it.id }
        operator fun get(id: Byte): TagType = reversed[id] ?: throw Exception("unknown tag id: $id")
    }
}