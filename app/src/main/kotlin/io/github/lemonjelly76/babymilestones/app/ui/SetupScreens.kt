package io.github.lemonjelly76.babymilestones.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.lemonjelly76.babymilestones.app.data.BabyRequest
import io.github.lemonjelly76.babymilestones.app.data.LogRequest
import io.github.lemonjelly76.babymilestones.core.age.AgeEngine
import io.github.lemonjelly76.babymilestones.core.age.BabyDates
import io.github.lemonjelly76.babymilestones.core.tracking.Baby
import java.time.LocalDate

private val REGIONS = listOf("england" to "England", "scotland" to "Scotland", "wales" to "Wales", "northern_ireland" to "Northern Ireland")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(state: AppState, viewModel: AppViewModel, invite: Invite?, onInviteUsed: () -> Unit, modifier: Modifier = Modifier) {
    var server by rememberSaveable { mutableStateOf(state.serverUrl) }
    var joining by rememberSaveable { mutableStateOf(false) }
    var familyName by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var region by rememberSaveable { mutableStateOf("england") }
    var setupCode by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf("parent") }

    LaunchedEffect(invite) {
        invite?.let {
            joining = true
            code = it.code
            it.server?.let { s -> server = s }
            onInviteUsed()
        }
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Gap) {
        Text("Baby Milestones", style = MaterialTheme.typography.headlineMedium)
        Text("Track your baby's development from birth to 5, with guidance from the CDC, WHO and NHS.",
            style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(server, { server = it }, label = { Text("Family server address") },
            placeholder = { Text("https://home.example.ts.net:8445") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(!joining, { joining = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("New family") }
            SegmentedButton(joining, { joining = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Join with a code") }
        }

        OutlinedTextField(name, { name = it }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(email, { email = it }, label = { Text("Email (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))

        if (joining) {
            OutlinedTextField(code, { code = it.uppercase() }, label = { Text("Invite code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("You are a…", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Gap) {
                listOf("parent", "grandparent", "nanny", "carer").forEach {
                    FilterChip(role == it, { role = it }, label = { Text(it.replaceFirstChar(Char::uppercase)) })
                }
            }
            Button(
                onClick = { viewModel.setServer(server); viewModel.join(code, name, email.ifBlank { null }, role) },
                enabled = server.isNotBlank() && code.length >= 6 && name.isNotBlank() && !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Join family") }
        } else {
            OutlinedTextField(familyName, { familyName = it }, label = { Text("Family name") }, placeholder = { Text("The Smiths") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Where do you live? This picks the appointment and vaccination schedule.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Gap) {
                REGIONS.forEach { (id, label) -> FilterChip(region == id, { region = id }, label = { Text(label) }) }
            }
            OutlinedTextField(setupCode, { setupCode = it }, label = { Text("Setup code (if your server has one)") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    viewModel.setServer(server)
                    viewModel.createFamily(familyName, region, name, email.ifBlank { null }, setupCode.ifBlank { null })
                },
                enabled = server.isNotBlank() && name.isNotBlank() && !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Create family") }
        }
    }
}

/** Add a baby in under a minute: name, date of birth (or age), born early?, then tick off what's already happened. */
@Composable
fun OnboardingFlow(state: AppState, viewModel: AppViewModel, modifier: Modifier = Modifier, onDone: () -> Unit, onCancel: (() -> Unit)? = null) {
    var created by remember { mutableStateOf<Baby?>(null) }
    val baby = created
    if (baby == null) {
        BabyForm(
            title = if (state.babies.isEmpty()) "Add your baby" else "Add a baby",
            initial = null, busy = state.busy, modifier = modifier, onCancel = onCancel,
        ) { request -> viewModel.addBaby(request) { created = it } }
    } else {
        BackfillScreen(state, baby.id, viewModel, onDone = onDone)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBabyScreen(state: AppState, babyId: String, viewModel: AppViewModel, onBack: () -> Unit) {
    val baby = state.babies.firstOrNull { it.id == babyId } ?: return
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Edit ${baby.name}") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
        })
    }) { padding ->
        Column(Modifier.padding(padding)) {
            BabyForm(title = null, initial = baby, busy = state.busy, onCancel = null) {
                viewModel.updateBaby(baby.id, it)
                onBack()
            }
            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(horizontal = 24.dp)) {
                Text("Delete ${baby.name} and all their records", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (confirmDelete) ConfirmDialog(
        "Delete ${baby.name}?", "This removes every milestone, note, moment and photo for ${baby.name} from the server. It can't be undone.",
        "Delete", { viewModel.deleteBaby(baby.id); onBack() }, { confirmDelete = false },
    )
}

@Composable
private fun BabyForm(
    title: String?,
    initial: Baby?,
    busy: Boolean,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)?,
    onSave: (BabyRequest) -> Unit,
) {
    val today = LocalDate.now()
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var knowsDob by rememberSaveable { mutableStateOf(initial?.dobEstimated != true) }
    var dob by remember { mutableStateOf(initial?.dateOfBirth) }
    var months by rememberSaveable { mutableStateOf("") }
    var weeks by rememberSaveable { mutableStateOf("") }
    var early by rememberSaveable { mutableStateOf(initial?.dueDate != null) }
    var due by remember { mutableStateOf(initial?.dueDate) }
    var sex by rememberSaveable { mutableStateOf(initial?.sex) }

    val dateOfBirth = if (knowsDob) dob else {
        val m = months.toIntOrNull() ?: 0
        val w = weeks.toIntOrNull() ?: 0
        if (m == 0 && w == 0) null else AgeEngine.estimateDateOfBirth(today, m, w)
    }
    val ages = dateOfBirth?.let { AgeEngine.ages(BabyDates(it, if (early) due else null), today) }

    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Gap) {
        title?.let { Text(it, style = MaterialTheme.typography.headlineSmall) }
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(knowsDob, { knowsDob = true }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Date of birth") }
            SegmentedButton(!knowsDob, { knowsDob = false }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("I know their age") }
        }
        if (knowsDob) {
            DateField("Date of birth", dob, { dob = it })
        } else {
            Row(horizontalArrangement = Gap) {
                OutlinedTextField(months, { months = it.filter(Char::isDigit).take(2) }, label = { Text("Months") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                OutlinedTextField(weeks, { weeks = it.filter(Char::isDigit).take(1) }, label = { Text("and weeks") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            }
            Text("We'll estimate the date of birth and mark it as estimated until you confirm it.", style = MaterialTheme.typography.bodySmall)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Was ${name.ifBlank { "baby" }} born early?", modifier = Modifier.weight(1f))
            Switch(early, { early = it })
        }
        if (early) {
            DateField("Due date", due, { due = it }, max = null)
            Text("Born 3 or more weeks early, milestones use corrected age (age from the due date) until 2 years.",
                style = MaterialTheme.typography.bodySmall)
        }

        ages?.let {
            InfoCard(color = MaterialTheme.colorScheme.primaryContainer) {
                Text("${name.ifBlank { "Baby" }} is ${it.chronological.display()}", style = MaterialTheme.typography.titleMedium)
                it.corrected?.let { c -> Text("Corrected age: ${c.display()}") }
            }
        }

        Text("Sex (optional, for growth charts later)", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Gap) {
            listOf("female" to "Girl", "male" to "Boy", null to "Prefer not to say").forEach { (value, label) ->
                FilterChip(sex == value, { sex = value }, label = { Text(label) })
            }
        }

        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            onCancel?.let { TextButton(onClick = it) { Text("Cancel") } }
            Button(
                enabled = name.isNotBlank() && dateOfBirth != null && !dateOfBirth.isAfter(today) && (!early || due != null) && !busy,
                onClick = { onSave(BabyRequest(name.trim(), dateOfBirth!!, if (early) due else null, sex, !knowsDob)) },
            ) { Text(if (initial == null) "Continue" else "Save") }
        }
    }
}

/** Milestones expected before today's age as a checklist; ticked ones are saved as "before today". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackfillScreen(state: AppState, babyId: String, viewModel: AppViewModel, onDone: () -> Unit) {
    val view = state.view(babyId) ?: return
    val candidates = view.snapshot.backfillCandidates()
        .filter { !it.expectedBy.isAfter(view.today.plusWeeks(4)) }
        .sortedBy { it.milestone.expectedBy }
    val ticked = remember { mutableStateMapOf<String, Boolean>() }

    Scaffold(topBar = { TopAppBar(title = { Text("What has ${view.baby.name} done already?") }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text("These are milestones most babies reach before ${view.baby.name}'s age. Tick any you've seen; you can add dates later.",
                style = MaterialTheme.typography.bodyMedium, modifier = ScreenPadding)
            if (candidates.isEmpty()) EmptyState("Nothing to catch up on yet.")
            LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                candidates.groupBy { it.milestone.expectedBy }.forEach { (months, items) ->
                    item(key = "h$months") { SectionTitle("By ${monthsLabel(months)}") }
                    items(items, key = { it.id }) { t ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Checkbox(ticked[t.id] == true, { ticked[t.id] = it })
                            Column(Modifier.weight(1f)) {
                                Text(t.milestone.title)
                                Text(t.milestone.domain.short(), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = onDone) { Text("Skip") }
                Spacer(Modifier.padding(4.dp))
                Button(enabled = !state.busy, onClick = {
                    val entries = ticked.filterValues { it }.keys.map { LogRequest(it, approximate = true) }
                    viewModel.backfill(babyId, entries, onDone)
                }) { Text("Save ${ticked.count { it.value }}") }
            }
        }
    }
}

fun monthsLabel(months: Int): String = when {
    months < 24 -> "$months months"
    months % 12 == 0 -> "${months / 12} years"
    else -> "${months / 12}½ years"
}
