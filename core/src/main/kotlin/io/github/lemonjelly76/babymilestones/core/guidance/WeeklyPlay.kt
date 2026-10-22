package io.github.lemonjelly76.babymilestones.core.guidance

import io.github.lemonjelly76.babymilestones.core.tracking.BabySnapshot
import io.github.lemonjelly76.babymilestones.core.tracking.MilestoneStatus
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

@Serializable
data class PlayIdea(val text: String, val milestoneId: String? = null, val milestoneTitle: String? = null)

/**
 * A few play ideas for the week, from the activities of milestones the child is working towards (due now, not seen yet,
 * not sure, coming up) and then the period guide. The selection rotates each Monday so the ideas don't go stale.
 */
object WeeklyPlay {
    const val IDEAS_PER_WEEK = 4
    private val WORKING_ON = listOf(
        MilestoneStatus.DUE_NOW, MilestoneStatus.NOT_SEEN_YET, MilestoneStatus.NOT_SURE, MilestoneStatus.COMING_UP,
    )

    fun ideas(snapshot: BabySnapshot): List<PlayIdea> {
        val fromMilestones = WORKING_ON.flatMap { status ->
            snapshot.withStatus(status).flatMap { t -> t.milestone.activities.map { PlayIdea(it, t.id, t.milestone.title) } }
        }
        val pool = (fromMilestones + snapshot.period.play.map { PlayIdea(it) }).distinctBy { it.text }
        if (pool.size <= IDEAS_PER_WEEK) return pool
        val week = ChronoUnit.WEEKS.between(EPOCH_MONDAY, snapshot.today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
        val start = ((week * IDEAS_PER_WEEK) % pool.size).toInt()
        return (0 until IDEAS_PER_WEEK).map { pool[(start + it) % pool.size] }
    }

    private val EPOCH_MONDAY: LocalDate = LocalDate.of(2024, 1, 1)
}
