package io.github.lemonjelly76.babymilestones.core.guidance

import io.github.lemonjelly76.babymilestones.core.Fixtures
import io.github.lemonjelly76.babymilestones.core.Fixtures.achieved
import io.github.lemonjelly76.babymilestones.core.Fixtures.baby
import io.github.lemonjelly76.babymilestones.core.Fixtures.date
import io.github.lemonjelly76.babymilestones.core.Fixtures.notSure
import io.github.lemonjelly76.babymilestones.core.schedule.ScheduleEngine
import io.github.lemonjelly76.babymilestones.core.tracking.Tracker
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GuidanceTest {

    private val content = Fixtures.content
    private val tracker = Tracker(content)

    @Test
    fun `pending updates lists due now, not seen yet and not sure rechecks`() {
        val baby = baby("2026-04-21", due = "2026-05-19")
        val today = date("2026-10-23")
        val snapshot = tracker.snapshot(baby, listOf(notSure("social.laughs", "2026-10-02")), today)
        val schedule = ScheduleEngine.forBaby(baby.dateOfBirth, content.schedule("england"), emptyList(), emptyList(), today)
        val pending = Guidance.pendingUpdates(snapshot, schedule)

        assertEquals(26, pending.age.chronologicalWeeks)
        assertEquals(22, pending.age.correctedWeeks)
        assertEquals("corrected", pending.age.milestoneAgeUses)
        assertEquals("infant_4_6m", pending.period)
        assertTrue(pending.dueNow.any { it.id == "move.rolls_tummy_to_back" })
        assertEquals(listOf("social.laughs"), pending.recheckNotSure.map { it.id })
        assertTrue(pending.appointmentsToMark.any { it.id == "new_birth_visit" })
        assertTrue(pending.vaccinationsDue.any { it.id == "menb_1" })
        assertEquals(content.version, pending.contentVersion)
    }

    @Test
    fun `what's next follows leads_to from recent achievements first`() {
        val snapshot = tracker.snapshot(
            baby("2026-04-21"), listOf(achieved("move.rolls_tummy_to_back", "2026-10-10")), date("2026-10-21"),
        )
        val next = Guidance.whatsNext(snapshot, null, content)
        assertEquals(listOf("move.gets_to_sitting"), next.followOn.map { it.id })
        assertTrue(next.upcoming.none { it.id == "move.gets_to_sitting" })
        assertEquals("explorer_9_12m", next.nextPeriod!!.id)
        assertEquals(date("2027-01-21"), next.nextPeriod.startsOn)
        assertTrue(next.activity != null)
    }
}
