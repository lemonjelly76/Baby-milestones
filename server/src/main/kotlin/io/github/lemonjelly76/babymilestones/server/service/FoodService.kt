package io.github.lemonjelly76.babymilestones.server.service

import io.github.lemonjelly76.babymilestones.core.age.AgeSpan
import io.github.lemonjelly76.babymilestones.core.content.ContentBundle
import io.github.lemonjelly76.babymilestones.core.food.FoodPlanner
import io.github.lemonjelly76.babymilestones.core.food.PlanKind
import io.github.lemonjelly76.babymilestones.core.food.PlanSource
import io.github.lemonjelly76.babymilestones.core.food.PlannedFood
import io.github.lemonjelly76.babymilestones.core.food.FoodGroup
import io.github.lemonjelly76.babymilestones.core.food.FoodTasting
import io.github.lemonjelly76.babymilestones.core.food.Liking
import io.github.lemonjelly76.babymilestones.server.store.BabyStore
import io.github.lemonjelly76.babymilestones.server.store.FoodStore
import io.github.lemonjelly76.babymilestones.server.store.Ids
import io.github.lemonjelly76.babymilestones.server.store.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate

/** A food offered: a catalogue [foodId], or the family's own food by [name] and [group]. */
data class TastingInput(
    val foodId: String? = null,
    val name: String? = null,
    val group: FoodGroup? = null,
    val tastedOn: LocalDate? = null,
    val liking: Liking? = null,
    val reaction: String? = null,
    val note: String? = null,
    val photoRef: String? = null,
)

/** Null leaves a field as it is; an empty reaction or note clears it, and the clear flags clear the others. */
data class TastingChanges(
    val tastedOn: LocalDate? = null,
    val liking: Liking? = null,
    val reaction: String? = null,
    val note: String? = null,
    val photoRef: String? = null,
    val clearLiking: Boolean = false,
    val clearPhoto: Boolean = false,
)

/** A food to put on the plan: a catalogue id or a name (matched to the catalogue where it can be). */
data class PlanInput(val food: String, val plannedOn: LocalDate, val kind: PlanKind = PlanKind.OTHER, val reason: String? = null)

/** The foods tracker's records: each time a baby was offered a food, and the plan of foods to try. */
@Service
class FoodService(
    private val foodStore: FoodStore,
    private val babies: BabyStore,
    private val access: BabyAccess,
    private val content: ContentBundle,
    private val clock: Clock,
) {
    private fun today() = LocalDate.now(clock)

    @Transactional
    fun log(writer: Writer, babyId: String, input: TastingInput): FoodTasting {
        access.checkBaby(writer, babyId)
        access.checkPhoto(writer, input.photoRef)
        val food = input.foodId?.let { content.food(it) ?: throw NotFoundException("Unknown food $it") }
            ?: input.name?.let(content::findFood)
        val name = food?.name ?: input.name?.trim()?.ifEmpty { null } ?: throw IllegalArgumentException("Choose a food or give its name")
        val tastedOn = input.tastedOn ?: today()
        require(!tastedOn.isAfter(today())) { "The date can't be in the future" }
        val tasting = FoodTasting(
            id = Ids.new("t"),
            babyId = babyId,
            foodId = food?.id,
            name = name.take(MAX_NAME),
            group = food?.group ?: input.group ?: FoodGroup.OTHER,
            tastedOn = tastedOn,
            liking = input.liking,
            reaction = input.reaction.clean(),
            note = input.note.clean(),
            photoRef = input.photoRef,
            loggedBy = writer.carerId,
        )
        foodStore.saveTasting(tasting)
        return tasting
    }

    @Transactional
    fun update(writer: Writer, babyId: String, tastingId: String, changes: TastingChanges): FoodTasting {
        access.checkBaby(writer, babyId)
        access.checkPhoto(writer, changes.photoRef)
        val current = foodStore.tasting(babyId, tastingId) ?: throw NotFoundException("No food entry $tastingId")
        changes.tastedOn?.let { require(!it.isAfter(today())) { "The date can't be in the future" } }
        val updated = current.copy(
            tastedOn = changes.tastedOn ?: current.tastedOn,
            liking = if (changes.clearLiking) null else changes.liking ?: current.liking,
            reaction = if (changes.reaction != null) changes.reaction.clean() else current.reaction,
            note = if (changes.note != null) changes.note.clean() else current.note,
            photoRef = if (changes.clearPhoto) null else changes.photoRef ?: current.photoRef,
        )
        foodStore.saveTasting(updated)
        return updated
    }

    @Transactional
    fun remove(writer: Writer, babyId: String, tastingId: String) {
        access.checkBaby(writer, babyId)
        if (!foodStore.deleteTasting(babyId, tastingId)) throw NotFoundException("No food entry $tastingId")
    }

    // --- the food plan ---

    /** Replaces the plan with one made by the app's rules ([FoodPlanner]) from today. */
    @Transactional
    fun generatePlan(writer: Writer, babyId: String, days: Int): List<PlannedFood> {
        access.checkBaby(writer, babyId)
        val baby = babies.baby(writer.familyId, babyId)!!
        val today = today()
        val suggestions = FoodPlanner(content).plan(
            foodStore.tastings(babyId), today, AgeSpan.between(baby.dateOfBirth, today).months, days.coerceIn(1, MAX_PLAN_DAYS),
        )
        return savePlan(writer, babyId, suggestions.map { PlanInput(it.foodId, it.plannedOn, it.kind, it.reason) }, PlanSource.APP)
    }

    /** Replaces the plan; Claude's plans come through here from the save_food_plan tool. */
    @Transactional
    fun savePlan(writer: Writer, babyId: String, items: List<PlanInput>, source: PlanSource): List<PlannedFood> {
        access.checkBaby(writer, babyId)
        require(items.size <= MAX_PLAN_ITEMS) { "Keep a plan to $MAX_PLAN_ITEMS foods or fewer" }
        val today = today()
        foodStore.deletePlan(babyId)
        return items.map { input ->
            require(input.food.isNotBlank()) { "Each planned food needs a name" }
            val food = content.findFood(input.food)
            PlannedFood(
                Ids.new("fp"), babyId, food?.id, food?.name ?: input.food.trim().take(MAX_NAME), food?.group ?: FoodGroup.OTHER,
                input.plannedOn, input.kind, input.reason.clean(), source, today,
            ).also { foodStore.savePlanItem(it, writer.carerId) }
        }
    }

    @Transactional
    fun clearPlan(writer: Writer, babyId: String) {
        access.checkBaby(writer, babyId)
        foodStore.deletePlan(babyId)
    }

    @Transactional
    fun removePlanItem(writer: Writer, babyId: String, itemId: String) {
        access.checkBaby(writer, babyId)
        if (!foodStore.deletePlanItem(babyId, itemId)) throw NotFoundException("No planned food $itemId")
    }

    private fun String?.clean() = this?.trim()?.ifEmpty { null }?.take(MAX_TEXT)

    companion object {
        const val MAX_NAME = 80
        const val MAX_TEXT = 1_000
        const val MAX_PLAN_DAYS = 28
        const val MAX_PLAN_ITEMS = 100
    }
}
