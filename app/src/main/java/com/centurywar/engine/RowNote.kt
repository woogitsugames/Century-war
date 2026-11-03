package com.centurywar.engine

import kotlin.math.max
import kotlin.math.min

/**
 * Noc 4: krótki podpis pod etykietą wiersza ustawień (jeden lub dwa wiersze, 10 sp).
 * Bufor jak `SettingsScreen.RowLabel`: przeliczany tylko po zmianie tekstu, szerokości lub skali.
 */
class RowNote {
    var first = ""; private set
    var second = ""; private set
    var size = NOTE_DP; private set
    private var text = ""
    private var width = -1f
    private var scale = 0f
    private var maxLines = 0

    val lines: Int get() = if (second.isEmpty()) 1 else 2

    fun layout(ui: Ui, s: String, avail: Float, maxLines: Int): RowNote {
        if (text == s && width == avail && scale == ui.textScale && this.maxLines == maxLines) return this
        text = s; width = avail; scale = ui.textScale; this.maxLines = maxLines
        first = s; second = ""; size = NOTE_DP
        if (ui.measure(s, NOTE_DP, true) <= avail) return this
        if (maxLines < 2) { size = ui.fit(s, NOTE_DP, avail); return this }
        var best = -1
        var bestW = Float.MAX_VALUE
        var i = s.indexOf(' ')
        while (i > 0) {
            val w = max(ui.measure(s.substring(0, i), NOTE_DP, true),
                ui.measure(s.substring(i + 1), NOTE_DP, true))
            if (w < bestW) { bestW = w; best = i }
            i = s.indexOf(' ', i + 1)
        }
        if (best < 0) { size = ui.fit(s, NOTE_DP, avail); return this }
        first = s.substring(0, best)
        second = s.substring(best + 1)
        size = min(ui.fit(first, NOTE_DP, avail), ui.fit(second, NOTE_DP, avail))
        return this
    }

    /** Wysokość jednego wiersza podpisu w pikselach. */
    fun lineHeight(ui: Ui): Float = size * ui.dp * ui.textScale * 1.15f

    /** Rysuje podpis od górnej krawędzi [top]; zwraca jego wysokość. */
    fun draw(c: android.graphics.Canvas, ui: Ui, x: Float, top: Float): Float {
        val lh = lineHeight(ui)
        ui.textO(c, first, x, top + lh * 0.8f, size, Ui.DIM)
        if (second.isNotEmpty()) ui.textO(c, second, x, top + lh * 1.8f, size, Ui.DIM)
        return lh * lines
    }

    companion object {
        const val NOTE_DP = 10f
    }
}
