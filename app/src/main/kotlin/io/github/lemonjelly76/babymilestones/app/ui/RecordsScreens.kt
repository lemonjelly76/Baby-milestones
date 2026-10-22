package io.github.lemonjelly76.babymilestones.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.lemonjelly76.babymilestones.app.data.BabyView
import io.github.lemonjelly76.babymilestones.core.api.ContactRequest
import io.github.lemonjelly76.babymilestones.core.api.GrowthRequest
import io.github.lemonjelly76.babymilestones.core.api.WordRequest
import io.github.lemonjelly76.babymilestones.core.growth.GrowthEngine
import io.github.lemonjelly76.babymilestones.core.growth.GrowthPoint
import io.github.lemonjelly76.babymilestones.core.growth.Measure
import io.github.lemonjelly76.babymilestones.core.records.Contact
import io.github.lemonjelly76.babymilestones.core.records.ContactRole
import io.github.lemonjelly76.babymilestones.core.records.Tooth
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

fun formatValue(measure: Measure, value: Double) =
    "%.${if (measure == Measure.WEIGHT) 2 else 1}f %s".format(Locale.UK, value, measure.unit)

fun centileText(centile: Double?): String? = centile?.let {
    when {
        it < 1 -> "below the 1st centile"
        it > 99 -> "above the 99th centile"
        else -> "about the ${ordinal(it.roundToInt())} centile"
    }
}

private fun ordinal(n: Int) = "$n" + if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }

// --- growth ---

@Composable
fun GrowthCard(view: BabyView, onOpen: () -> Unit) {
    val growth = view.growth
    InfoCard(onClick = onOpen) {
        Text("Growth", fontWeight = FontWeight.SemiBold)
        val latest = Measure.entries.mapNotNull { growth.latest(it) }
        if (latest.isEmpty()) Text("Add weights and measurements from the red book to see them on the growth chart.",
            style = MaterialTheme.typography.bodySmall)
        latest.forEach { p ->
            Text("${p.measure.label}: ${formatValue(p.measure, p.value)}, ${p.date.format(DAY_SHORT)}" +
                (centileText(p.centile)?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall)
        }
        growth.concerns.firstOrNull()?.let { Text(it.title, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge) }
        Text("Open the growth chart ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrowthScreen(state: AppState, babyId: String, viewModel: AppViewModel, onBack: () -> Unit) {
    val view = state.view(babyId) ?: return
    var measure by rememberSaveable { mutableStateOf(Measure.WEIGHT) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<String?>(null) }
    val growth = view.growth
    val points = growth.points.filter { it.measure == measure }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("${view.baby.name}'s growth") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } })
        },
        floatingActionButton = {
            if (state.canEdit) ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text("Add a measurement") })
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).then(ScreenPadding)) {
            growth.concerns.forEach { c ->
                InfoCard(color = urgencyColor(c.urgency)) {
                    Text(c.title, fontWeight = FontWeight.SemiBold)
                    Text(c.body)
                    Text(c.signpost, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Gap) {
                Measure.entries.forEach { FilterChip(measure == it, { measure = it }, label = { Text(it.label) }) }
            }
            val sex = view.baby.sex?.takeIf { it == "female" || it == "male" }
            if (sex == null) {
                InfoCard { Text("Add whether ${view.baby.name} is a girl or a boy in Settings to see the centile lines: the charts differ.") }
            } else {
                GrowthChart(view, measure, sex, points)
                Text(
                    "Lines are the 0.4th to 99.6th centiles of the WHO growth standard, as on the UK-WHO charts in the red book." +
                        if (growth.correctedForGestation) " Plotted from the due date, as the charts do for babies born before 37 weeks." else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SectionTitle("Measurements")
            if (points.isEmpty()) EmptyState("No ${measure.label.lowercase()} measurements yet.")
            points.sortedByDescending { it.date }.forEach { p ->
                val m = view.data.growth.first { it.id == p.measurementId }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                        Text(formatValue(measure, p.value), fontWeight = FontWeight.Medium)
                        Text(listOfNotNull(p.date.pretty(), m.measuredBy, centileText(p.centile)).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (state.canEdit) IconButton(onClick = { deleting = m.id }) { Icon(Icons.Outlined.Delete, "Remove") }
                }
                HorizontalDivider()
            }
            Text("A centile says how many in 100 children of the same age and sex weigh less or are shorter. Following " +
                "roughly the same line over time matters more than which line it is.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 12.dp))
            Sources(view.content, view.content.growth!!.sources)
            Disclaimer(view.content)
            Box(Modifier.padding(bottom = 72.dp))
        }
    }
    if (adding) GrowthDialog(onDismiss = { adding = false }, onSave = { viewModel.logGrowth(babyId, it); adding = false })
    deleting?.let { id ->
        ConfirmDialog("Remove this measurement?", "It's removed from the chart for everyone.", "Remove",
            { viewModel.deleteGrowth(babyId, id) }, { deleting = null })
    }
}

/** The centile lines for the age range in view, with the child's measurements on top. */
@Composable
private fun GrowthChart(view: BabyView, measure: Measure, sex: String, points: List<GrowthPoint>) {
    val engine = remember(view.content) { GrowthEngine(view.content.growth!!) }
    val lastDay = (points.maxOfOrNull { it.plotDays } ?: 0)
    val maxDays = maxOf(365, ((lastDay + 120) / 91 + 1) * 91).coerceAtMost(1826)
    val step = maxOf(7, maxDays / 60)
    val lines = GrowthEngine.CENTILE_LINES.map { it to engine.line(measure, sex, it.z, 0..maxDays step step) }
    val plotted = points.filter { it.z != null && it.plotDays in 0..maxDays }
    val values = lines.flatMap { (_, l) -> l.map { it.second } } + plotted.map { it.value }
    if (values.isEmpty()) return
    val yMin = values.min()
    val yMax = values.max()
    val lineColor = MaterialTheme.colorScheme.outline
    val medianColor = MaterialTheme.colorScheme.onSurfaceVariant
    val pointColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    Canvas(Modifier.fillMaxWidth().height(300.dp).padding(vertical = 8.dp)) {
        val left = 8.dp.toPx()
        val right = size.width - 36.dp.toPx()
        val top = 4.dp.toPx()
        val bottom = size.height - 20.dp.toPx()
        fun x(days: Int) = left + (right - left) * days / maxDays
        fun y(value: Double) = (bottom - (bottom - top) * (value - yMin) / (yMax - yMin)).toFloat()
        val paint = android.graphics.Paint().apply { color = labelColor; textSize = 10.dp.toPx(); isAntiAlias = true }

        lines.forEach { (line, pts) ->
            val path = Path()
            pts.forEachIndexed { i, (d, v) -> if (i == 0) path.moveTo(x(d), y(v)) else path.lineTo(x(d), y(v)) }
            val median = line.label == "50"
            drawPath(path, if (median) medianColor else lineColor, style = Stroke(width = (if (median) 2 else 1).dp.toPx()))
            pts.lastOrNull()?.let { (_, v) -> drawContext.canvas.nativeCanvas.drawText(line.label, right + 4.dp.toPx(), y(v) + 4.dp.toPx(), paint) }
        }
        val monthStep = if (maxDays <= 730) 3 else 12
        var month = 0
        while (month * 30.44 <= maxDays + 1) {
            val px = x((month * 30.44).toInt())
            drawLine(lineColor, Offset(px, bottom), Offset(px, bottom + 4.dp.toPx()))
            drawContext.canvas.nativeCanvas.drawText(if (monthStep == 12) "${month / 12}y" else "${month}m", px - 6.dp.toPx(), size.height - 4.dp.toPx(), paint)
            month += monthStep
        }
        val path = Path()
        plotted.sortedBy { it.plotDays }.forEachIndexed { i, p ->
            if (i == 0) path.moveTo(x(p.plotDays), y(p.value)) else path.lineTo(x(p.plotDays), y(p.value))
        }
        drawPath(path, pointColor, style = Stroke(width = 2.dp.toPx()))
        plotted.forEach { drawCircle(pointColor, 4.dp.toPx(), Offset(x(it.plotDays), y(it.value))) }
    }
}

@Composable
private fun GrowthDialog(onDismiss: () -> Unit, onSave: (GrowthRequest) -> Unit) {
    var date by remember { mutableStateOf<LocalDate?>(LocalDate.now()) }
    var weight by remember { mutableStateOf("") }
    var length by remember { mutableStateOf("") }
    var head by remember { mutableStateOf("") }
    var by by remember { mutableStateOf("Health visitor") }
    fun number(text: String) = text.replace(',', '.').trim().toDoubleOrNull()
    val ok = date != null && listOf(weight, length, head).any { number(it) != null }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a measurement") },
        text = {
            Column(verticalArrangement = Gap) {
                DateField("When", date, { date = it })
                val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                OutlinedTextField(weight, { weight = it }, label = { Text("Weight (kg)") }, keyboardOptions = decimal, singleLine = true)
                OutlinedTextField(length, { length = it }, label = { Text("Length or height (cm)") }, keyboardOptions = decimal, singleLine = true)
                OutlinedTextField(head, { head = it }, label = { Text("Head circumference (cm)") }, keyboardOptions = decimal, singleLine = true)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Gap) {
                    listOf("Health visitor", "GP", "Hospital", "Home").forEach { FilterChip(by == it, { by = it }, label = { Text(it) }) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = { onSave(GrowthRequest(date, number(weight), number(length), number(head), by)) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// --- teeth ---

@Composable
fun TeethCard(view: BabyView, onOpen: () -> Unit) {
    val teeth = view.data.teeth
    InfoCard(onClick = onOpen) {
        Text("Teeth", fontWeight = FontWeight.SemiBold)
        Text(
            if (teeth.isEmpty()) "Most babies get their first tooth at around 6 months. Tap to record each one."
            else "${teeth.size} of 20 milk teeth · first on ${teeth.minOf { it.cameOn }.pretty()}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeethScreen(state: AppState, babyId: String, viewModel: AppViewModel, onBack: () -> Unit, onOpenGuide: (String) -> Unit) {
    val view = state.view(babyId) ?: return
    var editing by remember { mutableStateOf<Tooth?>(null) }
    val cameOn = view.data.teeth.associateBy({ it.toothId }, { it.cameOn })
    Scaffold(topBar = {
        TopAppBar(title = { Text("${view.baby.name}'s teeth") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).then(ScreenPadding)) {
            Text("${cameOn.size} of 20 milk teeth. Bottom teeth usually come through before the matching top ones; most children " +
                "have all 20 by 2 to 3 years.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
            TextButton(onClick = { onOpenGuide("guide.teeth") }) { Text("Brushing and the first dentist visit ›") }
            listOf(false to "Lower teeth", true to "Upper teeth").forEach { (upper, title) ->
                SectionTitle(title)
                view.content.teeth.filter { it.upper == upper }.sortedBy { it.typicalMonths }.forEach { t ->
                    val date = cameOn[t.id]
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = state.canEdit) { editing = t }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (date != null) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null,
                            tint = if (date != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(end = 12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.name)
                            Text(date?.let { "Came through ${it.pretty()}" } ?: "Usually around ${monthsLabel(t.typicalMonths)}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Sources(view.content, view.content.teeth.flatMap { it.sources }.distinct())
            Disclaimer(view.content)
        }
    }
    editing?.let { t ->
        var date by remember(t.id) { mutableStateOf(cameOn[t.id] ?: LocalDate.now()) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(t.name) },
            text = { DateField("When it came through", date, { it?.let { d -> date = d } }) },
            confirmButton = { TextButton(onClick = { viewModel.logTooth(babyId, t.id, date); editing = null }) { Text("Save") } },
            dismissButton = {
                if (cameOn[t.id] != null) TextButton(onClick = { viewModel.deleteTooth(babyId, t.id); editing = null }) { Text("Remove") }
                else TextButton(onClick = { editing = null }) { Text("Cancel") }
            },
        )
    }
}

// --- words ---

@Composable
fun WordsCard(view: BabyView, onOpen: () -> Unit) {
    val words = view.data.words
    InfoCard(onClick = onOpen) {
        Text("Words", fontWeight = FontWeight.SemiBold)
        Text(
            if (words.isEmpty()) "Keep a list of the words ${view.baby.name} says, and when."
            else "${words.size} ${if (words.size == 1) "word" else "words"} · newest: " +
                words.sortedByDescending { it.saidOn }.take(3).joinToString { "“${it.word}”" },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordsScreen(state: AppState, babyId: String, viewModel: AppViewModel, onBack: () -> Unit) {
    val view = state.view(babyId) ?: return
    var word by rememberSaveable { mutableStateOf("") }
    var date by remember { mutableStateOf<LocalDate?>(LocalDate.now()) }
    var deleting by remember { mutableStateOf<String?>(null) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("${view.baby.name}'s words") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).then(ScreenPadding)) {
            if (state.canEdit) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(word, { word = it }, label = { Text("New word") }, singleLine = true, modifier = Modifier.weight(1f))
                    IconButton(enabled = word.isNotBlank() && date != null, onClick = {
                        viewModel.logWord(babyId, WordRequest(word, date))
                        word = ""
                    }) { Icon(Icons.Outlined.Add, "Add") }
                }
                DateField("First said", date, { date = it })
            }
            Text("Count a word when it's used with meaning, even if it's not said perfectly, like “ba” for ball.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
            SectionTitle("${view.data.words.size} ${if (view.data.words.size == 1) "word" else "words"}")
            view.data.words.sortedByDescending { it.saidOn }.forEach { w ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                        Text("“${w.word}”", style = MaterialTheme.typography.bodyLarge)
                        Text(w.saidOn.pretty() + (w.note?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall)
                    }
                    if (state.canEdit) IconButton(onClick = { deleting = w.id }) { Icon(Icons.Outlined.Delete, "Remove") }
                }
                HorizontalDivider()
            }
            Disclaimer(view.content)
        }
    }
    deleting?.let { id ->
        ConfirmDialog("Remove this word?", "It's removed from the list and the timeline.", "Remove",
            { viewModel.deleteWord(babyId, id) }, { deleting = null })
    }
}

// --- contacts ---

@Composable
fun ContactsSection(contacts: List<Contact>, canEdit: Boolean, viewModel: AppViewModel) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Contact?>(null) }
    var adding by remember { mutableStateOf(false) }
    SectionTitle("Contacts")
    if (contacts.isEmpty()) Text("Keep your health visitor, GP surgery and nursery numbers here.", style = MaterialTheme.typography.bodySmall)
    contacts.forEach { c ->
        Row(Modifier.fillMaxWidth().clickable(enabled = canEdit) { editing = c }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.name, fontWeight = FontWeight.Medium)
                Text(listOfNotNull(c.role.label, c.phone, c.email).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                c.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            c.phone?.let { phone ->
                IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.filter { it.isDigit() || it == '+' }}"))) }) {
                    Icon(Icons.Outlined.Call, "Call ${c.name}")
                }
            }
        }
    }
    if (canEdit) OutlinedButton(onClick = { adding = true }, modifier = Modifier.padding(top = 4.dp)) { Text("Add a contact") }

    if (adding || editing != null) ContactDialog(
        editing,
        onDismiss = { adding = false; editing = null },
        onSave = { request -> viewModel.saveContact(editing?.id, request); adding = false; editing = null },
        onDelete = editing?.let { c -> { viewModel.deleteContact(c.id); editing = null } },
    )
}

@Composable
private fun ContactDialog(contact: Contact?, onDismiss: () -> Unit, onSave: (ContactRequest) -> Unit, onDelete: (() -> Unit)?) {
    var role by remember { mutableStateOf(contact?.role ?: ContactRole.HEALTH_VISITOR) }
    var name by remember { mutableStateOf(contact?.name.orEmpty()) }
    var phone by remember { mutableStateOf(contact?.phone.orEmpty()) }
    var email by remember { mutableStateOf(contact?.email.orEmpty()) }
    var note by remember { mutableStateOf(contact?.note.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (contact == null) "Add a contact" else "Edit contact") },
        text = {
            Column(verticalArrangement = Gap) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Gap) {
                    ContactRole.entries.forEach { FilterChip(role == it, { role = it }, label = { Text(it.label) }) }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                OutlinedTextField(note, { note = it }, label = { Text("Note") })
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onSave(ContactRequest(role, name, phone.ifBlank { null }, email.ifBlank { null }, note.ifBlank { null }))
            }) { Text("Save") }
        },
        dismissButton = {
            if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            else TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
