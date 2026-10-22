package io.github.lemonjelly76.babymilestones.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Vaccines
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import io.github.lemonjelly76.babymilestones.core.api.MomentRequest
import io.github.lemonjelly76.babymilestones.core.content.ContentBundle
import io.github.lemonjelly76.babymilestones.core.schedule.AppointmentStatus
import io.github.lemonjelly76.babymilestones.core.schedule.DoseStatus
import io.github.lemonjelly76.babymilestones.core.schedule.ScheduledAppointment
import io.github.lemonjelly76.babymilestones.core.timeline.TimelineKind
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
fun TimelineScreen(view: BabyView, content: ContentBundle, viewModel: AppViewModel, canEdit: Boolean, onSummary: () -> Unit) {
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<String?>(null) }
    var making by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            item {
                OutlinedButton(onClick = onSummary, modifier = Modifier.fillMaxWidth()) { Text("Health visitor summary and export") }
                OutlinedButton(
                    enabled = !making,
                    onClick = {
                        making = true
                        scope.launch {
                            runCatching { Export.memoryBook(context, view, viewModel::photo) }
                                .onSuccess { Export.shareFile(context, it, "application/pdf") }
                            making = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (making) "Making the memory book…" else "Memory book (PDF with photos)") }
            }
            items(view.timeline, key = { "${it.kind}_${it.refId}_${it.date}" }) { e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        when (e.kind) {
                            TimelineKind.BIRTH -> Icons.Outlined.Cake
                            TimelineKind.MILESTONE -> Icons.Outlined.CheckCircle
                            TimelineKind.MOMENT -> Icons.Outlined.Star
                            TimelineKind.APPOINTMENT -> Icons.Outlined.Event
                            TimelineKind.VACCINATION -> Icons.Outlined.Vaccines
                            TimelineKind.WORD -> Icons.Outlined.RecordVoiceOver
                            TimelineKind.TOOTH -> Icons.Outlined.Mood
                        },
                        null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 12.dp, top = 2.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(e.title, fontWeight = FontWeight.Medium)
                        Text(loggedDate(e.before, e.approximate, e.date), style = MaterialTheme.typography.bodySmall)
                        e.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        e.photoRef?.let { PhotoImage(it, viewModel::photo) }
                    }
                    if (e.kind == TimelineKind.MOMENT && canEdit) {
                        IconButton(onClick = { deleting = e.refId }) { Icon(Icons.Outlined.Delete, "Delete moment") }
                    }
                }
            }
            item { Box(Modifier.padding(bottom = 72.dp)) }
        }
        if (canEdit) ExtendedFloatingActionButton(
            onClick = { adding = true }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Add a moment") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
    if (adding) MomentDialog(viewModel, onSave = { viewModel.addMoment(view.baby.id, it) }, onDismiss = { adding = false })
    deleting?.let { id ->
        ConfirmDialog("Delete this moment?", "It will be removed from the timeline.", "Delete",
            { viewModel.deleteMoment(view.baby.id, id) }, { deleting = null })
    }
}

@Composable
private fun MomentDialog(viewModel: AppViewModel, onSave: (MomentRequest) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var note by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
            photo = viewModel.uploadPhoto(bytes, context.contentResolver.getType(uri) ?: "image/jpeg")
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("A moment to remember") },
        text = {
            Column(verticalArrangement = Gap) {
                OutlinedTextField(title, { title = it }, label = { Text("What happened?") }, placeholder = { Text("First tooth") }, singleLine = true)
                DateField("When", date, { it?.let { d -> date = d } })
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    photo?.let { PhotoImage(it, viewModel::photo, size = 56.dp) }
                    TextButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Icon(Icons.Outlined.AddAPhoto, null); Text(" Photo")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = { onSave(MomentRequest(title, date, note.ifBlank { null }, photo)); onDismiss() }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun HealthScreen(
    state: AppState,
    view: BabyView,
    content: ContentBundle,
    viewModel: AppViewModel,
    onSummary: () -> Unit,
    onGrowth: () -> Unit,
    onTeeth: () -> Unit,
) {
    var editing by remember { mutableStateOf<ScheduledAppointment?>(null) }
    var vaccine by remember { mutableStateOf<String?>(null) }
    var question by remember { mutableStateOf("") }
    val babyId = view.baby.id
    val rules = content.schedule(view.data.region)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).then(ScreenPadding)) {
        SectionTitle("Growth and teeth")
        GrowthCard(view, onGrowth)
        TeethCard(view, onTeeth)

        SectionTitle("Reviews and checks")
        view.schedule.appointments.forEach { a ->
            InfoCard(onClick = { editing = a }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(a.rule.title, fontWeight = FontWeight.SemiBold)
                        Text("${a.rule.with} · ${a.windowStart.format(DAY_SHORT)} – ${a.windowEnd.pretty()}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(when (a.status) {
                        AppointmentStatus.DONE -> "Done ${a.record?.doneOn?.format(DAY_SHORT) ?: ""}"
                        AppointmentStatus.BOOKED -> "Booked ${a.record?.bookedOn?.format(DAY_SHORT) ?: ""}"
                        AppointmentStatus.OPEN -> "Due now"
                        AppointmentStatus.PAST_WINDOW -> "Mark it"
                        AppointmentStatus.UPCOMING -> "Later"
                    }, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                a.record?.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }

        SectionTitle("Vaccinations")
        Text(rules.note, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
        view.schedule.doses.forEach { d ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(d.status == DoseStatus.GIVEN, {
                    if (d.status == DoseStatus.GIVEN) viewModel.removeVaccination(babyId, d.rule.code) else vaccine = d.rule.code
                })
                Column(Modifier.weight(1f)) {
                    Text("${d.rule.vaccine} · ${d.rule.dose}")
                    Text(
                        (d.record?.givenOn?.let { "Given ${it.pretty()}" } ?: "Due ${d.dueOn.pretty()}") + " · ${d.rule.protectsAgainst}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (d.status == DoseStatus.OVERDUE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        SectionTitle("Questions for your health visitor")
        view.data.reviewNotes.forEach { n ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(n.resolved, { viewModel.resolveReviewNote(babyId, n.id, it) })
                Text(n.text, modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.deleteReviewNote(babyId, n.id) }) { Icon(Icons.Outlined.Delete, "Delete") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(question, { question = it }, label = { Text("Add a question") }, modifier = Modifier.weight(1f))
            IconButton(enabled = question.isNotBlank(), onClick = { viewModel.addReviewNote(babyId, question); question = "" }) {
                Icon(Icons.Outlined.Add, "Add")
            }
        }
        OutlinedButton(onClick = onSummary, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Health visitor summary and export") }

        ContactsSection(state.me?.contacts.orEmpty(), state.canEdit, viewModel)
        Sources(content, (rules.appointments.flatMap { it.sources } + rules.doses.flatMap { it.sources }).distinct())
        Disclaimer(content)
    }

    editing?.let { a -> AppointmentDialog(a, onSave = { status, date, note -> viewModel.updateAppointment(babyId, a.rule.id, status, date, note) }) { editing = null } }
    vaccine?.let { code ->
        var given by remember { mutableStateOf<LocalDate?>(LocalDate.now()) }
        AlertDialog(
            onDismissRequest = { vaccine = null },
            title = { Text("When was it given?") },
            text = { DateField("Date given", given, { given = it }) },
            confirmButton = { TextButton(enabled = given != null, onClick = { viewModel.recordVaccination(babyId, code, given!!); vaccine = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { vaccine = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AppointmentDialog(a: ScheduledAppointment, onSave: (String, LocalDate?, String?) -> Unit, onDismiss: () -> Unit) {
    var date by remember { mutableStateOf(a.record?.doneOn ?: a.record?.bookedOn) }
    var note by remember { mutableStateOf(a.record?.note ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(a.rule.title) },
        text = {
            Column(verticalArrangement = Gap) {
                Text(a.rule.description, style = MaterialTheme.typography.bodyMedium)
                DateField("Date", date, { date = it }, max = null)
                OutlinedTextField(note, { note = it }, label = { Text("Notes for the health visitor") })
            }
        },
        confirmButton = {
            Row {
                TextButton(enabled = date != null, onClick = { onSave("booked", date, note); onDismiss() }) { Text("Booked") }
                TextButton(enabled = date == null || !date!!.isAfter(LocalDate.now()), onClick = { onSave("done", date, note); onDismiss() }) { Text("Done") }
            }
        },
        dismissButton = {
            TextButton(onClick = { onSave(if (a.record != null) "not_booked" else "note", null, note); onDismiss() }) {
                Text(if (a.record?.bookedOn != null || a.record?.doneOn != null) "Clear" else "Save note")
            }
        },
    )
}
