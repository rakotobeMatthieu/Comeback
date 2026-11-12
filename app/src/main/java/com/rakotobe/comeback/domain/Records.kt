package com.rakotobe.comeback.domain

import java.time.LocalDate

data class Streak(val length: Int = 0, val start: LocalDate? = null, val end: LocalDate? = null)

data class HabitStreaks(val habitId: Long, val best: Streak, val current: Int)

data class RecordsView(
    val currentStreak: Streak,
    val bestStreak: Streak,
    val currentPerfect: Streak,
    val bestPerfect: Streak,
    val maxIndex: Double,
    val maxIndexDay: LocalDate?,
    val best7GainPct: Double?,
    val best7End: LocalDate?,
    val perfectDays: Int,
    val habits: List<HabitStreaks>,
)

object Records {

    /**
     * Séries de jours qui vérifient [pred]. Les jours sans objectif sont ignorés,
     * et la journée en cours ne casse pas la série tant qu'elle n'est pas finie.
     * Retourne (série en cours, meilleure série).
     */
    fun streaks(days: List<ScoredDay>, today: LocalDate, pred: (ScoredDay) -> Boolean): Pair<Streak, Streak> {
        var best = Streak()
        var len = 0
        var start: LocalDate? = null
        var end: LocalDate? = null
        for (d in days) {
            if (d.isNeutral) continue
            val ok = pred(d)
            if (d.day == today && !ok) continue
            if (ok) {
                if (len == 0) start = d.day
                len++
                end = d.day
                if (len > best.length) best = Streak(len, start, end)
            } else {
                len = 0
                start = null
                end = null
            }
        }
        return Streak(len, start, end) to best
    }

    fun habitStreaks(days: List<ScoredDay>, habitId: Long, today: LocalDate): HabitStreaks {
        var best = Streak()
        var run = 0
        var start: LocalDate? = null
        for (d in days) {
            if (habitId !in d.result.planned) continue
            val done = habitId in d.result.done
            if (d.day == today && !done) continue
            if (done) {
                if (run == 0) start = d.day
                run++
                if (run > best.length) best = Streak(run, start, d.day)
            } else {
                run = 0
                start = null
            }
        }
        return HabitStreaks(habitId, best, run)
    }

    fun compute(days: List<ScoredDay>, habitIds: List<Long>, today: LocalDate): RecordsView {
        val (current, best) = streaks(days, today) { !it.isNegative }
        val (currentPerfect, bestPerfect) = streaks(days, today) { it.result.isPerfect }
        val max = days.maxByOrNull { it.index }
        var best7: Double? = null
        var best7End: LocalDate? = null
        for (i in 6 until days.size) {
            val g = (days[i].index / days[i - 6].indexBefore - 1) * 100
            if (best7 == null || g > best7) {
                best7 = g
                best7End = days[i].day
            }
        }
        return RecordsView(
            currentStreak = current,
            bestStreak = best,
            currentPerfect = currentPerfect,
            bestPerfect = bestPerfect,
            maxIndex = max?.index ?: Scoring.BASE_INDEX,
            maxIndexDay = max?.day,
            best7GainPct = best7,
            best7End = best7End,
            perfectDays = days.count { it.result.isPerfect },
            habits = habitIds.map { habitStreaks(days, it, today) },
        )
    }
}
