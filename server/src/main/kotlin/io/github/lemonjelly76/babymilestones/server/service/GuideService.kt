package io.github.lemonjelly76.babymilestones.server.service

import io.github.lemonjelly76.babymilestones.core.content.ContentBundle
import io.github.lemonjelly76.babymilestones.core.guides.ChecklistTick
import io.github.lemonjelly76.babymilestones.core.notes.AssistantNote
import io.github.lemonjelly76.babymilestones.core.notes.NoteKind
import io.github.lemonjelly76.babymilestones.server.store.GuideStore
import io.github.lemonjelly76.babymilestones.server.store.Ids
import io.github.lemonjelly76.babymilestones.server.store.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate

/** Guide checklist ticks, and the notes Claude saves for the family. */
@Service
class GuideService(
    private val guideStore: GuideStore,
    private val access: BabyAccess,
    private val content: ContentBundle,
    private val clock: Clock,
) {
    @Transactional
    fun setChecklistItem(writer: Writer, babyId: String, itemId: String, done: Boolean): ChecklistTick? {
        access.checkBaby(writer, babyId)
        content.checklistItem(itemId) ?: throw NotFoundException("Unknown checklist item $itemId")
        if (!done) {
            guideStore.deleteTick(babyId, itemId)
            return null
        }
        val tick = ChecklistTick(babyId, itemId, LocalDate.now(clock), writer.carerId)
        guideStore.saveTick(tick)
        return guideStore.ticks(babyId).first { it.itemId == itemId }
    }

    @Transactional
    fun saveNote(writer: Writer, babyId: String, kind: NoteKind, title: String, body: String): AssistantNote {
        access.checkBaby(writer, babyId)
        val cleanTitle = title.trim()
        val cleanBody = body.trim()
        require(cleanTitle.isNotEmpty()) { "A title is needed" }
        require(cleanBody.isNotEmpty()) { "The note is empty" }
        require(cleanTitle.length <= AssistantNote.MAX_TITLE) { "Keep the title under ${AssistantNote.MAX_TITLE} characters" }
        require(cleanBody.length <= AssistantNote.MAX_BODY) { "Keep the note under ${AssistantNote.MAX_BODY} characters" }
        val note = AssistantNote(Ids.new("cn"), babyId, kind, cleanTitle, cleanBody, writer.carerId, clock.instant())
        guideStore.saveAssistantNote(note)
        return note
    }

    @Transactional
    fun deleteNote(writer: Writer, babyId: String, noteId: String) {
        access.checkBaby(writer, babyId)
        if (!guideStore.deleteAssistantNote(babyId, noteId)) throw NotFoundException("No note $noteId")
    }
}
