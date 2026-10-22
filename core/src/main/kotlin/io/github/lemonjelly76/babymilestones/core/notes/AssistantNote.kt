package io.github.lemonjelly76.babymilestones.core.notes

import io.github.lemonjelly76.babymilestones.core.json.Timestamp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class NoteKind(val label: String) {
    @SerialName("check_in") CHECK_IN("Check-in"),
    @SerialName("review_prep") REVIEW_PREP("Review preparation"),
    @SerialName("answer") ANSWER("Answer"),
    @SerialName("plan") PLAN("Plan or ideas"),
    @SerialName("other") OTHER("Note"),
}

/** Something Claude wrote that the family wants to keep, saved over MCP and shown in the app. */
@Serializable
data class AssistantNote(
    val id: String,
    val babyId: String,
    val kind: NoteKind,
    val title: String,
    val body: String,
    val createdBy: String? = null,
    val createdAt: Timestamp,
) {
    companion object {
        const val MAX_TITLE = 120
        const val MAX_BODY = 20_000
    }
}
