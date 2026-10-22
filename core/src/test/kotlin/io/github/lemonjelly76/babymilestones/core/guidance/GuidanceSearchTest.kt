package io.github.lemonjelly76.babymilestones.core.guidance

import io.github.lemonjelly76.babymilestones.core.Fixtures
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GuidanceSearchTest {

    private val search = GuidanceSearch(Fixtures.content)

    @Test
    fun `maps a parent's words to the milestone`() {
        assertEquals("move.sits_without_support", search.search("can sit without help now", ageMonths = 8).first().id)
        assertEquals("lang.calls_parent_name", search.search("she said dada yesterday", ageMonths = 11).first().id)
        assertEquals("move.rolls_tummy_to_back", search.search("rolled from tummy onto back", ageMonths = 5).first().id)
    }

    @Test
    fun `finds period guidance on sleep and feeding`() {
        val results = search.search("how much sleep", ageMonths = 5)
        assertTrue(results.any { it.kind == "period_sleep" && it.id == "infant_4_6m" })
    }

    @Test
    fun `leaves out milestones far beyond the baby's age`() {
        assertTrue(search.search("hops on one foot", ageMonths = 6).none { it.id == "move.hops" })
    }

    @Test
    fun `empty queries return nothing`() {
        assertEquals(emptyList(), search.search("the and is"))
    }
}
