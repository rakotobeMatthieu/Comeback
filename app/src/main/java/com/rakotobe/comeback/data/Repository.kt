package com.rakotobe.comeback.data

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.room.withTransaction
import com.rakotobe.comeback.domain.DayResult
import com.rakotobe.comeback.domain.Palier
import com.rakotobe.comeback.domain.ScoredDay
import com.rakotobe.comeback.domain.Scoring
import com.rakotobe.comeback.reminders.Notifications
import com.rakotobe.comeback.widget.ComebackWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Tout ce dont les écrans ont besoin, recalculé à chaque changement. */
data class AppState(
    val today: LocalDate,
    val now: LocalTime,
    val allHabits: List<Habit>,
    val days: List<ScoredDay>,
    val settings: SettingsState,
    val lockedThrough: LocalDate,
) {
    val activeHabits: List<Habit> get() = allHabits.filter { it.archivedDay == null }
    val todayDay: ScoredDay? get() = days.lastOrNull()?.takeIf { it.day == today }
    val yesterdayDay: ScoredDay? get() = days.firstOrNull { it.day == today.minusDays(1) }
    val yesterdayEditable: Boolean get() = yesterdayDay != null && today.minusDays(1).isAfter(lockedThrough)
    val startDay: LocalDate get() = LocalDate.ofEpochDay(settings.startDay)

    fun habit(id: Long): Habit? = allHabits.firstOrNull { it.id == id }
    fun habitsOf(result: DayResult): List<Habit> = allHabits.filter { it.id in result.planned }
}

class Repository(private val context: Context) {
    private val db = AppDatabase.build(context)
    private val dao = db.dao()
    val settings = Settings(context)

    /** Pour l'app : se rafraîchit aussi toutes les 30 s (passage de midi, de minuit…). */
    val state: Flow<AppState> = buildFlow(withTicker = true)

    /** Pour le widget : uniquement sur changement de données. */
    val liveState: Flow<AppState> = buildFlow(withTicker = false)

    private fun buildFlow(withTicker: Boolean): Flow<AppState> {
        val tick: Flow<Unit> = if (withTicker) {
            flow {
                while (true) {
                    emit(Unit)
                    delay(30_000)
                }
            }
        } else {
            flowOf(Unit)
        }
        return combine(dao.observeHabits(), dao.observeChecks(), dao.observeLogs(), settings.state, tick) { h, c, l, s, _ ->
            buildState(h, c, l, s, LocalDateTime.now())
        }.flowOn(Dispatchers.Default)
    }

    private fun buildState(
        habits: List<Habit>,
        checks: List<Check>,
        logs: List<DayLog>,
        s: SettingsState,
        now: LocalDateTime,
    ): AppState {
        val today = now.toLocalDate()
        val time = now.toLocalTime()
        val locked = lockedThrough(today, time)
        val configuredStart = LocalDate.ofEpochDay(s.startDay)
        val start: LocalDate = if (configuredStart.isAfter(today)) today else configuredStart
        val checksByDay: Map<Long, Set<Long>> = checks.groupBy { it.day }.mapValues { e -> e.value.map { it.habitId }.toSet() }
        val logsByDay = logs.associateBy { it.day }

        val results = ArrayList<DayResult>()
        var d = start
        while (!d.isAfter(today)) {
            val e = d.toEpochDay()
            val isLocked = !d.isAfter(locked)
            val log = logsByDay[e]
            results += if (isLocked && log != null) {
                DayResult(d, log.plannedSet(), log.doneSet(), true)
            } else {
                val planned = habits.filter { it.plannedOn(d) }.map { it.id }.toSet()
                DayResult(d, planned, (checksByDay[e] ?: emptySet()).intersect(planned), isLocked)
            }
            d = d.plusDays(1)
        }
        return AppState(today, time, habits, Scoring.score(results), s, locked)
    }

    // ---------- Validation des objectifs ----------

    fun isEditable(day: LocalDate, now: LocalDateTime = LocalDateTime.now()): Boolean {
        val start = LocalDate.ofEpochDay(settings.state.value.startDay)
        return day.isAfter(lockedThrough(now.toLocalDate(), now.toLocalTime())) &&
            !day.isAfter(now.toLocalDate()) && !day.isBefore(start)
    }

    suspend fun toggle(habitId: Long, day: LocalDate): Boolean {
        if (!isEditable(day)) return false
        val e = day.toEpochDay()
        if (dao.check(habitId, e) != null) dao.deleteCheck(habitId, e) else dao.upsertCheck(Check(habitId, e, 1.0))
        refreshWidgets()
        return true
    }

    suspend fun setDone(habitId: Long, day: LocalDate, done: Boolean) {
        if (!isEditable(day)) return
        val e = day.toEpochDay()
        if (done) dao.upsertCheck(Check(habitId, e, 1.0)) else dao.deleteCheck(habitId, e)
        refreshWidgets()
    }

    // ---------- Objectifs ----------

    suspend fun saveHabit(id: Long?, name: String, daysMask: Int, reminderMinutes: Int?) {
        val clean = name.trim()
        if (id == null) {
            dao.insertHabit(
                Habit(
                    name = clean,
                    daysMask = daysMask,
                    reminderMinutes = reminderMinutes,
                    sortOrder = dao.maxSortOrder() + 1,
                    createdDay = LocalDate.now().toEpochDay(),
                ),
            )
        } else {
            val existing = dao.habit(id) ?: return
            dao.updateHabit(existing.copy(name = clean, daysMask = daysMask, reminderMinutes = reminderMinutes))
        }
        refreshWidgets()
    }

    /** « Supprimer » = archiver : l'objectif disparaît mais l'historique reste juste. */
    suspend fun archiveHabit(id: Long) {
        val existing = dao.habit(id) ?: return
        dao.updateHabit(existing.copy(archivedDay = LocalDate.now().toEpochDay()))
        refreshWidgets()
    }

    // ---------- Réglages ----------

    fun setPalierRefDay(day: LocalDate) = settings.setPalierRefDay(day)
    fun setPaliers(list: List<Palier>) = settings.setPaliers(list)
    fun setReward(season: Int, text: String) = settings.setReward(season, text)

    // ---------- Journées figées ----------

    /** Fige les journées terminées (la veille se verrouille le lendemain à midi). */
    suspend fun lockDays() {
        val now = LocalDateTime.now()
        val locked = lockedThrough(now.toLocalDate(), now.toLocalTime())
        val start = LocalDate.ofEpochDay(settings.state.value.startDay)
        val last = dao.lastLoggedDay()
        val afterLast: LocalDate? = if (last != null) LocalDate.ofEpochDay(last + 1) else null
        var d: LocalDate = if (afterLast != null && afterLast.isAfter(start)) afterLast else start
        if (d.isAfter(locked)) return
        val habits = dao.habits()
        val logs = ArrayList<DayLog>()
        while (!d.isAfter(locked)) {
            val e = d.toEpochDay()
            val planned = habits.filter { it.plannedOn(d) }.map { it.id }
            val done = dao.checksOn(e).map { it.habitId }.filter { it in planned }
            logs += DayLog(e, planned.joinToString(","), done.joinToString(","))
            d = d.plusDays(1)
        }
        dao.upsertLogs(logs)
    }

    // ---------- Rappels ----------

    suspend fun sendDueReminders() {
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val minutes = now.hour * 60 + now.minute
        val e = today.toEpochDay()
        val done = dao.checksOn(e).map { it.habitId }.toSet()
        val already = settings.notifiedOn(e)
        for (h in dao.habits()) {
            val r = h.reminderMinutes ?: continue
            if (!h.plannedOn(today) || h.id in done || h.id in already) continue
            if (minutes >= r && minutes < r + 120) {
                Notifications.showReminder(context, h, e)
                settings.markNotified(e, h.id)
            }
        }
    }

    // ---------- Widget ----------

    suspend fun refreshWidgets() {
        try {
            ComebackWidget().updateAll(context)
        } catch (_: Throwable) {
        }
    }

    // ---------- Sauvegarde ----------

    suspend fun exportJson(): String =
        Backup.toJson(Backup.Data(dao.habits(), dao.checks(), dao.logs(), settings.state.value))

    suspend fun importJson(text: String) {
        val data = Backup.fromJson(text, settings.state.value)
        db.withTransaction {
            dao.clearChecks()
            dao.clearLogs()
            dao.clearHabits()
            dao.insertHabits(data.habits)
            dao.insertChecks(data.checks)
            dao.upsertLogs(data.logs)
        }
        settings.replaceAll(data.settings)
        refreshWidgets()
    }

    companion object {
        /** Dernier jour verrouillé : avant midi, la veille reste modifiable. */
        fun lockedThrough(today: LocalDate, now: LocalTime): LocalDate =
            if (now.isBefore(LocalTime.NOON)) today.minusDays(2) else today.minusDays(1)
    }
}
