package com.centurywar.screens

import com.centurywar.engine.HapticKind
import com.centurywar.game.FxBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BattleHapticsTest {
    @Test
    fun recognizesOnlyPlayerActionsAndEnemyBoss() {
        assertEquals(HapticKind.BARRAGE, BattleHaptics.kindForFx(FxBuffer.BARRAGE, 0, 0f))
        assertNull(BattleHaptics.kindForFx(FxBuffer.BARRAGE, 1, 0f))
        assertEquals(HapticKind.EVOLVE, BattleHaptics.kindForFx(FxBuffer.EVOLVE, 0, 0f))
        assertNull(BattleHaptics.kindForFx(FxBuffer.EVOLVE, 0, 1f)) // awans rangi
        assertEquals(HapticKind.BOSS, BattleHaptics.kindForFx(FxBuffer.EVOLVE, 1, 2f))
        assertNull(BattleHaptics.kindForFx(FxBuffer.BASE_HIT, 1, 0f))
    }
}
