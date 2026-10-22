package io.github.lemonjelly76.babymilestones.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.lemonjelly76.babymilestones.app.data.BabyView
import io.github.lemonjelly76.babymilestones.core.content.ContentBundle
import io.github.lemonjelly76.babymilestones.core.food.AllergenState
import io.github.lemonjelly76.babymilestones.core.guides.Guide
import io.github.lemonjelly76.babymilestones.core.guides.GuideFeature
import io.github.lemonjelly76.babymilestones.core.guides.GuideTopic
import io.github.lemonjelly76.babymilestones.core.guides.Guides

/** Practical guides for the child's age, with the foods tracker on top while they're weaning. */
@Composable
fun GuidesScreen(view: BabyView, content: ContentBundle, onOpenGuide: (String) -> Unit, onOpenFoods: () -> Unit) {
    val forAge = view.guides
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).then(ScreenPadding).padding(top = 8.dp)) {
        if (view.showFoods) FoodsSummaryCard(view, onOpenFoods)

        SectionTitle("For ${view.baby.name} now")
        if (forAge.now.isEmpty()) Text("Nothing for this age yet.", style = MaterialTheme.typography.bodyMedium)
        forAge.now.forEach { GuideCard(it, view, onClick = { onOpenGuide(it.id) }) }

        if (forAge.comingUp.isNotEmpty()) {
            SectionTitle("Coming up")
            forAge.comingUp.forEach { GuideCard(it, view, onClick = { onOpenGuide(it.id) }, from = true) }
        }

        val shown = (forAge.now + forAge.comingUp).map { it.id }.toSet()
        val others = content.guides.filter { it.id !in shown }
        if (others.isNotEmpty()) {
            SectionTitle("All guides")
            GuideTopic.entries.forEach { topic ->
                val inTopic = others.filter { it.topic == topic }
                if (inTopic.isNotEmpty()) {
                    Text(topic.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp))
                    inTopic.forEach { g ->
                        Text("${g.title} · ${g.fromMonths}–${g.toMonths} months", style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().clickable { onOpenGuide(g.id) }.padding(vertical = 8.dp))
                    }
                }
            }
        }
        Disclaimer(content)
    }
}

@Composable
private fun FoodsSummaryCard(view: BabyView, onOpenFoods: () -> Unit) {
    val foods = view.foods
    InfoCard(color = MaterialTheme.colorScheme.primaryContainer, onClick = onOpenFoods) {
        Text("Foods tracker", style = MaterialTheme.typography.labelLarge)
        Text(
            if (foods.tried == 0) "Log the foods ${view.baby.name} tries, and get ideas for what to offer next."
            else "${foods.tried} ${if (foods.tried == 1) "food" else "foods"} tried · " +
                "${foods.allergensIntroduced.size} of ${foods.allergens.size} allergens introduced",
            style = MaterialTheme.typography.titleMedium,
        )
        foods.allergensDue.firstOrNull()?.let {
            Text("${it.allergen.label} not offered for ${it.daysSinceLast} days", style = MaterialTheme.typography.bodySmall)
        }
        if (foods.allergens.any { it.state == AllergenState.REACTION }) {
            Text("You noted a possible reaction: see the foods tracker.", style = MaterialTheme.typography.bodySmall)
        }
        foods.suggestions.take(3).takeIf { it.isNotEmpty() }?.let { next ->
            Text("Try next: " + next.joinToString { it.name }, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp))
        }
        Text("Open the foods tracker ›", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun GuideCard(guide: Guide, view: BabyView, onClick: () -> Unit, from: Boolean = false) {
    val progress = Guides.progress(guide, view.data.checklist)
    InfoCard(onClick = onClick) {
        Text(guide.title, fontWeight = FontWeight.SemiBold)
        Text(guide.summary, style = MaterialTheme.typography.bodySmall)
        val meta = buildList {
            add(guide.topic.label)
            if (from) add("from ${guide.fromMonths} months")
            if (progress.total > 0) add("${progress.done} of ${progress.total} ticked")
        }
        Text(meta.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(state: AppState, babyId: String, guideId: String, viewModel: AppViewModel, onBack: () -> Unit, onOpenFoods: () -> Unit) {
    val view = state.view(babyId) ?: return
    val content = view.content
    val guide = content.guide(guideId) ?: return
    val ticked = view.ticked

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(guide.title, maxLines = 1) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
        )
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).then(ScreenPadding)) {
            Text("${guide.topic.label} · ${guide.fromMonths}–${guide.toMonths} months", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            Text(guide.summary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp))

            guide.card?.let { card ->
                InfoCard(color = urgencyColor(card.urgency)) {
                    Text(card.title, fontWeight = FontWeight.SemiBold)
                    Text(card.body)
                    Text(card.signpost, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                }
            }

            if (guide.feature == GuideFeature.SCHOOL_DATES) view.schoolDates?.let { dates ->
                InfoCard(color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Text("${view.baby.name}'s dates in England", fontWeight = FontWeight.SemiBold)
                    Text("Can start reception: September ${dates.startsOn.year}")
                    Text("Apply by: ${dates.applyBy.pretty()}")
                    Text("Offers: ${dates.offersOn.pretty()} (or the next working day)")
                }
            }

            guide.sections.forEach { section ->
                section.heading?.let { SectionTitle(it) }
                section.paragraphs.forEach { Text(it, modifier = Modifier.padding(vertical = 4.dp)) }
                section.bullets.forEach { Text("• $it", modifier = Modifier.padding(vertical = 2.dp)) }
            }

            if (guide.feature == GuideFeature.FOODS) {
                Button(onClick = onOpenFoods, modifier = Modifier.padding(top = 12.dp)) { Text("Open the foods tracker") }
            }

            if (guide.checklist.isNotEmpty()) {
                SectionTitle("Checklist")
                val progress = Guides.progress(guide, view.data.checklist)
                LinearProgressIndicator(
                    progress = { if (progress.total == 0) 0f else progress.done.toFloat() / progress.total },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                )
                guide.checklist.forEach { item ->
                    val done = item.id in ticked
                    Row(
                        Modifier.fillMaxWidth().clickable { viewModel.setChecklistItem(babyId, item.id, !done) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(done, { viewModel.setChecklistItem(babyId, item.id, it) })
                        Column {
                            Text(item.text)
                            item.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
            Sources(content, guide.sources)
            Disclaimer(content)
        }
    }
}
