package io.github.lemonjelly76.babymilestones.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Outlined.WbSunny),
    MILESTONES("Milestones", Icons.Outlined.Checklist),
    TIMELINE("Timeline", Icons.Outlined.Timeline),
    HEALTH("Health", Icons.Outlined.HealthAndSafety),
    ASK("Ask", Icons.AutoMirrored.Outlined.Chat),
}

/** Screens opened on top of the tabs. */
sealed interface Overlay {
    data class PeriodGuide(val periodId: String) : Overlay
    data object AddBaby : Overlay
    data class EditBaby(val babyId: String) : Overlay
    data class Backfill(val babyId: String) : Overlay
    data class Summary(val babyId: String) : Overlay
    data object Settings : Overlay
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BabyMilestonesApp(viewModel: AppViewModel, invite: Invite?, onInviteUsed: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    val content = state.content
    when {
        content == null || (state.loading && state.me == null && state.signedIn) -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        !state.signedIn -> Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            SetupScreen(state, viewModel, invite, onInviteUsed, Modifier.padding(padding))
        }
        state.babies.isEmpty() -> Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            OnboardingFlow(state, viewModel, Modifier.padding(padding), onDone = {})
        }
        else -> MainScreens(state, viewModel, snackbar)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreens(state: AppState, viewModel: AppViewModel, snackbar: SnackbarHostState) {
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var overlays by remember { mutableStateOf(listOf<Overlay>()) }
    fun open(o: Overlay) { overlays = overlays + o }
    fun close() { overlays = overlays.dropLast(1) }
    BackHandler(enabled = overlays.isNotEmpty()) { close() }

    val baby = state.selectedBaby ?: return
    val view = state.view(baby.id)
    val content = state.content!!

    when (val overlay = overlays.lastOrNull()) {
        is Overlay.PeriodGuide -> PeriodGuideScreen(state, baby.id, overlay.periodId, viewModel, onBack = ::close)
        Overlay.AddBaby -> Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            OnboardingFlow(state, viewModel, Modifier.padding(padding), onDone = ::close, onCancel = ::close)
        }
        is Overlay.EditBaby -> EditBabyScreen(state, overlay.babyId, viewModel, onBack = ::close)
        is Overlay.Backfill -> BackfillScreen(state, overlay.babyId, viewModel, onDone = ::close)
        is Overlay.Summary -> SummaryScreen(state, overlay.babyId, viewModel, onBack = ::close)
        Overlay.Settings -> SettingsScreen(state, viewModel, onBack = ::close, onAddBaby = { open(Overlay.AddBaby) },
            onEditBaby = { open(Overlay.EditBaby(it)) })
        null -> Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = { BabySwitcher(state, viewModel, onAdd = { open(Overlay.AddBaby) }) },
                        actions = {
                            IconButton(onClick = { open(Overlay.Settings) }) { Icon(Icons.Outlined.Settings, "Settings") }
                        },
                    )
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (state.offline) OfflineBanner(onRetry = { viewModel.refresh() })
                }
            },
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach {
                        NavigationBarItem(selected = tab == it, onClick = { tab = it }, icon = { Icon(it.icon, null) }, label = { Text(it.label) })
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                if (view == null) {
                    EmptyState("Loading ${baby.name}'s milestones…")
                } else when (tab) {
                    Tab.TODAY -> TodayScreen(view, content, viewModel,
                        onOpenGuide = { open(Overlay.PeriodGuide(it)) },
                        onBackfill = { open(Overlay.Backfill(baby.id)) },
                        onAsk = { tab = Tab.ASK },
                        onHealth = { tab = Tab.HEALTH })
                    Tab.MILESTONES -> MilestonesScreen(view, content, viewModel)
                    Tab.TIMELINE -> TimelineScreen(view, content, viewModel, onSummary = { open(Overlay.Summary(baby.id)) })
                    Tab.HEALTH -> HealthScreen(view, content, viewModel, onSummary = { open(Overlay.Summary(baby.id)) })
                    Tab.ASK -> AskScreen(state, view, viewModel)
                }
            }
        }
    }
}

@Composable
private fun BabySwitcher(state: AppState, viewModel: AppViewModel, onAdd: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val baby = state.selectedBaby ?: return
    val view = state.view(baby.id)
    Box {
        TextButton(onClick = { open = true }) {
            Column {
                Text(baby.name, style = MaterialTheme.typography.titleLarge)
                view?.let { Text(it.snapshot.ages.describe(), style = MaterialTheme.typography.labelMedium) }
            }
            Icon(Icons.Outlined.ArrowDropDown, "Switch baby")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            state.babies.forEach { b ->
                DropdownMenuItem(text = { Text(b.name) }, onClick = { viewModel.selectBaby(b.id); open = false })
            }
            DropdownMenuItem(text = { Text("Add a baby") }, onClick = { open = false; onAdd() })
        }
    }
}

@Composable
private fun OfflineBanner(onRetry: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text("Offline: showing what this phone saved last.", style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.CenterStart))
            TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.CenterEnd)) { Text("Retry") }
        }
    }
}
