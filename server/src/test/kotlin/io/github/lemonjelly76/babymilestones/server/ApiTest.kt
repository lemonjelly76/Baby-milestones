package io.github.lemonjelly76.babymilestones.server

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiTest : IntegrationTest() {

    @Test
    fun `creating a family needs the setup code`() {
        val response = post("/api/families", """{"family_name":"X","carer_name":"Y","setup_code":"wrong"}""")
        assertEquals(403, response.status)
    }

    @Test
    fun `requests without a valid token are refused`() {
        assertEquals(401, get("/api/me").status)
        assertEquals(401, get("/api/me", token = "not-a-token").status)
    }

    @Test
    fun `a parent adds a baby, logs milestones and gets a summary`() {
        val token = newFamily()
        val babyId = newBaby(token, due = "2026-05-19")

        val log = post("/api/babies/$babyId/logs", """{"milestone_id":"move.rolls_tummy_to_back","achieved_on":"2026-10-01","note":"On the play mat"}""", token)
        assertEquals(201, log.status, log.body)
        assertEquals("manual", log["source"])

        val backfill = post("/api/babies/$babyId/logs/backfill", """{"entries":[{"milestone_id":"social.smiles_back"},{"milestone_id":"lang.coos"}]}""", token)
        assertEquals(200, backfill.status, backfill.body)
        assertTrue(backfill.json.jsonArray.all { it.jsonObject["approximate"]!!.jsonPrimitive.content == "true" })

        val unknown = post("/api/babies/$babyId/logs", """{"milestone_id":"move.flies"}""", token)
        assertEquals(404, unknown.status)

        val data = get("/api/babies/$babyId/data", token)
        assertEquals(3, data.json.jsonObject["logs"]!!.jsonArray.size)
        assertEquals("england", data["region"])

        post("/api/babies/$babyId/review-notes", """{"text":"Is her sleep normal?"}""", token)
        val summary = get("/api/babies/$babyId/summary", token)
        assertTrue("Rolls from tummy to back" in summary["text"], summary.body)
        assertTrue("Is her sleep normal?" in summary["text"])
    }

    @Test
    fun `editing and deleting a log entry keeps history`() {
        val token = newFamily()
        val babyId = newBaby(token)
        val logId = post("/api/babies/$babyId/logs", """{"milestone_id":"social.laughs"}""", token)["id"]

        val updated = request("PATCH", "/api/babies/$babyId/logs/$logId", """{"achieved_on":"2026-09-30","approximate":true}""", token)
        assertEquals(200, updated.status, updated.body)
        assertEquals("2026-09-30", updated["achieved_on"])
        assertEquals(204, request("DELETE", "/api/babies/$babyId/logs/$logId", token = token).status)

        val actions = jdbc.sql("select action from milestone_log_history where log_id = :id order by id").param("id", logId)
            .query(String::class.java).list()
        assertEquals(listOf("created", "updated", "deleted"), actions)
    }

    @Test
    fun `an invited carer joins the family and sees the same babies`() {
        val token = newFamily(carer = "Mum")
        val babyId = newBaby(token)
        val code = post("/api/invites", "{}", token)["code"]

        val joined = post("/api/join", """{"code":"$code","name":"Grandma","role":"grandparent"}""")
        assertEquals(200, joined.status, joined.body)
        val grandma = joined["token"]
        val me = get("/api/me", grandma)
        assertEquals(babyId, me.json.jsonObject["babies"]!!.jsonArray.single().jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals(2, me.json.jsonObject["carers"]!!.jsonArray.size)

        assertEquals(403, post("/api/join", """{"code":"$code","name":"Someone"}""").status, "invite codes are single-use")
    }

    @Test
    fun `one family can never see another family's baby`() {
        val babyId = newBaby(newFamily("A"))
        val other = newFamily("B")
        assertEquals(404, get("/api/babies/$babyId/data", other).status)
        assertEquals(404, post("/api/babies/$babyId/logs", """{"milestone_id":"social.laughs"}""", other).status)
    }

    @Test
    fun `appointments and vaccinations are recorded against the schedule`() {
        val token = newFamily()
        val babyId = newBaby(token)
        assertEquals(200, request("PUT", "/api/babies/$babyId/appointments/review_6_8_weeks", """{"status":"done","date":"2026-06-10"}""", token).status)
        assertEquals(200, request("PUT", "/api/babies/$babyId/vaccinations/menb_1", """{"given_on":"2026-06-16"}""", token).status)
        assertEquals(404, request("PUT", "/api/babies/$babyId/vaccinations/nope", """{"given_on":"2026-06-16"}""", token).status)
        val data = get("/api/babies/$babyId/data", token)
        assertEquals(1, data.json.jsonObject["vaccinations"]!!.jsonArray.size)
    }

    @Test
    fun `photos are private to the family`() {
        val token = newFamily()
        val upload = request("POST", "/api/photos", "fake-jpeg-bytes", token, contentType = "image/jpeg")
        assertEquals(201, upload.status, upload.body)
        val id = upload["id"]
        assertEquals(200, get("/api/photos/$id", token).status)
        assertEquals(404, get("/api/photos/$id", newFamily("Other")).status)
    }

    @Test
    fun `content is served with its version`() {
        val token = newFamily()
        val content = get("/api/content", token)
        assertEquals(200, content.status)
        assertTrue(content.json.jsonObject["milestones"]!!.jsonArray.size > 150)
        assertEquals(content["version"], get("/api/content/version", token)["version"])
    }

    @Test
    fun `deleting the family removes everything`() {
        val token = newFamily()
        val babyId = newBaby(token)
        post("/api/babies/$babyId/logs", """{"milestone_id":"social.laughs"}""", token)
        assertEquals(204, request("DELETE", "/api/family", token = token).status)
        assertEquals(401, get("/api/me", token).status)
        val left = jdbc.sql("select count(*) from milestone_log where baby_id = :id").param("id", babyId).query(Int::class.java).single()
        assertEquals(0, left)
    }

    @Test
    fun `foods are logged from the catalogue or as the family's own, and feed the summary`() {
        val token = newFamily()
        val babyId = newBaby(token)

        val egg = post("/api/babies/$babyId/tastings", """{"food_id":"food.egg","tasted_on":"2026-10-20","liking":"liked"}""", token)
        assertEquals(201, egg.status, egg.body)
        assertEquals("Egg", egg["name"])
        assertEquals("protein", egg["group"])
        val own = post("/api/babies/$babyId/tastings", """{"name":"Gran's lentil soup","group":"protein"}""", token)
        assertEquals(201, own.status, own.body)
        assertEquals(404, post("/api/babies/$babyId/tastings", """{"food_id":"food.unicorn"}""", token).status)
        assertEquals(400, post("/api/babies/$babyId/tastings", """{"food_id":"food.egg","tasted_on":"2099-01-01"}""", token).status)
        assertEquals(400, post("/api/babies/$babyId/tastings", """{}""", token).status)

        val updated = request("PATCH", "/api/babies/$babyId/tastings/${egg["id"]}", """{"reaction":"red blotches round her mouth"}""", token)
        assertEquals(200, updated.status, updated.body)
        assertEquals("liked", updated["liking"])

        val data = get("/api/babies/$babyId/data", token)
        assertEquals(2, data.json.jsonObject["tastings"]!!.jsonArray.size)
        val summary = get("/api/babies/$babyId/summary", token)["text"]
        assertTrue("Foods tried: 2" in summary, summary)
        assertTrue("red blotches round her mouth" in summary)

        assertEquals(204, request("DELETE", "/api/babies/$babyId/tastings/${own["id"]}", token = token).status)
        assertEquals(1, get("/api/babies/$babyId/data", token).json.jsonObject["tastings"]!!.jsonArray.size)
    }

    @Test
    fun `checklist ticks are kept per baby and can be unticked`() {
        val token = newFamily()
        val babyId = newBaby(token)
        val tick = request("PUT", "/api/babies/$babyId/checklist/check.ready_sits", """{"done":true}""", token)
        assertEquals(200, tick.status, tick.body)
        assertEquals(1, get("/api/babies/$babyId/data", token).json.jsonObject["checklist"]!!.jsonArray.size)

        assertEquals(200, request("PUT", "/api/babies/$babyId/checklist/check.ready_sits", """{"done":false}""", token).status)
        assertEquals(0, get("/api/babies/$babyId/data", token).json.jsonObject["checklist"]!!.jsonArray.size)
        assertEquals(404, request("PUT", "/api/babies/$babyId/checklist/check.nope", """{"done":true}""", token).status)
    }

    @Test
    fun `another family can't log or delete foods for a baby`() {
        val token = newFamily("A")
        val babyId = newBaby(token)
        val tasting = post("/api/babies/$babyId/tastings", """{"food_id":"food.banana"}""", token)["id"]
        val other = newFamily("B")
        assertEquals(404, post("/api/babies/$babyId/tastings", """{"food_id":"food.banana"}""", other).status)
        assertEquals(404, request("DELETE", "/api/babies/$babyId/tastings/$tasting", token = other).status)
        assertEquals(404, request("PUT", "/api/babies/$babyId/checklist/check.ready_sits", """{"done":true}""", other).status)
    }

    @Test
    fun `a food typed by name is matched to the catalogue, and how it went can be cleared`() {
        val token = newFamily()
        val babyId = newBaby(token)
        val eggs = post("/api/babies/$babyId/tastings", """{"name":"Eggs","liking":"disliked"}""", token)
        assertEquals("food.egg", eggs["food_id"])

        val cleared = request("PATCH", "/api/babies/$babyId/tastings/${eggs["id"]}", """{"clear_liking":true}""", token)
        assertEquals(200, cleared.status, cleared.body)
        assertTrue("liking" !in cleared.json.jsonObject, cleared.body)
    }

    @Test
    fun `growth, first words and teeth are recorded and feed the summary`() {
        val token = newFamily()
        val babyId = post("/api/babies", """{"name":"Ava","date_of_birth":"2026-04-21","sex":"female"}""", token)["id"]

        val weighed = post("/api/babies/$babyId/growth",
            """{"measured_on":"2026-10-01","weight_kg":7.6,"length_cm":67.5,"measured_by":"Health visitor"}""", token)
        assertEquals(201, weighed.status, weighed.body)
        assertEquals(400, post("/api/babies/$babyId/growth", """{"weight_kg":76}""", token).status, "76 kg is a typo")
        assertEquals(400, post("/api/babies/$babyId/growth", """{"measured_on":"2026-10-01"}""", token).status)

        assertEquals(201, post("/api/babies/$babyId/words", """{"word":"dada","said_on":"2026-10-10"}""", token).status)
        assertEquals(400, post("/api/babies/$babyId/words", """{"word":"Dada"}""", token).status, "already logged")
        assertEquals(200, request("PUT", "/api/babies/$babyId/teeth/71", """{"came_on":"2026-10-05"}""", token).status)
        assertEquals(404, request("PUT", "/api/babies/$babyId/teeth/99", """{"came_on":"2026-10-05"}""", token).status)

        val data = get("/api/babies/$babyId/data", token).json.jsonObject
        assertEquals(1, data["growth"]!!.jsonArray.size)
        assertEquals(1, data["words"]!!.jsonArray.size)
        assertEquals(1, data["teeth"]!!.jsonArray.size)
        val summary = get("/api/babies/$babyId/summary", token)["text"]
        assertTrue("Weight 7.60 kg on 1 Oct 2026 (about the" in summary, summary)
        assertTrue("Words: 1" in summary && "Teeth: 1" in summary)

        assertEquals(204, request("DELETE", "/api/babies/$babyId/growth/${weighed["id"]}", token = token).status)
        assertEquals(204, request("DELETE", "/api/babies/$babyId/teeth/71", token = token).status)
    }

    @Test
    fun `contacts belong to the family`() {
        val token = newFamily()
        val contact = post("/api/contacts", """{"role":"health_visitor","name":"Jo Smith","phone":"01234 567890"}""", token)
        assertEquals(201, contact.status, contact.body)
        assertEquals(200, request("PUT", "/api/contacts/${contact["id"]}", """{"role":"health_visitor","name":"Jo Smith","phone":"01234 000000"}""", token).status)
        val contacts = get("/api/me", token).json.jsonObject["contacts"]!!.jsonArray
        assertEquals("01234 000000", contacts.single().jsonObject["phone"]!!.jsonPrimitive.content)
        assertEquals(404, request("DELETE", "/api/contacts/${contact["id"]}", token = newFamily("Other")).status)
        assertEquals(204, request("DELETE", "/api/contacts/${contact["id"]}", token = token).status)
    }

    @Test
    fun `a view-only carer can see everything but change nothing`() {
        val token = newFamily()
        val babyId = newBaby(token)
        val code = post("/api/invites", """{"access":"view"}""", token)["code"]
        val grandma = post("/api/join", """{"code":"$code","name":"Grandma","role":"grandparent"}""")["token"]

        assertEquals(200, get("/api/babies/$babyId/data", grandma).status)
        val me = get("/api/me", grandma).json.jsonObject["carer"]!!.jsonObject
        assertEquals("view", me["access"]!!.jsonPrimitive.content)
        val refused = post("/api/babies/$babyId/tastings", """{"food_id":"food.banana"}""", grandma)
        assertEquals(403, refused.status)
        assertTrue("view" in refused["error"])
        assertEquals(403, post("/api/invites", "{}", grandma).status)
        assertEquals(403, request("DELETE", "/api/family", token = grandma).status)
    }

    @Test
    fun `the app makes a food plan, and tasting a planned food ticks it off`() {
        val token = newFamily()
        val babyId = newBaby(token)
        val plan = post("/api/babies/$babyId/food-plan/generate", """{"days":7}""", token)
        assertEquals(200, plan.status, plan.body)
        val items = plan.json.jsonArray
        assertTrue(items.size >= 7)
        val first = items.first().jsonObject
        assertEquals("app", first["source"]!!.jsonPrimitive.content)

        post("/api/babies/$babyId/tastings", """{"food_id":"${first["food_id"]!!.jsonPrimitive.content}"}""", token)
        val data = get("/api/babies/$babyId/data", token).json.jsonObject
        assertEquals(items.size, data["food_plan"]!!.jsonArray.size)

        assertEquals(204, request("DELETE", "/api/babies/$babyId/food-plan/${first["id"]!!.jsonPrimitive.content}", token = token).status)
        assertEquals(204, request("DELETE", "/api/babies/$babyId/food-plan", token = token).status)
        assertEquals(0, get("/api/babies/$babyId/data", token).json.jsonObject["food_plan"]!!.jsonArray.size)
    }
}
