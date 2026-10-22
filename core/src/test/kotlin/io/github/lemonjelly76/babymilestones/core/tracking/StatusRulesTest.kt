package io.github.lemonjelly76.babymilestones.core.tracking

import io.github.lemonjelly76.babymilestones.core.Fixtures
import io.github.lemonjelly76.babymilestones.core.Fixtures.achieved
import io.github.lemonjelly76.babymilestones.core.Fixtures.baby
import io.github.lemonjelly76.babymilestones.core.Fixtures.date
import io.github.lemonjelly76.babymilestones.core.Fixtures.notSure
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StatusRulesTest {

    private val tracker = Tracker(Fixtures.content)

    // move.rolls_tummy_to_back: emerging from 4 months, expected by 6 months.
    private fun statusOn(today: String, logs: List<MilestoneLog> = emptyList(), due: String? = null) =
        tracker.snapshot(baby("2026-01-15", due), logs, date(today)).tracked("move.rolls_tummy_to_back")!!

    @Test
    fun `later before the milestone starts emerging`() {
        assertEquals(MilestoneStatus.LATER, statusOn("2026-05-14").status)
    }

    @Test
    fun `coming up from emerging_from until four weeks before expected_by`() {
        assertEquals(MilestoneStatus.COMING_UP, statusOn("2026-05-15").status)
        assertEquals(MilestoneStatus.COMING_UP, statusOn("2026-06-16").status)
    }

    @Test
    fun `due now in the four weeks up to expected_by`() {
        assertEquals(MilestoneStatus.DUE_NOW, statusOn("2026-06-17").status)
        assertEquals(MilestoneStatus.DUE_NOW, statusOn("2026-07-15").status)
    }

    @Test
    fun `not seen yet once past expected_by`() {
        assertEquals(MilestoneStatus.NOT_SEEN_YET, statusOn("2026-07-16").status)
    }

    @Test
    fun `logged milestones are achieved whatever the age`() {
        val t = statusOn("2026-05-01", listOf(achieved("move.rolls_tummy_to_back", "2026-04-30")))
        assertEquals(MilestoneStatus.ACHIEVED, t.status)
        assertEquals(date("2026-04-30"), t.log!!.achievedOn)
    }

    @Test
    fun `not sure is re-asked after two weeks`() {
        val t = statusOn("2026-07-01", listOf(notSure("move.rolls_tummy_to_back", "2026-06-20")))
        assertEquals(MilestoneStatus.NOT_SURE, t.status)
        assertEquals(date("2026-07-04"), t.recheckOn)
        assertEquals(false, t.recheckDue(date("2026-07-03")))
        assertEquals(true, t.recheckDue(date("2026-07-04")))
    }

    @Test
    fun `a skill marked lost is flagged`() {
        val t = statusOn("2026-08-01", listOf(achieved("move.rolls_tummy_to_back", "2026-06-01", lostOn = "2026-07-30")))
        assertEquals(MilestoneStatus.SKILL_LOST, t.status)
    }

    @Test
    fun `premature babies are judged on corrected age`() {
        // Born 2026-01-15, due 2026-03-15: the 6-month checklist date moves to 2026-09-15.
        val t = statusOn("2026-07-20", due = "2026-03-15")
        assertEquals(MilestoneStatus.COMING_UP, t.status)
        assertEquals(date("2026-09-15"), t.expectedBy)
        assertNull(t.recheckOn)
    }

    @Test
    fun `the newest log entry for a milestone wins`() {
        val logs = listOf(
            notSure("move.rolls_tummy_to_back", "2026-06-20"),
            achieved("move.rolls_tummy_to_back", "2026-06-28"),
        )
        assertEquals(MilestoneStatus.ACHIEVED, statusOn("2026-07-01", logs).status)
    }

    @Test
    fun `snapshot reports the current period and counts`() {
        val snapshot = tracker.snapshot(baby("2026-04-21"), emptyList(), date("2026-10-21"))
        assertEquals("mover_6_9m", snapshot.period.id)
        assertEquals(Fixtures.content.milestones.size, snapshot.counts().values.sum())
    }
}
