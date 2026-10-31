package com.centurywar.game

import org.junit.Assert.assertEquals
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
