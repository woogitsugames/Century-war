package com.centurywar.screens

import android.graphics.RectF
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
import com.centurywar.meta.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Przewijanie statystyk wynika z tych samych stałych co rysowanie (bez 1560 dp na sztywno). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StatsScreenTest {
    private fun game(width: Int, height: Int, density: Float): Game {
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val file = File.createTempFile("stats105", ".json").apply { delete() }
        val game = Game(host, Profile(file).apply { tutorialDone = true }, Quality.LOW)
        game.density = density
        game.resize(width, height, width, height)
        return game
    }

    private fun field(o: Any, name: String): Any =
        o.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(o)

    @Test
    fun scrollRangeMatchesDrawnContentOnPhoneAndFold() {
        for ((w, h, d) in listOf(Triple(1600, 720, 1.8f), Triple(2184, 1968, 2.7f), Triple(1802, 2000, 3.6f))) {
            val g = game(w, h, d)
            val s = StatsScreen(g)
            g.setScreen(s)
            val dp = g.ui.dp
            val panel = field(s, "panel") as RectF
            val content = s.statsContentHeightDp() * dp
            val maxScroll = field(s, "maxScroll") as Float
            assertEquals("${w}x$h", maxOf(0f, content - panel.height() + 26 * dp), maxScroll, 0.01f)
            // Stopka „dane lokalne” po przewinięciu do końca mieści się w panelu.
            val footerBaseline = panel.top + content - 26 * dp + 17 * dp - maxScroll
            assertTrue("${w}x$h stopka poza panelem", footerBaseline <= panel.bottom - 4 * dp)
            assertTrue("${w}x$h treść krótsza niż wykresy", content > 1400 * dp)
        }
    }
}
