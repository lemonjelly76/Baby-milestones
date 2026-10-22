package io.github.lemonjelly76.babymilestones.core.guidance

import io.github.lemonjelly76.babymilestones.core.content.ContentBundle
import kotlinx.serialization.Serializable
import kotlin.math.abs

@Serializable
data class GuidancePassage(
    val kind: String,
    val id: String,
    val title: String,
    val text: String,
    val score: Double,
    val sources: List<String>,
)

/**
 * Keyword search over milestone and period content, so the assistant can map a parent's words ("sits on her own")
 * to milestone ids. Results near the baby's age rank higher; passages far beyond it are left out.
 */
class GuidanceSearch(private val content: ContentBundle) {

    private val stopWords = setOf(
        "a", "an", "and", "are", "as", "at", "be", "by", "can", "do", "does", "for", "from", "has", "have", "he", "her",
        "his", "how", "i", "if", "in", "is", "it", "its", "my", "no", "not", "of", "on", "or", "our", "she", "so", "that",
        "the", "their", "them", "they", "this", "to", "up", "was", "we", "what", "when", "with", "yet", "you", "your",
        "baby", "now", "just", "still", "own", "yesterday", "today", "started", "starting",
    )

    fun search(query: String, ageMonths: Int? = null, limit: Int = 8): List<GuidancePassage> {
        val terms = tokens(query)
        if (terms.isEmpty()) return emptyList()

        val milestones = content.milestones.mapNotNull { m ->
            if (ageMonths != null && m.emergingFrom > ageMonths + 6) return@mapNotNull null
            val score = score(terms, m.title, 3.0) + score(terms, m.description, 1.0) +
                score(terms, m.activities.joinToString(" "), 0.5)
            if (score == 0.0) return@mapNotNull null
            val agePenalty = ageMonths?.let { 1.0 + abs(m.expectedBy - ageMonths) / 12.0 } ?: 1.0
            GuidancePassage("milestone", m.id, m.title, m.description, score / agePenalty, m.sources)
        }
        val periods = content.periods.flatMap { p ->
            listOf(
                "typical" to p.typical, "sleep" to p.sleep, "feeding" to p.feeding,
                "play" to p.play.joinToString(" "), "safety" to p.safety.joinToString(" "),
            ).mapNotNull { (section, text) ->
                if (ageMonths != null && p.fromMonths > ageMonths + 6) return@mapNotNull null
                val score = score(terms, text, 1.0) + score(terms, section, 2.0)
                if (score == 0.0) return@mapNotNull null
                val agePenalty = ageMonths?.let { if (ageMonths in p.fromMonths until p.toMonths) 1.0 else 2.0 } ?: 1.0
                GuidancePassage("period_$section", p.id, "${p.name}: $section", text, score / agePenalty, p.sources)
            }
        }
        return (milestones + periods).sortedByDescending { it.score }.take(limit)
    }

    private fun score(terms: Set<String>, text: String, weight: Double): Double {
        val words = tokens(text)
        return terms.count { term -> words.any { it.startsWith(term) || term.startsWith(it) && it.length >= 4 } } * weight
    }

    private fun tokens(text: String): Set<String> = text.lowercase()
        .split(Regex("[^a-z0-9]+"))
        .filter { it.length > 1 && it !in stopWords }
        .map { stem(it) }
        .toSet()

    private fun stem(word: String): String = when {
        word.length > 5 && word.endsWith("ing") -> word.dropLast(3)
        word.length > 4 && word.endsWith("es") -> word.dropLast(2)
        word.length > 3 && word.endsWith("s") && !word.endsWith("ss") -> word.dropLast(1)
        word.length > 4 && word.endsWith("ed") -> word.dropLast(2)
        else -> word
    }
}
