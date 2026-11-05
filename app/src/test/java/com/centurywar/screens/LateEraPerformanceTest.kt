package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
import com.centurywar.engine.Sound
import com.centurywar.game.Ai
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.Bonuses
import com.centurywar.game.Campaign
import com.centurywar.game.Mode
import com.centurywar.meta.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.lang.reflect.Method
import kotlin.math.ceil

/**
 * D59: budżety naturalnej bitwy późnych epok (obie strony prowadzi AI, bez zamrażania)
 * i pracy w tle. Pomiary JVM/Robolectric, nie GPU telefonu (docs/reports/d59-wydajnosc.md).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class LateEraPerformanceTest {
    private val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
    }

    private class Meter(private val bean: Any, private val getter: Method) {
        fun bytes(): Long = getter.invoke(bean, Thread.currentThread().id) as Long
    }

    private fun meter(): Meter {
        // android.jar nie udostępnia com.sun.management: dostęp tylko przez refleksję.
        val bean = Class.forName("java.lang.management.ManagementFactory")
            .getMethod("getThreadMXBean").invoke(null)!!
        val api = runCatching { Class.forName("com.sun.management.ThreadMXBean") }.getOrNull()
        assumeTrue("Wymagany ThreadMXBean", api != null && api.isInstance(bean) &&
            api.getMethod("isThreadAllocatedMemorySupported").invoke(bean) == true)
        api!!.getMethod("setThreadAllocatedMemoryEnabled", java.lang.Boolean.TYPE).invoke(bean, true)
        return Meter(bean, api.getMethod("getThreadAllocatedBytes", java.lang.Long.TYPE))
    }

    private fun game(level: Int): Game {
        val file = File.createTempFile("cw-d59-", ".json").apply { delete() }
        val profile = Profile(file).apply {
            tutorialDone = true
            hintsEnabled = false
            hintsSeen = Profile.HINT_VALID_MASK
        }
        return Game(host, profile, level).apply {
            density = 2f
            bitmapCacheMemoryClassMb = 64
            resize(1600, 720, 1600, 720)
            finishScreenTransition()
        }
    }

    private fun battle(g: Game, cfg: BattleConfig, setup: (Battle) -> Unit = {}): BattleScreen {
        val s = BattleScreen(g, cfg)
        g.setScreen(s)
        g.finishScreenTransition()
        s.battle.playerAi = Ai(s.battle, 0)
        setup(s.battle)
        return s
    }

    /** Pełna klatka bitwy z AI po obu stronach: najpierw rozgrzewka (pierwsze użycia zdolności). */
    private fun checkNaturalBattle(label: String, cfg: BattleConfig, setup: (Battle) -> Unit = {}) {
        val g = game(Quality.LOW)
        val c = Canvas(Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888))
        val s = battle(g, cfg, setup)
        val m = meter()
        repeat(1800) { s.update(Battle.STEP); s.render(c) }
        assertFalse("$label: bitwa skończyła się przed pomiarem", s.battle.over)
        // Nowa fala tworzy jednorazowo napisy i oddziały (Robolectric dokłada pierwsze wywołania
        // natywnych metod); budżet dotyczy stanu ustalonego, więc liczy się najlepsze z 3 okien.
        val windows = LongArray(3)
        for (w in windows.indices) {
            val before = m.bytes()
            repeat(600) { s.update(Battle.STEP); s.render(c) }
            windows[w] = m.bytes() - before
        }
        val allocated = windows.min()
        assertFalse("$label: bitwa skończyła się w trakcie pomiaru", s.battle.over)
        println("PERF-D59 $label: okna ${windows.joinToString()} B / 600 klatek, jednostki=" +
            s.battle.units.count { it.alive })
        assertTrue("$label: $allocated B / 600 klatek (limit < 16384 B)", allocated < 16 * 1024)
    }

    @Test fun campaignStage36NaturalBattleAllocations() = checkNaturalBattle("kampania-36",
        Campaign.config(Campaign.stages[35], Bonuses(), 5936L))

    @Test fun endlessWave52NaturalBattleAllocations() = checkNaturalBattle("endless-52",
        BattleConfig(Mode.ENDLESS, 5952L, 1f, startEra = 8, maxEra = 8)) { it.wave = 52 }

    /** D59: krajobraz rysuje tylko kafle widoczne z marginesem wstrząsu (dawniej +1 z każdej strony). */
    @Test fun landscapeDrawsOnlyVisibleTiles() {
        for (level in intArrayOf(Quality.LOW, Quality.HIGH)) {
            val f = Wave6PerformanceFixture(level, fullResolution = true)
            try {
                for (cam in floatArrayOf(0f, 333f, 1250f, 1e6f)) {
                    f.screen.r.camX = cam
                    f.screen.r.clampCam()
                    f.draw()
                    val backdrops = f.screen.r.backdrops
                    val layers = backdrops.lastDrawnLayers
                    val worldW = 1600f / f.screen.r.k
                    val margin = 8f * f.game.ui.dp / f.screen.r.k
                    // Kafel ma 640 j.: widoczny pas z marginesem przecina najwyżej tyle kafli.
                    val perLayer = ceil((worldW + 2f * margin) / 640f).toInt() + 1
                    assertTrue("Kafle ${backdrops.lastDrawnTiles} > $layers × $perLayer (kamera $cam)",
                        backdrops.lastDrawnTiles in layers..layers * perLayer)
                }
            } finally {
                f.close()
            }
        }
    }

    /** D59: cząstki z walki poza kadrem nie są rysowane (dalej się poruszają i gasną). */
    @Test fun offscreenParticlesAreSkipped() {
        val f = Wave6PerformanceFixture(Quality.HIGH)
        try {
            // Walka toczy się w środku pasa; kamera stoi przy bazie gracza.
            repeat(240) {
                f.update()
                f.screen.r.camX = 0f
                f.screen.r.clampCam()
                f.draw()
            }
            assertTrue("Cząstki poza ekranem powinny być pomijane", f.screen.r.culledParticles > 0L)
        } finally {
            f.close()
        }
    }

    /** D59: generator efektów dźwiękowych śpi bez limitu czasu, gdy nie ma zamówień (gra w tle). */
    @Test fun soundGeneratorSleepsWithoutPeriodicWakeups() {
        val sound = Sound(RuntimeEnvironment.getApplication())
        try {
            val deadline = System.currentTimeMillis() + 30_000L
            var worker: Thread? = null
            while (System.currentTimeMillis() < deadline) {
                worker = Thread.getAllStackTraces().keys.firstOrNull { it.name == "sfx-gen" && it.isAlive }
                if (worker != null && worker.state == Thread.State.WAITING) break
                Thread.sleep(20)
            }
            assertTrue("Brak wątku sfx-gen", worker != null)
            assertEquals("Bezczynny generator nie może budzić się okresowo",
                Thread.State.WAITING, worker!!.state)
        } finally {
            sound.release()
        }
    }
}
