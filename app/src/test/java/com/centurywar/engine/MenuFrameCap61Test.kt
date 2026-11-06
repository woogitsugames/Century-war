package com.centurywar.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import com.centurywar.meta.Profile
import com.centurywar.screens.BattleScreen
import com.centurywar.screens.CodexScreen
import com.centurywar.screens.MenuScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * D61 (decyzja właściciela 6.11): poza bitwą najwyżej 60 klatek na sekundę, w bitwie bez limitu.
 * Pętla [GameView] liczy limit przez [FramePacer.capFor] (`Game.uncappedScreen`) i czeka
 * [FramePacer.sleepNanos]; tu ta sama pętla na zegarze testowym, z klatką rysowaną w 2 ms
 * (szybki telefon bez vsync — najgorszy przypadek dla limitu).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MenuFrameCap61Test {

    private val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
    }

    private fun game(): Game {
        val f = File.createTempFile("cap61", ".json").apply { delete() }
        return Game(host, Profile(f), Quality.LOW).apply {
            density = 2f
            resize(1600, 720, 1600, 720)
        }
    }

    /** Klatki, które zaczęły się w pierwszej sekundzie pętli z limitem z bieżącego ekranu. */
    private fun framesPerSecond(g: Game, renderNs: Long = 2_000_000L): Int {
        val pacer = FramePacer()
        val canvas = Canvas(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))
        var now = 0L
        var last = 0L
        var frames = 0
        while (now < 1_000_000_000L) {
            val start = now
            frames++
            g.frame(canvas, (start - last) / 1e9f)
            last = start
            now += renderNs
            now += pacer.sleepNanos(start, now, FramePacer.capFor(g.effectiveFpsCap(), g.uncappedScreen))
        }
        return frames
    }

    @Test
    fun capForScreens() {
        assertEquals(60, FramePacer.capFor(0, battle = false))
        assertEquals(60, FramePacer.capFor(120, battle = false))
        assertEquals(60, FramePacer.capFor(60, battle = false))
        assertEquals("niższy wybór gracza obowiązuje też w menu", 30, FramePacer.capFor(30, battle = false))
        assertEquals("bitwa bez zmian: tempo ekranu", 0, FramePacer.capFor(0, battle = true))
        assertEquals("bitwa bez zmian: limit gracza", 60, FramePacer.capFor(60, battle = true))
        assertEquals("tryb baterii w bitwie", 30, FramePacer.capFor(GameModes.effectiveFps(0, GameModes.BATTERY), battle = true))
        assertEquals("tryb baterii w menu", 30, FramePacer.capFor(GameModes.effectiveFps(0, GameModes.BATTERY), battle = false))
    }

    @Test
    fun pacerNeverExceedsCapAndWaitsNothingWithoutCap() {
        val p = FramePacer()
        // 16,67 ms między początkami klatek, także gdy klatka trwa ułamek milisekundy.
        assertEquals(16_666_667L - 500_000L, p.sleepNanos(0L, 500_000L, 60))
        assertEquals("spóźniona klatka nie czeka", 0L, p.sleepNanos(0L, 20_000_000L, 60))
        assertEquals("bez limitu bez czekania", 0L, p.sleepNanos(0L, 1L, 0))
        // Dawne Thread.sleep(ms) obcinało do 16 ms (62 kl./s); teraz dokładnie ≤ 60 w każdej sekundzie.
        for (render in longArrayOf(100_000L, 2_000_000L, 8_000_000L, 16_000_000L)) {
            var now = 0L; var frames = 0
            val pacer = FramePacer()
            while (now < 3_000_000_000L) {
                val start = now; frames++
                now += render
                now += pacer.sleepNanos(start, now, 60)
            }
            assertTrue("render $render ns: $frames klatek w 3 s", frames <= 180)
            assertTrue("render $render ns: $frames klatek w 3 s (za mało)", frames >= 179)
        }
    }

    @Test
    fun menuAndCodexAtMost60BattleUncapped() {
        val g = game()
        g.frame(Canvas(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)), 0.1f)
        assertTrue(g.current is MenuScreen)
        assertFalse(g.uncappedScreen)
        val menu = framesPerSecond(g)
        assertTrue("menu: $menu klatek/s", menu in 59..60)

        g.push(CodexScreen(g))
        assertFalse(g.uncappedScreen)
        val codex = framesPerSecond(g)
        assertTrue("Kodeks: $codex klatek/s", codex in 59..60)
        g.pop()

        // Graj (nowy profil: samouczek w pierwszej bitwie) — bitwa bez limitu.
        val play = g.current!!.buttons.first { it.id == 1 }
        g.pushTouch(TouchEvent.DOWN, play.r.centerX(), play.r.centerY(), 0)
        g.pushTouch(TouchEvent.UP, play.r.centerX(), play.r.centerY(), 0)
        g.frame(Canvas(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)), 1f / 60f)
        assertTrue("Graj nie otwiera bitwy: ${g.current}", g.current is BattleScreen)
        assertTrue(g.uncappedScreen)
        val battle = framesPerSecond(g)
        assertTrue("bitwa: $battle klatek/s (bez limitu)", battle > 400)

        // Gracz wybrał 60 FPS: w bitwie limit gracza jak dotąd.
        g.profile.fpsCap = 60
        assertTrue(framesPerSecond(g) in 59..60)
        g.profile.fpsCap = 0

        // Powrót do menu: znów 60.
        g.setScreen(MenuScreen(g))
        assertFalse(g.uncappedScreen)
        assertTrue(framesPerSecond(g) in 59..60)
    }
}
