package io.github.lemonjelly76.babymilestones.core.summary

import io.github.lemonjelly76.babymilestones.core.Fixtures
import io.github.lemonjelly76.babymilestones.core.Fixtures.achieved
import io.github.lemonjelly76.babymilestones.core.Fixtures.baby
import io.github.lemonjelly76.babymilestones.core.Fixtures.date
import io.github.lemonjelly76.babymilestones.core.concerns.ConcernEngine
import io.github.lemonjelly76.babymilestones.core.content.Domain
import io.github.lemonjelly76.babymilestones.core.food.FoodTasting
import io.github.lemonjelly76.babymilestones.core.food.FoodTracker
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

    @Test
    fun `summary includes foods tried, allergens introduced and possible reactions`() {
        val content = Fixtures.content
        val snapshot = Tracker(content).snapshot(baby("2026-03-01"), emptyList(), date("2026-10-22"))
        val egg = content.food("food.egg")!!
        val broccoli = content.food("food.broccoli")!!
        val tastings = listOf(
            FoodTasting("t1", "b_01", egg.id, egg.name, egg.group, date("2026-10-01"), reaction = "blotchy cheeks"),
            FoodTasting("t2", "b_01", broccoli.id, broccoli.name, broccoli.group, date("2026-10-02")),
        )
        val foods = FoodTracker(content).report(tastings, date("2026-10-22"), 7)
        val summary = ReviewSummary.build(snapshot, emptyList(), emptyList(), foods)

        assertEquals(2, summary.foods!!.tried)
        assertEquals(listOf("Egg"), summary.foods!!.allergensIntroduced)
        val text = summary.toPlainText()
        assertTrue("Foods tried: 2" in text)
        assertTrue("Possible reactions noted" in text)
        assertTrue("blotchy cheeks" in text)
    }
}
