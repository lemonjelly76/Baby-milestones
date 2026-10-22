package io.github.lemonjelly76.babymilestones.server.mcp

import io.github.lemonjelly76.babymilestones.core.content.ContentBundle
import io.github.lemonjelly76.babymilestones.core.content.Reference
import io.github.lemonjelly76.babymilestones.core.json.BabyJson
import io.github.lemonjelly76.babymilestones.core.tracking.LogSource
import io.github.lemonjelly76.babymilestones.server.security.CarerPrincipal
import io.github.lemonjelly76.babymilestones.server.service.BabyService
import io.github.lemonjelly76.babymilestones.server.tools.Args
import io.github.lemonjelly76.babymilestones.server.tools.BabyTool
import io.github.lemonjelly76.babymilestones.server.tools.PlainJson
import io.github.lemonjelly76.babymilestones.server.tools.MilestoneDetail
import io.github.lemonjelly76.babymilestones.server.tools.PeriodGuideView
import io.github.lemonjelly76.babymilestones.server.tools.ToolCaller
import io.github.lemonjelly76.babymilestones.server.tools.ToolCatalog
import io.github.lemonjelly76.babymilestones.server.tools.ToolInputException
import io.github.lemonjelly76.babymilestones.server.tools.ToolViews
import io.modelcontextprotocol.common.McpTransportContext
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper
import io.modelcontextprotocol.server.McpServerFeatures.SyncPromptSpecification
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceTemplateSpecification
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification
import io.modelcontextprotocol.server.McpSyncServerExchange
import io.modelcontextprotocol.spec.McpSchema
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.core.Authentication
import tools.jackson.databind.json.JsonMapper

/**
 * Serves the tool catalog over MCP (Streamable HTTP at /mcp). Bearer-token auth runs before the transport, and the
 * transport copies the signed-in carer into each call's context, so every tool is scoped to that carer's family.
 */
@Configuration
class McpConfiguration {

    companion object {
        const val CARER = "carer"

        fun caller(exchange: McpSyncServerExchange): ToolCaller {
            val carer = exchange.transportContext().get(CARER) as? CarerPrincipal
                ?: throw IllegalStateException("Not signed in")
            return ToolCaller(carer.familyId, carer.carerId, LogSource.ASSISTANT, carer.canEdit)
        }
    }

    @Bean
    fun webMvcStreamableServerTransportProvider(
        jsonMapper: JsonMapper,
        properties: McpServerStreamableHttpProperties,
    ): WebMvcStreamableServerTransportProvider = WebMvcStreamableServerTransportProvider.builder()
        .jsonMapper(JacksonMcpJsonMapper(jsonMapper))
        .mcpEndpoint(properties.mcpEndpoint)
        .keepAliveInterval(properties.keepAliveInterval)
        .disallowDelete(properties.isDisallowDelete)
        .contextExtractor { request ->
            val carer = (request.servletRequest().userPrincipal as? Authentication)?.principal as? CarerPrincipal
            if (carer == null) McpTransportContext.EMPTY else McpTransportContext.create(mapOf(CARER to carer))
        }
        .build()

    @Bean
    fun babyToolSpecifications(catalog: ToolCatalog): List<SyncToolSpecification> = catalog.tools.map(::specification)

    private fun specification(tool: BabyTool): SyncToolSpecification = SyncToolSpecification.builder()
        .tool(
            McpSchema.Tool.builder()
                .name(tool.name)
                .title(tool.title)
                .description(tool.description + if (tool.write) " Confirm with the parent before calling this." else "")
                .inputSchema(@Suppress("UNCHECKED_CAST") (PlainJson.toPlain(tool.inputSchema) as Map<String, Any>))
                .annotations(McpSchema.ToolAnnotations(tool.title, !tool.write, tool.destructive, !tool.write, false, null))
                .build(),
        )
        .callHandler { exchange, request ->
            try {
                val args = Args(PlainJson.fromPlain(request.arguments().orEmpty()) as JsonObject)
                val caller = caller(exchange)
                if (tool.write && !caller.canEdit) throw ToolInputException("This connector has view-only access, so it can't make changes")
                val result = tool.handler(caller, args)
                McpSchema.CallToolResult.builder().addTextContent(result.toString()).build()
            } catch (e: RuntimeException) {
                McpSchema.CallToolResult.builder().addTextContent(e.message ?: "Something went wrong").isError(true).build()
            }
        }
        .build()

    @Bean
    fun babyResources(content: ContentBundle): List<SyncResourceSpecification> = listOf(
        SyncResourceSpecification(
            McpSchema.Resource.builder().uri("content://version").name("content_version")
                .description("The milestone content version and its sources").mimeType("application/json").build(),
        ) { _, request ->
            text(request.uri(), JsonObject(mapOf(
                "version" to JsonPrimitive(content.version),
                "references" to BabyJson.encodeToJsonElement(
                    ListSerializer(Reference.serializer()),
                    content.references,
                ),
            )).toString())
        },
    )

    @Bean
    fun babyResourceTemplates(content: ContentBundle, babies: BabyService): List<SyncResourceTemplateSpecification> = listOf(
        template("baby://{id}/summary", "baby_summary", "The health visitor summary for a baby, as plain text", "text/plain") { exchange, uri ->
            val babyId = uri.removePrefix("baby://").removeSuffix("/summary")
            babies.context(caller(exchange).familyId, babyId).summary.toPlainText()
        },
        template("content://milestones/{id}", "milestone", "One milestone's full content", "application/json") { _, uri ->
            val m = content.milestone(uri.removePrefix("content://milestones/")) ?: throw IllegalArgumentException("Unknown milestone")
            BabyJson.encodeToString(MilestoneDetail.serializer(), ToolViews.detail(content, m))
        },
        template("content://periods/{id}", "period", "One period guide", "application/json") { _, uri ->
            val p = content.period(uri.removePrefix("content://periods/")) ?: throw IllegalArgumentException("Unknown period")
            BabyJson.encodeToString(PeriodGuideView.serializer(), ToolViews.period(content, p, null))
        },
    )

    @Bean
    fun babyPrompts(): List<SyncPromptSpecification> = listOf(
        prompt(
            "weekly_checkin", "Walk through what needs updating for a baby this week",
            listOf(McpSchema.PromptArgument("baby_id", "Id from list_babies", true)),
        ) { args ->
            "Run a weekly check-in for baby ${args["baby_id"]}. Call get_pending_updates. Ask me about each Due now item " +
                "in plain words using the baby's name, one or two at a time. When I answer yes, log it with log_milestone " +
                "(after I confirm); 'not sure' becomes mark_not_sure. Then ask about any Not sure re-checks and appointments " +
                "to mark. If they're eating solid food, ask what new foods they tried this week and log each with log_food, then " +
                "mention anything get_foods says is due again. Ask about new words, teeth and any recent weigh-in, and log them " +
                "with log_word, log_tooth and log_growth. Finish with get_whats_next: two or three things to watch for " +
                "and one activity. Then save a short summary of the check-in with save_note (kind check_in) so it's in the app."
        },
        prompt(
            "prepare_for_review", "Build the health visitor summary before a review",
            listOf(McpSchema.PromptArgument("baby_id", "Id from list_babies", true)),
        ) { args ->
            "Help me prepare for a health visitor review for baby ${args["baby_id"]}. Read the baby://${args["baby_id"]}/summary " +
                "resource, then get_concerns. Summarise what's been achieved and anything not seen yet, ask whether I have " +
                "questions to add, and add each with add_review_note once I confirm. Finally save the summary you wrote with " +
                "save_note (kind review_prep)."
        },
        prompt(
            "explain_milestone", "What a milestone is, why it matters and how to encourage it",
            listOf(
                McpSchema.PromptArgument("milestone_id", "Milestone id", true),
                McpSchema.PromptArgument("baby_id", "Id from list_babies, to relate it to their age", false),
            ),
        ) { args ->
            "Explain milestone ${args["milestone_id"]} using get_milestone" +
                (args["baby_id"]?.let { " and relate it to baby $it with list_milestones" } ?: "") +
                ": what it looks like, why it matters, the typical age range, and two activities to encourage it. Cite the sources."
        },
        prompt(
            "meal_ideas", "Meal ideas for the next few days from the foods tracker",
            listOf(
                McpSchema.PromptArgument("baby_id", "Id from list_babies", true),
                McpSchema.PromptArgument("days", "How many days to plan; default 3", false),
            ),
        ) { args ->
            "Suggest simple family meals for baby ${args["baby_id"]} for the next ${args["days"] ?: 3} days. Start with " +
                "get_foods and get_guides (guide.baby_led_weaning and guide.foods_to_avoid). Follow the app's rules: an " +
                "iron-rich food at each meal, at most one new allergen at a time, foods worth offering again, nothing on the " +
                "foods-to-avoid list and nothing the baby is too young for, prepared as get_food says for their age. Keep it " +
                "short and practical, then offer to save it with save_note (kind plan)."
        },
    )

    private fun template(
        uriTemplate: String, name: String, description: String, mimeType: String,
        read: (McpSyncServerExchange, String) -> String,
    ) = SyncResourceTemplateSpecification(
        McpSchema.ResourceTemplate.builder().uriTemplate(uriTemplate).name(name).description(description).mimeType(mimeType).build(),
    ) { exchange, request -> text(request.uri(), read(exchange, request.uri()), mimeType) }

    private fun text(uri: String, body: String, mimeType: String = "application/json") =
        McpSchema.ReadResourceResult(listOf(McpSchema.TextResourceContents(uri, mimeType, body)))

    private fun prompt(
        name: String, description: String, arguments: List<McpSchema.PromptArgument>, text: (Map<String, Any?>) -> String,
    ) = SyncPromptSpecification(McpSchema.Prompt(name, description, arguments)) { _, request ->
        McpSchema.GetPromptResult(description, listOf(
            McpSchema.PromptMessage(McpSchema.Role.USER, McpSchema.TextContent(text(request.arguments().orEmpty()))),
        ))
    }

}
