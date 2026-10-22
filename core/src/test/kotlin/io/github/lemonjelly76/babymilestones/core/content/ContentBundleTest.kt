package io.github.lemonjelly76.babymilestones.core.content

import io.github.lemonjelly76.babymilestones.core.Fixtures
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
    fun `content versions compare numerically`() {
        assertTrue(ContentVersions.newer("2026.10.10", "2026.10.9"))
        assertEquals(false, ContentVersions.newer("2026.10.1", "2026.10.1"))
        assertEquals(false, ContentVersions.newer("2026.9.30", "2026.10.1"))
    }
}
