package com.centurywar.screens

import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.BattleController
import com.centurywar.game.Mode
import com.centurywar.game.Replay
import com.centurywar.game.ReplayPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleSummaryTest {
    private fun battle(seed: Long = 104L) = Battle(BattleConfig(Mode.SKIRMISH, seed, 1f))

    @Test
    fun samplesEverySecondAndFinalPartialSecond() {
        val battle = battle()
        val samples = BattleSummarySamples()
        samples.capture(battle)
        repeat(185) {
            battle.step()
            samples.capture(battle)
        }
        assertEquals(4, samples.count)
        assertEquals(0, samples.stepAt(0))
        assertEquals(60, samples.stepAt(1))
        assertEquals(120, samples.stepAt(2))
        assertEquals(180, samples.stepAt(3))
        assertTrue(samples.valueAt(2, 0, 3) >= 0)
        samples.finish(battle)
        assertEquals(5, samples.count)
        assertEquals(185, samples.stepAt(4))
        assertEquals(battle.player.aliveCount, samples.valueAt(0, 0, 4))
        assertEquals(battle.enemy.aliveCount, samples.valueAt(0, 1, 4))
        assertEquals(battle.player.baseHp.toInt().coerceAtLeast(0), samples.valueAt(1, 0, 4))
        assertEquals(battle.enemy.baseHp.toInt().coerceAtLeast(0), samples.valueAt(1, 1, 4))
        assertEquals(battle.player.gold.toInt().coerceAtLeast(0), samples.valueAt(2, 0, 4))
        assertEquals(battle.enemy.gold.toInt().coerceAtLeast(0), samples.valueAt(2, 1, 4))
    }

    @Test
    fun evolutionMarkersCaptureBothSidesWithoutDuplicateSamples() {
        val battle = battle()
        val samples = BattleSummarySamples()
        samples.capture(battle)
        battle.player.era++
        battle.enemy.era++
        samples.capture(battle)
        samples.capture(battle)
        assertEquals(1, samples.count)
        assertEquals(2, samples.evolutionCount)
        assertEquals(0, samples.evolutionStep(0))
        assertEquals(0, samples.evolutionSide(0))
        assertEquals(1, samples.evolutionSide(1))
    }

    @Test
    fun liveAndReplayProduceIdenticalSampleSeries() {
        val cfg = BattleConfig(Mode.SKIRMISH, 9104L, 1f)
        val battle = Battle(cfg)
        val controller = BattleController(battle)
        val live = BattleSummarySamples()
        live.capture(battle)
        repeat(180) {
            battle.step()
            live.capture(battle)
        }
        assertTrue(controller.surrender())
        live.finish(battle)

        val replay = ReplayPlayer(cfg, controller.log())
        assertFalse(replay.broken)
        val recreated = BattleSummarySamples()
        recreated.capture(replay.battle)
        var iterations = 0
        while (!replay.finished && !replay.broken && iterations < 400) {
            replay.advance(1)
            recreated.capture(replay.battle)
            iterations++
        }
        assertTrue("Powtórka nie zakończyła się poprawnie", replay.finished && !replay.broken)
        recreated.finish(replay.battle)
        assertEquals(live.count, recreated.count)
        assertEquals(live.evolutionCount, recreated.evolutionCount)
        for (i in 0 until live.count) {
            assertEquals(live.stepAt(i), recreated.stepAt(i))
            for (metric in 0..2) for (side in 0..1) {
                assertEquals("Próbka $i, metryka $metric, strona $side",
                    live.valueAt(metric, side, i), recreated.valueAt(metric, side, i))
            }
        }
    }

    @Test
    fun bufferIsBoundedAfterMaximumReplayDuration() {
        val battle = battle()
        val samples = BattleSummarySamples()
        val seconds = Replay.MAX_STEPS / 60 + 10
        for (i in 0..seconds) {
            battle.stepCount = i * 60
            samples.capture(battle)
        }
        assertTrue(samples.count <= Replay.MAX_STEPS / 60 + 2)
        assertEquals(seconds * 60, samples.stepAt(samples.count - 1))
        assertTrue(samples.stepAt(0) > 0)
        assertTrue(samples.stepAt(samples.count - 1) > samples.stepAt(0))
    }

    /** write() w pętli bitwy nie formatuje napisów; robi to dopiero formatLabels(). */
    @Test
    fun labelsAreFormattedLazily() {
        val battle = battle()
        val samples = BattleSummarySamples()
        samples.capture(battle)
        repeat(185) {
            battle.step()
            samples.capture(battle)
        }
        samples.finish(battle)
        assertEquals("0:00", samples.lastTimeText)
        samples.formatLabels()
        assertEquals("0:00", samples.firstTimeText)
        assertEquals("0:03", samples.lastTimeText)
        assertEquals(samples.maxHp.toString(), samples.maxHpText)
        assertEquals(samples.valueAt(2, 0, samples.count - 1).toString(), samples.playerGoldText)
        val cached = samples.lastTimeText
        samples.formatLabels()
        assertTrue("Bez zmian próbek napis nie jest tworzony ponownie", cached === samples.lastTimeText)
    }
}
