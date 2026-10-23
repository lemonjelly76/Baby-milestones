package io.github.lemonjelly76.babymilestones.server.service

import io.github.lemonjelly76.babymilestones.core.concerns.Concern
import io.github.lemonjelly76.babymilestones.core.concerns.ConcernEngine
import io.github.lemonjelly76.babymilestones.core.api.BabyData
import io.github.lemonjelly76.babymilestones.core.content.ContentBundle
import io.github.lemonjelly76.babymilestones.core.food.FoodPlanner
import io.github.lemonjelly76.babymilestones.core.food.FoodReport
import io.github.lemonjelly76.babymilestones.core.food.FoodTasting
import io.github.lemonjelly76.babymilestones.core.food.FoodTracker
import io.github.lemonjelly76.babymilestones.core.food.PlannedFood
import io.github.lemonjelly76.babymilestones.core.growth.GrowthEngine
import io.github.lemonjelly76.babymilestones.core.growth.GrowthMeasurement
import io.github.lemonjelly76.babymilestones.core.growth.GrowthReport
import io.github.lemonjelly76.babymilestones.core.guides.ChecklistTick
import io.github.lemonjelly76.babymilestones.core.records.FirstWord
import io.github.lemonjelly76.babymilestones.core.records.ToothRecord
import io.github.lemonjelly76.babymilestones.core.timeline.Timeline
import io.github.lemonjelly76.babymilestones.core.notes.AssistantNote
import io.github.lemonjelly76.babymilestones.core.schedule.BabySchedule
import io.github.lemonjelly76.babymilestones.core.schedule.ScheduleEngine
import io.github.lemonjelly76.babymilestones.core.summary.ReviewNote
import io.github.lemonjelly76.babymilestones.core.summary.ReviewSummary
import io.github.lemonjelly76.babymilestones.core.timeline.Moment
import io.github.lemonjelly76.babymilestones.core.tracking.Baby
import io.github.lemonjelly76.babymilestones.core.tracking.BabySnapshot
import io.github.lemonjelly76.babymilestones.core.tracking.Tracker
import io.github.lemonjelly76.babymilestones.core.weeks.WeekInputs
import io.github.lemonjelly76.babymilestones.server.store.BabyStore
import io.github.lemonjelly76.babymilestones.server.store.ChildRecordStore
import io.github.lemonjelly76.babymilestones.server.store.FoodStore
import io.github.lemonjelly76.babymilestones.server.store.GuideStore
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
    loadTastings: () -> List<FoodTasting> = { emptyList() },
    loadTicks: () -> List<ChecklistTick> = { emptyList() },
    loadNotes: () -> List<AssistantNote> = { emptyList() },
    loadGrowth: () -> List<GrowthMeasurement> = { emptyList() },
    loadWords: () -> List<FirstWord> = { emptyList() },
    loadTeeth: () -> List<ToothRecord> = { emptyList() },
    loadPlan: () -> List<PlannedFood> = { emptyList() },
) {
    val foodPlan: List<PlannedFood> by lazy(loadPlan)
    val growthMeasurements: List<GrowthMeasurement> by lazy(loadGrowth)
    val words: List<FirstWord> by lazy(loadWords)
    val teeth: List<ToothRecord> by lazy(loadTeeth)
    val tastings: List<FoodTasting> by lazy(loadTastings)
    val ticks: List<ChecklistTick> by lazy(loadTicks)
    val notes: List<AssistantNote> by lazy(loadNotes)
    val today: LocalDate get() = snapshot.today
    val weekInputs: WeekInputs get() = WeekInputs(
        baby, region, snapshot.milestones.mapNotNull { it.log }, schedule, FoodPlanner.days(content, foodPlan, tastings, baby.dateOfBirth),
    )
    val concerns: List<Concern> by lazy { ConcernEngine.concerns(snapshot, content.concerns) }
    val foods: FoodReport by lazy { FoodTracker(content).report(tastings, today, snapshot.ages.chronological.months) }
    val growth: GrowthReport by lazy { GrowthEngine(content.growth!!).report(baby, growthMeasurements, today) }
    val summary: ReviewSummary by lazy { ReviewSummary.build(snapshot, concerns, reviewNotes, foods, growth, words, teeth) }
    fun timeline(from: LocalDate? = null, to: LocalDate? = null) = Timeline.build(
        snapshot, moments, schedule, from, to, words, teeth, toothName = { content.tooth(it)?.name ?: it },
    )
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
    private val records: ChildRecordStore,
    private val foodStore: FoodStore,
    private val guideStore: GuideStore,
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
            loadTastings = { foodStore.tastings(babyId) },
            loadTicks = { guideStore.ticks(babyId) },
            loadNotes = { guideStore.assistantNotes(babyId) },
            loadGrowth = { records.growth(babyId) },
            loadWords = { records.words(babyId) },
            loadTeeth = { records.teeth(babyId) },
            loadPlan = { foodStore.plan(babyId) },
        )
    }

    /** The raw records the app computes its views from with the same core library (ADR-009). */
    fun data(familyId: String, babyId: String): BabyData {
        val baby = baby(familyId, babyId)
        return BabyData(
            baby, families.family(familyId)?.region ?: "england", babies.logs(babyId), babies.moments(babyId),
            babies.appointments(babyId), babies.doses(babyId), babies.reviewNotes(babyId), content.version,
            foodStore.tastings(babyId), guideStore.ticks(babyId), guideStore.assistantNotes(babyId),
            records.growth(babyId), records.words(babyId), records.teeth(babyId), foodStore.plan(babyId),
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
