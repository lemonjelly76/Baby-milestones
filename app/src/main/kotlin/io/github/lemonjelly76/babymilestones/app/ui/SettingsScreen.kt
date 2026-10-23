package io.github.lemonjelly76.babymilestones.app.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.lemonjelly76.babymilestones.app.data.NotificationSettings
import io.github.lemonjelly76.babymilestones.core.api.Family
import java.time.ZoneId
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: AppState, viewModel: AppViewModel, onBack: () -> Unit, onAddBaby: () -> Unit, onEditBaby: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var inviteText by remember { mutableStateOf<String?>(null) }
    var connector by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    Scaffold(topBar = {
        TopAppBar(title = { Text("Settings") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
        })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).then(ScreenPadding)) {
            SectionTitle("Babies")
            state.babies.forEach { b ->
                InfoCard(onClick = { onEditBaby(b.id) }) {
                    Text(b.name, fontWeight = FontWeight.SemiBold)
                    Text("Born ${b.dateOfBirth.pretty()}" + (b.dueDate?.let { " · due ${it.pretty()}" } ?: "") +
                        if (b.dobEstimated) " · date of birth estimated" else "", style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedButton(onClick = onAddBaby) { Text("Add a baby") }

            SectionTitle("Family")
            state.me?.let { me ->
                Text("${me.family.name} · signed in as ${me.carer.name}")
                Text("Carers: " + me.carers.joinToString { "${it.name} (${it.role})" }, style = MaterialTheme.typography.bodySmall)
                Text("Schedule region", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Gap) {
                    listOf("england" to "England", "scotland" to "Scotland", "wales" to "Wales", "northern_ireland" to "NI").forEach { (id, label) ->
                        FilterChip(me.family.region == id, { viewModel.updateFamily(null, id) }, label = { Text(label) })
                    }
                }
            }
            fun invite(access: String) = scope.launch {
                viewModel.invite(access)?.let {
                    val link = "babymilestones://join?code=${it.code}&server=${state.serverUrl}"
                    val expires = it.expiresAt.atZone(ZoneId.systemDefault()).toLocalDate().pretty()
                    val kind = if (access == "view") "view-only " else ""
                    inviteText = "Invite code ${it.code} (${kind}works once, until $expires).\n\nLink: $link"
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, "Join our family in Baby Milestones: $link\n\nOr enter code ${it.code} with server ${state.serverUrl}")
                    context.startActivity(Intent.createChooser(send, "Invite a carer"))
                }
            }
            if (state.canEdit) Row(horizontalArrangement = Gap, modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = { invite("edit") }) { Text("Invite a carer") }
                OutlinedButton(onClick = { invite("view") }) { Text("Invite to view only") }
            }
            Text("View-only carers, such as grandparents, see everything but can't change it.", style = MaterialTheme.typography.bodySmall)
            inviteText?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

            SectionTitle("Notifications")
            val n = state.notifications
            fun set(change: NotificationSettings) {
                if (Build.VERSION.SDK_INT >= 33) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                viewModel.setNotifications(change)
            }
            Toggle("Weekly 'this week' digest (Sundays)", n.weeklyDigest) { set(n.copy(weeklyDigest = it)) }
            Toggle("When a milestone is around now", n.dueNow) { set(n.copy(dueNow = it)) }
            Toggle("When an appointment window opens", n.appointmentWindows) { set(n.copy(appointmentWindows = it)) }
            Toggle("A nudge if nothing's been logged for 3 weeks", n.quietNudge) { set(n.copy(quietNudge = it)) }

            SectionTitle("Claude")
            Text("Use Claude on your phone or computer with the Baby Milestones connector: add this server's MCP address as a " +
                "custom connector in claude.ai (Settings, Connectors) with this token as the bearer token. It then works in the " +
                "Claude app too, on your Claude plan.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = {
                scope.launch { viewModel.connectorToken()?.let { connector = it.token; copy(context, it.token) } }
            }, modifier = Modifier.padding(top = 4.dp)) { Text("Create connector token") }
            val endpoint = state.me?.mcpUrl ?: "${state.serverUrl}/mcp"
            connector?.let {
                Text("Copied to the clipboard. Making a new token replaces this one.", style = MaterialTheme.typography.bodySmall)
            }
            Text("Connector address: $endpoint", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            if (state.me?.mcpUrl == null && endpoint.startsWith("http://")) Text(
                "Claude reaches connectors from the internet, so it can't use a home WiFi address. To use Claude, publish the " +
                    "server's /mcp on a public https address (for example with Tailscale Funnel) and set MCP_PUBLIC_URL.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp),
            )
            Toggle("In-app assistant (Ask here)", state.assistantEnabled) { viewModel.setAssistantEnabled(it) }
            Text("Off by default. When on, each message is answered by the Claude API on your server, which is charged per use. " +
                "Only the text of your questions is sent, never photos.", style = MaterialTheme.typography.bodySmall)

            SectionTitle("Your data")
            Text("Everything is stored on your family's server and this phone. Photos are never sent to Claude.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { scope.launch { viewModel.exportJson()?.let { Export.shareJson(context, it) } } },
                modifier = Modifier.padding(top = 4.dp)) { Text("Export everything (JSON)") }
            TextButton(onClick = { confirmSignOut = true }) { Text("Sign out of this phone") }
            TextButton(onClick = { confirmDelete = true }) { Text("Delete the family and all data", color = MaterialTheme.colorScheme.error) }

            SectionTitle("About the guidance")
            Text("Milestones come from the CDC and AAP 2022 checklists, which list what most children (75% or more) do by each age. " +
                "Movement windows come from the WHO Motor Development Study; sleep, activity and screen advice from WHO 2019 guidelines; " +
                "UK appointments and vaccinations from the NHS and UKHSA; weaning, allergen and food safety advice from the NHS, BSACI " +
                "and the BLISS trial. Content version ${state.content?.version}.",
                style = MaterialTheme.typography.bodySmall)
            state.content?.let { Sources(it, it.references.map { r -> r.id }) }
            state.content?.let { content ->
                Expandable("Food photo credits (${content.foods.count { it.image != null }})") {
                    val uri = LocalUriHandler.current
                    content.foods.forEach { f ->
                        f.image?.let { img ->
                            Text("${f.name}: ${img.author}, ${img.licence}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 2.dp).clickable { uri.openUri(img.source) })
                        }
                    }
                    Text("From Wikimedia Commons under Creative Commons or public domain licences. Tap a credit for its source " +
                        "and licence. Photos are cropped.", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("Server: ${state.serverUrl}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 16.dp))
        }
    }
    if (confirmDelete) ConfirmDialog(
        "Delete everything?", "This deletes your family, every baby, log entry, note and photo from the server, for every carer. It can't be undone.",
        "Delete", { viewModel.deleteFamily() }, { confirmDelete = false },
    )
    if (confirmSignOut) ConfirmDialog(
        "Sign out?", "This phone forgets the family and its saved data. You'll need a new invite code to sign back in.",
        "Sign out", { viewModel.signOut() }, { confirmSignOut = false },
    )
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(value, onChange)
    }
}

private fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Connector token", text))
}
