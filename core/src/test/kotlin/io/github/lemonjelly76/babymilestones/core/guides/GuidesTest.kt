package io.github.lemonjelly76.babymilestones.core.guides

import io.github.lemonjelly76.babymilestones.core.Fixtures
import io.github.lemonjelly76.babymilestones.core.Fixtures.date
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GuidesTest {

    private val content = Fixtures.content

    @Test
    fun `weaning guides show at 6 months and potty training is coming up at 15 months`() {
        val at6 = Guides.forAge(content.guides, 6)
        assertTrue(at6.now.any { it.id == "guide.baby_led_weaning" })
        assertTrue(at6.now.any { it.id == "guide.foods_to_avoid" })
        assertTrue(at6.now.none { it.id == "guide.potty_training" })

        val at15 = Guides.forAge(content.guides, 15)
        assertTrue(at15.comingUp.any { it.id == "guide.potty_training" })
        assertTrue(at15.now.none { it in at15.comingUp })
    }

    @Test
    fun `readiness for solids is coming up before 6 months`() {
        assertTrue(Guides.forAge(content.guides, 3).comingUp.any { it.id == "guide.ready_for_solids" })
    }

    @Test
    fun `checklist progress counts only this guide's ticked items`() {
        val guide = content.guide("guide.ready_for_solids")!!
        val ticks = listOf(
            ChecklistTick("b_01", guide.checklist.first().id, date("2026-10-01")),
            ChecklistTick("b_01", "check.someone_elses", date("2026-10-01")),
        )
        assertEquals(ChecklistProgress(1, guide.checklist.size), Guides.progress(guide, ticks))
    }

    @Test
    fun `reception starts the September after the 4th birthday, with England's national dates`() {
        val april = SchoolStart.england(date("2026-04-21"))
        assertEquals(date("2030-09-01"), april.startsOn)
        assertEquals(date("2030-01-15"), april.applyBy)
        assertEquals(date("2030-04-16"), april.offersOn)

        assertEquals(date("2031-09-01"), SchoolStart.england(date("2026-09-01")).startsOn)
        assertEquals(date("2030-09-01"), SchoolStart.england(date("2026-08-31")).startsOn)
    }

    @Test
    fun `the application window is the autumn and winter before the start`() {
        val dates = SchoolStart.england(date("2026-04-21"))
        assertTrue(dates.applicationOpen(date("2029-11-20")))
        assertTrue(dates.applicationOpen(date("2030-01-15")))
        assertEquals(false, dates.applicationOpen(date("2030-01-16")))
        assertEquals(false, dates.applicationOpen(date("2029-08-31")))
    }
}
