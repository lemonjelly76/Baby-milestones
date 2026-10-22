package io.github.lemonjelly76.babymilestones.core.content

import io.github.lemonjelly76.babymilestones.core.Fixtures
import io.github.lemonjelly76.babymilestones.core.food.Allergen
import io.github.lemonjelly76.babymilestones.core.food.FoodGroup
import io.github.lemonjelly76.babymilestones.core.guides.ChecklistItem
import io.github.lemonjelly76.babymilestones.core.guides.GuideFeature
import io.github.lemonjelly76.babymilestones.core.guides.GuideLink
import io.github.lemonjelly76.babymilestones.core.guides.LinkKind
import io.github.lemonjelly76.babymilestones.core.json.BabyJson
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContentBundleTest {

    private val content = Fixtures.content

    @Test
    fun `shipped bundle is valid`() {
        assertEquals(emptyList(), ContentValidator.problems(content))
    }

    @Test
    fun `every CDC checklist age from 2 months to 5 years has milestones in all four domains`() {
        val ages = listOf(2, 4, 6, 9, 12, 15, 18, 24, 30, 36, 48, 60)
        ages.forEach { age ->
            val domains = content.milestones.filter { it.expectedBy == age && it.concernTracking }.map { it.domain }.toSet()
            assertEquals(Domain.entries.toSet(), domains, "domains at $age months")
        }
    }

    @Test
    fun `periods run from birth to 5 years without gaps`() {
        assertEquals(0, content.periods.first().fromMonths)
        assertEquals(60, content.periods.last().toMonths)
        assertEquals("infant_4_6m", content.periodForMonths(5).id)
        assertEquals("four_48_60m", content.periodForMonths(72).id)
    }

    @Test
    fun `crawling never raises a concern`() {
        assertEquals(false, content.milestone("move.crawls")!!.concernTracking)
    }

    @Test
    fun `motor milestones carry the WHO windows of achievement`() {
        val sitting = content.milestone("move.sits_without_support")!!.typicalWindow!!
        assertEquals(3.8, sitting.fromMonths)
        assertEquals(9.2, sitting.toMonths)
        assertEquals("who-mgrs-motor-2006", sitting.reference)
    }

    @Test
    fun `assembled bundle round-trips through JSON`() {
        val text = BabyJson.encodeToString(ContentBundle.serializer(), content)
        assertTrue("\"expected_by\"" in text)
        assertEquals(content, ContentLoader.fromJson(text))
    }

    @Test
    fun `validator reports broken links and missing sources`() {
        val broken = content.copy(
            milestones = content.milestones.take(1).map { it.copy(leadsTo = listOf("nope"), sources = emptyList()) },
        )
        val problems = ContentValidator.problems(broken)
        assertTrue(problems.any { "unknown milestone nope" in it })
        assertTrue(problems.any { "cites no source" in it })
    }

    @Test
    fun `the foods catalogue covers every group and has a simple first food for each allergen`() {
        assertTrue(content.foods.size >= 100, "${content.foods.size} foods")
        FoodGroup.entries.filter { it != FoodGroup.OTHER }.forEach { g -> assertTrue(content.foods.count { it.group == g } >= 8, "$g") }
        Allergen.entries.forEach { a ->
            assertTrue(content.foods.any { it.allergens == listOf(a) && it.fromMonths <= 6 }, "starter food for $a")
        }
        assertTrue(content.foods.count { it.ironRich } >= 10)
    }

    @Test
    fun `guides run from birth to 5 with ticks that have unique ids`() {
        assertTrue(content.guides.size >= 10)
        assertTrue(content.guides.any { it.feature == GuideFeature.FOODS })
        val itemIds = content.guides.flatMap { g -> g.checklist.map { it.id } }
        assertEquals(itemIds.size, itemIds.toSet().size)
    }

    @Test
    fun `validator reports foods and guides that break the rules`() {
        val food = content.foods.first()
        val guide = content.guides.first()
        val broken = content.copy(
            foods = listOf(food.copy(id = "broccoli", sources = emptyList()), food.copy(serving = emptyList())),
            guides = listOf(guide.copy(fromMonths = 12, toMonths = 6, checklist = listOf(ChecklistItem("tick", "x")))),
        )
        val problems = ContentValidator.problems(broken)
        assertTrue(problems.any { "broccoli should start with food." in it })
        assertTrue(problems.any { "broccoli cites no source" in it })
        assertTrue(problems.any { "has no serving advice" in it })
        assertTrue(problems.any { "must start before it ends" in it })
        assertTrue(problems.any { "tick should start with check." in it })
    }

    @Test
    fun `every guide has an at-a-glance summary, a checked date and working cross-links`() {
        content.guides.forEach { g ->
            assertTrue(g.keyPoints.isNotEmpty(), "${g.id} key points")
            assertTrue(g.checkedOn != null, "${g.id} checked date")
        }
        assertTrue(content.guides.sumOf { it.links.size } >= 25)
        assertTrue(content.guides.flatMap { it.links }.any { it.kind == LinkKind.VIDEO })
        assertTrue(content.guide("guide.safer_sleep") != null && content.guide("guide.urgent_help") != null)
    }

    @Test
    fun `validator reports insecure links and unknown related guides`() {
        val g = content.guides.first()
        val broken = content.copy(guides = listOf(g.copy(
            links = listOf(GuideLink("Old page", "http://example.org", "Example", LinkKind.ARTICLE)),
            related = listOf("guide.nope"),
        )))
        val problems = ContentValidator.problems(broken)
        assertTrue(problems.any { "isn't https" in it }, problems.toString())
        assertTrue(problems.any { "unknown guide guide.nope" in it }, problems.toString())
    }

    @Test
    fun `validator reports names that find two foods once plurals are ignored`() {
        val peas = content.food("food.peas")!!
        val broken = content.copy(foods = content.foods + peas.copy(id = "food.pea", name = "Pea", aliases = emptyList()))
        assertTrue(ContentValidator.problems(broken).any { "'pea' names more than one food" in it })
    }

    @Test
    fun `content versions compare numerically`() {
        assertTrue(ContentVersions.newer("2026.10.10", "2026.10.9"))
        assertEquals(false, ContentVersions.newer("2026.10.1", "2026.10.1"))
        assertEquals(false, ContentVersions.newer("2026.9.30", "2026.10.1"))
    }
}
