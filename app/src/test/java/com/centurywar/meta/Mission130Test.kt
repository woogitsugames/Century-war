package com.centurywar.meta

import com.centurywar.engine.L
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.Doctrine
import com.centurywar.game.EndlessCard
import com.centurywar.game.Mode
import com.centurywar.game.Modifier
import com.centurywar.game.Weekly
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

class Mission130Test {
    private val now = LocalDate.of(2026, 11, 1)
        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun profile() = Profile(File.createTempFile("cw-mission-130-", ".json").apply {
        delete()
        deleteOnExit()
    })

    private fun missions(): Missions = Missions(profile()).apply { refresh(now) }

    private fun set(m: Missions, slot: Int, type: MissionType, target: Int = 10): MissionItem {
        val item = MissionItem("test_${type.name}", type, target, 30)
        if (slot < 3) m.state.daily[slot] = item else m.state.weekly[slot - 3] = item
        return item
    }

    private fun battle(cfg: BattleConfig, won: Boolean = false): Battle =
        Battle(cfg).apply {
            if (won) winner = 0
            else winner = 1
        }

    @Test fun allSevenTypesHaveLocalizedLabelsAndCanBeStored() {
        val types = listOf(MissionType.MODIFIER_BATTLES, MissionType.DOUBLE_MOD_WINS,
            MissionType.UNTOUCHED_BASE_WINS, MissionType.DOCTRINE_WINS,
            MissionType.CARD_VARIETY, MissionType.WEEKLY_BRONZE,
            MissionType.ADVANCED_BATTLES)
        assertEquals(7, types.size)
        val originalLang = L.lang
        try {
            for (type in types) {
                assertTrue(type.dailyGoal > 0)
                assertTrue(type.weeklyGoal > 0)
                val key = "ms_" + type.name.lowercase(Locale.ROOT)
                assertEquals(10, L.raw(key)?.size)
                for (lang in L.CODES.indices) {
                    L.lang = lang
                    val label = MissionTexts.label(MissionItem(type.name, type, 2, 10))
                    assertTrue("$key/${L.CODES[lang]}: $label", "2" in label)
                }
                val item = MissionItem("d_20760_${type.name}", type, 2, 12, progress = 1)
                val decoded = MissionItem.fromJson(item.toJson())!!
                assertEquals(type, decoded.type)
                assertEquals(1, decoded.progress)
                assertEquals(item.id, decoded.id)
            }
        } finally {
            L.lang = originalLang
        }
    }

    @Test fun modifierGoalsCountOnlyMatchingBattlesAndWins() {
        val m = missions()
        val played = set(m, 0, MissionType.MODIFIER_BATTLES)
        val wins = set(m, 1, MissionType.DOUBLE_MOD_WINS)
        val cfgNone = BattleConfig(Mode.SKIRMISH, 11L, 1f)
        m.onBattleEnd(battle(cfgNone, true), now, persist = false)
        assertEquals(0, played.progress)
        assertEquals(0, wins.progress)

        val one = BattleConfig(Mode.SKIRMISH, 12L, 1f,
            modifiers = setOf(Modifier.DOUBLE_INCOME))
        m.onBattleEnd(battle(one, true), now, persist = false)
        assertEquals(1, played.progress)
        assertEquals(0, wins.progress)

        val two = BattleConfig(Mode.SKIRMISH, 13L, 1f,
            modifiers = setOf(Modifier.DOUBLE_INCOME, Modifier.FAST_UNITS))
        m.onBattleEnd(battle(two, false), now, persist = false)
        assertEquals(2, played.progress)
        assertEquals(0, wins.progress)
        m.onBattleEnd(battle(two, true), now, persist = false)
        assertEquals(3, played.progress)
        assertEquals(1, wins.progress)
    }

    @Test fun untouchedBaseAndDoctrineWinsRequireRealWinConditions() {
        val m = missions()
        val perfect = set(m, 0, MissionType.UNTOUCHED_BASE_WINS)
        val doctrine = set(m, 1, MissionType.DOCTRINE_WINS)
        val progressed = set(m, 2, MissionType.ADVANCED_BATTLES)
        val cfg = BattleConfig(Mode.SKIRMISH, 14L, 1f)
        val loss = battle(cfg)
        loss.player.doctrines.add(Doctrine.ECONOMY)
        loss.player.erasAdvanced = 1
        m.onBattleEnd(loss, now, persist = false)
        assertEquals(0, perfect.progress)
        assertEquals(0, doctrine.progress)
        assertEquals(1, progressed.progress)

        val damaged = battle(cfg, true)
        damaged.player.baseDamageTaken = 2f
        m.onBattleEnd(damaged, now, persist = false)
        assertEquals(0, perfect.progress)
        assertEquals(0, doctrine.progress)

        val intact = battle(cfg, true)
        intact.player.doctrines.add(Doctrine.SIEGE)
        m.onBattleEnd(intact, now, persist = false)
        assertEquals(1, perfect.progress)
        assertEquals(1, doctrine.progress)
        assertEquals(1, progressed.progress)
    }

    @Test fun cardVarietyCountsUniquePicksAndOnlyInEndless() {
        val m = missions()
        val cards = set(m, 0, MissionType.CARD_VARIETY)
        val skirmish = battle(BattleConfig(Mode.SKIRMISH, 16L, 1f))
        skirmish.player.cardStacks[EndlessCard.FIELD_MEDICS.ordinal] = 1
        m.onBattleEnd(skirmish, now, persist = false)
        assertEquals(0, cards.progress)

        val endless = battle(BattleConfig(Mode.ENDLESS, 17L, 1f))
        endless.player.cardStacks[EndlessCard.FIELD_MEDICS.ordinal] = 3
        endless.player.cardStacks[EndlessCard.FINAL_SPARK.ordinal] = 1
        m.onBattleEnd(endless, now, persist = false)
        assertEquals(2, cards.progress)
        val next = battle(BattleConfig(Mode.ENDLESS, 18L, 1f))
        next.player.cardStacks[EndlessCard.FIELD_MEDICS.ordinal] = 2
        m.onBattleEnd(next, now, persist = false)
        assertEquals(3, cards.progress)
    }

    @Test fun weeklyBronzeRequiresCurrentWeekAndTenWaves() {
        val m = missions()
        val bronze = set(m, 3, MissionType.WEEKLY_BRONZE)
        val week = m.state.weeklyWeek
        val wrong = battle(BattleConfig(Mode.ENDLESS, 19L, 1f, weekly = week - 1))
        wrong.wave = 50
        m.onBattleEnd(wrong, now, persist = false)
        assertEquals(0, bronze.progress)
        val regular = battle(BattleConfig(Mode.ENDLESS, 20L, 1f))
        regular.wave = 50
        m.onBattleEnd(regular, now, persist = false)
        assertEquals(0, bronze.progress)

        val below = battle(BattleConfig(Mode.ENDLESS, 21L, 1f, weekly = week))
        below.wave = Weekly.threshold(0) - 1
        m.onBattleEnd(below, now, persist = false)
        assertEquals(0, bronze.progress)
        val valid = battle(BattleConfig(Mode.ENDLESS, 22L, 1f, weekly = week))
        valid.wave = Weekly.threshold(0)
        m.onBattleEnd(valid, now, persist = false)
        assertEquals(1, bronze.progress)
    }

    @Test fun everyNewTypeAppearsInDeterministicRotationForBeginner() {
        val m = missions()
        val required = setOf(MissionType.MODIFIER_BATTLES, MissionType.DOUBLE_MOD_WINS,
            MissionType.UNTOUCHED_BASE_WINS, MissionType.DOCTRINE_WINS,
            MissionType.CARD_VARIETY, MissionType.WEEKLY_BRONZE,
            MissionType.ADVANCED_BATTLES)
        val seen = mutableSetOf<MissionType>()
        for (i in 0..180) {
            m.refresh(now + i * Missions.DAY_MILLIS)
            for (item in m.state.daily) if (item.type in required) seen.add(item.type)
            for (item in m.state.weekly) if (item.type in required) seen.add(item.type)
            if (seen.containsAll(required)) break
        }
        assertTrue("Nowe misje muszą być dostępne od początku: $seen",
            seen.containsAll(required))
    }
}
