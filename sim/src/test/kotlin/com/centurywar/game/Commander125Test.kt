package com.centurywar.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regresja dowódcy: nie może zmienić wyników starszych powtórek. */
class Commander125Test {
    private fun config(mode: Mode = Mode.SKIRMISH, stage: Int = -1, seed: Long = 125L) =
        BattleConfig(mode, seed, 1f, stageIndex = stage,
            playerBonuses = Bonuses(startGold = 2000f))

    @Test fun unlocksOnlyAtConfiguredMilestones() {
        assertFalse(Battle(config(Mode.CAMPAIGN, 6)).commanderAvailable)
        assertTrue(Battle(config(Mode.CAMPAIGN, 7)).commanderAvailable)
        assertTrue(Battle(config(Mode.SKIRMISH)).commanderAvailable)
        assertTrue(Battle(config(Mode.ENDLESS)).commanderAvailable)
        assertFalse(Battle(Daily.config(Daily.RELEASE_DAY - 1, Bonuses())).commanderAvailable)
        assertTrue(Battle(Daily.config(Daily.RELEASE_DAY, Bonuses())).commanderAvailable)
        assertFalse(Battle(config(), commanderEnabled = false).commanderAvailable)
    }

    @Test fun purchaseIsUniqueAndChargedWithoutChangingUnitCosts() {
        val battle = Battle(config())
        val controller = BattleController(battle)
        val unitCost = battle.player.unit(Role.HEAVY).cost
        val before = battle.player.gold
        assertTrue(controller.summonCommander())
        assertEquals(before - unitCost * 2, battle.player.gold)
        assertEquals(unitCost, battle.player.unit(Role.HEAVY).cost)
        assertEquals(-1f, battle.player.commanderCd, 0f)
        val unit = battle.units.single { it.isCommander && it.alive }
        assertEquals(unit.def.hp * battle.player.hpMul * 1.8f, unit.maxHp, 0.01f)
        assertEquals(1, battle.player.aliveCount)
        assertFalse(controller.summonCommander())
        assertEquals(1, controller.log().commands.count { it.kind == Replay.Kind.SUMMON_COMMANDER })
    }

    @Test fun noGoldNoPopulationNoSummon() {
        val b = Battle(config())
        val controller = BattleController(b)
        b.player.gold = 0f
        assertFalse(controller.summonCommander())
        b.player.gold = 10000f
        b.player.cap = 0
        assertFalse(controller.summonCommander())
        assertTrue(controller.log().commands.isEmpty())
    }

    @Test fun pulseAndCooldownArePartOfSimulation() {
        val b = Battle(config())
        val c = BattleController(b)
        assertTrue(c.summonCommander())
        val commander = b.units.first { it.isCommander }
        assertTrue(b.spawn(b.enemy, b.enemy.unit(Role.MELEE)))
        val enemy = b.units.first { it.alive && it.side == 1 }
        commander.x = b.player.baseFront + 500f
        enemy.x = commander.x + 100f
        enemy.hp = 100000f
        enemy.maxHp = 100000f
        val hp = enemy.hp
        b.step()
        assertTrue(enemy.hp < hp)
        assertTrue(commander.commanderSkillCd > 0f)
        assertTrue(commander.commanderSkillCd <= Battle.COMMANDER_SKILL_COOLDOWN)
        val firstCd = commander.commanderSkillCd
        b.step()
        assertTrue(commander.commanderSkillCd < firstCd)
    }

    @Test fun auraIncreasesOnlyNearbyAlliedDamage() {
        val boosted = Battle(config())
        val controller = BattleController(boosted)
        assertTrue(controller.summonCommander())
        assertTrue(boosted.spawn(boosted.player, boosted.player.unit(Role.MELEE)))
        assertTrue(boosted.spawn(boosted.enemy, boosted.enemy.unit(Role.MELEE)))
        val leader = boosted.units.first { it.alive && it.isCommander }
        val ally = boosted.units.first { it.alive && it.side == 0 && !it.isCommander }
        val foe = boosted.units.first { it.alive && it.side == 1 }
        leader.x = 420f
        leader.cd = 100f
        leader.commanderSkillCd = Battle.COMMANDER_SKILL_COOLDOWN
        ally.x = 450f
        ally.cd = 0f
        ally.dmg = 100f
        foe.x = 465f
        foe.hp = 10000f
        foe.maxHp = 10000f
        foe.cd = 100f
        val plain = BattleSnapshot.decode(BattleSnapshot.encode(boosted))!!.battle
        plain.units.first { it.isCommander }.isCommander = false
        plain.player.commanderCd = 0f
        boosted.step()
        plain.step()
        val dmgAura = 10000f - boosted.units.first { it.side == 1 && it.alive }.hp
        val dmgPlain = 10000f - plain.units.first { it.side == 1 && it.alive }.hp
        assertEquals(100f, dmgPlain, 0.02f)
        assertEquals(110f, dmgAura, 0.02f)
    }

    @Test fun deathStartsCooldownAndAllowsOneNewPurchase() {
        val b = Battle(config())
        val ctl = BattleController(b)
        assertTrue(ctl.summonCommander())
        val leader = b.units.first { it.isCommander && it.alive }
        assertTrue(b.spawn(b.enemy, b.enemy.unit(Role.MELEE)))
        val foe = b.units.first { it.alive && it.side == 1 }
        leader.x = b.player.baseFront + 480f
        foe.x = leader.x + 20f
        leader.hp = 1f
        leader.cd = 100f
        foe.dmg = 10000f
        foe.hp = 100000f
        foe.maxHp = 100000f
        foe.cd = 0f
        b.step()
        assertFalse(leader.alive)
        assertEquals(Battle.COMMANDER_COOLDOWN, b.player.commanderCd, 0.05f)
        assertFalse(ctl.summonCommander())
        foe.cd = 10000f
        repeat((Battle.COMMANDER_COOLDOWN * 60).toInt() + 2) {
            if (!b.over) b.step()
        }
        if (!b.over) {
            assertEquals(0f, b.player.commanderCd, 0f)
            b.player.gold = 10000f
            assertTrue(ctl.summonCommander())
            assertEquals(1, b.units.count { it.alive && it.isCommander && it.side == 0 })
        }
    }

    @Test fun replayV7AcceptsCommanderAndOlderVersionsRejectIt() {
        val cfg = config()
        val b = Battle(cfg)
        val c = BattleController(b)
        assertTrue(c.summonCommander())
        assertTrue(c.surrender())
        val encoded = Replay.encode(c.log())
        assertEquals(Replay.VERSION, Replay.decode(encoded).version)
        assertEquals(b.score(), Replay.verify(cfg, encoded).score)
        val older = c.log().copy(version = 6)
        var rejected = false
        try { Replay.encode(older) } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        val legacy = Replay.Log(0, listOf(Replay.Command(0, Replay.Kind.SURRENDER)), version = 6)
        assertEquals(0, Replay.verify(cfg, Replay.encode(legacy)).steps)
        val legacyPlayer = ReplayPlayer(cfg, legacy)
        assertFalse(legacyPlayer.battle.commanderAvailable)
    }

    @Test fun aiSpendsGoldAndFollowsIdenticalSummonRule() {
        val b = Battle(config())
        b.enemy.gold = 10_000f
        assertTrue(b.spawn(b.enemy, b.enemy.unit(Role.MELEE)))
        assertTrue(b.spawn(b.enemy, b.enemy.unit(Role.MELEE)))
        val before = b.enemy.gold
        b.ai!!.update(1f)
        assertTrue(b.units.any { it.alive && it.isCommander && it.side == 1 })
        assertTrue(before - b.enemy.gold >= b.commanderCost(b.enemy))
        assertFalse(b.summonCommander(b.enemy))
    }

    @Test fun snapshotResumesCommanderPulseAndDeathCooldown() {
        val a = Battle(config())
        val commands = BattleController(a)
        assertTrue(commands.summonCommander())
        val fighter = a.units.first { it.isCommander && it.alive }
        fighter.commanderSkillCd = 13.5f
        a.enemy.commanderCd = 31.25f
        repeat(180) { a.step(); a.fx.count = 0 }
        val bytes = BattleSnapshot.encode(a)
        val restored = BattleSnapshot.decode(bytes)
        assertNotNull(restored)
        val b = restored!!.battle
        assertEquals(-1f, b.player.commanderCd, 0f)
        assertEquals(a.enemy.commanderCd, b.enemy.commanderCd, 0f)
        assertEquals(fighter.commanderSkillCd, b.units.first { it.isCommander }.commanderSkillCd, 0f)
        assertTrue(bytes.contentEquals(BattleSnapshot.encode(b)))
        repeat(240) {
            if (!a.over) a.step()
            if (!b.over) b.step()
            a.fx.count = 0
            b.fx.count = 0
        }
        assertTrue(BattleSnapshot.encode(a).contentEquals(BattleSnapshot.encode(b)))
    }
}
