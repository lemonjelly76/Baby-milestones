package io.github.lemonjelly76.babymilestones.server.assistant

import io.github.lemonjelly76.babymilestones.core.content.ContentBundle
import io.github.lemonjelly76.babymilestones.core.content.SignpostCopy
import io.github.lemonjelly76.babymilestones.core.food.Liking
import io.github.lemonjelly76.babymilestones.core.safety.SafetyCheck
import io.github.lemonjelly76.babymilestones.core.safety.SafetyKind
import io.github.lemonjelly76.babymilestones.core.tracking.LogSource
import io.github.lemonjelly76.babymilestones.server.security.CarerPrincipal
import io.github.lemonjelly76.babymilestones.server.service.BabyService
import io.github.lemonjelly76.babymilestones.server.store.Ids
import io.github.lemonjelly76.babymilestones.server.tools.Args
import io.github.lemonjelly76.babymilestones.server.tools.BabyTool
import io.github.lemonjelly76.babymilestones.server.tools.ToolCaller
import io.github.lemonjelly76.babymilestones.server.tools.ToolCatalog
import io.github.lemonjelly76.babymilestones.server.tools.ToolInputException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A write the assistant suggested, shown to the parent as a confirm chip. Nothing is saved until they confirm. */
@Serializable
data class Proposal(val id: String, val tool: String, val input: JsonObject, val summary: String)

@Serializable
data class AssistantReply(
    val reply: String? = null,
    val proposals: List<Proposal> = emptyList(),
    val safety: SafetyCard? = null,
)

@Serializable
data class SafetyCard(val kind: SafetyKind, val card: SignpostCopy)

@Serializable
data class ConfirmResult(val proposalId: String, val tool: String, val ok: Boolean, val result: JsonElement? = null, val error: String? = null)

@Service
class AssistantService(
    private val model: AssistantModel,
    private val catalog: ToolCatalog,
    private val babies: BabyService,
    private val content: ContentBundle,
    private val safety: SafetyCheck,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val MAX_HISTORY = 20

        val STABLE_PROMPT = """
            You are the assistant inside a baby development app used by parents and carers in the UK. You help them
            understand where their child is developmentally, log milestones, and get ready for health visitor reviews.

            How to work:
            - Answer only from the tools: the app's milestone and period content and this baby's own log. Always read
              before writing, and never invent a milestone id, a date or a fact the tools didn't give you.
            - To log something the parent says ("she said dada yesterday and can sit without help"), find the milestone
              ids with search_guidance, then call log_milestone (or mark_not_sure) once per milestone with the date they
              gave. These calls only propose the change: the parent confirms them in the app, so say "tap to confirm",
              never that it is saved.
            - For food, use get_foods and log_food (one call per food the parent mentions); for everyday questions
              (weaning, allergens, drinks, teeth, potty training, nursery, school), use get_guides.
            - Use the baby's name. Milestones use corrected age for babies born 3 or more weeks early, until 2 years;
              appointments and vaccinations use chronological age. Say which age you mean.
            - The milestone checklists (CDC/AAP 2022) list what most children (75% or more) do by each age. Not doing
              something by then is a reason to watch, try activities and maybe mention it to a health visitor, not a
              sign of being "behind average". Where a measured range exists (for example the WHO windows for sitting
              and walking), give it: it reassures.
            - Name the source when you give guidance (for example "the CDC checklist", "WHO guidance", "NHS advice").
            - Keep replies short enough to read on one phone screen. Offer to go deeper rather than writing more.

            Safety rules, which always win:
            - Never diagnose, never suggest a condition name, never give a screening score.
            - Never give medication or dose advice: point to a pharmacist, the GP or NHS 111.
            - If get_concerns returns anything, use its copy and its signpost. If you're unsure, signpost to the
              health visitor or GP.
            - If the parent describes their child being unwell or injured, stop and tell them to call NHS 111, or 999
              in an emergency.
            - If the parent sounds distressed, low or not coping, respond kindly, don't talk about milestones, and
              point them to their health visitor or GP, NHS 111 (mental health option) or Samaritans on 116 123.
        """.trimIndent()

        private val DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK)
    }

    fun reply(principal: CarerPrincipal, babyId: String, history: List<ChatMessage>): AssistantReply {
        val conversation = history.takeLast(MAX_HISTORY).dropWhile { it.role != ChatMessage.Role.USER }
        val last = conversation.lastOrNull { it.role == ChatMessage.Role.USER }
            ?: throw IllegalArgumentException("Send at least one message from the parent")

        // The fixed safety check runs before any model call; urgent symptoms and distress never reach the model.
        val check = safety.classify(last.text)
        val card = check.card?.let { SafetyCard(check.kind, it) }
        if (check.stopsAssistant) return AssistantReply(safety = card)

        val ctx = babies.context(principal.familyId, babyId)
        if (!model.available) {
            return AssistantReply("The assistant isn't set up on this server yet. Everything else in the app still works.", safety = card)
        }

        val caller = ToolCaller(principal.familyId, principal.carerId, LogSource.ASSISTANT)
        val proposals = mutableListOf<Proposal>()
        val others = babies.babies(principal.familyId).filter { it.id != babyId }
        val context = buildString {
            append("Today is ${ctx.today.format(DATE)}. ")
            append("You are talking about ${ctx.baby.name} (baby_id ${ctx.baby.id}), born ${ctx.baby.dateOfBirth.format(DATE)}")
            if (ctx.baby.dobEstimated) append(" (date of birth estimated)")
            append(", now ${ctx.snapshot.ages.describe()}; milestones use ${ctx.snapshot.ages.milestoneBasis.name.lowercase()} age. ")
            append("Current period: ${ctx.snapshot.period.name}.")
            if (others.isNotEmpty()) append(" Other children in the family: " + others.joinToString { "${it.name} (${it.id})" } + ".")
        }

        val reply = model.converse(SystemPrompt(STABLE_PROMPT, context), conversation, catalog.tools) { call ->
            val tool = catalog.find(call.name) ?: return@converse ToolOutcome("Unknown tool ${call.name}", isError = true)
            if (tool.write) propose(tool, call.input, ctx.today, proposals) else run(tool, caller, call.input)
        }
        return AssistantReply(reply, proposals, card)
    }

    /** Runs confirmed proposals. Each carries the input the parent saw; the ids are re-checked by the tools themselves. */
    fun confirm(principal: CarerPrincipal, proposals: List<Proposal>): List<ConfirmResult> {
        val caller = ToolCaller(principal.familyId, principal.carerId, LogSource.ASSISTANT)
        return proposals.map { proposal ->
            val tool = catalog.find(proposal.tool)?.takeIf { it.write }
                ?: return@map ConfirmResult(proposal.id, proposal.tool, false, error = "Not a write tool")
            try {
                ConfirmResult(proposal.id, proposal.tool, true, tool.handler(caller, Args(proposal.input)))
            } catch (e: RuntimeException) {
                ConfirmResult(proposal.id, proposal.tool, false, error = e.message)
            }
        }
    }

    private fun run(tool: BabyTool, caller: ToolCaller, input: JsonObject): ToolOutcome = try {
        ToolOutcome(tool.handler(caller, Args(input)).toString())
    } catch (e: ToolInputException) {
        ToolOutcome(e.message ?: "Invalid input", isError = true)
    } catch (e: RuntimeException) {
        log.info("Assistant tool {} failed: {}", tool.name, e.message)
        ToolOutcome(e.message ?: "The tool failed", isError = true)
    }

    private fun propose(tool: BabyTool, input: JsonObject, today: LocalDate, proposals: MutableList<Proposal>): ToolOutcome {
        val args = Args(input)
        val summary = try {
            summarise(tool.name, args, today)
        } catch (e: ToolInputException) {
            return ToolOutcome(e.message ?: "Invalid input", isError = true)
        }
        val proposal = Proposal(Ids.new("p", 10), tool.name, input, summary)
        proposals += proposal
        return ToolOutcome(buildJsonObject {
            put("status", "proposed")
            put("proposal_id", proposal.id)
            put("shown_to_parent_as", summary)
            put("note", "Shown to the parent as a confirm chip. It is not saved until they tap confirm.")
        }.toString())
    }

    /** The words on the confirm chip; also validates ids so the model hears about a bad id before the parent does. */
    private fun summarise(tool: String, args: Args, today: LocalDate): String {
        fun milestoneTitle() = args.string("milestone_id").let {
            content.milestone(it)?.title ?: throw ToolInputException("Unknown milestone $it; use search_guidance to find the id")
        }
        fun day(date: LocalDate?) = when (date) {
            null, today -> "today"
            today.minusDays(1) -> "yesterday"
            else -> date.format(DATE)
        }
        return when (tool) {
            "log_milestone" -> "${milestoneTitle()} — ${if (args.optBoolean("approximate") == true) "around " else ""}${day(args.optDate("achieved_on"))}"
            "mark_not_sure" -> "Not sure yet: ${milestoneTitle()}"
            "update_milestone_log" -> "Update log entry" + (args.optDate("achieved_on")?.let { " to ${day(it)}" } ?: "") +
                (args.optString("note")?.let { ": \"$it\"" } ?: "")
            "remove_milestone_log" -> "Remove log entry ${args.string("log_id")}"
            "add_moment" -> "Moment: ${args.string("title")} — ${day(args.date("date"))}"
            "update_appointment" -> {
                val id = args.string("appointment_id")
                val title = content.schedules.flatMap { it.appointments }.firstOrNull { it.id == id }?.title
                    ?: throw ToolInputException("Unknown appointment $id; use get_schedule")
                "$title: ${args.string("status").replace('_', ' ')}" + (args.optDate("date")?.let { " ${it.format(DATE)}" } ?: "")
            }
            "record_vaccination" -> {
                val code = args.string("dose_id")
                val dose = content.schedules.flatMap { it.doses }.firstOrNull { it.code == code }
                    ?: throw ToolInputException("Unknown dose $code; use get_schedule")
                "${dose.vaccine} (${dose.dose}) given ${day(args.date("given_on"))}"
            }
            "add_review_note" -> "Question for the health visitor: ${args.string("text")}"
            "log_food" -> {
                val text = args.string("food")
                "Tried ${content.findFood(text)?.name ?: text} — ${day(args.optDate("tasted_on"))}" +
                    (args.optString("liking")?.let { l -> Liking.entries.firstOrNull { it.name.lowercase() == l }?.let { " (${it.label.lowercase()})" } } ?: "")
            }
            "remove_food_tasting" -> "Remove food entry ${args.string("tasting_id")}"
            "update_checklist" -> {
                val id = args.string("item_id")
                val item = content.checklistItem(id)?.second ?: throw ToolInputException("Unknown checklist item $id; use get_guides")
                (if (args.optBoolean("done") == false) "Untick: " else "Tick: ") + item.text
            }
            "save_note" -> "Save note: ${args.string("title")}"
            "log_growth" -> "Measurement ${day(args.optDate("measured_on"))}: " + listOfNotNull(
                args.optDouble("weight_kg")?.let { "$it kg" }, args.optDouble("length_cm")?.let { "$it cm long" },
                args.optDouble("head_cm")?.let { "head $it cm" },
            ).joinToString()
            "log_word" -> "New word: \u201c${args.string("word")}\u201d — ${day(args.optDate("said_on"))}"
            "log_tooth" -> {
                val id = args.string("tooth")
                "Tooth: ${content.tooth(id)?.name ?: throw ToolInputException("Unknown tooth $id; use get_firsts")} — ${day(args.optDate("came_on"))}"
            }
            else -> tool
        }
    }
}
