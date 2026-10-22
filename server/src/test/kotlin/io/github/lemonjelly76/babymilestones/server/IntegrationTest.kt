package io.github.lemonjelly76.babymilestones.server

import io.github.lemonjelly76.babymilestones.core.json.BabyJson
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.testcontainers.postgresql.PostgreSQLContainer
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@TestConfiguration(proxyBeanMethods = false)
class IntegrationTestConfiguration {
    @Bean
    @ServiceConnection
    fun postgres() = PostgreSQLContainer("postgres:18-alpine")
}

data class Response(val status: Int, val body: String, val headers: Map<String, List<String>> = emptyMap()) {
    val json: JsonElement get() = BabyJson.parseToJsonElement(body)
    operator fun get(key: String): String = json.jsonObject[key]!!.jsonPrimitive.content
    fun obj(key: String): JsonObject = json.jsonObject[key]!!.jsonObject
}

/** Spring Boot on a random port against a real PostgreSQL; no ANTHROPIC_API_KEY, so nothing calls out. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = ["app.setup-code=test-setup"])
@Import(IntegrationTestConfiguration::class)
abstract class IntegrationTest {

    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var jdbc: JdbcClient

    private val http = HttpClient.newHttpClient()

    fun request(
        method: String, path: String, body: String? = null, token: String? = null,
        contentType: String = "application/json", headers: Map<String, String> = emptyMap(),
    ): Response {
        val builder = HttpRequest.newBuilder(URI.create("http://localhost:$port$path"))
            .method(method, body?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody())
        if (body != null) builder.header("Content-Type", contentType)
        token?.let { builder.header("Authorization", "Bearer $it") }
        headers.forEach { (k, v) -> builder.header(k, v) }
        val response = http.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream())
        val isStream = response.headers().firstValue("Content-Type").orElse("").startsWith("text/event-stream")
        val text = response.body().bufferedReader().use { reader ->
            // An event stream may stay open; one JSON-RPC answer is all a test needs.
            if (isStream) generateSequence { reader.readLine() }.first { it.startsWith("data:") } else reader.readText()
        }
        return Response(response.statusCode(), text, response.headers().map())
    }

    fun get(path: String, token: String? = null) = request("GET", path, token = token)
    fun post(path: String, body: String, token: String? = null) = request("POST", path, body, token)

    /** A new family with one parent; returns the parent's token. */
    fun newFamily(name: String = "Test family", carer: String = "Sam"): String =
        post("/api/families", """{"family_name":"$name","carer_name":"$carer","setup_code":"test-setup"}""")
            .also { check(it.status == 201) { it.body } }["token"]

    fun newBaby(token: String, name: String = "Ava", dob: String = "2026-04-21", due: String? = null): String =
        post("/api/babies", """{"name":"$name","date_of_birth":"$dob"${due?.let { ",\"due_date\":\"$it\"" } ?: ""}}""", token)
            .also { check(it.status == 201) { it.body } }["id"]
}
