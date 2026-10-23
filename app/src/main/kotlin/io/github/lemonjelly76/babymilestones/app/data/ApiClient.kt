package io.github.lemonjelly76.babymilestones.app.data

import io.github.lemonjelly76.babymilestones.core.api.AppointmentRequest
import io.github.lemonjelly76.babymilestones.core.api.AssistantReply
import io.github.lemonjelly76.babymilestones.core.api.AssistantRequest
import io.github.lemonjelly76.babymilestones.core.api.BabyData
import io.github.lemonjelly76.babymilestones.core.api.BabyRequest
import io.github.lemonjelly76.babymilestones.core.api.BackfillRequest
import io.github.lemonjelly76.babymilestones.core.api.ChatMessage
import io.github.lemonjelly76.babymilestones.core.api.ChecklistRequest
import io.github.lemonjelly76.babymilestones.core.api.ConfirmRequest
import io.github.lemonjelly76.babymilestones.core.api.ConfirmResult
import io.github.lemonjelly76.babymilestones.core.api.ContactRequest
import io.github.lemonjelly76.babymilestones.core.api.ContentVersion
import io.github.lemonjelly76.babymilestones.core.api.CreateFamilyRequest
import io.github.lemonjelly76.babymilestones.core.api.ErrorResponse
import io.github.lemonjelly76.babymilestones.core.api.Family
import io.github.lemonjelly76.babymilestones.core.api.GrowthRequest
import io.github.lemonjelly76.babymilestones.core.api.InviteRequest
import io.github.lemonjelly76.babymilestones.core.api.InviteResponse
import io.github.lemonjelly76.babymilestones.core.api.JoinRequest
import io.github.lemonjelly76.babymilestones.core.api.LogRequest
import io.github.lemonjelly76.babymilestones.core.api.LogUpdateRequest
import io.github.lemonjelly76.babymilestones.core.api.MeResponse
import io.github.lemonjelly76.babymilestones.core.api.MomentRequest
import io.github.lemonjelly76.babymilestones.core.api.NotSureRequest
import io.github.lemonjelly76.babymilestones.core.api.PhotoResponse
import io.github.lemonjelly76.babymilestones.core.api.Proposal
import io.github.lemonjelly76.babymilestones.core.api.ResolveRequest
import io.github.lemonjelly76.babymilestones.core.api.ReviewNoteRequest
import io.github.lemonjelly76.babymilestones.core.api.SignInResponse
import io.github.lemonjelly76.babymilestones.core.api.TastingRequest
import io.github.lemonjelly76.babymilestones.core.api.TastingUpdateRequest
import io.github.lemonjelly76.babymilestones.core.api.TokenRequest
import io.github.lemonjelly76.babymilestones.core.api.TokenResponse
import io.github.lemonjelly76.babymilestones.core.api.ToothRequest
import io.github.lemonjelly76.babymilestones.core.api.UpdateFamilyRequest
import io.github.lemonjelly76.babymilestones.core.api.VaccinationRequest
import io.github.lemonjelly76.babymilestones.core.api.WordRequest
import io.github.lemonjelly76.babymilestones.core.content.ContentBundle
import io.github.lemonjelly76.babymilestones.core.api.GeneratePlanRequest
import io.github.lemonjelly76.babymilestones.core.food.FoodTasting
import io.github.lemonjelly76.babymilestones.core.food.PlannedFood
import io.github.lemonjelly76.babymilestones.core.growth.GrowthMeasurement
import io.github.lemonjelly76.babymilestones.core.json.BabyJson
import io.github.lemonjelly76.babymilestones.core.records.Contact
import io.github.lemonjelly76.babymilestones.core.records.FirstWord
import io.github.lemonjelly76.babymilestones.core.records.ToothRecord
import io.github.lemonjelly76.babymilestones.core.schedule.AppointmentRecord
import io.github.lemonjelly76.babymilestones.core.schedule.DoseRecord
import io.github.lemonjelly76.babymilestones.core.summary.ReviewNote
import io.github.lemonjelly76.babymilestones.core.timeline.Moment
import io.github.lemonjelly76.babymilestones.core.tracking.Baby
import io.github.lemonjelly76.babymilestones.core.tracking.MilestoneLog
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer

class ApiException(val status: Int, message: String) : Exception(message)

/** The family server's REST API. Every call but sign-up carries the carer's bearer token. */
class ApiClient(
    private val baseUrl: () -> String,
    private val token: () -> String?,
    private val http: HttpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 10_000
        }
        expectSuccess = false
    },
) {
    private suspend fun send(method: HttpMethod, path: String, configure: HttpRequestBuilder.() -> Unit = {}): HttpResponse {
        val server = when (val address = ServerAddress.check(baseUrl())) {
            is ServerAddress.Valid -> address.url
            is ServerAddress.Invalid -> throw ApiException(0, address.message)
        }
        val response = http.request(server + path) {
            this.method = method
            token()?.let { header("Authorization", "Bearer $it") }
            configure()
        }
        if (!response.status.isSuccess()) {
            val text = response.bodyAsText()
            val message = runCatching { BabyJson.decodeFromString(ErrorResponse.serializer(), text).error }.getOrNull()
                ?: "The server answered ${response.status.value}"
            throw ApiException(response.status.value, message)
        }
        return response
    }

    private suspend fun <T> call(method: HttpMethod, path: String, out: KSerializer<T>, body: String? = null): T {
        val response = send(method, path) {
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
        return BabyJson.decodeFromString(out, response.bodyAsText())
    }

    private suspend fun <B, T> call(method: HttpMethod, path: String, out: KSerializer<T>, inSerializer: KSerializer<B>, body: B): T =
        call(method, path, out, BabyJson.encodeToString(inSerializer, body))

    private suspend fun noContent(method: HttpMethod, path: String) {
        send(method, path)
    }

    // --- family ---

    suspend fun createFamily(request: CreateFamilyRequest) =
        call(HttpMethod.Post, "/api/families", SignInResponse.serializer(), CreateFamilyRequest.serializer(), request)

    suspend fun join(request: JoinRequest) = call(HttpMethod.Post, "/api/join", SignInResponse.serializer(), JoinRequest.serializer(), request)

    suspend fun me() = call(HttpMethod.Get, "/api/me", MeResponse.serializer())

    suspend fun updateFamily(request: UpdateFamilyRequest) =
        call(HttpMethod.Patch, "/api/family", Family.serializer(), UpdateFamilyRequest.serializer(), request)

    suspend fun deleteFamily() = noContent(HttpMethod.Delete, "/api/family")

    suspend fun invite(access: String = "edit") =
        call(HttpMethod.Post, "/api/invites", InviteResponse.serializer(), InviteRequest.serializer(), InviteRequest(access))

    suspend fun saveContact(id: String?, request: ContactRequest) =
        if (id == null) call(HttpMethod.Post, "/api/contacts", Contact.serializer(), ContactRequest.serializer(), request)
        else call(HttpMethod.Put, "/api/contacts/$id", Contact.serializer(), ContactRequest.serializer(), request)

    suspend fun deleteContact(id: String) = noContent(HttpMethod.Delete, "/api/contacts/$id")

    suspend fun newToken(label: String) =
        call(HttpMethod.Post, "/api/tokens", TokenResponse.serializer(), TokenRequest.serializer(), TokenRequest(label))

    // --- content ---

    suspend fun contentVersion() = call(HttpMethod.Get, "/api/content/version", ContentVersion.serializer()).version

    suspend fun contentText(): String = send(HttpMethod.Get, "/api/content").bodyAsText()

    fun parseContent(text: String): ContentBundle = BabyJson.decodeFromString(ContentBundle.serializer(), text)

    // --- babies ---

    suspend fun createBaby(request: BabyRequest) = call(HttpMethod.Post, "/api/babies", Baby.serializer(), BabyRequest.serializer(), request)

    suspend fun updateBaby(id: String, request: BabyRequest) =
        call(HttpMethod.Put, "/api/babies/$id", Baby.serializer(), BabyRequest.serializer(), request)

    suspend fun deleteBaby(id: String) = noContent(HttpMethod.Delete, "/api/babies/$id")

    suspend fun babyData(id: String) = call(HttpMethod.Get, "/api/babies/$id/data", BabyData.serializer())

    // --- milestone log ---

    suspend fun log(babyId: String, request: LogRequest) =
        call(HttpMethod.Post, "/api/babies/$babyId/logs", MilestoneLog.serializer(), LogRequest.serializer(), request)

    suspend fun backfill(babyId: String, entries: List<LogRequest>) = call(
        HttpMethod.Post, "/api/babies/$babyId/logs/backfill", ListSerializer(MilestoneLog.serializer()),
        BackfillRequest.serializer(), BackfillRequest(entries),
    )

    suspend fun updateLog(babyId: String, logId: String, request: LogUpdateRequest) =
        call(HttpMethod.Patch, "/api/babies/$babyId/logs/$logId", MilestoneLog.serializer(), LogUpdateRequest.serializer(), request)

    suspend fun removeLog(babyId: String, logId: String) = noContent(HttpMethod.Delete, "/api/babies/$babyId/logs/$logId")

    suspend fun notSure(babyId: String, milestoneId: String) =
        call(HttpMethod.Post, "/api/babies/$babyId/not-sure", MilestoneLog.serializer(), NotSureRequest.serializer(), NotSureRequest(milestoneId))

    // --- records ---

    suspend fun addMoment(babyId: String, request: MomentRequest) =
        call(HttpMethod.Post, "/api/babies/$babyId/moments", Moment.serializer(), MomentRequest.serializer(), request)

    suspend fun deleteMoment(babyId: String, momentId: String) = noContent(HttpMethod.Delete, "/api/babies/$babyId/moments/$momentId")

    suspend fun appointment(babyId: String, type: String, request: AppointmentRequest) =
        call(HttpMethod.Put, "/api/babies/$babyId/appointments/$type", AppointmentRecord.serializer(), AppointmentRequest.serializer(), request)

    suspend fun vaccination(babyId: String, code: String, request: VaccinationRequest) =
        call(HttpMethod.Put, "/api/babies/$babyId/vaccinations/$code", DoseRecord.serializer(), VaccinationRequest.serializer(), request)

    suspend fun removeVaccination(babyId: String, code: String) = noContent(HttpMethod.Delete, "/api/babies/$babyId/vaccinations/$code")

    suspend fun addReviewNote(babyId: String, text: String) =
        call(HttpMethod.Post, "/api/babies/$babyId/review-notes", ReviewNote.serializer(), ReviewNoteRequest.serializer(), ReviewNoteRequest(text))

    suspend fun resolveReviewNote(babyId: String, noteId: String, resolved: Boolean) =
        call(HttpMethod.Patch, "/api/babies/$babyId/review-notes/$noteId", ReviewNote.serializer(), ResolveRequest.serializer(), ResolveRequest(resolved))

    suspend fun deleteReviewNote(babyId: String, noteId: String) = noContent(HttpMethod.Delete, "/api/babies/$babyId/review-notes/$noteId")

    // --- foods, checklists and Claude's notes ---

    suspend fun logFood(babyId: String, request: TastingRequest) =
        call(HttpMethod.Post, "/api/babies/$babyId/tastings", FoodTasting.serializer(), TastingRequest.serializer(), request)

    suspend fun updateFood(babyId: String, tastingId: String, request: TastingUpdateRequest) =
        call(HttpMethod.Patch, "/api/babies/$babyId/tastings/$tastingId", FoodTasting.serializer(), TastingUpdateRequest.serializer(), request)

    suspend fun removeFood(babyId: String, tastingId: String) = noContent(HttpMethod.Delete, "/api/babies/$babyId/tastings/$tastingId")

    suspend fun setChecklistItem(babyId: String, itemId: String, done: Boolean) {
        send(HttpMethod.Put, "/api/babies/$babyId/checklist/$itemId") {
            contentType(ContentType.Application.Json)
            setBody(BabyJson.encodeToString(ChecklistRequest.serializer(), ChecklistRequest(done)))
        }
    }

    suspend fun generatePlan(babyId: String, days: Int) = call(
        HttpMethod.Post, "/api/babies/$babyId/food-plan/generate", ListSerializer(PlannedFood.serializer()),
        GeneratePlanRequest.serializer(), GeneratePlanRequest(days),
    )

    suspend fun clearPlan(babyId: String) = noContent(HttpMethod.Delete, "/api/babies/$babyId/food-plan")

    suspend fun removePlanItem(babyId: String, itemId: String) = noContent(HttpMethod.Delete, "/api/babies/$babyId/food-plan/$itemId")

    suspend fun deleteNote(babyId: String, noteId: String) = noContent(HttpMethod.Delete, "/api/babies/$babyId/notes/$noteId")

    // --- growth, first words and teeth ---

    suspend fun logGrowth(babyId: String, request: GrowthRequest) =
        call(HttpMethod.Post, "/api/babies/$babyId/growth", GrowthMeasurement.serializer(), GrowthRequest.serializer(), request)

    suspend fun deleteGrowth(babyId: String, id: String) = noContent(HttpMethod.Delete, "/api/babies/$babyId/growth/$id")

    suspend fun logWord(babyId: String, request: WordRequest) =
        call(HttpMethod.Post, "/api/babies/$babyId/words", FirstWord.serializer(), WordRequest.serializer(), request)

    suspend fun deleteWord(babyId: String, id: String) = noContent(HttpMethod.Delete, "/api/babies/$babyId/words/$id")

    suspend fun logTooth(babyId: String, toothId: String, request: ToothRequest) =
        call(HttpMethod.Put, "/api/babies/$babyId/teeth/$toothId", ToothRecord.serializer(), ToothRequest.serializer(), request)

    suspend fun deleteTooth(babyId: String, toothId: String) = noContent(HttpMethod.Delete, "/api/babies/$babyId/teeth/$toothId")

    // --- photos ---

    suspend fun uploadPhoto(bytes: ByteArray, contentType: String): String {
        val response = send(HttpMethod.Post, "/api/photos") {
            contentType(ContentType.parse(contentType))
            setBody(bytes)
        }
        return BabyJson.decodeFromString(PhotoResponse.serializer(), response.bodyAsText()).id
    }

    suspend fun photo(id: String): ByteArray = send(HttpMethod.Get, "/api/photos/$id").bodyAsBytes()

    // --- assistant ---

    suspend fun ask(babyId: String, messages: List<ChatMessage>) = call(
        HttpMethod.Post, "/api/assistant/messages", AssistantReply.serializer(), AssistantRequest.serializer(),
        AssistantRequest(babyId, messages),
    )

    suspend fun confirm(proposals: List<Proposal>) = call(
        HttpMethod.Post, "/api/assistant/confirm", ListSerializer(ConfirmResult.serializer()), ConfirmRequest.serializer(),
        ConfirmRequest(proposals),
    )

    suspend fun export(): String = send(HttpMethod.Get, "/api/export").bodyAsText()
}
