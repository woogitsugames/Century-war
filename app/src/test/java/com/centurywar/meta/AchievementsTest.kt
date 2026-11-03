package com.centurywar.meta

import com.centurywar.engine.L
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.Defs
import com.centurywar.game.Doctrine
import com.centurywar.game.Mode
import com.centurywar.game.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AchievementsTest {
    private fun profile(): Profile {
        val file = File.createTempFile("century-achievements", ".json")
        file.delete()
        file.deleteOnExit()
        return Profile(file)
    }

    private fun finished(cfg: BattleConfig, won: Boolean): Battle {
        val b = Battle(cfg)
        if (won) {
            b.enemy.baseHp = 0f
            b.step()
        } else {
            b.surrender()
        }
        assertTrue(b.over)
        assertEquals(if (won) 0 else 1, b.winner)
        return b
    }

    private fun ids(list: List<AchievementDef>) = list.map { it.id }.toSet()

    @Test
    fun catalogIsCompleteAndLocalized() {
        assertEquals(78, Achievements.all.size)
        assertEquals(78, Achievements.all.map { it.id }.toSet().size)
        assertEquals(78, Achievements.all.map { it.pgsKey }.toSet().size)
        for (a in Achievements.all) {
            assertTrue(a.threshold > 0L)
            assertTrue(a.medalReward > 0)
            assertTrue(a.pgsKey.isNotBlank())
            for (lang in L.CODES.indices) {
                val nameKey = "ach_" + a.id
                val descKey = nameKey + "_d"
                assertEquals(L.CODES.size, L.raw(nameKey)?.size)
                assertEquals(L.CODES.size, L.raw(descKey)?.size)
                assertTrue(L.text(nameKey, lang).isNotBlank())
                assertTrue(L.text(nameKey, lang) != nameKey)
                assertTrue(L.text(descKey, lang).isNotBlank())
                assertTrue(L.text(descKey, lang) != descKey)
            }
        }
    }

    @Test
    fun hardCampaignAchievementRequiresAllUniqueStages() {
        val p = profile()
        val def = Achievements.byId("campaign_hard")!!
        for (i in 0 until 35) p.recordHardVictory(i)
        p.recordHardVictory(34)
        assertEquals(35L, Achievements.progress(p, def))
        val cfg = BattleConfig(Mode.SKIRMISH, 12L, 1f)
        val incomplete = Achievements.onBattleEnd(p, finished(cfg, false), cfg, false)
        assertFalse(ids(incomplete).contains("campaign_hard"))
        p.recordHardVictory(35)
        val lastCfg = BattleConfig(Mode.SKIRMISH, 13L, 1f)
        val complete = Achievements.onBattleEnd(p, finished(lastCfg, false), lastCfg, false)
        assertTrue(ids(complete).contains("campaign_hard"))
        assertEquals(36L, Achievements.progress(p, def))
    }

    @Test
    fun oneBattleCanBeRecordedOnlyOnce() {
        val p = profile()
        val cfg = BattleConfig(Mode.SKIRMISH, 1L, 1.05f)
        val b = finished(cfg, true)
        val events = ArrayList<String>()
        val online = object : OnlineAchievements {
            override fun unlock(pgsKey: String) { events.add(pgsKey) }
        }

        val earned = Achievements.onBattleEnd(p, b, cfg, true, online)
        assertTrue(ids(earned).contains("win_1"))
        val medals = p.medals
        val notifications = events.size
        assertTrue(Achievements.onBattleEnd(p, b, cfg, true, online).isEmpty())
        assertEquals(1, p.battles)
        assertEquals(1, p.wins)
        assertEquals(medals, p.medals)
        assertEquals(notifications, events.size)
    }

    @Test
    fun winThresholdsUnlockAtExactBoundary() {
        val p = profile()
        p.wins = 8
        val cfg1 = BattleConfig(Mode.SKIRMISH, 11L, 1f)
        val cfg2 = BattleConfig(Mode.SKIRMISH, 12L, 1f)
        val first = Achievements.onBattleEnd(p, finished(cfg1, true), cfg1, true)
        assertFalse(ids(first).contains("win_10"))
        val second = Achievements.onBattleEnd(p, finished(cfg2, true), cfg2, true)
        assertTrue(ids(second).contains("win_10"))
        assertEquals(10, p.wins)
        assertEquals(10L, Achievements.progress(p, Achievements.byId("win_10")!!))
    }

    @Test
    fun campaignMilestonesUseUniqueStagesAndBestStars() {
        val p = profile()
        p.stars.fill(3)
        val cfg = BattleConfig(Mode.SKIRMISH, 20L, 1f)
        val earned = ids(Achievements.onBattleEnd(p, finished(cfg, false), cfg, false))
        assertTrue("wszystkie gwiazdki", "stars_96" in earned)
        assertTrue("wszystkie etapy", "stages_32" in earned)
    }

    @Test
    fun dailyCountsDistinctSeedsEvenWhenReplayedOutOfOrder() {
        val p = profile()
        val seeds = listOf(20001L, 20002L, 20001L, 20003L, 20004L, 20005L, 20006L, 20007L)
        for (seed in seeds) {
            val cfg = BattleConfig(Mode.DAILY, seed, 1f)
            Achievements.onBattleEnd(p, finished(cfg, false), cfg, false)
        }
        assertEquals(7, p.achievementDailySeeds.size)
        assertTrue(Achievements.isUnlocked(p, "daily_7"))
        assertFalse(Achievements.isUnlocked(p, "daily_30"))
    }

    @Test
    fun builtThenSoldTurretStillPreventsNoTurretsReward() {
        val p = profile()
        val cfg = BattleConfig(Mode.SKIRMISH, 31L, 1f)
        val b = Battle(cfg)
        b.player.gold = 100_000f
        assertTrue(b.buyTurret(b.player, 0))
        assertTrue(b.sellTurret(b.player, 0))
        assertEquals(1, b.player.turretsBuilt)
        b.enemy.baseHp = 0f
        b.step()
        Achievements.onBattleEnd(p, b, cfg, true)
        assertFalse(Achievements.isUnlocked(p, "without_turrets"))
    }

    @Test
    fun survivingEliteRequiresAtLeastOneTrainedElite() {
        val p = profile()
        val cfg = BattleConfig(Mode.SKIRMISH, 32L, 1f)
        val b = Battle(cfg)
        assertTrue(b.spawn(b.player, Defs.unit(0, Role.ELITE)))
        assertEquals(1, b.player.elitesTrained)
        b.enemy.baseHp = 0f
        b.step()
        Achievements.onBattleEnd(p, b, cfg, true)
        assertTrue(Achievements.isUnlocked(p, "elite_survivors"))

        val p2 = profile()
        val cfg2 = BattleConfig(Mode.SKIRMISH, 33L, 1f)
        val b2 = Battle(cfg2)
        b2.player.elitesTrained = 1
        b2.player.elitesLost = 1
        b2.enemy.baseHp = 0f
        b2.step()
        Achievements.onBattleEnd(p2, b2, cfg2, true)
        assertFalse(Achievements.isUnlocked(p2, "elite_survivors"))
    }

    @Test
    fun allDoctrinesAccumulateAsBitmask() {
        val p = profile()
        val cfg = BattleConfig(Mode.SKIRMISH, 40L, 1f)
        val b = Battle(cfg)
        b.player.doctrines.addAll(Doctrine.values())
        b.surrender()
        Achievements.onBattleEnd(p, b, cfg, false)
        assertEquals(Doctrine.values().size.toLong(), Achievements.progress(p, Achievements.byId("all_doctrines")!!))
        assertTrue(Achievements.isUnlocked(p, "all_doctrines"))
    }

    @Test
    fun waveMilestonesAreAwardedOnDefeat() {
        val p = profile()
        val cfg = BattleConfig(Mode.ENDLESS, 50L, 1f)
        val b = Battle(cfg)
        b.wave = 25
        b.surrender()
        val earned = ids(Achievements.onBattleEnd(p, b, cfg, false))
        assertEquals(25, p.bestWave)
        assertTrue("wave_10" in earned)
        assertTrue("wave_25" in earned)
        assertFalse("wave_50" in earned)
    }

    @Test
    fun researchAchievementOnlyUnlocksOnce() {
        val p = profile()
        val def = Profile.research.first { it.key == "r_income" }
        p.levels[def.key] = 12
        val earned = ids(Achievements.onResearchBought(p, def))
        assertTrue("research_10" in earned)
        assertTrue("research_maxed" in earned)
        val medals = p.medals
        assertTrue(Achievements.onResearchBought(p, def).isEmpty())
        assertEquals(medals, p.medals)
    }

    @Test
    fun insaneDifficultyUsesActualBattleConfig() {
        val p = profile()
        val cfg = BattleConfig(Mode.SKIRMISH, 60L, 1.6f)
        val earned = ids(Achievements.onBattleEnd(p, finished(cfg, true), cfg, true))
        assertTrue("insane_win" in earned)
    }

    @Test
    fun combatEventCountersOnlyTrackSuccessfulActions() {
        val cfg = BattleConfig(Mode.SKIRMISH, 65L, 1f)
        val b = Battle(cfg)
        assertTrue(b.castBarrage(b.player))
        assertEquals(1, b.player.barragesUsed)
        assertFalse(b.castBarrage(b.player))
        assertEquals(1, b.player.barragesUsed)

        b.player.xp = Defs.evolveXp[0].toFloat()
        assertTrue(b.evolve(b.player))
        assertEquals(1, b.player.erasAdvanced)
    }

    @Test
    fun onlineAdapterCannotBlockLocalUnlock() {
        val p = profile()
        val cfg = BattleConfig(Mode.SKIRMISH, 303L, 1f)
        val adapter = object : OnlineAchievements {
            override fun unlock(pgsKey: String) { throw IllegalStateException("PGS offline") }
        }
        val unlocked = Achievements.onBattleEnd(p, finished(cfg, true), cfg, true, adapter)
        assertTrue(unlocked.any { it.id == "win_1" })
        assertTrue("win_1" in p.unlockedAchievements)
    }

    @Test
    fun profileProgressDoesNotGrantRewardsWithoutUnlock() {
        val p = profile()
        p.battles = 1
        p.wins = 1
        assertFalse(Achievements.isUnlocked(p, "win_1"))
        val def = Achievements.byId("win_1")!!
        assertEquals(1L, Achievements.progress(p, def))
        assertEquals(0, p.medals)
    }

    @Test
    fun invalidResultCannotAffectProfile() {
        val p = profile()
        val cfg = BattleConfig(Mode.SKIRMISH, 70L, 1f)
        val b = Battle(cfg)
        assertTrue(Achievements.onBattleEnd(p, b, cfg, true).isEmpty())
        b.surrender()
        assertTrue(Achievements.onBattleEnd(p, b, cfg, true).isEmpty())
        assertEquals(0, p.battles)
    }

    // R6-01: cztery osiągnięcia pierwszej godziny.
    @Test
    fun firstHourAchievementsMatchApprovedTable() {
        val expected = listOf(
            listOf("first_evolve", AchievementMetric.EVOLUTIONS, 1L, 10, AchievementCategory.TACTICS),
            listOf("first_turret", AchievementMetric.TURRETS_BUILT, 1L, 10, AchievementCategory.TACTICS),
            listOf("first_research", AchievementMetric.RESEARCH_LEVELS, 1L, 10, AchievementCategory.RESEARCH),
            listOf("stages_3", AchievementMetric.STAGES, 3L, 20, AchievementCategory.CAMPAIGN),
        )
        // Dopisane na końcu katalogu, w ustalonej kolejności.
        assertEquals(expected.map { it[0] }, Achievements.all.takeLast(4).map { it.id })
        for (row in expected) {
            val a = Achievements.byId(row[0] as String)!!
            assertEquals(row[1], a.metric)
            assertEquals(row[2], a.threshold)
            assertEquals(row[3], a.medalReward)
            assertEquals(row[4], a.category)
            assertFalse(a.hidden)
            assertEquals("cw_" + a.id, a.pgsKey)
        }
        assertEquals(50, expected.sumOf { it[3] as Int })
        // Nazwy unikalne w każdym języku (LU-AC-GAB), opisy także.
        for (lang in L.CODES.indices) {
            val names = Achievements.all.map { L.text("ach_" + it.id, lang) }
            assertEquals(L.CODES[lang], names.size, names.toSet().size)
            val descs = Achievements.all.map { L.text("ach_" + it.id + "_d", lang) }
            assertEquals(L.CODES[lang], descs.size, descs.toSet().size)
        }
    }

    @Test
    fun firstEvolveAndTurretUnlockAfterAnyBattle() {
        val p = profile()
        val cfg = BattleConfig(Mode.SKIRMISH, 31L, 1f)
        val b = finished(cfg, false)
        b.player.erasAdvanced = 1
        b.player.turretsBuilt = 1
        val earned = ids(Achievements.onBattleEnd(p, b, cfg, false))
        assertTrue("first_evolve" in earned)
        assertTrue("first_turret" in earned)
        assertFalse("evolutions_50" in earned)
        assertFalse("turrets_50" in earned)
    }

    @Test
    fun firstResearchUnlocksOnFirstPurchaseAndStagesThreeOnThirdStage() {
        val p = profile()
        val def = Profile.research.first { it.key == "r_income" }
        p.levels[def.key] = 1
        val earned = ids(Achievements.onResearchBought(p, def))
        assertEquals(setOf("first_research"), earned)
        p.stars[0] = 3
        p.stars[1] = 1
        val cfg = BattleConfig(Mode.SKIRMISH, 32L, 1f)
        assertFalse("stages_3" in ids(Achievements.onBattleEnd(p, finished(cfg, false), cfg, false)))
        p.stars[2] = 2
        val cfg2 = BattleConfig(Mode.SKIRMISH, 33L, 1f)
        assertTrue("stages_3" in ids(Achievements.onBattleEnd(p, finished(cfg2, false), cfg2, false)))
    }
}
