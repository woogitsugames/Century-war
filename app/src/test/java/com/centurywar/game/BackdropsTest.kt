package com.centurywar.game

import android.graphics.Bitmap
import android.graphics.Canvas
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.Palette
import com.centurywar.engine.Quality
import com.centurywar.game.art.Backdrops
import com.centurywar.meta.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Cache nie może odtwarzać kształtów na każdej klatce. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class BackdropsTest {
    private fun game(): Game {
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val file = File.createTempFile("backdrops", ".json").apply { delete() }
        return Game(host, Profile(file), Quality.LOW).apply {
            density = 2f
            resize(960, 540, 960, 540)
            setQuality(Quality.LOW)
        }
    }

    @Test
    fun picturesAreRecordedOnlyAfterVisualStateChanges() {
        val paletteBefore = Palette.selected
        val bitmap = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
        try {
            val g = game()
            val battle = Battle(BattleConfig(Mode.SKIRMISH, 56L, 1f,
                startEra = 5, maxEra = 8))
            val backdrops = Backdrops(g, battle)
            val canvas = Canvas(bitmap)

            fun draw(w: Float = 960f, ground: Float = 480f, cam: Float = 0f) {
                backdrops.drawSky(canvas, w, 540f, ground, 0f)
                backdrops.drawLandscape(canvas, w, ground, cam, 1f)
            }

            draw()
            assertEquals(EraArt.STEAM, backdrops.artInCache)
            assertEquals(1, backdrops.cacheBuilds)
            assertEquals(1, backdrops.lastDrawnLayers)
            repeat(240) { draw(cam = it * 3f) }
            assertEquals("Ruch kamery nie nagrywa kolejnych Picture", 1, backdrops.cacheBuilds)

            g.setQuality(Quality.HIGH)
            draw()
            assertEquals(2, backdrops.cacheBuilds)
            assertEquals(3, backdrops.lastDrawnLayers)

            assertTrue(battle.chooseVariant(0))
            draw()
            assertEquals(EraArt.COMBUSTION, backdrops.artInCache)
            assertEquals(3, backdrops.cacheBuilds)

            Palette.select(Palette.HIGH_CONTRAST)
            draw()
            assertEquals(4, backdrops.cacheBuilds)

            draw(w = 800f)
            assertEquals(5, backdrops.cacheBuilds)

            backdrops.invalidate()
            draw(w = 800f)
            assertEquals(6, backdrops.cacheBuilds)
        } finally {
            Palette.select(paletteBefore)
            bitmap.recycle()
        }
    }
}
