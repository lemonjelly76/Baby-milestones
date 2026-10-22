package io.github.lemonjelly76.babymilestones.server.tools

import io.github.lemonjelly76.babymilestones.core.content.ContentBundle
import io.github.lemonjelly76.babymilestones.core.json.BabyJson
import io.github.lemonjelly76.babymilestones.server.service.BabyService
import io.github.lemonjelly76.babymilestones.server.service.Writer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The tools for one area, defined once and served over MCP and to the in-app assistant. Each set is a Spring bean;
 * [ToolCatalog] lists them by [order], so the tool list (and the assistant's cached prompt) stays stable.
 */
abstract class ToolSet(protected val babies: BabyService, protected val content: ContentBundle) {
    abstract val order: Int
    abstract val tools: List<BabyTool>

    /** A tool result: the value's JSON (a list goes under "items") plus the content version it came from. */
    protected fun <T> json(serializer: KSerializer<T>, value: T, extra: Map<String, JsonElement> = emptyMap()): JsonElement {
        val encoded = BabyJson.encodeToJsonElement(serializer, value)
        val fields = if (encoded is JsonObject) encoded.toMap() else mapOf("items" to encoded)
        return JsonObject(fields + extra + ("content_version" to JsonPrimitive(content.version)))
    }

    protected fun <E : Enum<E>> serialName(serializer: KSerializer<E>, value: E) = serializer.descriptor.getElementName(value.ordinal)

    protected fun names(serializer: KSerializer<out Enum<*>>) =
        (0 until serializer.descriptor.elementsCount).map(serializer.descriptor::getElementName)

    protected fun <E : Enum<E>> enumValue(serializer: KSerializer<E>, values: Array<E>, name: String, text: String?): E? =
        text?.let { t -> values.firstOrNull { serialName(serializer, it) == t } ?: throw ToolInputException("Unknown $name '$t'") }

    protected fun ctx(caller: ToolCaller, args: Args) = babies.context(caller.familyId, args.string("baby_id"))

    protected fun writer(caller: ToolCaller) = Writer(caller.familyId, caller.carerId, caller.source)

    /** A date as a parent would say it on a confirm chip. */
    protected fun day(date: LocalDate?, today: LocalDate) = when (date) {
        null, today -> "today"
        today.minusDays(1) -> "yesterday"
        else -> date.format(DAY)
    }

    private companion object {
        val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK)
    }
}
