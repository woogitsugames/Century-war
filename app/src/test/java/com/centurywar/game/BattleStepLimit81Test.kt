package com.centurywar.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleStepLimit81Test {
    @Test fun endlessEndsAtReplayLimitAndRecordsFinalWave() {
        val cfg = BattleConfig(Mode.ENDLESS, 1001L, 1f)
        val battle = Battle(cfg)
        val controller = BattleController(battle)
        battle.playerAi = null
        battle.stepCount = Replay.MAX_STEPS - 1
        battle.time = battle.stepCount * Battle.STEP
        battle.wave = 41
        battle.waveTimer = 30f
        assertFalse(battle.over)

        battle.step()

        assertTrue(battle.over)
        assertEquals(1, battle.winner)
        assertEquals(Replay.MAX_STEPS, battle.stepCount)
        assertEquals(41, battle.wave)
        assertTrue(battle.score() >= 41L * 250L)
        val log = controller.log()
        assertEquals(Replay.MAX_STEPS, log.steps)
        assertEquals(log, Replay.decode(Replay.encode(log)))
        battle.step()
        assertEquals(Replay.MAX_STEPS, battle.stepCount)
    }

    @Test fun endlessContinuesAfterOldServerStepCap() {
        val battle = Battle(BattleConfig(Mode.ENDLESS, 1002L, 1f))
        battle.stepCount = 216_000
        battle.time = battle.stepCount * Battle.STEP
        battle.waveTimer = 30f
        battle.step()
        assertEquals(216_001, battle.stepCount)
        assertFalse(battle.over)
    }

    @Test fun formatRejectsBeyondSharedLimit() {
        val max = Replay.Log(Replay.MAX_STEPS, emptyList())
        assertEquals(max, Replay.decode(Replay.encode(max)))
        val tooLong = Replay.Log(Replay.MAX_STEPS + 1, emptyList())
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            Replay.encode(tooLong)
        }
    }
}
