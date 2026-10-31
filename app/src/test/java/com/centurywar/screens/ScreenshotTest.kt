package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
import com.centurywar.engine.TouchEvent
import com.centurywar.game.Ai
import com.centurywar.game.BattleConfig
import com.centurywar.game.Mode
import com.centurywar.meta.Profile
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renderuje ekrany gry do PNG (Robolectric + natywna Skia), żeby obejrzeć UI
 * bez telefonu. Rozdzielczości: tani telefon 720p, flagowiec 20:9, rozłożony Fold.
 * Pliki trafiają do build/screenshots/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ScreenshotTest {

    private val out = File("build/screenshots").apply { mkdirs() }

    private fun game(w: Int, h: Int, density: Float, q: Int): Game {
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val f = File.createTempFile("profile", ".json").apply { delete() }
        val g = Game(host, Profile(f).apply { tutorialDone = true; medals = 340 }, q)
        g.density = density
        g.resize(w, h, w, h)
        return g
    }

    private fun shot(g: Game, name: String, frames: Int = 3) {
        val bmp = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        repeat(frames) { g.frame(c, 1f / 60f) }
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun tap(g: Game, x: Float, y: Float) {
        g.pushTouch(TouchEvent.DOWN, x, y, 0)
        g.pushTouch(TouchEvent.UP, x, y, 0)
    }

    @Test
    fun screens() {
        for ((tag, dims) in mapOf(
            "phone720" to intArrayOf(1600, 720, 2),
            "phone20x9" to intArrayOf(2400, 1080, 3),
            "foldOpen" to intArrayOf(2184, 1968, 3),
        )) {
            val g = game(dims[0], dims[1], dims[2] / 1.0f * 0.9f, if (tag == "phone720") Quality.LOW else Quality.HIGH)
            shot(g, "${tag}_menu")
            g.setScreen(CampaignScreen(g)); shot(g, "${tag}_campaign")
            g.setScreen(ResearchScreen(g)); shot(g, "${tag}_research")

            for (era in intArrayOf(0, 3, 5, 7)) {
                val bs = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 5L + era, 1f, startEra = era, maxEra = 7))
                g.setScreen(bs)
                val f = BattleScreen::class.java.getDeclaredField("battle").apply { isAccessible = true }
                val b = f.get(bs) as com.centurywar.game.Battle
                b.playerAi = Ai(b, 0)
                val bmp = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp)
                repeat(60 * 70) { g.frame(c, 1f / 60f) }
                File(out, "${tag}_battle_era$era.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }
}
