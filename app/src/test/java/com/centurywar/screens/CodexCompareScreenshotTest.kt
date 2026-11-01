package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Palette
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

/** Zrzuty nowej zakładki w rozdzielczościach telefonu, Folda i tabletu. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CodexCompareScreenshotTest {
    private val output = File("build/screenshots").apply { mkdirs() }

    private fun game(width: Int, height: Int, density: Float): Game {
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val file = File.createTempFile("codex134-profile", ".json").apply { delete() }
        return Game(host, Profile(file).apply { tutorialDone = true }, Quality.LOW).apply {
            this.density = density
            resize(width, height, width, height)
            applyAccessibility()
        }
    }

    @Test
    fun compareFourScreenSizesAndColorBlindPalette() {
        val oldLang = L.lang
        val oldPalette = Palette.selected
        try {
            L.lang = L.PL
            val sizes = listOf(
                "phone720" to intArrayOf(1600, 720, 20),
                "fold_cover" to intArrayOf(904, 2316, 27),
                "fold_inner" to intArrayOf(2184, 1968, 30),
                "tablet" to intArrayOf(1600, 1200, 20),
            )
            for ((name, dims) in sizes) {
                Palette.select(if (name == "fold_inner") Palette.DEUTAN_PROTAN else Palette.STANDARD)
                val g = game(dims[0], dims[1], dims[2] / 10f)
                val codex = CodexScreen(g, 2)
                g.setScreen(codex)
                codex.showCategory(10)
                assertEquals(8, codex.buttons.count { it.id in 300..307 && it.visible })
                // Wymuś inne jednostki po obu stronach przed wykonaniem zrzutu.
                codex.onClick(codex.buttons.first { it.id == 301 })
                codex.onClick(codex.buttons.first { it.id == 307 })
                g.finishScreenTransition()
                val bitmap = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                repeat(3) { g.frame(canvas, 1f / 60f) }
                val file = File(output, "codex134_$name.png")
                file.outputStream().use {
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                assertTrue("Pusty zrzut: $name", file.length() > 3000L)
                bitmap.recycle()
            }
        } finally {
            L.lang = oldLang
            Palette.select(oldPalette)
        }
    }
}
