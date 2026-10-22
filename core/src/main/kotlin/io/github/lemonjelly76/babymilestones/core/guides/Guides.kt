package io.github.lemonjelly76.babymilestones.core.guides

import io.github.lemonjelly76.babymilestones.core.json.Date
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.Month

@Serializable
data class GuidesForAge(val now: List<Guide>, val comingUp: List<Guide>)

@Serializable
data class ChecklistProgress(val done: Int, val total: Int)

/** Guides follow chronological age: they're about everyday life (food, safety, nursery, school), not development. */
object Guides {
    const val COMING_UP_MONTHS = 3

    fun forAge(guides: List<Guide>, months: Int): GuidesForAge {
        val now = guides.filter { it.coversAge(months) }
        val comingUp = guides.filter { it.fromMonths in (months + 1)..(months + COMING_UP_MONTHS) }
        return GuidesForAge(now, comingUp)
    }

    fun progress(guide: Guide, ticks: List<ChecklistTick>): ChecklistProgress {
        val ids = guide.checklist.map { it.id }.toSet()
        return ChecklistProgress(ticks.count { it.itemId in ids }, ids.size)
    }
}

/** England's reception timetable: the national closing date (15 January) and offer day (16 April, or the next working day). */
@Serializable
data class SchoolDates(val startsOn: Date, val applyBy: Date, val offersOn: Date) {
    /** Councils open applications in the autumn before; from September is when a reminder is useful. */
    fun applicationOpen(today: LocalDate): Boolean =
        !today.isBefore(LocalDate.of(startsOn.year - 1, Month.SEPTEMBER, 1)) && !today.isAfter(applyBy)
}

object SchoolStart {
    /** Children start reception in the September after their 4th birthday; a school year runs 1 September to 31 August. */
    fun england(dateOfBirth: LocalDate): SchoolDates {
        val cohortYear = if (dateOfBirth.monthValue >= 9) dateOfBirth.year else dateOfBirth.year - 1
        val startYear = cohortYear + 5
        return SchoolDates(
            startsOn = LocalDate.of(startYear, Month.SEPTEMBER, 1),
            applyBy = LocalDate.of(startYear, Month.JANUARY, 15),
            offersOn = LocalDate.of(startYear, Month.APRIL, 16),
        )
    }
}
