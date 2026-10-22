package io.github.lemonjelly76.babymilestones.server.service

import io.github.lemonjelly76.babymilestones.core.content.ContentBundle
import io.github.lemonjelly76.babymilestones.core.guides.ChecklistTick
import io.github.lemonjelly76.babymilestones.core.notes.AssistantNote
import io.github.lemonjelly76.babymilestones.core.notes.NoteKind
import io.github.lemonjelly76.babymilestones.server.store.BabyStore
import io.github.lemonjelly76.babymilestones.server.store.Ids
import io.github.lemonjelly76.babymilestones.server.store.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate

/** Guide checklist ticks, and the notes Claude saves for the family. */
@Service
class GuideService(
    private val babies: BabyStore,
    private val access: BabyAccess,
    private val content: ContentBundle,
    private val clock: Clock,
) {
    @Transactional
    fun setChecklistItem(writer: Writer, babyId: String, itemId: String, done: Boolean): ChecklistTick? {
        access.checkBaby(writer, babyId)
        content.checklistItem(itemId) ?: throw NotFoundException("Unknown checklist item $itemId")
        if (!done) {
            babies.deleteTick(babyId, itemId)
            return null
        }
        val tick = ChecklistTick(babyId, itemId, LocalDate.now(clock), writer.carerId)
        babies.saveTick(tick)
        return babies.ticks(babyId).first { it.itemId == itemId }
    }

    @Transactional
    fun saveNote(writer: Writer, babyId: String, kind: NoteKind, title: String, body: String): AssistantNote {
        access.checkBaby(writer, babyId)
        require(title.isNotBlank()) { "A title is needed" }
        require(body.isNotBlank()) { "The note is empty" }
        require(title.length <= AssistantNote.MAX_TITLE) { "Keep the title under ${AssistantNote.MAX_TITLE} characters" }
        require(body.length <= AssistantNote.MAX_BODY) { "Keep the note under ${AssistantNote.MAX_BODY} characters" }
        val note = AssistantNote(Ids.new("cn"), babyId, kind, title.trim(), body.trim(), writer.carerId, clock.instant())
        babies.saveAssistantNote(note)
        return note
    }

    @Transactional
    fun deleteNote(writer: Writer, babyId: String, noteId: String) {
        access.checkBaby(writer, babyId)
        if (!babies.deleteAssistantNote(babyId, noteId)) throw NotFoundException("No note $noteId")
    }
}
