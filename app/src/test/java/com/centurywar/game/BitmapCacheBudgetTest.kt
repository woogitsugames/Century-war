package com.centurywar.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Picture
import com.centurywar.engine.Quality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Test rzeczywistych bitmap i limitów klasy pamięci, nie tylko wyliczeń. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class BitmapCacheBudgetTest {
    @Test fun slotsRespectMemoryClassAndLowLimit() {
        val low64 = BitmapCacheBudget(64)
        assertEquals(2L * 1024L * 1024L, low64.limitBytes(Quality.LOW))
        assertEquals(1, low64.bitmapSlots(Quality.LOW, 460, 580))
        assertEquals(0, BitmapCacheBudget(32).bitmapSlots(Quality.LOW, 460, 580))
        assertEquals(2, BitmapCacheBudget(256).bitmapSlots(Quality.HIGH, 460, 580))
        assertTrue(BitmapCacheBudget(128).limitBytes(Quality.LOW) <= 2L * 1024 * 1024)
    }

    private fun picture(): Picture = Picture().apply {
        val canvas = beginRecording(460, 580)
        val paint = Paint().apply { color = Color.BLUE }
        canvas.drawRect(25f, 25f, 300f, 400f, paint)
        endRecording()
    }

    @Test fun lazyCacheForTwoTeamsNeverExceedsLowBudget() {
        val picture = picture()
        val output = Bitmap.createBitmap(460, 580, Bitmap.Config.ARGB_8888)
        val cache = BaseFlashCache(460, 580, 64, Quality.LOW)
        assertEquals(0L, cache.cachedBytes)
        val canvas = Canvas(output)
        cache.draw(canvas, picture, 0, 70)
        assertTrue(cache.cachedBytes > 0L)
        assertTrue(cache.cachedBytes <= cache.budgetBytes)
        cache.draw(canvas, picture, 1, 70)
        assertTrue(cache.cachedBytes <= cache.budgetBytes)
        output.recycle()
    }

    @Test fun qualityDropRecyclesSecondBitmapAndTinyHeapSkipsCache() {
        val picture = picture()
        val output = Bitmap.createBitmap(460, 580, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val cache = BaseFlashCache(460, 580, 256, Quality.HIGH)
        cache.draw(canvas, picture, 0, 70)
        cache.draw(canvas, picture, 1, 70)
        assertEquals(2L * 460L * 580L * 4L, cache.cachedBytes)
        cache.setQuality(Quality.LOW)
        assertTrue(cache.cachedBytes <= cache.budgetBytes)
        cache.draw(canvas, picture, 1, 70)
        assertTrue(cache.cachedBytes <= cache.budgetBytes)

        val tiny = BaseFlashCache(460, 580, 16, Quality.LOW)
        tiny.draw(canvas, picture, 0, 70)
        assertEquals(0L, tiny.cachedBytes)
        output.recycle()
    }
}
