package io.github.lemonjelly76.babymilestones.core.age

import io.github.lemonjelly76.babymilestones.core.json.Date
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class BabyDates(val dateOfBirth: LocalDate, val dueDate: LocalDate? = null)

enum class AgeBasis { CHRONOLOGICAL, CORRECTED }

/**
 * Everything the app, notifications and MCP server need to know about how old a baby is.
 * Milestone ages are counted from [milestoneBaseDate]: the due date while correction applies, otherwise the birth date.
 */
@Serializable
data class Ages(
    val chronological: AgeSpan,
    val corrected: AgeSpan?,
    val weeksEarly: Int,
    val milestoneBasis: AgeBasis,
    val milestoneBaseDate: Date,
) {
    val milestoneAge: AgeSpan get() = if (milestoneBasis == AgeBasis.CORRECTED) corrected!! else chronological

    /** "7 weeks 3 days (corrected 3 weeks 3 days)" style label that always says which age is which. */
    fun describe(): String =
        if (corrected == null) chronological.display()
        else "${chronological.display()} (corrected ${corrected.display()})"
}

object AgeEngine {
    /** Born this many days or more before the due date and milestone ages are corrected. */
    const val CORRECTION_THRESHOLD_DAYS = 21L

    /** Corrected age is used for milestones until this chronological age, then chronological age. */
    const val CORRECT_UNTIL_MONTHS = 24

    fun ages(dates: BabyDates, today: LocalDate): Ages {
        val chronological = AgeSpan.between(dates.dateOfBirth, today)
        val daysEarly = dates.dueDate?.let { ChronoUnit.DAYS.between(dates.dateOfBirth, it) } ?: 0L
        if (daysEarly < CORRECTION_THRESHOLD_DAYS) {
            return Ages(chronological, null, 0, AgeBasis.CHRONOLOGICAL, dates.dateOfBirth)
        }
        // Corrected age = chronological age − (due date − date of birth) = time since the due date.
        val corrected = AgeSpan.between(dates.dueDate!!, today)
        val weeksEarly = (daysEarly / 7).toInt()
        val stillCorrecting = today.isBefore(dates.dateOfBirth.plusMonths(CORRECT_UNTIL_MONTHS.toLong()))
        return if (stillCorrecting) Ages(chronological, corrected, weeksEarly, AgeBasis.CORRECTED, dates.dueDate)
        else Ages(chronological, null, weeksEarly, AgeBasis.CHRONOLOGICAL, dates.dateOfBirth)
    }

    /** "She's 5 months" → an approximate date of birth, to be flagged as estimated until the parent confirms it. */
    fun estimateDateOfBirth(today: LocalDate, months: Int = 0, weeks: Int = 0): LocalDate =
        today.minusMonths(months.toLong()).minusWeeks(weeks.toLong())

    /** The date a baby reaches [months] on the milestone clock. */
    fun milestoneDate(ages: Ages, months: Int): LocalDate = ages.milestoneBaseDate.plusMonths(months.toLong())
}
