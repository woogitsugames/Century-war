package com.centurywar.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayTest {
    private fun mustReject(block: () -> Unit) {
        var rejected = false
        try {
            block()
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue("Zmodyfikowany zapis musi zostać odrzucony", rejected)
    }

    @Test
    fun endlessCardChoicesAreLoggedAndReplayed() {
        val cfg = BattleConfig(Mode.ENDLESS, 7L, 1f)
        val battle = Battle(cfg)
        val controller = BattleController(battle)
        battle.playerAi = Ai(battle, 0)
        var iterations = 0
        while (!battle.over && iterations < 60 * 60 * 60) {
            battle.step()
            battle.fx.count = 0
            iterations++
        }
        assertTrue(battle.over)
        val log = controller.log()
        assertTrue("Karty Endless muszą być w dzienniku", log.commands.any { it.kind == Replay.Kind.CARD })
        val result = Replay.verify(cfg, Replay.encode(log))
        assertEquals(battle.score(), result.score)
        assertEquals(battle.wave, result.wave)
        assertEquals(battle.stepCount, result.steps)
    }

    @Test
    fun aimedBarrageAndStanceOfPersonalityAiReplayExactly() {
        val cfg = BattleConfig(Mode.SKIRMISH, 4455L, 1.2f, startEra = 1, maxEra = 2)
        val battle = Battle(cfg)
        val controller = BattleController(battle)
        battle.playerAi = Ai(battle, 0, AiPersonality.SIEGER)
        var iterations = 0
        while (!battle.over && iterations < 30 * 60 * 60) {
            battle.step()
            battle.fx.count = 0
            iterations++
        }
        assertTrue(battle.over)
        val log = controller.log()
        assertTrue("Oblężnik musi oddać celowany ostrzał",
            log.commands.any { it.kind == Replay.Kind.BARRAGE_AT })
        val result = Replay.verify(cfg, Replay.encode(log))
        assertEquals(battle.score(), result.score)
        assertEquals(battle.winner, result.winner)
        assertEquals(battle.stepCount, result.steps)
        assertEquals(battle.player.kills, result.kills)
    }

    @Test
    fun playerAiCommandsReplayExactly() {
        val cfg = BattleConfig(
            mode = Mode.SKIRMISH,
            seed = 112233L,
            aiPower = 0.76f,
            startEra = 0,
            maxEra = 1,
            playerBonuses = Bonuses(
                startGold = 450f, incomeMul = 2.5f, unitHpMul = 2.4f,
                unitDmgMul = 3.5f, xpMul = 30f, trainMul = 1.5f,
                popCap = 18,
            ),
        )
        val battle = Battle(cfg)
        val controller = BattleController(battle)
        battle.playerAi = Ai(battle, 0)
        var iterations = 0
        while (!battle.over && iterations < 72_000) {
            battle.step()
            battle.fx.count = 0
            iterations++
        }
        assertTrue("Bitwa testowa musi się zakończyć", battle.over)
        assertEquals(iterations, battle.stepCount)

        val log = controller.log()
        assertTrue("AI musi wydać rozkazy", log.commands.any { it.kind == Replay.Kind.QUEUE })
        assertTrue("AI musi awansować epokę", log.commands.any { it.kind == Replay.Kind.EVOLVE })
        assertTrue("Wybór doktryny musi być w dzienniku", log.commands.any { it.kind == Replay.Kind.DOCTRINE })

        val serialized = Replay.encode(log)
        val decoded = Replay.decode(serialized)
        assertEquals(log, decoded)
        val result = Replay.verify(cfg, serialized)
        assertEquals(battle.score(), result.score)
        assertEquals(battle.winner, result.winner)
        assertEquals(battle.stepCount, result.steps)
        assertEquals(battle.player.kills, result.kills)
        assertEquals(battle.enemy.kills, result.enemyKills)
        assertEquals(battle.time, result.time, 0f)
        assertEquals(battle.wave, result.wave)
    }

    @Test
    fun controllerRecordsOnlySuccessfulActions() {
        val battle = Battle(BattleConfig(Mode.SKIRMISH, 55L, 1f))
        val controller = BattleController(battle)
        assertTrue(controller.queue(Role.MELEE))
        assertTrue(controller.cancel())
        assertFalse(controller.cancel())
        assertTrue(controller.stance(true))
        assertTrue(controller.buyTurret(0))
        assertFalse(controller.buyTurret(0))
        assertFalse(controller.sellTurret(-1))
        assertFalse(controller.upgradeTurret(99))
        assertFalse(controller.doctrine(0))
        battle.step()
        val commands = controller.log().commands
        assertEquals(
            listOf(Replay.Kind.QUEUE, Replay.Kind.CANCEL, Replay.Kind.STANCE, Replay.Kind.BUY_TURRET),
            commands.map { it.kind },
        )
        assertTrue(commands.all { it.step == 0 })
        assertTrue(battle.player.hold)
    }

    @Test
    fun doctrineIsLoggedInSameStepAsEvolve() {
        val battle = Battle(BattleConfig(Mode.SKIRMISH, 77L, 1f, maxEra = 1))
        val controller = BattleController(battle)
        battle.player.xp = Defs.evolveXp[0].toFloat()
        assertTrue(controller.evolve())
        assertNotNull(battle.pendingChoice)
        assertFalse(controller.doctrine(7))
        assertTrue(controller.doctrine(1))
        assertNull(battle.pendingChoice)
        battle.step()
        val commands = controller.log().commands
        assertEquals(listOf(Replay.Kind.EVOLVE, Replay.Kind.DOCTRINE), commands.map { it.kind })
        assertEquals(listOf(0, 0), commands.map { it.step })
    }

    @Test
    fun futureAndOutOfOrderCommandsAreRejected() {
        val cfg = BattleConfig(Mode.SKIRMISH, 42L, 1f)
        mustReject {
            Replay.verify(cfg, Replay.Log(2, listOf(Replay.Command(2, Replay.Kind.QUEUE, 0))))
        }
        mustReject {
            Replay.encode(Replay.Log(4, listOf(
                Replay.Command(3, Replay.Kind.QUEUE, 0),
                Replay.Command(1, Replay.Kind.CANCEL),
            )))
        }
    }

    @Test
    fun illegalCommandsAndArgumentsAreRejected() {
        val cfg = BattleConfig(Mode.SKIRMISH, 42L, 1f)
        mustReject {
            Replay.verify(cfg, Replay.Log(1, listOf(Replay.Command(0, Replay.Kind.CANCEL))))
        }
        mustReject {
            Replay.verify(cfg, Replay.Log(1, listOf(Replay.Command(0, Replay.Kind.DOCTRINE, 0))))
        }
        mustReject {
            Replay.verify(cfg, Replay.Log(1, listOf(Replay.Command(0, Replay.Kind.UPGRADE_TURRET, 0))))
        }
        mustReject {
            Replay.encode(Replay.Log(1, listOf(Replay.Command(0, Replay.Kind.QUEUE, 99))))
        }
        mustReject {
            Replay.encode(Replay.Log(1, listOf(Replay.Command(0, Replay.Kind.STANCE, 2))))
        }
    }

    @Test
    fun incompleteAndCorruptedLogsAreRejected() {
        val cfg = BattleConfig(Mode.SKIRMISH, 123L, 1f)
        mustReject { Replay.verify(cfg, Replay.Log(1, emptyList())) }
        mustReject { Replay.decode("!!!") }
        mustReject { Replay.decode("AQEA") } // wersja formatu 1
        val valid = Replay.encode(Replay.Log(2, listOf(Replay.Command(0, Replay.Kind.STANCE, 1))))
        mustReject { Replay.decode(valid + "!!") }
    }
    @Test
    fun surrenderIsReplayedWithoutAdvancingTime() {
        val cfg = BattleConfig(Mode.SKIRMISH, 2026L, 1f)
        val battle = Battle(cfg)
        val controller = BattleController(battle)
        assertTrue(controller.queue(Role.MELEE))
        repeat(20) { battle.step() }
        val originalStep = battle.stepCount
        assertTrue(controller.surrender())
        assertEquals(originalStep, battle.stepCount)
        val replayed = Replay.verify(cfg, Replay.decode(Replay.encode(controller.log())))
        assertEquals(battle.score(), replayed.score)
        assertEquals(1, replayed.winner)
        assertEquals(originalStep, replayed.steps)
    }

    @Test
    fun tutorialSupportIsLoggedAndBlockedElsewhere() {
        val cfg = BattleConfig(Mode.CAMPAIGN, 1L, 0.8f, stageIndex = 0, tutorial = true)
        val battle = Battle(cfg)
        val controller = BattleController(battle)
        assertTrue(controller.tutorialGold(Defs.unit(0, Role.MELEE).cost).not())
        battle.player.gold = 0f
        assertTrue(controller.tutorialGold(Defs.unit(0, Role.MELEE).cost))
        assertEquals(Replay.Kind.TUTORIAL_GOLD, controller.log().commands.last().kind)

        val regular = Battle(BattleConfig(Mode.SKIRMISH, 1L, 0.8f))
        val regularController = BattleController(regular)
        assertFalse(regularController.tutorialGold(Defs.unit(0, Role.MELEE).cost))
        mustReject {
            Replay.verify(regular.cfg, Replay.Log(0, listOf(Replay.Command(0, Replay.Kind.TUTORIAL_GOLD, 15))))
        }
    }

    @Test
    fun illegalCommandsAtFinishedStepAreRejected() {
        val cfg = BattleConfig(Mode.SKIRMISH, 15L, 1f)
        mustReject {
            Replay.verify(cfg, Replay.Log(0, listOf(Replay.Command(0, Replay.Kind.QUEUE, 0))))
        }
        mustReject {
            Replay.verify(cfg, Replay.Log(3, listOf(
                Replay.Command(3, Replay.Kind.SURRENDER),
                Replay.Command(3, Replay.Kind.QUEUE, 0),
            )))
        }
    }
}
