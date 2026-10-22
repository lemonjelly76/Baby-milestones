package io.github.lemonjelly76.babymilestones.server.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** Converts between kotlinx JSON and the plain maps and lists the Jackson-based MCP and Anthropic SDKs take. */
object PlainJson {

    fun toPlain(element: JsonElement): Any? = when (element) {
        is JsonNull -> null
        is JsonObject -> element.mapValues { toPlain(it.value) }
        is JsonArray -> element.map(::toPlain)
        is JsonPrimitive -> if (element.isString) element.content
            else element.booleanOrNull ?: element.longOrNull ?: element.doubleOrNull
    }

    fun fromPlain(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to fromPlain(v) })
        is Iterable<*> -> JsonArray(value.map(::fromPlain))
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        else -> JsonPrimitive(value.toString())
    }
}
