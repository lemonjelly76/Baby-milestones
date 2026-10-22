package io.github.lemonjelly76.babymilestones.core.guidance

import io.github.lemonjelly76.babymilestones.core.content.ContentBundle
import io.github.lemonjelly76.babymilestones.core.content.Domain
import io.github.lemonjelly76.babymilestones.core.content.PeriodGuide
import io.github.lemonjelly76.babymilestones.core.json.Date
import io.github.lemonjelly76.babymilestones.core.schedule.BabySchedule
import io.github.lemonjelly76.babymilestones.core.tracking.BabySnapshot
import io.github.lemonjelly76.babymilestones.core.tracking.MilestoneStatus
import io.github.lemonjelly76.babymilestones.core.tracking.TrackedMilestone
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class MilestoneRef(
    val id: String,
    val title: String,
    val domain: Domain,
    val expectedByMonths: Int,
    val expectedBy: Date,
    val markedOn: Date? = null,
    val recheckOn: Date? = null,
) {
    companion object {
        fun of(t: TrackedMilestone) = MilestoneRef(
            t.id, t.milestone.title, t.milestone.domain, t.milestone.expectedBy, t.expectedBy,
            t.log?.markedOn, t.recheckOn,
        )
    }
}

@Serializable
data class AgeSummary(
    val chronological: String,
    val corrected: String? = null,
    val chronologicalWeeks: Int,
    val correctedWeeks: Int? = null,
    val milestoneAgeUses: String,
)

@Serializable
data class ScheduleItemRef(val id: String, val title: String, val from: Date, val to: Date? = null, val status: String)

@Serializable
data class PendingUpdates(
    val babyId: String,
    val name: String,
    val age: AgeSummary,
    val period: String,
    val dueNow: List<MilestoneRef>,
    val notSeenYet: List<MilestoneRef>,
    val recheckNotSure: List<MilestoneRef>,
    val appointmentsToMark: List<ScheduleItemRef>,
    val vaccinationsDue: List<ScheduleItemRef>,
    val contentVersion: String,
)

@Serializable
data class WhatsNext(
    val babyId: String,
    val horizonWeeks: Int,
    val followOn: List<MilestoneRef>,
    val upcoming: List<MilestoneRef>,
    val nextPeriod: PeriodPreview? = null,
    val appointments: List<ScheduleItemRef>,
    val vaccinations: List<ScheduleItemRef>,
    val activity: String? = null,
    val contentVersion: String,
)

@Serializable
data class PeriodPreview(val id: String, val name: String, val startsOn: Date, val summary: String)

object Guidance {

    fun ageSummary(snapshot: BabySnapshot): AgeSummary {
        val ages = snapshot.ages
        return AgeSummary(
            chronological = ages.chronological.display(),
            corrected = ages.corrected?.display(),
            chronologicalWeeks = ages.chronological.weeks,
            correctedWeeks = ages.corrected?.weeks,
            milestoneAgeUses = ages.milestoneBasis.name.lowercase(),
        )
    }

    fun pendingUpdates(snapshot: BabySnapshot, schedule: BabySchedule?): PendingUpdates = PendingUpdates(
        babyId = snapshot.baby.id,
        name = snapshot.baby.name,
        age = ageSummary(snapshot),
        period = snapshot.period.id,
        dueNow = snapshot.withStatus(MilestoneStatus.DUE_NOW).map(MilestoneRef::of),
        notSeenYet = snapshot.withStatus(MilestoneStatus.NOT_SEEN_YET).map(MilestoneRef::of),
        recheckNotSure = snapshot.milestones.filter { it.recheckDue(snapshot.today) }.map(MilestoneRef::of),
        appointmentsToMark = schedule?.appointmentsToMark().orEmpty()
            .map { ScheduleItemRef(it.rule.id, it.rule.title, it.windowStart, it.windowEnd, it.status.name.lowercase()) },
        vaccinationsDue = schedule?.dosesDue().orEmpty()
            .map { ScheduleItemRef(it.rule.code, "${it.rule.vaccine} (${it.rule.dose})", it.dueOn, null, it.status.name.lowercase()) },
        contentVersion = snapshot.contentVersion,
    )

    /**
     * What to watch for over the next [horizonWeeks]: milestones that typically follow ones achieved recently
     * (leads_to), milestones whose window opens soon, the next period, and appointments and doses coming up.
     */
    fun whatsNext(
        snapshot: BabySnapshot,
        schedule: BabySchedule?,
        content: ContentBundle,
        horizonWeeks: Int = 8,
    ): WhatsNext {
        val today = snapshot.today
        val horizon = today.plusWeeks(horizonWeeks.toLong())
        val recentWindowStart = today.minusWeeks(12)
        val open = setOf(MilestoneStatus.LATER, MilestoneStatus.COMING_UP, MilestoneStatus.DUE_NOW)

        val recentAchievements = snapshot.withStatus(MilestoneStatus.ACHIEVED)
            .filter { (it.log?.achievedOn ?: it.log?.markedOn)?.isAfter(recentWindowStart) == true }
        val followOn = recentAchievements.flatMap { it.milestone.leadsTo }.distinct()
            .mapNotNull { snapshot.tracked(it) }
            .filter { it.status in open }
            .sortedBy { it.expectedBy }
        val followIds = followOn.map { it.id }.toSet()
        val upcoming = snapshot.milestones
            .filter { it.status in open && it.id !in followIds && !it.emergingOn.isAfter(horizon) }
            .sortedBy { it.expectedBy }

        val nextPeriod = content.nextPeriod(snapshot.period)?.let { p ->
            PeriodPreview(p.id, p.name, snapshot.ages.milestoneBaseDate.plusMonths(p.fromMonths.toLong()), p.typical)
        }
        val activity = (followOn + upcoming).firstNotNullOfOrNull { it.milestone.activities.firstOrNull() }

        return WhatsNext(
            babyId = snapshot.baby.id,
            horizonWeeks = horizonWeeks,
            followOn = followOn.map(MilestoneRef::of),
            upcoming = upcoming.map(MilestoneRef::of),
            nextPeriod = nextPeriod,
            appointments = schedule?.appointments.orEmpty()
                .filter { it.record?.doneOn == null && !it.windowStart.isAfter(horizon) && !it.windowEnd.isBefore(today) }
                .map { ScheduleItemRef(it.rule.id, it.rule.title, it.windowStart, it.windowEnd, it.status.name.lowercase()) },
            vaccinations = schedule?.doses.orEmpty()
                .filter { it.record == null && !it.dueOn.isAfter(horizon) && !it.dueOn.isBefore(today.minusWeeks(4)) }
                .map { ScheduleItemRef(it.rule.code, "${it.rule.vaccine} (${it.rule.dose})", it.dueOn, null, it.status.name.lowercase()) },
            activity = activity,
            contentVersion = content.version,
        )
    }

    fun periodById(content: ContentBundle, id: String): PeriodGuide? = content.period(id)

    /** The date a period starts on this baby's milestone clock. */
    fun periodStart(snapshot: BabySnapshot, period: PeriodGuide): LocalDate =
        snapshot.ages.milestoneBaseDate.plusMonths(period.fromMonths.toLong())
}
