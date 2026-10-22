package io.github.lemonjelly76.babymilestones.core.safety

import io.github.lemonjelly76.babymilestones.core.content.SafetyContent
import io.github.lemonjelly76.babymilestones.core.content.SignpostCopy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SafetyKind {
    @SerialName("urgent") URGENT,
    @SerialName("distress") DISTRESS,
    @SerialName("medication") MEDICATION,
    @SerialName("none") NONE,
}

@Serializable
data class SafetyResult(val kind: SafetyKind, val matched: List<String> = emptyList(), val card: SignpostCopy? = null) {
    /** Urgent symptoms and distress stop the normal assistant flow; medication questions get the card alongside it. */
    val stopsAssistant: Boolean get() = kind == SafetyKind.URGENT || kind == SafetyKind.DISTRESS
}

/**
 * A fixed phrase check that runs in app code before any model call. It errs towards showing help:
 * a false positive costs one card, a false negative could cost much more.
 */
class SafetyCheck(private val content: SafetyContent) {

    fun classify(message: String): SafetyResult {
        val text = normalise(message)
        matches(text, content.urgentPhrases).takeIf { it.isNotEmpty() }
            ?.let { return SafetyResult(SafetyKind.URGENT, it, content.urgentCard) }
        matches(text, content.distressPhrases).takeIf { it.isNotEmpty() }
            ?.let { return SafetyResult(SafetyKind.DISTRESS, it, content.distressCard) }
        matches(text, content.medicationPhrases).takeIf { it.isNotEmpty() }
            ?.let { return SafetyResult(SafetyKind.MEDICATION, it, content.medicationCard) }
        return SafetyResult(SafetyKind.NONE)
    }

    private fun matches(text: String, phrases: List<String>): List<String> =
        phrases.filter { phrase -> Regex("\\b${Regex.escape(normalise(phrase))}\\b").containsMatchIn(text) }

    private fun normalise(text: String) = text.lowercase()
        .replace('’', '\'')
        .replace(Regex("[^a-z0-9'°.\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
