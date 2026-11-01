package com.centurywar.meta

import com.centurywar.game.AiPersonality
import com.centurywar.game.Doctrine
import com.centurywar.game.Mode
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Historia lokalna ma własny zapis, limit i walidację niezależne od profilu. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BattleHistoryTest {
    private fun file(): File = File(Files.createTempDirectory("cw-history").toFile(), "history.json")

    private fun entry(
        number: Int,
        mode: Mode = Mode.SKIRMISH,
        result: Int = BattleHistory.WIN,
        date: Long = 1_700_000_000_000L + number,
        roles: IntArray = intArrayOf(2, 5, 0, 1),
        doctrines: Int = 1 shl Doctrine.ECONOMY.ordinal,
        personality: Int = AiPersonality.ASSAULT.ordinal,
    ) = BattleHistory.Record(
        date = date,
        mode = mode.ordinal,
        result = result,
        score = number.toLong(),
        roles = roles,
        doctrines = doctrines,
        personality = personality,
        kills = 5,
        wave = number,
        cards = intArrayOf(2, 0, 1),
        medals = 12,
    )

    @Test
    fun writesAndReadsLatest100InOrder() {
        val f = file()
        val h = BattleHistory(f)
        for (i in 0..104) h.add(entry(i))
        h.awaitWrites()
        val restored = BattleHistory(f).also { it.load() }
        try {
            val all = restored.snapshot()
            assertEquals(BattleHistory.LIMIT, all.size)
            assertEquals(104L, all.first().score)
            assertEquals(5L, all.last().score)
            assertEquals(12, all.first().medals)
            assertEquals(2, all.first().cards[0])
            assertFalse(f.readText().contains("\"score\":0,"))
            assertTrue(f.exists())
        } finally {
            h.close()
            restored.close()
        }
    }

    @Test
    fun ignoresBadEntriesAndClampsOptionalValues() {
        val f = file()
        val array = JSONArray()
            .put(JSONObject().put("date", "not a date").put("mode", 0).put("result", 0))
            .put(JSONObject().put("date", 2L).put("mode", 500).put("result", 0))
            .put(JSONObject().put("date", 3L).put("mode", Mode.CAMPAIGN.ordinal).put("result", 1)
                .put("stars", 900).put("kills", -100).put("seconds", Long.MAX_VALUE)
                .put("stage", 20000).put("wave", -2).put("roles", JSONArray().put(-9).put(800_000_000L))
                .put("score", Long.MAX_VALUE).put("tip", "unknown-tip")
                .put("personality", 99).put("ai", -20))
            .put(JSONObject().put("date", 4L).put("mode", Mode.DAILY.ordinal).put("result", 0))
        f.writeText(JSONObject().put("v", 1).put("entries", array).toString())
        val h = BattleHistory(f)
        try {
            h.load()
            val loaded = h.snapshot()
            assertEquals(2, loaded.size)
            val e = loaded[0]
            assertEquals(3, e.stars)
            assertEquals(0, e.kills)
            assertEquals(100_000_000, e.seconds)
            assertEquals(10_000, e.stage)
            assertEquals(0, e.wave)
            assertArrayEquals(intArrayOf(0, 100_000_000, 0, 0), e.roles)
            assertEquals(10_000_000_000_000L, e.score)
            assertEquals(-1, e.personality)
            assertEquals(0, e.aiPower)
            assertEquals("", e.tipKey)
            assertArrayEquals(IntArray(4), loaded[1].roles)
            assertEquals(0, loaded[1].medals)
        } finally {
            h.close()
        }
    }

    @Test
    fun resettingHistoryPersistsAnEmptyList() {
        val f = file()
        val h = BattleHistory(f)
        h.add(entry(1))
        h.awaitWrites()
        h.clear()
        h.awaitWrites()
        val fresh = BattleHistory(f).also { it.load() }
        try { assertTrue(fresh.snapshot().isEmpty()) } finally { h.close(); fresh.close() }
    }

    @Test
    fun malformedFileAndUnsupportedVersionDoNotCrash() {
        val f = file()
        f.writeText("{\"v\":1,\"entries\":[broken")
        val h = BattleHistory(f)
        try {
            h.load()
            assertTrue(h.snapshot().isEmpty())
            f.writeText("""{"v":99,"entries":[{"date":1,"mode":0,"result":0}]}""")
            h.load()
            assertTrue(h.snapshot().isEmpty())
            f.writeText("""{"v":1,"entries":[null,11,{},{"date":8,"mode":3,"result":0}]}""")
            h.load()
            assertEquals(1, h.snapshot().size)
        } finally {
            h.close()
        }
    }

    @Test
    fun aggregationComputesWinRatesFavoriteRoleDoctrineAndCharts() {
        val now = 1_700_000_000_000L
        val list = listOf(
            entry(100, Mode.ENDLESS, BattleHistory.WIN, now,
                intArrayOf(1, 8, 0, 0), 1 shl Doctrine.BULWARK.ordinal,
                AiPersonality.BALANCED.ordinal),
            entry(80, Mode.SKIRMISH, BattleHistory.LOSS, now - 86_400_000L,
                intArrayOf(3, 6, 0, 0), 1 shl Doctrine.BULWARK.ordinal,
                AiPersonality.BALANCED.ordinal),
            entry(70, Mode.ENDLESS, BattleHistory.WIN, now - 2 * 86_400_000L,
                intArrayOf(0, 5, 0, 0), 1 shl Doctrine.ECONOMY.ordinal,
                AiPersonality.ASSAULT.ordinal),
        )
        val m = BattleHistory.aggregate(list, now)
        assertEquals(1, m.favoriteRole)
        assertEquals(Doctrine.BULWARK.ordinal, m.favoriteDoctrine)
        assertEquals(2, m.aiBattles[AiPersonality.BALANCED.ordinal])
        assertEquals(1, m.aiWins[AiPersonality.BALANCED.ordinal])
        assertEquals(50, BattleHistory.percent(m.aiWins[AiPersonality.BALANCED.ordinal],
            m.aiBattles[AiPersonality.BALANCED.ordinal]))
        assertEquals(0, BattleHistory.percent(1, 0))
        assertEquals(100, BattleHistory.percent(6, 5))
        assertEquals(1, m.days[13])
        assertEquals(1, m.dayWins[13])
        assertArrayEquals(longArrayOf(70, 100), m.endlessScores)
    }


    /** Rekordy z nieznanym trybem nie zostawiają w oknie 30 pustych pozycji (= wygranych kampanii). */
    @Test
    fun unknownModesAreFilteredBeforeTakingLast30() {
        val now = 1_800_000_000_000L
        val records = (0 until 40).map { index ->
            val base = entry(100 - index, Mode.SKIRMISH, BattleHistory.LOSS, now - index * 1_000L)
            if (index % 4 == 1) base.copy(mode = 99) else base
        }
        val metrics = BattleHistory.aggregate(records, now)
        val valid = records.filter { it.mode in Mode.entries.indices }.take(30)
        assertEquals(30, metrics.recentModes.size)
        assertArrayEquals(valid.asReversed().map { it.mode }.toIntArray(), metrics.recentModes)
        assertTrue(metrics.recentModes.none { it == Mode.CAMPAIGN.ordinal })
        assertTrue(metrics.recentResults.all { it == BattleHistory.LOSS })
        assertEquals(0, metrics.recentWinsByMode.sum())
        assertEquals(30, metrics.recentLossesByMode[Mode.SKIRMISH.ordinal])

        // Mniej niż 30 poprawnych: okno ma tylko tyle pozycji, ile poprawnych rekordów.
        val few = listOf(entry(1, Mode.SKIRMISH, BattleHistory.LOSS, now), entry(2).copy(mode = -1))
        val small = BattleHistory.aggregate(few, now)
        assertEquals(1, small.recentModes.size)
        assertEquals(0, small.recentWinsByMode.sum())
    }

    @Test
    fun last30OutcomesAreChronologicalAndGroupedByMode() {
        val now = 1_800_000_000_000L
        val records = (0 until 35).map { index ->
            val result = when (index % 3) {
                0 -> BattleHistory.WIN
                1 -> BattleHistory.LOSS
                else -> BattleHistory.SURRENDER
            }
            entry(100 - index, Mode.entries[index % 4], result, now - index * 1_000L)
        }
        val metrics = BattleHistory.aggregate(records, now)
        val last30 = records.take(30)
        assertEquals(30, metrics.recentModes.size)
        assertArrayEquals(last30.asReversed().map { it.mode }.toIntArray(), metrics.recentModes)
        assertArrayEquals(last30.asReversed().map {
            if (it.result == BattleHistory.WIN) BattleHistory.WIN else BattleHistory.LOSS
        }.toIntArray(), metrics.recentResults)
        for (mode in Mode.entries) {
            assertEquals(last30.count { it.mode == mode.ordinal && it.result == BattleHistory.WIN },
                metrics.recentWinsByMode[mode.ordinal])
            assertEquals(last30.count { it.mode == mode.ordinal && it.result != BattleHistory.WIN },
                metrics.recentLossesByMode[mode.ordinal])
        }
        assertEquals(30, metrics.recentWinsByMode.sum() + metrics.recentLossesByMode.sum())
        val empty = BattleHistory.aggregate(emptyList(), now)
        assertTrue(empty.recentModes.isEmpty())
        assertTrue(empty.recentResults.isEmpty())
        assertEquals(0, empty.recentWinsByMode.sum())
        assertEquals(0, empty.recentLossesByMode.sum())
    }

    @Test
    fun bestEndlessWaveAndDailyTimeUseLocalCalendarDays() {
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.of(2026, 11, 1)
        fun day(ago: Long): Long = today.minusDays(ago).atTime(12, 0)
            .atZone(zone).toInstant().toEpochMilli()
        val records = listOf(
            entry(6, Mode.ENDLESS, date = day(0)).copy(wave = 6, seconds = 90),
            entry(7, Mode.SKIRMISH, date = day(0)).copy(seconds = 30),
            entry(10, Mode.ENDLESS, date = day(1)).copy(wave = 10, seconds = 3600),
            entry(4, Mode.ENDLESS, date = day(13)).copy(wave = 4, seconds = 600),
            entry(3, Mode.CAMPAIGN, date = day(14)).copy(seconds = 999),
            entry(2, Mode.SKIRMISH, date = day(-1)).copy(seconds = 888),
            entry(1, Mode.DAILY, date = day(0)).copy(seconds = -100),
        )
        val metrics = BattleHistory.aggregate(records, day(0))
        assertArrayEquals(intArrayOf(4, 10, 10), metrics.endlessBestWaves)
        assertEquals(14, metrics.dailySeconds.size)
        assertEquals(600L, metrics.dailySeconds[0])
        assertEquals(3600L, metrics.dailySeconds[12])
        assertEquals(120L, metrics.dailySeconds[13])
        assertEquals(0L, metrics.dailySeconds[1])
        assertEquals(3, metrics.days[13])
        val empty = BattleHistory.aggregate(emptyList(), day(0))
        assertTrue(empty.endlessBestWaves.isEmpty())
        assertArrayEquals(LongArray(14), empty.dailySeconds)
    }

    @Test
    fun corruptHistoryLoadsInBackgroundAndSignalsCompletion() {
        val f = file()
        f.writeText("{\"v\":1,\"entries\":[broken")
        val history = BattleHistory(f)
        val complete = CountDownLatch(1)
        try {
            history.loadAsync { complete.countDown() }
            assertTrue(complete.await(5, TimeUnit.SECONDS))
            assertTrue(history.loaded)
            assertTrue(history.snapshot().isEmpty())
        } finally {
            history.close()
        }
    }

    @Test
    fun addingWhileHistoryLoadsNeverDiscardsNewRecord() {
        val f = file()
        val old = BattleHistory(f)
        old.add(entry(10))
        old.awaitWrites()
        old.close()
        val history = BattleHistory(f)
        val complete = CountDownLatch(1)
        try {
            history.loadAsync { complete.countDown() }
            history.add(entry(11))
            assertTrue(complete.await(5, TimeUnit.SECONDS))
            history.awaitWrites()
            assertEquals(listOf(11L, 10L), history.snapshot().map { it.score })
            val restored = BattleHistory(f).also { it.load() }
            try { assertEquals(listOf(11L, 10L), restored.snapshot().map { it.score }) }
            finally { restored.close() }
        } finally {
            history.close()
        }
    }
}
