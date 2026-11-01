package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import com.centurywar.engine.ColorVisionSim
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Palette
import com.centurywar.engine.Quality
import com.centurywar.engine.Ui
import com.centurywar.engine.VisionDefect
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.Mode
import com.centurywar.game.Role
import com.centurywar.meta.Profile
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.min

/** Rzeczywiste ekrany Skia + obrazy PNG przefiltrowane według trzech typów daltonizmu. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ColorBlindScreenshotTest {
    private val output = File("build/screenshots/colorblind-111").apply { mkdirs() }

    private fun game(width: Int, height: Int, density: Float): Game {
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val state = File.createTempFile("colorblind-profile", ".json").apply { delete() }
        val g = Game(host, Profile(state).apply {
            tutorialDone = true
            palette = Palette.STANDARD
            teamMarkers = true
            reduceFlash = true
        }, if (width <= 1600) Quality.LOW else Quality.HIGH)
        g.density = density
        g.resize(width, height, width, height)
        g.applyAccessibility()
        return g
    }

    private fun png(bitmap: Bitmap, name: String) {
        File(output, name + ".png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    private fun capture(g: Game, name: String) {
        g.finishScreenTransition()
        val source = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(source)
        repeat(3) { g.frame(canvas, 1f / 60f) }

        // Render na pełnym ekranie, eksport ograniczony do 1000 px dla małych artefaktów CI.
        val targetWidth = min(g.width, 1000)
        val height = (g.height.toLong() * targetWidth / g.width).toInt()
        val scaled = Bitmap.createScaledBitmap(source, targetWidth, height, true)
        png(scaled, name + "-original")
        val pixels = IntArray(targetWidth * height)
        val filtered = IntArray(pixels.size)
        scaled.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, height)
        val result = Bitmap.createBitmap(targetWidth, height, Bitmap.Config.ARGB_8888)
        for (defect in VisionDefect.entries) {
            for (i in pixels.indices) filtered[i] = ColorVisionSim.filter(pixels[i], defect)
            result.setPixels(filtered, 0, targetWidth, 0, 0, targetWidth, height)
            png(result, name + "-" + defect.name.lowercase())
        }
        result.recycle()
        if (scaled !== source) scaled.recycle()
        source.recycle()
    }

    @Test
    fun screenshotsOfAccessibleScreens() {
        val oldPalette = Palette.selected
        val oldLanguage = L.lang
        try {
            L.lang = L.PL
            for ((tag, dimensions) in mapOf(
                "phone720" to intArrayOf(1600, 720, 2),
                "foldOpen" to intArrayOf(2184, 1968, 3),
            )) {
                val g = game(dimensions[0], dimensions[1], dimensions[2] * 0.9f)
                g.setScreen(AccessibilityScreen(g))
                capture(g, tag + "-accessibility")

                val battleScreen = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 111L, 1f))
                g.setScreen(battleScreen)
                val field = battleScreen.javaClass.getDeclaredField("battle").apply { isAccessible = true }
                val battle = field.get(battleScreen) as Battle
                // Rozkazy gracza wyłącznie przez BattleController.
                battle.commandController!!.queue(Role.MELEE)
                // Wróg może korzystać ze zwykłej kolejki AI.
                battle.queueUnit(battle.enemy, Role.MELEE)
                val scratch = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
                val scratchCanvas = Canvas(scratch)
                repeat(180) { g.frame(scratchCanvas, 1f / 60f) }
                scratch.recycle()
                capture(g, tag + "-battle")
            }
        } finally {
            Palette.select(oldPalette)
            L.lang = oldLanguage
        }
    }

    @Test
    fun minimapMarkersHaveDistinctShapesEvenWithoutColor() {
        val ui = Ui()
        ui.dp = 1f
        fun footprint(side: Int, marked: Boolean): BooleanArray {
            val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
            ui.miniTeamMarker(Canvas(bitmap), 24f, 24f, 9f, side, marked)
            val pixels = IntArray(48 * 48)
            bitmap.getPixels(pixels, 0, 48, 0, 0, 48, 48)
            bitmap.recycle()
            return BooleanArray(pixels.size) { (pixels[it] ushr 24) > 0 }
        }
        val player = footprint(0, true)
        val enemy = footprint(1, true)
        assertTrue("Trójkąt i szewron muszą różnić się geometrią",
            player.indices.count { player[it] != enemy[it] } >= 20)
        assertTrue("Przy wyłączonych znacznikach pozostają zwykłe okręgi",
            footprint(0, false).contentEquals(footprint(1, false)))
    }
}
