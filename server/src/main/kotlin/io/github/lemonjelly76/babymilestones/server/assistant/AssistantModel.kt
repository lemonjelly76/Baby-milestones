package io.github.lemonjelly76.babymilestones.server.assistant

import io.github.lemonjelly76.babymilestones.core.api.ChatMessage
import io.github.lemonjelly76.babymilestones.server.tools.BabyTool
import kotlinx.serialization.json.JsonObject

data class ToolCall(val id: String, val name: String, val input: JsonObject)

data class ToolOutcome(val content: String, val isError: Boolean = false)

/** The system prompt in two parts: [stable] is identical on every request and cached; [context] is about today's baby. */
data class SystemPrompt(val stable: String, val context: String)

/** A model that answers a conversation, calling tools through [runTool] until it has a reply. */
interface AssistantModel {
    val available: Boolean

    fun converse(system: SystemPrompt, history: List<ChatMessage>, tools: List<BabyTool>, runTool: (ToolCall) -> ToolOutcome): String
}
