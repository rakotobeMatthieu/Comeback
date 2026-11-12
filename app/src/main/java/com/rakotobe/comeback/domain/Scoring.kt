package com.rakotobe.comeback.domain

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.min

/** Résultat brut d'une journée : objectifs prévus et objectifs faits. */
data class DayResult(
    val day: LocalDate,
    val planned: Set<Long>,
    val done: Set<Long>,
    val locked: Boolean = true,
) {
    val plannedCount: Int get() = planned.size
    val doneCount: Int get() = planned.count { it in done }
    val completion: Double? get() = if (planned.isEmpty()) null else doneCount.toDouble() / planned.size
    val isPerfect: Boolean get() = planned.isNotEmpty() && doneCount == planned.size
}

/** Journée notée : variation de l'indice (en %) et indice avant / après. */
data class ScoredDay(
    val result: DayResult,
    val delta: Double,
    val indexBefore: Double,
    val index: Double,
    val negativeStreak: Int,
) {
    val day: LocalDate get() = result.day
    val isNeutral: Boolean get() = result.planned.isEmpty()
    val isNegative: Boolean get() = delta < 0
}

/**
 * Règles de l'indice ComeBack :
 *  - journée à 100 % : +1 %
 *  - journée partielle : linéaire entre -0,5 % et +1 % (positive dès 1/3 des objectifs)
 *  - journée à 0 % : -0,5 %, amplifié si les jours négatifs s'enchaînent (x1,5, x2 … jusqu'à x3)
 *  - jour sans objectif prévu : 0, ne casse pas et ne prolonge pas la série négative
 */
object Scoring {
    const val BASE_INDEX = 100.0
    const val MAX_GAIN = 1.0
    const val ZERO_LOSS = -0.5
    const val MAX_MULTIPLIER = 3.0

    fun baseDelta(completion: Double): Double {
        val raw = (MAX_GAIN - ZERO_LOSS) * completion + ZERO_LOSS
        return if (abs(raw) < 1e-9) 0.0 else raw
    }

    fun multiplier(negativeStreak: Int): Double =
        if (negativeStreak <= 1) 1.0 else min(1.0 + 0.5 * (negativeStreak - 1), MAX_MULTIPLIER)

    fun score(days: List<DayResult>, baseIndex: Double = BASE_INDEX): List<ScoredDay> {
        var index = baseIndex
        var streak = 0
        val out = ArrayList<ScoredDay>(days.size)
        for (d in days) {
            val c = d.completion
            var delta = 0.0
            if (c != null) {
                delta = baseDelta(c)
                if (delta < 0) {
                    streak += 1
                    delta *= multiplier(streak)
                } else {
                    streak = 0
                }
            }
            val before = index
            index *= (1 + delta / 100.0)
            out += ScoredDay(d, delta, before, index, streak)
        }
        return out
    }
}
