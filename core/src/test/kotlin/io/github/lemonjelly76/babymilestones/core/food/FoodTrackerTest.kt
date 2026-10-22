package io.github.lemonjelly76.babymilestones.core.food

import io.github.lemonjelly76.babymilestones.core.Fixtures
import io.github.lemonjelly76.babymilestones.core.Fixtures.date
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FoodTrackerTest {

    private val content = Fixtures.content
    private val tracker = FoodTracker(content)
    private val today = date("2026-10-22")
    private var n = 0

    private fun tasted(
        foodId: String, on: String, liking: Liking? = null, reaction: String? = null,
    ): FoodTasting {
        val food = content.food(foodId)!!
        return FoodTasting("t_${n++}", "b_01", food.id, food.name, food.group, date(on), liking, reaction)
    }

    private fun own(name: String, on: String, group: FoodGroup = FoodGroup.OTHER) =
        FoodTasting("t_${n++}", "b_01", null, name, group, date(on))

    @Test
    fun `counts each food once however often it was offered, own foods by name`() {
        val report = tracker.report(listOf(
            tasted("food.broccoli", "2026-10-01"), tasted("food.broccoli", "2026-10-05"),
            own("Gran's lentil soup", "2026-10-03"), own("gran's lentil soup ", "2026-10-04"),
        ), today, 7)

        assertEquals(2, report.tried)
        val broccoli = report.foods.first { it.foodId == "food.broccoli" }
        assertEquals(2, broccoli.timesOffered)
        assertEquals(date("2026-10-01"), broccoli.firstTried)
        assertEquals(date("2026-10-05"), broccoli.lastOffered)
        assertEquals(1, report.groups.first { it.group == FoodGroup.VEGETABLES }.tried)
    }

    @Test
    fun `a food she hasn't liked yet is worth offering again until it has had ten tries`() {
        val tries = (1..3).map { tasted("food.cauliflower", "2026-10-0$it", Liking.DISLIKED) }
        val report = tracker.report(tries, today, 7)
        val cauliflower = report.foods.first { it.foodId == "food.cauliflower" }
        assertTrue(cauliflower.keepOffering)
        assertTrue(report.suggestions.any { it.foodId == "food.cauliflower" && it.kind == SuggestionKind.KEEP_OFFERING })

        val tenTries = (1..10).map { tasted("food.cauliflower", "2026-10-${10 + it}", Liking.DISLIKED) }
        assertEquals(false, tracker.report(tenTries, today, 7).foods.first().keepOffering)

        val liked = tries + tasted("food.cauliflower", "2026-10-09", Liking.LIKED)
        assertEquals(false, tracker.report(liked, today, 7).foods.first().keepOffering)
    }

    @Test
    fun `allergens move from not yet to introduced, and are due again after a week without`() {
        val report = tracker.report(listOf(
            tasted("food.egg", "2026-10-20"),
            tasted("food.peanut_butter", "2026-10-10"),
        ), today, 7)

        fun state(a: Allergen) = report.allergens.first { it.allergen == a }.state
        assertEquals(AllergenState.INTRODUCED, state(Allergen.EGG))
        assertEquals(AllergenState.DUE, state(Allergen.PEANUT))
        assertEquals(AllergenState.NOT_YET, state(Allergen.MILK))
        assertEquals(12, report.allergens.first { it.allergen == Allergen.PEANUT }.daysSinceLast)
    }

    @Test
    fun `a possible reaction stops nudges for that allergen and is listed for the health visitor`() {
        val report = tracker.report(listOf(tasted("food.egg", "2026-10-01", reaction = "red blotches round her mouth")), today, 7)

        assertEquals(AllergenState.REACTION, report.allergens.first { it.allergen == Allergen.EGG }.state)
        assertTrue(report.suggestions.none { it.foodId == "food.egg" })
        assertEquals("red blotches round her mouth", report.reactions.single().text)
    }

    @Test
    fun `suggests a due allergen, then one new allergen at a time, then vegetables`() {
        val report = tracker.report(listOf(tasted("food.egg", "2026-10-01", Liking.LIKED)), today, 7)
        val kinds = report.suggestions.map { it.kind }

        assertEquals(SuggestionKind.ALLERGEN_KEEP_GOING, kinds.first())
        assertEquals("food.egg", report.suggestions.first().foodId)
        assertEquals(1, kinds.count { it == SuggestionKind.NEW_ALLERGEN })
        assertEquals("food.peanut_butter", report.suggestions.first { it.kind == SuggestionKind.NEW_ALLERGEN }.foodId)
        assertTrue(SuggestionKind.NEW_VEGETABLE in kinds)
        assertTrue(report.suggestions.size <= FoodTracker.MAX_SUGGESTIONS)
    }

    @Test
    fun `no new allergen is suggested within three days of the last new one`() {
        val report = tracker.report(listOf(tasted("food.egg", "2026-10-21")), today, 7)
        assertTrue(report.suggestions.none { it.kind == SuggestionKind.NEW_ALLERGEN })
    }

    @Test
    fun `never suggests a food she is too young for, and suggests nothing before weaning starts`() {
        val report = tracker.report(listOf(tasted("food.broccoli", "2026-10-01")), today, 7)
        assertTrue(report.suggestions.none { it.foodId == "food.honey" })

        assertEquals(emptyList(), tracker.report(emptyList(), today, 4).suggestions)
    }

    @Test
    fun `serving advice changes with age`() {
        val grapes = content.food("food.grapes")!!
        assertTrue(grapes.chokingRisk)
        assertTrue("quarters" in grapes.servingAt(7).text)
        val honey = content.food("food.honey")!!
        assertEquals(false, honey.suitableAt(11))
        assertTrue(honey.suitableAt(12))
    }

    @Test
    fun `finds catalogue foods by name, alias or plural, case-insensitively`() {
        assertEquals("food.broccoli", content.findFood("Broccoli")?.id)
        assertEquals("food.egg", content.findFood("eggs")?.id)
        assertEquals("food.peanut_butter", content.findFood("food.peanut_butter")?.id)
        assertNull(content.findFood("gran's lentil soup"))
    }

    @Test
    fun `a due allergen is never suggested through a food with an allergen that caused a possible reaction`() {
        val report = tracker.report(listOf(
            tasted("food.pancakes", "2026-10-01", Liking.LIKED),
            tasted("food.yoghurt", "2026-10-05", reaction = "hives"),
        ), today, 7)
        val egg = report.suggestions.first { it.kind == SuggestionKind.ALLERGEN_KEEP_GOING }
        assertEquals("food.egg", egg.foodId)
        assertTrue(report.suggestions.none { content.food(it.foodId)!!.allergens.contains(Allergen.MILK) })
    }

    @Test
    fun `plain milk is not taken to mean cows' milk`() {
        assertNull(content.findFood("milk"))
        assertEquals("food.cows_milk", content.findFood("cows' milk")?.id)
    }

    @Test
    fun `food search matches names and aliases, never a short name inside a longer query`() {
        assertEquals(emptyList(), content.searchFoods("milkshake").map { it.id })
        assertEquals(listOf("food.aubergine"), content.searchFoods("eggplant").map { it.id })
        assertTrue(content.searchFoods("berries").map { it.id }.containsAll(listOf("food.strawberries", "food.blueberries")))
        assertTrue(content.searchFoods("eggs").any { it.id == "food.egg" })
        assertTrue(content.searchFoods(null, FoodGroup.DAIRY).all { it.group == FoodGroup.DAIRY })
    }

    @Test
    fun `own foods share one key however they're typed`() {
        assertEquals(FoodNames.key(null, " Gran's Soup"), own("gran's soup", "2026-10-01").key)
        assertEquals("food.egg", FoodNames.key("food.egg", "Egg"))
    }

    @Test
    fun `the tracker shows from 4 months to 3 years, or whenever foods have been logged`() {
        assertEquals(false, FoodTracker.showsTracker(3, hasTastings = false))
        assertTrue(FoodTracker.showsTracker(4, hasTastings = false))
        assertEquals(false, FoodTracker.showsTracker(40, hasTastings = false))
        assertTrue(FoodTracker.showsTracker(40, hasTastings = true))
    }
}
