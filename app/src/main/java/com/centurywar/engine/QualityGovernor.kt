package com.centurywar.engine

/**
 * Sterownik jakości z osobnymi sygnałami przeciążenia CPU i GPU/kompozytora.
 * Cel to 60 FPS (lub limit użytkownika), niezależnie od odświeżania 90/120 Hz.
 */
class QualityGovernor {
    var reduction = 0
        private set
    /** Stopnie redukcji przeznaczone najpierw na skalę bufora i cienie. */
    var gpuReduction = 0
        private set
    private var slowFor = 0f
    private var gpuSlowFor = 0f
    private var spareFor = 0f
    private var sinceChange = 3f

    fun reset(): Boolean {
        val changed = reduction != 0
        reduction = 0
        gpuReduction = 0
        slowFor = 0f
        gpuSlowFor = 0f
        spareFor = 0f
        sinceChange = 3f
        return changed
    }

    /**
     * Brak pomiaru odstępu (0 ms) pozostawia poprzednie zachowanie CPU-only,
     * np. w testach ekranów rysowanych bez SurfaceView.
     */
    fun record(
        cpuMs: Float, seconds: Float, automatic: Boolean, fpsCap: Int, maximum: Int,
        frameIntervalMs: Float = 0f, refreshHz: Float = 60f
    ): Boolean {
        if (!automatic) return reset()
        val dt = seconds.coerceIn(0f, 0.1f)
        val budget = budgetMs(fpsCap)
        val target = targetFrameMs(fpsCap, refreshHz)
        val cpuSlow = cpuMs > budget * 1.03f
        val gpuSlow = frameIntervalMs > target * 1.35f && cpuMs <= budget * 0.80f
        val gpuHealthy = frameIntervalMs <= 0f || frameIntervalMs <= target * 1.10f
        val cpuSpare = cpuMs <= budget * 0.70f

        sinceChange += dt
        when {
            cpuSlow -> { slowFor += dt; gpuSlowFor = 0f; spareFor = 0f }
            gpuSlow -> { gpuSlowFor += dt; slowFor = 0f; spareFor = 0f }
            cpuSpare && gpuHealthy -> { spareFor += dt; slowFor = 0f; gpuSlowFor = 0f }
            else -> { slowFor = 0f; gpuSlowFor = 0f; spareFor = 0f }
        }
        if (sinceChange < 3f) return false
        if (gpuSlowFor >= 3f && reduction < maximum) {
            reduction++
            // Gdy skala osiągnie minimum, kolejne stopnie ograniczają efekty.
            if (gpuReduction < (maximum - 4).coerceAtLeast(0)) gpuReduction++
            changed()
            return true
        }
        if (slowFor >= 4f && reduction < maximum) {
            reduction++
            changed()
            return true
        }
        if (spareFor >= 10f && reduction > 0) {
            reduction--
            if (gpuReduction > 0) gpuReduction--
            changed()
            return true
        }
        return false
    }

    private fun changed() {
        sinceChange = 0f
        slowFor = 0f
        gpuSlowFor = 0f
        spareFor = 0f
    }

    companion object {
        fun budgetMs(fpsCap: Int): Float = if (fpsCap == 30) 1000f / 30f else 1000f / 60f

        /** Ekran 90/120 Hz nie wymusza 90/120 FPS; 30 FPS jest świadomym limitem. */
        fun targetFrameMs(fpsCap: Int, refreshHz: Float): Float {
            val hz = if (refreshHz.isFinite() && refreshHz >= 30f) refreshHz.coerceAtMost(240f) else 60f
            val goal = if (fpsCap == 30) 30f else 60f
            return 1000f / minOf(goal, hz)
        }
    }
}
