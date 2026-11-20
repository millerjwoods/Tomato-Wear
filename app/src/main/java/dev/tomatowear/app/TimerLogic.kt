/*
 * Tomato Wear (Unofficial), 2026. A Wear OS adaptation inspired by Tomato by Nishant Mishra.
 * GPL-3.0-or-later. See LICENSE and README.md. This source is new implementation.
 */
package dev.tomatowear.app

enum class Phase(val title: String) {
    FOCUS("Focus"), SHORT_BREAK("Short break"), LONG_BREAK("Long break")
}

data class TimerConfig(
    val focusMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val focusRounds: Int = 4,
    val vibrate: Boolean = true,
    val sound: Boolean = true,
    val autoStart: Boolean = false
) {
    fun duration(phase: Phase): Long = 60_000L * when (phase) {
        Phase.FOCUS -> focusMinutes
        Phase.SHORT_BREAK -> shortBreakMinutes
        Phase.LONG_BREAK -> longBreakMinutes
    }
}

data class TimerState(
    val phase: Phase = Phase.FOCUS,
    val running: Boolean = false,
    val remainingAtStartMs: Long = 25 * 60_000L,
    val startEpochMs: Long = 0,
    val deadlineEpochMs: Long = 0,
    val accruedMs: Long = 0,
    val completedFocusInCycle: Int = 0,
    val topic: String = "General"
) {
    fun remaining(nowMs: Long): Long =
        if (running) (deadlineEpochMs - nowMs).coerceAtLeast(0L)
        else remainingAtStartMs.coerceAtLeast(0L)
}

object TimerLogic {
    fun remainingAt(nowMs: Long, startMs: Long, initialMs: Long): Long =
        (initialMs - (nowMs - startMs).coerceAtLeast(0L)).coerceAtLeast(0L)

    fun nextPhase(phase: Phase, focusesCompleted: Int, rounds: Int): Phase = when (phase) {
        Phase.FOCUS -> if (focusesCompleted > 0 && focusesCompleted % rounds.coerceAtLeast(1) == 0)
            Phase.LONG_BREAK else Phase.SHORT_BREAK
        Phase.SHORT_BREAK, Phase.LONG_BREAK -> Phase.FOCUS
    }

    fun clock(milliseconds: Long): String {
        val totalSec = (milliseconds.coerceAtLeast(0L) + 999L) / 1000L
        val min = totalSec / 60L
        val sec = totalSec % 60L
        return "%02d:%02d".format(min, sec)
    }
}
