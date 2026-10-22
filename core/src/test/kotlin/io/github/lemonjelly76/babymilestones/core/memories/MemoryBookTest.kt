package io.github.lemonjelly76.babymilestones.core.memories

import io.github.lemonjelly76.babymilestones.core.Fixtures
import io.github.lemonjelly76.babymilestones.core.Fixtures.achieved
import io.github.lemonjelly76.babymilestones.core.Fixtures.date
import io.github.lemonjelly76.babymilestones.core.guidance.WeeklyPlay
import io.github.lemonjelly76.babymilestones.core.records.FirstWord
import io.github.lemonjelly76.babymilestones.core.timeline.Moment
import io.github.lemonjelly76.babymilestones.core.timeline.Timeline
import io.github.lemonjelly76.babymilestones.core.tracking.Tracker
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MemoryBookTest {

    private val baby = Fixtures.baby("2026-01-01")

    @Test
    fun `memories are grouped by month of age, oldest first, without appointments`() {
        val snapshot = Tracker(Fixtures.content).snapshot(baby, listOf(achieved("social.laughs", "2026-04-10")), date("2026-10-22"))
        val timeline = Timeline.build(
            snapshot,
            listOf(Moment("m1", "b_01", "First swim", date("2026-06-15")), Moment("m2", "b_01", "Met Grandma", date("2026-01-05"))),
            null,
            words = listOf(FirstWord("w1", "b_01", "dada", date("2026-09-20"))),
        )
        val chapters = MemoryBook.chapters(baby, timeline)
        assertEquals(listOf("The beginning", "3 months", "5 months", "8 months"), chapters.map { it.title })
        assertEquals(listOf("Ava was born", "Met Grandma"), chapters.first().entries.map { it.title })
    }

    @Test
    fun `weekly play ideas come from what the child is working on and change each week`() {
        val tracker = Tracker(Fixtures.content)
        val monday = WeeklyPlay.ideas(tracker.snapshot(baby, emptyList(), date("2026-10-19")))
        val sunday = WeeklyPlay.ideas(tracker.snapshot(baby, emptyList(), date("2026-10-25")))
        val nextWeek = WeeklyPlay.ideas(tracker.snapshot(baby, emptyList(), date("2026-10-26")))
        assertEquals(WeeklyPlay.IDEAS_PER_WEEK, monday.size)
        assertEquals(monday, sunday)
        assertNotEquals(monday, nextWeek)
        assertTrue(monday.any { it.milestoneId != null })
    }
}
