package io.github.lemonjelly76.babymilestones.server.service

import io.github.lemonjelly76.babymilestones.core.content.ContentBundle
import io.github.lemonjelly76.babymilestones.core.food.FoodGroup
import io.github.lemonjelly76.babymilestones.core.food.FoodTasting
import io.github.lemonjelly76.babymilestones.core.food.Liking
import io.github.lemonjelly76.babymilestones.server.store.BabyStore
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

/** The foods tracker's records: each time a baby was offered a food. */
@Service
class FoodService(
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
        babies.saveTasting(tasting)
        return tasting
    }

    @Transactional
    fun update(writer: Writer, babyId: String, tastingId: String, changes: TastingChanges): FoodTasting {
        access.checkBaby(writer, babyId)
        access.checkPhoto(writer, changes.photoRef)
        val current = babies.tasting(babyId, tastingId) ?: throw NotFoundException("No food entry $tastingId")
        changes.tastedOn?.let { require(!it.isAfter(today())) { "The date can't be in the future" } }
        val updated = current.copy(
            tastedOn = changes.tastedOn ?: current.tastedOn,
            liking = if (changes.clearLiking) null else changes.liking ?: current.liking,
            reaction = if (changes.reaction != null) changes.reaction.clean() else current.reaction,
            note = if (changes.note != null) changes.note.clean() else current.note,
            photoRef = if (changes.clearPhoto) null else changes.photoRef ?: current.photoRef,
        )
        babies.saveTasting(updated)
        return updated
    }

    @Transactional
    fun remove(writer: Writer, babyId: String, tastingId: String) {
        access.checkBaby(writer, babyId)
        if (!babies.deleteTasting(babyId, tastingId)) throw NotFoundException("No food entry $tastingId")
    }

    private fun String?.clean() = this?.trim()?.ifEmpty { null }?.take(MAX_TEXT)

    companion object {
        const val MAX_NAME = 80
        const val MAX_TEXT = 1_000
    }
}
