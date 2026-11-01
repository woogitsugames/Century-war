package com.centurywar.meta

import com.centurywar.game.AiPersonality
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.Bonuses
import com.centurywar.game.Defs
import com.centurywar.game.Mode
import com.centurywar.game.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject
import java.io.File
import java.nio.file.Files

/** Zapis potyczki, granice, ranking, nagrody i kwalifikacja misji. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SkirmishSetupTest {
    private fun file(): File = File(Files.createTempDirectory("skirmish").toFile(), "profile.json")
    private fun profile() = Profile(file())

    @Test
    fun presetsRemainEligibleForLeaderboard() {
        for (i in 0..3) {
            val setup = SkirmishSetup.defaults(i)
            val cfg = setup.toConfig(55L + i, Bonuses())
            assertTrue(setup.isRanked())
            assertTrue(SkirmishSetup.isRanked(cfg))
            assertEquals(0, cfg.startEra)
            assertEquals(Defs.ERA_COUNT - 1, cfg.maxEra)
            assertEquals(SkirmishSetup.PRESET_POWER[i] / 100f, cfg.aiPower, 0.0001f)
            assertEquals(null, cfg.aiPersonality)
            assertTrue(cfg.modifiers.isEmpty())
            assertEquals(15 + i * 14, SkirmishSetup.reward(cfg, true, 35f, 0))
        }
    }

    @Test
    fun mapsSelectedEraPersonalityModifiersAndPowerToBattle() {
        val perks = Bonuses(startGold = 99f)
        val set = SkirmishSetup(
            2, 6, AiPersonality.ENGINEER.ordinal, 175,
            (1 shl Modifier.DOUBLE_INCOME.ordinal) or (1 shl Modifier.FOG_OF_WAR.ordinal),
        )
        val cfg = set.toConfig(123456L, perks)
        assertEquals(Mode.SKIRMISH, cfg.mode)
        assertEquals(123456L, cfg.seed)
        assertSame(perks, cfg.playerBonuses)
        assertEquals(2, cfg.startEra)
        assertEquals(6, cfg.maxEra)
        assertEquals(1.75f, cfg.aiPower, 0.0001f)
        assertEquals(AiPersonality.ENGINEER, cfg.aiPersonality)
        assertEquals(setOf(Modifier.DOUBLE_INCOME, Modifier.FOG_OF_WAR), cfg.modifiers)
        assertEquals(set, SkirmishSetup.fromConfig(cfg))
        assertFalse(SkirmishSetup.isRanked(cfg))
    }

    @Test
    fun clampsCorruptOrOutOfRangeValues() {
        val all = (1 shl Modifier.values().size) - 1
        val s = SkirmishSetup(500, -20, 99, 999, Int.MAX_VALUE).sanitized()
        assertEquals(Defs.ERA_COUNT - 1, s.startEra)
        assertEquals(s.startEra, s.maxEra)
        assertEquals(AiPersonality.values().lastIndex, s.personality)
        assertEquals(200, s.powerPercent)
        assertEquals(3, Integer.bitCount(s.modifierMask))
        assertEquals(0, s.modifierMask and all.inv())
        val negative = SkirmishSetup(-88, -1, -99, -44, -8).sanitized()
        assertEquals(0, negative.startEra)
        assertEquals(0, negative.maxEra)
        assertEquals(-1, negative.personality)
        assertEquals(60, negative.powerPercent)
        assertEquals(3, Integer.bitCount(negative.modifierMask))
        assertEquals(0.6f, negative.toConfig(1L, Bonuses()).aiPower, 0.0001f)
    }

    @Test
    fun customRulesNeverReachRankingsEvenWhenPowerIsPreset() {
        val original = SkirmishSetup.defaults(3)
        assertTrue(original.isRanked())
        val cases = listOf(
            original.copy(startEra = 1),
            original.copy(maxEra = Defs.ERA_COUNT - 2),
            original.copy(personality = 0),
            original.copy(modifierMask = 1),
            original.copy(powerPercent = 165),
        )
        for (s in cases) {
            assertFalse(s.isRanked())
            assertFalse(SkirmishSetup.isRanked(s.toConfig(7L, Bonuses())))
        }
        val illegal = BattleConfig(Mode.SKIRMISH, 9L, 1.6f, startEra = -1)
        assertFalse(SkirmishSetup.isRanked(illegal))
        assertFalse(SkirmishSetup.isRanked(BattleConfig(Mode.CAMPAIGN, 9L, 1.6f)))
    }

    @Test
    fun setupAndCustomHistorySurviveSaveAndRestore() {
        val f = file()
        val p = Profile(f)
        p.skirmishSetup = SkirmishSetup(3, 7, 2, 140,
            (1 shl Modifier.FOG_OF_WAR.ordinal) or (1 shl Modifier.RICH_START.ordinal))
        val cfg = p.skirmishSetup.toConfig(42L, p.bonuses())
        p.recordSkirmish(cfg, 77777L, 20001, true)
        assertEquals(0, p.top(Profile.BOARD_SKIRMISH).size)
        p.save()
        Profile.awaitHintWrites()
        val q = Profile(f).also { it.load() }
        assertEquals(p.skirmishSetup, q.skirmishSetup)
        assertEquals(1, q.skirmishHistory.size)
        assertEquals(77777L, q.skirmishHistory[0].score)
        assertFalse(q.skirmishHistory[0].ranked)
        assertEquals(p.skirmishSetup, q.skirmishHistory[0].setup)
    }

    @Test
    fun profileClampsSavedCustomSetupAndHistory() {
        val f = file()
        val p = Profile(f)
        val root = JSONObject(p.toJson())
        root.put("skirmishSetup", JSONObject().put("startEra", -88).put("maxEra", 999)
            .put("personality", 888).put("powerPercent", 900).put("modifierMask", 0x7fffffff))
        root.put("skirmishHistory", org.json.JSONArray().put(JSONObject()
            .put("score", -2).put("day", -8).put("won", true)
            .put("powerPercent", 500).put("modifierMask", 0x7fffffff)))
        f.writeText(root.toString())
        val q = Profile(f).also { it.load() }
        assertEquals(0, q.skirmishSetup.startEra)
        assertEquals(Defs.ERA_COUNT - 1, q.skirmishSetup.maxEra)
        assertEquals(200, q.skirmishSetup.powerPercent)
        assertEquals(3, Integer.bitCount(q.skirmishSetup.modifierMask))
        assertEquals(AiPersonality.values().lastIndex, q.skirmishSetup.personality)
        assertEquals(0L, q.skirmishHistory[0].score)
        assertEquals(0L, q.skirmishHistory[0].day)
        assertEquals(200, q.skirmishHistory[0].setup.powerPercent)
    }

    @Test
    fun rewardRateNeverExceedsFullRankedMaximumAndCannotFarmOneEra() {
        val maxRanked = SkirmishSetup.defaults(3).toConfig(1L, Bonuses())
        val baseline = SkirmishSetup.reward(maxRanked, true, 600f, 0)
        assertEquals(57, baseline)
        for (eras in 1..Defs.ERA_COUNT) for (power in 60..200 step 5) {
            val custom = SkirmishSetup(0, eras - 1, 2, power).toConfig(3L, Bonuses())
            for (seconds in floatArrayOf(1f, 30f, 120f, 600f)) {
                val amount = SkirmishSetup.reward(custom, true, seconds, 0)
                assertTrue("sekundy=$seconds epoki=$eras moc=$power medale=$amount",
                    amount <= (seconds * 57f / 600f * eras / Defs.ERA_COUNT + 0.0001f))
                assertTrue(amount >= 0)
            }
        }
        val farm = SkirmishSetup(4, 4, 2, 60).toConfig(4L, Bonuses())
        assertEquals(0, SkirmishSetup.reward(farm, true, 30f, 0))
        assertEquals(0, SkirmishSetup.reward(farm, false, 0f, 0))
    }

    @Test
    fun fogDoesNotCountInsaneAchievementOrHardWinsMission() {
        val p = profile()
        val m = Missions(p)
        val now = 1_794_000_000_000L
        m.refresh(now)
        val hard = MissionItem("hard_test", MissionType.HARD_WINS, 1, 10)
        m.state.daily[0] = hard

        fun win(seed: Long, fog: Boolean): Battle {
            val cfg = BattleConfig(Mode.SKIRMISH, seed, 1.6f,
                modifiers = if (fog) setOf(Modifier.FOG_OF_WAR) else emptySet())
            val b = Battle(cfg)
            b.enemy.baseHp = 0f
            b.step()
            assertTrue(b.over)
            assertEquals(0, b.winner)
            return b
        }
        val withFog = win(5L, true)
        Achievements.onBattleEnd(p, withFog, withFog.cfg, true)
        m.onBattleEnd(withFog, now, persist = false)
        assertEquals(0L, p.achievementCounters["INSANE_WINS"] ?: 0L)
        assertEquals(0, hard.progress)

        val clear = win(6L, false)
        Achievements.onBattleEnd(p, clear, clear.cfg, true)
        m.onBattleEnd(clear, now, persist = false)
        assertEquals(1L, p.achievementCounters["INSANE_WINS"])
        assertEquals(1, hard.progress)
    }
    @Test
    fun newModifiersKeepStableProfileBitsAndAreNeverRanked() {
        val newMods = listOf(Modifier.SHORT_DAY, Modifier.VETERANS,
            Modifier.EXPENSIVE_TOWERS, Modifier.MERCENARIES)
        for ((index, modifier) in newMods.withIndex()) {
            assertEquals(10 + index, modifier.ordinal)
            val setup = SkirmishSetup.defaults().copy(modifierMask = 1 shl modifier.ordinal)
            assertEquals(setOf(modifier), setup.sanitized().modifiers())
            assertFalse(setup.isRanked())
            val cfg = setup.toConfig(70L + index, Bonuses())
            assertEquals(setOf(modifier), cfg.modifiers)
            assertFalse(SkirmishSetup.isRanked(cfg))
            assertEquals(setup.modifierMask, SkirmishSetup.fromConfig(cfg).modifierMask)
            val f = file()
            val p = Profile(f)
            p.skirmishSetup = setup
            p.save()
            Profile.awaitHintWrites()
            assertEquals(setup.modifierMask, Profile(f).also { it.load() }.skirmishSetup.modifierMask)
        }
    }

}
