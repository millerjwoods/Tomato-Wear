/* GPL-3.0-or-later. Inspired by Tomato (Nishant Mishra); modified Wear OS work, 2026. */
package dev.tomatowear.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** All timer writes are synchronized so alarm and activity callbacks cannot complete twice. */
class TimerStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("tomato_wear_v1", Context.MODE_PRIVATE)
    companion object { private val lock = Any() }

    data class DayRecord(
        val date: String, val topic: String, val focusMs: Long,
        val breakMs: Long, val completed: Int
    )

    private fun day(now: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))

    fun config(): TimerConfig = synchronized(lock) {
        TimerConfig(
            focusMinutes = prefs.getInt("focusMinutes", 25).coerceIn(1, 180),
            shortBreakMinutes = prefs.getInt("shortBreakMinutes", 5).coerceIn(1, 60),
            longBreakMinutes = prefs.getInt("longBreakMinutes", 15).coerceIn(1, 90),
            focusRounds = prefs.getInt("focusRounds", 4).coerceIn(1, 12),
            vibrate = prefs.getBoolean("vibrate", true),
            sound = prefs.getBoolean("sound", true),
            autoStart = prefs.getBoolean("autoStart", false)
        )
    }

    fun updateConfig(c: TimerConfig) = synchronized(lock) {
        prefs.edit()
            .putInt("focusMinutes", c.focusMinutes.coerceIn(1, 180))
            .putInt("shortBreakMinutes", c.shortBreakMinutes.coerceIn(1, 60))
            .putInt("longBreakMinutes", c.longBreakMinutes.coerceIn(1, 90))
            .putInt("focusRounds", c.focusRounds.coerceIn(1, 12))
            .putBoolean("vibrate", c.vibrate)
            .putBoolean("sound", c.sound)
            .putBoolean("autoStart", c.autoStart)
            .commit()
        val s = stateInternal()
        if (!s.running) saveState(s.copy(remainingAtStartMs = config().duration(s.phase)))
    }

    private fun stateInternal(): TimerState {
        val c = config()
        val phase = runCatching { Phase.valueOf(prefs.getString("phase", "FOCUS")!!) }
            .getOrDefault(Phase.FOCUS)
        return TimerState(
            phase = phase,
            running = prefs.getBoolean("running", false),
            remainingAtStartMs = prefs.getLong("remaining", c.duration(phase)),
            startEpochMs = prefs.getLong("start", 0),
            deadlineEpochMs = prefs.getLong("deadline", 0),
            accruedMs = prefs.getLong("accrued", 0),
            completedFocusInCycle = prefs.getInt("cycle", 0),
            topic = prefs.getString("topic", "General") ?: "General"
        )
    }

    fun state(): TimerState = synchronized(lock) { stateInternal() }

    private fun saveState(s: TimerState) {
        prefs.edit()
            .putString("phase", s.phase.name)
            .putBoolean("running", s.running)
            .putLong("remaining", s.remainingAtStartMs)
            .putLong("start", s.startEpochMs)
            .putLong("deadline", s.deadlineEpochMs)
            .putLong("accrued", s.accruedMs)
            .putInt("cycle", s.completedFocusInCycle)
            .putString("topic", s.topic)
            .commit()
    }

    private fun readStats(): MutableList<DayRecord> {
        val rows = mutableListOf<DayRecord>()
        val array = runCatching { JSONArray(prefs.getString("stats", "[]")) }
            .getOrDefault(JSONArray())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            rows += DayRecord(
                obj.optString("date"), obj.optString("topic", "General"),
                obj.optLong("focus"), obj.optLong("break"), obj.optInt("completed")
            )
        }
        return rows
    }

    fun stats(): List<DayRecord> = synchronized(lock) { readStats().toList() }

    private fun addStat(now: Long, topic: String, phase: Phase, elapsed: Long, completed: Int) {
        if (elapsed <= 0 && completed <= 0) return
        val records = readStats()
        val date = day(now)
        val index = records.indexOfFirst { it.date == date && it.topic == topic }
        val previous = if (index >= 0) records[index] else DayRecord(date, topic, 0, 0, 0)
        val updated = previous.copy(
            focusMs = previous.focusMs + if (phase == Phase.FOCUS) elapsed else 0,
            breakMs = previous.breakMs + if (phase != Phase.FOCUS) elapsed else 0,
            completed = previous.completed + completed
        )
        if (index >= 0) records[index] = updated else records.add(updated)
        val arr = JSONArray()
        records.takeLast(1500).forEach {
            arr.put(JSONObject().put("date", it.date).put("topic", it.topic)
                .put("focus", it.focusMs).put("break", it.breakMs)
                .put("completed", it.completed))
        }
        prefs.edit().putString("stats", arr.toString()).commit()
    }

    /** Crediting elapsed focus at each transition prevents losses when paused or terminated. */
    private fun accrue(s: TimerState, now: Long): TimerState {
        if (!s.running) return s
        val totalElapsed = (now - s.startEpochMs).coerceIn(0L, s.remainingAtStartMs)
        val delta = (totalElapsed - s.accruedMs).coerceAtLeast(0L)
        if (delta > 0) addStat(now, s.topic, s.phase, delta, 0)
        return s.copy(accruedMs = totalElapsed)
    }

    /** Returns the completed phase, or null if no running timer has expired. */
    fun finishIfDue(now: Long = System.currentTimeMillis()): Phase? = synchronized(lock) {
        var s = stateInternal()
        if (!s.running || now < s.deadlineEpochMs) return@synchronized null
        s = accrue(s, now)
        val finished = s.phase
        val completedFocus = if (finished == Phase.FOCUS) s.completedFocusInCycle + 1
            else s.completedFocusInCycle
        if (finished == Phase.FOCUS) addStat(now, s.topic, Phase.FOCUS, 0, 1)
        val next = TimerLogic.nextPhase(finished, completedFocus, config().focusRounds)
        val nextCycle = if (finished == Phase.LONG_BREAK) 0 else completedFocus
        val base = s.copy(
            phase = next, running = false,
            remainingAtStartMs = config().duration(next),
            startEpochMs = 0, deadlineEpochMs = 0,
            accruedMs = 0, completedFocusInCycle = nextCycle
        )
        val resulting = if (config().autoStart) startInternal(base, now) else base
        saveState(resulting)
        finished
    }

    private fun startInternal(s: TimerState, now: Long): TimerState = s.copy(
        running = true,
        startEpochMs = now,
        deadlineEpochMs = now + s.remainingAtStartMs,
        accruedMs = 0
    )

    fun toggle(now: Long = System.currentTimeMillis()) = synchronized(lock) {
        val s = stateInternal()
        if (!s.running) {
            saveState(startInternal(s, now))
        } else {
            val credited = accrue(s, now)
            saveState(credited.copy(
                running = false, remainingAtStartMs = s.remaining(now),
                startEpochMs = 0, deadlineEpochMs = 0, accruedMs = 0
            ))
        }
    }

    fun reset(now: Long = System.currentTimeMillis()) = synchronized(lock) {
        val s = accrue(stateInternal(), now)
        saveState(s.copy(running = false, remainingAtStartMs = config().duration(s.phase),
            startEpochMs = 0, deadlineEpochMs = 0, accruedMs = 0))
    }

    /** Skip intentionally does not award a completed focus session. */
    fun skip(now: Long = System.currentTimeMillis()) = synchronized(lock) {
        val s = accrue(stateInternal(), now)
        val next = TimerLogic.nextPhase(s.phase, s.completedFocusInCycle, config().focusRounds)
        saveState(s.copy(phase = next, running = false,
            remainingAtStartMs = config().duration(next), startEpochMs = 0,
            deadlineEpochMs = 0, accruedMs = 0,
            completedFocusInCycle = if (s.phase == Phase.LONG_BREAK) 0 else s.completedFocusInCycle))
    }

    fun setPhase(phase: Phase, now: Long = System.currentTimeMillis()) = synchronized(lock) {
        val s = accrue(stateInternal(), now)
        saveState(s.copy(phase = phase, running = false,
            remainingAtStartMs = config().duration(phase), startEpochMs = 0,
            deadlineEpochMs = 0, accruedMs = 0))
    }

    fun topics(): List<String> = synchronized(lock) {
        val saved = prefs.getString("topics", "General|Study|Work|Reading").orEmpty()
        saved.split("|").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    fun addTopic(topic: String): Boolean = synchronized(lock) {
        val cleaned = topic.trim().take(24).replace("|", "")
        if (cleaned.isBlank() || topics().any { it.equals(cleaned, ignoreCase = true) })
            return@synchronized false
        prefs.edit().putString("topics", (topics() + cleaned).joinToString("|")).commit()
        true
    }

    fun selectTopic(topic: String, now: Long = System.currentTimeMillis()) = synchronized(lock) {
        if (!topics().contains(topic)) return@synchronized
        val s = accrue(stateInternal(), now)
        // preserve running timer while assigning any future focus to new category
        val elapsedThisRun = if (s.running) (now - s.startEpochMs).coerceIn(0L, s.remainingAtStartMs) else 0
        saveState(s.copy(topic = topic, accruedMs = elapsedThisRun))
    }

    fun clearHistory() = synchronized(lock) {
        prefs.edit().putString("stats", "[]").commit()
    }
}
