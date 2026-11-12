package com.rakotobe.comeback.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rakotobe.comeback.data.AppState
import com.rakotobe.comeback.domain.Palier
import com.rakotobe.comeback.domain.PalierStatus
import com.rakotobe.comeback.domain.Paliers
import com.rakotobe.comeback.domain.Records
import com.rakotobe.comeback.domain.Seasons
import java.time.LocalDate
import kotlin.math.min

@Composable
fun ProgressScreen(
    state: AppState,
    onSetRefDay: (LocalDate) -> Unit,
    onSetPaliers: (List<Palier>) -> Unit,
    onSetReward: (Int, String) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenTitle(overline = "Paliers · saisons · records", title = "Progrès") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Paliers", tab == 0) { tab = 0 }
                Pill("Saison", tab == 1) { tab = 1 }
                Pill("Records", tab == 2) { tab = 2 }
            }
        }
        item {
            when (tab) {
                0 -> PaliersTab(state, onSetRefDay, onSetPaliers)
                1 -> SeasonTab(state, onSetReward)
                else -> RecordsTab(state)
            }
        }
    }
}

// ---------------- Paliers ----------------

@Composable
private fun PaliersTab(state: AppState, onSetRefDay: (LocalDate) -> Unit, onSetPaliers: (List<Palier>) -> Unit) {
    val context = LocalContext.current
    val refDay = LocalDate.ofEpochDay(state.settings.palierRefDay)
    val view = remember(state) { Paliers.compute(state.days, refDay, state.settings.paliers, state.today) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Palier?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Panel {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Depuis le ${dayShort(refDay)} · indice ${fmt(view.refIndex)} → ${fmt(view.currentIndex)}",
                        color = C.Muted, fontSize = 13.sp,
                    )
                    Text(
                        "${fmt(view.equivalentDays.coerceAtLeast(0.0))} jours",
                        color = C.Text, fontSize = 36.sp, fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "de progression parfaite, soit ${signedPct(view.gainPct)}",
                        color = Color(0xFFC9CBD0), fontSize = 13.sp,
                    )
                }
                val last = view.lastReached
                if (last != null) Tag("Palier ${last.palier.days} atteint", C.Lime, C.LimeDim)
            }
            val next = view.next
            if (next != null) {
                ProgressLine(next.progress.toFloat())
                Row {
                    Text(
                        "Prochain : palier ${next.palier.days} · ${signedPct(next.targetGainPct)}",
                        color = C.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
                    )
                    Text(etaLabel(next), color = C.Muted, fontSize = 13.sp)
                }
            } else if (view.started) {
                Text("Tous tes paliers sont atteints. Ajoute-en un plus haut !", color = C.Lime, fontSize = 13.sp)
            }
            if (!view.started) {
                Text("Ta date de départ est dans le futur : le compteur démarrera ce jour-là.", color = C.Muted, fontSize = 13.sp)
            }
        }

        SectionTitle("Tes paliers", "définis par toi")
        view.statuses.forEach { s -> PalierRow(s) { deleting = s.palier } }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionChip(
                modifier = Modifier.weight(1f),
                text = "Départ : ${dayShort(refDay)}",
                primary = false,
            ) {
                DatePickerDialog(
                    context,
                    { _, y, m, d -> onSetRefDay(LocalDate.of(y, m + 1, d)) },
                    refDay.year, refDay.monthValue - 1, refDay.dayOfMonth,
                ).show()
            }
            ActionChip(modifier = Modifier.weight(1f), text = "Nouveau palier", primary = true) { adding = true }
        }
        Text(
            "Palier N = l'indice que tu aurais après N journées parfaites (+1 %/jour) depuis ta date de départ. " +
                "Un jour raté ne remet rien à zéro : il retarde juste l'arrivée.",
            color = C.Muted, fontSize = 12.sp,
        )
    }

    if (adding) {
        InputDialog(
            title = "Nouveau palier",
            label = "Nombre de jours (ex. 120)",
            secondLabel = "Nom (optionnel)",
            numeric = true,
            confirmLabel = "Ajouter",
            onDismiss = { adding = false },
            onConfirm = { daysText, name ->
                val days = daysText.toIntOrNull()
                if (days != null && days > 0) {
                    val list = state.settings.paliers.filter { it.days != days } + Palier(days, name)
                    onSetPaliers(list.sortedBy { it.days })
                }
                adding = false
            },
        )
    }
    val toDelete = deleting
    if (toDelete != null) {
        ConfirmDialog(
            title = "Retirer le palier ${toDelete.days} ?",
            text = "Tu pourras le recréer à tout moment.",
            confirmLabel = "Retirer",
            onDismiss = { deleting = null },
            onConfirm = {
                onSetPaliers(state.settings.paliers.filter { it.days != toDelete.days })
                deleting = null
            },
        )
    }
}

private fun etaLabel(s: PalierStatus): String {
    val eta = s.eta ?: return "reprends le rythme"
    return "≈ ${dayShort(eta)} à ce rythme"
}

@Composable
private fun PalierRow(s: PalierStatus, onDelete: () -> Unit) {
    val reached = s.reachedOn
    val isCurrent = reached == null && s.progress > 0
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(C.Surface)
            .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val badgeMod = Modifier.width(48.dp).height(40.dp).clip(RoundedCornerShape(12.dp))
        Box(
            when {
                reached != null -> badgeMod.background(C.Lime)
                isCurrent -> badgeMod.border(2.dp, C.Lime, RoundedCornerShape(12.dp))
                else -> badgeMod.background(C.Surface2)
            },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${s.palier.days}",
                color = when {
                    reached != null -> C.Bg
                    isCurrent -> C.Lime
                    else -> C.Faint
                },
                fontSize = 15.sp, fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                s.palier.name.ifBlank { "Palier ${s.palier.days}" },
                color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val sub = when {
                reached != null -> "${signedPct(s.targetGainPct)} · atteint le ${dayShort(reached)}"
                s.eta != null -> "${signedPct(s.targetGainPct)} · vers le ${dayShort(s.eta)}"
                else -> signedPct(s.targetGainPct)
            }
            Text(sub, color = C.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when {
            reached != null -> Icon(AppIcons.Check, contentDescription = "atteint", tint = C.Lime, modifier = Modifier.size(20.dp))
            isCurrent -> Text("${(s.progress * 100).toInt()} %", color = C.Lime, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            else -> Icon(AppIcons.Lock, contentDescription = "à venir", tint = C.Off, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDelete) {
            Icon(AppIcons.Cross, contentDescription = "Retirer ce palier", tint = C.Off, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ActionChip(modifier: Modifier, text: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(if (primary) Modifier.background(C.Lime) else Modifier.border(1.dp, C.Border, RoundedCornerShape(14.dp)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (primary) C.Bg else C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------- Saison ----------------

@Composable
private fun SeasonTab(state: AppState, onSetReward: (Int, String) -> Unit) {
    val season = remember(state) { Seasons.compute(state.days, LocalDate.ofEpochDay(state.settings.seasonAnchor), state.today) }
    val byDay = season.days.associateBy { it.day }
    val reward = state.settings.rewards[season.number]
    var editingReward by remember { mutableStateOf(false) }
    val challenges = listOf(
        Challenge(
            "Zéro jour négatif",
            if (season.negativeDays == 0) "Tenu · ${season.dayOfSeason}/30" else "Raté · ${season.negativeDays} j négatif${if (season.negativeDays > 1) "s" else ""}",
            if (season.negativeDays == 0) season.dayOfSeason / 30f else 1f,
            if (season.negativeDays == 0) C.Lime else C.Orange,
        ),
        Challenge("10 jours parfaits", "${min(season.perfectDays, 10)}/10", season.perfectDays / 10f, if (season.perfectDays >= 10) C.Lime else C.Text),
        Challenge("Indice saison +10 %", signedPct(season.gainPct), (season.gainPct / 10.0).toFloat(), if (season.gainPct >= 10) C.Lime else C.Text),
    )
    val allDone = season.negativeDays == 0 && season.perfectDays >= 10 && season.gainPct >= 10

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Panel {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("Saison ${season.number} · ${dayShort(season.start)} → ${dayShort(season.end)}", color = C.Muted, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("Jour ${season.dayOfSeason}", color = C.Text, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        Text("/30", color = C.Off, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Indice saison", color = C.Muted, fontSize = 12.sp)
                    Text(
                        signedPct(season.gainPct), color = if (season.gainPct >= 0) C.Lime else C.Orange,
                        fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                for (r in 0 until 3) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        for (c in 0 until 10) {
                            val i = r * 10 + c
                            val date = season.start.plusDays(i.toLong())
                            val sd = byDay[date]
                            val (bg, fg) = if (sd == null) (Color.Transparent to C.Off) else heatColors(sd.result.completion)
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(bg)
                                    .then(
                                        if (date == state.today) Modifier.border(2.dp, C.Text, RoundedCornerShape(7.dp))
                                        else if (sd == null) Modifier.border(1.dp, C.Surface2, RoundedCornerShape(7.dp))
                                        else Modifier,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) { Text("${i + 1}", color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
            }
            Row {
                val left = 30 - season.dayOfSeason
                Text(
                    if (left == 0) "Dernier jour de la saison !" else "$left jour${if (left > 1) "s" else ""} restant${if (left > 1) "s" else ""}",
                    color = C.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f),
                )
                val prev = season.previousGainPct
                if (prev != null) Text("Saison précédente : ${signedPct(prev)}", color = C.Muted, fontSize = 12.sp)
            }
        }

        SectionTitle("Défis de la saison", "bonus à la clé")
        challenges.forEach { ch ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Surface).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row {
                    Text(ch.name, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(ch.status, color = ch.color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                ProgressLine(ch.progress, color = if (ch.color == C.Orange) C.Orange else C.Lime, height = 6.dp)
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, if (allDone) C.Lime else C.Border, RoundedCornerShape(18.dp))
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.Gift, contentDescription = null, tint = C.Lime, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (allDone) "Récompense débloquée !" else "Récompense de fin de saison",
                    color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (reward.isNullOrBlank()) "Si les 3 défis sont tenus : définis ta récompense" else "Si les 3 défis sont tenus : $reward",
                    color = C.Muted, fontSize = 12.sp,
                )
            }
            ActionChip(Modifier.width(96.dp), if (reward.isNullOrBlank()) "Définir" else "Modifier", primary = false) {
                editingReward = true
            }
        }
    }

    if (editingReward) {
        InputDialog(
            title = "Récompense · saison ${season.number}",
            label = "Ex. : resto, nouvelle paire de running…",
            initial = reward ?: "",
            onDismiss = { editingReward = false },
            onConfirm = { text, _ ->
                onSetReward(season.number, text)
                editingReward = false
            },
        )
    }
}

private data class Challenge(val name: String, val status: String, val progress: Float, val color: Color)

// ---------------- Records ----------------

@Composable
private fun RecordsTab(state: AppState) {
    val habits = state.activeHabits
    val rec = remember(state) { Records.compute(state.days, habits.map { it.id }, state.today) }
    val isRecord = rec.currentStreak.length >= 2 && rec.currentStreak.length >= rec.bestStreak.length

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(if (isRecord) C.LimeDim else C.Surface).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(C.Lime),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Progress, contentDescription = null, tint = C.Bg, modifier = Modifier.size(28.dp)) }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    if (isRecord) "Nouveau record" else "Série en cours",
                    color = if (isRecord) C.Lime else C.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                )
                Text("Série de ${rec.currentStreak.length} jour${if (rec.currentStreak.length > 1) "s" else ""}", color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                val best = rec.bestStreak
                Text(
                    if (best.start != null && best.end != null) "Record : ${best.length} j (${dayShort(best.start)} → ${dayShort(best.end)})" else "Jours non négatifs d'affilée",
                    color = Color(0xFFC9CBD0), fontSize = 12.sp,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Indice le plus haut", fmt(rec.maxIndex), rec.maxIndexDay?.let { dayShort(it) } ?: "—", Modifier.weight(1f))
            StatTile("Meilleurs 7 jours", rec.best7GainPct?.let { signedPct(it) } ?: "—", rec.best7End?.let { "fin le ${dayShort(it)}" } ?: "après 7 jours", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Jours parfaits d'affilée", "${rec.bestPerfect.length} j", "en cours : ${rec.currentPerfect.length} j", Modifier.weight(1f))
            StatTile("Jours parfaits au total", "${rec.perfectDays}", "depuis le début", Modifier.weight(1f))
        }

        SectionTitle("Meilleure série par objectif")
        if (habits.isEmpty()) EmptyHint("Ajoute des objectifs pour suivre leurs records.")
        rec.habits.sortedByDescending { it.best.length }.forEach { hs ->
            val h = state.habit(hs.habitId) ?: return@forEach
            val live = hs.current > 0 && hs.current == hs.best.length
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(C.Surface).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(h.name, color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (live) Tag("en cours", C.Lime, C.LimeDim)
                else if (hs.best.end != null) Text("fin le ${dayShort(hs.best.end)}", color = C.Muted, fontSize = 12.sp)
                Spacer(Modifier.width(10.dp))
                Text("${hs.best.length} j", color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
