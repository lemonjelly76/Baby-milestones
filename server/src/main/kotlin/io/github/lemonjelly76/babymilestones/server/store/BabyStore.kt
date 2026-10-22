package io.github.lemonjelly76.babymilestones.server.store

import io.github.lemonjelly76.babymilestones.core.food.FoodGroup
import io.github.lemonjelly76.babymilestones.core.food.FoodTasting
import io.github.lemonjelly76.babymilestones.core.food.Liking
import io.github.lemonjelly76.babymilestones.core.guides.ChecklistTick
import io.github.lemonjelly76.babymilestones.core.json.BabyJson
import io.github.lemonjelly76.babymilestones.core.notes.AssistantNote
import io.github.lemonjelly76.babymilestones.core.notes.NoteKind
import io.github.lemonjelly76.babymilestones.core.schedule.AppointmentRecord
import io.github.lemonjelly76.babymilestones.core.schedule.DoseRecord
import io.github.lemonjelly76.babymilestones.core.summary.ReviewNote
import io.github.lemonjelly76.babymilestones.core.timeline.Moment
import io.github.lemonjelly76.babymilestones.core.tracking.Baby
import io.github.lemonjelly76.babymilestones.core.tracking.LogSource
import io.github.lemonjelly76.babymilestones.core.tracking.LogState
import io.github.lemonjelly76.babymilestones.core.tracking.MilestoneLog
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.LocalDate

/** Babies and everything recorded about them. Every query is scoped by family or by a baby already checked against one. */
@Repository
class BabyStore(private val jdbc: JdbcClient) {

    fun babies(familyId: String): List<Baby> =
        jdbc.sql("select * from baby where family_id = :familyId order by date_of_birth").param("familyId", familyId)
            .query { rs, _ -> baby(rs) }.list()

    fun baby(familyId: String, id: String): Baby? =
        jdbc.sql("select * from baby where id = :id and family_id = :familyId").param("id", id).param("familyId", familyId)
            .query { rs, _ -> baby(rs) }.optional().orElse(null)

    fun saveBaby(familyId: String, baby: Baby) {
        jdbc.sql(
            """
            insert into baby (id, family_id, name, date_of_birth, due_date, sex, dob_estimated)
            values (:id, :familyId, :name, :dob, :due, :sex, :estimated)
            on conflict (id) do update set name = excluded.name, date_of_birth = excluded.date_of_birth,
                due_date = excluded.due_date, sex = excluded.sex, dob_estimated = excluded.dob_estimated
            """,
        ).param("id", baby.id).param("familyId", familyId).param("name", baby.name).param("dob", baby.dateOfBirth)
            .param("due", baby.dueDate).param("sex", baby.sex).param("estimated", baby.dobEstimated).update()
    }

    fun deleteBaby(familyId: String, id: String): Boolean =
        jdbc.sql("delete from baby where id = :id and family_id = :familyId").param("id", id).param("familyId", familyId)
            .update() > 0

    // --- milestone log ---

    fun logs(babyId: String): List<MilestoneLog> =
        jdbc.sql("select * from milestone_log where baby_id = :babyId").param("babyId", babyId).query { rs, _ -> log(rs) }.list()

    fun log(babyId: String, logId: String): MilestoneLog? =
        jdbc.sql("select * from milestone_log where id = :id and baby_id = :babyId").param("id", logId).param("babyId", babyId)
            .query { rs, _ -> log(rs) }.optional().orElse(null)

    fun logForMilestone(babyId: String, milestoneId: String): MilestoneLog? =
        jdbc.sql("select * from milestone_log where baby_id = :babyId and milestone_id = :milestoneId")
            .param("babyId", babyId).param("milestoneId", milestoneId).query { rs, _ -> log(rs) }.optional().orElse(null)

    fun saveLog(log: MilestoneLog) {
        jdbc.sql(
            """
            insert into milestone_log (id, baby_id, milestone_id, state, achieved_on, approximate, marked_on, note,
                photo_ref, source, logged_by, lost_on, updated_at)
            values (:id, :babyId, :milestoneId, :state, :achievedOn, :approximate, :markedOn, :note, :photoRef, :source,
                :loggedBy, :lostOn, now())
            on conflict (baby_id, milestone_id) do update set id = excluded.id, state = excluded.state,
                achieved_on = excluded.achieved_on, approximate = excluded.approximate, marked_on = excluded.marked_on,
                note = excluded.note, photo_ref = excluded.photo_ref, source = excluded.source,
                logged_by = excluded.logged_by, lost_on = excluded.lost_on, updated_at = now()
            """,
        ).param("id", log.id).param("babyId", log.babyId).param("milestoneId", log.milestoneId)
            .param("state", enumName(log.state)).param("achievedOn", log.achievedOn).param("approximate", log.approximate)
            .param("markedOn", log.markedOn).param("note", log.note).param("photoRef", log.photoRef)
            .param("source", enumName(log.source)).param("loggedBy", log.loggedBy).param("lostOn", log.lostOn).update()
    }

    fun deleteLog(babyId: String, logId: String): Boolean =
        jdbc.sql("delete from milestone_log where id = :id and baby_id = :babyId").param("id", logId).param("babyId", babyId)
            .update() > 0

    fun recordHistory(log: MilestoneLog, action: String, changedBy: String?) {
        jdbc.sql(
            """
            insert into milestone_log_history (log_id, baby_id, action, snapshot, changed_by)
            values (:logId, :babyId, :action, cast(:snapshot as jsonb), :changedBy)
            """,
        ).param("logId", log.id).param("babyId", log.babyId).param("action", action)
            .param("snapshot", BabyJson.encodeToString(MilestoneLog.serializer(), log)).param("changedBy", changedBy).update()
    }

    fun history(babyId: String): List<Pair<String, MilestoneLog>> =
        jdbc.sql("select action, snapshot from milestone_log_history where baby_id = :babyId order by id")
            .param("babyId", babyId)
            .query { rs, _ -> rs.getString("action") to BabyJson.decodeFromString(MilestoneLog.serializer(), rs.getString("snapshot")) }
            .list()

    // --- moments ---

    fun moments(babyId: String): List<Moment> =
        jdbc.sql("select * from moment where baby_id = :babyId order by date desc").param("babyId", babyId)
            .query { rs, _ -> Moment(rs.getString("id"), rs.getString("baby_id"), rs.getString("title"), date(rs, "date")!!, rs.getString("note"), rs.getString("photo_ref")) }
            .list()

    fun saveMoment(moment: Moment, createdBy: String?) {
        jdbc.sql(
            """
            insert into moment (id, baby_id, title, date, note, photo_ref, created_by)
            values (:id, :babyId, :title, :date, :note, :photoRef, :createdBy)
            on conflict (id) do update set title = excluded.title, date = excluded.date, note = excluded.note,
                photo_ref = excluded.photo_ref
            """,
        ).param("id", moment.id).param("babyId", moment.babyId).param("title", moment.title).param("date", moment.date)
            .param("note", moment.note).param("photoRef", moment.photoRef).param("createdBy", createdBy).update()
    }

    fun deleteMoment(babyId: String, id: String): Boolean =
        jdbc.sql("delete from moment where id = :id and baby_id = :babyId").param("id", id).param("babyId", babyId).update() > 0

    // --- appointments and vaccinations ---

    fun appointments(babyId: String): List<AppointmentRecord> =
        jdbc.sql("select * from appointment where baby_id = :babyId").param("babyId", babyId)
            .query { rs, _ ->
                AppointmentRecord(rs.getString("id"), rs.getString("baby_id"), rs.getString("type"),
                    date(rs, "booked_on"), date(rs, "done_on"), rs.getString("note"))
            }.list()

    fun saveAppointment(record: AppointmentRecord) {
        jdbc.sql(
            """
            insert into appointment (id, baby_id, type, booked_on, done_on, note)
            values (:id, :babyId, :type, :bookedOn, :doneOn, :note)
            on conflict (baby_id, type) do update set booked_on = excluded.booked_on, done_on = excluded.done_on,
                note = excluded.note
            """,
        ).param("id", record.id).param("babyId", record.babyId).param("type", record.type)
            .param("bookedOn", record.bookedOn).param("doneOn", record.doneOn).param("note", record.note).update()
    }

    fun doses(babyId: String): List<DoseRecord> =
        jdbc.sql("select * from vaccination_dose where baby_id = :babyId").param("babyId", babyId)
            .query { rs, _ ->
                DoseRecord(rs.getString("id"), rs.getString("baby_id"), rs.getString("dose_code"), date(rs, "given_on")!!, rs.getString("note"))
            }.list()

    fun saveDose(record: DoseRecord, scheduleVersion: String) {
        jdbc.sql(
            """
            insert into vaccination_dose (id, baby_id, schedule_version, dose_code, given_on, note)
            values (:id, :babyId, :version, :code, :givenOn, :note)
            on conflict (baby_id, dose_code) do update set given_on = excluded.given_on, note = excluded.note
            """,
        ).param("id", record.id).param("babyId", record.babyId).param("version", scheduleVersion)
            .param("code", record.doseCode).param("givenOn", record.givenOn).param("note", record.note).update()
    }

    fun deleteDose(babyId: String, doseCode: String): Boolean =
        jdbc.sql("delete from vaccination_dose where baby_id = :babyId and dose_code = :code")
            .param("babyId", babyId).param("code", doseCode).update() > 0

    // --- review notes ---

    fun reviewNotes(babyId: String): List<ReviewNote> =
        jdbc.sql("select * from review_note where baby_id = :babyId order by created_at").param("babyId", babyId)
            .query { rs, _ ->
                ReviewNote(rs.getString("id"), rs.getString("baby_id"), rs.getString("text"), rs.getString("created_by"),
                    rs.getTimestamp("created_at").toInstant(), rs.getBoolean("resolved"))
            }.list()

    fun saveReviewNote(note: ReviewNote) {
        jdbc.sql(
            """
            insert into review_note (id, baby_id, text, created_by, created_at, resolved)
            values (:id, :babyId, :text, :createdBy, :createdAt, :resolved)
            on conflict (id) do update set text = excluded.text, resolved = excluded.resolved
            """,
        ).param("id", note.id).param("babyId", note.babyId).param("text", note.text).param("createdBy", note.createdBy)
            .param("createdAt", java.sql.Timestamp.from(note.createdAt)).param("resolved", note.resolved).update()
    }

    fun deleteReviewNote(babyId: String, id: String): Boolean =
        jdbc.sql("delete from review_note where id = :id and baby_id = :babyId").param("id", id).param("babyId", babyId).update() > 0

    // --- foods ---

    fun tastings(babyId: String): List<FoodTasting> =
        jdbc.sql("select * from food_tasting where baby_id = :babyId order by tasted_on, created_at").param("babyId", babyId)
            .query { rs, _ -> tasting(rs) }.list()

    fun tasting(babyId: String, id: String): FoodTasting? =
        jdbc.sql("select * from food_tasting where id = :id and baby_id = :babyId").param("id", id).param("babyId", babyId)
            .query { rs, _ -> tasting(rs) }.optional().orElse(null)

    fun saveTasting(t: FoodTasting) {
        jdbc.sql(
            """
            insert into food_tasting (id, baby_id, food_id, name, food_group, tasted_on, liking, reaction, note, photo_ref, logged_by)
            values (:id, :babyId, :foodId, :name, :group, :tastedOn, :liking, :reaction, :note, :photoRef, :loggedBy)
            on conflict (id) do update set tasted_on = excluded.tasted_on, liking = excluded.liking,
                reaction = excluded.reaction, note = excluded.note, photo_ref = excluded.photo_ref
            """,
        ).param("id", t.id).param("babyId", t.babyId).param("foodId", t.foodId).param("name", t.name)
            .param("group", enumName(t.group)).param("tastedOn", t.tastedOn).param("liking", t.liking?.let(::enumName))
            .param("reaction", t.reaction).param("note", t.note).param("photoRef", t.photoRef).param("loggedBy", t.loggedBy)
            .update()
    }

    fun deleteTasting(babyId: String, id: String): Boolean =
        jdbc.sql("delete from food_tasting where id = :id and baby_id = :babyId").param("id", id).param("babyId", babyId).update() > 0

    // --- checklist ticks ---

    fun ticks(babyId: String): List<ChecklistTick> =
        jdbc.sql("select * from checklist_tick where baby_id = :babyId order by ticked_on").param("babyId", babyId)
            .query { rs, _ -> ChecklistTick(rs.getString("baby_id"), rs.getString("item_id"), date(rs, "ticked_on")!!, rs.getString("ticked_by")) }
            .list()

    fun saveTick(tick: ChecklistTick) {
        jdbc.sql(
            """
            insert into checklist_tick (baby_id, item_id, ticked_on, ticked_by) values (:babyId, :itemId, :tickedOn, :tickedBy)
            on conflict (baby_id, item_id) do nothing
            """,
        ).param("babyId", tick.babyId).param("itemId", tick.itemId).param("tickedOn", tick.tickedOn)
            .param("tickedBy", tick.tickedBy).update()
    }

    fun deleteTick(babyId: String, itemId: String) {
        jdbc.sql("delete from checklist_tick where baby_id = :babyId and item_id = :itemId").param("babyId", babyId)
            .param("itemId", itemId).update()
    }

    // --- notes saved by Claude ---

    fun assistantNotes(babyId: String): List<AssistantNote> =
        jdbc.sql("select * from assistant_note where baby_id = :babyId order by created_at desc").param("babyId", babyId)
            .query { rs, _ ->
                AssistantNote(rs.getString("id"), rs.getString("baby_id"), enumOf<NoteKind>(rs.getString("kind")),
                    rs.getString("title"), rs.getString("body"), rs.getString("created_by"), rs.getTimestamp("created_at").toInstant())
            }.list()

    fun saveAssistantNote(note: AssistantNote) {
        jdbc.sql(
            """
            insert into assistant_note (id, baby_id, kind, title, body, created_by, created_at)
            values (:id, :babyId, :kind, :title, :body, :createdBy, :createdAt)
            """,
        ).param("id", note.id).param("babyId", note.babyId).param("kind", enumName(note.kind)).param("title", note.title)
            .param("body", note.body).param("createdBy", note.createdBy).param("createdAt", java.sql.Timestamp.from(note.createdAt))
            .update()
    }

    fun deleteAssistantNote(babyId: String, id: String): Boolean =
        jdbc.sql("delete from assistant_note where id = :id and baby_id = :babyId").param("id", id).param("babyId", babyId).update() > 0

    private fun tasting(rs: ResultSet) = FoodTasting(
        id = rs.getString("id"),
        babyId = rs.getString("baby_id"),
        foodId = rs.getString("food_id"),
        name = rs.getString("name"),
        group = enumOf<FoodGroup>(rs.getString("food_group")),
        tastedOn = date(rs, "tasted_on")!!,
        liking = rs.getString("liking")?.let { enumOf<Liking>(it) },
        reaction = rs.getString("reaction"),
        note = rs.getString("note"),
        photoRef = rs.getString("photo_ref"),
        loggedBy = rs.getString("logged_by"),
    )

    private fun baby(rs: ResultSet) = Baby(
        id = rs.getString("id"),
        name = rs.getString("name"),
        dateOfBirth = date(rs, "date_of_birth")!!,
        dueDate = date(rs, "due_date"),
        sex = rs.getString("sex"),
        dobEstimated = rs.getBoolean("dob_estimated"),
        trackingSince = rs.getTimestamp("created_at").toInstant().atZone(java.time.ZoneId.of("Europe/London")).toLocalDate(),
    )

    private fun log(rs: ResultSet) = MilestoneLog(
        id = rs.getString("id"),
        babyId = rs.getString("baby_id"),
        milestoneId = rs.getString("milestone_id"),
        state = enumOf<LogState>(rs.getString("state")),
        achievedOn = date(rs, "achieved_on"),
        approximate = rs.getBoolean("approximate"),
        markedOn = date(rs, "marked_on")!!,
        note = rs.getString("note"),
        photoRef = rs.getString("photo_ref"),
        source = enumOf<LogSource>(rs.getString("source")),
        loggedBy = rs.getString("logged_by"),
        lostOn = date(rs, "lost_on"),
    )

    private fun date(rs: ResultSet, column: String): LocalDate? = rs.getObject(column, LocalDate::class.java)

    private fun enumName(value: Enum<*>) = value.name.lowercase()
    private inline fun <reified E : Enum<E>> enumOf(value: String): E = enumValueOf(value.uppercase())
}
