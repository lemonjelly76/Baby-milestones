package io.github.lemonjelly76.babymilestones.core.weeks

import io.github.lemonjelly76.babymilestones.core.Fixtures
import io.github.lemonjelly76.babymilestones.core.Fixtures.achieved
import io.github.lemonjelly76.babymilestones.core.Fixtures.baby
import io.github.lemonjelly76.babymilestones.core.Fixtures.date
import io.github.lemonjelly76.babymilestones.core.schedule.ScheduleEngine
import io.github.lemonjelly76.babymilestones.core.tracking.Baby
import io.github.lemonjelly76.babymilestones.core.tracking.MilestoneLog
import io.github.lemonjelly76.babymilestones.core.tracking.MilestoneStatus
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WeekByWeekTest {

    private val content = Fixtures.content
    private val weeks = WeekByWeek(content)

    private fun inputs(baby: Baby, today: LocalDate, logs: List<MilestoneLog> = emptyList(), region: String = "england") = WeekInputs(
        baby, region, logs, ScheduleEngine.forBaby(baby.dateOfBirth, content.schedule(region), emptyList(), emptyList(), today),
    )

    private fun page(baby: Baby, week: Int, today: LocalDate = baby.dateOfBirth.plusWeeks(week.toLong()), region: String = "england",
                     logs: List<MilestoneLog> = emptyList()) =
        weeks.page(inputs(baby, today, logs, region), week, today)

    @Test
    fun `the current week counts whole weeks since birth`() {
        val ava = baby("2026-04-21")
        assertEquals(26, weeks.currentWeek(ava, date("2026-10-23")))
        assertEquals(0, weeks.currentWeek(ava, date("2026-04-27")))
        assertEquals(1, weeks.currentWeek(ava, date("2026-04-28")))
    }

    @Test
    fun `each page covers seven days and is titled by age`() {
        val ava = baby("2026-04-21")
        val first = page(ava, 0)
        assertEquals("First week", first.title)
        assertEquals(date("2026-04-21"), first.startsOn)
        assertEquals(date("2026-04-27"), first.endsOn)

        val half = page(ava, 26)
        assertEquals("26 weeks old", half.title)
        assertEquals("About 6 months", half.subtitle)
        assertEquals("2 years", page(ava, 104).title)
    }

    @Test
    fun `the first week has the newborn checks and what to know about feeding, weight and the cord`() {
        val first = page(baby("2026-04-21"), 0)
        assertTrue(first.appointments.any { it.rule.id == "newborn_blood_spot" }, first.appointments.map { it.rule.id }.toString())
        val notes = first.notes.map { it.id }
        assertTrue(listOf("week.weight", "week.cord", "week.register_birth", "week.feeding_help").all { it in notes }, notes.toString())
    }

    @Test
    fun `week 8 has the first vaccinations`() {
        val week8 = page(baby("2026-04-21"), 8)
        assertTrue(week8.doses.map { it.rule.vaccine }.containsAll(listOf("6-in-1", "Rotavirus", "MenB")), week8.doses.toString())
    }

    @Test
    fun `this week counts earlier vaccinations not marked as given, rather than listing them`() {
        val ava = baby("2026-04-21")
        val now = page(ava, 26, date("2026-10-23"))
        assertTrue(now.doses.isEmpty(), now.doses.toString())
        assertTrue(now.earlierDosesDue >= 6, now.earlierDosesDue.toString())
        assertEquals(0, page(ava, 27, date("2026-10-23")).earlierDosesDue)
    }

    @Test
    fun `crying follows the due date for a baby born early`() {
        // Born 8 weeks early: crying usually peaks around 6 to 8 weeks after the due date, so around 14 weeks after birth.
        val early = baby("2026-03-01", due = "2026-04-26")
        assertFalse(page(early, 6).notes.any { it.id == "week.crying" })
        assertTrue(page(early, 14).notes.any { it.id == "week.crying" })
        assertTrue(page(early, 14).subtitle!!.startsWith("Corrected"), page(early, 14).subtitle)
    }

    @Test
    fun `notes can be limited to a region and to the sunny months`() {
        assertFalse(page(baby("2026-04-21"), 0, region = "scotland").notes.any { it.id == "week.register_birth" })
        assertTrue(page(baby("2026-06-01"), 4).notes.any { it.id == "week.sun_under_6_months" })
        assertFalse(page(baby("2026-11-01"), 4).notes.any { it.id == "week.sun_under_6_months" })
    }

    @Test
    fun `milestones around that age show today's status, or the next checklist when none are`() {
        val ava = baby("2026-04-21")
        val today = date("2026-10-23")
        val logs = listOf(achieved("social.smiles_back", "2026-06-10"))

        val week7 = page(ava, 7, today, logs = logs)
        assertTrue(week7.milestones.isNotEmpty())
        assertFalse(week7.milestonesAreNext)
        assertEquals(MilestoneStatus.ACHIEVED, week7.milestones.first { it.id == "social.smiles_back" }.status)

        val between = page(ava, 30, today)
        assertTrue(between.milestonesAreNext)
        assertTrue(between.milestones.all { it.milestone.expectedBy == 9 })
    }

    @Test
    fun `guides that start in a week are new that week`() {
        // guide.ready_for_solids is from 4 months: 21 August for a baby born on 21 April.
        val ava = baby("2026-04-21")
        val week = weeks.currentWeek(ava, date("2026-08-21"))
        assertTrue("guide.ready_for_solids" in page(ava, week).newGuides)
        assertFalse("guide.ready_for_solids" in page(ava, week + 1).newGuides)
    }

    @Test
    fun `play ideas and the stage follow the week's age, and the current week matches today`() {
        val ava = baby("2026-04-21")
        val today = date("2026-10-23")
        assertTrue(page(ava, 26, today).current)
        assertFalse(page(ava, 27, today).current)
        assertEquals("newborn_0_2m", page(ava, 2, today).periodId)
        assertEquals("mover_6_9m", page(ava, 30, today).periodId)
        assertTrue(page(ava, 30, today).playIdeas.isNotEmpty())
    }

    @Test
    fun `every week from birth to 5 has a page`() {
        val ava = baby("2026-04-21")
        (0..WeekByWeek.LAST_WEEK).forEach { page(ava, it, date("2026-10-23")) }
    }
}
