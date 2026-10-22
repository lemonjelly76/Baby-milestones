package io.github.lemonjelly76.babymilestones.server.assistant

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.Tool
import com.anthropic.models.messages.ToolResultBlockParam
import com.anthropic.models.messages.ToolUnion
import io.github.lemonjelly76.babymilestones.core.api.ChatMessage
import io.github.lemonjelly76.babymilestones.server.tools.BabyTool
import io.github.lemonjelly76.babymilestones.server.tools.PlainJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * The Claude API behind the in-app assistant. The API key stays on the server. Tool definitions and the stable system
 * prompt are cached; the loop appends each response unchanged (thinking blocks included) so the history stays valid.
 */
@Component
class AnthropicAssistantModel(
    @param:Value("\${anthropic.api-key:}") private val apiKey: String,
    @param:Value("\${anthropic.model:claude-opus-5-5}") private val model: String,
    @param:Value("\${anthropic.max-tool-rounds:8}") private val maxToolRounds: Int,
) : AssistantModel {

    private val log = LoggerFactory.getLogger(javaClass)
    private val client: AnthropicClient? by lazy {
        if (apiKey.isBlank()) null else AnthropicOkHttpClient.builder().apiKey(apiKey).build()
    }

    override val available: Boolean get() = apiKey.isNotBlank()

    override fun converse(
        system: SystemPrompt,
        history: List<ChatMessage>,
        tools: List<BabyTool>,
        runTool: (ToolCall) -> ToolOutcome,
    ): String {
        val client = client ?: return "The assistant isn't set up on this server yet."
        val messages = history.map { message ->
            MessageParam.builder()
                .role(if (message.role == ChatMessage.Role.USER) MessageParam.Role.USER else MessageParam.Role.ASSISTANT)
                .content(message.text)
                .build()
        }.toMutableList()
        val toolParams = tools.mapIndexed { i, tool -> toolParam(tool, cache = i == tools.lastIndex) }

        repeat(maxToolRounds) {
            val params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                // Chat on a phone: short answers matter more than deep deliberation.
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                .systemOfTextBlockParams(listOf(
                    TextBlockParam.builder().text(system.stable).cacheControl(CacheControlEphemeral.builder().build()).build(),
                    TextBlockParam.builder().text(system.context).build(),
                ))
                .tools(toolParams.map(ToolUnion::ofTool))
                .messages(messages)
                // If a safety classifier declines, the server retries on a fallback model it picks.
                .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .build()

            val response = try {
                client.messages().create(params)
            } catch (e: AnthropicServiceException) {
                log.warn("Assistant call failed: {} {}", e.statusCode(), e.errorType().orElse(null))
                return "Sorry, I couldn't answer just now. Please try again in a moment."
            }
            messages += response.toParam()

            val stop = response.stopReason().orElse(null)
            if (stop == StopReason.REFUSAL) {
                return "Sorry, I can't help with that one. Your health visitor or GP can."
            }
            val toolUses = response.content().mapNotNull { it.toolUse().orElse(null) }
            if (stop != StopReason.TOOL_USE || toolUses.isEmpty()) {
                return response.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("\n").trim()
                    .ifEmpty { "Sorry, I don't have an answer for that." }
            }
            val results = toolUses.map { use ->
                val input = PlainJson.fromPlain(use._input().convert(Map::class.java)) as JsonObject
                val outcome = runTool(ToolCall(use.id(), use.name(), input))
                ContentBlockParam.ofToolResult(
                    ToolResultBlockParam.builder().toolUseId(use.id()).content(outcome.content).isError(outcome.isError).build(),
                )
            }
            messages += MessageParam.builder().role(MessageParam.Role.USER).contentOfBlockParams(results).build()
        }
        return "Sorry, that took too many steps. Could you ask in a simpler way?"
    }

    private fun toolParam(tool: BabyTool, cache: Boolean): Tool {
        val properties = Tool.InputSchema.Properties.builder()
        tool.inputSchema["properties"]?.jsonObject?.forEach { (name, schema) ->
            properties.putAdditionalProperty(name, JsonValue.from(PlainJson.toPlain(schema)))
        }
        val builder = Tool.builder()
            .name(tool.name)
            .description(tool.description + if (tool.write) " This only proposes the change: the parent confirms it in the app." else "")
            .inputSchema(
                Tool.InputSchema.builder()
                    .properties(properties.build())
                    .required(tool.inputSchema["required"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList())
                    .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                    .build(),
            )
        if (cache) builder.cacheControl(CacheControlEphemeral.builder().build())
        return builder.build()
    }
}
