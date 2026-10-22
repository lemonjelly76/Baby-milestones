package io.github.lemonjelly76.babymilestones.server.tools

import io.github.lemonjelly76.babymilestones.core.content.ContentBundle
import io.github.lemonjelly76.babymilestones.core.growth.GrowthMeasurement
import io.github.lemonjelly76.babymilestones.core.growth.GrowthReport
import io.github.lemonjelly76.babymilestones.core.json.BabyJson
import io.github.lemonjelly76.babymilestones.core.records.Contact
import io.github.lemonjelly76.babymilestones.core.records.FirstWord
import io.github.lemonjelly76.babymilestones.core.records.ToothRecord
import io.github.lemonjelly76.babymilestones.server.service.BabyService
import io.github.lemonjelly76.babymilestones.server.service.ChildRecordService
import io.github.lemonjelly76.babymilestones.server.service.GrowthInput
import io.github.lemonjelly76.babymilestones.server.store.FamilyStore
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonPrimitive
import org.springframework.stereotype.Component

/** Growth, first words, teeth and the family's contacts. */
@Component
class RecordTools(
    babies: BabyService,
    content: ContentBundle,
    private val childRecords: ChildRecordService,
    private val families: FamilyStore,
) : ToolSet(babies, content) {
    override val order = 3

    private fun toothName(args: Args) = args.string("tooth").let { id ->
        content.tooth(id)?.name ?: throw ToolInputException("Unknown tooth $id; use get_firsts")
    }

    override val tools = listOf(
        BabyTool(
            "get_growth", "Growth",
            "Weight, length or height and head measurements with their centiles on the WHO growth standard used by the " +
                "UK-WHO charts (corrected for gestation where the charts are), plus any signpost to use. Explain a centile " +
                "as 'about N in 100 children of the same age and sex are lighter or shorter'; never as good or bad.",
            schema { string("baby_id", "Id from list_babies", required = true) }, write = false,
        ) { caller, args ->
            val ctx = ctx(caller, args)
            json(GrowthReport.serializer(), ctx.growth, mapOf(
                "standard" to JsonPrimitive(content.growth!!.standard),
                "sources" to BabyJson.encodeToJsonElement(ListSerializer(SourceRef.serializer()), ToolViews.sources(content, content.growth!!.sources)),
            ))
        },

        BabyTool(
            "get_firsts", "First words and teeth",
            "The words the child says, with when each was first said, and their milk teeth: which have come through and " +
                "when, and the ones still to come with the age they usually appear (NHS).",
            schema { string("baby_id", "Id from list_babies", required = true) }, write = false,
        ) { caller, args ->
            val ctx = ctx(caller, args)
            val cameOn = ctx.teeth.associateBy({ it.toothId }, { it.cameOn })
            json(FirstsView.serializer(), FirstsView(
                ctx.words,
                content.teeth.map { ToothView(it.id, it.name, it.typicalMonths, cameOn[it.id]) },
                ToolViews.sources(content, content.teeth.flatMap { it.sources }.distinct()),
            ))
        },

        BabyTool(
            "get_contacts", "Contacts",
            "The family's contacts: health visitor, GP surgery, midwife, nursery, dentist and others, with phone numbers.",
            schema { }, write = false,
        ) { caller, _ ->
            json(ListSerializer(Contact.serializer()), families.contacts(caller.familyId))
        },

        BabyTool(
            "log_growth", "Log a measurement",
            "Records a weighing or measurement: weight in kg, length or height in cm, head circumference in cm (any of " +
                "them). Only numbers the parent gave, converted to kg and cm if they used pounds or inches.",
            schema {
                string("baby_id", "Id from list_babies", required = true)
                date("measured_on", "When; default today")
                number("weight_kg", "Weight in kilograms")
                number("length_cm", "Length or height in centimetres")
                number("head_cm", "Head circumference in centimetres")
                string("measured_by", "Who measured, e.g. Health visitor, GP, Home")
            }, write = true,
            confirm = { args, today ->
                "Measurement ${day(args.optDate("measured_on"), today)}: " + listOfNotNull(
                args.optDouble("weight_kg")?.let { "$it kg" }, args.optDouble("length_cm")?.let { "$it cm long" },
                args.optDouble("head_cm")?.let { "head $it cm" },
            ).joinToString()
            },
        ) { caller, args ->
            val m = childRecords.logGrowth(writer(caller), args.string("baby_id"), GrowthInput(
                args.optDate("measured_on"), args.optDouble("weight_kg"), args.optDouble("length_cm"), args.optDouble("head_cm"),
                args.optString("measured_by"),
            ))
            json(GrowthMeasurement.serializer(), m)
        },

        BabyTool(
            "log_word", "Log a word",
            "Adds a word the child says to their words list, spelled the way the family says it. One call per word.",
            schema {
                string("baby_id", "Id from list_babies", required = true)
                string("word", "The word, e.g. dada", required = true)
                date("said_on", "When it was first said; default today")
                string("note", "What it means or when they say it")
            }, write = true,
            confirm = { args, today ->
                "New word: \u201c${args.string("word")}\u201d — ${day(args.optDate("said_on"), today)}"
            },
        ) { caller, args ->
            json(FirstWord.serializer(), childRecords.logWord(writer(caller), args.string("baby_id"), args.string("word"),
                args.optDate("said_on"), args.optString("note")))
        },

        BabyTool(
            "log_tooth", "Log a tooth",
            "Records a milk tooth coming through. Tooth ids are FDI numbers from get_firsts: 71 and 81 are the lower " +
                "front teeth, 51 and 61 the upper front teeth.",
            schema {
                string("baby_id", "Id from list_babies", required = true)
                string("tooth", "Tooth id from get_firsts", required = true)
                date("came_on", "When it came through; default today")
            }, write = true,
            confirm = { args, today ->
                "Tooth: ${toothName(args)} — ${day(args.optDate("came_on"), today)}"
            },
        ) { caller, args ->
            json(ToothRecord.serializer(), childRecords.logTooth(writer(caller), args.string("baby_id"), args.string("tooth"), args.optDate("came_on")))
        },
    )
}
