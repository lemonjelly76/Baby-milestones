package io.github.lemonjelly76.babymilestones.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.lemonjelly76.babymilestones.app.data.BabyView
import io.github.lemonjelly76.babymilestones.app.data.LogRequest
import io.github.lemonjelly76.babymilestones.core.content.ContentBundle
import io.github.lemonjelly76.babymilestones.core.content.Domain
import io.github.lemonjelly76.babymilestones.core.content.PeriodGuide
import io.github.lemonjelly76.babymilestones.core.tracking.MilestoneStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodGuideScreen(state: AppState, babyId: String, periodId: String, viewModel: AppViewModel, onBack: () -> Unit) {
    val view = state.view(babyId) ?: return
    val content = view.content
    val periods = content.periods
    val pager = rememberPagerState(initialPage = periods.indexOfFirst { it.id == periodId }.coerceAtLeast(0)) { periods.size }
    var open by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(periods[pager.currentPage].name) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
        )
    }) { padding ->
        HorizontalPager(pager, Modifier.padding(padding).fillMaxSize()) { page ->
            PeriodPage(view, content, periods[page], isCurrent = periods[page].id == view.snapshot.period.id, onOpen = { open = it }, viewModel)
        }
    }
    open?.let { MilestoneSheet(view, content, it, viewModel) { open = null } }
}

@Composable
private fun PeriodPage(view: BabyView, content: ContentBundle, period: PeriodGuide, isCurrent: Boolean, onOpen: (String) -> Unit, viewModel: AppViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).then(ScreenPadding)) {
        Text(
            "${period.fromMonths}–${period.toMonths} months" + if (isCurrent) " · ${view.baby.name} is here now"
            else " · from ${view.periodStart(period).pretty()}",
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp),
        )
        SectionTitle("What's typical now")
        Text(period.typical)
        SectionTitle("Play ideas")
        period.play.forEach { Text("• $it", modifier = Modifier.padding(vertical = 2.dp)) }
        SectionTitle("Sleep")
        Text(period.sleep)
        SectionTitle("Feeding")
        Text(period.feeding)
        SectionTitle("Safety for new abilities")
        InfoCard(color = MaterialTheme.colorScheme.secondaryContainer) {
            period.safety.forEach { Text("• $it", modifier = Modifier.padding(vertical = 2.dp)) }
        }
        if (period.touchpoints.isNotEmpty()) {
            SectionTitle("Appointments in this period")
            period.touchpoints.forEach { Text("• $it") }
        }
        SectionTitle("What happens next")
        Text(period.whatHappensNext)
        if (isCurrent) {
            val followOn = view.followOn()
            if (followOn.isNotEmpty()) {
                Text("Because of what ${view.baby.name} has done recently, watch for:", style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp))
                followOn.forEach { t -> MilestoneRow(t, onOpen = { onOpen(t.id) }, onTick = { viewModel.logMilestone(view.baby.id, LogRequest(t.id)) }) }
            }
        }
        SectionTitle("On the ${monthsLabel(period.checklistMonths)} checklist")
        view.snapshot.milestones.filter { it.milestone.expectedBy == period.checklistMonths }.forEach { t ->
            MilestoneRow(t, onOpen = { onOpen(t.id) }, onTick = null)
        }
        Sources(content, period.sources)
        Disclaimer(content)
    }
}

private enum class Grouping { DOMAIN, AGE }

@Composable
fun MilestonesScreen(view: BabyView, content: ContentBundle, viewModel: AppViewModel) {
    var grouping by rememberSaveable { mutableStateOf(Grouping.AGE) }
    var filter by rememberSaveable { mutableStateOf<MilestoneStatus?>(null) }
    var open by remember { mutableStateOf<String?>(null) }

    val shown = view.snapshot.milestones
        .filter { filter == null || it.status == filter }
        .filter { filter != null || it.status != MilestoneStatus.LATER || it.milestone.expectedBy <= view.snapshot.period.checklistMonths + 12 }

    Column(Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            SegmentedButton(grouping == Grouping.AGE, { grouping = Grouping.AGE }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("By age") }
            SegmentedButton(grouping == Grouping.DOMAIN, { grouping = Grouping.DOMAIN }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("By area") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Gap) {
            FilterChip(filter == null, { filter = null }, label = { Text("All") })
            listOf(MilestoneStatus.DUE_NOW, MilestoneStatus.NOT_SEEN_YET, MilestoneStatus.NOT_SURE, MilestoneStatus.COMING_UP,
                MilestoneStatus.ACHIEVED, MilestoneStatus.SKILL_LOST).forEach { s ->
                val count = view.snapshot.milestones.count { it.status == s }
                if (count > 0) FilterChip(filter == s, { filter = if (filter == s) null else s }, label = { Text("${s.label} ($count)") })
            }
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            val groups = when (grouping) {
                Grouping.AGE -> shown.groupBy { "By ${monthsLabel(it.milestone.expectedBy)}" }
                Grouping.DOMAIN -> Domain.entries.associate { d -> d.label to shown.filter { it.milestone.domain == d } }.filterValues { it.isNotEmpty() }
            }
            if (groups.isEmpty()) item { EmptyState("Nothing here yet.") }
            groups.forEach { (title, items) ->
                item(key = "h_$title") {
                    val done = items.count { it.status == MilestoneStatus.ACHIEVED }
                    Row {
                        SectionTitle(title, Modifier.weight(1f))
                        Text("$done of ${items.size}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 18.dp))
                    }
                }
                items(items, key = { "${title}_${it.id}" }) { t ->
                    MilestoneRow(t, onOpen = { open = t.id }, onTick = if (t.status == MilestoneStatus.LATER) null else {
                        { viewModel.logMilestone(view.baby.id, LogRequest(t.id)) }
                    })
                }
            }
            item {
                Text("Checklists from the CDC and AAP (2022): each lists what most children (75% or more) do by that age.",
                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 16.dp))
                Disclaimer(content)
            }
        }
    }
    open?.let { MilestoneSheet(view, content, it, viewModel) { open = null } }
}
