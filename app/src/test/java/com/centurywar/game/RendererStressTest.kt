package com.centurywar.game

import android.graphics.Bitmap
import android.graphics.Canvas
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
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

/** Wielokrotne salwy testują pulę efektów niezależną od symulacji. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RendererStressTest {
    private fun game(quality: Int): Game {
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val file = File.createTempFile("fx", ".json").apply { delete() }
        val g = Game(host, Profile(file), quality)
        g.density = 2f
        g.resize(960, 540, 960, 540)
        g.setQuality(quality)
        return g
    }

    @Test
    fun simultaneousBarragesStayWithinParticleBudget() {
        for (quality in intArrayOf(Quality.LOW, Quality.HIGH)) {
            val g = game(quality)
            val b = Battle(BattleConfig(Mode.SKIRMISH, 31337L, 1f, startEra = 6))
            val r = BattleRenderer(g, b)
            r.layout(960f, 540f, 0f, 55f)
            val bitmap = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            var peak = 0
            repeat(600) { frame ->
                if (frame % 110 == 0 && !b.over) {
                    for (side in b.sides) {
                        side.abilityCd[0] = 0f
                        b.castBarrage(side, 530f)
                    }
                }
                b.step()
                if (frame % 26 == 0) {
                    b.fx.add(FxBuffer.EXPLOSION, 530f, 20f, frame % 2, 1.2f)
                }
                if (frame == 300) g.profile.screenShake = 0
                r.render(canvas, Battle.STEP, 960f, 540f)
                peak = maxOf(peak, r.activeParticleCount)
                assertTrue("Przekroczona pula $quality / klatka $frame",
                    r.activeParticleCount <= g.quality.maxParticles)
                if (g.profile.screenShake == 0) {
                    assertEquals(0f, r.shakeOffsetX, 0f)
                    assertEquals(0f, r.shakeOffsetY, 0f)
                } else {
                    assertTrue(abs(r.shakeOffsetX) <= 7f * g.ui.dp + 0.001f)
                    assertTrue(abs(r.shakeOffsetY) <= 4f * g.ui.dp + 0.001f)
                    val x = 430f
                    val screenX = (x - r.camX) * r.k + r.shakeOffsetX
                    assertEquals(x, r.worldX(screenX), 0.001f)
                }
            }
            assertTrue("Ostrzały nie wygenerowały efektów", peak > 0)
            bitmap.recycle()
        }
    }
}
