package io.github.lemonjelly76.babymilestones.core.summary

import io.github.lemonjelly76.babymilestones.core.concerns.Concern
import io.github.lemonjelly76.babymilestones.core.content.Domain
import io.github.lemonjelly76.babymilestones.core.json.Date
import io.github.lemonjelly76.babymilestones.core.json.Timestamp
import io.github.lemonjelly76.babymilestones.core.tracking.BabySnapshot
import io.github.lemonjelly76.babymilestones.core.tracking.MilestoneStatus
import kotlinx.serialization.Serializable
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
data class ReviewNote(
    val id: String,
    val babyId: String,
    val text: String,
    val createdBy: String? = null,
    val createdAt: Timestamp,
    val resolved: Boolean = false,
)

@Serializable
data class SummaryLine(
    val title: String,
    val date: Date? = null,
    val approximate: Boolean = false,
    val note: String? = null,
    val before: Boolean = false,
)

/** What a parent takes to a health visitor review: achieved milestones with dates, anything not seen yet, and their questions. */
@Serializable
data class ReviewSummary(
    val babyName: String,
    val dateOfBirth: Date,
    val preparedOn: Date,
    val age: String,
    val weeksEarly: Int,
    val achieved: Map<Domain, List<SummaryLine>>,
    val notSeenYet: List<SummaryLine>,
    val notSure: List<SummaryLine>,
    val noLongerDoing: List<SummaryLine>,
    val concerns: List<Concern>,
    val questions: List<String>,
    val contentVersion: String,
) {
    fun toPlainText(): String = buildString {
        val format = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)
        fun line(l: SummaryLine) = buildString {
            append("• ").append(l.title)
            l.date?.let { append(" — ").append(if (l.before) "before " else if (l.approximate) "around " else "").append(it.format(format)) }
            l.note?.let { append(" (").append(it).append(")") }
        }
        appendLine("Development summary for $babyName")
        appendLine("Born ${dateOfBirth.format(format)} · $age" + if (weeksEarly > 0) " · born $weeksEarly weeks early" else "")
        appendLine("Prepared ${preparedOn.format(format)}")
        if (questions.isNotEmpty()) {
            appendLine().appendLine("Questions from parents")
            questions.forEach { appendLine("• $it") }
        }
        if (noLongerDoing.isNotEmpty()) {
            appendLine().appendLine("No longer doing (please discuss)")
            noLongerDoing.forEach { appendLine(line(it)) }
        }
        if (notSeenYet.isNotEmpty() || notSure.isNotEmpty()) {
            appendLine().appendLine("Not seen yet")
            notSeenYet.forEach { appendLine(line(it)) }
            notSure.forEach { appendLine(line(it.copy(title = "${it.title} (not sure)"))) }
        }
        appendLine().appendLine("Achieved")
        if (achieved.isEmpty()) appendLine("• Nothing logged yet")
        achieved.forEach { (domain, lines) ->
            appendLine(domain.label)
            lines.forEach { appendLine(line(it)) }
        }
        appendLine().append("Milestones from the CDC/AAP checklists (content $contentVersion). General information, not a screening result.")
    }

    companion object {
        fun build(snapshot: BabySnapshot, concerns: List<Concern>, notes: List<ReviewNote>): ReviewSummary {
            fun titles(status: MilestoneStatus) =
                snapshot.withStatus(status).map { SummaryLine(it.milestone.title, note = it.log?.note) }
            val achieved = snapshot.withStatus(MilestoneStatus.ACHIEVED)
                .sortedBy { it.log?.achievedOn ?: it.log?.markedOn }
                .groupBy({ it.milestone.domain }) {
                    SummaryLine(it.milestone.title, it.log?.achievedOn, it.log?.approximate ?: false, it.log?.note,
                        it.log?.isUndatedCatchUp ?: false)
                }
            return ReviewSummary(
                babyName = snapshot.baby.name,
                dateOfBirth = snapshot.baby.dateOfBirth,
                preparedOn = snapshot.today,
                age = snapshot.ages.describe(),
                weeksEarly = snapshot.ages.weeksEarly,
                achieved = Domain.entries.mapNotNull { d -> achieved[d]?.let { d to it } }.toMap(),
                notSeenYet = titles(MilestoneStatus.NOT_SEEN_YET),
                notSure = titles(MilestoneStatus.NOT_SURE),
                noLongerDoing = snapshot.withStatus(MilestoneStatus.SKILL_LOST)
                    .map { SummaryLine(it.milestone.title, it.log?.lostOn, false, it.log?.note) },
                concerns = concerns,
                questions = notes.filterNot { it.resolved }.map { it.text },
                contentVersion = snapshot.contentVersion,
            )
        }
    }
}
