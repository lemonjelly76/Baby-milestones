package io.github.lemonjelly76.babymilestones.core.content

import io.github.lemonjelly76.babymilestones.core.food.Food
import io.github.lemonjelly76.babymilestones.core.food.FoodGroup
import io.github.lemonjelly76.babymilestones.core.food.FoodNames
import io.github.lemonjelly76.babymilestones.core.growth.GrowthReference
import io.github.lemonjelly76.babymilestones.core.guides.ChecklistItem
import io.github.lemonjelly76.babymilestones.core.records.Tooth
import io.github.lemonjelly76.babymilestones.core.guides.Guide
import io.github.lemonjelly76.babymilestones.core.json.Date
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Domain(val label: String) {
    @SerialName("social") SOCIAL("Social and emotional"),
    @SerialName("language") LANGUAGE("Language and communication"),
    @SerialName("cognitive") COGNITIVE("Learning, thinking and problem-solving"),
    @SerialName("movement") MOVEMENT("Movement and physical"),
}

/** A published source: a guideline, checklist, study or review that content cites by [id]. */
@Serializable
data class Reference(
    val id: String,
    val title: String,
    val publisher: String,
    val year: Int,
    val url: String,
    val kind: Kind,
    val note: String? = null,
) {
    @Serializable
    enum class Kind {
        @SerialName("checklist") CHECKLIST,
        @SerialName("guideline") GUIDELINE,
        @SerialName("systematic_review") SYSTEMATIC_REVIEW,
        @SerialName("study") STUDY,
        @SerialName("public_health") PUBLIC_HEALTH,
    }
}

/** A measured range of ages over which healthy children reach a milestone, e.g. the WHO MGRS 1st–99th percentiles. */
@Serializable
data class TypicalWindow(val fromMonths: Double, val toMonths: Double, val reference: String, val label: String)

@Serializable
data class Milestone(
    val id: String,
    val domain: Domain,
    val title: String,
    val description: String,
    val emergingFrom: Int,
    val expectedBy: Int,
    val leadsTo: List<String> = emptyList(),
    val buildsOn: List<String> = emptyList(),
    val activities: List<String> = emptyList(),
    val concernNote: String? = null,
    val safetyNote: String? = null,
    val typicalWindow: TypicalWindow? = null,
    /** False for milestones many healthy children skip (e.g. crawling), which never raise a concern. */
    val concernTracking: Boolean = true,
    val sources: List<String>,
)

@Serializable
data class PeriodGuide(
    val id: String,
    val name: String,
    val fromMonths: Int,
    val toMonths: Int,
    val checklistMonths: Int,
    val typical: String,
    val sleep: String,
    val feeding: String,
    val play: List<String>,
    val safety: List<String>,
    val whatHappensNext: String,
    val touchpoints: List<String>,
    val sources: List<String>,
)

@Serializable
enum class Urgency {
    @SerialName("info") INFO,
    @SerialName("routine") ROUTINE,
    @SerialName("prompt") PROMPT,
    @SerialName("urgent") URGENT,
}

@Serializable
data class SignpostCopy(val title: String, val body: String, val signpost: String, val urgency: Urgency)

@Serializable
data class ConcernRules(
    val notSeenOne: SignpostCopy,
    val worthAChat: SignpostCopy,
    val skillLost: SignpostCopy,
    val worthAChatDomainCount: Int = 2,
    val worthAChatWeeksPast: Int = 4,
    val recheckWeeksMin: Int = 2,
    val recheckWeeksMax: Int = 4,
    val sources: List<String>,
)

/** Fixed phrase lists checked in app code before any model call; the cards are rendered by the app, not the model. */
@Serializable
data class SafetyContent(
    val urgentPhrases: List<String>,
    val distressPhrases: List<String>,
    val urgentCard: SignpostCopy,
    val distressCard: SignpostCopy,
    val medicationPhrases: List<String>,
    val medicationCard: SignpostCopy,
    val disclaimer: String,
    val disclaimerLink: String,
)

/** An age offset from date of birth: months first (calendar-aware), then weeks and days. */
@Serializable
data class AgeOffset(val months: Int = 0, val weeks: Int = 0, val days: Int = 0) {
    fun from(date: java.time.LocalDate): java.time.LocalDate =
        date.plusMonths(months.toLong()).plusWeeks(weeks.toLong()).plusDays(days.toLong())
}

@Serializable
data class AppointmentRule(
    val id: String,
    val title: String,
    val with: String,
    val windowStart: AgeOffset,
    val windowEnd: AgeOffset,
    val description: String,
    val dobFrom: Date? = null,
    val dobTo: Date? = null,
    val sources: List<String>,
)

@Serializable
data class DoseRule(
    val code: String,
    val vaccine: String,
    val dose: String,
    val age: AgeOffset,
    val appointment: String,
    val protectsAgainst: String,
    val dobFrom: Date? = null,
    val dobTo: Date? = null,
    val sources: List<String>,
)

@Serializable
data class RegionSchedule(
    val region: String,
    val version: String,
    val appliesTo: List<String>,
    val note: String,
    val appointments: List<AppointmentRule>,
    val doses: List<DoseRule>,
) {
    fun appointmentsFor(dob: java.time.LocalDate) = appointments.filter { inRange(dob, it.dobFrom, it.dobTo) }
    fun dosesFor(dob: java.time.LocalDate) = doses.filter { inRange(dob, it.dobFrom, it.dobTo) }

    private fun inRange(dob: java.time.LocalDate, from: java.time.LocalDate?, to: java.time.LocalDate?) =
        (from == null || !dob.isBefore(from)) && (to == null || !dob.isAfter(to))
}

@Serializable
data class ContentBundle(
    val version: String,
    val locale: String,
    val references: List<Reference>,
    val periods: List<PeriodGuide>,
    val milestones: List<Milestone>,
    val concerns: ConcernRules,
    val safety: SafetyContent,
    val schedules: List<RegionSchedule>,
    val foods: List<Food> = emptyList(),
    val guides: List<Guide> = emptyList(),
    val growth: GrowthReference? = null,
    val teeth: List<Tooth> = emptyList(),
) {
    private val milestonesById by lazy { milestones.associateBy { it.id } }
    private val referencesById by lazy { references.associateBy { it.id } }
    private val foodsById by lazy { foods.associateBy { it.id } }
    private val foodsByName by lazy {
        foods.flatMap { f -> (listOf(f.name) + f.aliases).flatMap(FoodNames::forms).map { it to f } }.toMap()
    }

    fun milestone(id: String): Milestone? = milestonesById[id]
    fun reference(id: String): Reference? = referencesById[id]
    fun period(id: String): PeriodGuide? = periods.firstOrNull { it.id == id }
    fun food(id: String): Food? = foodsById[id]
    fun tooth(id: String): Tooth? = teeth.firstOrNull { it.id == id }
    fun guide(id: String): Guide? = guides.firstOrNull { it.id == id }
    fun checklistItem(id: String): Pair<Guide, ChecklistItem>? =
        guides.firstNotNullOfOrNull { g -> g.checklist.firstOrNull { it.id == id }?.let { g to it } }

    /** A catalogue food by id, name or alias, ignoring case and a plural ending ("eggs" finds Egg). */
    fun findFood(text: String): Food? = food(text.trim()) ?: FoodNames.forms(text).firstNotNullOfOrNull { foodsByName[it] }

    /** Foods whose name or an alias contains the query (or its singular), optionally in one group. */
    fun searchFoods(query: String?, group: FoodGroup? = null): List<Food> {
        val forms = query?.let(FoodNames::forms).orEmpty()
        return foods.filter { f ->
            (group == null || f.group == group) &&
                (forms.isEmpty() || (listOf(f.name) + f.aliases).any { n -> forms.any { it in n.lowercase() } })
        }
    }

    /** The schedule for a UK nation; nations without their own schedule yet fall back to the first one. */
    fun schedule(region: String): RegionSchedule =
        schedules.firstOrNull { region in it.appliesTo } ?: schedules.first()

    /** The period whose range holds [months] on the milestone clock; past the last period, the last one. */
    fun periodForMonths(months: Int): PeriodGuide =
        periods.lastOrNull { months >= it.fromMonths } ?: periods.first()

    fun nextPeriod(period: PeriodGuide): PeriodGuide? = periods.firstOrNull { it.fromMonths == period.toMonths }
}
