package com.rakotobe.comeback.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rakotobe.comeback.data.ALL_DAYS
import com.rakotobe.comeback.data.AppState
import com.rakotobe.comeback.data.Habit
import com.rakotobe.comeback.data.WEEKEND_DAYS
import com.rakotobe.comeback.data.WEEK_DAYS

@Composable
fun HabitsScreen(state: AppState, onEdit: (Long) -> Unit, onNew: () -> Unit) {
    val habits = state.activeHabits
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                ScreenTitle(
                    overline = if (habits.size <= 1) "${habits.size} objectif actif" else "${habits.size} objectifs actifs",
                    title = "Objectifs",
                )
            }
            item {
                Text(
                    "Chaque objectif a ses propres jours : tous les jours, en semaine, ou à la carte.",
                    color = C.Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            if (habits.isEmpty()) {
                item { EmptyHint("Aucun objectif pour l'instant. Ajoute ton premier avec le bouton en bas.") }
            }
            items(habits, key = { it.id }) { h -> HabitListRow(h) { onEdit(h.id) } }
        }
        ExtendedFloatingActionButton(
            onClick = onNew,
            containerColor = C.Lime,
            contentColor = C.Bg,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            icon = { Icon(AppIcons.Plus, contentDescription = null) },
            text = { Text("Nouvel objectif", fontWeight = FontWeight.SemiBold) },
        )
    }
}

@Composable
private fun HabitListRow(h: Habit, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(C.Surface)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    h.name, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                val r = h.reminderMinutes
                Text(if (r == null) "Sans rappel" else "Rappel ${timeLabel(r)}", color = C.Muted, fontSize = 12.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until 7) {
                    val on = ((h.daysMask shr i) and 1) == 1
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(if (on) C.Lime else C.Surface2),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            DAY_LETTERS[i],
                            color = if (on) C.Bg else C.Faint,
                            fontSize = 12.sp,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
        }
        Icon(AppIcons.Chevron, contentDescription = "Modifier", tint = C.Muted, modifier = Modifier.padding(start = 8.dp).size(20.dp))
    }
}

@Composable
fun HabitEditorScreen(
    existing: Habit?,
    onSave: (name: String, daysMask: Int, reminderMinutes: Int?) -> Unit,
    onArchive: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var name by remember(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var mask by remember(existing?.id) { mutableIntStateOf(existing?.daysMask ?: ALL_DAYS) }
    var reminderOn by remember(existing?.id) { mutableStateOf(existing?.reminderMinutes != null) }
    var reminder by remember(existing?.id) { mutableIntStateOf(existing?.reminderMinutes ?: (20 * 60)) }
    var confirmArchive by remember { mutableStateOf(false) }
    val canSave = name.isNotBlank() && mask != 0

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BackRow("Objectifs", onBack)
        ScreenTitle(
            overline = if (existing == null) "Nouvel objectif" else "Modifier l'objectif",
            title = if (existing == null) "Créer" else existing.name,
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(60) },
            label = { Text("Nom de l'objectif") },
            placeholder = { Text("Ex. : Sport 45 min, Lecture, 2 L d'eau…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        SectionTitle("Jours prévus", daysLabel(mask))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (i in 0 until 7) {
                val on = ((mask shr i) and 1) == 1
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (on) C.Lime else C.Surface2)
                        .clickable { mask = mask xor (1 shl i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(DAY_LETTERS[i], color = if (on) C.Bg else C.Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Tous les jours", mask == ALL_DAYS) { mask = ALL_DAYS }
            Pill("En semaine", mask == WEEK_DAYS) { mask = WEEK_DAYS }
            Pill("Week-end", mask == WEEKEND_DAYS) { mask = WEEKEND_DAYS }
        }

        SectionTitle("Rappel")
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Me le rappeler si ce n'est pas fait", color = C.Text, fontSize = 15.sp)
                    Text("Une notification avec un bouton « Fait »", color = C.Muted, fontSize = 12.sp)
                }
                Switch(
                    checked = reminderOn,
                    onCheckedChange = { reminderOn = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = C.Lime, checkedThumbColor = C.Bg),
                )
            }
            if (reminderOn) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(C.Surface2)
                        .clickable {
                            TimePickerDialog(context, { _, h, m -> reminder = h * 60 + m }, reminder / 60, reminder % 60, true).show()
                        }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons.Clock, contentDescription = null, tint = C.Lime, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Heure du rappel", color = C.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text(timeLabel(reminder), color = C.Lime, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Button(
            onClick = { onSave(name, mask, if (reminderOn) reminder else null) },
            enabled = canSave,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = C.Lime, contentColor = C.Bg,
                disabledContainerColor = C.Surface2, disabledContentColor = C.Faint,
            ),
        ) { Text("Enregistrer", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }

        if (existing != null) {
            OutlinedButton(
                onClick = { confirmArchive = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = C.Orange),
            ) { Text("Supprimer cet objectif") }
            Text(
                "L'historique est conservé : tes anciens scores ne changent pas.",
                color = C.Muted, fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmArchive) {
        ConfirmDialog(
            title = "Supprimer « ${existing?.name ?: ""} » ?",
            text = "Il ne sera plus proposé à partir d'aujourd'hui. Ton historique et ton indice restent inchangés.",
            confirmLabel = "Supprimer",
            onDismiss = { confirmArchive = false },
            onConfirm = {
                confirmArchive = false
                onArchive()
            },
        )
    }
}
