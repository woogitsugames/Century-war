package com.centurywar.engine

import android.app.ActivityManager
import android.content.Context

/**
 * Profil wydajności. Na tanich telefonach renderujemy w mniejszej rozdzielczości (75–90%)
 * (sprzętowy skaler ekranu powiększa obraz za darmo), z mniejszą liczbą cząsteczek
 * i bez drobnych detali (faktury, nity, cieniowanie). Na flagowcach i Foldach w pełnej rozdzielczości.
 */
class Quality(val level: Int, val reduction: Int = 0, val gpuReduction: Int = 0) {
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

    private val cpuReduction = (reduction - gpuReduction).coerceAtLeast(0)
    private val normalScale = when (level) { LOW -> 0.75f; MED -> 0.9f; else -> 1f }
    /** Pierwsze cztery stopnie oszczędzają efekty; kolejne obniżają bufor po 0,05. */
    val maxReductionSteps = 4 + ((normalScale - 0.6f + 0.001f) / 0.05f).toInt()
    // Przy ograniczeniu GPU skala spada od pierwszego stopnia, bez czekania na efekty.
    val baseScale = (normalScale -
        ((cpuReduction - 4).coerceAtLeast(0) + gpuReduction) * 0.05f).coerceAtLeast(0.6f)
    private val particleBase = when (level) { LOW -> 70; MED -> 160; else -> 320 }
    val maxParticles = when {
        cpuReduction <= 0 -> particleBase
        cpuReduction == 1 -> particleBase * 3 / 4
        cpuReduction == 2 -> particleBase / 2
        else -> particleBase / 3
    }
    /** Kontury postaci zachowują AA także przy obniżonej jakości. */
    val antialias = true
    val parallaxLayers = (when (level) { LOW -> 1; MED -> 2; else -> 3 } -
        if (cpuReduction >= 3) 1 else 0).coerceAtLeast(1)
    val clouds = level >= MED && cpuReduction < 2
    val shadows = level >= HIGH && cpuReduction < 4 && gpuReduction == 0

    /** Ciężkie elementy menu redukujemy także przy dynamicznym przeciążeniu CPU/GPU. */
    val menuDetail = when {
        level == LOW || cpuReduction >= 3 || gpuReduction >= 2 -> LOW
        level == MED || reduction > 0 -> MED
        else -> HIGH
    }
    val menuAnimated = menuDetail > LOW && cpuReduction < 2 && gpuReduction < 2

    fun renderScale(viewW: Int, viewH: Int): Float {
        val px = viewW.toFloat().coerceAtLeast(1f) * viewH.coerceAtLeast(1)
        val cap = if (px * baseScale * baseScale > MAX_PIXELS) kotlin.math.sqrt(MAX_PIXELS / px) else baseScale
        return cap.coerceIn(0.6f, 1f)
    }
}
