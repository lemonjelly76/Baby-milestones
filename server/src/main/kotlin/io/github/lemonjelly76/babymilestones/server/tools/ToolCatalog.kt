package io.github.lemonjelly76.babymilestones.server.tools

import io.github.lemonjelly76.babymilestones.core.concerns.Concern
import io.github.lemonjelly76.babymilestones.core.content.ContentBundle
import io.github.lemonjelly76.babymilestones.core.content.Domain
import io.github.lemonjelly76.babymilestones.core.guidance.Guidance
import io.github.lemonjelly76.babymilestones.core.guidance.GuidancePassage
import io.github.lemonjelly76.babymilestones.core.guidance.GuidanceSearch
import io.github.lemonjelly76.babymilestones.core.guidance.PendingUpdates
import io.github.lemonjelly76.babymilestones.core.guidance.WhatsNext
import io.github.lemonjelly76.babymilestones.core.json.BabyJson
import io.github.lemonjelly76.babymilestones.core.schedule.AppointmentRecord
import io.github.lemonjelly76.babymilestones.core.schedule.DoseRecord
import io.github.lemonjelly76.babymilestones.core.summary.ReviewNote
import io.github.lemonjelly76.babymilestones.core.timeline.Moment
import io.github.lemonjelly76.babymilestones.core.timeline.Timeline
import io.github.lemonjelly76.babymilestones.core.timeline.TimelineEntry
import io.github.lemonjelly76.babymilestones.core.tracking.MilestoneLog
import io.github.lemonjelly76.babymilestones.core.tracking.MilestoneStatus
import io.github.lemonjelly76.babymilestones.core.food.FoodGroup
import io.github.lemonjelly76.babymilestones.core.food.FoodNames
import io.github.lemonjelly76.babymilestones.core.food.FoodReport
import io.github.lemonjelly76.babymilestones.core.food.FoodTasting
import io.github.lemonjelly76.babymilestones.core.food.Liking
import io.github.lemonjelly76.babymilestones.core.guides.Guides
import io.github.lemonjelly76.babymilestones.core.notes.AssistantNote
import io.github.lemonjelly76.babymilestones.core.notes.NoteKind
import io.github.lemonjelly76.babymilestones.server.service.BabyService
import io.github.lemonjelly76.babymilestones.server.service.FoodService
import io.github.lemonjelly76.babymilestones.server.service.GuideService
import io.github.lemonjelly76.babymilestones.server.service.TastingInput
import io.github.lemonjelly76.babymilestones.server.service.LogChanges
import io.github.lemonjelly76.babymilestones.server.service.LogInput
import io.github.lemonjelly76.babymilestones.server.service.RecordService
import io.github.lemonjelly76.babymilestones.server.service.Writer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.KSerializer
import io.github.lemonjelly76.babymilestones.server.store.BabyStore
import org.springframework.stereotype.Component

/** The MCP tools from the spec. Each returns JSON plus the content version, so answers can say where they came from. */
@Component
class ToolCatalog(
    private val babies: BabyService,
    private val records: RecordService,
    private val foods: FoodService,
    private val guides: GuideService,
    private val content: ContentBundle,
    private val search: GuidanceSearch,
    private val babyStore: BabyStore,
) {
    val tools: List<BabyTool> = readTools() + everydayReadTools() + writeTools() + everydayWriteTools()

    fun find(name: String): BabyTool? = tools.firstOrNull { it.name == name }

    private fun <T> json(serializer: KSerializer<T>, value: T, extra: Map<String, JsonElement> = emptyMap()): JsonElement {
        val encoded = BabyJson.encodeToJsonElement(serializer, value)
        val fields = if (encoded is JsonObject) encoded.toMap() else mapOf("items" to encoded)
        return JsonObject(fields + extra + ("content_version" to JsonPrimitive(content.version)))
    }

    private fun <E : Enum<E>> serialName(serializer: KSerializer<E>, value: E) = serializer.descriptor.getElementName(value.ordinal)

    private fun ctx(caller: ToolCaller, args: Args) = babies.context(caller.familyId, args.string("baby_id"))

    private fun writer(caller: ToolCaller) = Writer(caller.familyId, caller.carerId, caller.source)

    private fun readTools() = listOf(
        BabyTool(
            "list_babies", "List babies",
            "The babies in this family with their chronological and corrected ages and current developmental period. " +
                "Start here to get baby ids.",
            schema { }, write = false,
        ) { caller, _ ->
            val items = babies.babies(caller.familyId).map { ToolViews.babyItem(babies.context(caller.familyId, it.id)) }
            json(ListSerializer(BabyListItem.serializer()), items)
        },

        BabyTool(
            "get_baby_status", "Baby status",
            "Ages (chronological and corrected, and which one milestones use), current period, how many milestones are " +
                "in each status, the next appointment and the date of the last log entry.",
            schema { string("baby_id", "Id from list_babies", required = true) }, write = false,
        ) { caller, args ->
            val ctx = ctx(caller, args)
            val next = ctx.schedule.nextAppointment()
            json(BabyListItem.serializer(), ToolViews.babyItem(ctx), mapOf(
                "counts" to JsonObject(ctx.snapshot.counts().filterValues { it > 0 }
                    .map { (status, count) -> serialName(MilestoneStatus.serializer(), status) to JsonPrimitive(count) }
                    .toMap()),
                "next_appointment" to (next?.let {
                    buildJsonObject {
                        put("id", it.rule.id); put("title", it.rule.title)
                        put("window_start", it.windowStart.toString()); put("window_end", it.windowEnd.toString())
                        it.record?.bookedOn?.let { d -> put("booked_on", d.toString()) }
                    }
                } ?: JsonPrimitive(null as String?)),
                "last_log_date" to JsonPrimitive(ctx.snapshot.lastLogDate()?.toString()),
                "open_concerns" to JsonPrimitive(ctx.concerns.size),
                "today" to JsonPrimitive(ctx.today.toString()),
            ))
        },

        BabyTool(
            "get_pending_updates", "What needs updating",
            "What needs updating for a baby: milestones Due now and Not seen yet, Not sure answers due a re-check, " +
                "appointments whose window has passed without being marked, and vaccinations due. Use this to run a check-in.",
            schema { string("baby_id", "Id from list_babies", required = true) }, write = false,
        ) { caller, args ->
            val ctx = ctx(caller, args)
            BabyJson.encodeToJsonElement(
                PendingUpdates.serializer(),
                Guidance.pendingUpdates(ctx.snapshot, ctx.schedule),
            )
        },

        BabyTool(
            "list_milestones", "List milestones",
            "Milestones with their derived status and any log entry. Filter by status, domain or period to keep the " +
                "result short. Statuses: later, coming_up, due_now, achieved, not_seen_yet, not_sure, skill_lost.",
            schema {
                string("baby_id", "Id from list_babies", required = true)
                string("status", "Only this status", enum = MilestoneStatus.entries.map { serialName(MilestoneStatus.serializer(), it) })
                string("domain", "Only this domain", enum = listOf("social", "language", "cognitive", "movement"))
                string("period", "Only milestones on this period's checklist, e.g. infant_4_6m")
            }, write = false,
        ) { caller, args ->
            val ctx = ctx(caller, args)
            val status = args.optString("status")
            val domain = args.optString("domain")
            val period = args.optString("period")?.let { content.period(it) ?: throw ToolInputException("Unknown period $it") }
            val items = ctx.snapshot.milestones
                .filter { status == null || serialName(MilestoneStatus.serializer(), it.status) == status }
                .filter { domain == null || serialName(Domain.serializer(), it.milestone.domain) == domain }
                .filter { period == null || it.milestone.expectedBy == period.checklistMonths }
                .map(ToolViews::item)
            json(ListSerializer(MilestoneItem.serializer()), items)
        },

        BabyTool(
            "get_milestone", "Milestone details",
            "Full content for one milestone: what it looks like, activities to encourage it, what typically follows, " +
                "what to do if it isn't seen by its checklist age, the measured age range where one exists, and sources.",
            schema { string("milestone_id", "e.g. move.rolls_tummy_to_back", required = true) }, write = false,
        ) { _, args ->
            val id = args.string("milestone_id")
            val m = content.milestone(id) ?: throw ToolInputException("Unknown milestone $id; use search_guidance or list_milestones")
            json(MilestoneDetail.serializer(), ToolViews.detail(content, m))
        },

        BabyTool(
            "get_period_guide", "Period guide",
            "The guide for a developmental period: what's typical, sleep and feeding notes, play ideas, safety for new " +
                "abilities, upcoming touchpoints and what happens next. Give baby_id for their current period, or period_id.",
            schema {
                string("baby_id", "Id from list_babies, for the baby's current period")
                string("period_id", "A period id such as mover_6_9m")
            }, write = false,
        ) { caller, args ->
            val ctx = args.optString("baby_id")?.let { babies.context(caller.familyId, it) }
            val period = args.optString("period_id")?.let { content.period(it) ?: throw ToolInputException("Unknown period $it") }
                ?: ctx?.snapshot?.period
                ?: throw ToolInputException("Give baby_id or period_id")
            json(PeriodGuideView.serializer(), ToolViews.period(content, period, ctx))
        },

        BabyTool(
            "get_whats_next", "What's next",
            "What to watch for next: milestones that typically follow recent achievements, milestones whose window " +
                "opens soon, the next period, upcoming appointments and vaccinations, and one activity to try.",
            schema {
                string("baby_id", "Id from list_babies", required = true)
                integer("horizon_weeks", "How far ahead to look; default 8")
            }, write = false,
        ) { caller, args ->
            val ctx = ctx(caller, args)
            val horizon = (args.optInt("horizon_weeks") ?: 8).coerceIn(1, 52)
            BabyJson.encodeToJsonElement(
                WhatsNext.serializer(),
                Guidance.whatsNext(ctx.snapshot, ctx.schedule, content, horizon),
            )
        },

        BabyTool(
            "get_concerns", "Concerns",
            "Anything over the content's concern thresholds, each with the copy to use, who to contact and how urgently. " +
                "Empty means nothing is over a threshold. Never add a diagnosis or condition name to these.",
            schema { string("baby_id", "Id from list_babies", required = true) }, write = false,
        ) { caller, args ->
            val ctx = ctx(caller, args)
            json(ListSerializer(Concern.serializer()), ctx.concerns, mapOf(
                "disclaimer" to JsonPrimitive(content.safety.disclaimer),
                "sources" to BabyJson.encodeToJsonElement(ListSerializer(SourceRef.serializer()), ToolViews.sources(content, content.concerns.sources)),
            ))
        },

        BabyTool(
            "get_timeline", "Timeline",
            "Logged milestones, moments, appointments and vaccinations in date order, newest first.",
            schema {
                string("baby_id", "Id from list_babies", required = true)
                date("from", "Earliest date to include")
                date("to", "Latest date to include")
            }, write = false,
        ) { caller, args ->
            val ctx = ctx(caller, args)
            val entries = Timeline.build(ctx.snapshot, ctx.moments, ctx.schedule, args.optDate("from"), args.optDate("to"))
            json(ListSerializer(TimelineEntry.serializer()), entries)
        },

        BabyTool(
            "get_schedule", "Appointments and vaccinations",
            "Health visitor reviews, GP checks and the vaccination schedule for this baby's date of birth and region, " +
                "with what has been booked, done or given. Appointments and vaccinations always use chronological age.",
            schema { string("baby_id", "Id from list_babies", required = true) }, write = false,
        ) { caller, args ->
            val ctx = ctx(caller, args)
            json(ScheduleView.serializer(), ToolViews.schedule(content, ctx.region, ctx.schedule))
        },

        BabyTool(
            "search_guidance", "Search guidance",
            "Searches milestone and period content. Use it to map a parent's words ('sits on her own') to milestone ids, " +
                "or to find what the content says about sleep, feeding, play or safety. Give baby_id to rank by their age.",
            schema {
                string("query", "Words to search for", required = true)
                string("baby_id", "Id from list_babies, to rank results by age")
            }, write = false,
        ) { caller, args ->
            val ageMonths = args.optString("baby_id")?.let {
                babies.context(caller.familyId, it).snapshot.ages.milestoneAge.months
            }
            val results = search.search(args.string("query"), ageMonths)
            json(ListSerializer(GuidancePassage.serializer()), results)
        },
    )

    private fun <E : Enum<E>> enumValue(serializer: KSerializer<E>, values: Array<E>, name: String, text: String?): E? =
        text?.let { t -> values.firstOrNull { serialName(serializer, it) == t } ?: throw ToolInputException("Unknown $name '$t'") }

    private fun names(serializer: KSerializer<out Enum<*>>) = (0 until serializer.descriptor.elementsCount).map(serializer.descriptor::getElementName)

    private fun everydayReadTools() = listOf(
        BabyTool(
            "get_foods", "Foods tried",
            "The baby's foods tracker: how many foods tried, by group; each food with times offered, last offered and " +
                "whether they liked it; allergens introduced or due again; foods worth offering again; possible reactions the " +
                "parents noted; suggestions for what to try next, worked out by the app's rules; and the 20 most recently " +
                "logged entries with their ids. Give food to list every entry for one food (to correct or remove one), or " +
                "query/group to search the food catalogue (with how to serve each food at this age) instead.",
            schema {
                string("baby_id", "Id from list_babies", required = true)
                string("food", "Optional: a food id or name, to list all its entries")
                string("query", "Optional: search the food catalogue by name, e.g. 'salmon' or 'berries'")
                string("group", "Optional: only catalogue foods in this group", enum = names(FoodGroup.serializer()))
            }, write = false,
        ) { caller, args ->
            val ctx = ctx(caller, args)
            val months = ctx.snapshot.ages.chronological.months
            val food = args.optString("food")
            val query = args.optString("query")
            val group = enumValue(FoodGroup.serializer(), FoodGroup.entries.toTypedArray(), "group", args.optString("group"))
            when {
                food != null -> {
                    val key = FoodNames.key(content.findFood(food)?.id, food)
                    JsonObject(mapOf(
                        "food" to JsonPrimitive(food),
                        "tastings" to BabyJson.encodeToJsonElement(
                            ListSerializer(FoodTasting.serializer()), ctx.tastings.filter { it.key == key }.reversed(),
                        ),
                        "content_version" to JsonPrimitive(content.version),
                    ))
                }
                query != null || group != null ->
                    json(ListSerializer(FoodView.serializer()), content.searchFoods(query, group).map { FoodAndGuideViews.briefFood(it, months) })
                else -> json(FoodReport.serializer(), ctx.foods, mapOf(
                    "baby_name" to JsonPrimitive(ctx.baby.name),
                    "age_months" to JsonPrimitive(months),
                    "recent" to BabyJson.encodeToJsonElement(ListSerializer(FoodTasting.serializer()), babyStore.recentTastings(ctx.baby.id, 20)),
                ))
            }
        },

        BabyTool(
            "get_food", "Food details",
            "One catalogue food: how to serve it at the baby's age, choking-risk preparation, allergens, whether it's " +
                "iron-rich, the earliest age to offer it, and sources.",
            schema {
                string("food_id", "e.g. food.salmon, from get_foods", required = true)
                string("baby_id", "Id from list_babies, to tailor the serving advice to their age")
            }, write = false,
        ) { caller, args ->
            val id = args.string("food_id")
            val food = content.findFood(id) ?: throw ToolInputException("Unknown food $id; search with get_foods and a query")
            val months = args.optString("baby_id")?.let { babies.context(caller.familyId, it).snapshot.ages.chronological.months }
            json(FoodView.serializer(), FoodAndGuideViews.food(content, food, months))
        },

        BabyTool(
            "get_guides", "Guides and checklists",
            "Practical guides for the baby's age (weaning, allergens, choking, foods to avoid, drinks, vitamins, teeth, " +
                "sun safety, potty training, childcare, starting school) with this baby's checklist ticks. Without guide_id: " +
                "the guides for now and coming up. With guide_id: the full guide, its checklist and sources.",
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
    )

    private fun everydayWriteTools() = listOf(
        BabyTool(
            "log_food", "Log a food",
            "Records that the baby was offered a solid food or drink other than their usual milk (never log breast milk or " +
                "formula). Give the food's id or name: names are matched to the catalogue ('eggs' finds food.egg); anything " +
                "else is saved as the family's own food, with group if you know it. 'Milk' alone isn't matched: use " +
                "food.cows_milk only when the parent means cows' milk. Log each food separately. reaction is only for a " +
                "possible reaction the parent describes, in their words.",
            schema {
                string("baby_id", "Id from list_babies", required = true)
                string("food", "Catalogue food id or a food name", required = true)
                date("tasted_on", "When; default today")
                string("liking", "How it went", enum = names(Liking.serializer()))
                string("reaction", "A possible reaction the parent noticed, in their words")
                string("note", "Anything else, e.g. how it was served")
                string("group", "For a food not in the catalogue", enum = names(FoodGroup.serializer()))
            }, write = true,
        ) { caller, args ->
            val text = args.string("food")
            val tasting = foods.log(writer(caller), args.string("baby_id"), TastingInput(
                foodId = content.food(text)?.id,
                name = text,
                group = enumValue(FoodGroup.serializer(), FoodGroup.entries.toTypedArray(), "group", args.optString("group")),
                tastedOn = args.optDate("tasted_on"),
                liking = enumValue(Liking.serializer(), Liking.entries.toTypedArray(), "liking", args.optString("liking")),
                reaction = args.optString("reaction"),
                note = args.optString("note"),
            ))
            json(FoodTasting.serializer(), tasting, mapOf("in_catalogue" to JsonPrimitive(tasting.foodId != null)))
        },

        BabyTool(
            "remove_food_tasting", "Remove a food entry",
            "Deletes one food entry logged by mistake. Find its id with get_foods (recent, or food for one food's entries).",
            schema {
                string("baby_id", "Id from list_babies", required = true)
                string("tasting_id", "Id of the food entry", required = true)
            }, write = true, destructive = true,
        ) { caller, args ->
            foods.remove(writer(caller), args.string("baby_id"), args.string("tasting_id"))
            buildJsonObject { put("removed", args.string("tasting_id")); put("content_version", content.version) }
        },

        BabyTool(
            "update_checklist", "Tick a checklist item",
            "Ticks or unticks an item on a guide's checklist, such as check.ready_sits. Item ids come from get_guides.",
            schema {
                string("baby_id", "Id from list_babies", required = true)
                string("item_id", "Checklist item id from get_guides", required = true)
                boolean("done", "True to tick, false to untick", required = true)
            }, write = true,
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
        ) { caller, args ->
            val kind = enumValue(NoteKind.serializer(), NoteKind.entries.toTypedArray(), "kind", args.string("kind"))!!
            val note = guides.saveNote(writer(caller), args.string("baby_id"), kind, args.string("title"), args.string("body"))
            json(AssistantNote.serializer(), note)
        },
    )

    private fun writeTools() = listOf(
        BabyTool(
            "log_milestone", "Log a milestone",
            "Records that the baby has done a milestone. Use a milestone id from the tools, never an invented one, " +
                "and only a date the parent gave (default today).",
            schema {
                string("baby_id", "Id from list_babies", required = true)
                string("milestone_id", "Milestone id from list_milestones or search_guidance", required = true)
                date("achieved_on", "When it was first seen; default today")
                boolean("approximate", "True if the parent isn't sure of the exact date")
                string("note", "The parent's own words, if any")
            }, write = true,
        ) { caller, args ->
            val log = records.logMilestone(writer(caller), args.string("baby_id"), LogInput(
                args.string("milestone_id"), args.optDate("achieved_on"), args.optBoolean("approximate") ?: false,
                args.optString("note"),
            ))
            json(MilestoneLog.serializer(), log)
        },

        BabyTool(
            "update_milestone_log", "Edit a log entry",
            "Changes the date or note on a log entry. The previous version is kept in history.",
            schema {
                string("baby_id", "Id from list_babies", required = true)
                string("log_id", "log_id from list_milestones", required = true)
                date("achieved_on", "New date")
                boolean("approximate", "Whether the date is approximate")
                string("note", "New note")
            }, write = true,
        ) { caller, args ->
            val log = records.updateLog(writer(caller), args.string("baby_id"), args.string("log_id"), LogChanges(
                achievedOn = args.optDate("achieved_on"), approximate = args.optBoolean("approximate"), note = args.optString("note"),
            ))
            json(MilestoneLog.serializer(), log)
        },

        BabyTool(
            "remove_milestone_log", "Remove a log entry",
            "Deletes a log entry, for example one logged by mistake. A copy is kept in history.",
            schema {
                string("baby_id", "Id from list_babies", required = true)
                string("log_id", "log_id from list_milestones", required = true)
            }, write = true, destructive = true,
        ) { caller, args ->
            records.removeLog(writer(caller), args.string("baby_id"), args.string("log_id"))
            buildJsonObject { put("removed", args.string("log_id")); put("content_version", content.version) }
        },

        BabyTool(
            "mark_not_sure", "Mark not sure",
            "Records that the parent isn't sure whether the baby does this yet; it will be asked again in 2 weeks.",
            schema {
                string("baby_id", "Id from list_babies", required = true)
                string("milestone_id", "Milestone id", required = true)
            }, write = true,
        ) { caller, args ->
            val log = records.markNotSure(writer(caller), args.string("baby_id"), args.string("milestone_id"))
            json(MilestoneLog.serializer(), log)
        },

        BabyTool(
            "add_moment", "Add a moment",
            "Adds a custom moment outside the milestone list, such as first tooth, first food or first word said.",
            schema {
                string("baby_id", "Id from list_babies", required = true)
                string("title", "e.g. First tooth", required = true)
                date("date", "When it happened", required = true)
                string("note", "Any details")
            }, write = true,
        ) { caller, args ->
            val moment = records.addMoment(writer(caller), args.string("baby_id"), args.string("title"), args.date("date"),
                args.optString("note"), null)
            json(Moment.serializer(), moment)
        },

        BabyTool(
            "update_appointment", "Update an appointment",
            "Marks a health visitor or GP appointment as booked (with its date), done, or not booked, or adds a note.",
            schema {
                string("baby_id", "Id from list_babies", required = true)
                string("appointment_id", "Appointment id from get_schedule, e.g. review_1_year", required = true)
                string("status", "What changed", required = true, enum = listOf("booked", "done", "not_booked", "note"))
                date("date", "Booked date, or when it was done")
                string("note", "A note to keep with it")
            }, write = true,
        ) { caller, args ->
            val record = records.updateAppointment(writer(caller), args.string("baby_id"), args.string("appointment_id"),
                args.string("status"), args.optDate("date"), args.optString("note"))
            json(AppointmentRecord.serializer(), record)
        },

        BabyTool(
            "record_vaccination", "Record a vaccination",
            "Marks a vaccine dose as given on a date. Use the dose code from get_schedule.",
            schema {
                string("baby_id", "Id from list_babies", required = true)
                string("dose_id", "Dose code from get_schedule, e.g. menb_1", required = true)
                date("given_on", "When it was given", required = true)
            }, write = true,
        ) { caller, args ->
            val record = records.recordVaccination(writer(caller), args.string("baby_id"), args.string("dose_id"),
                args.date("given_on"), null)
            json(DoseRecord.serializer(), record)
        },

        BabyTool(
            "add_review_note", "Add a question for the health visitor",
            "Adds a question or note to the health visitor summary, in the parent's words.",
            schema {
                string("baby_id", "Id from list_babies", required = true)
                string("text", "The question or note", required = true)
            }, write = true,
        ) { caller, args ->
            val note = records.addReviewNote(writer(caller), args.string("baby_id"), args.string("text"))
            json(ReviewNote.serializer(), note)
        },
    )
}
