package kiwi.hoonkun.plugins.pixel.nbt.tag

import kiwi.hoonkun.plugins.pixel.nbt.AnyTag
import kiwi.hoonkun.plugins.pixel.nbt.Tag
import kiwi.hoonkun.plugins.pixel.nbt.TagType
import kiwi.hoonkun.plugins.pixel.nbt.TagType.*
import kiwi.hoonkun.plugins.pixel.nbt.extensions.byte
import kiwi.hoonkun.plugins.pixel.nbt.extensions.putString
import kiwi.hoonkun.plugins.pixel.nbt.extensions.string
import java.nio.ByteBuffer

typealias Compound = Map<String, AnyTag>

class CompoundTag private constructor(name: String? = null): Tag<Compound>(TAG_COMPOUND, name) {

    private var complicated = false

    override val sizeInBytes: Int
        get() = value.entries.sumOf { (name, tag) ->
            Byte.SIZE_BYTES + Short.SIZE_BYTES + name.toByteArray().size + tag.sizeInBytes
        } + Byte.SIZE_BYTES

    operator fun get(key: String) = value[key]

    constructor(value: Compound, name: String? = null): this(name) {
        this.value = value.map { (name, tag) -> name to tag.ensureName(name) }.toMap()
    }

    constructor(buffer: ByteBuffer, name: String? = null): this(name) {
        read(buffer)
    }

    override fun read(buffer: ByteBuffer) {
        val new = mutableMapOf<String, AnyTag>()

        var nextId: Byte
        do {
            nextId = buffer.byte

            if (nextId == TAG_END.id) break

            val nextName = buffer.string
            val nextTag = read(TagType[nextId], buffer, nextName)

            new[nextName] = nextTag
        } while (true)

        value = new
    }

    override fun write(buffer: ByteBuffer) {
        value.entries.forEach { (name, tag) ->
            buffer.put(tag.type.id)
            buffer.putString(name)

            tag.write(buffer)
        }

        buffer.put(TAG_END.id)
    }

    fun writeAsRoot(buffer: ByteBuffer) {
        buffer.put(TAG_COMPOUND.id)
        buffer.putString(name ?: "")
        write(buffer)
    }

    override fun clone(name: String?) = CompoundTag(value.map { (name, tag) -> name to tag.clone(name) }.toMap(), name)

    override fun valueToString(): String {
        val result = "{\n${value.entries.sortedBy { it.key }.joinToString(",\n") { "${it.value}" }}\n}"
        return if (complicated) result else result.replace("\n", " ")
    }

    fun generateTypes(parentPath: String = ""): Map<String, Byte> {
        val result = mutableMapOf<String, Byte>()
        value.entries.map { (k, v) ->
            val path = if (parentPath == "") k else "$parentPath.$k"
            result[path] = v.type.id
            if (v.type == TAG_COMPOUND) {
                result.putAll(v.getAs<CompoundTag>().generateTypes(path))
            }
            if (v.type == TAG_LIST) {
                val listTag = v.getAs<ListTag>()
                result["$path.*"] = listTag.elementsType.id
                val listCompoundTypes = listTag.generateTypes(path)
                result.putAll(listCompoundTypes)
            }
        }
        return result
    }

}