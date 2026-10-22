package io.github.lemonjelly76.babymilestones.core.memories

import io.github.lemonjelly76.babymilestones.core.age.AgeSpan
import io.github.lemonjelly76.babymilestones.core.timeline.TimelineEntry
import io.github.lemonjelly76.babymilestones.core.timeline.TimelineKind
import io.github.lemonjelly76.babymilestones.core.tracking.Baby
import kotlinx.serialization.Serializable

@Serializable
data class MemoryChapter(val title: String, val months: Int, val entries: List<TimelineEntry>)

/** A keepsake: the timeline's milestones, moments, words and teeth, oldest first, in chapters by month of age. */
object MemoryBook {
    private val KEPT = setOf(TimelineKind.BIRTH, TimelineKind.MILESTONE, TimelineKind.MOMENT, TimelineKind.WORD, TimelineKind.TOOTH)

    fun chapters(baby: Baby, timeline: List<TimelineEntry>): List<MemoryChapter> =
        timeline.filter { it.kind in KEPT }
            .sortedWith(compareBy<TimelineEntry> { it.kind != TimelineKind.BIRTH }.thenBy { it.date })
            .groupBy { if (it.kind == TimelineKind.BIRTH || it.before) 0 else AgeSpan.between(baby.dateOfBirth, it.date).months.coerceAtLeast(0) }
            .toSortedMap()
            .map { (months, entries) -> MemoryChapter(title(months), months, entries) }

    private fun title(months: Int) = when {
        months == 0 -> "The beginning"
        months < 24 -> "$months ${if (months == 1) "month" else "months"}"
        months % 12 == 0 -> "${months / 12} years"
        else -> "${months / 12} years ${months % 12} ${if (months % 12 == 1) "month" else "months"}"
    }
}
