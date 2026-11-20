import dev.tomatowear.app.Phase
import dev.tomatowear.app.TimerConfig
import dev.tomatowear.app.TimerLogic

fun main() {
    check(TimerLogic.nextPhase(Phase.FOCUS, 1, 4) == Phase.SHORT_BREAK)
    check(TimerLogic.nextPhase(Phase.FOCUS, 4, 4) == Phase.LONG_BREAK)
    check(TimerLogic.nextPhase(Phase.FOCUS, 8, 4) == Phase.LONG_BREAK)
    check(TimerLogic.nextPhase(Phase.LONG_BREAK, 4, 4) == Phase.FOCUS)
    check(TimerLogic.nextPhase(Phase.SHORT_BREAK, 1, 4) == Phase.FOCUS)
    check(TimerLogic.remainingAt(10_000, 1_000, 5_000) == 0L)
    check(TimerLogic.remainingAt(2_000, 1_000, 5_000) == 4_000L)
    check(TimerLogic.clock(1) == "00:01")
    check(TimerLogic.clock(25 * 60_000L) == "25:00")
    check(TimerConfig().duration(Phase.FOCUS) == 1_500_000L)
    println("All 10 timer-logic checks passed")
}
