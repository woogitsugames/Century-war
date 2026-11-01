package com.centurywar.screens

import android.graphics.Canvas
import android.graphics.RectF
import com.centurywar.engine.Ui
import kotlin.math.max
import kotlin.math.min

/** Wspólna oprawa dymków i samouczka; bez alokacji podczas rysowania. */
internal object HintFrame {
    private const val ABOVE = 0
    private const val BELOW = 1
    private const val LEFT = 2
    private const val RIGHT = 3

    fun chrome(c: Canvas, ui: Ui, rect: RectF, title: String, dp: Float) {
        ui.panel(c, rect)
        ui.ribbon(c, rect.centerX(), rect.top + 4 * dp, title, 18f, Ui.RIBBON,
            rect.width() * 0.45f, Ui.GOLD, rect.width() - 20 * dp)
    }

    /** Łamie tekst wyłącznie przy przygotowaniu dymka, nigdy podczas renderowania. */
    fun wrapInto(ui: Ui, s: String, width: Float, size: Float, out: Array<String?>): Int {
        var n = 0
        var line = ""
        for (word in s.split(' ')) {
            if (word.isEmpty()) continue
            val combined = if (line.isEmpty()) word else "$line $word"
            if (line.isNotEmpty() && ui.measure(combined, size) > width) {
                if (n == out.size) return n
                out[n++] = line
                line = ""
            }
            var remaining = word
            while (ui.measure(remaining, size) > width && remaining.length > 1) {
                var length = 1
                while (length < remaining.length &&
                    ui.measure(remaining.substring(0, length + 1), size) <= width) length++
                if (n == out.size) return n
                out[n++] = remaining.substring(0, length)
                remaining = remaining.substring(length)
            }
            line = if (line.isEmpty()) remaining else "$line $remaining"
        }
        if (line.isNotEmpty() && n < out.size) out[n++] = line
        return n
    }

    /**
     * Umieszcza panel wyłącznie w wolnej przestrzeni obok celu.
     * Gdy nie ma miejsca, zwraca false zamiast zasłaniać wskazany element.
     * [link] przechowuje początek i koniec wskaźnika (nie jest obszarem do rysowania).
     */
    fun place(
        target: RectF?, safeL: Float, safeT: Float, safeR: Float, safeB: Float,
        width: Float, height: Float, gap: Float, out: RectF, link: RectF,
    ): Boolean {
        link.setEmpty()
        val availableW = safeR - safeL
        val availableH = safeB - safeT
        if (width <= 0f || height <= 0f || width > availableW || height > availableH) {
            out.setEmpty()
            return false
        }
        if (target == null || target.width() <= 0f || target.height() <= 0f) {
            val x = safeL + (availableW - width) * 0.5f
            val y = safeT + (availableH - height) * 0.5f
            out.set(x, y, x + width, y + height)
            return true
        }

        val above = target.top - gap - height >= safeT
        val below = target.bottom + gap + height <= safeB
        val left = target.left - gap - width >= safeL
        val right = target.right + gap + width <= safeR
        val wide = target.width() >= availableW * 0.42f
        val tall = target.height() >= availableH * 0.55f
        val side = when {
            // Szerokie karty misji dokujemy nad nimi, by nie zasłaniać przycisków odbioru.
            wide && above -> ABOVE
            tall && target.centerX() >= (safeL + safeR) * 0.5f && left -> LEFT
            tall && right -> RIGHT
            target.centerX() >= (safeL + safeR) * 0.5f && left -> LEFT
            target.centerX() < (safeL + safeR) * 0.5f && right -> RIGHT
            above && (target.centerY() >= (safeT + safeB) * 0.5f || !below) -> ABOVE
            below -> BELOW
            above -> ABOVE
            left -> LEFT
            right -> RIGHT
            else -> {
                out.setEmpty()
                return false
            }
        }

        when (side) {
            ABOVE, BELOW -> {
                val x = (target.centerX() - width * 0.5f).coerceIn(safeL, safeR - width)
                val y = if (side == ABOVE) target.top - gap - height else target.bottom + gap
                out.set(x, y, x + width, y + height)
                val ax = target.centerX().coerceIn(x + min(9f, width * 0.2f),
                    x + width - min(9f, width * 0.2f))
                link.set(ax, if (side == ABOVE) out.bottom else out.top,
                    target.centerX(), if (side == ABOVE) target.top else target.bottom)
            }
            else -> {
                val x = if (side == LEFT) target.left - gap - width else target.right + gap
                val y = (target.centerY() - height * 0.5f).coerceIn(safeT, safeB - height)
                out.set(x, y, x + width, y + height)
                val ay = target.centerY().coerceIn(y + min(9f, height * 0.2f),
                    y + height - min(9f, height * 0.2f))
                link.set(if (side == LEFT) out.right else out.left, ay,
                    if (side == LEFT) target.left else target.right, target.centerY())
            }
        }
        return true
    }
}
