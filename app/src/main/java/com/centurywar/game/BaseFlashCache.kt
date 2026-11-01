package com.centurywar.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.Paint
import android.graphics.Picture
import com.centurywar.engine.Quality

/**
 * Bitmapy błysku są tworzone dopiero przy pierwszym trafieniu bazy.
 * Na LOW obie strony mogą współdzielić jedną bitmapę; przy zbyt małym budżecie
 * rysujemy samo Picture, zamiast ryzykować OutOfMemoryError.
 */
internal class BaseFlashCache(
    private val width: Int,
    private val height: Int,
    memoryClassMb: Int = 256,
    qualityLevel: Int = Quality.HIGH
) {
    private val budget = BitmapCacheBudget(memoryClassMb)
    private val images = arrayOfNulls<Bitmap>(2)
    private val painters = arrayOfNulls<Canvas>(2)
    private val sources = arrayOfNulls<Picture>(2)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private var quality = -1
    private var slots = 0

    // LightingColorFilter: RGB' = RGB * (255-a)/255 + a, alfa bez zmian.
    private val filters = Array(111) { a ->
        val multiplier = Color.rgb(255 - a, 255 - a, 255 - a)
        val addition = Color.rgb(a, a, a)
        LightingColorFilter(multiplier, addition)
    }

    init { setQuality(qualityLevel) }

    /** Wywoływane przed renderem; po spadku jakości nadmiarowe bitmapy zwalniamy. */
    fun setQuality(level: Int) {
        if (quality == level) return
        quality = level
        slots = budget.bitmapSlots(level, width, height)
        for (i in slots until images.size) {
            images[i]?.recycle()
            images[i] = null
            painters[i] = null
            sources[i] = null
        }
    }

    internal val budgetBytes: Long get() = budget.limitBytes(quality)
    internal val cachedBytes: Long get() =
        images.count { it != null }.toLong() * width.toLong() * height.toLong() * 4L

    fun draw(c: Canvas, picture: Picture, side: Int, alpha: Int) {
        if (slots == 0) {
            c.drawPicture(picture)
            return
        }
        val slot = if (slots == 1) 0 else side
        val image = images[slot] ?: Bitmap.createBitmap(
            width, height, Bitmap.Config.ARGB_8888
        ).also {
            images[slot] = it
            painters[slot] = Canvas(it)
        }
        if (sources[slot] !== picture) {
            image.eraseColor(Color.TRANSPARENT)
            painters[slot]!!.drawPicture(picture)
            sources[slot] = picture
        }
        paint.colorFilter = filters[alpha.coerceIn(0, 110)]
        c.drawBitmap(image, 0f, 0f, paint)
    }
}
