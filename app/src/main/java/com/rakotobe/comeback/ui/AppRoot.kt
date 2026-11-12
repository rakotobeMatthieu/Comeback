package com.rakotobe.comeback.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.rakotobe.comeback.CrashLog
import com.rakotobe.comeback.data.AppState
import com.rakotobe.comeback.data.Repository
import com.rakotobe.comeback.reminders.Notifications
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class Tab(val label: String) {
    TODAY("Aujourd'hui"),
    EVOLUTION("Évolution"),
    COMPLETION("Complétion"),
    PROGRESS("Progrès"),
    HABITS("Objectifs"),
}

private fun Tab.icon(): ImageVector = when (this) {
    Tab.TODAY -> AppIcons.Today
    Tab.EVOLUTION -> AppIcons.Evolution
    Tab.COMPLETION -> AppIcons.Completion
    Tab.PROGRESS -> AppIcons.Progress
    Tab.HABITS -> AppIcons.Habits
}

sealed interface Overlay {
    data object Yesterday : Overlay
    data object Settings : Overlay
    data class EditHabit(val habitId: Long?) : Overlay
}

@Composable
fun AppRoot(repo: Repository) {
    val stateOrNull by repo.state.collectAsState(initial = null)
    val state = stateOrNull
    if (state == null) {
        Box(Modifier.fillMaxSize().background(C.Bg))
        return
    }

    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Première ouverture : on demande l'autorisation des notifications (rappels).
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        repo.settings.setNotifAsked()
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !state.settings.notifAsked && !Notifications.canNotify(context)) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun launchSafely(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (t: Throwable) {
                CrashLog.record(context, t)
            }
        }
    }

    val toggle: (Long, LocalDate) -> Unit = { id, day -> launchSafely { repo.toggle(id, day) } }

    BackHandler(enabled = overlay != null) { overlay = null }
    BackHandler(enabled = overlay == null && tab != Tab.TODAY) { tab = Tab.TODAY }

    val current = overlay
    if (current != null) {
        Scaffold(containerColor = C.Bg) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                OverlayContent(current, state, repo, toggle, onClose = { overlay = null }, launchSafely = ::launchSafely)
            }
        }
        return
    }

    Scaffold(
        containerColor = C.Bg,
        bottomBar = {
            NavigationBar(containerColor = C.NavBg) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon(), contentDescription = null) },
                        label = { Text(t.label, fontSize = 11.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = C.Lime,
                            selectedTextColor = C.Lime,
                            indicatorColor = C.LimeDim,
                            unselectedIconColor = C.Muted,
                            unselectedTextColor = C.Muted,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.TODAY -> TodayScreen(
                    state = state,
                    onToggle = toggle,
                    onOpenYesterday = { overlay = Overlay.Yesterday },
                    onOpenSettings = { overlay = Overlay.Settings },
                    onOpenProgress = { tab = Tab.PROGRESS },
                    onAddHabit = { overlay = Overlay.EditHabit(null) },
                )
                Tab.EVOLUTION -> EvolutionScreen(state)
                Tab.COMPLETION -> CompletionScreen(state)
                Tab.PROGRESS -> ProgressScreen(
                    state = state,
                    onSetRefDay = { repo.setPalierRefDay(it) },
                    onSetPaliers = { repo.setPaliers(it) },
                    onSetReward = { n, text -> repo.setReward(n, text) },
                )
                Tab.HABITS -> HabitsScreen(
                    state = state,
                    onEdit = { overlay = Overlay.EditHabit(it) },
                    onNew = { overlay = Overlay.EditHabit(null) },
                )
            }
        }
    }
}

@Composable
private fun OverlayContent(
    overlay: Overlay,
    state: AppState,
    repo: Repository,
    toggle: (Long, LocalDate) -> Unit,
    onClose: () -> Unit,
    launchSafely: (suspend () -> Unit) -> Unit,
) {
    when (overlay) {
        Overlay.Yesterday -> YesterdayScreen(state, toggle, onBack = onClose)
        Overlay.Settings -> SettingsScreen(state, repo, onBack = onClose)
        is Overlay.EditHabit -> {
            val existing = overlay.habitId?.let { state.habit(it) }
            HabitEditorScreen(
                existing = existing,
                onSave = { name, mask, reminder ->
                    launchSafely { repo.saveHabit(overlay.habitId, name, mask, reminder) }
                    onClose()
                },
                onArchive = {
                    val id = overlay.habitId
                    if (id != null) launchSafely { repo.archiveHabit(id) }
                    onClose()
                },
                onBack = onClose,
            )
        }
    }
}
