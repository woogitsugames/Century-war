package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.meta.Profile
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Sześć obrazów regresyjnych: trzy epoki na telefonie 720p i rozłożonym Foldzie. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class EraIntroScreenshotTest {
    private val output = File("build/screenshots").apply { mkdirs() }

    private fun game(width: Int, height: Int, density: Float, quality: Int): Game {
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val file = File.createTempFile("intro-shot", ".json").apply { delete() }
        val g = Game(host, Profile(file), quality)
        g.density = density
        g.resize(width, height, width, height)
        g.profile.reduceFlash = quality == Quality.LOW
        return g
    }

    @Test
    fun threeErasOn720pAndFoldPortrait() {
        val previousLanguage = L.lang
        try {
            L.lang = L.PL
            for ((device, dims) in listOf(
                "phone720" to intArrayOf(720, 1280, 2),
                "foldPortrait" to intArrayOf(1968, 2184, 3),
            )) {
                val g = game(dims[0], dims[1], dims[2].toFloat(),
                    if (device == "phone720") Quality.LOW else Quality.HIGH)
                for (era in intArrayOf(0, 4, 8)) {
                    val overlay = EraIntroOverlay.preview(g, era)
                    overlay.layout()
                    val bitmap = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    overlay.draw(canvas)
                    assertTrue(bitmap.getPixel(g.width / 2, g.height / 2) != Color.TRANSPARENT)
                    val image = File(output, "era68_${device}_${era}.png")
                    image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    assertTrue(image.isFile && image.length() > 3000L)
                    bitmap.recycle()
                }
            }
        } finally {
            L.lang = previousLanguage
        }
    }
}
