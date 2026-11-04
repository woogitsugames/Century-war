package com.centurywar.crash

import com.centurywar.engine.Game
import com.centurywar.engine.GameModes
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
import com.centurywar.game.Mode
import com.centurywar.meta.BattleHistory
import com.centurywar.meta.PlaytestLog
import com.centurywar.meta.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

/** R2-04: raport testera bez danych osobowych, z wszystkimi sekcjami, w limicie długości. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaytestReportTextTest {
    private val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
    }

    private fun game(): Game {
        val dir = Files.createTempDirectory("cw-r204").toFile()
        val profile = Profile(File(dir, "profile.json"))
        profile.name = "Zażółć_Nick_7Q"
        profile.enableGlobalRanking()
        profile.tutorialDone = true
        val g = Game(host, profile, Quality.LOW).apply {
            density = 2f
            resize(1600, 720, 1600, 720)
        }
        g.playtest = PlaytestLog(File(dir, PlaytestLog.FILE_NAME)).also { it.load() }
        g.history = BattleHistory(File(dir, "history.json")).also { it.load() }
        return g
    }

    private fun build(g: Game): String =
        PlaytestReportText.build("1.0 (1)", "Acme Phone", "15 (API 35)", PlaytestReportText.collect(g))

    @Test
    fun noNicknameIdsOrSecretAndAllSections() {
        val g = game()
        val now = 1_790_000_000_000L
        g.playtestStarted(now)
        g.profile.recordBattle(Mode.CAMPAIGN, false, 95)
        g.playtest!!.battleSettled(true, false, true, 95L, true, now)
        g.profile.recordBattle(Mode.SKIRMISH, true, 300)
        g.playtest!!.battleSettled(false, true, false, 395L, true, now)
        g.playtest!!.perfSample(18.4f, 2, 5L, 1)
        repeat(30) { g.history!!.add(BattleHistory.Record(date = now, mode = Mode.ENDLESS.ordinal, wave = 12, seconds = 400)) }
        g.history!!.awaitWrites()

        val text = build(g)
        assertFalse("Bez nicku", text.contains("Zażółć_Nick_7Q"))
        assertFalse("Bez UUID", text.contains(g.profile.playerId))
        assertFalse("Bez sekretu", text.contains(g.profile.playerSecret))
        assertTrue(g.profile.playerId.isNotEmpty() && g.profile.playerSecret.isNotEmpty())
        assertFalse(text.contains("playerId"))
        assertFalse(text.contains("playerSecret"))
        assertTrue(text.length <= PlaytestReportText.MAX_CHARS)
        for (section in PlaytestReportText.SECTIONS) assertTrue(section, text.contains(section))
        assertTrue(text.contains("Version: 1.0 (1)"))
        assertTrue(text.contains("Device: Acme Phone"))
        assertTrue(text.contains("Android: 15 (API 35)"))
        assertTrue(text.contains("Surrenders: 1") || text.contains("surrenders: 1"))
        assertTrue(text.contains("worst p95 18.4 ms"))
        assertTrue(text.contains("Screens visited: -"))
        // Ostatnie bitwy: najwyżej 10 wierszy.
        assertEquals(10, Regex("\\n  ENDLESS, ").findAll(text).count())
    }

    @Test
    fun hugeValuesStayWithinLimit() {
        val g = game()
        g.profile.playSeconds = Long.MAX_VALUE / 2
        for (i in 0 until 7) {
            g.playtest!!.startSession(1_790_000_000_000L + i * 86_400_000L, 0L)
            g.playtest!!.perfSample(99999f, 99, Long.MAX_VALUE / 4, 9)
        }
        val text = build(g)
        assertTrue(text.length <= PlaytestReportText.MAX_CHARS)
        for (section in PlaytestReportText.SECTIONS) assertTrue(section, text.contains(section))
    }

    /** R10-04: wiersz trybu gry Androida (stała etykieta po angielsku). */
    @Test
    fun gameModeRow() {
        val g = game()
        assertTrue(build(g).contains("Game mode: unsupported\n"))
        g.applyGameMode(GameModes.BATTERY)
        assertTrue(build(g).contains("Game mode: battery\n"))
        g.applyGameMode(GameModes.PERFORMANCE)
        assertTrue(build(g).contains("Game mode: performance\n"))
        g.applyGameMode(GameModes.STANDARD)
        val text = build(g)
        assertTrue(text.contains("Game mode: standard\n"))
        assertTrue(text.length <= PlaytestReportText.MAX_CHARS)
    }
}
