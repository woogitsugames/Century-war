package com.centurywar.game

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Modyfikatory startowe nie mogą znikać przy przeliczeniu kolejnych fal. */
class EndlessModifier122Test {
    private data class Stats(val hp: Float, val dmg: Float)

    private fun nextWave(b: Battle) {
        if (b.pendingVariant != null) assertTrue(b.chooseVariant(0))
        if (b.pendingCards != null) assertTrue(b.chooseEndlessCard(0))
        // Izolujemy skalowanie fali od treningu i walki z poprzednimi falami.
        b.enemy.queueSize = 0
        b.waveTimer = 0f
        b.step()
        b.fx.count = 0
    }

    private fun atWaveTen(cfg: BattleConfig): Stats {
        val b = Battle(cfg)
        repeat(10) { nextWave(b) }
        assertEquals(10, b.wave)
        assertTrue(b.spawn(b.enemy, b.enemy.unit(Role.MELEE)))
        val fighter = b.units.first { it.alive && it.side == 1 && !it.isBoss }
        return Stats(fighter.maxHp, fighter.dmg)
    }

    private fun config(vararg mods: Modifier) =
        BattleConfig(Mode.ENDLESS, 122L, 1f, modifiers = mods.toSet())

    @Test fun waveTenKeepsArmoredGlassCannonAndVeterans() {
        val base = atWaveTen(config())
        val armored = atWaveTen(config(Modifier.ARMORED_FOE))
        val glass = atWaveTen(config(Modifier.GLASS_CANNON))
        val veterans = atWaveTen(config(Modifier.VETERANS))
        val combined = atWaveTen(config(
            Modifier.GLASS_CANNON, Modifier.VETERANS, Modifier.ARMORED_FOE))

        assertEquals("ARMORED_FOE: HP +30%", 1.3f, armored.hp / base.hp, 0.0001f)
        assertEquals(1f, armored.dmg / base.dmg, 0.0001f)
        assertEquals("GLASS_CANNON: HP -30%", 0.7f, glass.hp / base.hp, 0.0001f)
        assertEquals("GLASS_CANNON: obrażenia +50%", 1.5f, glass.dmg / base.dmg, 0.0001f)
        val veteranBonus = 1.06f * 1.06f
        assertEquals(veteranBonus, veterans.hp / base.hp, 0.0001f)
        assertEquals(veteranBonus, veterans.dmg / base.dmg, 0.0001f)
        assertEquals(0.7f * veteranBonus * 1.3f, combined.hp / base.hp, 0.0001f)
        assertEquals(1.5f * veteranBonus, combined.dmg / base.dmg, 0.0001f)
    }

    @Test fun historicalWeeklySiegeKeepsArmoredFoeOnWaveTen() {
        val weekly = Weekly.config(2000L)
        assertTrue(Modifier.ARMORED_FOE in weekly.modifiers)
        val withoutArmor = BattleConfig(
            mode = weekly.mode, seed = weekly.seed, aiPower = weekly.aiPower,
            startEra = weekly.startEra, maxEra = weekly.maxEra,
            modifiers = weekly.modifiers - Modifier.ARMORED_FOE,
            playerBonuses = weekly.playerBonuses, weekly = weekly.weekly,
            weeklyCard = weekly.weeklyCard,
        )
        val normal = atWaveTen(withoutArmor)
        val armored = atWaveTen(weekly)
        assertEquals(1.3f, armored.hp / normal.hp, 0.0001f)
        assertEquals(normal.dmg, armored.dmg, 0.0001f)
    }

    @Test fun modifiedEndlessRemainsIdenticalAfterRestoringBeforeWaveTen() {
        val cfg = config(Modifier.GLASS_CANNON, Modifier.ARMORED_FOE, Modifier.VETERANS)
        val original = Battle(cfg)
        repeat(9) { nextWave(original) }
        val restored = BattleSnapshot.decode(BattleSnapshot.encode(original))!!.battle
        assertArrayEquals(BattleSnapshot.encode(original), BattleSnapshot.encode(restored))
        repeat(3) {
            nextWave(original)
            nextWave(restored)
            assertArrayEquals(BattleSnapshot.encode(original), BattleSnapshot.encode(restored))
        }
        assertEquals(12, original.wave)
    }
}
