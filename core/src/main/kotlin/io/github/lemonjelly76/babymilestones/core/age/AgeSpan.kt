package io.github.lemonjelly76.babymilestones.core.age

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit

/** An age counted in calendar months (so month ends and leap years behave) plus the days past the last whole month. */
@Serializable
data class AgeSpan(val totalDays: Int, val months: Int, val daysIntoMonth: Int) {

    val weeks: Int get() = totalDays / 7
    val isBeforeStart: Boolean get() = totalDays < 0

    /** Weeks under 3 months, months and weeks under 2 years, then years and months. */
    fun display(): String = when {
        isBeforeStart -> {
            val weeksToGo = -totalDays / 7
            if (weeksToGo == 0) "${plural(-totalDays, "day")} before due date"
            else "${plural(weeksToGo, "week")} before due date"
        }
        totalDays == 0 -> "Born today"
        months < 3 -> joinParts(plural(weeks, "week", skipZero = true), plural(totalDays % 7, "day", skipZero = true))
        months < 24 -> joinParts(plural(months, "month"), plural(daysIntoMonth / 7, "week", skipZero = true))
        else -> joinParts(plural(months / 12, "year"), plural(months % 12, "month", skipZero = true))
    }

    companion object {
        /**
         * Whole months are counted the way milestone dates are worked out (`start.plusMonths(n)`), so a baby born on
         * 31 January is 1 month old on 28 February, the same day a 1-month milestone falls due.
         */
        fun between(start: LocalDate, end: LocalDate): AgeSpan {
            val totalDays = ChronoUnit.DAYS.between(start, end).toInt()
            if (totalDays < 0) return AgeSpan(totalDays, 0, 0)
            var months = Period.between(start, end).toTotalMonths().toInt()
            while (!start.plusMonths(months + 1L).isAfter(end)) months++
            val daysIntoMonth = ChronoUnit.DAYS.between(start.plusMonths(months.toLong()), end).toInt()
            return AgeSpan(totalDays, months, daysIntoMonth)
        }

        private fun plural(n: Int, unit: String, skipZero: Boolean = false): String? =
            if (n == 0 && skipZero) null else "$n $unit${if (n == 1) "" else "s"}"

        private fun joinParts(vararg parts: String?) = parts.filterNotNull().joinToString(" ")
    }
}
