package io.github.lemonjelly76.babymilestones.server.tools

import io.github.lemonjelly76.babymilestones.core.content.ContentBundle
import io.github.lemonjelly76.babymilestones.core.food.FoodGroup
import io.github.lemonjelly76.babymilestones.core.food.FoodNames
import io.github.lemonjelly76.babymilestones.core.food.FoodReport
import io.github.lemonjelly76.babymilestones.core.food.FoodPlanner
import io.github.lemonjelly76.babymilestones.core.food.FoodTasting
import io.github.lemonjelly76.babymilestones.core.food.PlanSource
import io.github.lemonjelly76.babymilestones.core.food.PlannedFood
import io.github.lemonjelly76.babymilestones.server.service.PlanInput
import io.github.lemonjelly76.babymilestones.core.food.Liking
import io.github.lemonjelly76.babymilestones.core.json.BabyJson
import io.github.lemonjelly76.babymilestones.server.service.BabyService
import io.github.lemonjelly76.babymilestones.server.service.FoodService
import io.github.lemonjelly76.babymilestones.server.service.TastingInput
import io.github.lemonjelly76.babymilestones.server.store.FoodStore
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.springframework.stereotype.Component

/** The foods tracker: what's been tried, the catalogue, and logging tastes. */
@Component
class FoodTools(
    babies: BabyService,
    content: ContentBundle,
    private val foods: FoodService,
    private val foodStore: FoodStore,
) : ToolSet(babies, content) {
    override val order = 1

    override val tools = listOf(
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
                    "recent" to BabyJson.encodeToJsonElement(ListSerializer(FoodTasting.serializer()), foodStore.recentTastings(ctx.baby.id, 20)),
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
            confirm = { args, today ->
                "Tried ${content.findFood(args.string("food"))?.name ?: args.string("food")} — ${day(args.optDate("tasted_on"), today)}" +
                (enumValue(Liking.serializer(), Liking.entries.toTypedArray(), "liking", args.optString("liking"))?.let { " (${it.label.lowercase()})" } ?: "")
            },
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
            confirm = { args, today ->
                "Remove food entry ${args.string("tasting_id")}"
            },
        ) { caller, args ->
            foods.remove(writer(caller), args.string("baby_id"), args.string("tasting_id"))
            buildJsonObject { put("removed", args.string("tasting_id")); put("content_version", content.version) }
        },
        BabyTool(
            "get_food_plan", "Food plan",
            "The baby's plan of foods to try, day by day: each food's status (done once tasted on or after the day the plan " +
                "was made), how to serve it at that age, when it was had before, and a note for any new allergen's day (or a " +
                "warning if that day also has something else new or another allergen). Plus 'suggested': the two weeks the " +
                "app's own rules would plan from today, a good starting point. Pass these details on when you show a plan.",
            schema { string("baby_id", "Id from list_babies", required = true) }, write = false,
        ) { caller, args ->
            val ctx = ctx(caller, args)
            json(FoodPlanView.serializer(), FoodPlanView(
                FoodPlanner.days(content, ctx.foodPlan, ctx.tastings, ctx.baby.dateOfBirth),
                FoodPlanner(content).plan(ctx.tastings, ctx.today, ctx.snapshot.ages.chronological.months),
            ))
        },

        BabyTool(
            "save_food_plan", "Save a food plan",
            "Replaces the baby's food plan with these foods, which the app shows as a checklist that ticks itself off as " +
                "each food is logged. Give a catalogue food id or name for each, and a date. Follow the app's rules: at most " +
                "one new allergen every 3 days, keep introduced allergens going weekly, nothing the baby is too young for, " +
                "and nothing with an allergen the parents noted a possible reaction to. Show the parent the plan first.",
            schema {
                string("baby_id", "Id from list_babies", required = true)
                objects("items", "The planned foods, in date order", required = true) {
                    string("food", "Catalogue food id or a food name", required = true)
                    date("planned_on", "The day to offer it", required = true)
                    string("reason", "Why, in a few words")
                }
            }, write = true,
            confirm = { args, today ->
                val items = args.objects("items")
                "Food plan: ${items.size} foods from ${day(items.minOfOrNull { it.date("planned_on") }, today)}"
            },
        ) { caller, args ->
            val saved = foods.savePlan(writer(caller), args.string("baby_id"), args.objects("items").map {
                PlanInput(it.string("food"), it.date("planned_on"), reason = it.optString("reason"))
            }, PlanSource.CLAUDE)
            json(ListSerializer(PlannedFood.serializer()), saved)
        },
    )
}
