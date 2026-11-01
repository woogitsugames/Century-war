package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.game.BattleConfig
import com.centurywar.game.Mode
import com.centurywar.meta.Profile
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Zrzuty: 720p, 20:9, Fold otwarty i wąski ekran zewnętrzny. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class BannerScreenshotTest {
    private fun game(w: Int, h: Int, density: Float): Game {
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val file = File.createTempFile("cw-banner-screen", ".json").apply { delete() }
        val g = Game(host, Profile(file).apply { tutorialDone = true }, Quality.HIGH)
        g.density = density
        g.resize(w, h, w, h)
        return g
    }

    private fun shot(g: Game, name: String) {
        val out = File("build/screenshots").apply { mkdirs() }
        g.finishScreenTransition()
        val bitmap = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        repeat(3) { g.frame(canvas, 1f / 60f) }
        val file = File(out, name + ".png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        assertTrue(file.path, file.isFile && file.length() > 0L)
    }

    @Test
    fun fourBannerFormatsAndBattleFlag() {
        val oldLang = L.lang
        try {
            L.lang = L.PL
            for ((tag, device) in linkedMapOf(
                "phone720" to intArrayOf(1600, 720, 2),
                "phone20x9" to intArrayOf(2400, 1080, 3),
                "foldOpen" to intArrayOf(2184, 1968, 3),
                "foldOuter" to intArrayOf(904, 2316, 3),
            )) {
                val g = game(device[0], device[1], device[2].toFloat() * 0.9f)
                val p = g.profile
                g.setScreen(BannerScreen(g))
                shot(g, "banner124_${tag}_locked")

                p.stars.fill(3)
                p.unlockedAchievements.addAll(
                    listOf("win_10", "wave_25", "orbital", "without_turrets"))
                for (day in 25000L..25013L) p.recordRankedDailyStreak(day)
                assertTrue(p.selectBanner(11))
                g.setScreen(BannerScreen(g))
                shot(g, "banner124_${tag}_unlocked")

                p.submit(Profile.BOARD_ENDLESS, 1000L, 24999L)
                g.setScreen(RankingsScreen(g))
                shot(g, "banner124_${tag}_local_ranking")

                g.setScreen(BattleScreen(g, BattleConfig(Mode.SKIRMISH, 124L, 1f)))
                shot(g, "banner124_${tag}_battle")
            }
        } finally {
            L.lang = oldLang
        }
    }
}
