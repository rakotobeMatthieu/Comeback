package com.rakotobe.comeback.domain

import java.time.LocalDate

data class SeasonView(
    val number: Int,
    val start: LocalDate,
    val end: LocalDate,
    val dayOfSeason: Int,
    val days: List<ScoredDay>,
    val gainPct: Double,
    val negativeDays: Int,
    val perfectDays: Int,
    val previousGainPct: Double?,
)

/** Saisons de 30 jours, à partir d'une date d'ancrage. */
object Seasons {
    const val LENGTH = 30

    fun seasonIndex(anchor: LocalDate, day: LocalDate): Int =
        Math.floorDiv(day.toEpochDay() - anchor.toEpochDay(), LENGTH.toLong()).toInt()

    fun gainPct(days: List<ScoredDay>): Double =
        if (days.isEmpty()) 0.0 else (days.last().index / days.first().indexBefore - 1) * 100

    fun compute(scored: List<ScoredDay>, anchor: LocalDate, today: LocalDate): SeasonView {
        val k = maxOf(0, seasonIndex(anchor, today))
        val start = anchor.plusDays(k.toLong() * LENGTH)
        val end = start.plusDays((LENGTH - 1).toLong())
        val inSeason = scored.filter { !it.day.isBefore(start) && !it.day.isAfter(end) }
        val prevStart = start.minusDays(LENGTH.toLong())
        val prev = if (k > 0) scored.filter { !it.day.isBefore(prevStart) && it.day.isBefore(start) } else emptyList()
        val dayOfSeason = (today.toEpochDay() - start.toEpochDay()).toInt() + 1
        return SeasonView(
            number = k + 1,
            start = start,
            end = end,
            dayOfSeason = dayOfSeason.coerceIn(1, LENGTH),
            days = inSeason,
            gainPct = gainPct(inSeason),
            negativeDays = inSeason.count { it.isNegative },
            perfectDays = inSeason.count { it.result.isPerfect },
            previousGainPct = if (prev.isEmpty()) null else gainPct(prev),
        )
    }
}
