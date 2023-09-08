package util

import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.type.CollectionType
import com.fasterxml.jackson.databind.type.MapType
import com.fasterxml.jackson.databind.type.TypeFactory
import java.util.Formatter


import java.util.function.Supplier

/**
 * extract the operators of JSON
 *
 * @author zhangt2333
 */
object JsonUtils {
    private val mapper: ObjectMapper = ObjectMapper()
    private val typeFactory: TypeFactory = mapper.getTypeFactory()
    private val formatter = Formatter()

    init {
        val module = SimpleModule()
        mapper.registerModule(module)
    }

    fun getMapper(): ObjectMapper {
        return mapper
    }

    private fun format(format: String, vararg args: Any): String {
        return formatter.format(format, *args).toString()
    }

    fun toJsonString(obj: Any?): String? {
        return if (obj == null) null else toJsonString(obj, { null })
    }

    @JvmOverloads
    fun toJsonString(obj: Any?, defaultSupplier: Supplier<String?>, format: Boolean = false): String? {
        try {
            if (obj == null) {
                return defaultSupplier.get()
            }
            return if (format) {
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj)
            } else mapper.writeValueAsString(obj)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        return defaultSupplier.get()
    }

    fun <T> toObject(value: String?, tClass: Class<T?>?): T? {
        return if (value == null) null else toObject(value, tClass) { null }
    }

    fun <T> toObject(value: String?, tClass: Class<T>?, defaultSupplier: Supplier<T>): T {
        try {
            return if (value == null || value.isBlank()) {
                defaultSupplier.get()
            } else mapper.readValue(value, tClass)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        return defaultSupplier.get()
    }

    fun <T> toList(value: String?, tClass: Class<T>?): List<T>? {
        return if (value == null) null else toList(value, tClass) { null }
    }

    fun <T> toList(value: String?, tClass: Class<T>?, defaultSupplier: Supplier<List<T>?>): List<T>? {
        try {
            return if (value == null || value.isBlank()) {
                defaultSupplier.get()
            } else mapper.readValue(value, typeFactory.constructCollectionType(MutableList::class.java, tClass))
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        return defaultSupplier.get()
    }

    fun <T> toNestedList(value: String?, tClass: Class<T>?): List<List<T>>? {
        if (value == null || value.isBlank()) {
            return null
        }
        try {
            val valueType: CollectionType = typeFactory.constructCollectionType(MutableList::class.java, tClass)
            val listType: CollectionType = typeFactory.constructCollectionType(MutableList::class.java, valueType)
            return mapper.readValue(value, listType)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        return null
    }

    fun <T> toSet(value: String?, tClass: Class<T>?): Set<T>? {
        return if (value == null) null else toSet(value, tClass) { null }
    }

    fun <T> toSet(value: String?, tClass: Class<T>?, defaultSupplier: Supplier<Set<T>?>): Set<T>? {
        try {
            return if (value == null || value.isBlank()) {
                defaultSupplier.get()
            } else mapper.readValue(value, typeFactory.constructCollectionType(MutableSet::class.java, tClass))
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        return defaultSupplier.get()
    }

    fun toMap(value: String?): Map<String, Any>? {
        return if (value == null || value.isBlank()) {
            null
        } else try {
            mapper.readValue(
                value, typeFactory.constructMapType(
                    MutableMap::class.java,
                    String::class.java,
                    Any::class.java
                )
            )
        } catch (t: Throwable) {
            throw RuntimeException(t)
        }
    }

    fun <K, V> toMapWithListAsValueType(
        value: String?,
        kClass: Class<K>?,
        vClass: Class<V>?
    ): Map<K, List<V>>? {
        return if (value == null || value.isBlank()) {
            null
        } else try {
            val keyType: JavaType = typeFactory.constructType(kClass)
            val valueType: CollectionType = typeFactory.constructCollectionType(MutableList::class.java, vClass)
            val mapType: MapType = typeFactory.constructMapType(MutableMap::class.java, keyType, valueType)
            mapper.readValue(value, mapType)
        } catch (t: Throwable) {
            throw RuntimeException(t)
        }
    }

    fun toJSONBytes(`object`: Any?): ByteArray? {
        return if (`object` == null) {
            null
        } else try {
            mapper.writeValueAsBytes(`object`)
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    fun toJson(value: String?): JsonNode? {
        return if (value == null || value.isBlank()) {
            null
        } else try {
            mapper.readTree(value)
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    fun createArrayJson(): ArrayNode {
        return mapper.createArrayNode()
    }

    fun createJson(): ObjectNode {
        return mapper.createObjectNode()
    }
}
