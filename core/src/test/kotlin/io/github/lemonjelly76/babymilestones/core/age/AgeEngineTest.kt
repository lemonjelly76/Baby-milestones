package io.github.lemonjelly76.babymilestones.core.age

import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgeEngineTest {

    private fun date(s: String) = LocalDate.parse(s)

    @Test
    fun `term baby has no corrected age`() {
        val ages = AgeEngine.ages(BabyDates(date("2026-08-01"), date("2026-08-10")), date("2026-10-21"))
        assertNull(ages.corrected)
        assertEquals(AgeBasis.CHRONOLOGICAL, ages.milestoneBasis)
        assertEquals(date("2026-08-01"), ages.milestoneBaseDate)
        assertEquals(0, ages.weeksEarly)
    }

    @Test
    fun `born 20 days early is not corrected but 21 days is`() {
        val today = date("2026-10-21")
        assertNull(AgeEngine.ages(BabyDates(date("2026-07-01"), date("2026-07-21")), today).corrected)
        val corrected = AgeEngine.ages(BabyDates(date("2026-07-01"), date("2026-07-22")), today)
        assertEquals(AgeBasis.CORRECTED, corrected.milestoneBasis)
        assertEquals(3, corrected.weeksEarly)
    }

    @Test
    fun `corrected age is chronological age minus weeks early`() {
        // Born 8 weeks early: chronological 26 weeks, corrected 18 weeks.
        val dob = date("2026-04-22")
        val ages = AgeEngine.ages(BabyDates(dob, dob.plusWeeks(8)), dob.plusWeeks(26))
        assertEquals(26, ages.chronological.weeks)
        assertEquals(18, ages.corrected!!.weeks)
        assertEquals(dob.plusWeeks(8), ages.milestoneBaseDate)
    }

    @Test
    fun `milestones switch to chronological age at 24 months`() {
        val dob = date("2024-01-10")
        val due = date("2024-03-10")
        val before = AgeEngine.ages(BabyDates(dob, due), date("2026-01-09"))
        val after = AgeEngine.ages(BabyDates(dob, due), date("2026-01-10"))
        assertEquals(AgeBasis.CORRECTED, before.milestoneBasis)
        assertEquals(AgeBasis.CHRONOLOGICAL, after.milestoneBasis)
        assertNull(after.corrected)
        assertEquals(8, after.weeksEarly)
    }

    @Test
    fun `before the due date the corrected age is negative`() {
        val ages = AgeEngine.ages(BabyDates(date("2026-10-01"), date("2026-11-12")), date("2026-10-21"))
        assertTrue(ages.corrected!!.isBeforeStart)
        assertEquals("3 weeks before due date", ages.corrected.display())
        assertEquals("2 weeks 6 days (corrected 3 weeks before due date)", ages.describe())
    }

    @Test
    fun `month ends and leap days count as whole months`() {
        assertEquals(1, AgeSpan.between(date("2026-01-31"), date("2026-02-28")).months)
        assertEquals(0, AgeSpan.between(date("2026-01-31"), date("2026-02-27")).months)
        assertEquals(12, AgeSpan.between(date("2024-02-29"), date("2025-02-28")).months)
        assertEquals(0, AgeSpan.between(date("2024-02-29"), date("2025-02-28")).daysIntoMonth)
    }

    @Test
    fun `ages display as weeks, then months and weeks, then years and months`() {
        val dob = date("2026-01-01")
        assertEquals("Born today", AgeSpan.between(dob, dob).display())
        assertEquals("5 days", AgeSpan.between(dob, dob.plusDays(5)).display())
        assertEquals("7 weeks 3 days", AgeSpan.between(dob, dob.plusDays(52)).display())
        assertEquals("1 week", AgeSpan.between(dob, dob.plusDays(7)).display())
        assertEquals("5 months 2 weeks", AgeSpan.between(dob, date("2026-06-15")).display())
        assertEquals("4 weeks 3 days", AgeSpan.between(dob, date("2026-02-01")).display())
        assertEquals("4 months", AgeSpan.between(dob, date("2026-05-01")).display())
        assertEquals("2 years", AgeSpan.between(dob, date("2028-01-01")).display())
        assertEquals("3 years 4 months", AgeSpan.between(dob, date("2029-05-20")).display())
    }

    @Test
    fun `an age given in months estimates the date of birth`() {
        assertEquals(date("2026-05-21"), AgeEngine.estimateDateOfBirth(date("2026-10-21"), months = 5))
    }
}
