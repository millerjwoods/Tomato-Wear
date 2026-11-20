package dev.tomatowear.app

import org.junit.Assert.assertEquals
import org.junit.Test

class TimerLogicTest {
    @Test fun focusTurnsIntoShortBreak() =
        assertEquals(Phase.SHORT_BREAK, TimerLogic.nextPhase(Phase.FOCUS, 1, 4))

    @Test fun fourthFocusTurnsIntoLongBreak() =
        assertEquals(Phase.LONG_BREAK, TimerLogic.nextPhase(Phase.FOCUS, 4, 4))

    @Test fun breaksReturnToFocus() {
        assertEquals(Phase.FOCUS, TimerLogic.nextPhase(Phase.SHORT_BREAK, 1, 4))
        assertEquals(Phase.FOCUS, TimerLogic.nextPhase(Phase.LONG_BREAK, 4, 4))
    }

    @Test fun remainingNeverGoesNegative() {
        assertEquals(0L, TimerLogic.remainingAt(12000, 1000, 10000))
        assertEquals(3000L, TimerLogic.remainingAt(8000, 1000, 10000))
    }

    @Test fun timerFormattingRoundsUpAndUsesTwoDigits() {
        assertEquals("25:00", TimerLogic.clock(1500000))
        assertEquals("00:01", TimerLogic.clock(1))
        assertEquals("00:00", TimerLogic.clock(0))
    }
}
