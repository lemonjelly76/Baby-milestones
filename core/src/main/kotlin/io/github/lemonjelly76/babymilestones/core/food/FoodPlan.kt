package io.github.lemonjelly76.babymilestones.core.food

import io.github.lemonjelly76.babymilestones.core.content.ContentBundle
import io.github.lemonjelly76.babymilestones.core.json.Date
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit

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

        fun progress(plan: List<PlannedFood>, tastings: List<FoodTasting>): List<PlanProgress> = plan.map { item ->
            PlanProgress(item, tastings.filter { it.key == item.key && !it.tastedOn.isBefore(item.createdOn) }.minOfOrNull { it.tastedOn })
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
                    "${allergen.label} for the first time: on its own, in a small amount, so you can spot any reaction.")
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
