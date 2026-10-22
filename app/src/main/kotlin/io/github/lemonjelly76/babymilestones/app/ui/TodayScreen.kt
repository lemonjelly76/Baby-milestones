package io.github.lemonjelly76.babymilestones.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.lemonjelly76.babymilestones.app.data.BabyView
import io.github.lemonjelly76.babymilestones.app.data.LogRequest
import io.github.lemonjelly76.babymilestones.core.content.ContentBundle
import io.github.lemonjelly76.babymilestones.core.tracking.MilestoneStatus
import io.github.lemonjelly76.babymilestones.core.tracking.TrackedMilestone

/** Always answers three questions: what's typical now, what's coming next, and what's not yet logged. */
@Composable
fun TodayScreen(
    view: BabyView,
    content: ContentBundle,
    viewModel: AppViewModel,
    assistantEnabled: Boolean,
    onOpenPeriod: (String) -> Unit,
    onOpenGuide: (String) -> Unit,
    onBackfill: () -> Unit,
    onFoods: () -> Unit,
    onClaude: (chat: Boolean) -> Unit,
    onHealth: () -> Unit,
) {
    var open by remember { mutableStateOf<String?>(null) }
    val name = view.baby.name
    val ages = view.snapshot.ages
    val period = view.snapshot.period

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).then(ScreenPadding).padding(top = 8.dp)) {
        InfoCard(color = MaterialTheme.colorScheme.primaryContainer) {
            Text("$name is ${ages.chronological.display()}", style = MaterialTheme.typography.titleLarge)
            ages.corrected?.let {
                Text("Corrected age ${it.display()} (born ${ages.weeksEarly} weeks early). Milestones use corrected age until 2.",
                    style = MaterialTheme.typography.bodySmall)
            }
            if (view.baby.dobEstimated) Text("Date of birth is estimated: confirm it in Settings.", style = MaterialTheme.typography.bodySmall)
        }

        InfoCard(onClick = { onOpenPeriod(period.id) }) {
            Text("${period.name} · ${period.fromMonths}–${period.toMonths} months", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
            Text(period.typical, modifier = Modifier.padding(top = 4.dp))
            Text("Open the guide: play, sleep, feeding and safety ›", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
        }

        view.concerns.forEach { c ->
            InfoCard(color = urgencyColor(c.urgency)) {
                Text(c.title, fontWeight = FontWeight.SemiBold)
                Text(c.body)
                Text(c.signpost, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
            }
        }

        val aroundNow = view.aroundNow
        SectionTitle("Around now")
        if (aroundNow.isEmpty()) Text("Nothing due in the next few weeks. Have a look at what to watch for below.",
            style = MaterialTheme.typography.bodyMedium)
        aroundNow.forEach { t ->
            MilestoneRow(t, onOpen = { open = t.id }, onTick = { viewModel.logMilestone(view.baby.id, LogRequest(t.id)) })
        }

        val seenYet = view.seenYet.sortedByDescending { it.expectedBy }
        if (seenYet.isNotEmpty()) {
            SectionTitle("Seen this yet?")
            seenYet.take(3).forEach { SeenYetCard(it, viewModel, view.baby.id, onOpen = { open = it.id }) }
            if (seenYet.size > 3) TextButton(onClick = onBackfill) { Text("${seenYet.size - 3} more from earlier ages: catch up") }
        }

        val watch = view.watchFor.take(5)
        if (watch.isNotEmpty()) {
            SectionTitle("Watch for")
            watch.forEach { t -> MilestoneRow(t, onOpen = { open = t.id }, onTick = null) }
        }

        view.schedule.nextAppointment()?.let { a ->
            SectionTitle("Next appointment")
            InfoCard(onClick = onHealth) {
                Text(a.rule.title, fontWeight = FontWeight.SemiBold)
                Text(a.record?.bookedOn?.let { "Booked for ${it.pretty()}" }
                    ?: "Usually between ${a.windowStart.pretty()} and ${a.windowEnd.pretty()} · ${a.rule.with}")
            }
        }

        view.tip?.let {
            SectionTitle("Today's idea")
            InfoCard(color = MaterialTheme.colorScheme.tertiaryContainer) { Text(it) }
        }

        if (view.showFoods) FoodsCard(view, onFoods)

        view.schoolDates?.takeIf { it.applicationOpen(view.today) }?.let { dates ->
            InfoCard(color = MaterialTheme.colorScheme.tertiaryContainer, onClick = { onOpenGuide("guide.starting_school") }) {
                Text("School place for September ${dates.startsOn.year}", fontWeight = FontWeight.SemiBold)
                Text("Apply to your council by ${dates.applyBy.pretty()}.")
            }
        }

        view.data.assistantNotes.firstOrNull()?.takeIf {
            it.createdAt.atZone(java.time.ZoneId.systemDefault()).toLocalDate().isAfter(view.today.minusDays(7))
        }?.let { note ->
            InfoCard(onClick = { onClaude(false) }) {
                Text("Saved by Claude · ${note.kind.label}", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                Text(note.title, fontWeight = FontWeight.SemiBold)
            }
        }

        AskClaudeCard(view, assistantEnabled, onOpenClaude = { onClaude(false) }, onAskHere = {
            viewModel.ask(view.baby.id, "Let's do this week's check-in for $name.")
            onClaude(true)
        })
        if (view.notYetLogged > 3) TextButton(onClick = onBackfill) { Text("Catch up on earlier milestones") }
        Disclaimer(content)
    }

    open?.let { MilestoneSheet(view, content, it, viewModel) { open = null } }
}

@Composable
private fun SeenYetCard(t: TrackedMilestone, viewModel: AppViewModel, babyId: String, onOpen: () -> Unit) {
    InfoCard(onClick = onOpen) {
        Text(t.milestone.title, fontWeight = FontWeight.Medium)
        Text(
            if (t.status == MilestoneStatus.NOT_SURE) "You weren't sure last time. Have you seen it since?"
            else "Most children do this by ${monthsLabel(t.milestone.expectedBy)}, but many get there a little later. Tap for things to try.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            TextButton(onClick = { viewModel.logMilestone(babyId, LogRequest(t.id)) }) { Text("Yes") }
            TextButton(onClick = { viewModel.notSure(babyId, t.id) }) { Text("Not sure") }
            TextButton(onClick = onOpen) { Text("Not yet") }
        }
    }
}

@Composable
private fun FoodsCard(view: BabyView, onFoods: () -> Unit) {
    val foods = view.foods
    SectionTitle("Food")
    InfoCard(onClick = onFoods) {
        Text(
            if (foods.tried == 0) "Starting solids? Log each new food and get ideas for what to try next."
            else "${foods.tried} ${if (foods.tried == 1) "food" else "foods"} tried so far",
            fontWeight = FontWeight.SemiBold,
        )
        foods.allergensDue.take(2).forEach {
            Text("${it.allergen.label}: not offered for ${it.daysSinceLast} days. Keep it in the usual diet.",
                style = MaterialTheme.typography.bodySmall)
        }
        foods.suggestions.take(3).takeIf { it.isNotEmpty() }?.let { next ->
            Text("Try next: " + next.joinToString { it.name }, style = MaterialTheme.typography.bodyMedium)
        }
        Text("Log a food ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp))
    }
}
