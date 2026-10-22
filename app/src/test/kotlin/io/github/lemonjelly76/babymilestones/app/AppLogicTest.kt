package io.github.lemonjelly76.babymilestones.app

import io.github.lemonjelly76.babymilestones.app.data.ApiClient
import io.github.lemonjelly76.babymilestones.app.data.ApiException
import io.github.lemonjelly76.babymilestones.app.data.BabyData
import io.github.lemonjelly76.babymilestones.app.data.BabyView
import io.github.lemonjelly76.babymilestones.app.data.LogRequest
import io.github.lemonjelly76.babymilestones.app.data.NotificationSettings
import io.github.lemonjelly76.babymilestones.app.data.TastingRequest
import io.github.lemonjelly76.babymilestones.app.notify.ReminderPlanner
import io.github.lemonjelly76.babymilestones.core.content.ContentLoader
import io.github.lemonjelly76.babymilestones.core.food.Liking
import io.github.lemonjelly76.babymilestones.core.growth.GrowthMeasurement
import io.github.lemonjelly76.babymilestones.core.growth.Measure
import io.github.lemonjelly76.babymilestones.core.guidance.WeeklyPlay
import io.github.lemonjelly76.babymilestones.core.records.FirstWord
import io.github.lemonjelly76.babymilestones.core.records.ToothRecord
import io.github.lemonjelly76.babymilestones.core.tracking.Baby
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AppLogicTest {

    private val content = ContentLoader.fromClasspath()

    private fun view(today: String, dob: String = "2026-04-21") = BabyView(
        BabyData(Baby("b_1", "Ava", LocalDate.parse(dob)), "england", emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), content.version),
        content, LocalDate.parse(today),
    )

    @Test
    fun `due now reminders are sent once each`() {
        val v = view("2026-10-21")
        val first = ReminderPlanner.plan(v, NotificationSettings(), emptySet(), null)
        val dueNow = first.filter { it.once && !it.key.startsWith("appt") }
        assertTrue(dueNow.isNotEmpty())
        val second = ReminderPlanner.plan(v, NotificationSettings(), dueNow.map { it.key }.toSet(), null)
        assertTrue(second.none { it.key in dueNow.map { r -> r.key } })
    }

    @Test
    fun `weekly digest goes out on Sundays and the quiet nudge on Saturdays`() {
        assertTrue(ReminderPlanner.plan(view("2026-10-25"), NotificationSettings(), emptySet(), null).any { it.key.startsWith("digest") })
        assertTrue(ReminderPlanner.plan(view("2026-10-24"), NotificationSettings(), emptySet(), null).any { it.key.startsWith("nudge") })
        val off = NotificationSettings(weeklyDigest = false, dueNow = false, appointmentWindows = false, quietNudge = false)
        assertEquals(emptyList(), ReminderPlanner.plan(view("2026-10-25"), off, emptySet(), null))
    }

    @Test
    fun `today's view answers what's typical, what's next and what's not logged`() {
        val v = view("2026-10-21")
        assertEquals("mover_6_9m", v.snapshot.period.id)
        assertTrue(v.notYetLogged > 0)
        assertTrue(v.tip != null)
    }

    @Test
    fun `server errors surface their message`() = runTest {
        val engine = MockEngine {
            respond("""{"error":"Unknown milestone move.flies"}""", HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val api = ApiClient({ "https://example.test" }, { "token" }, HttpClient(engine) { expectSuccess = false })
        val e = assertFailsWith<ApiException> { api.log("b_1", LogRequest("move.flies")) }
        assertEquals(404, e.status)
        assertEquals("Unknown milestone move.flies", e.message)
        val request = engine.requestHistory.single()
        assertEquals("Bearer token", request.headers["Authorization"])
        assertEquals("""{"milestone_id":"move.flies","approximate":false}""", String(request.body.toByteArray()))
        assertEquals("application/json", request.body.contentType?.withoutParameters().toString())
    }

    @Test
    fun `a 6 month old sees the foods tracker, weaning guides and suggestions`() {
        val v = view("2026-10-22")
        assertTrue(v.showFoods)
        assertTrue(v.guides.now.any { it.id == "guide.baby_led_weaning" })
        assertTrue(v.foods.suggestions.isNotEmpty())
        assertEquals(LocalDate.parse("2030-01-15"), v.schoolDates?.applyBy)
    }

    @Test
    fun `logging a food sends the catalogue id and the parent's words`() = runTest {
        val engine = MockEngine {
            respond("""{"id":"t_1","baby_id":"b_1","food_id":"food.egg","name":"Egg","group":"protein","tasted_on":"2026-10-22"}""",
                HttpStatusCode.Created, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val api = ApiClient({ "https://example.test" }, { "token" }, HttpClient(engine) { expectSuccess = false })
        val tasting = api.logFood("b_1", TastingRequest(foodId = "food.egg", liking = Liking.LIKED, reaction = "rash on chin"))
        assertEquals("Egg", tasting.name)
        assertEquals("""{"food_id":"food.egg","liking":"liked","reaction":"rash on chin"}""",
            String(engine.requestHistory.single().body.toByteArray()))
    }

    @Test
    fun `the view puts growth on the chart and words and teeth on the timeline`() {
        val data = BabyData(
            Baby("b_1", "Ava", LocalDate.parse("2026-04-21"), sex = "female"), "england", emptyList(), emptyList(), emptyList(),
            emptyList(), emptyList(), content.version,
            growth = listOf(GrowthMeasurement("g1", "b_1", LocalDate.parse("2026-10-01"), weightKg = 7.6)),
            words = listOf(FirstWord("w1", "b_1", "dada", LocalDate.parse("2026-10-10"))),
            teeth = listOf(ToothRecord("b_1", "71", LocalDate.parse("2026-10-05"))),
        )
        val v = BabyView(data, content, LocalDate.parse("2026-10-22"))
        assertTrue(v.growth.latest(Measure.WEIGHT)!!.centile!! in 1.0..99.0)
        assertTrue(v.timeline.any { it.title == "First word: \u201cdada\u201d" })
        assertTrue(v.memories.isNotEmpty())
        assertEquals(WeeklyPlay.IDEAS_PER_WEEK, v.playIdeas.size)
    }

    @Test
    fun `a view-only invite says so`() = runTest {
        val engine = MockEngine {
            respond("""{"code":"ABCDEFGHJK","expires_at":"2026-10-29T10:00:00Z"}""", HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val api = ApiClient({ "https://example.test" }, { "token" }, HttpClient(engine) { expectSuccess = false })
        api.invite("view")
        assertEquals("""{"access":"view"}""", String(engine.requestHistory.single().body.toByteArray()))
    }
}
