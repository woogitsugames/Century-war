package com.centurywar.game

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayPlayerTest {
    /** Wszystkie akcje gracza przechodzą przez kontroler również w generatorze testów. */
    private fun record(cfg: BattleConfig, limit: Int = 2_100, playerAi: Boolean = false): Pair<Battle, Replay.Log> {
        val b = Battle(cfg)
        val c = BattleController(b)
        if (playerAi) b.playerAi = Ai(b, 0)
        else c.queue(Role.MELEE)
        var n = 0
        while (!b.over && n < limit) {
            if (b.pendingChoice != null) assertTrue(c.doctrine(0))
            if (b.pendingCards != null) assertTrue(c.card(0))
            b.step()
            b.fx.count = 0
            n++
        }
        if (!b.over) assertTrue(c.surrender())
        assertTrue(b.over)
        return b to c.log()
    }

    private fun compare(cfg: BattleConfig, playerAi: Boolean = false, limit: Int = 2_100,
        required: Replay.Kind? = null) {
        val (original, log) = record(cfg, limit = limit, playerAi = playerAi)
        if (required != null) assertTrue("Brakuje wymaganej komendy $required",
            log.commands.any { it.kind == required })
        val expected = Replay.verify(cfg, log)
        val p = ReplayPlayer(cfg, log)
        var limit = log.steps + 2
        while (!p.broken && !p.finished && limit-- > 0) p.advance(1)
        assertFalse("Odtwarzanie nie może się urwać", p.broken)
        assertTrue(p.finished)
        assertEquals(expected, p.result())
        assertEquals(original.score(), p.result()!!.score)
        assertEquals(original.winner, p.result()!!.winner)
    }

    @Test fun tutorialCampaignReplaysExactly() {
        compare(Campaign.config(Campaign.stages[0], Bonuses(baseHpMul = 3f),
            seed = 7L, tutorial = true))
    }

    @Test fun endlessCardsReplaysExactly() {
        compare(BattleConfig(Mode.ENDLESS, 7L, 1f,
            playerBonuses = Bonuses(baseHpMul = 6f)), playerAi = true,
            limit = 60 * 240, required = Replay.Kind.CARD)
    }

    @Test fun dailyChallengeReplaysExactly() {
        compare(Daily.config(20_000L, Bonuses()), playerAi = true)
    }

    @Test fun seekForwardBackwardMatchesLinearSnapshot() {
        val cfg = BattleConfig(Mode.SKIRMISH, 238L, aiPower = 0.12f,
            playerBonuses = Bonuses(baseHpMul = 30f, startGold = 500f))
        val (_, log) = record(cfg, limit = 3_500)
        assertTrue("Test musi przejść przez punkt kontrolny", log.steps > 2_000)
        val normal = ReplayPlayer(cfg, log)
        val jumping = ReplayPlayer(cfg, log)
        assertTrue(jumping.seek(2_200))
        assertEquals(2_200, normal.advance(2_200))
        assertArrayEquals(BattleSnapshot.encode(normal.battle), BattleSnapshot.encode(jumping.battle))
        assertTrue(jumping.seek(240))
        val at240 = ReplayPlayer(cfg, log)
        at240.advance(240)
        assertArrayEquals(BattleSnapshot.encode(at240.battle), BattleSnapshot.encode(jumping.battle))
        assertTrue(jumping.seek(3_000))
        val at3000 = ReplayPlayer(cfg, log)
        at3000.advance(3_000)
        assertArrayEquals(BattleSnapshot.encode(at3000.battle), BattleSnapshot.encode(jumping.battle))
        assertTrue(jumping.seek(log.steps))
        assertEquals(Replay.verify(cfg, log), jumping.result())
    }

    @Test fun seekCanRunInSmallBudgetsAndRejectsIllegalCommands() {
        val cfg = BattleConfig(Mode.SKIRMISH, 8L, 1f,
            playerBonuses = Bonuses(baseHpMul = 10f))
        val (_, log) = record(cfg, 1_000)
        val p = ReplayPlayer(cfg, log)
        p.requestSeek(minOf(600, log.steps))
        var slices = 0
        while (p.seeking && slices < 1_000) {
            p.seekStep(24_000)
            slices++
        }
        assertFalse(p.seeking)
        assertEquals(minOf(600, log.steps), p.step)

        val bad = ReplayPlayer(cfg, Replay.Log(2,
            listOf(Replay.Command(0, Replay.Kind.CANCEL))))
        bad.advance(2)
        assertTrue(bad.broken)
    }

    @Test fun resumedBattlePreservesOneContinuousCommandLog() {
        val cfg = BattleConfig(Mode.SKIRMISH, 91L, 1f)
        val battle = Battle(cfg)
        val ctrl = BattleController(battle)
        assertTrue(ctrl.queue(Role.MELEE))
        repeat(90) { if (!battle.over) battle.step() }
        val restored = BattleSnapshot.decode(BattleSnapshot.encode(battle))
        assertNotNull(restored)
        val resumed = restored!!.battle
        val c = resumed.commandController!!
        assertEquals(ctrl.log(), c.log())
        if (!resumed.over) {
            c.stance(true)
            repeat(45) { if (!resumed.over) resumed.step() }
            if (!resumed.over) c.surrender()
        }
        val log = c.log()
        assertTrue(log.commands.first().kind == Replay.Kind.QUEUE)
        assertEquals(resumed.score(), Replay.verify(cfg, log).score)
        val player = ReplayPlayer(cfg, log)
        assertTrue(player.seek(log.steps))
        assertEquals(Replay.verify(cfg, log), player.result())
    }
}
