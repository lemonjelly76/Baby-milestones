package io.github.lemonjelly76.babymilestones.server.tools

import io.github.lemonjelly76.babymilestones.core.content.ContentBundle
import io.github.lemonjelly76.babymilestones.core.content.Domain
import io.github.lemonjelly76.babymilestones.core.content.Milestone
import io.github.lemonjelly76.babymilestones.core.content.PeriodGuide
import io.github.lemonjelly76.babymilestones.core.content.TypicalWindow
import io.github.lemonjelly76.babymilestones.core.guidance.AgeSummary
import io.github.lemonjelly76.babymilestones.core.guidance.Guidance
import io.github.lemonjelly76.babymilestones.core.json.Date
import io.github.lemonjelly76.babymilestones.core.schedule.BabySchedule
import io.github.lemonjelly76.babymilestones.core.tracking.MilestoneStatus
import io.github.lemonjelly76.babymilestones.core.tracking.TrackedMilestone
import io.github.lemonjelly76.babymilestones.server.service.BabyContext
import kotlinx.serialization.Serializable

/** Compact shapes for tool results: enough for the model to answer and cite, small enough to keep calls cheap. */

@Serializable
data class SourceRef(val id: String, val title: String, val publisher: String, val year: Int, val url: String)

@Serializable
data class PeriodRef(val id: String, val name: String)

@Serializable
data class BabyListItem(
    val id: String,
    val name: String,
    val dateOfBirth: Date,
    val dobEstimated: Boolean,
    val age: AgeSummary,
    val period: PeriodRef,
)

@Serializable
data class LogView(
    val logId: String,
    val state: String,
    val achievedOn: Date? = null,
    val approximate: Boolean,
    val markedOn: Date,
    val note: String? = null,
    val lostOn: Date? = null,
)

@Serializable
data class MilestoneItem(
    val id: String,
    val title: String,
    val domain: Domain,
    val status: MilestoneStatus,
    val emergingFromMonths: Int,
    val expectedByMonths: Int,
    val expectedBy: Date,
    val log: LogView? = null,
)

@Serializable
data class MilestoneDetail(
    val id: String,
    val title: String,
    val domain: Domain,
    val description: String,
    val emergingFromMonths: Int,
    val expectedByMonths: Int,
    val whatExpectedByMeans: String,
    val typicalWindow: TypicalWindow? = null,
    val activities: List<String>,
    val concernNote: String? = null,
    val safetyNote: String? = null,
    val raisesConcerns: Boolean,
    val leadsTo: List<String>,
    val buildsOn: List<String>,
    val sources: List<SourceRef>,
)

@Serializable
data class PeriodGuideView(
    val id: String,
    val name: String,
    val ageRangeMonths: String,
    val startsOn: Date? = null,
    val typical: String,
    val sleep: String,
    val feeding: String,
    val play: List<String>,
    val safety: List<String>,
    val whatHappensNext: String,
    val touchpoints: List<String>,
    val sources: List<SourceRef>,
)

@Serializable
data class AppointmentView(
    val id: String,
    val title: String,
    val with: String,
    val windowStart: Date,
    val windowEnd: Date,
    val status: String,
    val bookedOn: Date? = null,
    val doneOn: Date? = null,
    val note: String? = null,
)

@Serializable
data class DoseView(
    val code: String,
    val vaccine: String,
    val dose: String,
    val protectsAgainst: String,
    val dueOn: Date,
    val status: String,
    val givenOn: Date? = null,
)

@Serializable
data class ScheduleView(
    val region: String,
    val scheduleVersion: String,
    val note: String,
    val appointments: List<AppointmentView>,
    val vaccinations: List<DoseView>,
    val sources: List<SourceRef>,
)

object ToolViews {
    const val EXPECTED_BY_MEANS =
        "expected_by is the CDC/AAP checklist age by which most children (75% or more) do this; not doing it by then " +
            "is a reason to watch and maybe talk to a health visitor, not a sign of being behind average."

    fun sources(content: ContentBundle, ids: List<String>) = ids.mapNotNull { content.reference(it) }
        .map { SourceRef(it.id, it.title, it.publisher, it.year, it.url) }

    fun babyItem(ctx: BabyContext) = BabyListItem(
        ctx.baby.id, ctx.baby.name, ctx.baby.dateOfBirth, ctx.baby.dobEstimated,
        Guidance.ageSummary(ctx.snapshot), PeriodRef(ctx.snapshot.period.id, ctx.snapshot.period.name),
    )

    fun item(t: TrackedMilestone) = MilestoneItem(
        t.id, t.milestone.title, t.milestone.domain, t.status, t.milestone.emergingFrom, t.milestone.expectedBy,
        t.expectedBy,
        t.log?.let { LogView(it.id, it.state.name.lowercase(), it.achievedOn, it.approximate, it.markedOn, it.note, it.lostOn) },
    )

    fun detail(content: ContentBundle, m: Milestone) = MilestoneDetail(
        m.id, m.title, m.domain, m.description, m.emergingFrom, m.expectedBy, EXPECTED_BY_MEANS, m.typicalWindow,
        m.activities, m.concernNote, m.safetyNote, m.concernTracking, m.leadsTo, m.buildsOn, sources(content, m.sources),
    )

    fun period(content: ContentBundle, p: PeriodGuide, ctx: BabyContext?) = PeriodGuideView(
        p.id, p.name, "${p.fromMonths}–${p.toMonths}", ctx?.let { Guidance.periodStart(it.snapshot, p) },
        p.typical, p.sleep, p.feeding, p.play, p.safety, p.whatHappensNext, p.touchpoints, sources(content, p.sources),
    )

    fun schedule(content: ContentBundle, region: String, schedule: BabySchedule): ScheduleView {
        val rules = content.schedule(region)
        return ScheduleView(
            region = schedule.region,
            scheduleVersion = schedule.version,
            note = rules.note,
            appointments = schedule.appointments.map {
                AppointmentView(it.rule.id, it.rule.title, it.rule.with, it.windowStart, it.windowEnd,
                    it.status.name.lowercase(), it.record?.bookedOn, it.record?.doneOn, it.record?.note)
            },
            vaccinations = schedule.doses.map {
                DoseView(it.rule.code, it.rule.vaccine, it.rule.dose, it.rule.protectsAgainst, it.dueOn,
                    it.status.name.lowercase(), it.record?.givenOn)
            },
            sources = sources(content, (rules.appointments.flatMap { it.sources } + rules.doses.flatMap { it.sources }).distinct()),
        )
    }
}
