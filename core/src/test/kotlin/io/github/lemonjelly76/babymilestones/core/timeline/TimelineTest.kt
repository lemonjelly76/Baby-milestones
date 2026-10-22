package io.github.lemonjelly76.babymilestones.core.timeline

import io.github.lemonjelly76.babymilestones.core.Fixtures
import io.github.lemonjelly76.babymilestones.core.Fixtures.achieved
import io.github.lemonjelly76.babymilestones.core.Fixtures.baby
import io.github.lemonjelly76.babymilestones.core.Fixtures.date
import io.github.lemonjelly76.babymilestones.core.records.FirstWord
import io.github.lemonjelly76.babymilestones.core.records.ToothRecord
import io.github.lemonjelly76.babymilestones.core.schedule.DoseRecord
import io.github.lemonjelly76.babymilestones.core.schedule.ScheduleEngine
import io.github.lemonjelly76.babymilestones.core.tracking.LogSource
import io.github.lemonjelly76.babymilestones.core.tracking.Tracker
import org.junit.Test
import kotlin.test.assertEquals

class TimelineTest {

    @Test
    fun `timeline merges birth, milestones, moments and vaccinations newest first`() {
        val content = Fixtures.content
        val b = baby("2026-05-01")
        val today = date("2026-10-21")
        val snapshot = Tracker(content).snapshot(b, listOf(achieved("social.smiles_back", "2026-06-15")), today)
        val schedule = ScheduleEngine.forBaby(
            b.dateOfBirth, content.schedule("england"), emptyList(),
            listOf(DoseRecord("d1", b.id, "menb_1", date("2026-06-26"))), today,
        )
        val moments = listOf(Moment("m1", b.id, "First tooth", date("2026-10-01")))

        val timeline = Timeline.build(snapshot, moments, schedule)

        assertEquals(
            listOf(TimelineKind.MOMENT, TimelineKind.VACCINATION, TimelineKind.MILESTONE, TimelineKind.BIRTH),
            timeline.map { it.kind },
        )
        assertEquals(1, Timeline.build(snapshot, moments, schedule, from = date("2026-09-01")).size)
    }

    @Test
    fun `catch-up ticks without a date sit just after the birth`() {
        val content = Fixtures.content
        val b = baby("2026-05-01")
        val catchUp = achieved("social.smiles_back", "2026-10-20").copy(
            source = LogSource.BACKFILL, approximate = true,
        )
        val snapshot = Tracker(content).snapshot(b, listOf(catchUp, achieved("social.laughs", "2026-09-01")), date("2026-10-21"))
        val timeline = Timeline.build(snapshot, emptyList(), null)
        assertEquals(listOf("social.laughs", "social.smiles_back", b.id), timeline.map { it.refId })
        assertEquals(true, timeline[1].before)
    }

    @Test
    fun `first words and teeth appear on the timeline, the first of each called out`() {
        val snapshot = Tracker(Fixtures.content).snapshot(Fixtures.baby("2026-01-01"), emptyList(), Fixtures.date("2026-10-22"))
        val entries = Timeline.build(
            snapshot, emptyList(), null,
            words = listOf(FirstWord("w2", "b_01", "ball", Fixtures.date("2026-10-10")), FirstWord("w1", "b_01", "dada", Fixtures.date("2026-09-01"))),
            teeth = listOf(ToothRecord("b_01", "71", Fixtures.date("2026-08-01"))),
            toothName = { Fixtures.content.tooth(it)!!.name },
        )
        assertEquals(listOf("Said \u201cball\u201d", "First word: \u201cdada\u201d", "First tooth: lower left central incisor", "Ava was born"),
            entries.map { it.title })
    }
}
