package io.github.lemonjelly76.babymilestones.app.data

import io.github.lemonjelly76.babymilestones.core.content.SignpostCopy
import io.github.lemonjelly76.babymilestones.core.food.FoodGroup
import io.github.lemonjelly76.babymilestones.core.food.FoodTasting
import io.github.lemonjelly76.babymilestones.core.food.Liking
import io.github.lemonjelly76.babymilestones.core.guides.ChecklistTick
import io.github.lemonjelly76.babymilestones.core.notes.AssistantNote
import io.github.lemonjelly76.babymilestones.core.json.Date
import io.github.lemonjelly76.babymilestones.core.json.Timestamp
import io.github.lemonjelly76.babymilestones.core.safety.SafetyKind
import io.github.lemonjelly76.babymilestones.core.schedule.AppointmentRecord
import io.github.lemonjelly76.babymilestones.core.schedule.DoseRecord
import io.github.lemonjelly76.babymilestones.core.summary.ReviewNote
import io.github.lemonjelly76.babymilestones.core.timeline.Moment
import io.github.lemonjelly76.babymilestones.core.tracking.Baby
import io.github.lemonjelly76.babymilestones.core.tracking.MilestoneLog
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** The server's JSON shapes (server/web/ApiModels.kt), in the shared snake_case dialect. */

@Serializable
data class Family(val id: String, val name: String, val region: String)

@Serializable
data class Carer(val id: String, val familyId: String, val name: String, val email: String? = null, val role: String)

@Serializable
data class CreateFamilyRequest(
    val familyName: String,
    val region: String,
    val carerName: String,
    val email: String? = null,
    val setupCode: String? = null,
)

@Serializable
data class JoinRequest(val code: String, val name: String, val email: String? = null, val role: String = "parent")

@Serializable
data class SignInResponse(val family: Family, val carer: Carer, val token: String)

@Serializable
data class MeResponse(
    val family: Family,
    val carer: Carer,
    val carers: List<Carer>,
    val babies: List<Baby>,
    val contentVersion: String,
    val mcpUrl: String? = null,
)

@Serializable
data class UpdateFamilyRequest(val name: String? = null, val region: String? = null)

@Serializable
data class InviteResponse(val code: String, val expiresAt: Timestamp)

@Serializable
data class TokenRequest(val label: String)

@Serializable
data class TokenResponse(val token: String, val label: String)

@Serializable
data class BabyRequest(
    val name: String,
    val dateOfBirth: Date,
    val dueDate: Date? = null,
    val sex: String? = null,
    val dobEstimated: Boolean = false,
)

@Serializable
data class BabyData(
    val baby: Baby,
    val region: String,
    val logs: List<MilestoneLog>,
    val moments: List<Moment>,
    val appointments: List<AppointmentRecord>,
    val vaccinations: List<DoseRecord>,
    val reviewNotes: List<ReviewNote>,
    val contentVersion: String,
    val tastings: List<FoodTasting> = emptyList(),
    val checklist: List<ChecklistTick> = emptyList(),
    val assistantNotes: List<AssistantNote> = emptyList(),
)

@Serializable
data class TastingRequest(
    val foodId: String? = null,
    val name: String? = null,
    val group: FoodGroup? = null,
    val tastedOn: Date? = null,
    val liking: Liking? = null,
    val reaction: String? = null,
    val note: String? = null,
    val photoRef: String? = null,
)

@Serializable
data class TastingUpdateRequest(
    val tastedOn: Date? = null,
    val liking: Liking? = null,
    val reaction: String? = null,
    val note: String? = null,
    val photoRef: String? = null,
)

@Serializable
data class ChecklistRequest(val done: Boolean)

@Serializable
data class LogRequest(
    val milestoneId: String,
    val achievedOn: Date? = null,
    val approximate: Boolean = false,
    val note: String? = null,
    val photoRef: String? = null,
)

@Serializable
data class BackfillRequest(val entries: List<LogRequest>)

@Serializable
data class LogUpdateRequest(
    val achievedOn: Date? = null,
    val approximate: Boolean? = null,
    val note: String? = null,
    val photoRef: String? = null,
    val lostOn: Date? = null,
    val clearLost: Boolean = false,
)

@Serializable
data class NotSureRequest(val milestoneId: String)

@Serializable
data class MomentRequest(val title: String, val date: Date, val note: String? = null, val photoRef: String? = null)

@Serializable
data class AppointmentRequest(val status: String, val date: Date? = null, val note: String? = null)

@Serializable
data class VaccinationRequest(val givenOn: Date, val note: String? = null)

@Serializable
data class ReviewNoteRequest(val text: String)

@Serializable
data class ResolveRequest(val resolved: Boolean)

@Serializable
data class PhotoResponse(val id: String)

@Serializable
data class ContentVersion(val version: String)

@Serializable
data class ErrorResponse(val error: String)

@Serializable
data class ChatMessage(val role: Role, val text: String) {
    @Serializable
    enum class Role {
        @SerialName("user") USER,
        @SerialName("assistant") ASSISTANT,
    }
}

@Serializable
data class AssistantRequest(val babyId: String, val messages: List<ChatMessage>)

@Serializable
data class Proposal(val id: String, val tool: String, val input: JsonObject, val summary: String)

@Serializable
data class SafetyCard(val kind: SafetyKind, val card: SignpostCopy)

@Serializable
data class AssistantReply(val reply: String? = null, val proposals: List<Proposal> = emptyList(), val safety: SafetyCard? = null)

@Serializable
data class ConfirmRequest(val proposals: List<Proposal>)

@Serializable
data class ConfirmResult(val proposalId: String, val tool: String, val ok: Boolean, val result: JsonElement? = null, val error: String? = null)
