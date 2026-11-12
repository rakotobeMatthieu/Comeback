package com.rakotobe.comeback.domain

import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Palier N = l'indice qu'on aurait après N journées parfaites (+1 %/jour) depuis la date de départ. */
data class Palier(val days: Int, val name: String)

data class PalierStatus(
    val palier: Palier,
    val targetGainPct: Double,
    val reachedOn: LocalDate?,
    val progress: Double,
    val eta: LocalDate?,
)

data class PaliersView(
    val refDay: LocalDate,
    val refIndex: Double,
    val currentIndex: Double,
    val equivalentDays: Double,
    val gainPct: Double,
    val started: Boolean,
    val statuses: List<PalierStatus>,
) {
    val next: PalierStatus? get() = statuses.firstOrNull { it.reachedOn == null }
    val lastReached: PalierStatus? get() = statuses.lastOrNull { it.reachedOn != null }
}

object Paliers {
    val DEFAULT = listOf(
        Palier(7, "Lancé"),
        Palier(30, "Ancré"),
        Palier(60, "Solide"),
        Palier(90, "Transformé"),
        Palier(180, "Inarrêtable"),
        Palier(365, "Nouvelle version"),
    )

    private val LN_DAY = ln(1.01)

    fun factor(days: Int): Double = 1.01.pow(days)

    fun equivalentDays(index: Double, refIndex: Double): Double =
        if (refIndex <= 0 || index <= 0) 0.0 else ln(index / refIndex) / LN_DAY

    /** Croissance moyenne (log) par jour sur les derniers jours, pour estimer les dates. */
    fun dailyLogRate(scored: List<ScoredDay>, window: Int = 14): Double? {
        if (scored.isEmpty()) return null
        val slice = scored.takeLast(min(window, scored.size))
        return ln(slice.last().index / slice.first().indexBefore) / slice.size
    }

    fun compute(
        scored: List<ScoredDay>,
        refDay: LocalDate,
        paliers: List<Palier>,
        today: LocalDate,
    ): PaliersView {
        val refScored = scored.firstOrNull { !it.day.isBefore(refDay) }
        val started = refScored != null
        val current = scored.lastOrNull()?.index ?: Scoring.BASE_INDEX
        val refIndex = refScored?.indexBefore ?: current
        val eq = if (started) equivalentDays(current, refIndex) else 0.0
        val rate = dailyLogRate(scored)

        val statuses = paliers.sortedBy { it.days }.map { p ->
            val target = refIndex * factor(p.days)
            val reached = if (!started) null else scored.firstOrNull {
                !it.day.isBefore(refDay) && it.index >= target - 1e-9
            }?.day
            val progress = when {
                reached != null -> 1.0
                started && p.days > 0 -> (eq / p.days).coerceIn(0.0, 1.0)
                else -> 0.0
            }
            val eta = if (reached == null && started && rate != null && rate > 1e-6) {
                val remaining = max(ln(target / current) / rate, 0.0)
                if (remaining < 36500) today.plusDays(ceil(remaining).toLong()) else null
            } else null
            PalierStatus(p, (factor(p.days) - 1) * 100, reached, progress, eta)
        }

        return PaliersView(
            refDay = refDay,
            refIndex = refIndex,
            currentIndex = current,
            equivalentDays = eq,
            gainPct = (current / refIndex - 1) * 100,
            started = started,
            statuses = statuses,
        )
    }
}
