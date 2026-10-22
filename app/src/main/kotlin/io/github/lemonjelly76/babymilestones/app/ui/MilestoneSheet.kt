package io.github.lemonjelly76.babymilestones.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.lemonjelly76.babymilestones.app.data.BabyView
import io.github.lemonjelly76.babymilestones.app.data.LogRequest
import io.github.lemonjelly76.babymilestones.app.data.LogUpdateRequest
import io.github.lemonjelly76.babymilestones.core.content.ContentBundle
import io.github.lemonjelly76.babymilestones.core.tracking.LogState
import io.github.lemonjelly76.babymilestones.core.tracking.MilestoneStatus
import io.github.lemonjelly76.babymilestones.core.tracking.TrackedMilestone
import kotlinx.coroutines.launch
import java.time.LocalDate

/** A milestone row: tick it in one tap from the circle, or open it for details, a date, a note or a photo. */
@Composable
fun MilestoneRow(t: TrackedMilestone, onOpen: () -> Unit, onTick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onTick != null && t.status != MilestoneStatus.ACHIEVED) {
            IconButton(onClick = onTick) { Icon(Icons.Outlined.RadioButtonUnchecked, "Log ${t.milestone.title}") }
        } else {
            IconButton(onClick = onOpen) {
                Icon(
                    if (t.status == MilestoneStatus.ACHIEVED) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    null, tint = if (t.status == MilestoneStatus.ACHIEVED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(t.milestone.title, style = MaterialTheme.typography.bodyLarge)
            val sub = when (t.status) {
                MilestoneStatus.ACHIEVED -> t.log?.let { log -> log.achievedOn?.let { loggedDate(log.isUndatedCatchUp, log.approximate, it) } } ?: "Done"
                else -> "${t.milestone.domain.short()} · most by ${monthsLabel(t.milestone.expectedBy)}"
            }
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (t.status != MilestoneStatus.ACHIEVED) StatusPill(t.status)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MilestoneSheet(view: BabyView, content: ContentBundle, milestoneId: String, viewModel: AppViewModel, onDismiss: () -> Unit) {
    val t = view.snapshot.tracked(milestoneId) ?: return
    val m = t.milestone
    val log = t.log
    val babyId = view.baby.id
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var date by remember(milestoneId) { mutableStateOf(log?.achievedOn ?: LocalDate.now()) }
    var approximate by remember(milestoneId) { mutableStateOf(log?.approximate ?: false) }
    var note by remember(milestoneId) { mutableStateOf(log?.note ?: "") }
    var photoRef by remember(milestoneId) { mutableStateOf(log?.photoRef) }
    var confirmLost by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val type = context.contentResolver.getType(uri) ?: "image/jpeg"
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
            viewModel.uploadPhoto(bytes, type)?.let { photoRef = it }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Gap) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                StatusPill(t.status)
            }
            Text("${m.domain.label} · most children by ${monthsLabel(m.expectedBy)}" +
                if (t.status != MilestoneStatus.ACHIEVED) " (${t.expectedBy.pretty()} for ${view.baby.name})" else "",
                style = MaterialTheme.typography.bodySmall)
            Text(m.description)
            m.typicalWindow?.let {
                InfoCard(color = MaterialTheme.colorScheme.primaryContainer) {
                    Text("Typical range", fontWeight = FontWeight.SemiBold)
                    Text(it.label)
                }
            }
            if (m.activities.isNotEmpty()) {
                Text("Things to try", style = MaterialTheme.typography.titleSmall)
                m.activities.forEach { Text("• $it") }
            }
            m.safetyNote?.let {
                InfoCard(color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text("Safety", fontWeight = FontWeight.SemiBold)
                    Text(it)
                }
            }
            if (t.status == MilestoneStatus.NOT_SEEN_YET || t.status == MilestoneStatus.NOT_SURE) {
                InfoCard {
                    Text("If you haven't seen this yet", fontWeight = FontWeight.SemiBold)
                    Text(m.concernNote ?: content.concerns.notSeenOne.body.replace("{milestones}", m.title.replaceFirstChar(Char::lowercase)))
                    Text(content.concerns.notSeenOne.signpost, style = MaterialTheme.typography.bodySmall)
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(if (log?.state == LogState.ACHIEVED) "Logged" else "Log it",
                style = MaterialTheme.typography.titleSmall)
            DateField("When did you first see it?", date, { it?.let { d -> date = d } })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Not sure of the exact date", modifier = Modifier.weight(1f))
                Switch(approximate, { approximate = it })
            }
            OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Gap) {
                photoRef?.let { PhotoImage(it, viewModel::photo, size = 72.dp) }
                TextButton(onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Icon(Icons.Outlined.AddAPhoto, null)
                    Text(if (photoRef == null) " Add a photo" else " Change photo")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (log?.state == LogState.ACHIEVED) {
                    Button(onClick = {
                        viewModel.updateLog(babyId, log.id, LogUpdateRequest(date, approximate, note, photoRef))
                        onDismiss()
                    }) { Text("Save changes") }
                } else {
                    Button(onClick = {
                        viewModel.logMilestone(babyId, LogRequest(m.id, date, approximate, note.ifBlank { null }, photoRef))
                        onDismiss()
                    }) { Text("Seen it") }
                    OutlinedButton(onClick = { viewModel.notSure(babyId, m.id); onDismiss() }) { Text("Not sure") }
                }
            }
            if (log != null) {
                if (log.state == LogState.ACHIEVED) {
                    if (log.lostOn == null) {
                        TextButton(onClick = { confirmLost = true }) { Text("${view.baby.name} no longer does this") }
                    } else {
                        TextButton(onClick = {
                            viewModel.updateLog(babyId, log.id, LogUpdateRequest(clearLost = true)); onDismiss()
                        }) { Text("Doing it again") }
                    }
                }
                TextButton(onClick = { confirmRemove = true }) { Text("Remove this entry", color = MaterialTheme.colorScheme.error) }
            }
            Sources(content, m.sources)
            Disclaimer(content)
        }
    }

    if (confirmLost) ConfirmDialog(
        "No longer doing this?",
        "Losing a skill they had is always worth checking. We'll suggest contacting your health visitor or GP in the next few days.",
        "Mark as lost",
        { log?.let { viewModel.updateLog(babyId, it.id, LogUpdateRequest(lostOn = LocalDate.now())) }; onDismiss() },
        { confirmLost = false },
    )
    if (confirmRemove) ConfirmDialog(
        "Remove this entry?", "It will go from the log (a copy is kept in the history).", "Remove",
        { log?.let { viewModel.removeLog(babyId, it.id) }; onDismiss() }, { confirmRemove = false },
    )
}

fun loggedDate(before: Boolean, approximate: Boolean, date: LocalDate): String = when {
    before -> "Before ${date.pretty()}"
    approximate -> "Around ${date.pretty()}"
    else -> date.pretty()
}
