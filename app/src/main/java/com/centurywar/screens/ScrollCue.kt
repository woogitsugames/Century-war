package com.centurywar.screens

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.centurywar.engine.Ui

/** Wspólne wskazówki przewijania: tylko dostępne kierunki, bez alokacji na klatkę. */
internal class ScrollCue {
    private val background = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    fun vertical(c: Canvas, x: Float, top: Float, bottom: Float,
                 scroll: Float, maxScroll: Float, dp: Float) {
        if (maxScroll <= dp || bottom - top < 35 * dp) return
        if (scroll > dp) arrow(c, x, top + 12 * dp, 0, dp)
        if (scroll < maxScroll - dp) arrow(c, x, bottom - 12 * dp, 1, dp)
    }

    fun horizontal(c: Canvas, left: Float, right: Float, y: Float,
                   scroll: Float, maxScroll: Float, dp: Float) {
        if (maxScroll <= dp || right - left < 60 * dp) return
        if (scroll > dp) arrow(c, left + 12 * dp, y, 2, dp)
        if (scroll < maxScroll - dp) arrow(c, right - 12 * dp, y, 3, dp)
    }

    private fun arrow(c: Canvas, x: Float, y: Float, direction: Int, dp: Float) {
        background.color = Color.argb(220, 24, 30, 45)
        c.drawRoundRect(x - 11 * dp, y - 10 * dp, x + 11 * dp, y + 10 * dp,
            7 * dp, 7 * dp, background)
        line.color = Ui.GOLD
        line.strokeWidth = 2.5f * dp
        when (direction) {
            0 -> {
                c.drawLine(x - 5 * dp, y + 2 * dp, x, y - 3 * dp, line)
                c.drawLine(x, y - 3 * dp, x + 5 * dp, y + 2 * dp, line)
            }
            1 -> {
                c.drawLine(x - 5 * dp, y - 2 * dp, x, y + 3 * dp, line)
                c.drawLine(x, y + 3 * dp, x + 5 * dp, y - 2 * dp, line)
            }
            2 -> {
                c.drawLine(x + 2 * dp, y - 5 * dp, x - 3 * dp, y, line)
                c.drawLine(x - 3 * dp, y, x + 2 * dp, y + 5 * dp, line)
            }
            3 -> {
                c.drawLine(x - 2 * dp, y - 5 * dp, x + 3 * dp, y, line)
                c.drawLine(x + 3 * dp, y, x - 2 * dp, y + 5 * dp, line)
            }
        }
    }
}
