package io.github.lemonjelly76.babymilestones.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.lemonjelly76.babymilestones.app.data.BabyView
import io.github.lemonjelly76.babymilestones.core.content.ContentBundle
import io.github.lemonjelly76.babymilestones.core.food.AllergenState
import io.github.lemonjelly76.babymilestones.core.guides.Guide
import io.github.lemonjelly76.babymilestones.core.guides.GuideFeature
import io.github.lemonjelly76.babymilestones.core.guides.GuideLink
import io.github.lemonjelly76.babymilestones.core.guides.GuideTopic
import io.github.lemonjelly76.babymilestones.core.guides.Guides
import io.github.lemonjelly76.babymilestones.core.guides.LinkKind

/** Practical guides for the child's age, with the foods tracker on top while they're weaning. */
@Composable
fun GuidesScreen(view: BabyView, content: ContentBundle, onOpenGuide: (String) -> Unit, onOpenFoods: () -> Unit) {
    val forAge = view.guides
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).then(ScreenPadding).padding(top = 8.dp)) {
        OutlinedTextField(query, { query = it }, placeholder = { Text("Search guides") }, singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, null) }, modifier = Modifier.fillMaxWidth())
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            val matches = content.guides.filter { g ->
                (listOf(g.title, g.summary) + g.keyPoints + g.sections.flatMap { it.paragraphs + it.bullets + listOfNotNull(it.heading) })
                    .any { q in it.lowercase() }
            }
            SectionTitle("${matches.size} ${if (matches.size == 1) "guide" else "guides"}")
            matches.forEach { GuideRow(it, view, onClick = { onOpenGuide(it.id) }) }
            Disclaimer(content)
            return@Column
        }
        if (view.showFoods) FoodsSummaryCard(view, onOpenFoods)

        SectionTitle("For ${view.baby.name} now")
        if (forAge.now.isEmpty()) Text("Nothing for this age yet.", style = MaterialTheme.typography.bodyMedium)
        GuideTopic.entries.forEach { topic ->
            val inTopic = forAge.now.filter { it.topic == topic }
            if (inTopic.isNotEmpty()) {
                Text(topic.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp))
                inTopic.forEach { GuideRow(it, view, onClick = { onOpenGuide(it.id) }) }
            }
        }

        if (forAge.comingUp.isNotEmpty()) {
            SectionTitle("Coming up")
            forAge.comingUp.forEach { GuideRow(it, view, onClick = { onOpenGuide(it.id) }, from = true) }
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

/** A guide in a list: title, one line of summary, and checklist progress. */
@Composable
private fun GuideRow(guide: Guide, view: BabyView, onClick: () -> Unit, from: Boolean = false) {
    val progress = Guides.progress(guide, view.data.checklist)
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(guide.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(guide.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(
                if (from) "from ${monthsLabel(guide.fromMonths)}" else null,
                if (progress.total > 0) "${progress.done} of ${progress.total} ticked" else null,
            )
            if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(
    state: AppState, babyId: String, guideId: String, viewModel: AppViewModel, onBack: () -> Unit, onOpenFoods: () -> Unit,
    onOpenGuide: (String) -> Unit,
) {
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

            if (guide.keyPoints.isNotEmpty()) InfoCard(color = MaterialTheme.colorScheme.primaryContainer) {
                Text("In short", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                guide.keyPoints.forEach { point ->
                    Row(Modifier.padding(top = 6.dp)) {
                        Icon(Icons.Outlined.CheckCircle, null, Modifier.padding(end = 8.dp).size(18.dp))
                        Text(point)
                    }
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
            if (guide.links.isNotEmpty()) {
                SectionTitle("Watch and read")
                guide.links.forEach { GuideLinkRow(it) }
            }

            val related = guide.related.mapNotNull(content::guide)
            if (related.isNotEmpty()) {
                SectionTitle("Related guides")
                related.forEach { g ->
                    Text("${g.title} ›", color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().clickable { onOpenGuide(g.id) }.padding(vertical = 8.dp))
                }
            }

            Expandable("Sources (${guide.sources.size})") { Sources(content, guide.sources) }
            guide.checkedOn?.let {
                Text("Checked against these sources on ${it.pretty()}.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Disclaimer(content)
        }
    }
}

/** A trusted page, video, tool or service, opened in the browser. */
@Composable
private fun GuideLinkRow(link: GuideLink) {
    val uri = LocalUriHandler.current
    Row(
        Modifier.fillMaxWidth().clickable { uri.openUri(link.url) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            when (link.kind) {
                LinkKind.VIDEO -> Icons.Outlined.PlayCircle
                LinkKind.ARTICLE -> Icons.AutoMirrored.Outlined.Article
                LinkKind.TOOL -> Icons.Outlined.Build
                LinkKind.SERVICE -> Icons.Outlined.SupportAgent
            },
            link.kind.label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 12.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(link.title, style = MaterialTheme.typography.bodyLarge)
            Text(listOfNotNull(link.kind.label, link.publisher, link.note).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
