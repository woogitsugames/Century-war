package com.centurywar.meta

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.centurywar.engine.L
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.Campaign
import com.centurywar.game.DailyEvent
import com.centurywar.game.Doctrine
import com.centurywar.game.EndlessCard
import com.centurywar.game.Mode
import com.centurywar.game.Modifier
import com.centurywar.game.Weekly
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Achievement130Test {
    private fun profileFile(): File = File.createTempFile("cw-ach-130-", ".json").apply {
        delete()
        deleteOnExit()
    }

    private fun finished(cfg: BattleConfig, won: Boolean, wave: Int = 0): Battle {
        val battle = Battle(cfg)
        battle.wave = wave
        if (won) {
            battle.enemy.baseHp = 0f
            battle.step()
        } else battle.surrender()
        assertTrue(battle.over)
        return battle
    }

    private fun record(profile: Profile, battle: Battle, tracker: AchievementBattleTracker? = null): Set<String> =
        Achievements.onBattleEnd(profile, battle, battle.cfg, battle.winner == 0,
            events = tracker).map { it.id }.toSet()

    @Test fun allNewDefinitionsHaveTenTranslationsAndUniqueKeys() {
        val ids = listOf("card_medics_30", "card_spark_30", "card_guard_30", "card_recall_30",
            "card_new_50", "weekly_rules_3", "weekly_rules_6", "hard_perfect",
            "daily_events_6", "modifier_victories_10", "doctrine_victories_13")
        assertEquals(11, ids.size)
        assertEquals(66, Achievements.all.size)
        for (id in ids) {
            assertEquals("cw_" + id, Achievements.byId(id)?.pgsKey)
            assertEquals(10, L.raw("ach_" + id)?.size)
            assertEquals(10, L.raw("ach_" + id + "_d")?.size)
        }
    }

    @Test fun newCardsRequireTheSpecificCardAndWaveThreshold() {
        val p = Profile(profileFile())
        val cards = listOf(EndlessCard.FIELD_MEDICS, EndlessCard.FINAL_SPARK,
            EndlessCard.FIRST_GUARD, EndlessCard.BOSS_RECALL)
        val ids = listOf("card_medics_30", "card_spark_30",
            "card_guard_30", "card_recall_30")
        for (i in cards.indices) {
            val low = finished(BattleConfig(Mode.ENDLESS, 100L + i, 1f), false, 29)
            low.player.cardStacks[cards[i].ordinal] = 1
            assertFalse(ids[i] in record(p, low))
            val high = Battle(BattleConfig(Mode.ENDLESS, 200L + i, 1f))
            high.player.cardStacks[cards[i].ordinal] = 1
            val tracker = AchievementBattleTracker(high)
            assertEquals(1 shl i, tracker.newCardMask)
            high.wave = 30
            high.surrender()
            assertTrue(ids[i] in record(p, high, tracker))
            val def = Achievements.byId(ids[i])!!
            assertEquals(1L, Achievements.progress(p, def))
        }
        val fifty = finished(BattleConfig(Mode.ENDLESS, 300L, 1f), false, 50)
        assertFalse("card_new_50" in record(p, fifty))
        val withCard = finished(BattleConfig(Mode.ENDLESS, 301L, 1f), false, 50)
        withCard.player.cardStacks[EndlessCard.FIELD_MEDICS.ordinal] = 1
        assertTrue("card_new_50" in record(p, withCard))
        assertFalse("card_new_50" in record(p, withCard))
    }

    @Test fun thematicWeeklyRulesCountDistinctSetsNotRepeatedAttempts() {
        val p = Profile(profileFile())
        val start = Weekly.firstRuleSetWeek()
        for (i in 0 until 6) {
            val cfg = Weekly.config(start + i * Weekly.RULE_SET_EVERY)
            val b = finished(cfg, false, Weekly.threshold(1))
            record(p, b)
            val count = Achievements.progress(p, Achievements.byId("weekly_rules_6")!!)
            assertEquals((i + 1).toLong(), count)
            if (i == 2) assertTrue(Achievements.isUnlocked(p, "weekly_rules_3"))
            if (i < 5) assertFalse(Achievements.isUnlocked(p, "weekly_rules_6"))
            record(p, finished(cfg, false, Weekly.threshold(1)))
            assertEquals(count, Achievements.progress(p, Achievements.byId("weekly_rules_6")!!))
        }
        assertTrue(Achievements.isUnlocked(p, "weekly_rules_6"))
        val normal = finished(Weekly.config(start + 1L), false, 99)
        record(p, normal)
        assertEquals(6L, Achievements.progress(p, Achievements.byId("weekly_rules_6")!!))
    }

    @Test fun hardPerfectRequiresCampaignWinHardStageAndUntouchedBase() {
        val p = Profile(profileFile())
        val hardStage = Campaign.stages.first { it.aiPower >= 1.2f }
        val cfg = Campaign.config(hardStage, p.bonuses(), 400L)
        val loss = finished(cfg, false)
        record(p, loss)
        assertFalse(Achievements.isUnlocked(p, "hard_perfect"))

        val damaged = finished(Campaign.config(hardStage, p.bonuses(), 401L), true)
        damaged.player.baseDamageTaken = 2f
        record(p, damaged)
        assertFalse(Achievements.isUnlocked(p, "hard_perfect"))

        val easy = finished(BattleConfig(Mode.CAMPAIGN, 402L, 1f, stageIndex = 0), true)
        record(p, easy)
        assertFalse(Achievements.isUnlocked(p, "hard_perfect"))

        val perfect = finished(Campaign.config(hardStage, p.bonuses(), 403L), true)
        assertTrue("hard_perfect" in record(p, perfect))
    }

    @Test fun dailyEventsModifiersAndWinningDoctrinesUseDistinctBitMasks() {
        val p = Profile(profileFile())
        for ((i, event) in DailyEvent.values().withIndex()) {
            record(p, finished(BattleConfig(Mode.DAILY, 500L + i, 1f, dailyEvent = event), true))
        }
        assertEquals(6L, Achievements.progress(p, Achievements.byId("daily_events_6")!!))
        assertTrue(Achievements.isUnlocked(p, "daily_events_6"))

        for ((i, modifier) in Modifier.values().take(10).withIndex()) {
            record(p, finished(BattleConfig(Mode.SKIRMISH, 600L + i, 1f,
                modifiers = setOf(modifier)), true))
        }
        assertEquals(10L, Achievements.progress(p, Achievements.byId("modifier_victories_10")!!))

        for ((i, doctrine) in Doctrine.values().withIndex()) {
            val cfg = BattleConfig(Mode.SKIRMISH, 700L + i, 1f)
            val b = Battle(cfg)
            b.player.doctrines.add(doctrine)
            b.surrender()
            record(p, b)
        }
        assertEquals(0L, Achievements.progress(p, Achievements.byId("doctrine_victories_13")!!))
        for ((i, doctrine) in Doctrine.values().withIndex()) {
            val cfg = BattleConfig(Mode.SKIRMISH, 800L + i, 1f)
            val b = Battle(cfg)
            b.player.doctrines.add(doctrine)
            b.enemy.baseHp = 0f
            b.step()
            record(p, b)
        }
        assertEquals(Doctrine.values().size.toLong(),
            Achievements.progress(p, Achievements.byId("doctrine_victories_13")!!))
        assertTrue(Achievements.isUnlocked(p, "doctrine_victories_13"))
    }

    @Test fun masksAreClampedOnLoadAndAbsentFieldsDefaultToZero() {
        val file = profileFile()
        val p = Profile(file)
        for (metric in listOf(AchievementMetric.WEEKLY_RULESETS,
            AchievementMetric.DAILY_EVENTS, AchievementMetric.MODIFIER_VICTORIES,
            AchievementMetric.DOCTRINE_VICTORIES, AchievementMetric.CARD_NEW_50,
            AchievementMetric.HARD_PERFECT)) {
            p.achievementCounters[metric.name] = Long.MAX_VALUE
        }
        p.save()
        Profile.awaitHintWrites()
        val restored = Profile(file).apply { load() }
        assertEquals(63L, restored.achievementCounters[AchievementMetric.WEEKLY_RULESETS.name])
        assertEquals(63L, restored.achievementCounters[AchievementMetric.DAILY_EVENTS.name])
        assertEquals((1L shl Modifier.values().size) - 1L,
            restored.achievementCounters[AchievementMetric.MODIFIER_VICTORIES.name])
        assertEquals((1L shl Doctrine.values().size) - 1L,
            restored.achievementCounters[AchievementMetric.DOCTRINE_VICTORIES.name])
        assertEquals(1L, restored.achievementCounters[AchievementMetric.CARD_NEW_50.name])

        val oldJson = JSONObject(file.readText()).apply { remove("achievementCounters") }
        file.writeText(oldJson.toString())
        val old = Profile(file).apply { load() }
        assertEquals(0L, Achievements.progress(old, Achievements.byId("weekly_rules_6")!!))
        assertEquals(0L, Achievements.progress(old, Achievements.byId("daily_events_6")!!))
    }
}
