package com.centurywar.game

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pola reakcji na trafienie są czysto wizualne, poza zapisem i RNG bitwy. */
class BattleRulesTestVisual {

    @Test
    fun burnDoesNotStartDirectHitReaction() {
        val b = Battle(BattleConfig(Mode.SKIRMISH, 115L, 1f))
        assertTrue(b.spawn(b.player, Defs.unit(0, Role.MELEE)))
        val u = b.units.first { it.active }

        val damage = Battle::class.java.getDeclaredMethod(
            "damageUnit", Fighter::class.java, java.lang.Float.TYPE, Integer.TYPE,
            Role::class.java, Integer.TYPE, java.lang.Boolean.TYPE,
        )
        damage.isAccessible = true

        damage.invoke(b, u, 3f, 1, null, -1, false)
        assertTrue(u.hp < u.maxHp)
        assertEquals(0.12f, u.flash, 0f)
        assertEquals(-1, u.hitStep)
        assertEquals(0f, u.hitPow, 0f)

        damage.invoke(b, u, 7f, 1, Role.MELEE, -1, true)
        assertEquals(b.stepCount, u.hitStep)
        assertTrue(u.hitPow > 0f && u.hitPow <= 1f)
    }

    @Test
    fun visualFieldsAreNotSerializedAndDoNotChangeSimulation() {
        val cfg = BattleConfig(Mode.SKIRMISH, 777L, 1f)
        val a = Battle(cfg)
        val b = Battle(cfg)
        val def = Defs.unit(0, Role.MELEE)
        assertTrue(a.spawn(a.player, def))
        assertTrue(b.spawn(b.player, def))
        a.units[0].hitStep = 912
        a.units[0].hitPow = 1f
        assertArrayEquals(BattleSnapshot.encode(b), BattleSnapshot.encode(a))

        repeat(180) {
            a.step()
            b.step()
        }
        assertArrayEquals(BattleSnapshot.encode(b), BattleSnapshot.encode(a))
    }
}
