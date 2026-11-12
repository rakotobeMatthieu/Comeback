package com.rakotobe.comeback.data

import android.content.Context
import com.rakotobe.comeback.domain.Palier
import com.rakotobe.comeback.domain.Paliers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

data class SettingsState(
    val startDay: Long,
    val palierRefDay: Long,
    val paliers: List<Palier>,
    val seasonAnchor: Long,
    val rewards: Map<Int, String>,
    val notifAsked: Boolean,
)

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("comeback", Context.MODE_PRIVATE)
    private val mutableState: MutableStateFlow<SettingsState>
    val state: StateFlow<SettingsState>

    init {
        if (!prefs.contains(K_START)) {
            val today = LocalDate.now().toEpochDay()
            prefs.edit()
                .putLong(K_START, today)
                .putLong(K_PALIER_REF, today)
                .putLong(K_SEASON, today)
                .putString(K_PALIERS, encodePaliers(Paliers.DEFAULT))
                .apply()
        }
        mutableState = MutableStateFlow(load())
        state = mutableState.asStateFlow()
    }

    private fun load(): SettingsState {
        val start = prefs.getLong(K_START, LocalDate.now().toEpochDay())
        val rewards = HashMap<Int, String>()
        for ((key, value) in prefs.all) {
            if (key.startsWith(K_REWARD) && value is String) {
                key.removePrefix(K_REWARD).toIntOrNull()?.let { rewards[it] = value }
            }
        }
        return SettingsState(
            startDay = start,
            palierRefDay = prefs.getLong(K_PALIER_REF, start),
            paliers = decodePaliers(prefs.getString(K_PALIERS, null)),
            seasonAnchor = prefs.getLong(K_SEASON, start),
            rewards = rewards,
            notifAsked = prefs.getBoolean(K_NOTIF_ASKED, false),
        )
    }

    private fun reload() {
        mutableState.value = load()
    }

    fun setPalierRefDay(day: LocalDate) {
        prefs.edit().putLong(K_PALIER_REF, day.toEpochDay()).apply()
        reload()
    }

    fun setPaliers(list: List<Palier>) {
        prefs.edit().putString(K_PALIERS, encodePaliers(list)).apply()
        reload()
    }

    fun setReward(season: Int, text: String) {
        prefs.edit().putString(K_REWARD + season, text).apply()
        reload()
    }

    fun setNotifAsked() {
        prefs.edit().putBoolean(K_NOTIF_ASKED, true).apply()
        reload()
    }

    fun replaceAll(s: SettingsState) {
        val e = prefs.edit()
        for (k in prefs.all.keys) if (k.startsWith(K_REWARD)) e.remove(k)
        e.putLong(K_START, s.startDay)
            .putLong(K_PALIER_REF, s.palierRefDay)
            .putLong(K_SEASON, s.seasonAnchor)
            .putString(K_PALIERS, encodePaliers(s.paliers))
        for ((n, t) in s.rewards) e.putString(K_REWARD + n, t)
        e.apply()
        reload()
    }

    fun notifiedOn(day: Long): Set<Long> =
        if (prefs.getLong(K_NOTIFIED_DAY, -1L) == day) DayLog.parseIds(prefs.getString(K_NOTIFIED_IDS, "") ?: "")
        else emptySet()

    fun markNotified(day: Long, habitId: Long) {
        val ids = notifiedOn(day) + habitId
        prefs.edit().putLong(K_NOTIFIED_DAY, day).putString(K_NOTIFIED_IDS, ids.joinToString(",")).apply()
    }

    companion object {
        private const val K_START = "startDay"
        private const val K_PALIER_REF = "palierRefDay"
        private const val K_SEASON = "seasonAnchor"
        private const val K_PALIERS = "paliers"
        private const val K_REWARD = "reward_"
        private const val K_NOTIF_ASKED = "notifAsked"
        private const val K_NOTIFIED_DAY = "notifiedDay"
        private const val K_NOTIFIED_IDS = "notifiedIds"

        fun encodePaliers(list: List<Palier>): String =
            list.joinToString(";") { "${it.days}|${it.name.replace(";", " ").replace("|", " ")}" }

        fun decodePaliers(s: String?): List<Palier> {
            if (s.isNullOrBlank()) return Paliers.DEFAULT
            return s.split(";").mapNotNull { part ->
                val bits = part.split("|", limit = 2)
                val days = bits.getOrNull(0)?.trim()?.toIntOrNull() ?: return@mapNotNull null
                Palier(days, bits.getOrNull(1) ?: "")
            }.sortedBy { it.days }
        }
    }
}
