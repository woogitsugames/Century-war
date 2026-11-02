package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
import com.centurywar.game.BattleConfig
import com.centurywar.game.Mode
import com.centurywar.meta.Profile
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Obrazy A156-09 i A156-24 obok dotychczasowych wzorców 720p i Fold. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class TurretOverlay161ScreenshotTest {
    private val output = File("build/screenshots").apply { mkdirs() }

    private fun game(width: Int, height: Int, density: Float, textScale: Int = 100): Game {
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val f = File.createTempFile("turret161", ".json").apply { delete() }
        val p = Profile(f).apply {
            tutorialDone = true
            hintsEnabled = false
            eraIntrosEnabled = false
            this.textScale = textScale
        }
        return Game(host, p, Quality.LOW).apply {
            this.density = density
            resize(width, height, width, height)
        }
    }

    private fun save(g: Game, name: String) {
        val bitmap = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
        try {
            g.finishScreenTransition()
            g.frame(Canvas(bitmap), 1f / 60f)
            File(output, "161_$name.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun towerCompactAndOriginalLayouts() {
        for ((name, dims) in mapOf(
            "compact_1200x720" to floatArrayOf(1200f, 720f, 2f),
            "normal_1600x720" to floatArrayOf(1600f, 720f, 2f),
            "fold_2184x1968" to floatArrayOf(2184f, 1968f, 2.7f),
        )) {
            val g = game(dims[0].toInt(), dims[1].toInt(), dims[2])
            val screen = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 161L, 1f))
            screen.battle.player.slots = 4
            screen.battle.player.gold = 1_000_000f
            g.setScreen(screen)
            screen.showTurrets = true
            save(g, name)
            assertEquals("$name: niewłaściwy wariant panelu", name.startsWith("compact"), screen.turretOverlay)
        }
    }

    @Test
    fun lateEraPricesAtMaximumProfileTextScale() {
        val g = game(720, 1280, 2f, textScale = 130)
        val screen = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 162L, 1f, startEra = 8, maxEra = 8))
        g.setScreen(screen)
        save(g, "ceny_epoka8_720x1280_130procent")
    }
}
