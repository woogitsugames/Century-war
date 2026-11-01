package com.centurywar.engine

import android.graphics.Canvas
import kotlin.math.sin

/**
 * Lot medali do plakietki nagłówka. Cząstki używają stałej puli tablic, bez
 * obiektów, napisów i buforów tworzonych podczas klatek animacji.
 */
internal class RewardFeedback {
    companion object {
        private const val CAPACITY = 16
        private const val DURATION = 0.72f
    }

    private val originX = FloatArray(CAPACITY)
    private val originY = FloatArray(CAPACITY)
    private val age = FloatArray(CAPACITY) { -1f }
    private val delay = FloatArray(CAPACITY)
    private val spread = FloatArray(CAPACITY)
    private var next = 0
    private var count = 0
    private var shownMedals = Int.MIN_VALUE
    private var medalsText = ""

    internal val activeCount: Int get() = count

    /** Współrzędne względne przetrwają zmianę rozmiaru w trakcie animacji. */
    fun launch(g: Game, x: Float, y: Float, medals: Int) {
        if (medals <= 0 || g.profile.reduceShake) return
        val n = if (g.profile.reduceFlash) 3 else 7
        val nx = if (g.width > 1) (x / g.width).coerceIn(0f, 1f) else 0.5f
        val ny = if (g.height > 1) (y / g.height).coerceIn(0f, 1f) else 0.62f
        for (i in 0 until n) {
            val slot = next
            next = (next + 1) % CAPACITY
            if (age[slot] < 0f) count++
            originX[slot] = nx
            originY[slot] = ny
            age[slot] = 0f
            delay[slot] = i * 0.045f
            spread[slot] = (i - n / 2) * 10f
        }
    }

    /** Rysowanie nad ekranem; docelowy środek odpowiada ikonie w Chrome.badges(). */
    fun draw(c: Canvas, g: Game, dt: Float) {
        if (count == 0) return
        if (g.profile.reduceShake) { clear(); return }
        val value = g.profile.medals
        if (value != shownMedals) {
            shownMedals = value
            medalsText = value.toString()
        }
        val d = g.ui.dp
        val badgeH = 16f * d * 1.9f * g.ui.textScale.coerceAtMost(1.15f)
        val targetX = g.width - g.insetR - 14f * d -
            g.ui.badgeWidth(medalsText, 16f) + badgeH * 0.45f
        val targetY = g.insetT + 32f * d
        val delta = dt.coerceIn(0f, 0.1f)
        for (i in 0 until CAPACITY) {
            if (age[i] < 0f) continue
            age[i] += delta
            val t = (age[i] - delay[i]) / DURATION
            if (t >= 1f) {
                age[i] = -1f
                count--
                continue
            }
            if (t < 0f) continue
            val smooth = t * t * (3f - 2f * t)
            val arc = sin(t * 3.1415927f) * 35f * d
            val x0 = originX[i] * g.width
            val y0 = originY[i] * g.height
            val x = x0 + (targetX - x0) * smooth + spread[i] * d * (1f - t)
            val y = y0 + (targetY - y0) * smooth - arc
            Icons.draw(c, Icons.MEDAL, x, y, (16f - 5f * smooth) * d, dp = d)
        }
    }

    fun clear() {
        for (i in age.indices) age[i] = -1f
        count = 0
    }
}
