package io.github.lemonjelly76.babymilestones.core.weeks

import io.github.lemonjelly76.babymilestones.core.age.AgeEngine
import io.github.lemonjelly76.babymilestones.core.content.ContentBundle
import io.github.lemonjelly76.babymilestones.core.food.PlanDay
import io.github.lemonjelly76.babymilestones.core.guidance.PlayIdea
import io.github.lemonjelly76.babymilestones.core.guidance.WeeklyPlay
import io.github.lemonjelly76.babymilestones.core.json.Date
import io.github.lemonjelly76.babymilestones.core.schedule.AppointmentStatus
import io.github.lemonjelly76.babymilestones.core.schedule.BabySchedule
import io.github.lemonjelly76.babymilestones.core.schedule.DoseStatus
import io.github.lemonjelly76.babymilestones.core.schedule.ScheduledAppointment
import io.github.lemonjelly76.babymilestones.core.schedule.ScheduledDose
import io.github.lemonjelly76.babymilestones.core.tracking.Baby
import io.github.lemonjelly76.babymilestones.core.tracking.MilestoneLog
import io.github.lemonjelly76.babymilestones.core.tracking.TrackedMilestone
import io.github.lemonjelly76.babymilestones.core.tracking.Tracker
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** Which clock a week note counts on: weeks since birth, or since the due date while milestone ages are corrected. */
@Serializable
enum class WeekClock {
    @SerialName("birth") BIRTH,
    @SerialName("development") DEVELOPMENT,
}

/** Something worth knowing in particular weeks of a baby's life, from the content bundle's weeks.json. */
@Serializable
data class WeekNote(
    val id: String,
    val title: String,
    val text: String,
    @SerialName("from_week") val fromWeek: Int,
    @SerialName("to_week") val toWeek: Int,
    val clock: WeekClock = WeekClock.BIRTH,
    val guide: String? = null,
    /** UK nations it applies to; empty means all. */
    val regions: List<String> = emptyList(),
    /** Calendar months it applies in (1 to 12), for seasonal advice; empty means all year. */
    @SerialName("in_months") val inMonths: List<Int> = emptyList(),
    val sources: List<String>,
)

/** One baby's records, as the week pages need them. */
data class WeekInputs(
    val baby: Baby,
    val region: String,
    val logs: List<MilestoneLog>,
    val schedule: BabySchedule,
    val foodDays: List<PlanDay> = emptyList(),
)

/**
 * One week of a baby's life: what's specific to it, plus the appointments, milestones, guides, play ideas and planned
 * foods that fall in it. Week 0 is the seven days from birth.
 */
@Serializable
data class WeekPage(
    val week: Int,
    val startsOn: Date,
    val endsOn: Date,
    val title: String,
    val subtitle: String? = null,
    val current: Boolean,
    val notes: List<WeekNote>,
    val appointments: List<ScheduledAppointment>,
    val doses: List<ScheduledDose>,
    /** In the current week only: doses due before it that aren't marked as given yet. */
    val earlierDosesDue: Int = 0,
    /** Milestones whose "due now" window overlaps the week, with today's status; or the next checklist if none do. */
    val milestones: List<TrackedMilestone>,
    val milestonesAreNext: Boolean,
    val newGuides: List<String>,
    val periodId: String,
    val playIdeas: List<PlayIdea>,
    val food: List<PlanDay>,
)

class WeekByWeek(private val content: ContentBundle) {

    fun currentWeek(baby: Baby, today: LocalDate): Int =
        (ChronoUnit.DAYS.between(baby.dateOfBirth, today) / 7).toInt().coerceIn(0, LAST_WEEK)

    fun page(inputs: WeekInputs, week: Int, today: LocalDate): WeekPage {
        val baby = inputs.baby
        val startsOn = baby.dateOfBirth.plusWeeks(week.toLong())
        val endsOn = startsOn.plusDays(6)
        fun inWeek(date: LocalDate?) = date != null && !date.isBefore(startsOn) && !date.isAfter(endsOn)
        val current = currentWeek(baby, today) == week

        val tracker = Tracker(content)
        val now = tracker.snapshot(baby, inputs.logs, today)
        val then = if (current) now else tracker.snapshot(baby, inputs.logs, startsOn)

        val aroundThen = now.milestones.filter { !it.dueNowFrom.isAfter(endsOn) && !it.expectedBy.isBefore(startsOn) }
        val next = if (aroundThen.isNotEmpty()) emptyList() else now.milestones.filter { it.expectedBy.isAfter(endsOn) }
            .let { later -> later.minOfOrNull { it.expectedBy }?.let { first -> later.filter { it.expectedBy == first } }.orEmpty() }

        return WeekPage(
            week = week,
            startsOn = startsOn,
            endsOn = endsOn,
            title = title(week),
            subtitle = subtitle(baby, week, startsOn),
            current = current,
            notes = notes(baby, inputs.region, startsOn, endsOn),
            appointments = inputs.schedule.appointments.filter {
                inWeek(it.windowStart) || inWeek(it.record?.bookedOn) || (current && it.status == AppointmentStatus.OPEN)
            },
            doses = inputs.schedule.doses.filter { inWeek(it.dueOn) },
            earlierDosesDue = if (!current) 0 else inputs.schedule.doses.count {
                it.dueOn.isBefore(startsOn) && it.status in setOf(DoseStatus.DUE, DoseStatus.OVERDUE)
            },
            milestones = aroundThen.ifEmpty { next },
            milestonesAreNext = aroundThen.isEmpty(),
            newGuides = content.guides.filter { it.fromMonths > 0 && inWeek(baby.dateOfBirth.plusMonths(it.fromMonths.toLong())) }
                .map { it.id },
            periodId = then.period.id,
            playIdeas = WeeklyPlay.ideas(then),
            food = inputs.foodDays.filter { inWeek(it.date) },
        )
    }

    private fun notes(baby: Baby, region: String, startsOn: LocalDate, endsOn: LocalDate): List<WeekNote> {
        val developmentStart = AgeEngine.ages(baby.dates, startsOn).milestoneBaseDate
        return content.weeks.filter { note ->
            val from = if (note.clock == WeekClock.DEVELOPMENT) developmentStart else baby.dateOfBirth
            val days = ChronoUnit.DAYS.between(from, startsOn)
            days >= 0 && (days / 7).toInt() in note.fromWeek..note.toWeek &&
                (note.regions.isEmpty() || region in note.regions) &&
                (note.inMonths.isEmpty() || startsOn.monthValue in note.inMonths || endsOn.monthValue in note.inMonths)
        }
    }

    private fun title(week: Int): String = when {
        week == 0 -> "First week"
        week < WEEKS_IN_TWO_YEARS -> "$week weeks old"
        else -> roundedMonths(week).let { m ->
            listOfNotNull(plural(m / 12, "year"), (m % 12).takeIf { it > 0 }?.let { plural(it, "month") }).joinToString(" ")
        }
    }

    /** The corrected age while milestones use it, otherwise roughly how many months a week is. */
    private fun subtitle(baby: Baby, week: Int, startsOn: LocalDate): String? {
        val ages = AgeEngine.ages(baby.dates, startsOn)
        return when {
            ages.corrected?.isBeforeStart == true -> ages.corrected.display()
            ages.corrected != null -> "Corrected ${ages.corrected.display().replaceFirstChar { it.lowercase() }}"
            week in MONTHS_FROM_WEEK until WEEKS_IN_TWO_YEARS -> "About ${plural(roundedMonths(week), "month")}"
            else -> null
        }
    }

    private fun roundedMonths(week: Int) = (week * 7 / DAYS_PER_MONTH).roundToInt()

    private fun plural(n: Int, unit: String) = "$n $unit${if (n == 1) "" else "s"}"

    companion object {
        /** Pages run to the 5th birthday, the end of the content. */
        const val LAST_WEEK = 260
        private const val WEEKS_IN_TWO_YEARS = 104
        private const val MONTHS_FROM_WEEK = 13
        private const val DAYS_PER_MONTH = 30.4375
    }
}
