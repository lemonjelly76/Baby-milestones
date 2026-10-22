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
}
