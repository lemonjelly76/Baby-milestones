package io.github.lemonjelly76.babymilestones.core.guides

import io.github.lemonjelly76.babymilestones.core.content.SignpostCopy
import io.github.lemonjelly76.babymilestones.core.json.Date
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class GuideTopic(val label: String) {
    @SerialName("feeding") FEEDING("Feeding"),
    @SerialName("safety") SAFETY("Safety"),
    @SerialName("health") HEALTH("Health and teeth"),
    @SerialName("everyday") EVERYDAY("Everyday life"),
}

/** Something a guide adds beyond its text, worked out by the app from the baby's records. */
@Serializable
enum class GuideFeature {
    @SerialName("foods") FOODS,
    @SerialName("school_dates") SCHOOL_DATES,
}

@Serializable
data class GuideSection(
    val heading: String? = null,
    val paragraphs: List<String> = emptyList(),
    val bullets: List<String> = emptyList(),
)

@Serializable
data class ChecklistItem(val id: String, val text: String, val detail: String? = null)

/** A short practical guide that the Guides tab shows while the child's age is in [fromMonths, toMonths). */
@Serializable
data class Guide(
    val id: String,
    val title: String,
    val summary: String,
    val topic: GuideTopic,
    val fromMonths: Int,
    val toMonths: Int,
    val sections: List<GuideSection>,
    val checklist: List<ChecklistItem> = emptyList(),
    /** A card shown above the text, such as the 999 steps for choking. */
    val card: SignpostCopy? = null,
    val feature: GuideFeature? = null,
    val sources: List<String>,
) {
    fun coversAge(months: Int) = months in fromMonths until toMonths
}

/** A checklist item a carer ticked for one baby. Only ticks are stored; an unticked item has no row. */
@Serializable
data class ChecklistTick(val babyId: String, val itemId: String, val tickedOn: Date, val tickedBy: String? = null)
