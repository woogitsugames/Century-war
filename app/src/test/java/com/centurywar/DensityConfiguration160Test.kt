package com.centurywar

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import com.centurywar.engine.Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Regresja A156-20: configChanges nie restartuje ekranu, lecz zmienia skalę UI. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DensityConfiguration160Test {
    @Test fun foldDensityChangeRecalculatesScaleOnGameThread() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            val field = MainActivity::class.java.getDeclaredField("game").apply { isAccessible = true }
            val game = field.get(activity) as Game
            game.density = 1f
            game.resize(2000, 1200, 2000, 1200)
            val before = game.ui.dp
            val current = game.current
            activity.onConfigurationChanged(Configuration(activity.resources.configuration).apply {
                densityDpi = 480
            })
            game.frame(Canvas(Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)), 0f)
            assertEquals(3f, game.density, 0.001f)
            assertTrue(before != game.ui.dp)
            assertSame(current, game.current)
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
