package com.centurywar.game

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Nowe mnożniki i timer doktryny muszą przeżyć zapis oraz dalsze kroki. */
class Doctrine69SnapshotTest {
    @Test fun fourDoctrinesResumeBitForBit() {
        val cfg = BattleConfig(Mode.SKIRMISH, 6901L, 1f,
            startEra = 2, maxEra = 2, variantsEnabled = false,
            modifiers = setOf(Modifier.FOG_OF_WAR))
        val original = Battle(cfg)
        BattleController(original)
        original.playerAi = Ai(original, 0, AiPersonality.BALANCED)
        original.applyDoctrine(original.player, Doctrine.BANNER)
        original.applyDoctrine(original.player, Doctrine.SUPPLY)
        original.applyDoctrine(original.player, Doctrine.SIEGE_MASTERS)
        original.applyDoctrine(original.player, Doctrine.VIGILANCE)
        original.applyDoctrine(original.enemy, Doctrine.SUPPLY)
        assertTrue(original.castRally(original.player))
        repeat(240) { if (!original.over) { original.step(); original.fx.count = 0 } }

        val bytes = BattleSnapshot.encode(original)
        val restored = BattleSnapshot.decode(bytes)
        assertNotNull(restored)
        val copy = restored!!.battle
        assertEquals(14, BattleSnapshot.VERSION)
        assertEquals(original.player.rallyArmorMul, copy.player.rallyArmorMul)
        assertEquals(original.player.supplyUntil, copy.player.supplyUntil)
        assertEquals(original.player.heavyBaseDmgMul, copy.player.heavyBaseDmgMul)
        assertEquals(original.player.turretRangeMul, copy.player.turretRangeMul)
        assertEquals(original.player.fogSight, copy.player.fogSight)
        assertEquals(original.enemy.supplyUntil, copy.enemy.supplyUntil)
        assertArrayEquals(bytes, BattleSnapshot.encode(copy))

        repeat(3700) {
            if (!original.over) { original.step(); original.fx.count = 0 }
            if (!copy.over) { copy.step(); copy.fx.count = 0 }
        }
        assertTrue("Okno zaopatrzenia powinno już wygasnąć", original.time > original.player.supplyUntil)
        assertArrayEquals(BattleSnapshot.encode(original), BattleSnapshot.encode(copy))
    }
}
