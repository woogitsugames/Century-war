package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
import com.centurywar.game.Ai
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.Defs
import com.centurywar.game.Mode
import com.centurywar.game.Weather
import com.centurywar.meta.Profile
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.zip.CRC32

/** Sonda d59 (tymczasowa): sumy CRC pikseli bitwy do porównania przed/po. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class D59PixelHarnessTest {
    private val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
    }

    private fun crc(bmp: Bitmap): Long {
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        val bytes = java.nio.ByteBuffer.allocate(px.size * 4)
        bytes.asIntBuffer().put(px)
        return CRC32().apply { update(bytes.array()) }.value
    }

    private fun scene(out: StringBuilder, level: Int, era: Int, variant: Int, weather: Weather,
                      w: Int, h: Int) {
        val file = File.createTempFile("cw-d59px-", ".json").apply { delete() }
        val profile = Profile(file).apply {
            tutorialDone = true; hintsEnabled = false; hintsSeen = Profile.HINT_VALID_MASK
        }
        val g = Game(host, profile, level).apply {
            density = 2f; bitmapCacheMemoryClassMb = 64
            resize(w, h, w, h); finishScreenTransition()
        }
        val cfg = BattleConfig(Mode.SKIRMISH, 5900L + era * 7 + variant, 1f, startEra = era,
            maxEra = era, weather = weather)
        val s = BattleScreen(g, cfg)
        g.setScreen(s); g.finishScreenTransition()
        val b = s.battle
        b.playerAi = Ai(b, 0)
        for (side in b.sides) { side.eraVariant = variant; side.gold = 1e9f }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val trauma = com.centurywar.game.BattleRenderer::class.java.getDeclaredField("trauma")
            .apply { isAccessible = true }
        val cams = floatArrayOf(0f, 913f, 1e6f)
        for (f in 1..540) {
            s.update(Battle.STEP)
            if (b.over) break
            if (f % 45 == 0) {
                for ((ci, cam) in cams.withIndex()) {
                    s.r.camX = cam + f
                    s.r.clampCam()
                    trauma.setFloat(s.r, if (ci == 1) 1f else 0.6f)
                    s.render(c)
                    out.append("L$level e$era v$variant ${weather.name} ${w}x$h f$f c$ci ${crc(bmp)}\n")
                }
            } else s.render(c)
        }
        bmp.recycle()
    }

    @Test fun battleCrc() {
        val out = StringBuilder()
        for (era in 0 until Defs.ERA_COUNT) {
            val variants = Defs.spec(era).variants.size
            for (variant in -1 until variants) {
                for (weather in Weather.values()) {
                    if (variant >= 0 && weather != Weather.CLEAR) continue
                    scene(out, Quality.HIGH, era, variant, weather, 1600, 720)
                }
            }
            scene(out, Quality.LOW, era, -1, Weather.CLEAR, 1600, 720)
            scene(out, Quality.MED, era, -1, Weather.RAIN, 720, 1600)
        }
        val dir = System.getenv("D59_OUT") ?: return
        File(dir, "crc.txt").writeText(out.toString())
    }
}
