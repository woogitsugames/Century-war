package com.centurywar.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.centurywar.meta.Profile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Nawigacja zmienia tylko sposób rysowania ekranów, nie ich stan ani symulację. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ScreenTransitionTest {
    private val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
    }

    private class Solid(g: Game, private val color: Int) : Screen(g) {
        override fun render(c: Canvas) { c.drawColor(color) }
    }

    private fun game(level: Int = Quality.HIGH, reduceFlash: Boolean = false): Game {
        val file = File.createTempFile("cw-transition-", ".json").apply { delete() }
        val p = Profile(file)
        p.tutorialDone = true
        p.reduceFlash = reduceFlash
        return Game(host, p, level).apply {
            density = 1f
            resize(1280, 720, 1280, 720)
        }
    }

    @Test fun pushAndPopFinishInTwoHundredTwentyMilliseconds() {
        val g = game()
        val first = Solid(g, Color.BLUE)
        val second = Solid(g, Color.RED)
        g.setScreen(first)
        g.finishScreenTransition()
        g.push(second)
        assertSame(second, g.current)
        assertTrue(g.transitionActive)

        val bitmap = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        repeat(15) { g.frame(canvas, 1f / 60f) }
        assertFalse(g.transitionActive)

        g.pop()
        assertSame(first, g.current)
        assertTrue(g.transitionActive)
        repeat(15) { g.frame(canvas, 1f / 60f) }
        assertFalse(g.transitionActive)
        bitmap.recycle()
    }

    @Test fun lowQualityAndReducedFlashSkipTransition() {
        for (g in arrayOf(game(Quality.LOW), game(Quality.HIGH, reduceFlash = true))) {
            g.push(Solid(g, Color.GREEN))
            assertFalse(g.transitionActive)
            g.setScreen(Solid(g, Color.YELLOW))
            assertFalse(g.transitionActive)
        }
    }

    @Test fun resizingCancelsTransitionAndKeepsDestination() {
        val g = game()
        val destination = Solid(g, Color.RED)
        g.push(destination)
        assertTrue(g.transitionActive)
        g.resize(1600, 720, 1600, 720)
        assertFalse(g.transitionActive)
        assertSame(destination, g.current)
    }
}
