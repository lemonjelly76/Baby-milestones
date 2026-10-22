package io.github.lemonjelly76.babymilestones.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.lemonjelly76.babymilestones.app.data.BabyView
import io.github.lemonjelly76.babymilestones.core.api.ChatMessage
import io.github.lemonjelly76.babymilestones.core.api.Proposal

@Composable
fun AskScreen(state: AppState, view: BabyView, viewModel: AppViewModel) {
    val babyId = view.baby.id
    val chat = state.chat(babyId)
    var input by rememberSaveable(babyId) { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(chat.entries.size) { if (chat.entries.isNotEmpty()) list.animateScrollToItem(chat.entries.size - 1) }

    Column(Modifier.fillMaxSize().imePadding()) {
        Text(
            "Answers come from the app's milestone guidance and ${view.baby.name}'s log. Only what's needed for your question is sent " +
                "to the AI service, never photos, and it isn't used for training. Not medical advice.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.weight(1f), state = list, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (chat.entries.isEmpty()) item {
                Column(verticalArrangement = Gap) {
                    Text("Try asking", style = MaterialTheme.typography.titleSmall)
                    listOf(
                        "Let's do this week's check-in",
                        "${view.baby.name} said dada yesterday and can sit without help now",
                        "Is it normal ${view.baby.name} isn't crawling yet?",
                        "Help me get ready for the health visitor review",
                    ).forEach { prompt ->
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant,
                            onClick = { viewModel.ask(babyId, prompt) }) {
                            Text(prompt, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            }
            itemsIndexed(chat.entries) { index, entry ->
                when (entry) {
                    is ChatEntry.Text -> Bubble(entry.message)
                    is ChatEntry.Card -> InfoCard(color = urgencyColor(entry.card.card.urgency)) {
                        Text(entry.card.card.title, fontWeight = FontWeight.SemiBold)
                        Text(entry.card.card.body)
                        Text(entry.card.card.signpost, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                    }
                    is ChatEntry.Proposals -> ProposalSheet(entry,
                        onConfirm = { chosen -> viewModel.confirm(babyId, index, chosen) },
                        onDismiss = { viewModel.dismissProposals(babyId, index) })
                }
            }
            if (chat.sending) item { CircularProgressIndicator(Modifier.padding(8.dp)) }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it }, placeholder = { Text("Ask about ${view.baby.name}…") }, modifier = Modifier.weight(1f), maxLines = 4)
            IconButton(enabled = input.isNotBlank() && !chat.sending, onClick = { viewModel.ask(babyId, input); input = "" }) {
                Icon(Icons.AutoMirrored.Outlined.Send, "Send")
            }
        }
        if (chat.entries.isNotEmpty()) TextButton(onClick = { viewModel.clearChat(babyId) }, modifier = Modifier.padding(start = 8.dp)) {
            Text("Start a new conversation")
        }
    }
}

@Composable
private fun Bubble(message: ChatMessage) {
    val mine = message.role == ChatMessage.Role.USER
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 320.dp),
        ) { Text(message.text, modifier = Modifier.padding(12.dp)) }
    }
}

/** Every suggested write, batched into one confirmation sheet. Nothing is saved until the parent taps Confirm. */
@Composable
private fun ProposalSheet(entry: ChatEntry.Proposals, onConfirm: (List<Proposal>) -> Unit, onDismiss: () -> Unit) {
    val chosen = remember(entry.items) { mutableStateMapOf<String, Boolean>().apply { entry.items.forEach { put(it.id, true) } } }
    val pending = entry.items.filter { it.id !in entry.confirmed }
    InfoCard(color = MaterialTheme.colorScheme.tertiaryContainer) {
        Text(if (pending.isEmpty()) "Saved" else "Confirm these updates", fontWeight = FontWeight.SemiBold)
        entry.items.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = p.id in entry.confirmed || chosen[p.id] == true,
                    onCheckedChange = { chosen[p.id] = it },
                    enabled = p.id !in entry.confirmed && !entry.dismissed,
                )
                Text(p.summary)
            }
        }
        if (pending.isNotEmpty() && !entry.dismissed) {
            Row(horizontalArrangement = Gap) {
                Button(onClick = { onConfirm(pending.filter { chosen[it.id] == true }) }, enabled = pending.any { chosen[it.id] == true }) {
                    Text("Confirm")
                }
                TextButton(onClick = onDismiss) { Text("Not now") }
            }
        }
    }
}
