package com.centurywar.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
import com.centurywar.meta.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** A156-08: liczba rozbłysków zależy od realnego czasu, nie przyspieszenia bitwy. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class BattleFlash161Test {
    private fun game(): Game {
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val f = File.createTempFile("flash161", ".json").apply { delete() }
        return Game(host, Profile(f).apply { reduceFlash = true }, Quality.LOW).apply {
            density = 2f
            resize(1200, 720, 1200, 720)
        }
    }

    private class NightCanvas(bitmap: Bitmap) : Canvas(bitmap) {
        val baseAuraAlphas = ArrayList<Int>()
        override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) {
            val color = paint.color
            if (Color.red(color) == 255 && Color.green(color) == 224 &&
                Color.blue(color) == 142) baseAuraAlphas.add(Color.alpha(color))
            super.drawCircle(cx, cy, radius, paint)
        }
    }

    private fun setup(): Triple<Game, Battle, BattleRenderer> {
        val g = game()
        val battle = Battle(BattleConfig(Mode.SKIRMISH, 161L, 1f, weather = Weather.NIGHT))
        val renderer = BattleRenderer(g, battle)
        renderer.layout(1200f, 720f, 170f, 220f)
        return Triple(g, battle, renderer)
    }

    @Test
    fun reducedFlashesHaveSameRealTimeLimitAtOneThreeAndFiveTimesSpeed() {
        val counts = IntArray(3)
        for ((index, speed) in intArrayOf(1, 3, 5).withIndex()) {
            val (_, battle, renderer) = setup()
            val bitmap = Bitmap.createBitmap(1200, 720, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            repeat(120) { frame ->
                battle.player.baseFlash = if (frame % 2 == 0) 0.15f else 0f
                renderer.render(canvas, speed / 60f, 1200f, 720f, realDt = 1f / 60f)
            }
            counts[index] = renderer.limitedFlashEvents
            assertTrue("×$speed: więcej niż trzy błyski na sekundę rzeczywistą: ${counts[index]}",
                counts[index] <= 6)
            assertTrue("×$speed: nie testujemy martwego limitera", counts[index] >= 4)
            bitmap.recycle()
        }
        assertEquals("zmiana prędkości zwiększyła liczbę błysków", counts[0], counts[1])
        assertEquals("zmiana prędkości zwiększyła liczbę błysków", counts[0], counts[2])
    }

    @Test
    fun nightBaseAuraCannotBypassSharedFlashGate() {
        val (_, battle, renderer) = setup()
        val bitmap = Bitmap.createBitmap(1200, 720, Bitmap.Config.ARGB_8888)
        val c = NightCanvas(bitmap)
        battle.player.baseFlash = 0.15f
        renderer.render(c, 1f / 60f, 1200f, 720f, realDt = 1f / 60f)
        assertEquals(1, renderer.limitedFlashEvents)
        assertTrue(renderer.baseAuraFlashAllowed(0))
        assertTrue("nocne trafienie nie zostało pokazane", 43 in c.baseAuraAlphas)

        battle.player.baseFlash = 0f
        c.baseAuraAlphas.clear()
        renderer.render(c, 1f / 60f, 1200f, 720f, realDt = 1f / 60f)
        battle.player.baseFlash = 0.15f
        c.baseAuraAlphas.clear()
        renderer.render(c, 5f / 60f, 1200f, 720f, realDt = 1f / 60f)
        assertEquals("nocny błysk ominął bramkę", 1, renderer.limitedFlashEvents)
        assertFalse(renderer.baseAuraFlashAllowed(0))
        assertTrue("nocne światło nie wróciło do spokojnej aureoli", 35 in c.baseAuraAlphas)
        assertFalse("zablokowane trafienie podbiło nocne światło", 43 in c.baseAuraAlphas || 85 in c.baseAuraAlphas)
        bitmap.recycle()
    }
}
