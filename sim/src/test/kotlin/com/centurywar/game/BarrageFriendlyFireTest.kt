package com.centurywar.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ostrzał (ręczny i celowany) nigdy nie rani własnych jednostek. */
class BarrageFriendlyFireTest {
    @Test
    fun ownBarrageDoesNotDamageOwnUnits() {
        for (aimed in listOf(true, false)) {
            val b = Battle(BattleConfig(Mode.SKIRMISH, 31L, 1f))
            val p = b.player
            repeat(6) { assertTrue(b.spawn(p, p.unit(Role.MELEE))) }
            val own = b.units.filter { it.active && it.side == p.index }
            assertTrue(own.isNotEmpty())
            // Własne jednostki stoją dokładnie w miejscu ostrzału.
            val target = p.baseFront + p.dir * 700f
            for ((i, u) in own.withIndex()) u.x = target + (i - 3) * 20f
            val hpBefore = own.map { it.hp }
            assertTrue(if (aimed) b.castBarrage(p, target) else b.castBarrage(p))
            // Bez jednostek wroga w pobliżu: tylko pociski ostrzału mogłyby zadać obrażenia.
            repeat(150) {
                b.enemy.abilityCd[0] = 999f
                for (u in b.units) if (u.active && u.side != p.index) u.x = b.enemy.baseFront
                for ((i, u) in own.withIndex()) u.x = target + (i - 3) * 20f
                b.step()
            }
            for ((i, u) in own.withIndex()) {
                assertEquals("jednostka $i (celowany=$aimed)", hpBefore[i], u.hp, 0.001f)
            }
        }
    }
}
