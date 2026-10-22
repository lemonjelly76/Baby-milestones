package io.github.lemonjelly76.babymilestones.core.concerns

import io.github.lemonjelly76.babymilestones.core.content.ConcernRules
import io.github.lemonjelly76.babymilestones.core.content.Domain
import io.github.lemonjelly76.babymilestones.core.content.Urgency
import io.github.lemonjelly76.babymilestones.core.tracking.BabySnapshot
import io.github.lemonjelly76.babymilestones.core.tracking.MilestoneStatus
import io.github.lemonjelly76.babymilestones.core.tracking.TrackedMilestone
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ConcernKind {
    @SerialName("not_seen_one") NOT_SEEN_ONE,
    @SerialName("worth_a_chat") WORTH_A_CHAT,
    @SerialName("skill_lost") SKILL_LOST,
}

/** Every concern ends with who to contact and how urgently; it never names a condition. */
@Serializable
data class Concern(
    val kind: ConcernKind,
    val milestoneIds: List<String>,
    val domain: Domain? = null,
    val title: String,
    val body: String,
    val signpost: String,
    val urgency: Urgency,
)

object ConcernEngine {

    fun concerns(snapshot: BabySnapshot, rules: ConcernRules): List<Concern> {
        val today = snapshot.today
        val tracked = snapshot.milestones.filter { it.milestone.concernTracking }

        val lost = tracked.filter { it.status == MilestoneStatus.SKILL_LOST }
            .map { concern(ConcernKind.SKILL_LOST, rules, listOf(it), it.milestone.domain) }

        // Unlogged is not the same as not seen: milestones whose checklist date passed before tracking began only
        // count once the parent has answered "not sure" about them.
        val since = snapshot.baby.trackingSince
        val open = tracked.filter { it.isOpenPastExpected(today) }
            .filter { since == null || it.status == MilestoneStatus.NOT_SURE || !it.expectedBy.isBefore(since) }
        val grouped = open.groupBy { it.milestone.domain }
            .filterValues { it.size >= rules.worthAChatDomainCount }
            .map { (domain, items) -> concern(ConcernKind.WORTH_A_CHAT, rules, items, domain) }
        val inGroups = grouped.flatMap { it.milestoneIds }.toSet()

        val longOpen = open.filter { it.id !in inGroups }
            .filter { today.isAfter(it.expectedBy.plusWeeks(rules.worthAChatWeeksPast.toLong())) }
            .map { concern(ConcernKind.WORTH_A_CHAT, rules, listOf(it), it.milestone.domain) }
        val escalated = inGroups + longOpen.flatMap { it.milestoneIds }

        val single = open.filter { it.id !in escalated && it.status == MilestoneStatus.NOT_SEEN_YET }
            .map { concern(ConcernKind.NOT_SEEN_ONE, rules, listOf(it), it.milestone.domain) }

        return (lost + grouped + longOpen + single).sortedByDescending { it.urgency }
    }

    private fun concern(kind: ConcernKind, rules: ConcernRules, items: List<TrackedMilestone>, domain: Domain?): Concern {
        val copy = when (kind) {
            ConcernKind.NOT_SEEN_ONE -> rules.notSeenOne
            ConcernKind.WORTH_A_CHAT -> rules.worthAChat
            ConcernKind.SKILL_LOST -> rules.skillLost
        }
        val names = items.joinToString(", ") { it.milestone.title.replaceFirstChar(Char::lowercase) }
        return Concern(
            kind = kind,
            milestoneIds = items.map { it.id },
            domain = domain,
            title = copy.title,
            body = copy.body.replace("{milestones}", names).replace("{domain}", domain?.label?.lowercase() ?: ""),
            signpost = copy.signpost,
            urgency = copy.urgency,
        )
    }
}
