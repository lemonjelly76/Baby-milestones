package io.github.lemonjelly76.babymilestones.server.web

import io.github.lemonjelly76.babymilestones.core.api.AppointmentRequest
import io.github.lemonjelly76.babymilestones.core.api.BabyData
import io.github.lemonjelly76.babymilestones.core.api.BabyRequest
import io.github.lemonjelly76.babymilestones.core.api.BackfillRequest
import io.github.lemonjelly76.babymilestones.core.api.LogRequest
import io.github.lemonjelly76.babymilestones.core.api.LogUpdateRequest
import io.github.lemonjelly76.babymilestones.core.api.MomentRequest
import io.github.lemonjelly76.babymilestones.core.api.NotSureRequest
import io.github.lemonjelly76.babymilestones.core.api.ResolveRequest
import io.github.lemonjelly76.babymilestones.core.api.ReviewNoteRequest
import io.github.lemonjelly76.babymilestones.core.api.SummaryResponse
import io.github.lemonjelly76.babymilestones.core.api.VaccinationRequest
import io.github.lemonjelly76.babymilestones.core.schedule.AppointmentRecord
import io.github.lemonjelly76.babymilestones.core.schedule.DoseRecord
import io.github.lemonjelly76.babymilestones.core.summary.ReviewNote
import io.github.lemonjelly76.babymilestones.core.timeline.Moment
import io.github.lemonjelly76.babymilestones.core.tracking.Baby
import io.github.lemonjelly76.babymilestones.core.tracking.MilestoneLog
import io.github.lemonjelly76.babymilestones.server.security.currentCarer
import io.github.lemonjelly76.babymilestones.server.service.BabyInput
import io.github.lemonjelly76.babymilestones.server.service.BabyService
import io.github.lemonjelly76.babymilestones.server.service.LogChanges
import io.github.lemonjelly76.babymilestones.server.service.LogInput
import io.github.lemonjelly76.babymilestones.server.service.RecordService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** Babies, their milestone log, moments, appointments, vaccinations and questions for the health visitor. */
@RestController
@RequestMapping("/api")
class BabyController(
    private val babyService: BabyService,
    private val records: RecordService,
) {
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
    fun babyData(@PathVariable babyId: String): BabyData = babyService.data(currentCarer().familyId, babyId)

    @GetMapping("/babies/{babyId}/summary")
    fun summary(@PathVariable babyId: String): SummaryResponse =
        babyService.context(currentCarer().familyId, babyId).summary.let { SummaryResponse(it, it.toPlainText()) }

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

    private fun BabyRequest.toInput() = BabyInput(name, dateOfBirth, dueDate, sex, dobEstimated)
    private fun LogRequest.toInput() = LogInput(milestoneId, achievedOn, approximate, note, photoRef)
}
