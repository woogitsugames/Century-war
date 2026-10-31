package com.centurywar.game

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regresje pogody: determinizm, zgodność historycznych dni i symetria zasad. */
class WeatherTest {
    @Test fun campaignWeatherFollowsFixedCycle() {
        for (stage in Campaign.stages) {
            val expected = if (stage.index >= 4 && (stage.index - 4) % 3 == 0) when ((stage.index - 4) / 3 % 3) {
                0 -> Weather.RAIN
                1 -> Weather.WIND
                else -> Weather.NIGHT
            } else Weather.CLEAR
            assertEquals("Etap ${stage.index + 1}", expected, stage.weather)
            assertEquals(expected, Campaign.config(stage, Bonuses(), 100L).weather)
        }
        assertEquals(Weather.CLEAR, BattleConfig(Mode.SKIRMISH, 77L, 1f).weather)
    }

    @Test fun rangeEffectsAndWindAreSymmetric() {
        val clear = Battle(BattleConfig(Mode.SKIRMISH, 20L, 1f))
        assertEquals(1f, clear.projectileRangeMul(0), 0f)
        assertEquals(1f, clear.projectileRangeMul(1, true), 0f)
        val rain = Battle(BattleConfig(Mode.SKIRMISH, 20L, 1f, weather = Weather.RAIN))
        for (side in 0..1) {
            assertEquals(0.9f, rain.projectileRangeMul(side), 0f)
            assertEquals(0.9f, rain.projectileRangeMul(side, true), 0f)
        }
        val night = Battle(BattleConfig(Mode.SKIRMISH, 20L, 1f, weather = Weather.NIGHT))
        for (side in 0..1) {
            assertEquals(0.95f, night.projectileRangeMul(side), 0f)
            assertEquals(0.85f, night.projectileRangeMul(side, true), 0f)
        }
        val wind = Battle(BattleConfig(Mode.SKIRMISH, 20L, 1f, weather = Weather.WIND))
        val initial = wind.windDirection()
        assertTrue(initial == 1 || initial == -1)
        val tail = if (initial == 1) 0 else 1
        assertEquals(1.1f, wind.projectileRangeMul(tail), 0f)
        assertEquals(0.9f, wind.projectileRangeMul(1 - tail), 0f)
        wind.stepCount = 75 * 60 - 1
        assertEquals(initial, wind.windDirection())
        wind.stepCount = 75 * 60
        assertEquals(-initial, wind.windDirection())
        assertEquals(0.9f, wind.projectileRangeMul(tail), 0f)
        assertEquals(1.1f, wind.projectileRangeMul(1 - tail), 0f)
        wind.stepCount = 150 * 60
        assertEquals(initial, wind.windDirection())
    }

    @Test fun rainSlowsMovementAndHalvesActiveBurnDamage() {
        val clear = Battle(BattleConfig(Mode.SKIRMISH, 123L, 1f))
        val rain = Battle(BattleConfig(Mode.SKIRMISH, 123L, 1f, weather = Weather.RAIN))
        clear.ai = null
        rain.ai = null
        val def = Defs.unit(0, Role.MELEE)
        assertTrue(clear.spawn(clear.player, def))
        assertTrue(rain.spawn(rain.player, def))
        val u = clear.units.first { it.alive && it.side == 0 }
        val v = rain.units.first { it.alive && it.side == 0 }
        val start = u.x
        clear.step(); rain.step()
        assertTrue(u.x > start && v.x > start)
        assertEquals(0.92f, (v.x - start) / (u.x - start), 0.005f)

        val a = clear.units.first { it.alive && it.side == 0 }
        val b = rain.units.first { it.alive && it.side == 0 }
        val ahp = a.hp; val bhp = b.hp
        a.burnDps = 42f; a.burnLeft = 2f
        b.burnDps = 42f; b.burnLeft = 2f
        clear.step(); rain.step()
        assertTrue(a.hp < ahp && b.hp < bhp)
        assertEquals(0.5f, (bhp - b.hp) / (ahp - a.hp), 0.01f)
    }

    @Test fun endlessWeatherDependsOnEnemyEraOnly() {
        val battle = Battle(BattleConfig(Mode.ENDLESS, 1151L, 1f))
        val first = battle.weather
        val rng = battle.rng.state
        battle.enemy.era++
        assertFalse(first == battle.weather)
        assertEquals(rng, battle.rng.state)
        battle.enemy.era--
        assertEquals(first, battle.weather)
    }

    @Test fun historicalDailyDrawsAreUnchanged() {
        for (day in longArrayOf(19000L, 20000L, 20757L, Daily.RELEASE_DAY - 2, Daily.RELEASE_DAY - 1)) {
            val rng = Rng(day * 7919L + 17L)
            val mods = HashSet<Modifier>()
            val values = Modifier.values()
            val count = 2 + rng.nextInt(2)
            while (mods.size < count) mods.add(values[rng.nextInt(values.size)])
            val rolledStart = if (day < Daily.RELEASE_DAY) rng.nextInt(3) else rng.nextInt(6)
            val start = if (day < Daily.RELEASE_DAY && rolledStart >= 2) rolledStart + 1 else rolledStart
            val rolledMax = if (day < Daily.RELEASE_DAY) minOf(7, rolledStart + 3) else minOf(Defs.ERA_COUNT - 1, rolledStart + 3)
            val maxEra = if (day < Daily.RELEASE_DAY && rolledMax >= 2) rolledMax + 1 else rolledMax
            val aiPower = 1.1f + rng.nextFloat() * 0.25f
            val actual = Daily.config(day, Bonuses())
            assertEquals(Weather.CLEAR, actual.weather)
            assertEquals(mods, actual.modifiers)
            assertEquals(start, actual.startEra)
            assertEquals(maxEra, actual.maxEra)
            assertEquals(aiPower, actual.aiPower, 0f)
        }
        val future = Daily.config(Daily.RELEASE_DAY + 3, Bonuses())
        assertEquals(future.weather, Daily.config(Daily.RELEASE_DAY + 3, Bonuses()).weather)
    }

    @Test fun clearDefaultHasIdenticalStateToExplicitClear() {
        val implicit = Battle(BattleConfig(Mode.SKIRMISH, 19991L, 1.05f, startEra = 2, maxEra = 3))
        val explicit = Battle(BattleConfig(Mode.SKIRMISH, 19991L, 1.05f, startEra = 2, maxEra = 3, weather = Weather.CLEAR))
        implicit.playerAi = Ai(implicit, 0)
        explicit.playerAi = Ai(explicit, 0)
        repeat(60 * 75) {
            implicit.step(); explicit.step()
            implicit.fx.count = 0; explicit.fx.count = 0
        }
        assertArrayEquals(BattleSnapshot.encode(implicit), BattleSnapshot.encode(explicit))
    }

    @Test fun rainyAndWindyReplaysRemainDeterministic() {
        for (weather in arrayOf(Weather.RAIN, Weather.WIND, Weather.NIGHT)) {
            val cfg = BattleConfig(Mode.SKIRMISH, 67890L, 1f, startEra = 2, maxEra = 3, weather = weather)
            val original = Battle(cfg)
            val replay = Battle(cfg)
            original.playerAi = Ai(original, 0)
            replay.playerAi = Ai(replay, 0)
            repeat(60 * 80) {
                original.step(); replay.step()
                original.fx.count = 0; replay.fx.count = 0
            }
            assertArrayEquals("${weather.name}: stan", BattleSnapshot.encode(original), BattleSnapshot.encode(replay))
        }
    }
}
