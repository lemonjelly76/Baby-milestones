package io.github.lemonjelly76.babymilestones.core.concerns

import io.github.lemonjelly76.babymilestones.core.Fixtures
import io.github.lemonjelly76.babymilestones.core.Fixtures.achieved
import io.github.lemonjelly76.babymilestones.core.Fixtures.baby
import io.github.lemonjelly76.babymilestones.core.Fixtures.date
import io.github.lemonjelly76.babymilestones.core.content.Domain
import io.github.lemonjelly76.babymilestones.core.content.Urgency
import io.github.lemonjelly76.babymilestones.core.tracking.MilestoneLog
import io.github.lemonjelly76.babymilestones.core.tracking.Tracker
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConcernEngineTest {

    private val content = Fixtures.content
    private val tracker = Tracker(content)

    /** Everything expected by [months] logged, except [skip]. */
    private fun logsUpTo(months: Int, skip: Set<String>, on: String): List<MilestoneLog> =
        content.milestones.filter { it.expectedBy <= months && it.id !in skip }.map { achieved(it.id, on) }

    private fun concerns(today: String, logs: List<MilestoneLog>) =
        ConcernEngine.concerns(tracker.snapshot(baby("2026-01-01"), logs, date(today)), content.concerns)

    @Test
    fun `one milestone not seen gets gentle copy`() {
        // 6-month checklist date is 2026-07-01; a week later one item is open.
        val result = concerns("2026-07-08", logsUpTo(6, setOf("move.rolls_tummy_to_back"), "2026-06-01"))
        assertEquals(1, result.size)
        assertEquals(ConcernKind.NOT_SEEN_ONE, result[0].kind)
        assertEquals(Urgency.INFO, result[0].urgency)
        assertTrue("roll" in result[0].body)
    }

    @Test
    fun `two open in one domain is worth a chat`() {
        val skip = setOf("move.rolls_tummy_to_back", "move.pushes_up_straight_arms")
        val result = concerns("2026-07-08", logsUpTo(6, skip, "2026-06-01"))
        assertEquals(1, result.size)
        assertEquals(ConcernKind.WORTH_A_CHAT, result[0].kind)
        assertEquals(Domain.MOVEMENT, result[0].domain)
        assertEquals(skip, result[0].milestoneIds.toSet())
    }

    @Test
    fun `one open for more than four weeks past expected is worth a chat`() {
        val result = concerns("2026-07-30", logsUpTo(6, setOf("move.rolls_tummy_to_back"), "2026-06-01"))
        assertEquals(ConcernKind.WORTH_A_CHAT, result.single().kind)
    }

    @Test
    fun `losing a skill raises a prompt concern straight away`() {
        val logs = logsUpTo(4, emptySet(), "2026-04-01") +
            achieved("social.laughs", "2026-04-15", lostOn = "2026-05-01")
        val result = concerns("2026-05-02", logs)
        assertEquals(ConcernKind.SKILL_LOST, result.first().kind)
        assertEquals(Urgency.PROMPT, result.first().urgency)
    }

    @Test
    fun `nothing is raised when everything due is logged`() {
        assertEquals(emptyList(), concerns("2026-07-08", logsUpTo(6, emptySet(), "2026-06-01")))
    }

    @Test
    fun `milestones due before tracking began are catch-up, not concerns, until the parent is unsure`() {
        val baby = baby("2026-01-01").copy(trackingSince = date("2026-08-01"))
        fun concernsWith(logs: List<MilestoneLog>) =
            ConcernEngine.concerns(tracker.snapshot(baby, logs, date("2026-08-10")), content.concerns)
        assertEquals(emptyList(), concernsWith(emptyList()))
        val unsure = concernsWith(listOf(Fixtures.notSure("move.rolls_tummy_to_back", "2026-08-05")))
        assertEquals(listOf("move.rolls_tummy_to_back"), unsure.flatMap { it.milestoneIds })
    }
}
