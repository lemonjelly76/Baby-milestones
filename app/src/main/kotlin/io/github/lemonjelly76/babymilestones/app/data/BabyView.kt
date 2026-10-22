package io.github.lemonjelly76.babymilestones.app.data

import io.github.lemonjelly76.babymilestones.core.concerns.Concern
import io.github.lemonjelly76.babymilestones.core.concerns.ConcernEngine
import io.github.lemonjelly76.babymilestones.core.content.ContentBundle
import io.github.lemonjelly76.babymilestones.core.content.PeriodGuide
import io.github.lemonjelly76.babymilestones.core.food.FoodReport
import io.github.lemonjelly76.babymilestones.core.food.FoodTracker
import io.github.lemonjelly76.babymilestones.core.guides.Guides
import io.github.lemonjelly76.babymilestones.core.guides.GuidesForAge
import io.github.lemonjelly76.babymilestones.core.guides.SchoolDates
import io.github.lemonjelly76.babymilestones.core.guides.SchoolStart
import io.github.lemonjelly76.babymilestones.core.guidance.Guidance
import io.github.lemonjelly76.babymilestones.core.guidance.PendingUpdates
import io.github.lemonjelly76.babymilestones.core.guidance.WhatsNext
import io.github.lemonjelly76.babymilestones.core.schedule.BabySchedule
import io.github.lemonjelly76.babymilestones.core.schedule.ScheduleEngine
import io.github.lemonjelly76.babymilestones.core.summary.ReviewSummary
import io.github.lemonjelly76.babymilestones.core.timeline.Timeline
import io.github.lemonjelly76.babymilestones.core.timeline.TimelineEntry
import io.github.lemonjelly76.babymilestones.core.tracking.BabySnapshot
import io.github.lemonjelly76.babymilestones.core.tracking.MilestoneStatus
import io.github.lemonjelly76.babymilestones.core.tracking.TrackedMilestone
import io.github.lemonjelly76.babymilestones.core.tracking.Tracker
import java.time.LocalDate

/**
 * Everything the screens show for one baby, computed on the phone by the same core library the server uses,
 * so the Today screen and the assistant always agree, online or off.
 */
class BabyView(val data: BabyData, val content: ContentBundle, val today: LocalDate) {
    val baby get() = data.baby
    val snapshot: BabySnapshot = Tracker(content).snapshot(data.baby, data.logs, today)
    val schedule: BabySchedule = ScheduleEngine.forBaby(
        data.baby.dateOfBirth, content.schedule(data.region), data.appointments, data.vaccinations, today,
    )
    val concerns: List<Concern> by lazy { ConcernEngine.concerns(snapshot, content.concerns) }
    val pending: PendingUpdates by lazy { Guidance.pendingUpdates(snapshot, schedule) }
    val whatsNext: WhatsNext by lazy { Guidance.whatsNext(snapshot, schedule, content) }
    val timeline: List<TimelineEntry> by lazy { Timeline.build(snapshot, data.moments, schedule) }
    val summary: ReviewSummary by lazy { ReviewSummary.build(snapshot, concerns, data.reviewNotes, foods) }

    /** Everyday guides and food follow chronological age. */
    val ageMonths: Int get() = snapshot.ages.chronological.months
    val foods: FoodReport by lazy { FoodTracker(content).report(data.tastings, today, ageMonths) }
    val guides: GuidesForAge by lazy { Guides.forAge(content.guides, ageMonths) }
    val ticked: Set<String> get() = data.checklist.map { it.itemId }.toSet()
    val showFoods: Boolean get() = ageMonths in 4 until 36 || data.tastings.isNotEmpty()

    /** England's reception dates; the other nations' admissions work differently. */
    val schoolDates: SchoolDates? get() = if (data.region == "england") SchoolStart.england(baby.dateOfBirth) else null

    val aroundNow: List<TrackedMilestone> get() = snapshot.withStatus(MilestoneStatus.DUE_NOW)
    val seenYet: List<TrackedMilestone> get() =
        snapshot.withStatus(MilestoneStatus.NOT_SEEN_YET) + snapshot.milestones.filter { it.recheckDue(today) }
    val watchFor: List<TrackedMilestone> get() = snapshot.withStatus(MilestoneStatus.COMING_UP)
    val notYetLogged: Int get() = snapshot.milestones.count { it.log == null && !today.isBefore(it.emergingOn) }

    /** One tip for today: an activity from what's around now, or else from the period guide's play ideas. */
    val tip: String? get() = (aroundNow + watchFor).firstNotNullOfOrNull { it.milestone.activities.firstOrNull() }
        ?: snapshot.period.play.firstOrNull()

    fun periodStart(period: PeriodGuide): LocalDate = Guidance.periodStart(snapshot, period)

    /** Milestones that typically follow the baby's recent achievements, for the period guide's "what happens next". */
    fun followOn(): List<TrackedMilestone> = whatsNext.followOn.mapNotNull { snapshot.tracked(it.id) }
}
