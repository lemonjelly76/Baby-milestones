package io.github.lemonjelly76.babymilestones.core.food

import io.github.lemonjelly76.babymilestones.core.content.ContentBundle
import io.github.lemonjelly76.babymilestones.core.json.Date
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Serializable
data class FoodProgress(
    val key: String,
    val foodId: String? = null,
    val name: String,
    val group: FoodGroup,
    val timesOffered: Int,
    val firstTried: Date,
    val lastOffered: Date,
    val lastLiking: Liking? = null,
    val everLiked: Boolean,
    /** Not liked yet and offered fewer than [FoodTracker.KEEP_OFFERING_TRIES] times. */
    val keepOffering: Boolean,
    val reactions: List<String> = emptyList(),
)

@Serializable
enum class AllergenState(val label: String) {
    @SerialName("not_yet") NOT_YET("Not introduced yet"),
    @SerialName("introduced") INTRODUCED("Introduced"),
    @SerialName("due") DUE("Not offered for a while"),
    @SerialName("reaction") REACTION("Possible reaction noted"),
}

@Serializable
data class AllergenStatus(
    val allergen: Allergen,
    val state: AllergenState,
    val firstOffered: Date? = null,
    val lastOffered: Date? = null,
    val timesOffered: Int = 0,
    val daysSinceLast: Int? = null,
)

@Serializable
data class GroupProgress(val group: FoodGroup, val tried: Int, val inCatalogue: Int)

@Serializable
enum class SuggestionKind {
    @SerialName("allergen_keep_going") ALLERGEN_KEEP_GOING,
    @SerialName("new_allergen") NEW_ALLERGEN,
    @SerialName("keep_offering") KEEP_OFFERING,
    @SerialName("new_vegetable") NEW_VEGETABLE,
    @SerialName("iron") IRON,
}

@Serializable
data class FoodSuggestion(val foodId: String, val name: String, val kind: SuggestionKind, val reason: String)

@Serializable
data class ReactionNote(val name: String, val date: Date, val text: String)

/** Everything the foods tracker shows for one baby, worked out from the tastings alone. */
@Serializable
data class FoodReport(
    val today: Date,
    val tried: Int,
    val groups: List<GroupProgress>,
    val foods: List<FoodProgress>,
    val allergens: List<AllergenStatus>,
    val suggestions: List<FoodSuggestion>,
    val reactions: List<ReactionNote>,
) {
    fun progress(key: String): FoodProgress? = foods.firstOrNull { it.key == key }
    val allergensIntroduced: List<Allergen> get() = allergens.filter { it.state != AllergenState.NOT_YET }.map { it.allergen }
    val allergensDue: List<AllergenStatus> get() = allergens.filter { it.state == AllergenState.DUE }
}

/**
 * The rules behind the foods tracker:
 * - keep offering a food that isn't liked yet: the NHS says it may take 10 tries or more (Maier 2007 saw intake of a
 *   disliked vegetable catch up by the 8th);
 * - introduce allergens one at a time and, once introduced, keep them in the usual diet (NHS; BSACI), so an allergen not
 *   offered for a week is suggested again;
 * - vegetables first, less sweet ones especially (NHS; Barends 2013), and an iron-rich food at each meal (BLISS).
 */
class FoodTracker(private val content: ContentBundle) {

    companion object {
        const val KEEP_OFFERING_TRIES = 10
        const val ALLERGEN_REGULAR_DAYS = 7
        const val NEW_ALLERGEN_GAP_DAYS = 3
        const val WEANING_FROM_MONTHS = 6
        const val MAX_SUGGESTIONS = 5
    }

    fun report(tastings: List<FoodTasting>, today: LocalDate, ageMonths: Int): FoodReport {
        val foods = progress(tastings)
        val allergens = allergens(tastings, today)
        return FoodReport(
            today = today,
            tried = foods.size,
            groups = FoodGroup.entries.map { g ->
                GroupProgress(g, foods.count { it.group == g }, content.foods.count { it.group == g })
            }.filter { it.tried > 0 || it.inCatalogue > 0 },
            foods = foods,
            allergens = allergens,
            suggestions = if (tastings.isEmpty() && ageMonths < WEANING_FROM_MONTHS) emptyList()
            else suggestions(foods, allergens, tastings, today, ageMonths),
            reactions = tastings.filter { !it.reaction.isNullOrBlank() }.sortedByDescending { it.tastedOn }
                .map { ReactionNote(it.name, it.tastedOn, it.reaction!!.trim()) },
        )
    }

    private fun progress(tastings: List<FoodTasting>): List<FoodProgress> =
        tastings.groupBy { it.key }.map { (key, all) ->
            val sorted = all.sortedBy { it.tastedOn }
            val latest = sorted.last()
            val everLiked = all.any { it.liking == Liking.LIKED }
            val food = latest.foodId?.let(content::food)
            FoodProgress(
                key = key,
                foodId = latest.foodId,
                name = food?.name ?: latest.name.trim(),
                group = food?.group ?: latest.group,
                timesOffered = all.size,
                firstTried = sorted.first().tastedOn,
                lastOffered = latest.tastedOn,
                lastLiking = sorted.lastOrNull { it.liking != null }?.liking,
                everLiked = everLiked,
                keepOffering = !everLiked && all.any { it.liking != null } && all.size < KEEP_OFFERING_TRIES,
                reactions = all.mapNotNull { it.reaction?.trim()?.ifEmpty { null } },
            )
        }.sortedWith(compareByDescending<FoodProgress> { it.lastOffered }.thenBy { it.name })

    private fun allergens(tastings: List<FoodTasting>, today: LocalDate): List<AllergenStatus> =
        Allergen.entries.map { allergen ->
            val with = tastings.filter { t -> t.foodId?.let(content::food)?.allergens?.contains(allergen) == true }
            if (with.isEmpty()) return@map AllergenStatus(allergen, AllergenState.NOT_YET)
            val last = with.maxOf { it.tastedOn }
            val days = ChronoUnit.DAYS.between(last, today).toInt()
            val state = when {
                with.any { !it.reaction.isNullOrBlank() } -> AllergenState.REACTION
                days > ALLERGEN_REGULAR_DAYS -> AllergenState.DUE
                else -> AllergenState.INTRODUCED
            }
            AllergenStatus(allergen, state, with.minOf { it.tastedOn }, last, with.size, days)
        }

    private fun suggestions(
        foods: List<FoodProgress>, allergens: List<AllergenStatus>, tastings: List<FoodTasting>, today: LocalDate, ageMonths: Int,
    ): List<FoodSuggestion> {
        val tried = foods.mapNotNull { it.foodId }.toSet()
        val suitable = content.foods.filter { it.suitableAt(ageMonths) }
        val out = linkedMapOf<String, FoodSuggestion>()
        fun add(food: Food, kind: SuggestionKind, reason: String) {
            if (food.id !in out && out.size < MAX_SUGGESTIONS) out[food.id] = FoodSuggestion(food.id, food.name, kind, reason)
        }

        allergens.filter { it.state == AllergenState.DUE }.sortedByDescending { it.daysSinceLast }.take(2).forEach { status ->
            val usual = tastings.filter { t -> t.liking != Liking.DISLIKED && t.foodId?.let(content::food)?.allergens?.contains(status.allergen) == true }
                .maxByOrNull { it.tastedOn }?.foodId?.let(content::food)
            (usual ?: starterFood(status.allergen, suitable))?.let {
                add(it, SuggestionKind.ALLERGEN_KEEP_GOING,
                    "${status.allergen.label} was last offered ${status.daysSinceLast} days ago. Once introduced, keep it in the usual diet.")
            }
        }

        val reactionAllergens = allergens.filter { it.state == AllergenState.REACTION }.map { it.allergen }.toSet()
        val recentNew = allergens.any { it.firstOffered != null && ChronoUnit.DAYS.between(it.firstOffered, today) < NEW_ALLERGEN_GAP_DAYS }
        if (!recentNew) {
            allergens.firstOrNull { it.state == AllergenState.NOT_YET }?.let { next ->
                starterFood(next.allergen, suitable.filter { f -> f.allergens.none { it in reactionAllergens } })?.let {
                    add(it, SuggestionKind.NEW_ALLERGEN,
                        "${next.allergen.label} isn't introduced yet. Offer one new allergen at a time, in a small amount, so you can spot any reaction.")
                }
            }
        }

        foods.filter { it.keepOffering && it.foodId != null }
            .sortedWith(compareBy<FoodProgress> { it.group != FoodGroup.VEGETABLES }.thenBy { it.lastOffered })
            .take(2)
            .forEach { p ->
                content.food(p.foodId!!)?.takeIf { it.suitableAt(ageMonths) && it.allergens.none { a -> a in reactionAllergens } }?.let {
                    add(it, SuggestionKind.KEEP_OFFERING,
                        "Offered ${p.timesOffered} ${if (p.timesOffered == 1) "time" else "times"} so far. It can take 10 tries or more to get used to a new food.")
                }
            }

        suitable.filter { it.group == FoodGroup.VEGETABLES && it.id !in tried }.take(2).forEach {
            add(it, SuggestionKind.NEW_VEGETABLE, "Not tried yet. Vegetables, especially less sweet ones, help babies get used to a range of tastes.")
        }

        suitable.firstOrNull { it.ironRich && it.id !in tried && it.allergens.isEmpty() }?.let {
            add(it, SuggestionKind.IRON, "Rich in iron. Offering an iron-rich food at each meal is part of the BLISS approach to baby-led weaning.")
        }
        return out.values.toList()
    }

    /** The simplest food for a first taste of an allergen: the first catalogue food containing only that allergen. */
    private fun starterFood(allergen: Allergen, suitable: List<Food>): Food? =
        suitable.firstOrNull { it.allergens == listOf(allergen) } ?: suitable.firstOrNull { allergen in it.allergens }
}
