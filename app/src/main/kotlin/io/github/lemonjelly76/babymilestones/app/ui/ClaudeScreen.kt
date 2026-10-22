package io.github.lemonjelly76.babymilestones.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.lemonjelly76.babymilestones.app.data.BabyView
import io.github.lemonjelly76.babymilestones.core.notes.AssistantNote
import java.time.ZoneId

private const val CLAUDE_PACKAGE = "com.anthropic.claude"

/**
 * What Claude saved for the family over MCP, and how to ask it. The in-app chat, which calls the Claude API and costs
 * money per message, only appears when it's switched on in Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClaudeScreen(state: AppState, babyId: String, viewModel: AppViewModel, onBack: () -> Unit, startInChat: Boolean = false) {
    val view = state.view(babyId) ?: return
    var chat by rememberSaveable { mutableStateOf(startInChat) }
    var reading by remember { mutableStateOf<String?>(null) }
    val note = view.data.assistantNotes.firstOrNull { it.id == reading }

    BackHandler(enabled = note != null) { reading = null }
    if (note != null) {
        NoteScreen(note, onBack = { reading = null }, onDelete = { viewModel.deleteNote(babyId, note.id); reading = null })
        return
    }
    Scaffold(topBar = {
        Column {
            TopAppBar(
                title = { Text("Claude") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
            if (state.assistantEnabled) PrimaryTabRow(selectedTabIndex = if (chat) 1 else 0) {
                Tab(!chat, { chat = false }, text = { Text("Saved notes") })
                Tab(chat, { chat = true }, text = { Text("Ask here") })
            }
        }
    }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (chat && state.assistantEnabled) AskScreen(state, view, viewModel)
            else Notes(view, onOpen = { reading = it })
        }
    }
}

@Composable
private fun Notes(view: BabyView, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).then(ScreenPadding).padding(top = 8.dp)) {
        InfoCard(color = MaterialTheme.colorScheme.primaryContainer) {
            Text("Ask Claude on your phone", fontWeight = FontWeight.SemiBold)
            Text("In the Claude app, the Baby Milestones connector lets Claude read and update ${view.baby.name}'s records. " +
                "Ask for a weekly check-in, meal ideas or help preparing for a review, and ask it to save the result: it appears here.")
            Button(onClick = { openClaude(context, "Use Baby Milestones to do this week's check-in for ${view.baby.name}.") },
                modifier = Modifier.padding(top = 8.dp)) { Text("Copy a check-in prompt and open Claude") }
        }
        SectionTitle("Saved notes")
        if (view.data.assistantNotes.isEmpty()) EmptyState("Nothing saved yet.")
        view.data.assistantNotes.forEach { n ->
            InfoCard(onClick = { onOpen(n.id) }) {
                Text("${n.kind.label} · ${n.createdAt.atZone(ZoneId.systemDefault()).toLocalDate().pretty()}",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(n.title, fontWeight = FontWeight.SemiBold)
                Text(n.body.lines().filter { it.isNotBlank() }.take(2).joinToString(" ") { it.trimStart('#', '-', '*', ' ').replace("**", "") },
                    style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
        }
        Disclaimer(view.content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteScreen(note: AssistantNote, onBack: () -> Unit, onDelete: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(note.title, maxLines = 1) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            actions = { IconButton(onClick = { confirm = true }) { Icon(Icons.Outlined.Delete, "Delete") } },
        )
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).then(ScreenPadding)) {
            Text("${note.kind.label} · saved by Claude on ${note.createdAt.atZone(ZoneId.systemDefault()).toLocalDate().pretty()}",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp))
            MarkdownText(note.body)
            Text("Written by Claude from the app's data. General information, not medical advice.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp))
        }
    }
    if (confirm) ConfirmDialog("Delete this note?", "It's removed for everyone in the family.", "Delete", onDelete, { confirm = false })
}

/** The small part of Markdown Claude's notes use: headings, bullets and bold. */
@Composable
private fun MarkdownText(text: String) {
    text.lines().forEach { raw ->
        val line = raw.trimEnd()
        when {
            line.isBlank() -> Box(Modifier.padding(vertical = 4.dp))
            line.startsWith("#") -> Text(inline(line.trimStart('#').trim()), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            line.trimStart().let { it.startsWith("- ") || it.startsWith("* ") } -> Row(Modifier.padding(vertical = 2.dp)) {
                val indent = (line.length - line.trimStart().length) * 4
                Text("•", modifier = Modifier.padding(start = indent.dp, end = 8.dp))
                Text(inline(line.trimStart().drop(2)))
            }
            else -> Text(inline(line), modifier = Modifier.padding(vertical = 2.dp))
        }
    }
}

private fun inline(text: String): AnnotatedString = buildAnnotatedString {
    text.split("**").forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(part) } else append(part)
    }
}

/** Copies a prompt and opens the Claude app (or claude.ai when it isn't installed). */
fun openClaude(context: Context, prompt: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Prompt", prompt))
    val intent = context.packageManager.getLaunchIntentForPackage(CLAUDE_PACKAGE)
        ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://claude.ai/new"))
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
fun AskClaudeCard(view: BabyView, assistantEnabled: Boolean, onOpenClaude: () -> Unit, onAskHere: () -> Unit) {
    val context = LocalContext.current
    InfoCard {
        Text("Weekly check-in with Claude", fontWeight = FontWeight.SemiBold)
        Text("Claude asks what's new, logs what you confirm, and saves a summary here.")
        Row(horizontalArrangement = Gap, modifier = Modifier.padding(top = 8.dp)) {
            Button(onClick = { openClaude(context, "Use Baby Milestones to do this week's check-in for ${view.baby.name}.") }) {
                Text("Open Claude")
            }
            if (assistantEnabled) OutlinedButton(onClick = onAskHere) { Text("Ask here") }
            else OutlinedButton(onClick = onOpenClaude) { Text("Saved notes") }
        }
    }
}
