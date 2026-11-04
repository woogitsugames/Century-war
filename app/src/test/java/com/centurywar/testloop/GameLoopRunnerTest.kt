package com.centurywar.testloop

import android.graphics.Bitmap
import android.graphics.Canvas
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
import com.centurywar.game.SnapshotStore
import com.centurywar.meta.Profile
import com.centurywar.screens.BattleScreen
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** R11-04: scenariusze pętli gry na skróconych czasach (zegar udawany, klatki ręcznie). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class GameLoopRunnerTest {
    @get:Rule val folder = TemporaryFolder()

    private val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
    }
    private var now = 1_000_000_000L
    private lateinit var game: Game
    private lateinit var runner: GameLoopRunner

    private fun newGame(): Game {
        val profile = Profile(File(folder.root, "profile.json")).apply {
            tutorialDone = true
            hintsEnabled = false
            eraIntrosEnabled = false
        }
        return Game(host, profile, Quality.LOW).apply {
            testLoop = true
            density = 2f
            resize(1600, 720, 1600, 720)
            finishScreenTransition()
        }
    }

    /** Wszystkie fazy skrócone 90×: bitwa 2 s zamiast 3 min (rysowanie w Robolectric jest wolne). */
    private fun run(scenario: Int, scale: Float = 1f / 90f, limitS: Float = GameLoopTiming.LIMIT_S): GameLoopReport {
        game = newGame()
        game.snapshots = SnapshotStore(File(folder.root, "battle.snap"))
        var report: GameLoopReport? = null
        runner = GameLoopRunner(
            game, scenario, GameLoopTiming(scale, limitS),
            clock = { now }, pssKb = { 1234L }, startupMs = { 777L },
            io = { it.run() }, onDone = { report = it },
        )
        runner.start()
        val canvas = Canvas(Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888))
        var frames = 0
        while (report == null && frames < 30_000) {
            now += 33_333_333L
            game.frame(canvas, 1f / 30f)
            frames++
        }
        assertNotNull("Scenariusz $scenario nie skończył się", report)
        // Po końcu runner nie dokłada się już do kolejki klatek.
        repeat(3) { now += 33_333_333L; game.frame(canvas, 1f / 30f) }
        assertTrue(runner.finished)
        return report!!
    }

    private fun json(r: GameLoopReport): JSONObject =
        JSONObject(GameLoopResult.json(r, "1.2.3", 42, "0.8.6", "Test Phone", 35))

    @Test fun menuScenarioVisitsScreensAndMeasuresStartup() {
        val r = run(1)
        assertEquals("ok", r.result)
        assertEquals(777L, r.startupMs)
        for (label in listOf("menu", "campaign", "research", "codex", "settings"))
            assertTrue("Brak klatek ekranu $label", (r.screens[label]?.count ?: 0) > 0)
        val o = json(r)
        assertEquals(1, o.getInt("v"))
        assertEquals(1, o.getInt("scenario"))
        assertEquals("ok", o.getString("result"))
        assertEquals(777L, o.getLong("startupMs"))
        assertTrue(o.getJSONObject("screens").has("codex"))
        assertEquals(1234L, o.getLong("pssStartKb"))
        assertEquals("Test Phone", o.getJSONObject("device").getString("model"))
        assertFalse("Bez bitwy nie ma zwycięzcy", o.has("winner"))
    }

    @Test fun campaignBattleRunsWithPlayerAiAtTripleSpeed() {
        val r = run(2)
        assertEquals("ok", r.result)
        val bs = game.current as BattleScreen
        assertEquals(3, bs.speed)
        assertNotNull(bs.battle.playerAi)
        assertTrue("Bitwa stoi: ${r.battleTimeS}", r.battleTimeS > 2f)
        assertTrue(r.frames.count >= 55)
        val o = json(r)
        assertTrue(o.getJSONObject("frames").getDouble("p95") > 0.0)
        assertTrue(o.has("battleTimeS"))
        assertTrue(o.has("winner"))
        assertTrue(o.has("discardedSteps"))
        assertTrue(o.has("qualityMin"))
    }

    @Test fun lateEndlessStartsInAdvancedEra() {
        val r = run(3)
        assertEquals("ok", r.result)
        val bs = game.current as BattleScreen
        assertEquals(GameLoopTiming.ENDLESS_START_ERA, bs.battle.cfg.startEra)
        assertTrue(bs.battle.player.era >= GameLoopTiming.ENDLESS_START_ERA)
        assertTrue(r.wave >= 0)
        assertTrue(r.battleTimeS > 2f)
    }

    @Test fun pauseSavesSnapshotAndResumedBattleContinues() {
        val r = run(4, scale = 1f / 30f)
        assertEquals(r.reason, "ok", r.result)
        assertEquals(true, r.snapshotSaved)
        assertEquals(true, r.snapshotLoaded)
        assertTrue(r.snapshotLoadMs >= 0)
        assertTrue(r.resumedAtS > 0f)
        assertTrue("Wznowiona bitwa gra dalej", r.battleTimeS > r.resumedAtS)
        val bs = game.current as BattleScreen
        assertEquals(1, bs.speed)
        assertFalse(bs.paused)
        val o = json(r)
        assertTrue(o.getBoolean("snapshotLoaded"))
    }

    @Test fun unknownScenarioReportsErrorImmediately() {
        val r = run(9)
        assertEquals("error", r.result)
        assertEquals("unknown_scenario", r.reason)
    }

    @Test fun hardLimitEndsWithTimeout() {
        val r = run(2, scale = 1f, limitS = 0.5f)
        assertEquals("timeout", r.result)
        assertTrue(r.elapsedS in 0.5f..0.6f)
    }
}
