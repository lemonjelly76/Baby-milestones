package io.github.lemonjelly76.babymilestones.server.tools

import io.github.lemonjelly76.babymilestones.core.content.ContentBundle
import io.github.lemonjelly76.babymilestones.core.content.SignpostCopy
import io.github.lemonjelly76.babymilestones.core.food.Allergen
import io.github.lemonjelly76.babymilestones.core.food.Food
import io.github.lemonjelly76.babymilestones.core.food.FoodGroup
import io.github.lemonjelly76.babymilestones.core.food.PlanDay
import io.github.lemonjelly76.babymilestones.core.food.PlanSuggestion
import io.github.lemonjelly76.babymilestones.core.guides.ChecklistProgress
import io.github.lemonjelly76.babymilestones.core.guides.ChecklistTick
import io.github.lemonjelly76.babymilestones.core.guides.Guide
import io.github.lemonjelly76.babymilestones.core.guides.GuideFeature
import io.github.lemonjelly76.babymilestones.core.guides.SchoolDates
import io.github.lemonjelly76.babymilestones.core.guides.SchoolStart
import io.github.lemonjelly76.babymilestones.core.guides.GuideSection
import io.github.lemonjelly76.babymilestones.core.guides.GuideTopic
import io.github.lemonjelly76.babymilestones.core.guides.Guides
import io.github.lemonjelly76.babymilestones.core.json.Date
import io.github.lemonjelly76.babymilestones.core.records.FirstWord
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class FoodView(
    val id: String,
    val name: String,
    val group: FoodGroup,
    val allergens: List<Allergen>,
    val ironRich: Boolean,
    val fromMonths: Int,
    val suitableNow: Boolean? = null,
    val howToServe: String,
    val chokingRisk: Boolean,
    val note: String? = null,
    val sources: List<SourceRef> = emptyList(),
)

@Serializable
data class ChecklistItemView(val id: String, val text: String, val done: Boolean, val tickedOn: Date? = null)

@Serializable
data class GuideSummaryView(
    val id: String,
    val title: String,
    val summary: String,
    val topic: GuideTopic,
    val ages: String,
    val checklist: ChecklistProgress,
)

@Serializable
data class GuideView(
    val id: String,
    val title: String,
    val summary: String,
    val topic: GuideTopic,
    val ages: String,
    val card: SignpostCopy? = null,
    val sections: List<GuideSection>,
    val checklist: List<ChecklistItemView>,
    val sources: List<SourceRef>,
    val schoolDates: SchoolDates? = null,
)

@Serializable
data class GuidesView(val ageMonths: Int, val now: List<GuideSummaryView>, val comingUp: List<GuideSummaryView>)

@Serializable
data class ToothView(val id: String, val name: String, val usuallyAroundMonths: Int, val cameOn: Date? = null)

@Serializable
data class FirstsView(val words: List<FirstWord>, val teeth: List<ToothView>, val sources: List<SourceRef>)

@Serializable
data class FoodPlanView(val days: List<PlanDay>, val suggested: List<PlanSuggestion>)

object FoodAndGuideViews {
    fun food(content: ContentBundle, f: Food, ageMonths: Int?) = FoodView(
        f.id, f.name, f.group, f.allergens, f.ironRich, f.fromMonths, ageMonths?.let(f::suitableAt),
        f.servingAt(ageMonths ?: f.fromMonths).text, f.chokingRisk, f.note, ToolViews.sources(content, f.sources),
    )

    fun briefFood(f: Food, ageMonths: Int?) = FoodView(
        f.id, f.name, f.group, f.allergens, f.ironRich, f.fromMonths, ageMonths?.let(f::suitableAt),
        f.servingAt(ageMonths ?: f.fromMonths).text, f.chokingRisk, f.note,
    )

    private fun ages(g: Guide) = "${g.fromMonths}–${g.toMonths} months"

    fun summary(g: Guide, ticks: List<ChecklistTick>) =
        GuideSummaryView(g.id, g.title, g.summary, g.topic, ages(g), Guides.progress(g, ticks))

    fun guide(content: ContentBundle, g: Guide, ticks: List<ChecklistTick>, region: String, dateOfBirth: LocalDate): GuideView {
        val byItem = ticks.associateBy { it.itemId }
        return GuideView(
            g.id, g.title, g.summary, g.topic, ages(g), g.card, g.sections,
            g.checklist.map { ChecklistItemView(it.id, it.text, it.id in byItem, byItem[it.id]?.tickedOn) },
            ToolViews.sources(content, g.sources),
            if (g.feature == GuideFeature.SCHOOL_DATES) SchoolStart.forRegion(region, dateOfBirth) else null,
        )
    }
}
