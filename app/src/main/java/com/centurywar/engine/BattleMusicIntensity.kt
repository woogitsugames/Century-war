package com.centurywar.engine

/**
 * Odczyt napięcia bitwy wyłącznie z danych renderera.
 * Nie dotyka RNG, stanu symulacji, zapisów ani powtórek.
 */
object BattleMusicIntensity {
    fun level(
        allyFront: Float,
        enemyFront: Float,
        baseFront: Float,
        lane: Float,
        baseHpRatio: Float,
    ): Float {
        if (!lane.isFinite() || lane <= 0f) return 0f
        val health = if (baseHpRatio.isFinite()) baseHpRatio.coerceIn(0f, 1f) else 1f
        val healthLoss = 1f - health
        // Słaby niepokój po uszkodzeniu bazy utrzymuje się także między falami.
        if (!enemyFront.isFinite()) return healthLoss * 0.30f

        val proximity = (1f - (enemyFront - baseFront) / (lane * 0.35f)).coerceIn(0f, 1f)
        val engagement = if (allyFront.isFinite() && allyFront + lane * 0.12f >= enemyFront) {
            0.60f
        } else 0.18f
        return maxOf(engagement, proximity * 0.95f,
            healthLoss * (0.30f + 0.55f * proximity)).coerceIn(0f, 1f)
    }
}
