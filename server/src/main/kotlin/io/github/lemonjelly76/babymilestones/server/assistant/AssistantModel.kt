package io.github.lemonjelly76.babymilestones.server.assistant

import io.github.lemonjelly76.babymilestones.server.tools.BabyTool
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ChatMessage(val role: Role, val text: String) {
    @Serializable
    enum class Role {
        @SerialName("user") USER,
        @SerialName("assistant") ASSISTANT,
    }
}

data class ToolCall(val id: String, val name: String, val input: JsonObject)

data class ToolOutcome(val content: String, val isError: Boolean = false)

/** The system prompt in two parts: [stable] is identical on every request and cached; [context] is about today's baby. */
data class SystemPrompt(val stable: String, val context: String)

/** A model that answers a conversation, calling tools through [runTool] until it has a reply. */
interface AssistantModel {
    val available: Boolean

    fun converse(system: SystemPrompt, history: List<ChatMessage>, tools: List<BabyTool>, runTool: (ToolCall) -> ToolOutcome): String
}
