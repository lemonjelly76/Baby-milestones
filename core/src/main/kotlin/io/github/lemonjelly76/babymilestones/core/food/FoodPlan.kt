package io.github.lemonjelly76.babymilestones.core.food

import io.github.lemonjelly76.babymilestones.core.age.AgeSpan
import io.github.lemonjelly76.babymilestones.core.content.ContentBundle
import io.github.lemonjelly76.babymilestones.core.json.Date
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@Serializable
enum class PlanKind(val label: String) {
    @SerialName("new_food") NEW_FOOD("New food"),
    @SerialName("new_allergen") NEW_ALLERGEN("New allergen"),
    @SerialName("keep_going") KEEP_GOING("Keep it going"),
    @SerialName("offer_again") OFFER_AGAIN("Offer again"),
    @SerialName("other") OTHER("Planned"),
}

@Serializable
enum class PlanSource {
    @SerialName("app") APP,
    @SerialName("claude") CLAUDE,
}

/** A food the planner suggests for a day, before it's saved. */
@Serializable
data class PlanSuggestion(val foodId: String, val name: String, val plannedOn: Date, val kind: PlanKind, val reason: String)

/** A food on the baby's plan: a catalogue food, or the family's own by name. Done once it's tasted on or after [createdOn]. */
@Serializable
data class PlannedFood(
    val id: String,
    val babyId: String,
    val foodId: String? = null,
    val name: String,
    val group: FoodGroup = FoodGroup.OTHER,
    val plannedOn: Date,
    val kind: PlanKind = PlanKind.OTHER,
    val reason: String? = null,
    val source: PlanSource = PlanSource.APP,
    val createdOn: Date,
) {
    val key: String get() = FoodNames.key(foodId, name)
}

@Serializable
data class PlanProgress(val item: PlannedFood, val doneOn: Date? = null) {
    val done: Boolean get() = doneOn != null
}

/** One food on a day of the plan, with how to serve it at that age and when it was had before. */
@Serializable
data class PlanDayItem(val progress: PlanProgress, val howToServe: String? = null, val hadBefore: String? = null)

/** A day of the plan; [note] explains a new allergen's day, or warns when the day makes a reaction hard to pin down. */
@Serializable
data class PlanDay(val date: Date, val items: List<PlanDayItem>, val note: String? = null)

/**
 * A day-by-day plan of foods to try, following the same rules as the tracker's suggestions: one new thing a day; a new
 * allergen at most every [FoodTracker.NEW_ALLERGEN_GAP_DAYS] days, on a day with no other allergen, and then kept in the
 * diet at least weekly; vegetables
 * (less sweet first) before other new foods; foods not liked yet offered again every other day until they've had
 * [FoodTracker.KEEP_OFFERING_TRIES] tries; nothing with an allergen that caused a possible reaction, or that the baby is
 * too young for.
 */
class FoodPlanner(private val content: ContentBundle) {

    companion object {
        const val DEFAULT_DAYS = 14

        private val DAY = DateTimeFormatter.ofPattern("d MMM", Locale.UK)

        fun progress(plan: List<PlannedFood>, tastings: List<FoodTasting>): List<PlanProgress> = plan.map { item ->
            PlanProgress(item, tastings.filter { it.key == item.key && !it.tastedOn.isBefore(item.createdOn) }.minOfOrNull { it.tastedOn })
        }

        /**
         * The plan by day, as the app and Claude show it. Works for any plan, including one Claude wrote, so a day that
         * adds a new allergen alongside something else new, or another allergen, is pointed out.
         */
        fun days(content: ContentBundle, plan: List<PlannedFood>, tastings: List<FoodTasting>, dateOfBirth: LocalDate): List<PlanDay> {
            val progress = progress(plan, tastings).associateBy { it.item.id }
            return plan.groupBy { it.plannedOn }.toSortedMap().map { (date, items) ->
                val months = AgeSpan.between(dateOfBirth, date).months
                val sorted = items.sortedBy { if (it.kind == PlanKind.NEW_ALLERGEN) -1 else it.kind.ordinal }
                PlanDay(
                    date,
                    sorted.map { item ->
                        PlanDayItem(
                            progress.getValue(item.id),
                            item.foodId?.let(content::food)?.servingAt(months)?.text,
                            hadBefore(tastings.filter { it.key == item.key && it.tastedOn.isBefore(date) }),
                        )
                    },
                    dayNote(content, sorted, plan, tastings, date),
                )
            }
        }

        private fun hadBefore(earlier: List<FoodTasting>): String? {
            val last = earlier.maxByOrNull { it.tastedOn } ?: return null
            val liking = last.liking?.let { ": ${it.label.lowercase()}" }.orEmpty()
            return if (earlier.size == 1) "Had once before, on ${last.tastedOn.format(DAY)}$liking"
            else "Had ${earlier.size} times before, last on ${last.tastedOn.format(DAY)}$liking"
        }

        private fun dayNote(
            content: ContentBundle, items: List<PlannedFood>, plan: List<PlannedFood>, tastings: List<FoodTasting>, date: LocalDate,
        ): String? {
            fun allergens(item: PlannedFood) = item.foodId?.let(content::food)?.allergens.orEmpty()
            val earlierKeys = tastings.filter { it.tastedOn.isBefore(date) }.map { it.key }.toSet() +
                plan.filter { it.plannedOn.isBefore(date) }.map { it.key }
            val introduced = (tastings.filter { it.tastedOn.isBefore(date) }.mapNotNull { it.foodId } +
                plan.filter { it.plannedOn.isBefore(date) }.mapNotNull { it.foodId })
                .mapNotNull(content::food).flatMap { it.allergens }.toSet()
            val newAllergens = items.flatMap { item -> allergens(item).filter { it !in introduced }.map { it to item } }
            if (newAllergens.isEmpty()) return null
            val labels = newAllergens.map { it.first.label }.distinct()
            if (labels.size > 1) {
                return labels.joinToString(" and ") + " would ${if (labels.size == 2) "both" else "all"} be new today. Offer one new allergen at a time, a few days apart, so you can tell what caused any reaction."
            }
            val (allergen, carrier) = newAllergens.first()
            val first = "${allergen.label} for the first time today."
            val others = items.filter { it != carrier }
            if (others.isEmpty()) return "$first Offer it in a small amount, so you can spot any reaction."
            val problems = others.mapNotNull { item ->
                when {
                    item.key !in earlierKeys -> "${item.name} is new too"
                    allergens(item).isNotEmpty() -> "${item.name} also contains ${allergens(item).joinToString { it.label.lowercase() }}"
                    else -> null
                }
            }
            return if (problems.isEmpty()) {
                "$first Everything else today has been had before and has no allergens, so if there's a reaction you'll know " +
                    "what caused it. Start with a small amount."
            } else {
                "$first ${problems.joinToString("; ")}: think about moving ${if (problems.size == 1) "it" else "them"} to " +
                    "another day, so you'd know what caused any reaction."
            }
        }
    }

    fun plan(tastings: List<FoodTasting>, start: LocalDate, ageMonths: Int, days: Int = DEFAULT_DAYS): List<PlanSuggestion> {
        if (tastings.isEmpty() && ageMonths < FoodTracker.WEANING_FROM_MONTHS) return emptyList()
        val report = FoodTracker(content).report(tastings, start, ageMonths)
        val reactions = report.allergens.filter { it.state == AllergenState.REACTION }.map { it.allergen }.toSet()
        val suitable = content.foods.filter { f -> f.suitableAt(ageMonths) && f.allergens.none { it in reactions } }
        val tried = tastings.mapNotNull { it.foodId }.toMutableSet()
        val firstOffered = report.allergens.mapNotNull { a -> a.firstOffered?.let { a.allergen to it } }.toMap().toMutableMap()
        val lastOffered = report.allergens.mapNotNull { a -> a.lastOffered?.let { a.allergen to it } }.toMap().toMutableMap()
        val usual = tastings.sortedByDescending { it.tastedOn }.mapNotNull { it.foodId?.let(content::food) }.filter { it in suitable }
        val again = report.foods.filter { it.keepOffering && it.foodId != null }
            .mapNotNull { p -> content.food(p.foodId!!)?.takeIf { it in suitable }?.let { it to p.timesOffered } }
            .toMap().toMutableMap()
        val out = mutableListOf<PlanSuggestion>()

        fun add(food: Food, date: LocalDate, kind: PlanKind, reason: String) {
            out += PlanSuggestion(food.id, food.name, date, kind, reason)
            food.allergens.forEach { a -> firstOffered.putIfAbsent(a, date); lastOffered[a] = date }
        }

        for (day in 0 until days) {
            val date = start.plusDays(day.toLong())
            val before = out.size
            lastOffered.filterKeys { it !in reactions }
                .filterValues { ChronoUnit.DAYS.between(it, date) > FoodTracker.ALLERGEN_REGULAR_DAYS }
                .minByOrNull { it.value }?.key
                ?.let { allergen ->
                    (usual.firstOrNull { allergen in it.allergens } ?: starter(allergen, suitable))?.let {
                        add(it, date, PlanKind.KEEP_GOING, "Keep ${allergen.label.lowercase()} in the usual diet: it's been a week.")
                    }
                }

            // A new allergen gets a day to itself, so any reaction can be put down to it.
            val keptOneGoing = out.size > before
            val newAllergenAllowed = !keptOneGoing &&
                firstOffered.values.none { ChronoUnit.DAYS.between(it, date) < FoodTracker.NEW_ALLERGEN_GAP_DAYS }
            val nextAllergen = Allergen.entries.firstOrNull { it !in firstOffered && it !in reactions }
                ?.let { a -> starter(a, suitable)?.let { a to it } }
            if (newAllergenAllowed && nextAllergen != null) {
                val (allergen, food) = nextAllergen
                add(food, date, PlanKind.NEW_ALLERGEN,
                    "${allergen.label} for the first time, in a small amount.")
                tried += food.id
            } else {
                nextNewFood(suitable, tried)?.let { food ->
                    add(food, date, PlanKind.NEW_FOOD, when {
                        food.group == FoodGroup.VEGETABLES -> "A new vegetable: less sweet ones help babies get used to a range of tastes."
                        food.ironRich -> "Rich in iron: aim for an iron-rich food at each meal."
                        else -> "Something new, for variety."
                    })
                    tried += food.id
                }
            }

            val newAllergenToday = out.drop(before).any { it.kind == PlanKind.NEW_ALLERGEN }
            if (day % 2 == 0) {
                again.entries.filter { !newAllergenToday || it.key.allergens.isEmpty() }.minByOrNull { it.value }?.let { (food, tries) ->
                    add(food, date, PlanKind.OFFER_AGAIN, "Offer again: it can take 10 tries or more to get used to a new food.")
                    if (tries + 1 >= FoodTracker.KEEP_OFFERING_TRIES) again.remove(food) else again[food] = tries + 1
                }
            }
        }
        return out
    }

    /** Untried foods without allergens, vegetables first, then iron-rich foods, then the rest in catalogue order. */
    private fun nextNewFood(suitable: List<Food>, tried: Set<String>): Food? {
        val candidates = suitable.filter { it.id !in tried && it.allergens.isEmpty() }
        return candidates.firstOrNull { it.group == FoodGroup.VEGETABLES }
            ?: candidates.firstOrNull { it.ironRich }
            ?: candidates.firstOrNull()
    }

    private fun starter(allergen: Allergen, suitable: List<Food>): Food? =
        suitable.firstOrNull { it.allergens == listOf(allergen) } ?: suitable.firstOrNull { allergen in it.allergens }
}
