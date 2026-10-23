package io.github.lemonjelly76.babymilestones.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.lemonjelly76.babymilestones.app.data.BabyView
import io.github.lemonjelly76.babymilestones.core.api.LogRequest
import io.github.lemonjelly76.babymilestones.core.content.ContentBundle
import io.github.lemonjelly76.babymilestones.core.schedule.AppointmentStatus
import io.github.lemonjelly76.babymilestones.core.schedule.DoseStatus
import io.github.lemonjelly76.babymilestones.core.tracking.MilestoneStatus
import io.github.lemonjelly76.babymilestones.core.weeks.WeekByWeek
import io.github.lemonjelly76.babymilestones.core.weeks.WeekPage
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Where a week page's links go. */
class WeekLinks(
    val guide: (String) -> Unit,
    val period: (String) -> Unit,
    val health: () -> Unit,
    val foods: () -> Unit,
)

/** Week by week, from birth to 5: swipe or use the arrows to move between weeks; everything links to its detail. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekScreen(state: AppState, babyId: String, startWeek: Int?, viewModel: AppViewModel, links: WeekLinks, onBack: () -> Unit) {
    val view = state.view(babyId) ?: return
    val pager = rememberPagerState(initialPage = startWeek ?: view.currentWeek) { WeekByWeek.LAST_WEEK + 1 }
    var open by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(view.week(pager.currentPage).title) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
        )
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            WeekStepper(view, pager)
            HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                WeekPageContent(view, view.week(page), viewModel, links, onOpenMilestone = { open = it })
            }
        }
    }
    open?.let { MilestoneSheet(view, view.content, it, viewModel) { open = null } }
}

@Composable
private fun WeekStepper(view: BabyView, pager: PagerState) {
    val scope = rememberCoroutineScope()
    val page = pager.currentPage
    val week = view.week(page)
    fun go(to: Int) = scope.launch { pager.animateScrollToPage(to.coerceIn(0, WeekByWeek.LAST_WEEK)) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { go(page - 1) }, enabled = page > 0) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Week before")
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(listOfNotNull(week.subtitle, weekDates(week)).joinToString(" · "), style = MaterialTheme.typography.labelLarge)
            Box(Modifier.height(40.dp), contentAlignment = Alignment.Center) {
                if (week.current) Text("This week", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                else TextButton(onClick = { go(view.currentWeek) }) { Text("Back to this week") }
            }
        }
        IconButton(onClick = { go(page + 1) }, enabled = page < WeekByWeek.LAST_WEEK) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Week after")
        }
    }
}

private val WEEK_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.UK)

private fun weekDates(week: WeekPage) = "${week.startsOn.format(WEEK_DAY)} to ${week.endsOn.format(WEEK_DAY)}"

@Composable
private fun WeekPageContent(view: BabyView, week: WeekPage, viewModel: AppViewModel, links: WeekLinks, onOpenMilestone: (String) -> Unit) {
    val content = view.content
    val name = view.baby.name
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).then(ScreenPadding)) {
        week.notes.forEach { note ->
            val guide = note.guide?.let(content::guide)
            InfoCard(onClick = guide?.let { { links.guide(it.id) } }) {
                Text(note.title, fontWeight = FontWeight.SemiBold)
                Text(note.text, modifier = Modifier.padding(top = 2.dp))
                guide?.let { LinkText("${it.title} ›") }
            }
        }

        if (week.appointments.isNotEmpty() || week.doses.isNotEmpty() || week.earlierDosesDue > 0) {
            SectionTitle("Checks and vaccinations")
            InfoCard(onClick = links.health) {
                week.appointments.forEach { a ->
                    Text(a.rule.title, fontWeight = FontWeight.Medium)
                    Text(when (a.status) {
                        AppointmentStatus.DONE -> "Done"
                        AppointmentStatus.BOOKED -> "Booked for ${a.record?.bookedOn?.pretty()}"
                        else -> "Usually ${a.windowStart.format(DAY_SHORT)} to ${a.windowEnd.pretty()} · ${a.rule.with}"
                    }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 6.dp))
                }
                week.doses.groupBy { it.dueOn }.forEach { (due, doses) ->
                    Text(doses.joinToString { it.rule.vaccine }, fontWeight = FontWeight.Medium)
                    Text(
                        if (doses.all { it.status == DoseStatus.GIVEN }) "Given" else "Due ${due.pretty()}",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                if (week.earlierDosesDue > 0) Text(
                    "${week.earlierDosesDue} earlier ${if (week.earlierDosesDue == 1) "vaccination isn't" else "vaccinations aren't"} " +
                        "marked as given yet.",
                    style = MaterialTheme.typography.bodySmall,
                )
                LinkText("Open Health ›")
            }
        }

        val checklist = week.milestones.firstOrNull()?.milestone?.expectedBy
        if (checklist != null) {
            SectionTitle(if (week.milestonesAreNext) "Next: by ${monthsLabel(checklist)}" else "Milestones for this age")
            Text(
                if (week.milestonesAreNext) "Nothing on the checklists is due this week. These come next."
                else "Most children do these by ${monthsLabel(checklist)}. Tap one for things to try.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            week.milestones.forEach { t ->
                MilestoneRow(t, onOpen = { onOpenMilestone(t.id) }, onTick = if (!view.canEdit || t.status == MilestoneStatus.LATER) null else {
                    { viewModel.logMilestone(view.baby.id, LogRequest(t.id)) }
                })
            }
        }

        if (week.playIdeas.isNotEmpty()) {
            SectionTitle("Play ideas")
            InfoCard(color = MaterialTheme.colorScheme.tertiaryContainer) {
                week.playIdeas.forEach { idea ->
                    Text("• ${idea.text}", modifier = Modifier.padding(vertical = 2.dp))
                    idea.milestoneTitle?.let { Text("   For: $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }

        if (week.food.isNotEmpty()) {
            SectionTitle("Food plan")
            InfoCard(onClick = links.foods) {
                week.food.forEach { day ->
                    Text("${day.date.format(PLAN_DAY)}: " + day.items.joinToString { it.progress.item.name +
                        if (it.progress.done) " (done)" else "" }, modifier = Modifier.padding(vertical = 2.dp))
                }
                LinkText("Open $name's foods ›")
            }
        }

        val guides = week.newGuides.mapNotNull(content::guide)
        if (guides.isNotEmpty()) {
            SectionTitle("New guides for this age")
            guides.forEach { GuideRow(it, view, onClick = { links.guide(it.id) }) }
        }

        content.period(week.periodId)?.let { period ->
            SectionTitle("This stage: ${period.name}, ${period.fromMonths} to ${period.toMonths} months")
            InfoCard(onClick = { links.period(period.id) }) {
                Text(period.typical)
                LinkText("Sleep, feeding, play and safety at this stage ›")
            }
        }

        Sources(content, week.notes.flatMap { it.sources }.distinct())
        Disclaimer(content)
        Spacer(Modifier.padding(bottom = 24.dp))
    }
}

private val PLAN_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)

@Composable
private fun LinkText(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
}
