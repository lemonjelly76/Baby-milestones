package io.github.lemonjelly76.babymilestones.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.lemonjelly76.babymilestones.app.data.BabyView
import io.github.lemonjelly76.babymilestones.core.api.TastingRequest
import io.github.lemonjelly76.babymilestones.core.api.TastingUpdateRequest
import io.github.lemonjelly76.babymilestones.core.content.ContentBundle
import io.github.lemonjelly76.babymilestones.core.food.AllergenState
import io.github.lemonjelly76.babymilestones.core.food.Food
import io.github.lemonjelly76.babymilestones.core.food.FoodGroup
import io.github.lemonjelly76.babymilestones.core.food.FoodNames
import io.github.lemonjelly76.babymilestones.core.food.FoodProgress
import io.github.lemonjelly76.babymilestones.core.food.FoodTasting
import io.github.lemonjelly76.babymilestones.core.food.Liking
import io.github.lemonjelly76.babymilestones.core.food.PlanProgress
import io.github.lemonjelly76.babymilestones.core.food.PlanSource
import io.github.lemonjelly76.babymilestones.core.food.PlannedFood
import java.time.LocalDate

private enum class FoodFilter(val label: String) {
    NEXT("Try next"),
    TRIED("Tried"),
    ALL("All foods"),
    ALLERGENS("Allergens"),
}

/** A food to open: a catalogue food, or one of the family's own by name. */
private data class FoodTarget(val key: String, val name: String, val group: FoodGroup, val food: Food?) {
    companion object {
        fun of(food: Food) = FoodTarget(food.id, food.name, food.group, food)
        fun own(name: String, group: FoodGroup) = FoodTarget(FoodNames.key(null, name), name.trim(), group, null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodsScreen(state: AppState, babyId: String, viewModel: AppViewModel, onBack: () -> Unit, onOpenGuide: (String) -> Unit) {
    val view = state.view(babyId) ?: return
    val content = view.content
    var filter by rememberSaveable { mutableStateOf(FoodFilter.NEXT) }
    var open by remember { mutableStateOf<FoodTarget?>(null) }
    var addOwn by remember { mutableStateOf(false) }

    fun target(p: FoodProgress) = p.foodId?.let(content::food)?.let(FoodTarget::of) ?: FoodTarget.own(p.name, p.group)
    fun quickLog(t: FoodTarget) = viewModel.logFood(
        babyId,
        TastingRequest(foodId = t.food?.id, name = if (t.food == null) t.name else null, group = if (t.food == null) t.group else null),
        "Logged ${t.name}",
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${view.baby.name}'s foods") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { addOwn = true }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Your own food") })
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).then(ScreenPadding)) {
            FoodsHeader(view)
            if (view.foods.reactions.isNotEmpty()) ReactionsCard(view, onOpenGuide)

            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Gap) {
                FoodFilter.entries.forEach { FilterChip(filter == it, { filter = it }, label = { Text(it.label) }) }
            }

            when (filter) {
                FoodFilter.NEXT -> {
                    FoodPlanSection(view, content, viewModel, state.canEdit, onOpen = { open = it }, onLog = ::quickLog)
                    TryNext(view, content, onOpen = { open = it }, onLog = ::quickLog, onOpenGuide = onOpenGuide)
                }
                FoodFilter.TRIED -> {
                    if (view.foods.foods.isEmpty()) EmptyState("Nothing logged yet. Tap + on any food to log it.")
                    view.foods.foods.forEach { p ->
                        FoodRow(
                            name = p.name,
                            detail = "Offered ${p.timesOffered} ${if (p.timesOffered == 1) "time" else "times"} · last ${p.lastOffered.format(DAY_SHORT)}" +
                                (p.lastLiking?.let { " · ${it.label.lowercase()}" } ?: "") +
                                (if (p.keepOffering) " · keep offering" else ""),
                            tried = true,
                            onOpen = { open = target(p) },
                            onLog = { quickLog(target(p)) },
                        )
                    }
                }
                FoodFilter.ALL -> AllFoods(view, content, onOpen = { open = it }, onLog = ::quickLog)
                FoodFilter.ALLERGENS -> Allergens(view, content, onOpen = { open = it }, onOpenGuide = onOpenGuide)
            }

            SectionTitle("Guides")
            listOf("guide.baby_led_weaning", "guide.allergens", "guide.foods_to_avoid", "guide.gagging_choking").mapNotNull(content::guide)
                .forEach { g ->
                    Text(g.title, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().clickable { onOpenGuide(g.id) }.padding(vertical = 8.dp))
                }
            Disclaimer(content)
            Text("", modifier = Modifier.padding(bottom = 64.dp))
        }
    }

    open?.let { FoodSheet(view, content, it, viewModel, state.canEdit, onDismiss = { open = null }) }
    if (addOwn) OwnFoodDialog(onDismiss = { addOwn = false }, onChoose = { name, group ->
        addOwn = false
        open = content.findFood(name)?.let(FoodTarget::of) ?: FoodTarget.own(name, group)
    })
}

@Composable
private fun FoodsHeader(view: BabyView) {
    val foods = view.foods
    InfoCard(color = MaterialTheme.colorScheme.primaryContainer) {
        Text("${foods.tried} ${if (foods.tried == 1) "food" else "foods"} tried", style = MaterialTheme.typography.titleLarge)
        Text("Variety and repetition matter more than a number: keep offering what isn't liked yet.",
            style = MaterialTheme.typography.bodySmall)
        foods.groups.filter { it.group != FoodGroup.OTHER }.forEach { g ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Text(g.group.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(140.dp))
                LinearProgressIndicator(
                    progress = { if (g.inCatalogue == 0) 0f else (g.tried.toFloat() / g.inCatalogue).coerceAtMost(1f) },
                    modifier = Modifier.weight(1f),
                )
                Text(" ${g.tried}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ReactionsCard(view: BabyView, onOpenGuide: (String) -> Unit) {
    InfoCard(color = MaterialTheme.colorScheme.errorContainer, onClick = { onOpenGuide("guide.allergens") }) {
        Text("Possible reactions you noted", fontWeight = FontWeight.SemiBold)
        view.foods.reactions.take(3).forEach { Text("• ${it.name}, ${it.date.format(DAY_SHORT)}: ${it.text}") }
        Text("Talk to your GP or health visitor before offering that food again. Call 999 if your child has breathing " +
            "problems or a swollen throat or tongue.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    }
}

/** The day-by-day plan as a checklist: an item ticks itself off when the food is logged, so ticking it logs the food. */
@Composable
private fun FoodPlanSection(
    view: BabyView, content: ContentBundle, viewModel: AppViewModel, canEdit: Boolean,
    onOpen: (FoodTarget) -> Unit, onLog: (FoodTarget) -> Unit,
) {
    val context = LocalContext.current
    val plan = view.plan
    var replacing by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    val claudePrompt = "Use Baby Milestones to make a 2-week food plan for ${view.baby.name}, and save it to the app."
    fun target(item: PlannedFood) = item.foodId?.let(content::food)?.let(FoodTarget::of) ?: FoodTarget.own(item.name, item.group)

    if (plan.isEmpty()) {
        if (!canEdit || view.foods.suggestions.isEmpty()) return
        InfoCard {
            Text("Make a food plan", fontWeight = FontWeight.SemiBold)
            Text("A day-by-day list of foods to try, following the same NHS-based rules. Each food ticks itself off when you log it.",
                style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Gap, modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = { viewModel.generatePlan(view.baby.id) }) { Text("Make a 2-week plan") }
                OutlinedButton(onClick = { openClaude(context, claudePrompt) }) { Text("Ask Claude") }
            }
        }
        return
    }

    val done = plan.count { it.done }
    val source = if (plan.any { it.item.source == PlanSource.CLAUDE }) "Made with Claude" else "Made by the app"
    SectionTitle("Food plan")
    Text("$done of ${plan.size} done · $source", style = MaterialTheme.typography.bodySmall)
    LinearProgressIndicator(progress = { done.toFloat() / plan.size }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
    val (earlier, coming) = plan.partition { it.item.plannedOn.isBefore(view.today) }
    val missed = earlier.filterNot { it.done }
    if (missed.isNotEmpty()) {
        Text("Earlier, not logged yet", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp))
        missed.forEach { PlanRow(it, canEdit, onOpen = { onOpen(target(it.item)) }, onLog = { onLog(target(it.item)) }) }
    }
    coming.groupBy { it.item.plannedOn }.forEach { (date, items) ->
        Text(dayLabel(date, view.today), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp))
        items.forEach { PlanRow(it, canEdit, onOpen = { onOpen(target(it.item)) }, onLog = { onLog(target(it.item)) }) }
    }
    if (canEdit) Row(horizontalArrangement = Gap, modifier = Modifier.padding(vertical = 8.dp)) {
        OutlinedButton(onClick = { replacing = true }) { Text("New plan") }
        OutlinedButton(onClick = { openClaude(context, claudePrompt) }) { Text("Ask Claude") }
        TextButton(onClick = { clearing = true }) { Text("Clear") }
    }
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    if (replacing) ConfirmDialog("Make a new plan?", "This replaces the current plan with two weeks from today.", "Make it",
        { viewModel.generatePlan(view.baby.id) }, { replacing = false })
    if (clearing) ConfirmDialog("Clear the plan?", "Foods you've logged stay in the tracker.", "Clear",
        { viewModel.clearPlan(view.baby.id) }, { clearing = false })
}

@Composable
private fun PlanRow(progress: PlanProgress, canEdit: Boolean, onOpen: () -> Unit, onLog: () -> Unit) {
    val item = progress.item
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(progress.done, { if (!progress.done) onLog() }, enabled = canEdit && !progress.done)
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (progress.done) TextDecoration.LineThrough else null)
            Text(progress.doneOn?.let { "Logged ${it.format(DAY_SHORT)}" } ?: item.reason ?: item.kind.label,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    else -> date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMM", java.util.Locale.UK))
}

@Composable
private fun TryNext(
    view: BabyView, content: ContentBundle, onOpen: (FoodTarget) -> Unit, onLog: (FoodTarget) -> Unit, onOpenGuide: (String) -> Unit,
) {
    val suggestions = view.foods.suggestions
    if (suggestions.isEmpty()) {
        InfoCard(onClick = { onOpenGuide("guide.ready_for_solids") }) {
            Text("The NHS recommends starting solid food at around 6 months. See the signs of readiness ›")
        }
        return
    }
    SectionTitle("Ideas right now")
    Text("Worked out from what ${view.baby.name} has tried, following NHS weaning advice.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    suggestions.forEach { s ->
        val food = content.food(s.foodId) ?: return@forEach
        FoodRow(food.name, s.reason, tried = view.foods.progress(food.id) != null,
            onOpen = { onOpen(FoodTarget.of(food)) }, onLog = { onLog(FoodTarget.of(food)) })
    }
}

@Composable
private fun AllFoods(view: BabyView, content: ContentBundle, onOpen: (FoodTarget) -> Unit, onLog: (FoodTarget) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(query, { query = it }, label = { Text("Search foods") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    val matches = content.searchFoods(query.ifBlank { null })
    FoodGroup.entries.forEach { group ->
        val inGroup = matches.filter { it.group == group }
        if (inGroup.isEmpty()) return@forEach
        SectionTitle(group.label)
        inGroup.forEach { f ->
            val tags = buildList {
                if (!f.suitableAt(view.ageMonths)) add("from ${f.fromMonths} months")
                if (f.allergens.isNotEmpty()) add(f.allergens.joinToString { it.label.lowercase() })
                if (f.ironRich) add("iron-rich")
                if (f.chokingRisk) add("prepare carefully")
            }
            FoodRow(f.name, tags.joinToString(" · ").ifEmpty { null }, tried = view.foods.progress(f.id) != null,
                onOpen = { onOpen(FoodTarget.of(f)) }, onLog = if (f.suitableAt(view.ageMonths)) ({ onLog(FoodTarget.of(f)) }) else null)
        }
    }
}

@Composable
private fun Allergens(view: BabyView, content: ContentBundle, onOpen: (FoodTarget) -> Unit, onOpenGuide: (String) -> Unit) {
    Text("One new allergen at a time, in a small amount. Once introduced, keep it in the usual diet.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    TextButton(onClick = { onOpenGuide("guide.allergens") }) { Text("Read the allergens guide") }
    view.foods.allergens.forEach { status ->
        val detail = when (status.state) {
            AllergenState.NOT_YET -> "Not introduced yet"
            AllergenState.INTRODUCED -> "Introduced · last offered ${status.lastOffered?.format(DAY_SHORT)}"
            AllergenState.DUE -> "Not offered for ${status.daysSinceLast} days: keep it going"
            AllergenState.REACTION -> "Possible reaction noted: talk to your GP or health visitor first"
        }
        InfoCard(color = if (status.state == AllergenState.REACTION) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(status.allergen.label, fontWeight = FontWeight.SemiBold)
                    Text(detail, style = MaterialTheme.typography.bodySmall)
                }
                if (status.state == AllergenState.INTRODUCED) Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
            }
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                content.foods.filter { status.allergen in it.allergens && it.suitableAt(view.ageMonths) }
                    .sortedBy { it.allergens.size }.take(4).forEach { f ->
                    TextButton(onClick = { onOpen(FoodTarget.of(f)) }) { Text(f.name) }
                }
            }
        }
    }
}

@Composable
private fun FoodRow(name: String, detail: String?, tried: Boolean, onOpen: () -> Unit, onLog: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                if (tried) Icon(Icons.Outlined.CheckCircle, "Tried", tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 6.dp))
            }
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (onLog != null) IconButton(onClick = onLog) { Icon(Icons.Outlined.Add, "Log $name today") }
    }
    HorizontalDivider()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FoodSheet(
    view: BabyView, content: ContentBundle, target: FoodTarget, viewModel: AppViewModel, canEdit: Boolean, onDismiss: () -> Unit,
) {
    val food = target.food
    val babyId = view.baby.id
    val months = view.ageMonths
    var date by remember { mutableStateOf<LocalDate?>(view.today) }
    var liking by remember { mutableStateOf<Liking?>(null) }
    var reaction by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<FoodTasting?>(null) }
    val history = view.data.tastings.filter { it.key == target.key }.sortedByDescending { it.tastedOn }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).then(ScreenPadding).padding(bottom = 32.dp)) {
            Text(target.name, style = MaterialTheme.typography.headlineSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                Tag(target.group.label)
                food?.allergens?.forEach { Tag("Allergen: ${it.label.lowercase()}") }
                if (food?.ironRich == true) Tag("Iron-rich")
                if (food?.chokingRisk == true) Tag("Prepare to lower choking risk")
            }

            if (food != null) {
                if (!food.suitableAt(months)) InfoCard(color = MaterialTheme.colorScheme.errorContainer) {
                    Text("Not until ${monthsLabel(food.fromMonths)}.", fontWeight = FontWeight.SemiBold)
                }
                Text("How to serve at ${monthsLabel(months)}", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                Text(food.servingAt(months).text)
                food.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
            }

            if (canEdit) {
                SectionTitle("Log a taste")
                DateField("When", date, { date = it })
                Row(horizontalArrangement = Gap, modifier = Modifier.padding(vertical = 8.dp)) {
                    Liking.entries.forEach { l -> FilterChip(liking == l, { liking = if (liking == l) null else l }, label = { Text(l.label) }) }
                }
                OutlinedTextField(reaction, { reaction = it }, label = { Text("Possible reaction (optional)") },
                    supportingText = { Text("What you noticed, in your words. Call 999 for breathing problems or a swollen throat or tongue.") },
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = {
                        viewModel.logFood(babyId, TastingRequest(
                            foodId = food?.id,
                            name = if (food == null) target.name else null,
                            group = if (food == null) target.group else null,
                            tastedOn = date,
                            liking = liking,
                            reaction = reaction.trim().ifEmpty { null },
                            note = note.trim().ifEmpty { null },
                        ), "Logged ${target.name}")
                        onDismiss()
                    },
                    enabled = food == null || food.suitableAt(months),
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("Log it") }
            }

            if (history.isNotEmpty()) {
                SectionTitle("Offered ${history.size} ${if (history.size == 1) "time" else "times"}")
                Text("Tap an entry to change it.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                history.forEach { t ->
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = canEdit) { editing = t }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(t.tastedOn.pretty() + (t.liking?.let { " · ${it.label.lowercase()}" } ?: ""))
                            t.reaction?.let { Text("Possible reaction: $it", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error) }
                            t.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                        if (canEdit) Icon(Icons.Outlined.Edit, "Change", tint = MaterialTheme.colorScheme.primary)
                    }
                    HorizontalDivider()
                }
            }
            food?.let { Sources(content, it.sources) }
        }
        // Inside the sheet, so the dialog always opens on top of it.
        editing?.let { t ->
            EditTastingDialog(t, onDismiss = { editing = null },
                onSave = { request -> viewModel.updateFood(babyId, t.id, request); editing = null },
                onDelete = { editing = null; deleting = t.id })
        }
    }
    deleting?.let { id ->
        ConfirmDialog("Remove this entry?", "It will no longer count towards ${target.name}.", "Remove",
            { viewModel.removeFood(babyId, id) }, { deleting = null })
    }
}

@Composable
private fun Tag(text: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(50)) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OwnFoodDialog(onDismiss: () -> Unit, onChoose: (String, FoodGroup) -> Unit) {
    var name by remember { mutableStateOf("") }
    var group by remember { mutableStateOf(FoodGroup.OTHER) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your own food") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Food or dish") }, singleLine = true)
                Text("Group", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FoodGroup.entries.forEach { g -> FilterChip(group == g, { group = g }, label = { Text(g.label) }) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onChoose(name, group) }, enabled = name.isNotBlank()) { Text("Next") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Changes how a past taste went, or the reaction and note; "Not recorded" clears how it went. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditTastingDialog(
    tasting: FoodTasting, onDismiss: () -> Unit, onSave: (TastingUpdateRequest) -> Unit, onDelete: () -> Unit,
) {
    var date by remember { mutableStateOf<LocalDate?>(tasting.tastedOn) }
    var liking by remember { mutableStateOf(tasting.liking) }
    var reaction by remember { mutableStateOf(tasting.reaction.orEmpty()) }
    var note by remember { mutableStateOf(tasting.note.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tasting.name) },
        text = {
            Column(verticalArrangement = Gap) {
                DateField("When", date, { date = it })
                FlowRow(horizontalArrangement = Gap) {
                    FilterChip(liking == null, { liking = null }, label = { Text("Not recorded") })
                    Liking.entries.forEach { l -> FilterChip(liking == l, { liking = l }, label = { Text(l.label) }) }
                }
                OutlinedTextField(reaction, { reaction = it }, label = { Text("Possible reaction") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(TastingUpdateRequest(
                    tastedOn = date, liking = liking, clearLiking = liking == null, reaction = reaction.trim(), note = note.trim(),
                ))
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
