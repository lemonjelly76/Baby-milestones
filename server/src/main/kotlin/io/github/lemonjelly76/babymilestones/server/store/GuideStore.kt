package io.github.lemonjelly76.babymilestones.server.store

import io.github.lemonjelly76.babymilestones.core.guides.ChecklistTick
import io.github.lemonjelly76.babymilestones.core.notes.AssistantNote
import io.github.lemonjelly76.babymilestones.core.notes.NoteKind
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

/** Guide checklist ticks and the notes Claude saves. Callers check the baby belongs to the family first. */
@Repository
class GuideStore(private val jdbc: JdbcClient) {

    fun ticks(babyId: String): List<ChecklistTick> =
        jdbc.sql("select * from checklist_tick where baby_id = :babyId order by ticked_on").param("babyId", babyId)
            .query { rs, _ -> ChecklistTick(rs.getString("baby_id"), rs.getString("item_id"), rs.date("ticked_on")!!, rs.getString("ticked_by")) }
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
                AssistantNote(rs.getString("id"), rs.getString("baby_id"), fromDb<NoteKind>(rs.getString("kind")),
                    rs.getString("title"), rs.getString("body"), rs.getString("created_by"), rs.getTimestamp("created_at").toInstant())
            }.list()

    fun saveAssistantNote(note: AssistantNote) {
        jdbc.sql(
            """
            insert into assistant_note (id, baby_id, kind, title, body, created_by, created_at)
            values (:id, :babyId, :kind, :title, :body, :createdBy, :createdAt)
            """,
        ).param("id", note.id).param("babyId", note.babyId).param("kind", dbName(note.kind)).param("title", note.title)
            .param("body", note.body).param("createdBy", note.createdBy).param("createdAt", java.sql.Timestamp.from(note.createdAt))
            .update()
    }

    fun deleteAssistantNote(babyId: String, id: String): Boolean =
        jdbc.sql("delete from assistant_note where id = :id and baby_id = :babyId").param("id", id).param("babyId", babyId).update() > 0
}
