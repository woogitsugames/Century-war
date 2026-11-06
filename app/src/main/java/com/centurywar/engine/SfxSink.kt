package com.centurywar.engine

/**
 * D63: ujście efektów bitwy. Na urządzeniu to [Game] (→ [Sound]); w testach JVM — mikser
 * pomiarowy głośności. Wartości domyślne jak w [Game.sfx].
 */
interface SfxSink {
    fun sfx(
        id: Int, vol: Float = 1f, gap: Long = 45L, pitch: Float = 1f,
        pan: Float = 0f, priority: Int = 1,
    )
}
