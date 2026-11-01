package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ScreenshotGoldenTest {
    private fun image(width: Int = 20, height: Int = 20): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(20, 50, 100))
        }

    @Test
    fun identicalPixelsPass() {
        val a = image()
        val b = image()
        try {
            val result = ScreenshotGolden.compare(a, b, "phone720_reg100_settings")
            assertTrue(result.passes)
            assertEquals(400L, result.inspectedPixels)
            assertEquals(0L, result.changedPixels)
        } finally {
            a.recycle()
            b.recycle()
        }
    }

    @Test
    fun smallRasterizationDifferenceIsIgnored() {
        val a = image()
        val b = image()
        try {
            b.setPixel(1, 1, Color.rgb(35, 50, 100))
            assertTrue(ScreenshotGolden.compare(a, b, "foldOpen_reg100_research").passes)
        } finally {
            a.recycle()
            b.recycle()
        }
    }

    @Test
    fun realDifferenceFailsAndProducesRedDiff() {
        val a = image()
        val b = image()
        try {
            b.setPixel(1, 1, Color.WHITE)
            val result = ScreenshotGolden.compare(a, b, "phone720_reg100_campaign")
            assertFalse(result.passes)
            assertEquals(1L, result.changedPixels)
            val diff = ScreenshotGolden.renderDiff(a, b, "phone720_reg100_campaign")
            try {
                assertEquals(Color.rgb(255, 25, 65), diff.getPixel(1, 1))
            } finally {
                diff.recycle()
            }
        } finally {
            a.recycle()
            b.recycle()
        }
    }

    @Test
    fun battleHudIsComparedWithoutMask() {
        val a = image(100, 100)
        val b = image(100, 100)
        try {
            val name = "foldOpen_reg100_battle_hud"
            // Środek pola bitwy nie jest już maskowany: 11 pikseli > 0,1% z 10 000.
            for (i in 0 until 11) b.setPixel(10 + i, 50, Color.WHITE)
            val result = ScreenshotGolden.compare(a, b, name)
            assertFalse(result.passes)
            assertEquals(0L, result.maskedPixels)
            assertEquals(11L, result.changedPixels)
            assertEquals(10_000L, result.inspectedPixels)
            // Do 0,1% zmian (10 pikseli) mieści się w tolerancji rasteryzacji.
            b.setPixel(10, 50, Color.rgb(20, 50, 100))
            assertTrue(ScreenshotGolden.compare(a, b, name).passes)
        } finally {
            a.recycle()
            b.recycle()
        }
    }

    @Test
    fun sizeMismatchFailsEvenIfSharedPixelsMatch() {
        val a = image()
        val b = image(21, 20)
        try {
            val result = ScreenshotGolden.compare(a, b, "phone720_reg100_menu")
            assertFalse(result.sameSize)
            assertFalse(result.passes)
        } finally {
            a.recycle()
            b.recycle()
        }
    }
}
