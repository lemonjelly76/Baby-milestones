package io.github.lemonjelly76.babymilestones.app

import io.github.lemonjelly76.babymilestones.app.data.ApiClient
import io.github.lemonjelly76.babymilestones.app.data.ApiException
import io.github.lemonjelly76.babymilestones.app.data.BabyData
import io.github.lemonjelly76.babymilestones.app.data.BabyView
import io.github.lemonjelly76.babymilestones.app.data.LogRequest
import io.github.lemonjelly76.babymilestones.app.data.NotificationSettings
import io.github.lemonjelly76.babymilestones.app.notify.ReminderPlanner
import io.github.lemonjelly76.babymilestones.core.content.ContentLoader
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
}
