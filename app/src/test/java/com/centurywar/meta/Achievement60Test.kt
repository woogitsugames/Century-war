package com.centurywar.meta

import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.BattleController
import com.centurywar.game.Defs
import com.centurywar.game.EndlessBosses
import com.centurywar.game.Mode
import com.centurywar.game.Modifier
import com.centurywar.game.Role
import com.centurywar.game.Weather
import com.centurywar.game.Weekly
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Achievement60Test {
    private fun profile(): Profile {
        val f = File.createTempFile("century-ach60-", ".json")
        f.delete()
        f.deleteOnExit()
        return Profile(f)
    }

    private fun battle(cfg: BattleConfig, won: Boolean): Battle {
        val b = Battle(cfg)
        if (won) {
            b.enemy.baseHp = 0f
            b.step()
        } else b.surrender()
        assertTrue(b.over)
        return b
    }

    private fun earned(
        p: Profile, b: Battle,
        events: AchievementBattleTracker? = null,
    ): Set<String> = Achievements.onBattleEnd(
        p, b, b.cfg, b.winner == 0, events = events,
    ).mapTo(HashSet()) { it.id }

    @Test
    fun eachWeatherUnlocksOnceAndNotOnLoss() {
        val pairs = listOf(
            Weather.RAIN to "rain_victory",
            Weather.WIND to "wind_victory",
            Weather.NIGHT to "night_victory",
        )
        val p = profile()
        for ((i, pair) in pairs.withIndex()) {
            val cfgLoss = BattleConfig(Mode.SKIRMISH, 700L + i, 1f, weather = pair.first)
            assertFalse(pair.second in earned(p, battle(cfgLoss, false)))
            val cfgWin = BattleConfig(Mode.SKIRMISH, 710L + i, 1f, weather = pair.first)
            assertTrue(pair.second in earned(p, battle(cfgWin, true)))
            val medals = p.medals
            val cfgRepeat = BattleConfig(Mode.SKIRMISH, 720L + i, 1f, weather = pair.first)
            assertFalse(pair.second in earned(p, battle(cfgRepeat, true)))
            assertEquals(medals, p.medals)
        }
    }

    @Test
    fun bothEraVariantsRequireTwoSeparateWins() {
        val tests = listOf(
            "era_steam" to "steam_twins",
            "era_silicon" to "silicon_twins",
        )
        for ((i, pair) in tests.withIndex()) {
            val p = profile()
            val era = Defs.eraKeys.indexOf(pair.first)
            assertTrue(era >= 0)
            for (variant in listOf(0, 0, 1, 1)) {
                val cfg = BattleConfig(Mode.SKIRMISH, 800L + i * 20L + variant + p.battles, 1f,
                    startEra = era, maxEra = era)
                val b = Battle(cfg)
                assertTrue(b.chooseVariant(variant))
                val events = AchievementBattleTracker(b)
                b.enemy.baseHp = 0f
                b.step()
                assertTrue(b.over)
                val unlocked = earned(p, b, events)
                if (variant == 1 && p.battles == 3) assertTrue(pair.second in unlocked)
                else assertFalse(pair.second in unlocked)
            }
            assertEquals(2L, Achievements.progress(p, Achievements.byId(pair.second)!!))
        }
    }

    @Test
    fun allFiveBossesUnlockExactlyOnceAfterRealDefeatState() {
        val p = profile()
        val names = arrayOf("boss_knockback", "boss_aura", "boss_shield",
            "boss_recruiter", "boss_rage")
        for (kind in 1..EndlessBosses.MAX_KIND) {
            val cfg = BattleConfig(Mode.ENDLESS, 900L + kind, 1f)
            val b = Battle(cfg)
            val u = b.units[0]
            u.active = true
            u.side = 1
            u.def = Defs.unit(0, Role.ELITE)
            u.bossKind = kind
            u.hp = 0f
            u.dying = 0f
            b.bossesDefeated = 1
            val events = AchievementBattleTracker(b)
            b.surrender()
            assertTrue(names[kind - 1] in earned(p, b, events))
            val repeatCfg = BattleConfig(Mode.ENDLESS, 950L + kind, 1f)
            val again = Battle(repeatCfg)
            val other = again.units[0]
            other.active = true
            other.side = 1
            other.def = Defs.unit(0, Role.ELITE)
            other.bossKind = kind
            other.hp = 0f
            other.dying = 0f
            again.bossesDefeated = 1
            again.surrender()
            assertFalse(names[kind - 1] in earned(p, again))
        }
        for (id in names) assertTrue(Achievements.isUnlocked(p, id))
    }

    @Test
    fun aimedBarrageNeedsEightDistinctHitUnits() {
        val p = profile()
        val cfg = BattleConfig(Mode.SKIRMISH, 1001L, 1f)
        val b = Battle(cfg)
        val events = AchievementBattleTracker(b)
        val controller = BattleController(b)
        events.beforeAimedBarrage()
        events.afterAimedBarrage(controller.barrage(750f))
        val shot = b.projs.first { it.active && it.side == 0 && it.splash > 0f }
        b.stepCount = 4
        for (i in 0 until 8) {
            val u = b.units[i]
            u.active = true
            u.def = Defs.unit(0, Role.MELEE)
            u.side = 1
            u.x = shot.tx
            u.hitStep = b.stepCount
            u.dying = -1f
        }
        // Pierwsze 7 trafień nie spełnia kryterium.
        b.units[7].hitStep = -1
        shot.t = shot.dur
        shot.active = false
        events.afterStep()
        assertFalse(events.barrageEight)
        val second = b.projs.first { it.active && it.side == 0 && it.splash > 0f }
        second.tx = shot.tx
        second.t = second.dur
        second.active = false
        b.units[7].hitStep = b.stepCount
        events.afterStep()
        assertTrue(events.barrageEight)
        b.surrender()
        assertTrue("barrage_eight" in earned(p, b, events))
        assertEquals(1L, Achievements.progress(p, Achievements.byId("barrage_eight")!!))
    }

    @Test
    fun goldWeeklyAndCustomSkirmishHaveStrictConditions() {
        val p = profile()
        val badWeekly = BattleConfig(Mode.ENDLESS, 1100L, 1f, weekly = 30L)
        val weak = Battle(badWeekly)
        weak.wave = Weekly.threshold(2) - 1
        weak.surrender()
        assertFalse("weekly_gold" in earned(p, weak))
        val cfgWeekly = BattleConfig(Mode.ENDLESS, 1101L, 1f, weekly = 30L)
        val gold = Battle(cfgWeekly)
        gold.wave = Weekly.threshold(2)
        gold.surrender()
        assertTrue("weekly_gold" in earned(p, gold))
        val modifiers = setOf(Modifier.DOUBLE_INCOME, Modifier.FRAGILE_BASES, Modifier.FAST_UNITS)
        val cfgCustom = BattleConfig(Mode.SKIRMISH, 1102L, 1f, modifiers = modifiers)
        assertTrue("custom_three" in earned(p, battle(cfgCustom, true)))
        val medals = p.medals
        assertFalse("custom_three" in earned(p,
            battle(BattleConfig(Mode.SKIRMISH, 1103L, 1f, modifiers = modifiers), true)))
        assertEquals(medals, p.medals)
    }

    @Test
    fun replayMustBeOwnTopScoreAndUnlockOnce() {
        val p = profile()
        p.submit(Profile.BOARD_ENDLESS, 100L, 0L, "old")
        p.submit(Profile.BOARD_ENDLESS, 200L, 1L, "top")
        assertTrue(Achievements.onReplayWatched(p, "random").isEmpty())
        assertTrue(Achievements.onReplayWatched(p, "old").isEmpty())
        assertFalse(Achievements.isUnlocked(p, "replay_record"))
        assertEquals(setOf("replay_record"), Achievements.onReplayWatched(p, "top").map { it.id }.toSet())
        val medals = p.medals
        assertTrue(Achievements.onReplayWatched(p, "top").isEmpty())
        assertEquals(medals, p.medals)
    }

    @Test
    fun catalogHasAllNewIdsAndAllLocalizedDescriptions() {
        val newIds = setOf("steam_twins", "silicon_twins", "rain_victory", "wind_victory",
            "night_victory", "barrage_eight", "boss_knockback", "boss_aura",
            "boss_shield", "boss_recruiter", "boss_rage", "weekly_gold",
            "custom_three", "replay_record")
        assertEquals(14, newIds.size)
        assertEquals(66, Achievements.all.size)
        for (id in newIds) {
            assertTrue(id, Achievements.byId(id) != null)
            assertTrue(Achievements.byId(id)!!.pgsKey == "cw_" + id)
        }
    }
}
