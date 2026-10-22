package io.github.lemonjelly76.babymilestones.core.growth

import io.github.lemonjelly76.babymilestones.core.Fixtures
import io.github.lemonjelly76.babymilestones.core.Fixtures.date
import io.github.lemonjelly76.babymilestones.core.tracking.Baby
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GrowthEngineTest {

    private val engine = GrowthEngine(Fixtures.content.growth!!)
    private val girl = Baby("b_01", "Ava", date("2026-01-01"), sex = "female")
    private var n = 0

    private fun weighed(on: String, kg: Double? = null, cm: Double? = null, head: Double? = null) =
        GrowthMeasurement("g_${n++}", "b_01", date(on), kg, cm, head)

    private fun near(expected: Double, actual: Double?, within: Double = 0.02) =
        assertTrue(actual != null && abs(expected - actual) <= within, "expected $expected, got $actual")

    @Test
    fun `the WHO median is the 50th centile and the published SD lines give their z-scores`() {
        val birth = engine.report(girl, listOf(weighed("2026-01-01", kg = 3.2322)), date("2026-01-02"))
        near(0.0, birth.points.single().z, 0.001)
        near(50.0, birth.points.single().centile, 0.1)

        // WHO's boys' length table puts -3 SD at 44.205 cm at birth.
        val boy = girl.copy(sex = "male")
        near(-3.0, engine.report(boy, listOf(weighed("2026-01-01", cm = 44.205)), date("2026-01-02")).points.single().z)
    }

    @Test
    fun `weight beyond 3 SD uses the WHO's restricted method`() {
        // WHO's girls' weight table puts -4 SD at 1.671 kg at birth.
        near(-4.0, engine.report(girl, listOf(weighed("2026-01-01", kg = 1.671)), date("2026-01-02")).points.single().z, 0.05)
    }

    @Test
    fun `ages between the weekly reference points are interpolated`() {
        val ref = Fixtures.content.growth!!
        val m0 = ref.table(Measure.WEIGHT, "female")!!.m[0]
        val m7 = ref.table(Measure.WEIGHT, "female")!!.m[1]
        near(m0 + (m7 - m0) * 3 / 7, engine.valueAt(Measure.WEIGHT, "female", 3.0, 0.0), 0.0001)
    }

    @Test
    fun `babies born before 37 weeks are plotted from their due date until 1 year, or 2 years under 32 weeks`() {
        val early = girl.copy(dateOfBirth = date("2026-01-01"), dueDate = date("2026-02-26")) // 32 weeks
        val points = engine.report(early, listOf(
            weighed("2026-02-01", kg = 2.0), weighed("2026-03-26", kg = 4.0), weighed("2027-02-01", kg = 9.0),
        ), date("2027-02-02")).points
        assertNull(points[0].z, "before the due date there's no centile")
        assertEquals(28, points[1].plotDays)
        assertEquals(396, points[2].plotDays, "after 1 year, chronological age")

        val veryEarly = girl.copy(dateOfBirth = date("2026-01-01"), dueDate = date("2026-03-12")) // 30 weeks
        assertEquals(326, engine.report(veryEarly, listOf(weighed("2027-02-01", kg = 9.0)), date("2027-02-02")).points.single().plotDays)

        val nearTerm = girl.copy(dueDate = date("2026-01-15")) // 38 weeks: no correction
        assertEquals(31, engine.report(nearTerm, listOf(weighed("2026-02-01", kg = 4.0)), date("2026-02-02")).points.single().plotDays)
    }

    @Test
    fun `without the baby's sex there are no centiles`() {
        val report = engine.report(girl.copy(sex = null), listOf(weighed("2026-02-01", kg = 4.0)), date("2026-02-02"))
        assertNull(report.points.single().centile)
        assertTrue(report.needsSex)
    }

    @Test
    fun `a fall of 2 centile spaces from the middle of the chart is worth a chat (NICE NG75)`() {
        val steady = engine.report(girl, listOf(weighed("2026-01-22", kg = 3.9), weighed("2026-04-01", kg = 5.9)), date("2026-04-02"))
        assertEquals(emptyList(), steady.concerns)

        val ref = engine.valueAt(Measure.WEIGHT, "female", 21.0, 0.0)
        val dropped = engine.valueAt(Measure.WEIGHT, "female", 90.0, -1.5)
        val falling = engine.report(girl, listOf(weighed("2026-01-22", kg = ref), weighed("2026-04-01", kg = dropped)), date("2026-04-02"))
        val concern = falling.concerns.single()
        assertTrue("Ava's weight has moved down across 2 of the centile spaces on the chart since 22 Jan 2026" in concern.body, concern.body)
    }

    @Test
    fun `a weight below the 2nd centile is worth a chat whatever came before`() {
        val low = engine.valueAt(Measure.WEIGHT, "female", 90.0, -2.2)
        val report = engine.report(girl, listOf(weighed("2026-04-01", kg = low)), date("2026-04-02"))
        assertTrue("below the lowest centile line but one" in report.concerns.single().body)
    }

    @Test
    fun `centile lines follow the UK-WHO chart`() {
        assertEquals(listOf("0.4", "2", "9", "25", "50", "75", "91", "98", "99.6"), GrowthEngine.CENTILE_LINES.map { it.label })
        val line = engine.line(Measure.HEAD, "male", 0.0, 0..365)
        assertTrue(line.first().second < line.last().second)
    }
}
