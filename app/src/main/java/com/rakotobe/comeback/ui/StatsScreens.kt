package com.rakotobe.comeback.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rakotobe.comeback.data.AppState
import com.rakotobe.comeback.domain.Records
import com.rakotobe.comeback.domain.ScoredDay
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

// ====================== Évolution ======================

@Composable
fun EvolutionScreen(state: AppState) {
    var period by rememberSaveable { mutableIntStateOf(30) }
    val all = state.days
    val slice = if (period <= 0 || all.size <= period) all else all.takeLast(period)
    val current = all.lastOrNull()?.index ?: 100.0
    val gain = if (slice.isEmpty()) 0.0 else (slice.last().index / slice.first().indexBefore - 1) * 100

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle(overline = "Base 100 au ${dayFull(state.startDay)}", title = "Évolution") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("7 J", period == 7) { period = 7 }
                Pill("30 J", period == 30) { period = 30 }
                Pill("90 J", period == 90) { period = 90 }
                Pill("Tout", period == 0) { period = 0 }
            }
        }
        item {
            Panel(padding = 14.dp) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(fmt(current), color = C.Text, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    val label = if (period == 0) "depuis le début" else "sur ${slice.size} jours"
                    Text(
                        "${signedPct(gain)} $label",
                        color = if (gain >= 0) C.Lime else C.Orange,
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                if (slice.isEmpty()) Text("Pas encore de données.", color = C.Muted) else IndexChart(slice)
            }
        }
        if (slice.isNotEmpty()) {
            item {
                Panel(padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Variation par jour", color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        LegendDot(C.Lime, "gain")
                        Spacer(Modifier.width(10.dp))
                        LegendDot(C.Orange, "perte")
                    }
                    DeltaBars(slice)
                }
            }
        }
        item {
            Panel {
                Text("Comment c'est calculé", color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                RuleLine("+1 %", C.Lime, "Journée à 100 %")
                RuleLine("prorata", C.Text, "Journée partielle : positive dès 1/3 des objectifs")
                RuleLine("−0,5 %", C.Orange, "Journée à 0 %, amplifiée si les jours négatifs s'enchaînent (jusqu'à ×3)")
                RuleLine("0", C.Muted, "Jour sans objectif prévu")
            }
        }
    }
}

@Composable
private fun RuleLine(value: String, color: androidx.compose.ui.graphics.Color, text: String) {
    Row {
        Text(value, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(64.dp))
        Text(text, color = C.Muted, fontSize = 13.sp)
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, color = C.Muted, fontSize = 12.sp)
    }
}

@Composable
private fun IndexChart(slice: List<ScoredDay>) {
    val measurer = rememberTextMeasurer()
    val values = ArrayList<Double>(slice.size + 1)
    values += slice.first().indexBefore
    slice.forEach { values += it.index }
    var lo = values.min()
    var hi = values.max()
    if (hi - lo < 1.0) {
        val mid = (hi + lo) / 2
        lo = mid - 0.5
        hi = mid + 0.5
    }
    val pad = (hi - lo) * 0.12
    lo -= pad
    hi += pad
    val labelStyle = TextStyle(color = C.Faint, fontSize = 10.sp)
    val digits = if (hi - lo < 4) 1 else 0

    Canvas(Modifier.fillMaxWidth().height(190.dp)) {
        val left = 34.dp.toPx()
        val right = size.width - 6.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 22.dp.toPx()
        val n = values.size
        fun x(i: Int): Float = if (n <= 1) (left + right) / 2 else left + (right - left) * i / (n - 1)
        fun y(v: Double): Float = (bottom - (bottom - top) * ((v - lo) / (hi - lo))).toFloat()

        for (k in 0..3) {
            val v = lo + (hi - lo) * k / 3.0
            val yy = y(v)
            drawLine(C.Surface2, Offset(left, yy), Offset(right, yy), 1.dp.toPx())
            drawText(measurer, fmt(v, digits), Offset(0f, yy - 7.dp.toPx()), labelStyle)
        }
        if (100.0 in lo..hi) {
            val yy = y(100.0)
            drawLine(
                C.Off, Offset(left, yy), Offset(right, yy), 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
            )
        }

        val line = Path()
        values.forEachIndexed { i, v -> if (i == 0) line.moveTo(x(i), y(v)) else line.lineTo(x(i), y(v)) }
        val area = Path()
        area.addPath(line)
        area.lineTo(x(n - 1), bottom)
        area.lineTo(x(0), bottom)
        area.close()
        drawPath(area, C.Lime.copy(alpha = 0.10f))
        drawPath(line, C.Lime, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        slice.forEachIndexed { i, d ->
            if (d.isNegative) drawCircle(C.Orange, 3.5.dp.toPx(), Offset(x(i + 1), y(d.index)))
        }
        val last = Offset(x(n - 1), y(values.last()))
        drawCircle(C.Bg, 5.dp.toPx(), last)
        drawCircle(C.Lime, 5.dp.toPx(), last, style = Stroke(2.5.dp.toPx()))

        val first = measurer.measure(dayShort(slice.first().day), labelStyle)
        drawText(first, topLeft = Offset(left, size.height - first.size.height))
        val lastLabel = measurer.measure(dayShort(slice.last().day), labelStyle)
        drawText(lastLabel, topLeft = Offset(right - lastLabel.size.width, size.height - lastLabel.size.height))
    }
}

@Composable
private fun DeltaBars(slice: List<ScoredDay>) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = C.Faint, fontSize = 10.sp)
    val maxAbs = max(1.0, slice.maxOf { abs(it.delta) })
    Canvas(Modifier.fillMaxWidth().height(90.dp)) {
        val left = 34.dp.toPx()
        val right = size.width - 6.dp.toPx()
        val top = 6.dp.toPx()
        val bottom = size.height - 6.dp.toPx()
        val zero = top + (bottom - top) * 0.5f
        val half = (bottom - top) * 0.5f
        drawLine(C.Border, Offset(left, zero), Offset(right, zero), 1.dp.toPx())
        drawText(measurer, "+${fmt(maxAbs, 0)} %", Offset(0f, top - 2.dp.toPx()), labelStyle)
        drawText(measurer, "0", Offset(0f, zero - 7.dp.toPx()), labelStyle)
        drawText(measurer, "−${fmt(maxAbs, 0)} %", Offset(0f, bottom - 12.dp.toPx()), labelStyle)
        val n = slice.size
        val slot = (right - left) / n
        val barW = (slot * 0.62f).coerceAtMost(10.dp.toPx()).coerceAtLeast(1.5f)
        slice.forEachIndexed { i, d ->
            val cx = left + slot * (i + 0.5f)
            val h = (abs(d.delta) / maxAbs * half).toFloat()
            if (d.delta >= 0) {
                drawRect(C.Lime, Offset(cx - barW / 2, zero - h), androidx.compose.ui.geometry.Size(barW, h))
            } else {
                drawRect(C.Orange, Offset(cx - barW / 2, zero), androidx.compose.ui.geometry.Size(barW, h))
            }
        }
    }
}

// ====================== Complétion ======================

@Composable
fun CompletionScreen(state: AppState) {
    val today = state.today
    val byDay = remember(state) { state.days.associateBy { it.day } }
    val last30 = state.days.takeLast(30)
    val planned30 = last30.sumOf { it.result.plannedCount }
    val done30 = last30.sumOf { it.result.doneCount }
    val rate30 = if (planned30 == 0) 0 else (done30 * 100.0 / planned30).toInt()
    val perfect30 = last30.count { it.result.isPerfect }
    val records = remember(state) { Records.compute(state.days, emptyList(), today) }
    var month by remember { mutableStateOf(YearMonth.from(today)) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle(overline = "30 derniers jours", title = "Complétion") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("objectifs tenus", "$rate30 %", "sur 30 j", Modifier.weight(1f))
                StatTile("jours parfaits", "$perfect30", "sur 30 j", Modifier.weight(1f))
                StatTile("série en cours", "${records.currentStreak.length} j", "jours positifs", Modifier.weight(1f))
            }
        }
        item {
            Panel(padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { month = month.minusMonths(1) }) {
                        Icon(AppIcons.ChevronLeft, contentDescription = "Mois précédent", tint = C.Muted)
                    }
                    Text(
                        month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRANCE)).replaceFirstChar { it.titlecase(Locale.FRANCE) },
                        color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    IconButton(onClick = { if (month.isBefore(YearMonth.from(today))) month = month.plusMonths(1) }) {
                        Icon(AppIcons.Chevron, contentDescription = "Mois suivant", tint = C.Muted)
                    }
                }
                MonthGrid(month, today, byDay)
                HeatLegend()
            }
        }
        item { HabitWeekTable(state) }
    }
}

@Composable
private fun MonthGrid(month: YearMonth, today: LocalDate, byDay: Map<LocalDate, ScoredDay>) {
    val offset = month.atDay(1).dayOfWeek.value - 1
    val count = month.lengthOfMonth()
    val cells = offset + count
    val rows = (cells + 6) / 7
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            DAY_LETTERS.forEach {
                Text(it, color = C.Faint, fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        for (r in 0 until rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (c in 0 until 7) {
                    val idx = r * 7 + c
                    val dayNum = idx - offset + 1
                    Box(Modifier.weight(1f).height(32.dp)) {
                        if (dayNum in 1..count) {
                            val date = month.atDay(dayNum)
                            val sd = byDay[date]
                            val isToday = date == today
                            val (bg, fg) = if (sd == null) (androidx.compose.ui.graphics.Color.Transparent to C.Off) else heatColors(sd.result.completion)
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(bg)
                                    .then(
                                        if (isToday) Modifier.border(2.dp, C.Text, RoundedCornerShape(9.dp))
                                        else if (sd == null) Modifier.border(1.dp, C.Surface2, RoundedCornerShape(9.dp))
                                        else Modifier,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("$dayNum", color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeatLegend() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("0 %", color = C.Muted, fontSize = 11.sp)
        listOf(C.OrangeDim, C.Heat1, C.Heat2, C.Heat3, C.Lime).forEach {
            Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(it))
        }
        Text("100 %", color = C.Muted, fontSize = 11.sp)
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(12.dp).border(2.dp, C.Text, RoundedCornerShape(3.dp)))
        Text("aujourd'hui", color = C.Muted, fontSize = 11.sp)
    }
}

@Composable
private fun HabitWeekTable(state: AppState) {
    val today = state.today
    val byDay = state.days.associateBy { it.day }
    val week = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val last30 = state.days.takeLast(30)
    val habits = state.activeHabits
    Panel(padding = 14.dp) {
        Row {
            Text("Par objectif · 7 jours", color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("taux 30 j", color = C.Muted, fontSize = 11.sp)
        }
        if (habits.isEmpty()) {
            Text("Ajoute des objectifs pour voir ce tableau.", color = C.Muted, fontSize = 13.sp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(92.dp))
                week.forEach { d ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(DAY_LETTERS[d.dayOfWeek.value - 1], color = C.Faint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text("${d.dayOfMonth}", color = C.Faint, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.width(40.dp))
            }
            habits.forEach { h ->
                val planned = last30.count { h.id in it.result.planned }
                val done = last30.count { h.id in it.result.planned && h.id in it.result.done }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(h.name, color = C.Text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(92.dp))
                    week.forEach { d ->
                        val sd = byDay[d]
                        val isPlanned = sd != null && h.id in sd.result.planned
                        val isDone = sd != null && h.id in sd.result.done
                        Box(Modifier.weight(1f).height(30.dp), contentAlignment = Alignment.Center) {
                            when {
                                isPlanned && isDone -> Box(
                                    Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(C.Lime),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(AppIcons.Check, contentDescription = "fait", tint = C.Bg, modifier = Modifier.size(15.dp)) }
                                isPlanned && d == today -> Box(
                                    Modifier.size(26.dp).border(2.dp, C.Faint, RoundedCornerShape(8.dp)),
                                )
                                isPlanned -> Box(
                                    Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(C.OrangeDim),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(AppIcons.Cross, contentDescription = "manqué", tint = C.Orange, modifier = Modifier.size(13.dp)) }
                                else -> Box(Modifier.width(10.dp).height(2.dp).background(C.Off))
                            }
                        }
                    }
                    Text(
                        if (planned == 0) "—" else "${done * 100 / planned} %",
                        color = C.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    )
                }
            }
        }
    }
}
