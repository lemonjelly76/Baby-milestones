package io.github.lemonjelly76.babymilestones.server

import io.github.lemonjelly76.babymilestones.core.api.ChatMessage
import io.github.lemonjelly76.babymilestones.server.assistant.AssistantModel
import io.github.lemonjelly76.babymilestones.server.assistant.SystemPrompt
import io.github.lemonjelly76.babymilestones.server.assistant.ToolCall
import io.github.lemonjelly76.babymilestones.server.assistant.ToolOutcome
import io.github.lemonjelly76.babymilestones.server.tools.BabyTool
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary

/** Stands in for Claude: searches for the milestone the parent named, then proposes logging it. */
class ScriptedModel : AssistantModel {
    var calls = 0
    lateinit var lastSystem: SystemPrompt

    override val available = true

    override fun converse(system: SystemPrompt, history: List<ChatMessage>, tools: List<BabyTool>, runTool: (ToolCall) -> ToolOutcome): String {
        calls++
        lastSystem = system
        val babyId = Regex("baby_id (b_\\w+)").find(system.context)!!.groupValues[1]
        val search = runTool(ToolCall("t1", "search_guidance", JsonObject(mapOf("query" to JsonPrimitive("sits without support"), "baby_id" to JsonPrimitive(babyId)))))
        check(!search.isError) { search.content }
        val proposal = runTool(ToolCall("t2", "log_milestone", JsonObject(mapOf(
            "baby_id" to JsonPrimitive(babyId), "milestone_id" to JsonPrimitive("move.sits_without_support"),
        ))))
        val bad = runTool(ToolCall("t3", "log_milestone", JsonObject(mapOf(
            "baby_id" to JsonPrimitive(babyId), "milestone_id" to JsonPrimitive("move.invented"),
        ))))
        check(bad.isError)
        return "Lovely! Tap to confirm. ${proposal.content}"
    }
}

@TestConfiguration(proxyBeanMethods = false)
class ScriptedModelConfiguration {
    @Bean
    @Primary
    fun scriptedModel() = ScriptedModel()
}

@Import(ScriptedModelConfiguration::class)
class AssistantTest : IntegrationTest() {

    @org.springframework.beans.factory.annotation.Autowired
    lateinit var model: ScriptedModel

    private fun ask(token: String, babyId: String, text: String) =
        post("/api/assistant/messages", """{"baby_id":"$babyId","messages":[{"role":"user","text":"$text"}]}""", token)

    @Test
    fun `writes come back as proposals and are saved only when confirmed`() {
        val token = newFamily()
        val babyId = newBaby(token)

        val reply = ask(token, babyId, "She can sit without help now")
        assertEquals(200, reply.status, reply.body)
        val proposals = reply.json.jsonObject.getValue("proposals").jsonArray
        assertEquals(1, proposals.size)
        assertTrue("Sits without support" in proposals.first().jsonObject.getValue("summary").jsonPrimitive.content)
        assertEquals(0, logCount(babyId), "nothing is saved before confirmation")
        assertTrue("Ava" in model.lastSystem.context)

        val confirm = post("/api/assistant/confirm", """{"proposals":$proposals}""", token)
        assertEquals(200, confirm.status, confirm.body)
        assertEquals("true", confirm.json.jsonArray.single().jsonObject.getValue("ok").jsonPrimitive.content)
        assertEquals(1, logCount(babyId))
    }

    @Test
    fun `urgent symptoms get the urgent card and never reach the model`() {
        val token = newFamily()
        val babyId = newBaby(token)
        val before = model.calls
        val reply = ask(token, babyId, "He has a fever and is really floppy")
        assertEquals("urgent", reply.obj("safety").getValue("kind").jsonPrimitive.content)
        assertTrue("999" in reply.obj("safety").getValue("card").jsonObject.getValue("signpost").jsonPrimitive.content)
        assertEquals(before, model.calls)
    }

    @Test
    fun `distress gets the supportive card and never reaches the model`() {
        val token = newFamily()
        val babyId = newBaby(token)
        val before = model.calls
        assertEquals("distress", ask(token, babyId, "I just cannot cope any more").obj("safety").getValue("kind").jsonPrimitive.content)
        assertEquals(before, model.calls)
    }

    @Test
    fun `confirming a proposal for another family's baby fails`() {
        val babyId = newBaby(newFamily("A"))
        val other = newFamily("B")
        val forged = """{"proposals":[{"id":"p_x","tool":"log_milestone","input":{"baby_id":"$babyId","milestone_id":"social.laughs"},"summary":"x"}]}"""
        val result = post("/api/assistant/confirm", forged, other)
        assertEquals("false", result.json.jsonArray.single().jsonObject.getValue("ok").jsonPrimitive.content)
        assertEquals(0, logCount(babyId))
    }

    private fun logCount(babyId: String) =
        jdbc.sql("select count(*) from milestone_log where baby_id = :id").param("id", babyId).query(Int::class.java).single()
}
