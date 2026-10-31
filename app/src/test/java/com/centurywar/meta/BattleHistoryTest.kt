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
}
