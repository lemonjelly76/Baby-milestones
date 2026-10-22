package io.github.lemonjelly76.babymilestones.core.food

import io.github.lemonjelly76.babymilestones.core.json.Date
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class FoodGroup(val label: String) {
    @SerialName("vegetables") VEGETABLES("Vegetables"),
    @SerialName("fruit") FRUIT("Fruit"),
    @SerialName("starchy") STARCHY("Starchy foods"),
    @SerialName("protein") PROTEIN("Protein"),
    @SerialName("dairy") DAIRY("Dairy and alternatives"),
    @SerialName("other") OTHER("Other"),
}

/** The foods the NHS lists as able to trigger allergic reactions, in the order the tracker suggests them: egg and peanut first. */
@Serializable
enum class Allergen(val label: String) {
    @SerialName("egg") EGG("Egg"),
    @SerialName("peanut") PEANUT("Peanut"),
    @SerialName("milk") MILK("Cows' milk"),
    @SerialName("gluten") GLUTEN("Gluten (wheat, barley, rye)"),
    @SerialName("sesame") SESAME("Sesame and other seeds"),
    @SerialName("fish") FISH("Fish"),
    @SerialName("soya") SOYA("Soya"),
    @SerialName("tree_nuts") TREE_NUTS("Tree nuts"),
    @SerialName("shellfish") SHELLFISH("Shellfish"),
}

/** How to serve a food from an age; a food lists these in age order and the latest that applies is shown. */
@Serializable
data class ServingTip(val fromMonths: Int, val text: String)

/** One food in the cited catalogue that the foods tracker is built on. */
@Serializable
data class Food(
    val id: String,
    val name: String,
    val group: FoodGroup,
    val aliases: List<String> = emptyList(),
    val allergens: List<Allergen> = emptyList(),
    val ironRich: Boolean = false,
    /** The earliest age to offer it at all, e.g. 12 for honey. */
    val fromMonths: Int = 6,
    val serving: List<ServingTip>,
    /** Needs particular preparation to lower the choking risk; the serving tip says how. */
    val chokingRisk: Boolean = false,
    val note: String? = null,
    val sources: List<String>,
) {
    fun servingAt(months: Int): ServingTip = serving.lastOrNull { months >= it.fromMonths } ?: serving.first()
    fun suitableAt(months: Int): Boolean = months >= fromMonths
}

@Serializable
enum class Liking(val label: String) {
    @SerialName("liked") LIKED("Liked it"),
    @SerialName("not_sure") NOT_SURE("Not sure"),
    @SerialName("disliked") DISLIKED("Didn't like it"),
}

/**
 * One time a baby was offered a food. Catalogue foods carry [foodId]; the family's own foods carry only a [name] and
 * [group]. A [reaction] is the parent's own words about a possible reaction, never a diagnosis.
 */
@Serializable
data class FoodTasting(
    val id: String,
    val babyId: String,
    val foodId: String? = null,
    val name: String,
    val group: FoodGroup = FoodGroup.OTHER,
    val tastedOn: Date,
    val liking: Liking? = null,
    val reaction: String? = null,
    val note: String? = null,
    val photoRef: String? = null,
    val loggedBy: String? = null,
) {
    /** Tastings of the same food share a key: the catalogue id, or the family's own food by name. */
    val key: String get() = foodId ?: ("own:" + name.trim().lowercase())
}
