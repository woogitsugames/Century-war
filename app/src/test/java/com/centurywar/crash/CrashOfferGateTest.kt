package com.centurywar.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R10-04 L1: pytanie o raport po odczycie w tle nigdy nie pojawia się nad bitwą. */
class CrashOfferGateTest {
    @Test fun outsideBattleShowsAtOnce() {
        val gate = CrashOfferGate()
        assertEquals("anr-1", gate.collected("anr-1", inBattle = false))
        assertNull(gate.takeAtMenu())
    }

    @Test fun inBattleWaitsForMenu() {
        val gate = CrashOfferGate()
        assertNull(gate.collected("anr-1", inBattle = true))
        assertTrue(gate.hasDeferred)
        assertEquals("anr-1", gate.takeAtMenu())
        assertFalse(gate.hasDeferred)
        assertNull("Tylko raz", gate.takeAtMenu())
    }

    @Test fun nothingCollectedOrAlreadyAskedAtStartup() {
        val gate = CrashOfferGate()
        assertNull(gate.collected(null, inBattle = false))
        gate.offeredAtStartup()
        assertNull("Jedno pytanie na uruchomienie", gate.collected("anr-2", inBattle = false))
        assertNull(gate.takeAtMenu())
    }
}
