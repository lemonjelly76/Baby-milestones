package io.github.lemonjelly76.babymilestones.core.tracking

import io.github.lemonjelly76.babymilestones.core.age.AgeEngine
import io.github.lemonjelly76.babymilestones.core.age.Ages
import io.github.lemonjelly76.babymilestones.core.age.BabyDates
import io.github.lemonjelly76.babymilestones.core.content.ContentBundle
import io.github.lemonjelly76.babymilestones.core.content.Milestone
import io.github.lemonjelly76.babymilestones.core.content.PeriodGuide
import io.github.lemonjelly76.babymilestones.core.json.Date
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class Baby(
    val id: String,
    val name: String,
    val dateOfBirth: Date,
    val dueDate: Date? = null,
    val sex: String? = null,
    val dobEstimated: Boolean = false,
    /** When the family started using the app; milestones due before then are catch-up, not concerns. */
    val trackingSince: Date? = null,
) {
    val dates: BabyDates get() = BabyDates(dateOfBirth, dueDate)
}

@Serializable
enum class LogState {
    @SerialName("achieved") ACHIEVED,
    @SerialName("not_sure") NOT_SURE,
}

@Serializable
enum class LogSource {
    @SerialName("manual") MANUAL,
    @SerialName("backfill") BACKFILL,
    @SerialName("assistant") ASSISTANT,
}

/** The only milestone data that is stored: what a carer said. Status is always recomputed from age and this log. */
@Serializable
data class MilestoneLog(
    val id: String,
    val babyId: String,
    val milestoneId: String,
    val state: LogState,
    val achievedOn: Date? = null,
    val approximate: Boolean = false,
    val markedOn: Date,
    val note: String? = null,
    val photoRef: String? = null,
    val source: LogSource = LogSource.MANUAL,
    val loggedBy: String? = null,
    /** Set when a carer reports the baby no longer does something they used to. */
    val lostOn: Date? = null,
) {
    /** Ticked on the catch-up list without a date: it happened some time before [achievedOn]. */
    val isUndatedCatchUp: Boolean get() = source == LogSource.BACKFILL && approximate
}

@Serializable
enum class MilestoneStatus(val label: String) {
    @SerialName("later") LATER("Later"),
    @SerialName("coming_up") COMING_UP("Watch for"),
    @SerialName("due_now") DUE_NOW("Around now"),
    @SerialName("achieved") ACHIEVED("Achieved"),
    @SerialName("not_seen_yet") NOT_SEEN_YET("Seen this yet?"),
    @SerialName("not_sure") NOT_SURE("Not sure"),
    @SerialName("skill_lost") SKILL_LOST("No longer doing this"),
}

@Serializable
data class TrackedMilestone(
    val milestone: Milestone,
    val status: MilestoneStatus,
    val log: MilestoneLog?,
    val emergingOn: Date,
    val dueNowFrom: Date,
    val expectedBy: Date,
    val recheckOn: Date? = null,
) {
    val id: String get() = milestone.id

    /** Not achieved and past its checklist age, including "not sure" answers. */
    fun isOpenPastExpected(today: LocalDate) =
        (status == MilestoneStatus.NOT_SEEN_YET || status == MilestoneStatus.NOT_SURE) && today.isAfter(expectedBy)

    fun recheckDue(today: LocalDate) = status == MilestoneStatus.NOT_SURE && recheckOn != null && !today.isBefore(recheckOn)
}

object StatusRules {
    const val DUE_NOW_WINDOW_DAYS = 28L
    const val NOT_SURE_RECHECK_DAYS = 14L

    fun evaluate(milestone: Milestone, ages: Ages, log: MilestoneLog?, today: LocalDate): TrackedMilestone {
        val emergingOn = AgeEngine.milestoneDate(ages, milestone.emergingFrom)
        val expectedBy = AgeEngine.milestoneDate(ages, milestone.expectedBy)
        val dueNowFrom = expectedBy.minusDays(DUE_NOW_WINDOW_DAYS)
        val status = when {
            log?.state == LogState.ACHIEVED && log.lostOn != null -> MilestoneStatus.SKILL_LOST
            log?.state == LogState.ACHIEVED -> MilestoneStatus.ACHIEVED
            log?.state == LogState.NOT_SURE -> MilestoneStatus.NOT_SURE
            today.isAfter(expectedBy) -> MilestoneStatus.NOT_SEEN_YET
            !today.isBefore(dueNowFrom) -> MilestoneStatus.DUE_NOW
            !today.isBefore(emergingOn) -> MilestoneStatus.COMING_UP
            else -> MilestoneStatus.LATER
        }
        val recheckOn = if (status == MilestoneStatus.NOT_SURE) log!!.markedOn.plusDays(NOT_SURE_RECHECK_DAYS) else null
        return TrackedMilestone(milestone, status, log, emergingOn, dueNowFrom, expectedBy, recheckOn)
    }
}

@Serializable
data class BabySnapshot(
    val baby: Baby,
    val today: Date,
    val ages: Ages,
    val period: PeriodGuide,
    val milestones: List<TrackedMilestone>,
    val contentVersion: String,
) {
    fun withStatus(vararg statuses: MilestoneStatus) = milestones.filter { it.status in statuses }
    fun counts(): Map<MilestoneStatus, Int> = MilestoneStatus.entries.associateWith { s -> milestones.count { it.status == s } }
    fun lastLogDate(): LocalDate? = milestones.mapNotNull { it.log?.markedOn }.maxOrNull()
    fun tracked(id: String): TrackedMilestone? = milestones.firstOrNull { it.id == id }

    /** Milestones the baby might be expected to have done before today, for the onboarding backfill checklist. */
    fun backfillCandidates() = milestones.filter { it.log == null && !today.isBefore(it.emergingOn) }
}

/** Joins the content bundle with one baby's log; the app, notifications and MCP server all build their views on this. */
class Tracker(val content: ContentBundle) {

    fun snapshot(baby: Baby, logs: List<MilestoneLog>, today: LocalDate): BabySnapshot {
        val ages = AgeEngine.ages(baby.dates, today)
        // A milestone keeps one current log entry; the newest marking wins if older ones linger.
        val logByMilestone = logs.filter { it.babyId == baby.id }
            .sortedBy { it.markedOn }
            .associateBy { it.milestoneId }
        val tracked = content.milestones.map { StatusRules.evaluate(it, ages, logByMilestone[it.id], today) }
        return BabySnapshot(baby, today, ages, periodFor(ages), tracked, content.version)
    }

    fun periodFor(ages: Ages): PeriodGuide =
        content.periodForMonths(if (ages.milestoneAge.isBeforeStart) 0 else ages.milestoneAge.months)
}
