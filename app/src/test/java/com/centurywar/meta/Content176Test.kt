package com.centurywar.meta

import com.centurywar.engine.Icons
import com.centurywar.engine.L
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.Campaign
import com.centurywar.game.Defs
import com.centurywar.game.Fighter
import com.centurywar.game.Mode
import com.centurywar.game.Modifier
import com.centurywar.game.Replay
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

/** Zlecenie 176: osiągnięcia i misje dowódcy oraz stopni wież, bez zmian w symulacji. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Content176Test {
    private val now = LocalDate.of(2026, 11, 3)
        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private val newIds = listOf("commander_first", "commander_survivor", "commander_pulses_50",
        "commander_wins_10", "turret_tier_four", "late_no_turrets")
    private val newMissions = listOf(MissionType.COMMANDER_SUMMONS, MissionType.COMMANDER_WINS,
        MissionType.TURRET_TIERS, MissionType.TOP_TIER_WINS)

    private fun profileFile(): File = File.createTempFile("cw-176-", ".json").apply {
        delete()
        deleteOnExit()
    }

    private fun skirmish(seed: Long) = Battle(BattleConfig(Mode.SKIRMISH, seed, 1f))

    private fun win(b: Battle) {
        b.enemy.baseHp = 0f
        b.step()
        assertTrue(b.over)
        assertEquals(0, b.winner)
    }

    private fun summon(b: Battle): Fighter {
        b.player.gold = 100_000f
        assertTrue(b.summonCommander(b.player))
        return b.units.first { it.active && it.side == 0 && it.isCommander }
    }

    private fun record(p: Profile, b: Battle, tracker: AchievementBattleTracker? = null): Set<String> =
        Achievements.onBattleEnd(p, b, b.cfg, b.winner == 0, events = tracker).map { it.id }.toSet()

    @Test fun definitionsHaveKeysTranslationsAndRewardsInExistingScale() {
        assertEquals(79, Achievements.all.size)
        val rewards = Achievements.all.map { it.medalReward }
        for (id in newIds) {
            val def = Achievements.byId(id)!!
            assertEquals("cw_$id", def.pgsKey)
            assertTrue(def.medalReward in rewards.minOrNull()!!..rewards.maxOrNull()!!)
            assertTrue(def.medalReward in 40..200)
            for (key in listOf("ach_$id", "ach_${id}_d")) {
                val texts = L.raw(key)
                assertEquals(key, 10, texts?.size)
                assertTrue(key, texts!!.all { it.isNotBlank() })
            }
        }
        // Nowe metryki są dopisane na końcu enumu (zapis po nazwie i kolejność w JSON).
        val metrics = AchievementMetric.values().map { it.name }
        assertEquals(listOf("COMMANDER_SUMMONED", "COMMANDER_SURVIVOR", "COMMANDER_PULSES",
            "COMMANDER_WINS", "TURRET_TIER_FOUR", "LATE_NO_TURRET_WINS"), metrics.takeLast(6))
        for (key in listOf("cx176_tiers", "cx176_tiers_d", "cx176_commander_tips",
            "cx176_commander_tips_d", "wn_176_content")) assertEquals(key, 10, L.raw(key)?.size)
        assertTrue("wn_176_content" in WhatsNewCatalog.releases.first().textKeys)
        assertNotEquals(Icons.TOWER_TIERS, Icons.COMMANDER_PULSE)
    }

    @Test fun firstCommanderAndSurvivorNeedTheTrackerAndAWin() {
        val p = Profile(profileFile())
        val b = skirmish(9001L)
        val tracker = AchievementBattleTracker(b)
        assertFalse(tracker.commanderSummoned)
        summon(b)
        tracker.afterStep()
        assertTrue(tracker.commanderSummoned)
        assertTrue(tracker.commanderSurvived)
        win(b)
        val unlocked = record(p, b, tracker)
        assertTrue("commander_first" in unlocked)
        assertTrue("commander_survivor" in unlocked)
        assertEquals(1L, Achievements.progress(p, Achievements.byId("commander_wins_10")!!))

        // Przegrana z żywym dowódcą nie liczy się do wygranych ani przetrwania.
        val p2 = Profile(profileFile())
        val loss = skirmish(9002L)
        val t2 = AchievementBattleTracker(loss)
        summon(loss)
        t2.afterStep()
        loss.surrender()
        val lost = record(p2, loss, t2)
        assertTrue("commander_first" in lost)
        assertFalse("commander_survivor" in lost)
        assertEquals(0L, Achievements.progress(p2, Achievements.byId("commander_wins_10")!!))
    }

    @Test fun commanderDeathBlocksSurvivorButCountsTheWin() {
        val p = Profile(profileFile())
        val b = skirmish(9003L)
        val tracker = AchievementBattleTracker(b)
        summon(b)
        tracker.afterStep()
        // Ten sam zapis co w symulacji przy śmierci: odnowienie zamiast -1.
        b.player.commanderCd = Battle.COMMANDER_COOLDOWN
        tracker.afterStep()
        assertTrue(tracker.commanderSummoned)
        assertFalse(tracker.commanderSurvived)
        win(b)
        val unlocked = record(p, b, tracker)
        assertTrue("commander_first" in unlocked)
        assertFalse("commander_survivor" in unlocked)
        assertEquals(1L, Achievements.progress(p, Achievements.byId("commander_wins_10")!!))

        // Bez dowódcy żadne z nowych osiągnięć dowódcy nie rusza.
        val plain = skirmish(9004L)
        val t2 = AchievementBattleTracker(plain)
        win(plain)
        val none = record(Profile(profileFile()), plain, t2)
        assertTrue(none.none { it.startsWith("commander_") })
    }

    @Test fun commanderUnavailableBeforeStageEightNeverCounts() {
        val p = Profile(profileFile())
        val cfg = Campaign.config(Campaign.stages[3], p.bonuses(), 9005L)
        val b = Battle(cfg)
        assertFalse(b.commanderAvailable)
        val tracker = AchievementBattleTracker(b)
        b.player.gold = 100_000f
        assertFalse(b.summonCommander(b.player))
        tracker.afterStep()
        win(b)
        assertTrue(record(p, b, tracker).none { it.startsWith("commander_") })
    }

    @Test fun pulsesAreCountedFromSkillCooldownResets() {
        val p = Profile(profileFile())
        p.achievementCounters[AchievementMetric.COMMANDER_PULSES.name] = 47L
        val b = skirmish(9006L)
        val tracker = AchievementBattleTracker(b)
        val c = summon(b)
        tracker.afterStep()
        for (i in 0 until 3) {
            c.commanderSkillCd = 0f
            tracker.afterStep()
            c.commanderSkillCd = Battle.COMMANDER_SKILL_COOLDOWN
            tracker.afterStep()
            // Zwykłe odliczanie nie jest Impulsem.
            c.commanderSkillCd -= Battle.STEP
            tracker.afterStep()
        }
        assertEquals(3, tracker.commanderPulses)
        b.surrender()
        assertTrue("commander_pulses_50" in record(p, b, tracker))
        assertEquals(50L, Achievements.progress(p, Achievements.byId("commander_pulses_50")!!))
    }

    @Test fun realSimulationPulseIsSeenByTracker() {
        val b = skirmish(9007L)
        val tracker = AchievementBattleTracker(b)
        val c = summon(b)
        // Wróg tuż przed dowódcą: pierwszy krok wyzwala Impuls (odnowienie 20 s).
        b.enemy.gold = 100_000f
        var steps = 0
        while (tracker.commanderPulses == 0 && steps < 60 * 240 && !b.over) {
            b.step()
            tracker.afterStep()
            steps++
        }
        if (c.isCommander && tracker.commanderPulses > 0)
            assertTrue(c.commanderSkillCd <= Battle.COMMANDER_SKILL_COOLDOWN)
        assertTrue("Impuls w prawdziwej bitwie", tracker.commanderPulses > 0 || b.over)
    }

    @Test fun fourTopTierTurretsAtOnce() {
        val p = Profile(profileFile())
        val b = skirmish(9008L)
        val tracker = AchievementBattleTracker(b)
        b.player.slots = Defs.MAX_TURRET_SLOTS
        for (i in 0 until 3) { b.player.turretEra[i] = 0; b.player.turretTier[i] = 3 }
        b.player.turretEra[3] = 0; b.player.turretTier[3] = 2
        tracker.afterStep()
        assertFalse(tracker.turretTierFour)
        b.player.turretTier[3] = 3
        tracker.afterStep()
        assertTrue(tracker.turretTierFour)
        // Późniejsza sprzedaż nie cofa chwili, w której wszystkie cztery były na stopniu 3.
        b.player.turretEra[3] = -1; b.player.turretTier[3] = 0
        tracker.afterStep()
        b.surrender()
        assertTrue("turret_tier_four" in record(p, b, tracker))

        // Bez trackera liczy się stan końcowy.
        val p2 = Profile(profileFile())
        val end = skirmish(9009L)
        end.player.slots = Defs.MAX_TURRET_SLOTS
        for (i in 0 until Defs.MAX_TURRET_SLOTS) { end.player.turretEra[i] = 1; end.player.turretTier[i] = 3 }
        end.surrender()
        assertTrue("turret_tier_four" in record(p2, end))
    }

    @Test fun lateCampaignWinWithoutTurrets() {
        val p = Profile(profileFile())
        val early = Battle(Campaign.config(Campaign.stages[Achievements.LATE_STAGE_INDEX - 1], p.bonuses(), 9010L))
        win(early)
        assertFalse("late_no_turrets" in record(p, early))

        val built = Battle(Campaign.config(Campaign.stages[Achievements.LATE_STAGE_INDEX], p.bonuses(), 9011L))
        win(built)
        built.player.turretsBuilt = 1
        assertFalse("late_no_turrets" in record(p, built))

        val loss = Battle(Campaign.config(Campaign.stages[Achievements.LATE_STAGE_INDEX], p.bonuses(), 9012L))
        loss.surrender()
        assertFalse("late_no_turrets" in record(p, loss))

        val clean = Battle(Campaign.config(Campaign.stages[Achievements.LATE_STAGE_INDEX + 2], p.bonuses(), 9013L))
        assertEquals(0, clean.player.turretsBuilt)
        win(clean)
        assertTrue("late_no_turrets" in record(p, clean))

        val skirmishWin = skirmish(9014L)
        win(skirmishWin)
        assertFalse("late_no_turrets" in record(Profile(profileFile()), skirmishWin))
        assertFalse(Modifier.NO_TURRETS in clean.cfg.modifiers)
    }

    @Test fun newCountersSurviveSaveAndAreClampedOnLoad() {
        val file = profileFile()
        val p = Profile(file)
        val b = skirmish(9015L)
        val tracker = AchievementBattleTracker(b)
        summon(b)
        tracker.afterStep()
        win(b)
        record(p, b, tracker)
        p.achievementCounters[AchievementMetric.COMMANDER_PULSES.name] = 12L
        p.achievementCounters[AchievementMetric.TURRET_TIER_FOUR.name] = Long.MAX_VALUE
        p.achievementCounters[AchievementMetric.LATE_NO_TURRET_WINS.name] = 7L
        p.save()
        Profile.awaitHintWrites()
        val restored = Profile(file).apply { load() }
        assertTrue(Achievements.isUnlocked(restored, "commander_first"))
        assertTrue(Achievements.isUnlocked(restored, "commander_survivor"))
        assertEquals(1L, restored.achievementCounters[AchievementMetric.COMMANDER_SUMMONED.name])
        assertEquals(1L, restored.achievementCounters[AchievementMetric.COMMANDER_WINS.name])
        assertEquals(12L, restored.achievementCounters[AchievementMetric.COMMANDER_PULSES.name])
        assertEquals(1L, restored.achievementCounters[AchievementMetric.TURRET_TIER_FOUR.name])
        assertEquals(1L, restored.achievementCounters[AchievementMetric.LATE_NO_TURRET_WINS.name])

        // Stary profil bez nowych pól: zero postępu, bez błędu odczytu.
        val json = JSONObject(file.readText())
        json.getJSONObject("achievementCounters").apply {
            remove(AchievementMetric.COMMANDER_PULSES.name)
            remove(AchievementMetric.COMMANDER_WINS.name)
        }
        file.writeText(json.toString())
        val old = Profile(file).apply { load() }
        assertEquals(0L, Achievements.progress(old, Achievements.byId("commander_pulses_50")!!))
        assertEquals(0L, Achievements.progress(old, Achievements.byId("commander_wins_10")!!))
    }

    // ------------------------------------------------------------------ misje

    private fun missions(p: Profile = Profile(profileFile())): Missions = Missions(p).apply { refresh(now) }

    private fun set(m: Missions, slot: Int, type: MissionType, target: Int = 10): MissionItem {
        val item = MissionItem("test_${type.name}", type, target, 30)
        if (slot < 3) m.state.daily[slot] = item else m.state.weekly[slot - 3] = item
        return item
    }

    private fun log(vararg kinds: Replay.Kind) =
        Replay.Log(100, kinds.mapIndexed { i, k -> Replay.Command(i, k, 0) })

    @Test fun missionTypesAreAppendedLocalizedAndStored() {
        val names = MissionType.values().map { it.name }
        assertEquals(newMissions.map { it.name }, names.takeLast(4))
        val originalLang = L.lang
        try {
            for (type in newMissions) {
                val key = "ms_" + type.name.lowercase(Locale.ROOT)
                assertEquals(key, 10, L.raw(key)?.size)
                for (lang in L.CODES.indices) {
                    L.lang = lang
                    val label = MissionTexts.label(MissionItem(type.name, type, 2, 10))
                    assertTrue("$key/${L.CODES[lang]}: $label", "2" in label)
                }
                val item = MissionItem("w_2900_${type.name}", type, 4, 40, progress = 2)
                val decoded = MissionItem.fromJson(item.toJson())!!
                assertEquals(type, decoded.type)
                assertEquals(2, decoded.progress)
            }
        } finally {
            L.lang = originalLang
        }
    }

    @Test fun commanderMissionsCountAcceptedSummonsAndWins() {
        val m = missions()
        val summons = set(m, 0, MissionType.COMMANDER_SUMMONS)
        val wins = set(m, 1, MissionType.COMMANDER_WINS)
        val tiers = set(m, 2, MissionType.TURRET_TIERS)
        val lost = skirmish(9100L).apply { winner = 1 }
        m.onBattleEnd(lost, now, persist = false,
            playerCommands = log(Replay.Kind.SUMMON_COMMANDER, Replay.Kind.SUMMON_COMMANDER, Replay.Kind.QUEUE))
        assertEquals(2, summons.progress)
        assertEquals(0, wins.progress)
        assertEquals(0, tiers.progress)

        val wonPlain = skirmish(9101L).apply { winner = 0 }
        m.onBattleEnd(wonPlain, now, persist = false, playerCommands = log(Replay.Kind.QUEUE))
        assertEquals(0, wins.progress)

        val wonCommander = skirmish(9102L).apply { winner = 0 }
        m.onBattleEnd(wonCommander, now, persist = false,
            playerCommands = log(Replay.Kind.SUMMON_COMMANDER, Replay.Kind.TURRET_TIER, Replay.Kind.TURRET_TIER))
        assertEquals(3, summons.progress)
        assertEquals(1, wins.progress)
        assertEquals(2, tiers.progress)
    }

    @Test fun topTierWinsNeedAStandingTierThreeTurret() {
        val m = missions()
        val top = set(m, 3, MissionType.TOP_TIER_WINS)
        val low = skirmish(9103L).apply { winner = 0 }
        low.player.turretEra[0] = 0; low.player.turretTier[0] = 2
        m.onBattleEnd(low, now, persist = false)
        assertEquals(0, top.progress)

        val lostTop = skirmish(9104L).apply { winner = 1 }
        lostTop.player.turretEra[0] = 0; lostTop.player.turretTier[0] = 3
        m.onBattleEnd(lostTop, now, persist = false)
        assertEquals(0, top.progress)

        val wonTop = skirmish(9105L).apply { winner = 0 }
        wonTop.player.turretEra[1] = 2; wonTop.player.turretTier[1] = 3
        m.onBattleEnd(wonTop, now, persist = false)
        assertEquals(1, top.progress)
    }

    @Test fun newMissionsAppearOnlyAfterTheirMechanicsUnlock() {
        val fresh = Profile(profileFile())
        val seen = HashSet<MissionType>()
        for (d in 0 until 120) {
            val state = Missions.refresh(fresh, now + d * Missions.DAY_MILLIS)
            state.daily.forEach { seen.add(it.type) }
            state.weekly.forEach { seen.add(it.type) }
        }
        assertTrue(seen.none { it in newMissions })

        val veteran = Profile(profileFile())
        for (i in 0..Achievements.LATE_STAGE_INDEX) veteran.stars[i] = 2
        val later = HashSet<MissionType>()
        for (d in 0 until 400) {
            val state = Missions.refresh(veteran, now + d * Missions.DAY_MILLIS)
            state.daily.forEach { later.add(it.type) }
            state.weekly.forEach { later.add(it.type) }
        }
        assertTrue(later.containsAll(newMissions))
    }

    @Test fun missionStateWithNewTypesSurvivesProfileReload() {
        val file = profileFile()
        val p = Profile(file)
        for (i in 0..Achievements.LATE_STAGE_INDEX) p.stars[i] = 1
        val m = missions(p)
        val item = set(m, 4, MissionType.COMMANDER_WINS, target = 4)
        val won = skirmish(9106L).apply { winner = 0 }
        m.onBattleEnd(won, now, persist = true, playerCommands = log(Replay.Kind.SUMMON_COMMANDER))
        assertEquals(1, item.progress)
        Profile.awaitHintWrites()
        val restored = Profile(file).apply { load() }
        val back = restored.missionState.weekly.firstOrNull { it.id == item.id }
        assertEquals(MissionType.COMMANDER_WINS, back?.type)
        assertEquals(1, back?.progress)
    }
}
