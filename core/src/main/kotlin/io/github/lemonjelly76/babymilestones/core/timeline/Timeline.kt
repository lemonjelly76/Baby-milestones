package io.github.lemonjelly76.babymilestones.core.timeline

import io.github.lemonjelly76.babymilestones.core.json.Date
import io.github.lemonjelly76.babymilestones.core.records.FirstWord
import io.github.lemonjelly76.babymilestones.core.records.ToothRecord
import io.github.lemonjelly76.babymilestones.core.schedule.BabySchedule
import io.github.lemonjelly76.babymilestones.core.tracking.BabySnapshot
import io.github.lemonjelly76.babymilestones.core.tracking.MilestoneStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

/** First tooth, first word, first food: free-text moments outside the milestone model. */
@Serializable
data class Moment(
    val id: String,
    val babyId: String,
    val title: String,
    val date: Date,
    val note: String? = null,
    val photoRef: String? = null,
)

@Serializable
enum class TimelineKind {
    @SerialName("birth") BIRTH,
    @SerialName("milestone") MILESTONE,
    @SerialName("moment") MOMENT,
    @SerialName("appointment") APPOINTMENT,
    @SerialName("vaccination") VACCINATION,
    @SerialName("word") WORD,
    @SerialName("tooth") TOOTH,
}

@Serializable
data class TimelineEntry(
    val kind: TimelineKind,
    val date: Date,
    val title: String,
    val refId: String,
    val approximate: Boolean = false,
    val note: String? = null,
    val photoRef: String? = null,
    /** Happened some time before [date] (ticked on the catch-up list); listed just after the birth. */
    val before: Boolean = false,
)

object Timeline {

    /** Everything that has happened, newest first, optionally limited to [from]..[to]. */
    fun build(
        snapshot: BabySnapshot,
        moments: List<Moment>,
        schedule: BabySchedule?,
        from: LocalDate? = null,
        to: LocalDate? = null,
        words: List<FirstWord> = emptyList(),
        teeth: List<ToothRecord> = emptyList(),
        toothName: (String) -> String = { it },
    ): List<TimelineEntry> {
        val baby = snapshot.baby
        val entries = buildList {
            add(TimelineEntry(TimelineKind.BIRTH, baby.dateOfBirth, "${baby.name} was born", baby.id))
            snapshot.withStatus(MilestoneStatus.ACHIEVED, MilestoneStatus.SKILL_LOST).forEach { t ->
                val log = t.log!!
                add(
                    TimelineEntry(
                        TimelineKind.MILESTONE, log.achievedOn ?: log.markedOn, t.milestone.title, t.id,
                        log.approximate, log.note, log.photoRef, before = log.isUndatedCatchUp,
                    ),
                )
            }
            moments.forEach { add(TimelineEntry(TimelineKind.MOMENT, it.date, it.title, it.id, note = it.note, photoRef = it.photoRef)) }
            // Added newest first, so on the same day the first word or tooth still sits below the later ones.
            val firstWord = words.minByOrNull { it.saidOn }
            words.sortedByDescending { it.saidOn }.forEach { w ->
                add(TimelineEntry(TimelineKind.WORD, w.saidOn,
                    if (w == firstWord) "First word: \u201c${w.word}\u201d" else "Said \u201c${w.word}\u201d", w.id, note = w.note))
            }
            val firstTooth = teeth.minByOrNull { it.cameOn }
            teeth.sortedByDescending { it.cameOn }.forEach { t ->
                add(TimelineEntry(TimelineKind.TOOTH, t.cameOn, (if (t == firstTooth) "First tooth: " else "New tooth: ") +
                    toothName(t.toothId).replaceFirstChar { it.lowercase() }, t.toothId))
            }
            schedule?.appointments?.forEach { a ->
                a.record?.doneOn?.let { add(TimelineEntry(TimelineKind.APPOINTMENT, it, a.rule.title, a.rule.id, note = a.record.note)) }
            }
            schedule?.doses?.forEach { d ->
                d.record?.let { add(TimelineEntry(TimelineKind.VACCINATION, it.givenOn, "${d.rule.vaccine} (${d.rule.dose})", d.rule.code)) }
            }
        }
        return entries
            .filter { (from == null || !it.date.isBefore(from)) && (to == null || !it.date.isAfter(to)) }
            .sortedWith(
                compareBy<TimelineEntry> { it.kind == TimelineKind.BIRTH }
                    .thenBy { it.before }
                    .thenByDescending { it.date }
                    .thenBy { it.kind },
            )
    }
}
