package com.centurywar.game

import org.junit.Assert.*
import org.junit.Test

class TurretTierTest {
    @Test fun tierCostAndSnapshot() {
        val b = Battle(BattleConfig(Mode.SKIRMISH, 42L, 1f))
        val p = b.player
        p.gold = 100000f
        assertFalse(b.upgradeTurretTier(p, -1))
        assertFalse(b.upgradeTurretTier(p, Defs.MAX_TURRET_SLOTS))
        assertTrue(b.buyTurret(p, 0))
        val price = p.turretCost(p.turretEra[0], p.turretVariant[0])
        assertEquals((price * 0.6f).toInt().coerceAtLeast(1), b.turretTierCost(p, 0))
        assertTrue(b.upgradeTurretTier(p, 0))
        assertEquals((price * 0.9f).toInt().coerceAtLeast(1), b.turretTierCost(p, 0))
        assertTrue(b.upgradeTurretTier(p, 0))
        assertFalse(b.upgradeTurretTier(p, 0))
        val restored = BattleSnapshot.decode(BattleSnapshot.encode(b))!!.battle
        assertEquals(3, restored.player.turretTier[0])
        repeat(300) { if (!b.over && !restored.over) { b.step(); restored.step() } }
        assertArrayEquals(BattleSnapshot.encode(b), BattleSnapshot.encode(restored))
    }

    @Test fun thirdTierChangesProjectileAndCooldown() {
        val b = Battle(BattleConfig(Mode.SKIRMISH, 72L, 1f))
        val p = b.player
        p.gold = 100000f
        assertTrue(b.buyTurret(p, 0))
        assertTrue(b.upgradeTurretTier(p, 0))
        assertTrue(b.upgradeTurretTier(p, 0))
        assertTrue(b.spawn(b.enemy, b.enemy.unit(Role.MELEE)))
        val target = b.units.first { it.alive && it.side == 1 }
        target.x = p.baseFront + 300f
        target.prevX = target.x
        p.turretCd[0] = 0f
        b.step()
        val shot = b.projs.first { it.active && it.side == 0 }
        val def = Defs.turret(p.turretEra[0], p.turretVariant[0])
        assertEquals(def.dmg * p.turretDmgMul * 1.7f, shot.dmg, 0.001f)
        assertEquals(def.cooldown / (p.turretRateMul * 1.7f), p.turretCd[0], 0.001f)
        assertTrue(shot.splash >= 45f)
    }

    @Test fun eraUpgradePreservesTierAndSaleClearsIt() {
        val b = Battle(BattleConfig(Mode.SKIRMISH, 73L, 1f))
        val p = b.player
        p.gold = 100000f
        assertTrue(b.buyTurret(p, 0))
        assertTrue(b.upgradeTurretTier(p, 0))
        p.era = 1
        p.eraVariant = -1
        assertTrue(b.upgradeTurret(p, 0))
        assertEquals(2, p.turretTier[0])
        assertTrue(b.sellTurret(p, 0))
        assertEquals(0, p.turretTier[0])
    }

    @Test fun engineerAiInvestsInTurretTiers() {
        val b = Battle(BattleConfig(Mode.SKIRMISH, 71L, 1f, aiPersonality = AiPersonality.ENGINEER))
        b.enemy.gold = 10000f
        repeat(3000) { if (!b.over && b.enemy.turretTier.none { it > 1 }) b.step() }
        assertTrue(b.enemy.turretTier.any { it > 1 })
    }

    @Test fun tierCommandRoundTripsThroughReplay() {
        val cfg = BattleConfig(Mode.SKIRMISH, 42L, 1f)
        val b = Battle(cfg)
        val commands = BattleController(b)
        var steps = 0
        while (b.player.gold < b.player.turretCost() && !b.over && steps++ < 10000) b.step()
        assertTrue(commands.buyTurret(0))
        while (b.player.gold < b.turretTierCost(b.player, 0) && !b.over && steps++ < 10000) b.step()
        assertTrue(commands.upgradeTurretTier(0))
        assertTrue(commands.surrender())
        val log = commands.log()
        assertTrue(log.commands.any { it.kind == Replay.Kind.TURRET_TIER })
        assertEquals(b.score(), Replay.verify(cfg, Replay.encode(log)).score)
    }

    @Test fun oldVersionFiveReplayKeepsItsRecordedResult() {
        val cfg = Campaign.config(Campaign.stages[0], Bonuses(), 91001L)
        val replay = "Bf_gEhAAAAAAAwAZAAA3AAFpAADAAgAAiwMHVQAA1wEIaQAAoAEAAKABAACgAQAAIwABvQMAAIcBCw"
        val result = Replay.verify(cfg, replay)
        assertEquals(10L, result.score)
        assertEquals(1, result.winner)
        assertEquals(2400, result.steps)
        assertEquals(2, result.kills)
        assertEquals(7, result.enemyKills)
        val player = ReplayPlayer(cfg, Replay.decode(replay))
        player.advance(2000)
        assertTrue(player.seek(1800))
        player.advance(600)
        assertTrue(player.finished)
        assertEquals(10L, player.battle.score())
    }

    @Test fun oldReplayFormatRemainsRoundTrippable() {
        val log = Replay.Log(0, listOf(Replay.Command(0, Replay.Kind.SURRENDER)), 5)
        assertEquals(log, Replay.decode(Replay.encode(log)))
        val result = Replay.verify(BattleConfig(Mode.SKIRMISH, 42L, 1f), log)
        assertEquals(1, result.winner)
    }
}
