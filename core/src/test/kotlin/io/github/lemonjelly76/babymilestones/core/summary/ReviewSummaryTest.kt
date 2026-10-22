package io.github.lemonjelly76.babymilestones.core.summary

import io.github.lemonjelly76.babymilestones.core.Fixtures
import io.github.lemonjelly76.babymilestones.core.Fixtures.achieved
import io.github.lemonjelly76.babymilestones.core.Fixtures.baby
import io.github.lemonjelly76.babymilestones.core.Fixtures.date
import io.github.lemonjelly76.babymilestones.core.concerns.ConcernEngine
import io.github.lemonjelly76.babymilestones.core.content.Domain
import io.github.lemonjelly76.babymilestones.core.tracking.Tracker
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReviewSummaryTest {

    @Test
    fun `summary lists achievements by domain, open items and parents' questions`() {
        val content = Fixtures.content
        val snapshot = Tracker(content).snapshot(
            baby("2026-01-01"),
            content.milestones.filter { it.expectedBy <= 6 && it.id != "lang.squeals" }.map { achieved(it.id, "2026-06-01") },
            date("2026-07-10"),
        )
        val notes = listOf(
            ReviewNote("n1", "b_01", "Is her sleep normal?", createdAt = Instant.parse("2026-07-01T10:00:00Z")),
            ReviewNote("n2", "b_01", "Old question", createdAt = Instant.parse("2026-06-01T10:00:00Z"), resolved = true),
        )
        val summary = ReviewSummary.build(snapshot, ConcernEngine.concerns(snapshot, content.concerns), notes)

        assertEquals(listOf("Makes squealing noises"), summary.notSeenYet.map { it.title })
        assertEquals(listOf("Is her sleep normal?"), summary.questions)
        assertTrue(summary.achieved.getValue(Domain.MOVEMENT).isNotEmpty())
        val text = summary.toPlainText()
        assertTrue("Development summary for Ava" in text)
        assertTrue("• Makes squealing noises" in text)
        assertTrue("not a screening result" in text)
    }
}
