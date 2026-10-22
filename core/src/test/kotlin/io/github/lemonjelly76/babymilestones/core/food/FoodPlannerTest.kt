package io.github.lemonjelly76.babymilestones.core.food

import io.github.lemonjelly76.babymilestones.core.Fixtures
import io.github.lemonjelly76.babymilestones.core.Fixtures.date
import org.junit.Test
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FoodPlannerTest {

    private val content = Fixtures.content
    private val planner = FoodPlanner(content)
    private val start = date("2026-10-22")
    private var n = 0

    private fun tasted(foodId: String, on: String, liking: Liking? = null, reaction: String? = null): FoodTasting {
        val food = content.food(foodId)!!
        return FoodTasting("t_${n++}", "b_01", food.id, food.name, food.group, date(on), liking, reaction)
    }

    @Test
    fun `a two-week plan has a new food every day, starting with less sweet vegetables`() {
        val plan = planner.plan(emptyList(), start, ageMonths = 6)
        val newFoods = plan.filter { it.kind == PlanKind.NEW_FOOD || it.kind == PlanKind.NEW_ALLERGEN }
        assertEquals(14, newFoods.map { it.plannedOn }.distinct().size)
        assertEquals(newFoods.size, newFoods.map { it.foodId }.distinct().size, "each new food once")
        assertEquals("food.broccoli", plan.first { it.kind == PlanKind.NEW_FOOD }.foodId)
    }

    @Test
    fun `new allergens come one at a time, at least three days apart, and are then kept going`() {
        val plan = planner.plan(emptyList(), start, ageMonths = 6, days = 21)
        val firsts = plan.filter { it.kind == PlanKind.NEW_ALLERGEN }
        assertTrue(firsts.size >= 3)
        assertEquals("food.egg", firsts.first().foodId)
        firsts.zipWithNext().forEach { (a, b) -> assertTrue(ChronoUnit.DAYS.between(a.plannedOn, b.plannedOn) >= 3) }
        firsts.forEach { f -> assertTrue(plan.none { it.plannedOn == f.plannedOn && it != f && it.kind == PlanKind.NEW_ALLERGEN }) }
        assertTrue(plan.any { it.kind == PlanKind.KEEP_GOING && it.foodId == "food.egg" }, "egg comes back within the three weeks")
        plan.groupBy { it.plannedOn }.values.forEach { day ->
            if (day.any { it.kind == PlanKind.NEW_ALLERGEN }) {
                assertEquals(1, day.count { content.food(it.foodId)!!.allergens.isNotEmpty() }, "a new allergen's day has no other allergen")
            }
        }
    }

    @Test
    fun `foods not liked yet are offered again, and nothing with a reaction allergen is planned`() {
        val tastings = listOf(
            tasted("food.cauliflower", "2026-10-15", Liking.DISLIKED),
            tasted("food.yoghurt", "2026-10-16", reaction = "hives"),
        )
        val plan = planner.plan(tastings, start, ageMonths = 7)
        assertTrue(plan.any { it.kind == PlanKind.OFFER_AGAIN && it.foodId == "food.cauliflower" })
        assertTrue(plan.none { content.food(it.foodId)!!.allergens.contains(Allergen.MILK) })
        assertTrue(plan.none { it.foodId == "food.honey" })
    }

    @Test
    fun `nothing is planned before weaning age`() {
        assertEquals(emptyList(), planner.plan(emptyList(), start, ageMonths = 4))
    }

    @Test
    fun `a planned food is done once it has been tasted since the plan was made`() {
        val item = PlannedFood("p1", "b_01", "food.broccoli", "Broccoli", FoodGroup.VEGETABLES, date("2026-10-23"),
            createdOn = date("2026-10-22"))
        val before = tasted("food.broccoli", "2026-10-20")
        assertEquals(false, FoodPlanner.progress(listOf(item), listOf(before)).single().done)
        val after = tasted("food.broccoli", "2026-10-22")
        assertEquals(date("2026-10-22"), FoodPlanner.progress(listOf(item), listOf(before, after)).single().doneOn)

        val own = item.copy(foodId = null, name = "Gran's soup")
        val soup = FoodTasting("t_x", "b_01", null, "gran's soup ", FoodGroup.OTHER, date("2026-10-24"))
        assertTrue(FoodPlanner.progress(listOf(own), listOf(soup)).single().done)
    }
}
