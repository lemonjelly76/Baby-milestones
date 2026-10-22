package io.github.lemonjelly76.babymilestones.core.safety

import io.github.lemonjelly76.babymilestones.core.Fixtures
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SafetyCheckTest {

    private val check = SafetyCheck(Fixtures.content.safety)

    @Test
    fun `acute symptoms stop the assistant with the urgent card`() {
        val result = check.classify("He's got a temperature of 38.5 and seems really floppy")
        assertEquals(SafetyKind.URGENT, result.kind)
        assertTrue(result.stopsAssistant)
        assertTrue("999" in result.card!!.signpost)
    }

    @Test
    fun `curly apostrophes and capitals still match`() {
        assertEquals(SafetyKind.URGENT, check.classify("Rash that DOESN’T FADE under a glass").kind)
    }

    @Test
    fun `distress gets the supportive card`() {
        val result = check.classify("Honestly I can't cope any more")
        assertEquals(SafetyKind.DISTRESS, result.kind)
        assertTrue(result.stopsAssistant)
    }

    @Test
    fun `medicine questions are signposted to a pharmacist without stopping the assistant`() {
        val result = check.classify("How much Calpol can I give for teething?")
        assertEquals(SafetyKind.MEDICATION, result.kind)
        assertEquals(false, result.stopsAssistant)
    }

    @Test
    fun `ordinary development questions pass`() {
        assertEquals(SafetyKind.NONE, check.classify("Is it normal he's not crawling at 9 months?").kind)
        assertEquals(SafetyKind.NONE, check.classify("She fits into 12 month clothes and loves ice cream").kind)
    }
}
