package com.rakotobe.comeback.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.DayOfWeek
import java.time.LocalDate

/** Masque des jours : bit 0 = lundi … bit 6 = dimanche. */
const val ALL_DAYS = 0b1111111
const val WEEK_DAYS = 0b0011111
const val WEEKEND_DAYS = 0b1100000

@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val daysMask: Int = ALL_DAYS,
    val reminderMinutes: Int? = null,
    // Prévus pour plus tard (pondération, objectifs chiffrés) : non utilisés en V1.
    val weight: Int = 1,
    val sortOrder: Int = 0,
    val createdDay: Long,
    val archivedDay: Long? = null,
) {
    fun isScheduledOn(dow: DayOfWeek): Boolean = ((daysMask shr (dow.value - 1)) and 1) == 1

    fun plannedOn(day: LocalDate): Boolean {
        val e = day.toEpochDay()
        if (e < createdDay) return false
        val archived = archivedDay
        if (archived != null && e >= archived) return false
        return isScheduledOn(day.dayOfWeek)
    }
}

@Entity(tableName = "checks", primaryKeys = ["habitId", "day"])
data class Check(
    val habitId: Long,
    val day: Long,
    val value: Double = 1.0,
)

/** Photo figée d'une journée terminée : l'historique ne bouge plus si on modifie ses objectifs. */
@Entity(tableName = "day_logs")
data class DayLog(
    @PrimaryKey val day: Long,
    val plannedIds: String,
    val doneIds: String,
) {
    fun plannedSet(): Set<Long> = parseIds(plannedIds)
    fun doneSet(): Set<Long> = parseIds(doneIds)

    companion object {
        fun parseIds(s: String): Set<Long> =
            if (s.isBlank()) emptySet() else s.split(",").mapNotNull { it.trim().toLongOrNull() }.toSet()
    }
}
