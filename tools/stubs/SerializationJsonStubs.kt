@file:Suppress("unused", "UNUSED_PARAMETER")
package kotlinx.serialization.json

import kotlinx.serialization.KSerializer

class JsonBuilder {
    var ignoreUnknownKeys: Boolean = false
    var isLenient: Boolean = false
    var encodeDefaults: Boolean = false
    var explicitNulls: Boolean = true
}

class Json(private val builder: JsonBuilder = JsonBuilder()) {
    @Suppress("UNCHECKED_CAST")
    fun <T> decodeFromString(deserializer: KSerializer<T>, string: String): T =
        throw UnsupportedOperationException("stub")

    fun parseToJsonElement(string: String): JsonElement = throw UnsupportedOperationException("stub")
    fun encodeToString(value: JsonElement): String = throw UnsupportedOperationException("stub")

    companion object {
        val Default = Json()
    }
}

@Suppress("FunctionName")
fun Json(from: Json = Json.Default, builderAction: JsonBuilder.() -> Unit = {}): Json = Json.Default

@Suppress("FunctionName")
fun Json(builderAction: JsonBuilder.() -> Unit): Json = Json.Default

sealed class JsonElement
class JsonPrimitive : JsonElement() {
    val content: String get() = ""
    val isString: Boolean get() = false
}
class JsonObject : JsonElement(), Map<String, JsonElement> {
    override val entries: Set<Map.Entry<String, JsonElement>> get() = emptySet()
    override val keys: Set<String> get() = emptySet()
    override val size: Int get() = 0
    override val values: Collection<JsonElement> get() = emptyList()
    override fun containsKey(key: String): Boolean = false
    override fun containsValue(value: JsonElement): Boolean = false
    override fun get(key: String): JsonElement? = null
    override fun isEmpty(): Boolean = true
}
class JsonNull : JsonElement()
class JsonArray(list: List<JsonElement> = emptyList()) : JsonElement(), List<JsonElement> {
    private val delegate = list
    override val size: Int get() = delegate.size
    override fun get(index: Int): JsonElement = delegate[index]
    override fun isEmpty(): Boolean = delegate.isEmpty()
    override fun iterator(): Iterator<JsonElement> = delegate.iterator()
    override fun listIterator(): ListIterator<JsonElement> = delegate.listIterator()
    override fun listIterator(index: Int): ListIterator<JsonElement> = delegate.listIterator(index)
    override fun subList(fromIndex: Int, toIndex: Int): List<JsonElement> = delegate.subList(fromIndex, toIndex)
    override fun indexOf(element: JsonElement): Int = delegate.indexOf(element)
    override fun lastIndexOf(element: JsonElement): Int = delegate.lastIndexOf(element)
    override fun contains(element: JsonElement): Boolean = delegate.contains(element)
    override fun containsAll(elements: Collection<JsonElement>): Boolean = delegate.containsAll(elements)
}

val JsonElement.jsonPrimitive: JsonPrimitive get() = JsonPrimitive()
val JsonElement.jsonObject: JsonObject get() = JsonObject()
val JsonElement.jsonArray: JsonArray get() = JsonArray()
val JsonPrimitive.intOrNull: Int? get() = content.toIntOrNull()
val JsonPrimitive.doubleOrNull: Double? get() = content.toDoubleOrNull()
val JsonPrimitive.booleanOrNull: Boolean? get() = content.toBooleanStrictOrNull()
val JsonPrimitive.contentOrNull: String? get() = null
val JsonPrimitive.isStringPrimitive: Boolean get() = isString
val JsonObject?.orEmptyObject: JsonObject get() = this ?: JsonObject()
