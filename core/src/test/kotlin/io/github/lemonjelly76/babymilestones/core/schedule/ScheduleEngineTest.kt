package io.github.lemonjelly76.babymilestones.core.schedule

import io.github.lemonjelly76.babymilestones.core.Fixtures
import io.github.lemonjelly76.babymilestones.core.Fixtures.date
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScheduleEngineTest {

    private val england = Fixtures.content.schedule("england")

    private fun codes(dob: String) =
        ScheduleEngine.forBaby(date(dob), england, emptyList(), emptyList(), date(dob)).doses.map { it.rule.code }

    @Test
    fun `babies born from 2025 get MMRV at 1 year and 18 months`() {
        val doses = codes("2026-02-01")
        assertTrue("mmrv_1" in doses && "mmrv_2" in doses && "6in1_4" in doses)
        assertFalse("mmr_1" in doses || "hibmenc" in doses || "mmrv_preschool" in doses)
    }

    @Test
    fun `babies born in the second half of 2024 get the 18 month appointment but MMR at 1 year`() {
        val doses = codes("2024-09-01")
        assertTrue("mmr_1" in doses && "hibmenc" in doses && "6in1_4" in doses && "mmrv_18m" in doses)
        assertFalse("mmrv_preschool" in doses)
    }

    @Test
    fun `children born before July 2024 have no 18 month appointment`() {
        val schedule = ScheduleEngine.forBaby(date("2023-05-01"), england, emptyList(), emptyList(), date("2026-10-21"))
        assertFalse(schedule.appointments.any { it.rule.id == "vaccinations_18_months" })
        assertTrue(schedule.doses.any { it.rule.code == "mmrv_preschool" })
    }

    @Test
    fun `the 12 and 16 week doses follow the July 2025 change`() {
        val new = ScheduleEngine.forBaby(date("2026-02-01"), england, emptyList(), emptyList(), date("2026-02-01"))
        assertEquals("vaccinations_12_weeks", new.doses.single { it.rule.code == "menb_2" }.rule.appointment)
        assertEquals("vaccinations_16_weeks", new.doses.single { it.rule.code == "pcv_1" }.rule.appointment)
        val old = ScheduleEngine.forBaby(date("2025-01-01"), england, emptyList(), emptyList(), date("2025-01-01"))
        assertEquals("vaccinations_16_weeks", old.doses.single { it.rule.code == "menb_2" }.rule.appointment)
    }

    @Test
    fun `appointment status follows the window and what was recorded`() {
        val dob = date("2026-08-01")
        fun status(today: String, records: List<AppointmentRecord> = emptyList()) =
            ScheduleEngine.forBaby(dob, england, records, emptyList(), date(today))
                .appointments.single { it.rule.id == "review_6_8_weeks" }.status
        assertEquals(AppointmentStatus.UPCOMING, status("2026-09-01"))
        assertEquals(AppointmentStatus.OPEN, status("2026-09-15"))
        assertEquals(AppointmentStatus.PAST_WINDOW, status("2026-10-01"))
        assertEquals(AppointmentStatus.BOOKED,
            status("2026-10-01", listOf(AppointmentRecord("a1", "b_01", "review_6_8_weeks", bookedOn = date("2026-10-05")))))
        assertEquals(AppointmentStatus.DONE,
            status("2026-10-01", listOf(AppointmentRecord("a1", "b_01", "review_6_8_weeks", doneOn = date("2026-09-20")))))
    }

    @Test
    fun `doses due then overdue after four weeks, unless given`() {
        val dob = date("2026-08-01")
        fun status(today: String, given: Boolean = false) = ScheduleEngine.forBaby(
            dob, england, emptyList(),
            if (given) listOf(DoseRecord("d1", "b_01", "menb_1", date("2026-09-26"))) else emptyList(), date(today),
        ).doses.single { it.rule.code == "menb_1" }.status
        assertEquals(DoseStatus.UPCOMING, status("2026-09-25"))
        assertEquals(DoseStatus.DUE, status("2026-09-26"))
        assertEquals(DoseStatus.OVERDUE, status("2026-10-25"))
        assertEquals(DoseStatus.GIVEN, status("2026-10-25", given = true))
    }

    @Test
    fun `other UK nations fall back to the shared schedule`() {
        assertEquals("england", Fixtures.content.schedule("scotland").region)
    }
}
