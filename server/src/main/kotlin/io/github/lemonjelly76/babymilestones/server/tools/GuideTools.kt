package io.github.lemonjelly76.babymilestones.server.tools

import io.github.lemonjelly76.babymilestones.core.content.ContentBundle
import io.github.lemonjelly76.babymilestones.core.guides.Guides
import io.github.lemonjelly76.babymilestones.core.notes.AssistantNote
import io.github.lemonjelly76.babymilestones.core.notes.NoteKind
import io.github.lemonjelly76.babymilestones.server.service.BabyService
import io.github.lemonjelly76.babymilestones.server.service.GuideService
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.springframework.stereotype.Component

/** Guides with this baby's checklist ticks, and the notes Claude saves for the family. */
@Component
class GuideTools(
    babies: BabyService,
    content: ContentBundle,
    private val guides: GuideService,
) : ToolSet(babies, content) {
    override val order = 2

    private fun checklistText(args: Args) = args.string("item_id").let { id ->
        content.checklistItem(id)?.second?.text ?: throw ToolInputException("Unknown checklist item $id; use get_guides")
    }

    override val tools = listOf(
        BabyTool(
            "get_guides", "Guides and checklists",
            "Practical guides for the baby's age (weaning, allergens, choking, foods to avoid, drinks, vitamins, teeth, " +
                "sun safety, safer sleep, when to get urgent help, potty training, childcare, starting school and more) with this " +
                "baby's checklist ticks. Without guide_id: the guides for now and coming up. With guide_id: the full guide with " +
                "its key points, checklist, sources, and links to trusted videos, tools and support (NHS, charities, GOV.UK) " +
                "to pass on to the parent.",
            schema {
                string("baby_id", "Id from list_babies", required = true)
                string("guide_id", "e.g. guide.baby_led_weaning")
            }, write = false,
        ) { caller, args ->
            val ctx = ctx(caller, args)
            val id = args.optString("guide_id")
            if (id != null) {
                val guide = content.guide(id) ?: throw ToolInputException("Unknown guide $id; call get_guides without guide_id")
                json(GuideView.serializer(), FoodAndGuideViews.guide(content, guide, ctx.ticks, ctx.region, ctx.baby.dateOfBirth))
            } else {
                val months = ctx.snapshot.ages.chronological.months
                val forAge = Guides.forAge(content.guides, months)
                json(GuidesView.serializer(), GuidesView(
                    months,
                    forAge.now.map { FoodAndGuideViews.summary(it, ctx.ticks) },
                    forAge.comingUp.map { FoodAndGuideViews.summary(it, ctx.ticks) },
                ))
            }
        },

        BabyTool(
            "list_notes", "Saved notes",
            "Notes saved with save_note: earlier check-in summaries, answers, plans and meal ideas, newest first.",
            schema {
                string("baby_id", "Id from list_babies", required = true)
                integer("limit", "How many; default 10")
            }, write = false,
        ) { caller, args ->
            val ctx = ctx(caller, args)
            json(ListSerializer(AssistantNote.serializer()), ctx.notes.take((args.optInt("limit") ?: 10).coerceIn(1, 50)))
        },

        BabyTool(
            "update_checklist", "Tick a checklist item",
            "Ticks or unticks an item on a guide's checklist, such as check.ready_sits. Item ids come from get_guides.",
            schema {
                string("baby_id", "Id from list_babies", required = true)
                string("item_id", "Checklist item id from get_guides", required = true)
                boolean("done", "True to tick, false to untick", required = true)
            }, write = true,
            confirm = { args, today ->
                (if (args.optBoolean("done") == false) "Untick: " else "Tick: ") + checklistText(args)
            },
        ) { caller, args ->
            val done = args.optBoolean("done") ?: throw ToolInputException("Missing 'done'")
            guides.setChecklistItem(writer(caller), args.string("baby_id"), args.string("item_id"), done)
            buildJsonObject { put("item_id", args.string("item_id")); put("done", done); put("content_version", content.version) }
        },

        BabyTool(
            "save_note", "Save a note for the family",
            "Saves something you wrote so the family can read it later in the app's Claude notes: a check-in summary, an " +
                "answer worth keeping, a plan or meal ideas. Write it for the parent, in plain words, with sources named. " +
                "Short Markdown is fine: headings with #, bullets with -, bold with **. Never include a diagnosis.",
            schema {
                string("baby_id", "Id from list_babies", required = true)
                string("kind", "What it is", required = true, enum = names(NoteKind.serializer()))
                string("title", "A short title, e.g. 'Check-in, 22 October'", required = true)
                string("body", "The note", required = true)
            }, write = true,
            confirm = { args, today ->
                "Save note: ${args.string("title")}"
            },
        ) { caller, args ->
            val kind = enumValue(NoteKind.serializer(), NoteKind.entries.toTypedArray(), "kind", args.string("kind"))!!
            val note = guides.saveNote(writer(caller), args.string("baby_id"), kind, args.string("title"), args.string("body"))
            json(AssistantNote.serializer(), note)
        },
    )
}
