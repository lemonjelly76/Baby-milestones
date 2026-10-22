package io.github.lemonjelly76.babymilestones.server.service

import io.github.lemonjelly76.babymilestones.core.concerns.Concern
import io.github.lemonjelly76.babymilestones.core.concerns.ConcernEngine
import io.github.lemonjelly76.babymilestones.core.content.ContentBundle
import io.github.lemonjelly76.babymilestones.core.food.FoodReport
import io.github.lemonjelly76.babymilestones.core.food.FoodTasting
import io.github.lemonjelly76.babymilestones.core.food.FoodTracker
import io.github.lemonjelly76.babymilestones.core.guides.ChecklistTick
import io.github.lemonjelly76.babymilestones.core.notes.AssistantNote
import io.github.lemonjelly76.babymilestones.core.schedule.BabySchedule
import io.github.lemonjelly76.babymilestones.core.schedule.ScheduleEngine
import io.github.lemonjelly76.babymilestones.core.summary.ReviewNote
import io.github.lemonjelly76.babymilestones.core.summary.ReviewSummary
import io.github.lemonjelly76.babymilestones.core.timeline.Moment
import io.github.lemonjelly76.babymilestones.core.tracking.Baby
import io.github.lemonjelly76.babymilestones.core.tracking.BabySnapshot
import io.github.lemonjelly76.babymilestones.core.tracking.Tracker
import io.github.lemonjelly76.babymilestones.server.store.BabyStore
import io.github.lemonjelly76.babymilestones.server.store.FamilyStore
import io.github.lemonjelly76.babymilestones.server.store.Ids
import io.github.lemonjelly76.babymilestones.server.store.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate

/** One baby with everything recorded about them, and the views the core library derives from it. */
class BabyContext(
    val baby: Baby,
    val region: String,
    val content: ContentBundle,
    val snapshot: BabySnapshot,
    val moments: List<Moment>,
    val reviewNotes: List<ReviewNote>,
    val schedule: BabySchedule,
    val tastings: List<FoodTasting> = emptyList(),
    val ticks: List<ChecklistTick> = emptyList(),
    val notes: List<AssistantNote> = emptyList(),
) {
    val today: LocalDate get() = snapshot.today
    val concerns: List<Concern> by lazy { ConcernEngine.concerns(snapshot, content.concerns) }
    val foods: FoodReport by lazy { FoodTracker(content).report(tastings, today, snapshot.ages.chronological.months) }
    val summary: ReviewSummary by lazy { ReviewSummary.build(snapshot, concerns, reviewNotes, foods) }
}

data class BabyInput(
    val name: String,
    val dateOfBirth: LocalDate,
    val dueDate: LocalDate? = null,
    val sex: String? = null,
    val dobEstimated: Boolean = false,
)

@Service
class BabyService(
    private val babies: BabyStore,
    private val families: FamilyStore,
    private val content: ContentBundle,
    private val tracker: Tracker,
    private val clock: Clock,
) {
    fun today(): LocalDate = LocalDate.now(clock)

    fun babies(familyId: String): List<Baby> = babies.babies(familyId)

    fun baby(familyId: String, babyId: String): Baby =
        babies.baby(familyId, babyId) ?: throw NotFoundException("No baby $babyId in this family")

    @Transactional
    fun create(familyId: String, input: BabyInput): Baby = save(familyId, Ids.new("b"), input)

    @Transactional
    fun update(familyId: String, babyId: String, input: BabyInput): Baby {
        baby(familyId, babyId)
        return save(familyId, babyId, input)
    }

    @Transactional
    fun delete(familyId: String, babyId: String) {
        if (!babies.deleteBaby(familyId, babyId)) throw NotFoundException("No baby $babyId in this family")
    }

    fun context(familyId: String, babyId: String): BabyContext {
        val baby = baby(familyId, babyId)
        val region = families.family(familyId)?.region ?: "england"
        val today = today()
        val schedule = ScheduleEngine.forBaby(
            baby.dateOfBirth, content.schedule(region), babies.appointments(babyId), babies.doses(babyId), today,
        )
        return BabyContext(
            baby = baby,
            region = region,
            content = content,
            snapshot = tracker.snapshot(baby, babies.logs(babyId), today),
            moments = babies.moments(babyId),
            reviewNotes = babies.reviewNotes(babyId),
            schedule = schedule,
            tastings = babies.tastings(babyId),
            ticks = babies.ticks(babyId),
            notes = babies.assistantNotes(babyId),
        )
    }

    private fun save(familyId: String, id: String, input: BabyInput): Baby {
        require(input.name.isNotBlank()) { "A name is needed" }
        require(!input.dateOfBirth.isAfter(today())) { "Date of birth can't be in the future" }
        input.dueDate?.let { require(!it.isBefore(input.dateOfBirth.minusWeeks(4)) && !it.isAfter(input.dateOfBirth.plusWeeks(20))) {
            "Due date should be within 20 weeks after the date of birth"
        } }
        val baby = Baby(id, input.name.trim(), input.dateOfBirth, input.dueDate, input.sex, input.dobEstimated)
        babies.saveBaby(familyId, baby)
        return baby
    }
}
