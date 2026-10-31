package com.centurywar.engine

/**
 * Sterownik jakości oparty na czasie pracy CPU, a nie odstępie między klatkami.
 * Celem jest budżet 60 Hz także na wyświetlaczu 90/120 Hz.
 */
class QualityGovernor {
    var reduction = 0
        private set
    private var slowFor = 0f
    private var spareFor = 0f
    private var sinceChange = 3f

    fun reset(): Boolean {
        val changed = reduction != 0
        reduction = 0
        slowFor = 0f
        spareFor = 0f
        sinceChange = 3f
        return changed
    }

    fun record(cpuMs: Float, seconds: Float, automatic: Boolean, fpsCap: Int, maximum: Int): Boolean {
        if (!automatic) return reset()
        val dt = seconds.coerceIn(0f, 0.1f)
        val budget = budgetMs(fpsCap)
        sinceChange += dt
        when {
            cpuMs > budget * 1.03f -> { slowFor += dt; spareFor = 0f }
            cpuMs <= budget * 0.70f -> { spareFor += dt; slowFor = 0f }
            else -> { slowFor = 0f; spareFor = 0f }
        }
        if (sinceChange < 3f) return false
        if (slowFor >= 4f && reduction < maximum) {
            reduction++
            sinceChange = 0f; slowFor = 0f; spareFor = 0f
            return true
        }
        if (spareFor >= 10f && reduction > 0) {
            reduction--
            sinceChange = 0f; slowFor = 0f; spareFor = 0f
            return true
        }
        return false
    }

    companion object {
        fun budgetMs(fpsCap: Int): Float = if (fpsCap == 30) 1000f / 30f else 1000f / 60f
    }
}
