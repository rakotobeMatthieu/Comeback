package com.rakotobe.comeback.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

/** Sauvegarde complète au format JSON (export / import depuis les réglages). */
object Backup {
    private const val FORMAT = "comeback-backup"
    private const val VERSION = 1

    data class Data(
        val habits: List<Habit>,
        val checks: List<Check>,
        val logs: List<DayLog>,
        val settings: SettingsState,
    )

    fun toJson(d: Data): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", VERSION)
        root.put("exportedAt", LocalDateTime.now().toString())

        val habits = JSONArray()
        for (h in d.habits) {
            val o = JSONObject()
            o.put("id", h.id)
            o.put("name", h.name)
            o.put("daysMask", h.daysMask)
            o.put("reminderMinutes", h.reminderMinutes ?: JSONObject.NULL)
            o.put("weight", h.weight)
            o.put("sortOrder", h.sortOrder)
            o.put("createdDay", h.createdDay)
            o.put("archivedDay", h.archivedDay ?: JSONObject.NULL)
            habits.put(o)
        }
        root.put("habits", habits)

        val checks = JSONArray()
        for (c in d.checks) {
            val o = JSONObject()
            o.put("habitId", c.habitId)
            o.put("day", c.day)
            o.put("value", c.value)
            checks.put(o)
        }
        root.put("checks", checks)

        val logs = JSONArray()
        for (l in d.logs) {
            val o = JSONObject()
            o.put("day", l.day)
            o.put("planned", l.plannedIds)
            o.put("done", l.doneIds)
            logs.put(o)
        }
        root.put("logs", logs)

        val s = JSONObject()
        s.put("startDay", d.settings.startDay)
        s.put("palierRefDay", d.settings.palierRefDay)
        s.put("seasonAnchor", d.settings.seasonAnchor)
        s.put("paliers", Settings.encodePaliers(d.settings.paliers))
        val rewards = JSONObject()
        for ((n, t) in d.settings.rewards) rewards.put(n.toString(), t)
        s.put("rewards", rewards)
        root.put("settings", s)

        return root.toString(2)
    }

    fun fromJson(text: String, current: SettingsState): Data {
        val root = JSONObject(text)
        require(root.optString("format") == FORMAT) { "Ce fichier n'est pas une sauvegarde ComeBack." }

        val habits = ArrayList<Habit>()
        val ha = root.getJSONArray("habits")
        for (i in 0 until ha.length()) {
            val o = ha.getJSONObject(i)
            habits += Habit(
                id = o.getLong("id"),
                name = o.getString("name"),
                daysMask = o.optInt("daysMask", ALL_DAYS),
                reminderMinutes = if (o.isNull("reminderMinutes")) null else o.getInt("reminderMinutes"),
                weight = o.optInt("weight", 1),
                sortOrder = o.optInt("sortOrder", 0),
                createdDay = o.getLong("createdDay"),
                archivedDay = if (o.isNull("archivedDay")) null else o.getLong("archivedDay"),
            )
        }

        val checks = ArrayList<Check>()
        val ca = root.getJSONArray("checks")
        for (i in 0 until ca.length()) {
            val o = ca.getJSONObject(i)
            checks += Check(o.getLong("habitId"), o.getLong("day"), o.optDouble("value", 1.0))
        }

        val logs = ArrayList<DayLog>()
        val la = root.getJSONArray("logs")
        for (i in 0 until la.length()) {
            val o = la.getJSONObject(i)
            logs += DayLog(o.getLong("day"), o.optString("planned", ""), o.optString("done", ""))
        }

        val so = root.optJSONObject("settings")
        val settings = if (so == null) current else {
            val rewards = HashMap<Int, String>()
            val ro = so.optJSONObject("rewards")
            if (ro != null) {
                val keys = ro.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    k.toIntOrNull()?.let { rewards[it] = ro.getString(k) }
                }
            }
            val start = so.optLong("startDay", current.startDay)
            current.copy(
                startDay = start,
                palierRefDay = so.optLong("palierRefDay", start),
                seasonAnchor = so.optLong("seasonAnchor", start),
                paliers = Settings.decodePaliers(so.optString("paliers", "")),
                rewards = rewards,
            )
        }
        return Data(habits, checks, logs, settings)
    }
}
