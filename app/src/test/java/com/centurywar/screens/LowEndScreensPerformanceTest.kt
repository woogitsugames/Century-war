package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Picture
import android.graphics.RectF
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
import com.centurywar.engine.Screen
import com.centurywar.game.BattleConfig
import com.centurywar.game.Mode
import com.centurywar.meta.Profile
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.lang.reflect.Method

/** Alokacje i czasy update + render na JVM; nie są to pomiary GPU telefonu. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class LowEndScreensPerformanceTest {
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
        // Android.jar nie udostępnia com.sun.management; dostęp tylko przez refleksję.
        val bean = Class.forName("java.lang.management.ManagementFactory")
            .getMethod("getThreadMXBean").invoke(null)!!
        val api = runCatching { Class.forName("com.sun.management.ThreadMXBean") }.getOrNull()
        assumeTrue("Wymagany ThreadMXBean", api != null && api.isInstance(bean) &&
            api.getMethod("isThreadAllocatedMemorySupported").invoke(bean) == true)
        api!!.getMethod("setThreadAllocatedMemoryEnabled", java.lang.Boolean.TYPE).invoke(bean, true)
        return Meter(bean, api.getMethod("getThreadAllocatedBytes", java.lang.Long.TYPE))
    }

    private fun game(level: Int = Quality.LOW): Game {
        val file = File.createTempFile("cw-perf77-", ".json").apply { delete() }
        val profile = Profile(file).apply {
            tutorialDone = true
            hintsEnabled = false
        }
        return Game(host, profile, level).apply {
            density = 2f
            resize(1600, 720, 1600, 720)
            finishScreenTransition()
        }
    }

    private fun canvas(): Canvas =
        Canvas(Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888))

    private fun check(label: String, frame: () -> Unit) {
        val m = meter()
        repeat(300) { frame() }
        val durations = LongArray(600)
        val before = m.bytes()
        for (i in durations.indices) {
            val start = System.nanoTime()
            frame()
            durations[i] = System.nanoTime() - start
        }
        val allocated = m.bytes() - before
        val total = durations.sum()
        durations.sort()
        val avgMs = total / 600_000_000.0
        val p95Ms = durations[569] / 1_000_000.0
        println("PERF77 $label: avg=${"%.3f".format(java.util.Locale.ROOT, avgMs)} ms, " +
            "p95=${"%.3f".format(java.util.Locale.ROOT, p95Ms)} ms, " +
            "alloc=$allocated B / 600 klatek")
        assertTrue("$label: $allocated B / 600 klatek (limit < 16384 B)", allocated < 16 * 1024)
    }

    @Test fun menuLow() {
        val g = game()
        val c = canvas()
        val s = g.current as MenuScreen
        check("Menu LOW") { s.update(1f / 60f); s.render(c) }
    }

    @Test fun menuHigh() {
        val g = game(Quality.HIGH)
        val c = canvas()
        val s = g.current as MenuScreen
        check("Menu HIGH") { s.update(1f / 60f); s.render(c) }
    }

    @Test fun campaignScrollingLow() {
        val g = game()
        val c = canvas()
        val s = CampaignScreen(g)
        g.setScreen(s)
        check("Kampania przewijana LOW") {
            s.onDrag(-4f, -4f)
            s.update(1f / 60f)
            s.render(c)
        }
    }

    @Test fun campaignPicturesReusedUntilGeometryChanges() {
        val g = game()
        val s = CampaignScreen(g)
        g.setScreen(s)
        val field = CampaignScreen::class.java.getDeclaredField("regionPictures").apply {
            isAccessible = true
        }
        fun first(): Picture = (field.get(s) as Array<*>)[0] as Picture
        val original = first()
        s.layout()
        assertSame("Nie wolno nagrywać Picture ponownie przy powrocie", original, first())
        g.resize(720, 1600, 720, 1600)
        assertNotSame("Zmiana orientacji wymaga nowej geometrii", original, first())
    }

    @Test fun codexScrollingLow() {
        val g = game()
        val c = canvas()
        val s = CodexScreen(g)
        g.setScreen(s)
        s.showCategory(5) // Długi, przewijany spis doktryn.
        check("Kodeks przewijany LOW") {
            s.onDrag(0f, -4f)
            s.update(1f / 60f)
            s.render(c)
        }
    }

    @Test fun battlePauseLow() {
        val g = game()
        val c = canvas()
        val s = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 7701L, 1f, startEra = 0, maxEra = 0))
        g.setScreen(s)
        val button = BattleScreen::class.java.getDeclaredField("pauseBtn").apply {
            isAccessible = true
        }.get(s) as Button
        s.onClick(button)
        check("Pauza bitwy LOW") { s.update(1f / 60f); s.render(c) }
    }

    @Test fun hintBubbleLow() {
        val g = game()
        val c = canvas()
        g.profile.hintsEnabled = true
        val hint = HintOverlay(g)
        hint.showScreen(HintOverlay.RESEARCH, RectF(160f, 120f, 420f, 210f))
        assertTrue("Dymek musi być widoczny", hint.activeId >= 0)
        check("Dymek LOW") { hint.update(0.005f); hint.draw(c) }
    }
}
