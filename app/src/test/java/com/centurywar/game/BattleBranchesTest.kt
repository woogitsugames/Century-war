package com.centurywar.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Testy rozgałęzień bez zależności od Androida. */
class BattleBranchesTest {
    @Test
    fun everyBranchHasOwnUnitsAndTurret() {
        val all = HashSet<Int>()
        for (era in intArrayOf(4, 6)) {
            val variants = Defs.eras[era].variants
            assertEquals(2, variants.size)
            for (variant in variants) {
                assertEquals(4, variant.units.size)
                assertNotEquals(Defs.eras[era].turret.nameKey, variant.turret.nameKey)
                assertEquals(era, variant.turret.era)
                for (u in variant.units) {
                    assertTrue(all.add(u.id))
                    assertEquals(u, Defs.allUnits[u.id])
                    assertEquals(variant.index, u.variant)
                    assertEquals(era, u.era)
                }
            }
        }
        assertEquals(16, all.size)
        assertTrue(Defs.unit(4, Role.HEAVY, 0).hp > Defs.unit(4, Role.HEAVY, 1).hp)
        assertTrue(Defs.unit(4, Role.MELEE, 0).speed < Defs.unit(4, Role.MELEE, 1).speed)
        assertTrue(Defs.unit(4, Role.RANGED, 1).chain > 0f)
        assertTrue(Defs.unit(6, Role.HEAVY, 1).regen > 0f)
        assertTrue(Defs.unit(6, Role.MELEE, 0).cost < Defs.unit(6, Role.MELEE, 1).cost)
    }

    @Test
    fun playerChoosesBranchBeforeDoctrine() {
        val b = Battle(BattleConfig(Mode.SKIRMISH, 1L, 1f, 3, 4))
        b.ai = null
        b.player.xp = Defs.evolveXp[3].toFloat()
        assertTrue(b.evolve(b.player))
        assertEquals(4, b.player.era)
        assertEquals(EraBranches.NONE, b.player.eraVariant)
        assertEquals(4, b.pendingVariantEra)
        assertTrue(b.pendingChoice == null)
        val before = b.time
        b.step()
        assertEquals(before, b.time)
        assertFalse(b.chooseEraVariant(3))
        assertTrue(b.chooseEraVariant(1))
        assertEquals(1, b.player.eraVariant)
        assertEquals(EraBranches.NONE, b.pendingVariantEra)
        assertTrue(b.pendingChoice != null)
        b.chooseDoctrine(0)
        b.player.gold = 100_000f
        assertTrue(b.queueUnit(b.player, Role.MELEE))
        assertEquals(Defs.unit(4, Role.MELEE, 1).id, b.player.queue[0])
    }

    @Test
    fun campaignCanForceVariantAtBattleStart() {
        for ((era, mod, choice) in listOf(
            Triple(4, Modifier.FORCE_DIESEL, 0),
            Triple(4, Modifier.FORCE_TESLA, 1),
            Triple(6, Modifier.FORCE_CYBER, 0),
            Triple(6, Modifier.FORCE_BIO, 1),
        )) {
            val b = Battle(BattleConfig(Mode.CAMPAIGN, 4L, 1f, era, era, setOf(mod)))
            assertEquals(EraBranches.NONE, b.pendingVariantEra)
            assertEquals(choice, b.player.eraVariant)
            assertEquals(choice, b.enemy.eraVariant)
            assertEquals(Defs.unit(era, Role.MELEE, choice), b.player.unit(Role.MELEE))
        }
    }

    @Test
    fun teslaProjectileChainsToNearbyTargets() {
        val b = Battle(BattleConfig(Mode.SKIRMISH, 5L, 1f, 4, 4, setOf(Modifier.FORCE_TESLA)))
        b.ai = null
        b.enemy.hold = true
        b.enemy.dmgMul = 0f
        assertTrue(b.spawn(b.player, b.player.unit(Role.RANGED)))
        val shooter = b.units.first { it.alive && it.side == 0 }
        shooter.x = 500f
        val targets = Array(3) { n ->
            assertTrue(b.spawn(b.enemy, Defs.unit(4, Role.HEAVY, 0)))
            b.units.first { it.alive && it.side == 1 && it.x == b.enemy.baseFront - b.enemy.dir * 30f }
                .also { it.x = 680f + n * 34f }
        }
        repeat(105) { b.step(); b.fx.count = 0 }
        assertTrue("trafienie podstawowe", targets[0].hp < targets[0].maxHp)
        assertTrue("pierwszy przeskok", targets[1].hp < targets[1].maxHp)
        assertTrue("drugi przeskok", targets[2].hp < targets[2].maxHp)
    }

    @Test
    fun bioRegenerationCannotExceedMaxHp() {
        val b = Battle(BattleConfig(Mode.SKIRMISH, 8L, 1f, 6, 6, setOf(Modifier.FORCE_BIO)))
        b.ai = null
        assertTrue(b.spawn(b.player, b.player.unit(Role.HEAVY)))
        val u = b.units.first { it.alive }
        u.hp = u.maxHp * 0.5f
        val initial = u.hp
        repeat(120) { b.step(); b.fx.count = 0 }
        assertTrue(u.hp > initial)
        u.hp = u.maxHp - 0.1f
        b.step()
        assertEquals(u.maxHp, u.hp)
    }

    @Test
    fun dailyModifierIsDeterministicAndNonConflicting() {
        val forced = setOf(Modifier.FORCE_DIESEL, Modifier.FORCE_TESLA, Modifier.FORCE_CYBER, Modifier.FORCE_BIO)
        var daysWithBranch = 0
        for (day in 20000L until 20100L) {
            val a = Daily.config(day, Bonuses())
            val b = Daily.config(day, Bonuses(unitDmgMul = 3f))
            assertEquals(a.modifiers, b.modifiers)
            assertEquals(a.startEra, b.startEra)
            assertEquals(a.maxEra, b.maxEra)
            assertTrue(a.modifiers.count { it in forced } <= 1)
            if (a.modifiers.any { it in forced }) daysWithBranch++
        }
        assertTrue(daysWithBranch > 0)
    }
}
