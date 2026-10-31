package com.centurywar.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Weryfikacja ID, wyborów i nieruszonych historycznych wyzwań dnia. */
class BattleVariantsTest {
    private fun legacyConfig(day: Long): BattleConfig {
        val rng = Rng(day * 7919L + 17L)
        val all = Modifier.values()
        val mods = HashSet<Modifier>()
        val count = 2 + rng.nextInt(2)
        while (mods.size < count) mods.add(all[rng.nextInt(all.size)])
        val historical = day < Daily.ERA_EXPANSION_DAY
        val rolledStart = if (historical) rng.nextInt(3) else rng.nextInt(6)
        val start = if (historical && rolledStart >= 2) rolledStart + 1 else rolledStart
        val rolledMax = if (historical) minOf(7, rolledStart + 3)
            else minOf(Defs.ERA_COUNT - 1, rolledStart + 3)
        val maxEra = if (historical && rolledMax >= 2) rolledMax + 1 else rolledMax
        return BattleConfig(
            Mode.DAILY, day, 1.1f + rng.nextFloat() * 0.25f, start, maxEra, mods,
            Bonuses(popCap = 14, turretSlots = 3, eliteUnlocked = true, rallyUnlocked = true),
        )
    }

    @Test
    fun dailyDaysBeforeVariantsAreBitwiseStable() {
        assertTrue(Daily.VARIANTS_DAY >= Daily.ERA_EXPANSION_DAY)
        for (day in 20000L until Daily.VARIANTS_DAY) {
            val old = legacyConfig(day)
            val actual = Daily.config(day, Bonuses())
            assertEquals("start $day", old.startEra, actual.startEra)
            assertEquals("max $day", old.maxEra, actual.maxEra)
            assertEquals("power $day", old.aiPower.toRawBits(), actual.aiPower.toRawBits())
            assertEquals("mods $day", old.modifiers, actual.modifiers)
            assertEquals("seed $day", old.seed, actual.seed)
            assertFalse("warianty dziennego wyzwania nie mogą działać przed datą", actual.variantsEnabled)
        }
    }

    @Test
    fun baseUnitIdsAndCampaignStageIndicesAreNotMoved() {
        assertEquals(9, Defs.ERA_COUNT)
        assertEquals(Defs.ERA_COUNT * 4, Campaign.stages.size)
        for (era in 0 until Defs.ERA_COUNT) for (role in Role.entries) {
            assertEquals(era * 4 + role.ordinal, Defs.unit(era, role).id)
            assertEquals(-1, Defs.unit(era, role).variant)
        }
        for ((i, stage) in Campaign.stages.withIndex()) assertEquals(i, stage.index)
        assertEquals(Defs.allUnits.size, Defs.allUnits.map { it.id }.distinct().size)
        for (i in Defs.allUnits.indices) assertEquals(i, Defs.allUnits[i].id)
    }

    @Test
    fun variantUnitStatsAndArtsAreIndependent() {
        for (art in arrayOf(EraArt.STEAM, EraArt.SILICON)) {
            val era = Defs.eraOf(art)
            assertTrue(era >= 0)
            assertEquals(2, Defs.spec(era).variants.size)
            for (v in 0..1) {
                val variant = Defs.spec(era).variants[v]
                val heavy = Defs.unit(era, Role.HEAVY, v)
                val elite = Defs.unit(era, Role.ELITE, v)
                assertEquals(v, heavy.variant)
                assertEquals(v, elite.variant)
                assertEquals(variant.art, heavy.art)
                assertEquals(variant.art, elite.art)
                assertEquals(variant.art, Defs.turret(era, v).art)
                assertTrue(heavy.id >= Defs.ERA_COUNT * Role.entries.size)
                assertTrue(elite.id >= Defs.ERA_COUNT * Role.entries.size)
                assertTrue(heavy.hp > 0f && elite.hp > 0f)
                assertTrue(Defs.barrage(era, v).count > 0)
            }
            assertTrue(Defs.unit(era, Role.HEAVY, 0).body !=
                Defs.unit(era, Role.HEAVY, 1).body ||
                Defs.unit(era, Role.HEAVY, 0).art != Defs.unit(era, Role.HEAVY, 1).art)
        }
    }

    @Test
    fun choiceDoesNotConsumeBattleRngAndBlocksSimulation() {
        for (art in arrayOf(EraArt.STEAM, EraArt.SILICON)) {
            val era = Defs.eraOf(art)
            val config = BattleConfig(Mode.SKIRMISH, 78433L, 1f, startEra = era, maxEra = era)
            val a = Battle(config)
            val b = Battle(config)
            val before = a.rng.state
            assertEquals(before, b.rng.state)
            assertEquals(era, a.pendingVariant)
            a.step()
            assertEquals(0, a.stepCount)
            assertTrue(a.chooseVariant(0))
            assertTrue(b.chooseVariant(0))
            assertEquals(before, a.rng.state)
            assertEquals(before, b.rng.state)
            repeat(180) {
                if (!a.over) a.step()
                if (!b.over) b.step()
            }
            assertEquals(BattleSnapshot.encode(a).toList(), BattleSnapshot.encode(b).toList())
        }
    }
}
