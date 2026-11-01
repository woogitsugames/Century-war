package com.centurywar.game

import com.centurywar.engine.Quality

/**
 * Limit pamięci bitmap pochodzący z limitu sterty aplikacji, nie z całego RAM telefonu.
 * Dla LOW rezerwujemy najwyżej 2 MiB lub 1/32 memoryClass (mniejszą wartość).
 */
internal class BitmapCacheBudget(private val memoryClassMb: Int) {
    companion object {
        private const val MIB = 1024L * 1024L
    }

    fun limitBytes(quality: Int): Long {
        val heapShare = memoryClassMb.coerceAtLeast(1).toLong() * MIB / 32L
        val qualityCap = when (quality) {
            Quality.LOW -> 2L * MIB
            Quality.MED -> 4L * MIB
            else -> 8L * MIB
        }
        return minOf(heapShare, qualityCap)
    }

    fun bitmapSlots(quality: Int, width: Int, height: Int): Int {
        if (width <= 0 || height <= 0) return 0
        val bytesPerBitmap = width.toLong() * height.toLong() * 4L
        return (limitBytes(quality) / bytesPerBitmap).coerceIn(0L, 2L).toInt()
    }
}
