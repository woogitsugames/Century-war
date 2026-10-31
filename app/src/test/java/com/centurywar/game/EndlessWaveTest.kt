package com.centurywar.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tematy obowiązują jednostki konkretnej fali; boss i wcześniejsze fale są niezależne. */
class EndlessWaveTest {
    private fun newBattle(seed: Long = 46L) = Battle(BattleConfig(Mode.ENDLESS, seed, 1f))

    @Test fun skirmishersDoNotWeakenEarlierFightersOrBosses() {
        val b = newBattle()
        assertTrue(b.spawn(b.enemy, b.enemy.unit(Role.MELEE)))
        val old = b.units.first { it.alive && it.side == 1 }
        val oldHp = old.maxHp
        b.nextWaveTheme = EndlessWaveTheme.SKIRMISHERS
        b.waveTimer = 0f
        b.step()
        assertEquals(oldHp, old.maxHp, 0f)
        assertEquals(oldHp, old.hp, 0f)
        assertEquals(1.05f, b.enemy.hpMul, 0.0001f)
        val queued = Defs.allUnits[b.enemy.queue[0]]
        assertEquals(EndlessWaveTheme.SKIRMISHERS.ordinal, b.enemy.queueWaveTheme[0])
        b.enemy.trainT = queued.trainTime
        b.waveTimer = 900f
        b.step()
        val fresh = b.units.first { it.alive && it.side == 1 && it !== old }
        val expected = queued.hp * b.enemy.hpMul * Defs.tierStatMul(fresh.tier) * 0.8f
        assertEquals(expected, fresh.maxHp, 0.02f)
        assertEquals(oldHp, old.maxHp, 0f)
    }

    @Test fun siegeSpeedIsPerFighterAndQueueKeepsThemeAfterNextWave() {
        val b = newBattle()
        assertTrue(b.spawn(b.enemy, b.enemy.unit(Role.MELEE)))
        val old = b.units.first { it.alive && it.side == 1 }
        b.nextWaveTheme = EndlessWaveTheme.SIEGE_TRAIN
        b.waveTimer = 0f
        b.step()
        assertEquals(1f, b.enemy.speedMul, 0f)
        assertEquals(1f, old.waveSpeedMul, 0f)
        val firstDef = Defs.allUnits[b.enemy.queue[0]]
        b.nextWaveTheme = EndlessWaveTheme.MIXED
        b.waveTimer = 0f
        b.step()
        assertEquals(2, b.wave)
        assertEquals(EndlessWaveTheme.SIEGE_TRAIN.ordinal, b.enemy.queueWaveTheme[0])
        b.enemy.trainT = firstDef.trainTime
        b.waveTimer = 900f
        b.step()
        val fresh = b.units.first { it.alive && it.side == 1 && it !== old }
        assertEquals(0.85f, fresh.waveSpeedMul, 0f)
        assertEquals(1f, old.waveSpeedMul, 0f)
        assertEquals(1f, b.enemy.speedMul, 0f)
    }

    @Test fun bossesIgnoreHpAndSpeedPenaltiesAtThemedWave() {
        for (theme in arrayOf(EndlessWaveTheme.SKIRMISHERS, EndlessWaveTheme.SIEGE_TRAIN)) {
            val b = newBattle()
            for (w in 1..10) {
                if (b.pendingCards != null) b.chooseEndlessCard(0)
                for (u in b.units) u.active = false
                b.enemy.aliveCount = 0
                b.enemy.queueSize = 0
                b.nextWaveTheme = if (w == 10) theme else EndlessWaveTheme.MIXED
                b.waveTimer = 0f
                b.step()
            }
            val boss = b.currentBoss()!!
            val expected = boss.def.hp * b.enemy.hpMul * (3f + 0.45f) *
                Defs.tierStatMul(boss.tier)
            assertEquals("HP bossa w $theme", expected, boss.maxHp, 0.05f)
            assertEquals("Prędkość bossa w $theme", 1f, boss.waveSpeedMul, 0f)
            assertEquals(10, b.wave)
        }
    }

    @Test fun enemyAiChoosesBothVariantsDeterministicallyInEndless() {
        val steam = Defs.eraOf(EraArt.STEAM)
        val silicon = Defs.eraOf(EraArt.SILICON)
        val first = mutableSetOf<Int>()
        val advanced = mutableSetOf<Int>()
        for (seed in 1L..48L) {
            val cfg = BattleConfig(Mode.ENDLESS, seed, 1f, startEra = steam, maxEra = silicon)
            val a = Battle(cfg)
            val again = Battle(cfg)
            assertEquals(a.enemy.eraVariant, again.enemy.eraVariant)
            assertTrue(a.enemy.eraVariant in 0..1)
            first.add(a.enemy.eraVariant)
            assertTrue(a.chooseVariant(0))
            repeat(12) {
                if (a.pendingCards != null) assertTrue(a.chooseEndlessCard(0))
                a.waveTimer = 0f
                a.step()
            }
            assertEquals(silicon, a.enemy.era)
            assertTrue(a.enemy.eraVariant in 0..1)
            advanced.add(a.enemy.eraVariant)
        }
        assertEquals(setOf(0, 1), first)
        assertEquals(setOf(0, 1), advanced)
    }

    @Test fun weeklySiegeAlsoHasEnemyVariants() {
        val steam = Defs.eraOf(EraArt.STEAM)
        val seen = mutableSetOf<Int>()
        var matching = 0
        for (week in 1L..300L) {
            val config = Weekly.config(week)
            if (config.startEra != steam) continue
            assertTrue(config.variantsEnabled)
            val a = Battle(config)
            val b = Battle(config)
            assertEquals(a.enemy.eraVariant, b.enemy.eraVariant)
            assertTrue(a.enemy.eraVariant in 0..1)
            seen.add(a.enemy.eraVariant)
            matching++
        }
        assertTrue(matching >= 10)
        assertEquals(setOf(0, 1), seen)
    }
}
