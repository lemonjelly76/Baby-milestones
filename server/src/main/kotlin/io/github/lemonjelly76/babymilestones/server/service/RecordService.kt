package io.github.lemonjelly76.babymilestones.server.service

import io.github.lemonjelly76.babymilestones.core.content.ContentBundle
import io.github.lemonjelly76.babymilestones.core.schedule.AppointmentRecord
import io.github.lemonjelly76.babymilestones.core.schedule.DoseRecord
import io.github.lemonjelly76.babymilestones.core.summary.ReviewNote
import io.github.lemonjelly76.babymilestones.core.timeline.Moment
import io.github.lemonjelly76.babymilestones.core.tracking.LogSource
import io.github.lemonjelly76.babymilestones.core.tracking.LogState
import io.github.lemonjelly76.babymilestones.core.tracking.MilestoneLog
import io.github.lemonjelly76.babymilestones.server.store.BabyStore
import io.github.lemonjelly76.babymilestones.server.store.FamilyStore
import io.github.lemonjelly76.babymilestones.server.store.Ids
import io.github.lemonjelly76.babymilestones.server.store.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate

/** Who is writing, and from where, so log entries record their source. */
data class Writer(val familyId: String, val carerId: String?, val source: LogSource = LogSource.MANUAL)

data class LogInput(
    val milestoneId: String,
    val achievedOn: LocalDate? = null,
    val approximate: Boolean = false,
    val note: String? = null,
    val photoRef: String? = null,
)

data class LogChanges(
    val achievedOn: LocalDate? = null,
    val approximate: Boolean? = null,
    val note: String? = null,
    val photoRef: String? = null,
    val lostOn: LocalDate? = null,
    val clearLost: Boolean = false,
)

/** Writes to a baby's records. Edits keep a history; every write first checks the baby belongs to the writer's family. */
@Service
class RecordService(
    private val babies: BabyStore,
    private val families: FamilyStore,
    private val access: BabyAccess,
    private val content: ContentBundle,
    private val clock: Clock,
) {
    private fun today() = LocalDate.now(clock)

    private fun checkBaby(writer: Writer, babyId: String) = access.checkBaby(writer, babyId)

    private fun checkPhoto(writer: Writer, photoRef: String?) = access.checkPhoto(writer, photoRef)

    // --- milestone log ---

    @Transactional
    fun logMilestone(writer: Writer, babyId: String, input: LogInput): MilestoneLog {
        checkBaby(writer, babyId)
        content.milestone(input.milestoneId) ?: throw NotFoundException("Unknown milestone ${input.milestoneId}")
        checkPhoto(writer, input.photoRef)
        val today = today()
        val achievedOn = input.achievedOn ?: today
        require(!achievedOn.isAfter(today)) { "The date can't be in the future" }
        val log = MilestoneLog(
            id = Ids.new("l"),
            babyId = babyId,
            milestoneId = input.milestoneId,
            state = LogState.ACHIEVED,
            achievedOn = achievedOn,
            approximate = input.approximate || (input.achievedOn == null && writer.source == LogSource.BACKFILL),
            markedOn = today,
            note = input.note?.trim()?.ifEmpty { null },
            photoRef = input.photoRef,
            source = writer.source,
            loggedBy = writer.carerId,
        )
        return replace(writer, babyId, log)
    }

    @Transactional
    fun backfill(writer: Writer, babyId: String, inputs: List<LogInput>): List<MilestoneLog> =
        inputs.map { logMilestone(writer.copy(source = LogSource.BACKFILL), babyId, it) }

    @Transactional
    fun markNotSure(writer: Writer, babyId: String, milestoneId: String): MilestoneLog {
        checkBaby(writer, babyId)
        content.milestone(milestoneId) ?: throw NotFoundException("Unknown milestone $milestoneId")
        val log = MilestoneLog(
            id = Ids.new("l"), babyId = babyId, milestoneId = milestoneId, state = LogState.NOT_SURE,
            markedOn = today(), source = writer.source, loggedBy = writer.carerId,
        )
        return replace(writer, babyId, log)
    }

    @Transactional
    fun updateLog(writer: Writer, babyId: String, logId: String, changes: LogChanges): MilestoneLog {
        checkBaby(writer, babyId)
        checkPhoto(writer, changes.photoRef)
        val current = babies.log(babyId, logId) ?: throw NotFoundException("No log entry $logId")
        changes.achievedOn?.let { require(!it.isAfter(today())) { "The date can't be in the future" } }
        val updated = current.copy(
            state = if (changes.achievedOn != null) LogState.ACHIEVED else current.state,
            achievedOn = changes.achievedOn ?: current.achievedOn,
            approximate = changes.approximate ?: current.approximate,
            note = changes.note?.trim()?.ifEmpty { null } ?: current.note,
            photoRef = changes.photoRef ?: current.photoRef,
            lostOn = if (changes.clearLost) null else changes.lostOn ?: current.lostOn,
        )
        require(updated.lostOn == null || updated.state == LogState.ACHIEVED) { "Only an achieved milestone can be marked as lost" }
        babies.recordHistory(current, "updated", writer.carerId)
        babies.saveLog(updated)
        return updated
    }

    @Transactional
    fun removeLog(writer: Writer, babyId: String, logId: String) {
        checkBaby(writer, babyId)
        val current = babies.log(babyId, logId) ?: throw NotFoundException("No log entry $logId")
        babies.recordHistory(current, "deleted", writer.carerId)
        babies.deleteLog(babyId, logId)
    }

    private fun replace(writer: Writer, babyId: String, log: MilestoneLog): MilestoneLog {
        babies.logForMilestone(babyId, log.milestoneId)?.let { babies.recordHistory(it, "updated", writer.carerId) }
        babies.saveLog(log)
        babies.recordHistory(log, "created", writer.carerId)
        return log
    }

    // --- moments ---

    @Transactional
    fun addMoment(writer: Writer, babyId: String, title: String, date: LocalDate, note: String?, photoRef: String?): Moment {
        checkBaby(writer, babyId)
        checkPhoto(writer, photoRef)
        require(title.isNotBlank()) { "A title is needed" }
        require(!date.isAfter(today())) { "The date can't be in the future" }
        val moment = Moment(Ids.new("m"), babyId, title.trim(), date, note?.trim()?.ifEmpty { null }, photoRef)
        babies.saveMoment(moment, writer.carerId)
        return moment
    }

    @Transactional
    fun deleteMoment(writer: Writer, babyId: String, momentId: String) {
        checkBaby(writer, babyId)
        if (!babies.deleteMoment(babyId, momentId)) throw NotFoundException("No moment $momentId")
    }

    // --- appointments and vaccinations ---

    @Transactional
    fun updateAppointment(
        writer: Writer, babyId: String, type: String, status: String, date: LocalDate?, note: String?,
    ): AppointmentRecord {
        checkBaby(writer, babyId)
        content.schedules.flatMap { it.appointments }.firstOrNull { it.id == type }
            ?: throw NotFoundException("Unknown appointment $type")
        val existing = babies.appointments(babyId).firstOrNull { it.type == type }
        val base = existing ?: AppointmentRecord(Ids.new("a"), babyId, type)
        val updated = when (status) {
            "booked" -> base.copy(bookedOn = date ?: throw IllegalArgumentException("A booked date is needed"), doneOn = null)
            "done" -> base.copy(doneOn = date ?: today())
            "not_booked" -> base.copy(bookedOn = null, doneOn = null)
            "note" -> base
            else -> throw IllegalArgumentException("Status must be booked, done, not_booked or note")
        }.let { if (note != null) it.copy(note = note.trim().ifEmpty { null }) else it }
        babies.saveAppointment(updated)
        return updated
    }

    @Transactional
    fun recordVaccination(writer: Writer, babyId: String, doseCode: String, givenOn: LocalDate, note: String?): DoseRecord {
        checkBaby(writer, babyId)
        val schedule = content.schedule(families.family(writer.familyId)?.region ?: "england")
        schedule.doses.firstOrNull { it.code == doseCode } ?: throw NotFoundException("Unknown dose $doseCode")
        require(!givenOn.isAfter(today())) { "The date can't be in the future" }
        val existing = babies.doses(babyId).firstOrNull { it.doseCode == doseCode }
        val record = DoseRecord(existing?.id ?: Ids.new("v"), babyId, doseCode, givenOn, note?.trim()?.ifEmpty { null })
        babies.saveDose(record, schedule.version)
        return record
    }

    @Transactional
    fun removeVaccination(writer: Writer, babyId: String, doseCode: String) {
        checkBaby(writer, babyId)
        if (!babies.deleteDose(babyId, doseCode)) throw NotFoundException("No record of $doseCode")
    }

    // --- review notes ---

    @Transactional
    fun addReviewNote(writer: Writer, babyId: String, text: String): ReviewNote {
        checkBaby(writer, babyId)
        require(text.isNotBlank()) { "The note is empty" }
        val note = ReviewNote(Ids.new("n"), babyId, text.trim(), writer.carerId, clock.instant())
        babies.saveReviewNote(note)
        return note
    }

    @Transactional
    fun resolveReviewNote(writer: Writer, babyId: String, noteId: String, resolved: Boolean): ReviewNote {
        checkBaby(writer, babyId)
        val note = babies.reviewNotes(babyId).firstOrNull { it.id == noteId } ?: throw NotFoundException("No note $noteId")
        return note.copy(resolved = resolved).also(babies::saveReviewNote)
    }

    @Transactional
    fun deleteReviewNote(writer: Writer, babyId: String, noteId: String) {
        checkBaby(writer, babyId)
        if (!babies.deleteReviewNote(babyId, noteId)) throw NotFoundException("No note $noteId")
    }
}
