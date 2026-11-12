package com.rakotobe.comeback.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ScoringTest {

    private val d0: LocalDate = LocalDate.of(2026, 11, 2)

    /** Jour i : [done] objectifs faits sur [planned]. */
    private fun day(i: Int, done: Int, planned: Int): DayResult {
        val p = (1..planned).map { it.toLong() }.toSet()
        val dn = (1..done).map { it.toLong() }.toSet()
        return DayResult(d0.plusDays(i.toLong()), p, dn)
    }

    @Test
    fun baseDelta_followsRules() {
        assertEquals(1.0, Scoring.baseDelta(1.0), 1e-9)
        assertEquals(-0.5, Scoring.baseDelta(0.0), 1e-9)
        assertEquals(0.0, Scoring.baseDelta(1.0 / 3.0), 0.0)
        assertEquals(0.25, Scoring.baseDelta(0.5), 1e-9)
    }

    @Test
    fun perfectDay_gainsOnePercent() {
        val s = Scoring.score(listOf(day(0, 3, 3)))
        assertEquals(101.0, s.last().index, 1e-9)
    }

    @Test
    fun negativeDays_accelerate_andAreCapped() {
        val days = (0 until 6).map { day(it, 0, 2) }
        val deltas = Scoring.score(days).map { it.delta }
        assertEquals(listOf(-0.5, -0.75, -1.0, -1.25, -1.5, -1.5), deltas.map { Math.round(it * 100) / 100.0 })
    }

    @Test
    fun positiveDay_resetsNegativeStreak() {
        val s = Scoring.score(listOf(day(0, 0, 2), day(1, 0, 2), day(2, 2, 2), day(3, 0, 2)))
        assertEquals(-0.5, s[3].delta, 1e-9)
    }

    @Test
    fun neutralDay_isZero_andKeepsStreak() {
        val s = Scoring.score(listOf(day(0, 0, 2), day(1, 0, 0), day(2, 0, 2)))
        assertEquals(0.0, s[1].delta, 0.0)
        assertEquals(-0.75, s[2].delta, 1e-9)
    }

    @Test
    fun paliers_useEquivalentPerfectDays() {
        val days = (0 until 30).map { day(it, 2, 2) }
        val scored = Scoring.score(days)
        val view = Paliers.compute(scored, d0, Paliers.DEFAULT, d0.plusDays(29))
        assertEquals(30.0, view.equivalentDays, 1e-6)
        assertEquals(d0.plusDays(6), view.statuses.first { it.palier.days == 7 }.reachedOn)
        assertEquals(d0.plusDays(29), view.statuses.first { it.palier.days == 30 }.reachedOn)
        assertNull(view.statuses.first { it.palier.days == 60 }.reachedOn)
        assertNotNull(view.statuses.first { it.palier.days == 60 }.eta)
    }

    @Test
    fun paliers_badDayDelaysButDoesNotReset() {
        val days = (0 until 10).map { day(it, 2, 2) } + day(10, 0, 2)
        val scored = Scoring.score(days)
        val view = Paliers.compute(scored, d0, Paliers.DEFAULT, d0.plusDays(10))
        assertEquals(9.5, view.equivalentDays, 0.05)
        assertNotNull(view.statuses.first { it.palier.days == 7 }.reachedOn)
    }

    @Test
    fun records_streakSkipsNeutralDays_andIgnoresUnfinishedToday() {
        val days = listOf(day(0, 2, 2), day(1, 0, 0), day(2, 1, 2), day(3, 0, 2))
        val scored = Scoring.score(days)
        val r = Records.compute(scored, listOf(1L, 2L), d0.plusDays(3))
        assertEquals(2, r.currentStreak.length)
        assertEquals(2, r.bestStreak.length)
        assertEquals(1, r.perfectDays)
    }

    @Test
    fun seasons_areThirtyDaysFromAnchor() {
        val days = (0 until 35).map { day(it, 1, 1) }
        val v = Seasons.compute(Scoring.score(days), d0, d0.plusDays(34))
        assertEquals(2, v.number)
        assertEquals(5, v.dayOfSeason)
        assertEquals(5, v.days.size)
        assertNotNull(v.previousGainPct)
    }
}
