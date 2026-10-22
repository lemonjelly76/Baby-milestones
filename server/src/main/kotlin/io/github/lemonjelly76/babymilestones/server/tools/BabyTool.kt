package io.github.lemonjelly76.babymilestones.server.tools

import io.github.lemonjelly76.babymilestones.core.tracking.LogSource
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Who called a tool. Tools only ever see this family's data. */
data class ToolCaller(val familyId: String, val carerId: String?, val source: LogSource, val canEdit: Boolean = true)

/**
 * One tool, defined once and served both over MCP and to the in-app assistant.
 * Write tools never run straight from the in-app assistant: it turns them into confirm chips (see AssistantService).
 */
class BabyTool(
    val name: String,
    val title: String,
    val description: String,
    val inputSchema: JsonObject,
    val write: Boolean,
    val destructive: Boolean = false,
    /** For write tools: the words on the in-app confirm chip. It also checks ids, so a bad one fails before the parent sees it. */
    val confirm: ((Args, LocalDate) -> String)? = null,
    val handler: (ToolCaller, Args) -> JsonElement,
)

class ToolInputException(message: String) : RuntimeException(message)

/** Typed access to a tool's JSON arguments, with errors the model can act on. */
class Args(val raw: JsonObject) {
    fun string(name: String): String = optString(name) ?: throw ToolInputException("Missing '$name'")
    fun optString(name: String): String? = (raw[name] as? JsonPrimitive)?.contentOrNull?.trim()?.ifEmpty { null }
    fun optInt(name: String): Int? = (raw[name] as? JsonPrimitive)?.let { it.intOrNull ?: throw ToolInputException("'$name' must be a whole number") }
    fun optBoolean(name: String): Boolean? = (raw[name] as? JsonPrimitive)?.booleanOrNull
    fun optDouble(name: String): Double? = (raw[name] as? JsonPrimitive)?.let {
        it.doubleOrNull ?: throw ToolInputException("'$name' must be a number")
    }
    fun date(name: String): LocalDate = optDate(name) ?: throw ToolInputException("Missing '$name'")
    fun optDate(name: String): LocalDate? = optString(name)?.let {
        try {
            LocalDate.parse(it)
        } catch (e: DateTimeParseException) {
            throw ToolInputException("'$name' must be a date like 2026-10-21")
        }
    }
    fun objects(name: String): List<Args> =
        (raw[name] as? JsonArray)?.map { Args(it as? JsonObject ?: throw ToolInputException("Each of '$name' must be an object")) }
            ?: throw ToolInputException("Missing '$name'")

    fun strings(name: String): List<String> =
        (raw[name] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
}

/** A small builder for the JSON Schema of a tool's input. */
class SchemaBuilder {
    private val properties = mutableMapOf<String, JsonObject>()
    private val required = mutableListOf<String>()

    fun string(name: String, description: String, required: Boolean = false, enum: List<String>? = null) =
        add(name, required, buildJsonObject {
            put("type", "string")
            put("description", description)
            enum?.let { values -> putJsonArray("enum") { values.forEach { add(JsonPrimitive(it)) } } }
        })

    fun date(name: String, description: String, required: Boolean = false) =
        add(name, required, buildJsonObject {
            put("type", "string")
            put("format", "date")
            put("description", "$description (YYYY-MM-DD)")
        })

    fun integer(name: String, description: String, required: Boolean = false) =
        add(name, required, buildJsonObject { put("type", "integer"); put("description", description) })

    fun number(name: String, description: String, required: Boolean = false) =
        add(name, required, buildJsonObject { put("type", "number"); put("description", description) })

    /** A list of objects, each described by [item]. */
    fun objects(name: String, description: String, required: Boolean = false, item: SchemaBuilder.() -> Unit) =
        add(name, required, buildJsonObject {
            put("type", "array")
            put("description", description)
            put("items", SchemaBuilder().apply(item).build())
        })

    fun boolean(name: String, description: String, required: Boolean = false) =
        add(name, required, buildJsonObject { put("type", "boolean"); put("description", description) })

    private fun add(name: String, isRequired: Boolean, schema: JsonObject) {
        properties[name] = schema
        if (isRequired) required += name
    }

    fun build(): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { properties.forEach { (k, v) -> put(k, v) } }
        putJsonArray("required") { required.forEach { add(JsonPrimitive(it)) } }
        put("additionalProperties", false)
    }
}

fun schema(block: SchemaBuilder.() -> Unit): JsonObject = SchemaBuilder().apply(block).build()
