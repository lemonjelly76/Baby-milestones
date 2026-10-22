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
    onOpenGuide: (String) -> Unit,
    onBackfill: () -> Unit,
    onAsk: () -> Unit,
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

        InfoCard(onClick = { onOpenGuide(period.id) }) {
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

        InfoCard {
            Text("Weekly check-in", fontWeight = FontWeight.SemiBold)
            Text("Go through what's due with the assistant: it asks, you answer, and it suggests updates for you to confirm.")
            Row(horizontalArrangement = Gap, modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = { viewModel.ask(view.baby.id, "Let's do this week's check-in for $name."); onAsk() }) { Text("Start") }
                if (view.notYetLogged > 3) OutlinedButton(onClick = onBackfill) { Text("Catch up on earlier ones") }
            }
        }
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
