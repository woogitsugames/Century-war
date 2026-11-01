package com.centurywar.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.Paint
import android.graphics.Picture

/**
 * Błysk bazy bez saveLayer na każdą klatkę trafienia.
 * Canvas.drawPicture nie przyjmuje Paint: filtr nakładamy na bitmapową kopię
 * Picture, odświeżaną wyłącznie po zmianie nagranego korpusu bazy.
 */
internal class BaseFlashCache(width: Int, height: Int) {
    private val images = Array(2) { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) }
    private val painters = Array(2) { Canvas(images[it]) }
    private val sources = arrayOfNulls<Picture>(2)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

    // LightingColorFilter: RGB' = RGB * (255-a)/255 + a, alfa bez zmian.
    // Odpowiednik białej warstwy SRC_ATOP, ale bez alokacji filtra na klatkę.
    private val filters = Array(111) { a ->
        val multiplier = Color.rgb(255 - a, 255 - a, 255 - a)
        val addition = Color.rgb(a, a, a)
        LightingColorFilter(multiplier, addition)
    }

    fun draw(c: Canvas, picture: Picture, side: Int, alpha: Int) {
        if (sources[side] !== picture) {
            images[side].eraseColor(Color.TRANSPARENT)
            painters[side].drawPicture(picture)
            sources[side] = picture
        }
        paint.colorFilter = filters[alpha.coerceIn(0, 110)]
        c.drawBitmap(images[side], 0f, 0f, paint)
    }
}
