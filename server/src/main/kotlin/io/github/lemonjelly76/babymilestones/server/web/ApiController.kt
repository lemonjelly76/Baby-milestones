package io.github.lemonjelly76.babymilestones.server.web

import io.github.lemonjelly76.babymilestones.core.content.ContentBundle
import io.github.lemonjelly76.babymilestones.core.food.FoodTasting
import io.github.lemonjelly76.babymilestones.core.schedule.AppointmentRecord
import io.github.lemonjelly76.babymilestones.core.schedule.DoseRecord
import io.github.lemonjelly76.babymilestones.core.summary.ReviewNote
import io.github.lemonjelly76.babymilestones.core.timeline.Moment
import io.github.lemonjelly76.babymilestones.core.tracking.Baby
import io.github.lemonjelly76.babymilestones.core.tracking.MilestoneLog
import io.github.lemonjelly76.babymilestones.server.assistant.AssistantReply
import io.github.lemonjelly76.babymilestones.server.assistant.AssistantService
import io.github.lemonjelly76.babymilestones.server.assistant.ConfirmResult
import io.github.lemonjelly76.babymilestones.server.security.currentCarer
import io.github.lemonjelly76.babymilestones.server.service.BabyInput
import io.github.lemonjelly76.babymilestones.server.service.BabyService
import io.github.lemonjelly76.babymilestones.server.service.FamilyService
import io.github.lemonjelly76.babymilestones.server.service.FoodService
import io.github.lemonjelly76.babymilestones.server.service.GuideService
import io.github.lemonjelly76.babymilestones.server.service.TastingChanges
import io.github.lemonjelly76.babymilestones.server.service.TastingInput
import io.github.lemonjelly76.babymilestones.server.service.LogChanges
import io.github.lemonjelly76.babymilestones.server.service.LogInput
import io.github.lemonjelly76.babymilestones.server.service.RecordService
import io.github.lemonjelly76.babymilestones.server.service.Writer
import io.github.lemonjelly76.babymilestones.server.store.BabyStore
import io.github.lemonjelly76.babymilestones.server.store.FamilyStore
import io.github.lemonjelly76.babymilestones.server.store.Ids
import io.github.lemonjelly76.babymilestones.server.store.NotFoundException
import io.github.lemonjelly76.babymilestones.server.store.Photo
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Clock

@RestController
@RequestMapping("/api")
class ApiController(
    private val familyService: FamilyService,
    private val babyService: BabyService,
    private val records: RecordService,
    private val foods: FoodService,
    private val guides: GuideService,
    private val assistant: AssistantService,
    private val content: ContentBundle,
    private val babyStore: BabyStore,
    private val familyStore: FamilyStore,
    private val clock: Clock,
    @param:Value("\${app.mcp-public-url:}") private val mcpPublicUrl: String,
) {
    companion object {
        const val MAX_PHOTO_BYTES = 8 * 1024 * 1024
        val PHOTO_TYPES = setOf("image/jpeg", "image/png", "image/webp", "image/heic")
    }

    private fun writer() = currentCarer().let { Writer(it.familyId, it.carerId) }

    // --- family and sign-in ---

    @PostMapping("/families")
    @ResponseStatus(HttpStatus.CREATED)
    fun createFamily(@RequestBody request: CreateFamilyRequest): SignInResponse =
        familyService.createFamily(request.familyName, request.region, request.carerName, request.email, request.setupCode)
            .let { SignInResponse(it.family, it.carer, it.token) }

    @PostMapping("/join")
    fun join(@RequestBody request: JoinRequest): SignInResponse =
        familyService.join(request.code, request.name, request.email, request.role).let { SignInResponse(it.family, it.carer, it.token) }

    @GetMapping("/me")
    fun me(): MeResponse {
        val principal = currentCarer()
        return MeResponse(
            familyService.family(principal), familyService.carer(principal), familyService.carers(principal),
            babyService.babies(principal.familyId), content.version, mcpPublicUrl.ifBlank { null },
        )
    }

    @PatchMapping("/family")
    fun updateFamily(@RequestBody request: UpdateFamilyRequest) = familyService.updateFamily(currentCarer(), request.name, request.region)

    @DeleteMapping("/family")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteFamily() = familyService.deleteFamily(currentCarer())

    @PostMapping("/invites")
    fun invite(): InviteResponse = familyService.createInvite(currentCarer()).let { InviteResponse(it.code, it.expiresAt) }

    @PostMapping("/tokens")
    fun token(@RequestBody request: TokenRequest): TokenResponse {
        require(request.label != FamilyService.APP_TOKEN) { "Choose another label" }
        return TokenResponse(familyService.newToken(currentCarer(), request.label), request.label)
    }

    // --- content ---

    @GetMapping("/content/version")
    fun contentVersion() = ContentVersion(content.version)

    @GetMapping("/content")
    fun content(): ResponseEntity<ContentBundle> = ResponseEntity.ok().eTag(content.version)
        .cacheControl(CacheControl.noCache()).body(content)

    // --- babies ---

    @PostMapping("/babies")
    @ResponseStatus(HttpStatus.CREATED)
    fun createBaby(@RequestBody request: BabyRequest): Baby = babyService.create(currentCarer().familyId, request.toInput())

    @PutMapping("/babies/{babyId}")
    fun updateBaby(@PathVariable babyId: String, @RequestBody request: BabyRequest): Baby =
        babyService.update(currentCarer().familyId, babyId, request.toInput())

    @DeleteMapping("/babies/{babyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteBaby(@PathVariable babyId: String) = babyService.delete(currentCarer().familyId, babyId)

    @GetMapping("/babies/{babyId}/data")
    fun babyData(@PathVariable babyId: String): BabyData = data(currentCarer().familyId, babyId)

    @GetMapping("/babies/{babyId}/summary")
    fun summary(@PathVariable babyId: String): SummaryResponse =
        babyService.context(currentCarer().familyId, babyId).summary.let { SummaryResponse(it, it.toPlainText()) }

    // --- milestone log ---

    @PostMapping("/babies/{babyId}/logs")
    @ResponseStatus(HttpStatus.CREATED)
    fun log(@PathVariable babyId: String, @RequestBody request: LogRequest): MilestoneLog =
        records.logMilestone(writer(), babyId, request.toInput())

    @PostMapping("/babies/{babyId}/logs/backfill")
    fun backfill(@PathVariable babyId: String, @RequestBody request: BackfillRequest): List<MilestoneLog> =
        records.backfill(writer(), babyId, request.entries.map { it.toInput() })

    @PatchMapping("/babies/{babyId}/logs/{logId}")
    fun updateLog(@PathVariable babyId: String, @PathVariable logId: String, @RequestBody request: LogUpdateRequest): MilestoneLog =
        records.updateLog(writer(), babyId, logId, LogChanges(
            request.achievedOn, request.approximate, request.note, request.photoRef, request.lostOn, request.clearLost,
        ))

    @DeleteMapping("/babies/{babyId}/logs/{logId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeLog(@PathVariable babyId: String, @PathVariable logId: String) = records.removeLog(writer(), babyId, logId)

    @PostMapping("/babies/{babyId}/not-sure")
    fun notSure(@PathVariable babyId: String, @RequestBody request: NotSureRequest): MilestoneLog =
        records.markNotSure(writer(), babyId, request.milestoneId)

    // --- moments, appointments, vaccinations, notes ---

    @PostMapping("/babies/{babyId}/moments")
    @ResponseStatus(HttpStatus.CREATED)
    fun addMoment(@PathVariable babyId: String, @RequestBody request: MomentRequest): Moment =
        records.addMoment(writer(), babyId, request.title, request.date, request.note, request.photoRef)

    @DeleteMapping("/babies/{babyId}/moments/{momentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteMoment(@PathVariable babyId: String, @PathVariable momentId: String) = records.deleteMoment(writer(), babyId, momentId)

    @PutMapping("/babies/{babyId}/appointments/{type}")
    fun appointment(@PathVariable babyId: String, @PathVariable type: String, @RequestBody request: AppointmentRequest): AppointmentRecord =
        records.updateAppointment(writer(), babyId, type, request.status, request.date, request.note)

    @PutMapping("/babies/{babyId}/vaccinations/{doseCode}")
    fun vaccination(@PathVariable babyId: String, @PathVariable doseCode: String, @RequestBody request: VaccinationRequest): DoseRecord =
        records.recordVaccination(writer(), babyId, doseCode, request.givenOn, request.note)

    @DeleteMapping("/babies/{babyId}/vaccinations/{doseCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeVaccination(@PathVariable babyId: String, @PathVariable doseCode: String) =
        records.removeVaccination(writer(), babyId, doseCode)

    @PostMapping("/babies/{babyId}/review-notes")
    @ResponseStatus(HttpStatus.CREATED)
    fun addNote(@PathVariable babyId: String, @RequestBody request: ReviewNoteRequest): ReviewNote =
        records.addReviewNote(writer(), babyId, request.text)

    @PatchMapping("/babies/{babyId}/review-notes/{noteId}")
    fun resolveNote(@PathVariable babyId: String, @PathVariable noteId: String, @RequestBody request: ResolveRequest): ReviewNote =
        records.resolveReviewNote(writer(), babyId, noteId, request.resolved)

    @DeleteMapping("/babies/{babyId}/review-notes/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteNote(@PathVariable babyId: String, @PathVariable noteId: String) = records.deleteReviewNote(writer(), babyId, noteId)

    // --- foods, checklists and Claude's notes ---

    @PostMapping("/babies/{babyId}/tastings")
    @ResponseStatus(HttpStatus.CREATED)
    fun logFood(@PathVariable babyId: String, @RequestBody request: TastingRequest): FoodTasting =
        foods.log(writer(), babyId, TastingInput(
            request.foodId, request.name, request.group, request.tastedOn, request.liking, request.reaction, request.note,
            request.photoRef,
        ))

    @PatchMapping("/babies/{babyId}/tastings/{tastingId}")
    fun updateFood(@PathVariable babyId: String, @PathVariable tastingId: String, @RequestBody request: TastingUpdateRequest): FoodTasting =
        foods.update(writer(), babyId, tastingId, TastingChanges(
            request.tastedOn, request.liking, request.reaction, request.note, request.photoRef, request.clearLiking, request.clearPhoto,
        ))

    @DeleteMapping("/babies/{babyId}/tastings/{tastingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeFood(@PathVariable babyId: String, @PathVariable tastingId: String) = foods.remove(writer(), babyId, tastingId)

    @PutMapping("/babies/{babyId}/checklist/{itemId}")
    fun checklist(@PathVariable babyId: String, @PathVariable itemId: String, @RequestBody request: ChecklistRequest): ChecklistResponse =
        ChecklistResponse(itemId, request.done, guides.setChecklistItem(writer(), babyId, itemId, request.done))

    @DeleteMapping("/babies/{babyId}/notes/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteAssistantNote(@PathVariable babyId: String, @PathVariable noteId: String) = guides.deleteNote(writer(), babyId, noteId)

    // --- photos ---

    @PostMapping("/photos", consumes = ["image/*"])
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    fun uploadPhoto(@RequestHeader("Content-Type") contentType: String, @RequestBody bytes: ByteArray): PhotoResponse {
        val type = contentType.substringBefore(';').trim().lowercase()
        require(type in PHOTO_TYPES) { "Photos must be JPEG, PNG, WebP or HEIC" }
        require(bytes.size in 1..MAX_PHOTO_BYTES) { "Photos must be under 8 MB" }
        val photo = Photo(Ids.new("ph", 20), currentCarer().familyId, type, clock.instant())
        familyStore.storePhoto(photo, bytes)
        return PhotoResponse(photo.id)
    }

    @GetMapping("/photos/{photoId}")
    fun photo(@PathVariable photoId: String): ResponseEntity<ByteArray> {
        val (type, bytes) = familyStore.photo(currentCarer().familyId, photoId) ?: throw NotFoundException("No photo $photoId")
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type)).cacheControl(CacheControl.noStore()).body(bytes)
    }

    // --- assistant ---

    @PostMapping("/assistant/messages")
    fun ask(@RequestBody request: AssistantRequest): AssistantReply = assistant.reply(currentCarer(), request.babyId, request.messages)

    @PostMapping("/assistant/confirm")
    fun confirm(@RequestBody request: ConfirmRequest): List<ConfirmResult> = assistant.confirm(currentCarer(), request.proposals)

    // --- export ---

    @GetMapping("/export")
    fun export(): ExportResponse {
        val principal = currentCarer()
        return ExportResponse(
            familyService.family(principal), familyService.carers(principal),
            babyService.babies(principal.familyId).map { data(principal.familyId, it.id) }, clock.instant(),
        )
    }

    private fun data(familyId: String, babyId: String): BabyData {
        val baby = babyService.baby(familyId, babyId)
        return BabyData(
            baby, familyStore.family(familyId)?.region ?: "england", babyStore.logs(babyId), babyStore.moments(babyId),
            babyStore.appointments(babyId), babyStore.doses(babyId), babyStore.reviewNotes(babyId), content.version,
            babyStore.tastings(babyId), babyStore.ticks(babyId), babyStore.assistantNotes(babyId),
        )
    }

    private fun BabyRequest.toInput() = BabyInput(name, dateOfBirth, dueDate, sex, dobEstimated)
    private fun LogRequest.toInput() = LogInput(milestoneId, achievedOn, approximate, note, photoRef)
}
