package com.centurywar.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticGateTest {
    @Test
    fun limitsSameAndLowerPriorityUntil120Ms() {
        val gate = HapticGate()
        assertTrue(gate.allow(HapticKind.TAP, 0))
        assertFalse(gate.allow(HapticKind.TAP, 119))
        assertTrue(gate.allow(HapticKind.TAP, 120))
        assertFalse(gate.allow(HapticKind.TAP, 239))
        assertTrue(gate.allow(HapticKind.TAP, 240))
    }

    @Test
    fun importantEventPreemptsAndRestartsWindow() {
        val gate = HapticGate()
        assertTrue(gate.allow(HapticKind.TAP, 1000))
        assertTrue(gate.allow(HapticKind.BARRAGE, 1020))
        assertFalse(gate.allow(HapticKind.TAP, 1100))
        assertTrue(gate.allow(HapticKind.BOSS, 1110))
        assertFalse(gate.allow(HapticKind.HEAVY_HIT, 1150))
        assertTrue(gate.allow(HapticKind.TAP, 1230))
    }

    @Test
    fun tiedPriorityCannotPreemptAndResetAllowsImmediatePulse() {
        val gate = HapticGate()
        assertTrue(gate.allow(HapticKind.VICTORY, 50))
        assertFalse(gate.allow(HapticKind.DEFEAT, 60))
        gate.reset()
        assertTrue(gate.allow(HapticKind.DEFEAT, 60))
        assertTrue(gate.allow(HapticKind.TAP, 1)) // restart zegara
    }

    @Test
    fun allBattleSignalsOutrankButtonTap() {
        for (kind in HapticKind.entries) {
            if (kind == HapticKind.TAP) continue
            assertTrue(kind.priority > HapticKind.TAP.priority)
        }
    }
}
