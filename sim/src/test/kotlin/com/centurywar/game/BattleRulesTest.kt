package com.centurywar.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Testy pojedynczych reguł symulacji (regresje znalezionych błędów). */
class BattleRulesTest {

    /** Bitwa bez AI przeciwnika: kontrolujemy każdą jednostkę ręcznie. */
    private fun quietBattle(): Battle = Battle(BattleConfig(Mode.SKIRMISH, 1L, 1f)).also { it.ai = null }

    /** Obrażenia zadane bazie wroga przez jednego wojownika stojącego tuż przed nią. */
    private fun baseDamageFromOneMelee(doctrine: Doctrine?): Float {
        val b = quietBattle()
        doctrine?.let { b.applyDoctrine(b.player, it) }
        assertTrue(b.spawn(b.player, Defs.unit(0, Role.MELEE)))
        val u = b.units.first { it.alive }
        u.x = b.enemy.baseFront - 20f
        val before = b.enemy.baseHp
        repeat(30) { b.step() } // 0,5 s: startowe cd 0,3 s, więc dokładnie jeden cios
        return before - b.enemy.baseHp
    }

    @Test
    fun siegeDoctrineIncreasesDamageToBases() {
        val plain = baseDamageFromOneMelee(null)
        val siege = baseDamageFromOneMelee(Doctrine.SIEGE)
        assertTrue("jednostka powinna uderzyć bazę", plain > 0f)
        assertEquals(plain * 1.3f, siege, plain * 0.01f)
    }

    @Test
    fun turretMuzzleFlashFadesWhenNoTargetLeft() {
        val b = quietBattle()
        val p = b.player
        p.gold = 10_000f
        assertTrue(b.buyTurret(p, 0))
        assertTrue(b.spawn(b.enemy, Defs.unit(0, Role.MELEE)))
        val foe = b.units.first { it.alive && it.side == 1 }
        foe.x = p.baseFront + 200f
        var fired = false
        for (i in 0 until 120) {
            b.step()
            if (p.turretFire[0] > 0f) { fired = true; break }
        }
        assertTrue("wieża powinna strzelić", fired)
        foe.active = false // cel znika (np. zginął od innego źródła)
        repeat(60) { b.step() }
        assertTrue("błysk wystrzału nie może wisieć bez celu", p.turretFire[0] <= 0f)
    }

    @Test
    fun upgradeCostsAndBoundsInAllEras() {
        for (era in 0 until Defs.ERA_COUNT) {
            for (role in Role.values()) {
                val second = Defs.unitUpgradeCost(era, role, 2)
                val third = Defs.unitUpgradeCost(era, role, 3)
                assertTrue(second > 0 && third > second)
                if (era > 0) assertTrue(second > Defs.unitUpgradeCost(era - 1, role, 2))
            }
        }
        val b = quietBattle()
        val p = b.player
        val role = Role.MELEE
        val cost = b.unitUpgradeCost(p, role)
        p.gold = cost - 1f
        assertFalse(b.upgradeUnit(p, role))
        assertEquals(1, p.unitTier[role.ordinal])
        p.gold = cost + Defs.unitUpgradeCost(0, role, 3) + 100f
        assertTrue(b.upgradeUnit(p, role))
        assertEquals(2, p.unitTier[role.ordinal])
        assertTrue(b.upgradeUnit(p, role))
        assertEquals(3, p.unitTier[role.ordinal])
        val gold = p.gold
        assertFalse(b.upgradeUnit(p, role))
        assertEquals(gold, p.gold)
        p.eliteUnlocked = false
        assertFalse(b.upgradeUnit(p, Role.ELITE))
    }

    @Test
    fun newFightersGainTiersAndEraResetDoesNotNerfVeterans() {
        val b = quietBattle()
        val p = b.player
        p.gold = 10000f
        assertTrue(b.upgradeUnit(p, Role.MELEE))
        assertTrue(b.upgradeUnit(p, Role.MELEE))
        val d = Defs.unit(0, Role.MELEE)
        assertTrue(b.spawn(p, d))
        val veteran = b.units.first { it.alive }
        assertEquals(3, veteran.tier)
        assertEquals(d.hp * Defs.tierStatMul(3), veteran.maxHp)
        assertEquals(d.dmg * Defs.tierStatMul(3), veteran.dmg)
        assertEquals(d.speed * 1.1f, Defs.unitSpeed(d, veteran.tier))
        assertEquals(Defs.unit(0, Role.RANGED).range * 1.1f,
            Defs.unitRange(Defs.unit(0, Role.RANGED), 3))
        assertTrue(Defs.unitSplash(Defs.unit(0, Role.ELITE), 3) > Defs.unit(0, Role.ELITE).splash)
        assertEquals(1.15f, Defs.unitTrainRate(Defs.unit(0, Role.HEAVY), 3))

        p.xp = Defs.evolveXp[0].toFloat()
        assertTrue(b.evolve(p))
        assertTrue(p.unitTier.all { it == 1 })
        assertEquals(3, veteran.tier)
        assertTrue(b.spawn(p, Defs.unit(1, Role.MELEE)))
        val newUnit = b.units.first { it.alive && it !== veteran }
        assertEquals(1, newUnit.tier)
    }

    @Test
    fun computerBuysUpgradesWhenFormationIsReady() {
        val b = Battle(BattleConfig(Mode.SKIRMISH, 73L, 1f))
        val e = b.enemy
        e.gold = 10000f
        repeat(3) { assertTrue(b.spawn(e, Defs.unit(0, Role.RANGED))) }
        repeat(100) { b.step() }
        assertTrue("AI powinno kupić ulepszenie popularnej roli", e.unitTier[Role.RANGED.ordinal] >= 2)
    }

    @Test
    fun aliveCountMatchesUnitsDuringBattle() {
        val b = Battle(BattleConfig(Mode.SKIRMISH, 5L, 1f))
        b.playerAi = Ai(b, 0)
        repeat(60 * 60 * 4) {
            if (!b.over) b.step()
            b.fx.count = 0
            for (s in b.sides) {
                val n = b.units.count { it.alive && it.side == s.index }
                assertEquals(n, s.aliveCount)
            }
        }
    }
}
