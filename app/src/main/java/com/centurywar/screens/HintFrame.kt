package com.centurywar.screens

import android.graphics.Canvas
import android.graphics.RectF
import com.centurywar.engine.Ui

/** Wspólna oprawa dymków i samouczka; bez alokacji podczas rysowania. */
internal object HintFrame {
    fun chrome(c: Canvas, ui: Ui, rect: RectF, title: String, dp: Float) {
        ui.panel(c, rect)
        ui.ribbon(c, rect.centerX(), rect.top + 4 * dp, title, 18f, Ui.RIBBON,
            rect.width() * 0.45f, Ui.GOLD, rect.width() - 20 * dp)
    }

    /** Łamie tekst tylko przy zmianie treści/szerokości; wynik jest cache'owany przez ekran. */
    fun wrapInto(ui: Ui, s: String, width: Float, size: Float, out: Array<String?>): Int {
        var n = 0
        var start = 0
        var lastFit = -1
        var i = 0
        while (i <= s.length && n < out.size) {
            if (i == s.length || s[i] == ' ') {
                val wpx = ui.measure(s.substring(start, i), size)
                if (wpx > width && lastFit > start) {
                    out[n++] = s.substring(start, lastFit)
                    start = lastFit + 1
                    lastFit = -1
                    continue
                }
                if (i == s.length) { out[n++] = s.substring(start); break }
                lastFit = i
            }
            i++
        }
        return n
    }
}
