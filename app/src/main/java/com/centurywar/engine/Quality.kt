package com.centurywar.engine

import android.app.ActivityManager
import android.content.Context

/**
 * Profil wydajności. Na tanich telefonach renderujemy w mniejszej rozdzielczości
 * (sprzętowy skaler ekranu powiększa obraz za darmo), z mniejszą liczbą cząsteczek
 * i bez antyaliasingu. Na flagowcach i Foldach w pełnej rozdzielczości.
 */
class Quality(val level: Int) {
    companion object {
        const val LOW = 1
        const val MED = 2
        const val HIGH = 3
        /** Górny limit pikseli bufora: powyżej tego różnica jest niewidoczna, a koszt rośnie. */
        private const val MAX_PIXELS = 3_900_000f

        fun detect(ctx: Context): Int {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            if (am.isLowRamDevice) return LOW
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            val gb = mi.totalMem / (1024f * 1024f * 1024f)
            val cores = Runtime.getRuntime().availableProcessors()
            return when {
                gb < 3.2f || cores <= 4 -> LOW
                gb < 6.5f -> MED
                else -> HIGH
            }
        }
    }

    val baseScale = when (level) { LOW -> 0.6f; MED -> 0.8f; else -> 1f }
    val maxParticles = when (level) { LOW -> 70; MED -> 160; else -> 320 }
    val antialias = level >= MED
    val parallaxLayers = when (level) { LOW -> 1; MED -> 2; else -> 3 }
    val clouds = level >= MED
    val shadows = level >= HIGH

    fun renderScale(viewW: Int, viewH: Int): Float {
        val px = viewW.toFloat() * viewH
        val cap = if (px * baseScale * baseScale > MAX_PIXELS) kotlin.math.sqrt(MAX_PIXELS / px) else baseScale
        return cap.coerceIn(0.4f, 1f)
    }
}
