package com.rakotobe.comeback.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rakotobe.comeback.data.AppState
import com.rakotobe.comeback.data.Habit
import com.rakotobe.comeback.domain.Paliers
import com.rakotobe.comeback.domain.ScoredDay
import com.rakotobe.comeback.domain.Seasons
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.min

private fun habitMeta(h: Habit): String {
    val days = daysLabel(h.daysMask)
    val r = h.reminderMinutes
    return if (r == null) days else "$days · ${timeLabel(r)}"
}

@Composable
fun TodayScreen(
    state: AppState,
    onToggle: (Long, LocalDate) -> Unit,
    onOpenYesterday: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProgress: () -> Unit,
    onAddHabit: () -> Unit,
) {
    val today = state.today
    val todayDay = state.todayDay
    val result = todayDay?.result
    val habits = if (result == null) emptyList() else state.habitsOf(result)
    val season = remember(state) { Seasons.compute(state.days, LocalDate.ofEpochDay(state.settings.seasonAnchor), today) }
    val paliers = remember(state) {
        Paliers.compute(state.days, LocalDate.ofEpochDay(state.settings.palierRefDay), state.settings.paliers, today)
    }
    val haptic = LocalHapticFeedback.current
    val yesterday = state.yesterdayDay

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ScreenTitle(
                overline = "${dayLong(today)} · Saison ${season.number}, jour ${season.dayOfSeason}/30",
                title = "Aujourd'hui",
                trailing = {
                    HeaderIconButton(onClick = onOpenSettings, description = "Réglages") {
                        Icon(AppIcons.Settings, contentDescription = "Réglages", tint = C.Text)
                    }
                },
            )
        }

        if (state.yesterdayEditable && yesterday != null && yesterday.result.plannedCount > 0 && !yesterday.result.isPerfect) {
            item { YesterdayBanner(yesterday, onOpenYesterday) }
        }

        item {
            Panel(padding = 20.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val planned = result?.plannedCount ?: 0
                    val done = result?.doneCount ?: 0
                    Ring(
                        fraction = if (planned == 0) 0f else done.toFloat() / planned,
                        size = 112.dp,
                        stroke = 12.dp,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$done/$planned", color = C.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Text("objectifs", color = C.Muted, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.width(20.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Indice ComeBack", color = C.Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(fmt(todayDay?.index ?: 100.0), color = C.Text, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                        val delta = todayDay?.delta ?: 0.0
                        if (delta >= 0) Tag("${signedPct(delta, 2)} aujourd'hui", C.Lime, C.LimeDim)
                        else Tag("${signedPct(delta, 2)} aujourd'hui", C.Orange, C.OrangeDim)
                        val next = paliers.next
                        Text(
                            text = if (next == null) "Tous les paliers atteints ›"
                            else "Palier ${next.palier.days} · ${fmt(min(paliers.equivalentDays, next.palier.days.toDouble()))}/${next.palier.days} ›",
                            color = C.Muted,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable(onClick = onOpenProgress),
                        )
                    }
                }
            }
        }

        item { SectionTitle("Objectifs du jour", if (habits.isEmpty()) null else "Touchez pour valider") }

        if (habits.isEmpty()) {
            item {
                Panel {
                    Text(
                        if (state.activeHabits.isEmpty()) "Commence par définir tes objectifs." else "Aucun objectif prévu aujourd'hui. Profite !",
                        color = C.Muted,
                        fontSize = 14.sp,
                    )
                    if (state.activeHabits.isEmpty()) {
                        Button(
                            onClick = onAddHabit,
                            colors = ButtonDefaults.buttonColors(containerColor = C.Lime, contentColor = C.Bg),
                        ) { Text("Ajouter un objectif", fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }

        items(habits, key = { it.id }) { h ->
            val done = result?.done?.contains(h.id) == true
            HabitCheckRow(name = h.name, meta = habitMeta(h), done = done) {
                if (!done && result != null && result.doneCount + 1 == result.plannedCount) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                } else {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                onToggle(h.id, today)
            }
        }
    }
}

@Composable
private fun YesterdayBanner(yesterday: ScoredDay, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(BorderStroke(1.dp, C.Border), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Clock, contentDescription = null, tint = C.Orange, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Hier : ${yesterday.result.doneCount}/${yesterday.result.plannedCount} validés",
                color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            )
            Text("Un oubli ? Modifiable jusqu'à 12:00", color = C.Muted, fontSize = 12.sp)
        }
        Text("Compléter", color = C.Lime, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Icon(AppIcons.Chevron, contentDescription = null, tint = C.Lime, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun YesterdayScreen(state: AppState, onToggle: (Long, LocalDate) -> Unit, onBack: () -> Unit) {
    val yDate = state.today.minusDays(1)
    val y = state.yesterdayDay
    val editable = state.yesterdayEditable
    val habits = if (y == null) emptyList() else state.habitsOf(y.result)
    val remaining = Duration.between(state.now, LocalTime.NOON)
    val minutesLeft = remaining.toMinutes().coerceAtLeast(0)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { BackRow("Aujourd'hui", onBack) }
        item { ScreenTitle(overline = dayLong(yDate), title = "Hier") }
        item {
            Panel(color = if (editable) C.OrangeDim else C.Surface) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (editable) AppIcons.Clock else AppIcons.Lock,
                        contentDescription = null,
                        tint = C.Orange,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            if (editable) "Encore ${minutesLeft / 60} h ${"%02d".format(minutesLeft % 60)} pour modifier" else "Journée verrouillée",
                            color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (editable) "Verrouillé aujourd'hui à 12:00, pour garder des données fiables."
                            else "La veille n'est modifiable que jusqu'à midi.",
                            color = C.Muted, fontSize = 12.sp,
                        )
                    }
                }
                if (editable) ProgressLine((minutesLeft / 720f), color = C.Orange, height = 6.dp)
            }
        }
        if (y != null) {
            item {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Indice d'hier", color = C.Muted, fontSize = 13.sp)
                            Text(fmt(y.index), color = C.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${y.result.doneCount}/${y.result.plannedCount}", color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "${signedPct(y.delta, 2)} ce jour-là",
                                color = if (y.delta >= 0) C.Lime else C.Orange,
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
        item { SectionTitle("Objectifs d'hier", if (editable) "Touchez pour corriger" else null) }
        if (habits.isEmpty()) {
            item { Text("Aucun objectif n'était prévu hier.", color = C.Muted, fontSize = 14.sp) }
        }
        items(habits, key = { it.id }) { h ->
            val done = y?.result?.done?.contains(h.id) == true
            HabitCheckRow(name = h.name, meta = daysLabel(h.daysMask), done = done, enabled = editable) {
                onToggle(h.id, yDate)
            }
        }
        item {
            Text(
                "Seule la veille est modifiable. Indice, paliers, saison et records sont recalculés dès que tu corriges.",
                color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
fun EmptyHint(text: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Surface).padding(16.dp),
    ) { Text(text, color = C.Muted, fontSize = 14.sp) }
}
