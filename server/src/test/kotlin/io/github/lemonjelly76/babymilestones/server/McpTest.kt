package io.github.lemonjelly76.babymilestones.server

import io.github.lemonjelly76.babymilestones.core.json.BabyJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class McpTest : IntegrationTest() {

    private var nextId = 1

    private fun rpc(token: String?, session: String?, method: String, params: String = "{}", notification: Boolean = false): Response {
        val id = if (notification) "" else "\"id\":${nextId++},"
        val headers = buildMap {
            put("Accept", "application/json, text/event-stream")
            session?.let { put("Mcp-Session-Id", it) }
        }
        return request("POST", "/mcp", """{"jsonrpc":"2.0",$id"method":"$method","params":$params}""", token, headers = headers)
    }

    /** Streamable HTTP may answer with plain JSON or an SSE stream; take the JSON-RPC message either way. */
    private fun message(response: Response): JsonObject {
        val text = response.body.lines().filter { it.startsWith("data:") }.joinToString("") { it.removePrefix("data:").trim() }
            .ifEmpty { response.body }
        return BabyJson.parseToJsonElement(text).jsonObject
    }

    private fun connect(token: String): String {
        val init = rpc(token, null, "initialize",
            """{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"test","version":"1"}}""")
        assertEquals(200, init.status, init.body)
        val session = init.headers.entries.first { it.key.equals("mcp-session-id", true) }.value.first()
        rpc(token, session, "notifications/initialized", notification = true)
        return session
    }

    private fun callTool(token: String, session: String, name: String, args: String): JsonObject =
        message(rpc(token, session, "tools/call", """{"name":"$name","arguments":$args}""")).getValue("result").jsonObject

    @Test
    fun `mcp needs a token`() {
        assertEquals(401, rpc(null, null, "initialize").status)
    }

    @Test
    fun `lists the spec's tools and answers pending updates for the family's baby`() {
        val token = newFamily()
        val babyId = newBaby(token, dob = "2026-04-21", due = "2026-05-19")
        val session = connect(token)

        val tools = message(rpc(token, session, "tools/list")).getValue("result").jsonObject.getValue("tools").jsonArray
            .map { it.jsonObject.getValue("name").jsonPrimitive.content }.toSet()
        assertTrue(tools.containsAll(listOf(
            "list_babies", "get_baby_status", "get_pending_updates", "list_milestones", "get_milestone", "get_period_guide",
            "get_whats_next", "get_concerns", "get_timeline", "get_schedule", "search_guidance", "log_milestone",
            "update_milestone_log", "remove_milestone_log", "mark_not_sure", "add_moment", "update_appointment",
            "record_vaccination", "add_review_note", "get_foods", "get_food", "get_guides", "list_notes", "log_food",
            "remove_food_tasting", "update_checklist", "save_note",
        )), tools.toString())

        val pending = callTool(token, session, "get_pending_updates", """{"baby_id":"$babyId"}""")
        val text = pending.getValue("content").jsonArray.first().jsonObject.getValue("text").jsonPrimitive.content
        val json = BabyJson.parseToJsonElement(text).jsonObject
        assertEquals(babyId, json.getValue("baby_id").jsonPrimitive.content)
        assertTrue("content_version" in json)
    }

    @Test
    fun `tool calls cannot reach another family's baby`() {
        val babyId = newBaby(newFamily("A"))
        val other = newFamily("B")
        val result = callTool(other, connect(other), "get_baby_status", """{"baby_id":"$babyId"}""")
        assertEquals("true", result.getValue("isError").jsonPrimitive.content)
    }

    @Test
    fun `writes over mcp are logged with the assistant as source`() {
        val token = newFamily()
        val babyId = newBaby(token)
        val session = connect(token)
        callTool(token, session, "log_milestone", """{"baby_id":"$babyId","milestone_id":"social.laughs","achieved_on":"2026-09-01"}""")
        val source = jdbc.sql("select source from milestone_log where baby_id = :id").param("id", babyId).query(String::class.java).single()
        assertEquals("assistant", source)
    }

    @Test
    fun `prompts and the summary resource are available`() {
        val token = newFamily()
        val babyId = newBaby(token)
        val session = connect(token)
        val prompts = message(rpc(token, session, "prompts/list")).getValue("result").jsonObject.getValue("prompts").jsonArray
            .map { it.jsonObject.getValue("name").jsonPrimitive.content }
        assertEquals(setOf("weekly_checkin", "prepare_for_review", "explain_milestone", "meal_ideas"), prompts.toSet())

        val summary = message(rpc(token, session, "resources/read", """{"uri":"baby://$babyId/summary"}"""))
        val text = summary.getValue("result").jsonObject.getValue("contents").jsonArray.first().jsonObject.getValue("text").jsonPrimitive.content
        assertTrue("Development summary for Ava" in text, text)
    }

    private fun text(result: JsonObject) = result.getValue("content").jsonArray.first().jsonObject.getValue("text").jsonPrimitive.content

    @Test
    fun `claude logs foods by name and saves its outputs for the app`() {
        val token = newFamily()
        val babyId = newBaby(token)
        val session = connect(token)

        val logged = callTool(token, session, "log_food", """{"baby_id":"$babyId","food":"eggs","liking":"liked"}""")
        assertEquals("food.egg", BabyJson.parseToJsonElement(text(logged)).jsonObject.getValue("food_id").jsonPrimitive.content)
        callTool(token, session, "log_food", """{"baby_id":"$babyId","food":"Gran's lentil soup","group":"protein"}""")

        val foods = BabyJson.parseToJsonElement(text(callTool(token, session, "get_foods", """{"baby_id":"$babyId"}"""))).jsonObject
        assertEquals(2, foods.getValue("tried").jsonPrimitive.content.toInt())
        assertTrue(foods.getValue("suggestions").jsonArray.isNotEmpty())

        val saved = callTool(token, session, "save_note",
            """{"baby_id":"$babyId","kind":"check_in","title":"Check-in 22 October","body":"- Rolling both ways\n- Try tummy time"}""")
        assertTrue("isError" !in saved || saved.getValue("isError").jsonPrimitive.content == "false", saved.toString())
        val notes = text(callTool(token, session, "list_notes", """{"baby_id":"$babyId"}"""))
        assertTrue("Check-in 22 October" in notes, notes)

        val data = get("/api/babies/$babyId/data", token).json.jsonObject
        val note = data.getValue("assistant_notes").jsonArray.single().jsonObject
        assertEquals("check_in", note.getValue("kind").jsonPrimitive.content)
        assertEquals(204, request("DELETE", "/api/babies/$babyId/notes/${note.getValue("id").jsonPrimitive.content}", token = token).status)
    }

    @Test
    fun `guides come with this baby's checklist ticks`() {
        val token = newFamily()
        val babyId = newBaby(token)
        val session = connect(token)
        callTool(token, session, "update_checklist", """{"baby_id":"$babyId","item_id":"check.ready_sits","done":true}""")
        val guides = text(callTool(token, session, "get_guides", """{"baby_id":"$babyId"}"""))
        assertTrue("guide.baby_led_weaning" in guides, guides)
        val one = BabyJson.parseToJsonElement(text(callTool(token, session, "get_guides",
            """{"baby_id":"$babyId","guide_id":"guide.ready_for_solids"}"""))).jsonObject
        val items = one.getValue("checklist").jsonArray.map { it.jsonObject }
        assertEquals("true", items.first { it.getValue("id").jsonPrimitive.content == "check.ready_sits" }.getValue("done").jsonPrimitive.content)
    }
}
