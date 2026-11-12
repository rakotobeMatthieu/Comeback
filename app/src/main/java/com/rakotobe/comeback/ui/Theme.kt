package com.rakotobe.comeback.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** Palette des maquettes. */
object C {
    val Bg = Color(0xFF111214)
    val Surface = Color(0xFF1C1D21)
    val Surface2 = Color(0xFF2A2C31)
    val Border = Color(0xFF33363C)
    val NavBg = Color(0xFF17181B)
    val Text = Color(0xFFF2F2F0)
    val Muted = Color(0xFFA1A3A8)
    val Faint = Color(0xFF8A8E97)
    val Off = Color(0xFF5A5D66)
    val Lime = Color(0xFFB8F25B)
    val LimeDim = Color(0xFF26301A)
    val Orange = Color(0xFFFFA066)
    val OrangeDim = Color(0xFF3A2619)
    val Heat1 = Color(0xFF33402A)
    val Heat2 = Color(0xFF556F31)
    val Heat3 = Color(0xFF8DB846)
}

@Composable
fun ComebackTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Lime,
            onPrimary = C.Bg,
            primaryContainer = C.LimeDim,
            onPrimaryContainer = C.Lime,
            secondary = C.Orange,
            onSecondary = C.Bg,
            background = C.Bg,
            onBackground = C.Text,
            surface = C.Bg,
            onSurface = C.Text,
            surfaceVariant = C.Surface,
            onSurfaceVariant = C.Muted,
            surfaceContainer = C.NavBg,
            surfaceContainerHigh = C.Surface,
            surfaceContainerHighest = C.Surface2,
            outline = C.Border,
            error = C.Orange,
        ),
        content = content,
    )
}

/** Couleur de fond / texte d'une case selon le taux de réussite du jour. */
fun heatColors(completion: Double?): Pair<Color, Color> = when {
    completion == null -> C.Surface2 to C.Faint
    completion <= 0.0 -> C.OrangeDim to C.Orange
    completion < 0.5 -> C.Heat1 to Color(0xFFDDE6D0)
    completion < 0.8 -> C.Heat2 to C.Text
    completion < 1.0 -> C.Heat3 to C.Bg
    else -> C.Lime to C.Bg
}

// ---------- Icônes (traits, comme dans les maquettes) ----------

private fun strokeIcon(name: String, vararg paths: String): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    for (p in paths) {
        builder.addPath(
            pathData = addPathNodes(p),
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return builder.build()
}

object AppIcons {
    val Today: ImageVector = strokeIcon("today", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z", "M8 12.5l2.5 2.5L16 9.5")
    val Evolution: ImageVector = strokeIcon("evolution", "M3 17l6-6 4 4 8-8", "M15 7h6v6")
    val Completion: ImageVector = strokeIcon(
        "completion",
        "M3.5 3.5h7v7h-7z", "M13.5 3.5h7v7h-7z", "M3.5 13.5h7v7h-7z", "M13.5 13.5h7v7h-7z",
    )
    val Progress: ImageVector = strokeIcon(
        "progress",
        "M8 4h8v5a4 4 0 0 1-8 0V4z", "M8 6H5.5a2.5 2.5 0 0 0 2.6 3.5", "M16 6h2.5a2.5 2.5 0 0 1-2.6 3.5",
        "M12 13v4", "M8.5 20h7", "M10 17h4",
    )
    val Habits: ImageVector = strokeIcon(
        "habits",
        "M4 6h9M17 6h3M4 12h3M11 12h9M4 18h11M19 18h1",
        "M13 6a2 2 0 1 0 4 0a2 2 0 1 0-4 0", "M7 12a2 2 0 1 0 4 0a2 2 0 1 0-4 0", "M15 18a2 2 0 1 0 4 0a2 2 0 1 0-4 0",
    )
    val Check: ImageVector = strokeIcon("check", "M5 12.5l4.5 4.5L19 7.5")
    val Cross: ImageVector = strokeIcon("cross", "M7 7l10 10M17 7L7 17")
    val Clock: ImageVector = strokeIcon("clock", "M12 3.5a8.5 8.5 0 1 0 0 17a8.5 8.5 0 1 0 0-17z", "M12 7.5V12l3 2")
    val Lock: ImageVector = strokeIcon("lock", "M5 11h14v9H5z", "M8 11V8a4 4 0 0 1 8 0v3")
    val Calendar: ImageVector = strokeIcon("calendar", "M4 5h16v15H4z", "M4 10h16M9 3v4M15 3v4")
    val Gift: ImageVector = strokeIcon("gift", "M3.5 8h17v12h-17z", "M3.5 12h17M12 8v12", "M12 8c-1.5-3-5-3.5-5-1.2C7 8 9.5 8 12 8z", "M12 8c1.5-3 5-3.5 5-1.2C17 8 14.5 8 12 8z")
    val Plus: ImageVector = strokeIcon("plus", "M12 5v14M5 12h14")
    val Chevron: ImageVector = strokeIcon("chevron", "M9 6l6 6-6 6")
    val ChevronLeft: ImageVector = strokeIcon("chevronLeft", "M15 6l-6 6 6 6")
    val Back: ImageVector = strokeIcon("back", "M19 12H5M11 6l-6 6 6 6")
    val Settings: ImageVector = strokeIcon(
        "settings",
        "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z",
        "M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5.3 5.3l2.1 2.1M16.6 16.6l2.1 2.1M5.3 18.7l2.1-2.1M16.6 7.4l2.1-2.1",
    )
}

// ---------- Mise en forme ----------

private val FR: Locale = Locale.FRANCE

fun fmt(v: Double, digits: Int = 1): String = "%.${digits}f".format(FR, v)

fun signedPct(v: Double, digits: Int = 1): String = (if (v < 0) "−" else "+") + fmt(abs(v), digits) + " %"

fun dayLong(d: LocalDate): String =
    d.format(DateTimeFormatter.ofPattern("EEEE d MMMM", FR)).replaceFirstChar { it.titlecase(FR) }

fun dayShort(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("d MMM", FR))

fun dayFull(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("d MMMM yyyy", FR))

fun timeLabel(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

val DAY_LETTERS = listOf("L", "M", "M", "J", "V", "S", "D")
private val DAY_SHORT = listOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim")

fun daysLabel(mask: Int): String = when (mask) {
    0b1111111 -> "Tous les jours"
    0b0011111 -> "En semaine"
    0b1100000 -> "Week-end"
    0 -> "Aucun jour"
    else -> (0 until 7).filter { ((mask shr it) and 1) == 1 }.joinToString(" · ") { DAY_SHORT[it] }
}
