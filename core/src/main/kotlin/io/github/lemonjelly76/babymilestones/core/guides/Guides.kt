package io.github.lemonjelly76.babymilestones.core.guides

import io.github.lemonjelly76.babymilestones.core.json.Date
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
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
            offersOn = nextWorkingDay(LocalDate.of(startYear, Month.APRIL, 16)),
        )
    }

    /** Other nations' admissions work differently, so dates are only worked out for England. */
    fun forRegion(region: String, dateOfBirth: LocalDate): SchoolDates? = if (region == "england") england(dateOfBirth) else null

    /** Skips weekends and England's Easter bank holidays, the only ones that can fall around 16 April. */
    private fun nextWorkingDay(date: LocalDate): LocalDate {
        val easter = easterSunday(date.year)
        val holidays = setOf(easter.minusDays(2), easter.plusDays(1))
        return generateSequence(date) { it.plusDays(1) }
            .first { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY && it !in holidays }
    }

    /** The Gregorian computus (Meeus/Jones/Butcher). */
    private fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val h = (19 * a + b - b / 4 - (b - (b + 8) / 25 + 1) / 3 + 15) % 30
        val l = (32 + 2 * (b % 4) + 2 * (c / 4) - h - c % 4) % 7
        val m = (a + 11 * h + 22 * l) / 451
        return LocalDate.of(year, (h + l - 7 * m + 114) / 31, (h + l - 7 * m + 114) % 31 + 1)
    }
}
