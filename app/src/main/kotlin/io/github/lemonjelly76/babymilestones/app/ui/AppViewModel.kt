package io.github.lemonjelly76.babymilestones.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.lemonjelly76.babymilestones.app.data.ApiException
import io.github.lemonjelly76.babymilestones.app.data.BabyView
import io.github.lemonjelly76.babymilestones.app.data.LocalStore
import io.github.lemonjelly76.babymilestones.app.data.NotificationSettings
import io.github.lemonjelly76.babymilestones.app.data.Repository
import io.github.lemonjelly76.babymilestones.app.notify.Reminders
import io.github.lemonjelly76.babymilestones.core.api.AppointmentRequest
import io.github.lemonjelly76.babymilestones.core.api.BabyData
import io.github.lemonjelly76.babymilestones.core.api.BabyRequest
import io.github.lemonjelly76.babymilestones.core.api.ChatMessage
import io.github.lemonjelly76.babymilestones.core.api.ContactRequest
import io.github.lemonjelly76.babymilestones.core.api.CreateFamilyRequest
import io.github.lemonjelly76.babymilestones.core.api.GrowthRequest
import io.github.lemonjelly76.babymilestones.core.api.JoinRequest
import io.github.lemonjelly76.babymilestones.core.api.LogRequest
import io.github.lemonjelly76.babymilestones.core.api.LogUpdateRequest
import io.github.lemonjelly76.babymilestones.core.api.MeResponse
import io.github.lemonjelly76.babymilestones.core.api.MomentRequest
import io.github.lemonjelly76.babymilestones.core.api.Proposal
import io.github.lemonjelly76.babymilestones.core.api.SafetyCard
import io.github.lemonjelly76.babymilestones.core.api.TastingRequest
import io.github.lemonjelly76.babymilestones.core.api.TastingUpdateRequest
import io.github.lemonjelly76.babymilestones.core.api.ToothRequest
import io.github.lemonjelly76.babymilestones.core.api.UpdateFamilyRequest
import io.github.lemonjelly76.babymilestones.core.api.VaccinationRequest
import io.github.lemonjelly76.babymilestones.core.api.WordRequest
import io.github.lemonjelly76.babymilestones.core.content.ContentBundle
import io.github.lemonjelly76.babymilestones.core.safety.SafetyCheck
import io.github.lemonjelly76.babymilestones.core.tracking.Baby
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ChatEntry {
    data class Text(val message: ChatMessage) : ChatEntry
    data class Card(val card: SafetyCard) : ChatEntry
    data class Proposals(val items: List<Proposal>, val confirmed: Set<String> = emptySet(), val dismissed: Boolean = false) : ChatEntry
}

data class ChatState(val entries: List<ChatEntry> = emptyList(), val sending: Boolean = false) {
    /** What the model sees: the text turns only. Cards and chips stay on the phone. */
    val history: List<ChatMessage> get() = entries.filterIsInstance<ChatEntry.Text>().map { it.message }
}

data class AppState(
    val loading: Boolean = true,
    val signedIn: Boolean = false,
    val me: MeResponse? = null,
    val content: ContentBundle? = null,
    val babyData: Map<String, BabyData> = emptyMap(),
    val selectedBabyId: String? = null,
    val offline: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val chats: Map<String, ChatState> = emptyMap(),
    val today: LocalDate = LocalDate.now(),
    val notifications: NotificationSettings = NotificationSettings(),
    val serverUrl: String = "",
    val assistantEnabled: Boolean = false,
) {
    val babies: List<Baby> get() = me?.babies.orEmpty()
    val canEdit: Boolean get() = me?.carer?.access != "view"
    val selectedBaby: Baby? get() = babies.firstOrNull { it.id == selectedBabyId } ?: babies.firstOrNull()

    fun view(babyId: String? = selectedBaby?.id): BabyView? {
        val data = babyId?.let { babyData[it] } ?: return null
        return BabyView(data, content ?: return null, today)
    }

    fun chat(babyId: String): ChatState = chats[babyId] ?: ChatState()
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val store = LocalStore(application)
    val repository = Repository(store)
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state

    init {
        load()
    }

    private fun load() = viewModelScope.launch {
        val content = repository.content()
        val me = repository.cachedMe()
        val cached = me?.babies.orEmpty().mapNotNull { b -> repository.cachedData(b.id)?.let { b.id to it } }.toMap()
        _state.update {
            it.copy(
                loading = store.token != null && me == null,
                signedIn = store.token != null,
                me = me,
                content = content,
                babyData = cached,
                selectedBabyId = store.selectedBabyId,
                notifications = store.notifications,
                serverUrl = store.serverUrl,
                assistantEnabled = store.assistantEnabled,
            )
        }
        if (store.token != null) refresh()
    }

    /** Pulls everything from the server; on failure keeps the cached copy and shows the offline banner. */
    fun refresh() = viewModelScope.launch {
        try {
            val content = repository.refreshContent(_state.value.content ?: repository.content())
            val me = repository.refreshMe()
            val data = me.babies.associate { it.id to repository.refreshBaby(it.id) }
            _state.update { it.copy(loading = false, offline = false, me = me, content = content, babyData = data, today = LocalDate.now()) }
            Reminders.schedule(getApplication())
        } catch (e: ApiException) {
            if (e.status == 401) signOut("This phone has been signed out. Please sign in again.") else fail(e)
        } catch (e: IOException) {
            _state.update { it.copy(loading = false, offline = true) }
        }
    }

    private fun fail(e: Throwable) {
        val text = when (e) {
            is ApiException -> e.message
            is IOException -> "Can't reach the server. Check your connection and try again."
            else -> e.message
        } ?: "Something went wrong"
        _state.update { it.copy(busy = false, loading = false, message = text) }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    private fun action(success: String? = null, refreshBaby: String? = null, block: suspend () -> Unit) = viewModelScope.launch {
        _state.update { it.copy(busy = true) }
        try {
            block()
            refreshBaby?.let { id ->
                val data = repository.refreshBaby(id)
                _state.update { it.copy(babyData = it.babyData + (id to data)) }
            }
            _state.update { it.copy(busy = false, offline = false, message = success) }
        } catch (e: Exception) {
            fail(e)
        }
    }

    // --- setup and family ---

    fun setServer(url: String) {
        store.serverUrl = url
        _state.update { it.copy(serverUrl = store.serverUrl) }
    }

    fun createFamily(familyName: String, region: String, carerName: String, email: String?, setupCode: String?) = action {
        val signedIn = repository.api.createFamily(CreateFamilyRequest(familyName, region, carerName, email, setupCode))
        repository.signIn(signedIn)
        _state.update { it.copy(signedIn = true, loading = true) }
        refresh().join()
    }

    fun join(code: String, name: String, email: String?, role: String) = action {
        val signedIn = repository.api.join(JoinRequest(code, name, email, role))
        repository.signIn(signedIn)
        _state.update { it.copy(signedIn = true, loading = true) }
        refresh().join()
    }

    fun signOut(message: String? = null) {
        repository.signOut()
        Reminders.cancel(getApplication())
        _state.value = AppState(loading = false, content = repository.content(), message = message)
    }

    fun updateFamily(name: String?, region: String?) = action("Saved") {
        repository.api.updateFamily(UpdateFamilyRequest(name, region))
        val me = repository.refreshMe()
        _state.update { it.copy(me = me) }
    }

    fun deleteFamily() = action {
        repository.api.deleteFamily()
        signOut("Your family and everything in it has been deleted.")
    }

    suspend fun invite(access: String = "edit") = runCatching { repository.api.invite(access) }.onFailure(::fail).getOrNull()

    fun saveContact(id: String?, request: ContactRequest) = action("Saved") {
        repository.api.saveContact(id, request)
        _state.update { it.copy(me = repository.refreshMe()) }
    }

    fun deleteContact(id: String) = action("Deleted") {
        repository.api.deleteContact(id)
        _state.update { it.copy(me = repository.refreshMe()) }
    }

    suspend fun connectorToken() = runCatching { repository.api.newToken("claude") }.onFailure(::fail).getOrNull()

    suspend fun exportJson() = runCatching { repository.api.export() }.onFailure(::fail).getOrNull()

    fun setNotifications(settings: NotificationSettings) {
        store.notifications = settings
        _state.update { it.copy(notifications = settings) }
    }

    fun setAssistantEnabled(enabled: Boolean) {
        store.assistantEnabled = enabled
        _state.update { it.copy(assistantEnabled = enabled) }
    }

    // --- babies ---

    fun selectBaby(id: String) {
        store.selectedBabyId = id
        _state.update { it.copy(selectedBabyId = id) }
    }

    fun addBaby(request: BabyRequest, onCreated: (Baby) -> Unit) = action {
        val baby = repository.api.createBaby(request)
        val me = repository.refreshMe()
        val data = repository.refreshBaby(baby.id)
        store.selectedBabyId = baby.id
        _state.update { it.copy(me = me, babyData = it.babyData + (baby.id to data), selectedBabyId = baby.id) }
        onCreated(baby)
    }

    fun updateBaby(id: String, request: BabyRequest) = action("Saved", refreshBaby = id) {
        repository.api.updateBaby(id, request)
        val me = repository.refreshMe()
        _state.update { it.copy(me = me) }
    }

    fun deleteBaby(id: String) = action("Deleted") {
        repository.api.deleteBaby(id)
        val me = repository.refreshMe()
        _state.update { it.copy(me = me, babyData = it.babyData - id) }
    }

    // --- milestones ---

    fun logMilestone(babyId: String, request: LogRequest) = action("Logged", refreshBaby = babyId) {
        repository.api.log(babyId, request)
    }

    fun backfill(babyId: String, entries: List<LogRequest>, done: () -> Unit) = action(refreshBaby = babyId) {
        if (entries.isNotEmpty()) repository.api.backfill(babyId, entries)
        done()
    }

    fun updateLog(babyId: String, logId: String, request: LogUpdateRequest) = action("Saved", refreshBaby = babyId) {
        repository.api.updateLog(babyId, logId, request)
    }

    fun removeLog(babyId: String, logId: String) = action("Removed", refreshBaby = babyId) {
        repository.api.removeLog(babyId, logId)
    }

    fun notSure(babyId: String, milestoneId: String) = action("We'll ask again in 2 weeks", refreshBaby = babyId) {
        repository.api.notSure(babyId, milestoneId)
    }

    suspend fun uploadPhoto(bytes: ByteArray, contentType: String): String? =
        runCatching { repository.api.uploadPhoto(bytes, contentType) }.onFailure(::fail).getOrNull()

    suspend fun photo(id: String): ByteArray? = runCatching { repository.api.photo(id) }.getOrNull()

    // --- records ---

    fun addMoment(babyId: String, request: MomentRequest) = action("Added", refreshBaby = babyId) {
        repository.api.addMoment(babyId, request)
    }

    fun deleteMoment(babyId: String, momentId: String) = action("Removed", refreshBaby = babyId) {
        repository.api.deleteMoment(babyId, momentId)
    }

    fun updateAppointment(babyId: String, type: String, status: String, date: LocalDate?, note: String?) =
        action("Saved", refreshBaby = babyId) { repository.api.appointment(babyId, type, AppointmentRequest(status, date, note)) }

    fun recordVaccination(babyId: String, code: String, givenOn: LocalDate) = action("Saved", refreshBaby = babyId) {
        repository.api.vaccination(babyId, code, VaccinationRequest(givenOn))
    }

    fun removeVaccination(babyId: String, code: String) = action("Removed", refreshBaby = babyId) {
        repository.api.removeVaccination(babyId, code)
    }

    fun addReviewNote(babyId: String, text: String) = action("Added to your health visitor summary", refreshBaby = babyId) {
        repository.api.addReviewNote(babyId, text)
    }

    fun resolveReviewNote(babyId: String, noteId: String, resolved: Boolean) = action(refreshBaby = babyId) {
        repository.api.resolveReviewNote(babyId, noteId, resolved)
    }

    fun deleteReviewNote(babyId: String, noteId: String) = action(refreshBaby = babyId) {
        repository.api.deleteReviewNote(babyId, noteId)
    }

    // --- foods, checklists and Claude's notes ---

    fun logFood(babyId: String, request: TastingRequest, message: String = "Logged") = action(message, refreshBaby = babyId) {
        repository.api.logFood(babyId, request)
    }

    fun updateFood(babyId: String, tastingId: String, request: TastingUpdateRequest) = action("Saved", refreshBaby = babyId) {
        repository.api.updateFood(babyId, tastingId, request)
    }

    fun removeFood(babyId: String, tastingId: String) = action("Removed", refreshBaby = babyId) {
        repository.api.removeFood(babyId, tastingId)
    }

    fun setChecklistItem(babyId: String, itemId: String, done: Boolean) = action(refreshBaby = babyId) {
        repository.api.setChecklistItem(babyId, itemId, done)
    }

    fun deleteNote(babyId: String, noteId: String) = action("Deleted", refreshBaby = babyId) {
        repository.api.deleteNote(babyId, noteId)
    }

    // --- growth, first words and teeth ---

    fun logGrowth(babyId: String, request: GrowthRequest) = action("Saved", refreshBaby = babyId) {
        repository.api.logGrowth(babyId, request)
    }

    fun deleteGrowth(babyId: String, id: String) = action("Removed", refreshBaby = babyId) {
        repository.api.deleteGrowth(babyId, id)
    }

    fun logWord(babyId: String, request: WordRequest) = action("Added \u201c${request.word.trim()}\u201d", refreshBaby = babyId) {
        repository.api.logWord(babyId, request)
    }

    fun deleteWord(babyId: String, id: String) = action("Removed", refreshBaby = babyId) {
        repository.api.deleteWord(babyId, id)
    }

    fun logTooth(babyId: String, toothId: String, cameOn: LocalDate) = action("Saved", refreshBaby = babyId) {
        repository.api.logTooth(babyId, toothId, ToothRequest(cameOn))
    }

    fun deleteTooth(babyId: String, toothId: String) = action("Removed", refreshBaby = babyId) {
        repository.api.deleteTooth(babyId, toothId)
    }

    // --- assistant ---

    private fun updateChat(babyId: String, change: (ChatState) -> ChatState) =
        _state.update { it.copy(chats = it.chats + (babyId to change(it.chat(babyId)))) }

    fun ask(babyId: String, text: String) {
        val message = ChatMessage(ChatMessage.Role.USER, text.trim())
        if (message.text.isEmpty()) return
        updateChat(babyId) { it.copy(entries = it.entries + ChatEntry.Text(message)) }

        // The fixed safety check runs on the phone before anything is sent; the card comes from the app's content.
        val content = _state.value.content
        val check = content?.let { SafetyCheck(it.safety).classify(message.text) }
        if (check?.card != null) updateChat(babyId) { it.copy(entries = it.entries + ChatEntry.Card(SafetyCard(check.kind, check.card!!))) }
        if (check?.stopsAssistant == true) return

        updateChat(babyId) { it.copy(sending = true) }
        viewModelScope.launch {
            try {
                val reply = repository.api.ask(babyId, _state.value.chat(babyId).history)
                updateChat(babyId) { chat ->
                    val entries = chat.entries.toMutableList()
                    reply.safety?.takeIf { check?.card == null }?.let { entries += ChatEntry.Card(it) }
                    reply.reply?.let { entries += ChatEntry.Text(ChatMessage(ChatMessage.Role.ASSISTANT, it)) }
                    if (reply.proposals.isNotEmpty()) entries += ChatEntry.Proposals(reply.proposals)
                    chat.copy(entries = entries, sending = false)
                }
            } catch (e: Exception) {
                updateChat(babyId) { it.copy(sending = false) }
                fail(e)
            }
        }
    }

    /** Confirms the chosen chips in one go, as the spec's single confirmation sheet. */
    fun confirm(babyId: String, entryIndex: Int, chosen: List<Proposal>) = action(refreshBaby = babyId) {
        val results = repository.api.confirm(chosen)
        val ok = results.filter { it.ok }.map { it.proposalId }.toSet()
        updateChat(babyId) { chat ->
            chat.copy(entries = chat.entries.mapIndexed { i, entry ->
                if (i == entryIndex && entry is ChatEntry.Proposals) entry.copy(confirmed = entry.confirmed + ok) else entry
            })
        }
        val failed = results.filterNot { it.ok }
        if (failed.isNotEmpty()) _state.update { it.copy(message = failed.first().error ?: "Some changes couldn't be saved") }
        else _state.update { it.copy(message = if (ok.size == 1) "Saved" else "Saved ${ok.size} updates") }
    }

    fun dismissProposals(babyId: String, entryIndex: Int) = updateChat(babyId) { chat ->
        chat.copy(entries = chat.entries.mapIndexed { i, e -> if (i == entryIndex && e is ChatEntry.Proposals) e.copy(dismissed = true) else e })
    }

    fun clearChat(babyId: String) = updateChat(babyId) { ChatState() }
}
