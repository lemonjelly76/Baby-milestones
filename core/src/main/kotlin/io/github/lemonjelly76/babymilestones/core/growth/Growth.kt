package io.github.lemonjelly76.babymilestones.core.growth

import io.github.lemonjelly76.babymilestones.core.content.SignpostCopy
import io.github.lemonjelly76.babymilestones.core.json.Date
import io.github.lemonjelly76.babymilestones.core.tracking.Baby
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

@Serializable
enum class Measure(val label: String, val unit: String) {
    @SerialName("weight") WEIGHT("Weight", "kg"),
    @SerialName("length") LENGTH("Length or height", "cm"),
    @SerialName("head") HEAD("Head circumference", "cm"),
}

/** One WHO table: L, M and S every [GrowthReference.stepDays] days from birth. */
@Serializable
data class LmsTable(val measure: Measure, val sex: String, val l: List<Double>, val m: List<Double>, val s: List<Double>)

@Serializable
data class GrowthReference(
    val standard: String,
    val stepDays: Int,
    val tables: List<LmsTable>,
    val fallConcern: SignpostCopy,
    val lowConcern: SignpostCopy,
    val sources: List<String>,
) {
    fun table(measure: Measure, sex: String): LmsTable? = tables.firstOrNull { it.measure == measure && it.sex == sex }
}

/** A weighing or measuring, by a health visitor, GP or at home. Any of the three values may be missing. */
@Serializable
data class GrowthMeasurement(
    val id: String,
    val babyId: String,
    val measuredOn: Date,
    val weightKg: Double? = null,
    val lengthCm: Double? = null,
    val headCm: Double? = null,
    val measuredBy: String? = null,
    val note: String? = null,
) {
    fun value(measure: Measure): Double? = when (measure) {
        Measure.WEIGHT -> weightKg
        Measure.LENGTH -> lengthCm
        Measure.HEAD -> headCm
    }
}

/** A measurement placed on the chart: [plotDays] is the age it's plotted at, corrected for gestation where the charts do. */
@Serializable
data class GrowthPoint(
    val measurementId: String,
    val measure: Measure,
    val date: Date,
    val value: Double,
    val plotDays: Int,
    val z: Double? = null,
    val centile: Double? = null,
)

@Serializable
data class GrowthReport(
    val points: List<GrowthPoint>,
    val concerns: List<SignpostCopy>,
    /** Measurements exist but the baby's sex isn't recorded, so there are no centiles. */
    val needsSex: Boolean = false,
    /** Plotting from the due date because the baby was born before 37 weeks. */
    val correctedForGestation: Boolean = false,
) {
    fun latest(measure: Measure): GrowthPoint? = points.filter { it.measure == measure }.maxByOrNull { it.date }
}

@Serializable
data class CentileLine(val label: String, val z: Double)

/**
 * Centiles from the WHO Child Growth Standards, used by the UK-WHO charts in the red book:
 * - z = ((value / M)^L - 1) / (L * S), with the WHO's restricted method for weight beyond ±3 SD;
 * - born before 37 weeks: plotted from the due date until 1 year (32 to 36 weeks) or 2 years (under 32 weeks), as RCPCH
 *   advises; nothing is plotted before the due date;
 * - signposting follows NICE NG75's thresholds for a fall across centile spaces, or a weight below the 2nd centile.
 */
class GrowthEngine(private val reference: GrowthReference) {

    companion object {
        /** The UK-WHO chart's nine lines, two thirds of a standard deviation apart. */
        val CENTILE_LINES = listOf(
            CentileLine("0.4", -8.0 / 3), CentileLine("2", -2.0), CentileLine("9", -4.0 / 3), CentileLine("25", -2.0 / 3),
            CentileLine("50", 0.0), CentileLine("75", 2.0 / 3), CentileLine("91", 4.0 / 3), CentileLine("98", 2.0),
            CentileLine("99.6", 8.0 / 3),
        )
        const val CENTILE_SPACE = 2.0 / 3
        private const val TERM_DAYS = 280L
        private const val CORRECT_BELOW_DAYS = 37 * 7
        private const val LONG_CORRECTION_BELOW_DAYS = 32 * 7
        private val DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)
    }

    fun report(baby: Baby, measurements: List<GrowthMeasurement>, today: LocalDate): GrowthReport {
        val sex = baby.sex?.takeIf { it == "female" || it == "male" }
        val points = measurements.sortedBy { it.measuredOn }.flatMap { m ->
            Measure.entries.mapNotNull { measure -> m.value(measure)?.let { point(baby, sex, m, measure, it) } }
        }
        return GrowthReport(
            points = points,
            concerns = concerns(baby, points),
            needsSex = sex == null && points.isNotEmpty(),
            correctedForGestation = correctionDays(baby, today) > 0,
        )
    }

    /** The value at a z-score for a measure, sex and age in days: used to draw centile lines. */
    fun valueAt(measure: Measure, sex: String, days: Double, z: Double): Double? {
        val (l, m, s) = lms(measure, sex, days) ?: return null
        return if (abs(l) < 1e-9) m * exp(s * z) else m * (1 + l * s * z).pow(1 / l)
    }

    fun line(measure: Measure, sex: String, z: Double, days: IntProgression): List<Pair<Int, Double>> =
        days.mapNotNull { d -> valueAt(measure, sex, d.toDouble(), z)?.let { d to it } }

    private fun point(baby: Baby, sex: String?, m: GrowthMeasurement, measure: Measure, value: Double): GrowthPoint {
        val plotDays = plotDays(baby, m.measuredOn)
        val z = if (sex != null && plotDays >= 0) zScore(measure, sex, plotDays.toDouble(), value) else null
        return GrowthPoint(m.id, measure, m.measuredOn, value, plotDays, z, z?.let { normalCdf(it) * 100 })
    }

    /** How many days early to correct by at [date]: the days before the due date, while the charts still correct. */
    private fun correctionDays(baby: Baby, date: LocalDate): Long {
        val due = baby.dueDate ?: return 0
        val early = ChronoUnit.DAYS.between(baby.dateOfBirth, due)
        val gestation = TERM_DAYS - early
        if (gestation >= CORRECT_BELOW_DAYS) return 0
        val until = baby.dateOfBirth.plusYears(if (gestation < LONG_CORRECTION_BELOW_DAYS) 2 else 1)
        return if (date.isBefore(until)) early else 0
    }

    private fun plotDays(baby: Baby, date: LocalDate): Int =
        (ChronoUnit.DAYS.between(baby.dateOfBirth, date) - correctionDays(baby, date)).toInt()

    private fun zScore(measure: Measure, sex: String, days: Double, value: Double): Double? {
        val (l, m, s) = lms(measure, sex, days) ?: return null
        fun at(z: Double) = if (abs(l) < 1e-9) m * exp(s * z) else m * (1 + l * s * z).pow(1 / l)
        val z = if (abs(l) < 1e-9) ln(value / m) / s else ((value / m).pow(l) - 1) / (l * s)
        if (measure != Measure.WEIGHT || abs(z) <= 3) return z
        // WHO restricted method: beyond ±3 SD, distances are measured in units of the 2-3 SD gap.
        return if (z > 3) 3 + (value - at(3.0)) / (at(3.0) - at(2.0)) else -3 + (value - at(-3.0)) / (at(-2.0) - at(-3.0))
    }

    private fun lms(measure: Measure, sex: String, days: Double): Triple<Double, Double, Double>? {
        val table = reference.table(measure, sex) ?: return null
        val position = days / reference.stepDays
        if (position < 0 || position > table.m.size - 1) return null
        val i = floor(position).toInt().coerceAtMost(table.m.size - 2)
        val f = position - i
        fun lerp(values: List<Double>) = values[i] + (values[i + 1] - values[i]) * f
        return Triple(lerp(table.l), lerp(table.m), lerp(table.s))
    }

    private fun concerns(baby: Baby, points: List<GrowthPoint>): List<SignpostCopy> {
        val weights = points.filter { it.measure == Measure.WEIGHT && it.z != null }
        val latest = weights.lastOrNull() ?: return emptyList()
        fun fill(copy: SignpostCopy, vararg values: Pair<String, String>) =
            copy.copy(body = values.fold(copy.body.replace("{name}", baby.name)) { text, (k, v) -> text.replace("{$k}", v) })

        if (latest.z!! < -2.0537) return listOf(fill(reference.lowConcern))
        val first = weights.first()
        if (first == latest) return emptyList()
        val spaces = floor((first.z!! - latest.z) / CENTILE_SPACE + 1e-9).toInt()
        val threshold = when {
            first.z < -4.0 / 3 -> 1
            first.z > 4.0 / 3 -> 3
            else -> 2
        }
        return if (spaces >= threshold) listOf(fill(reference.fallConcern, "spaces" to "$spaces", "since" to first.date.format(DAY)))
        else emptyList()
    }

    /** The standard normal distribution function (Abramowitz and Stegun 26.2.17, error below 7.5e-8). */
    private fun normalCdf(z: Double): Double {
        val t = 1 / (1 + 0.2316419 * abs(z))
        val poly = t * (0.319381530 + t * (-0.356563782 + t * (1.781477937 + t * (-1.821255978 + t * 1.330274429))))
        val upper = exp(-z * z / 2) / sqrt(2 * Math.PI) * poly
        return if (z >= 0) 1 - upper else upper
    }
}
