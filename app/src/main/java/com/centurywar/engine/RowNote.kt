package com.centurywar.engine

import kotlin.math.max
import kotlin.math.min

/**
 * Noc 4: krótki podpis pod etykietą wiersza ustawień (10 sp).
 *
 * Noc 8: podpis zawsze mieści się w prostokącie, który poda wiersz: szerokość do przycisku
 * i wysokość, która zostaje pod etykietą. Gdy wysokość pozwala na dwa wiersze, długi tekst
 * zawija się na dwa (rozmiar od 10 do 9 sp). W przeciwnym razie zostaje jeden wiersz, a tekst,
 * który nie mieści się nawet przy 9 sp, kończy się wielokropkiem „…”. Rozmiar nigdy nie spada
 * poniżej [MIN_DP] (jak [Ui.fit]: w pikselach co najmniej 9 dp przy każdej skali tekstu).
 *
 * Bufor jak `SettingsScreen.RowLabel`: przeliczany tylko po zmianie tekstu, rozmiaru miejsca
 * lub skali, więc rysowanie klatki niczego nie alokuje.
 */
class RowNote {
    var first = ""; private set
    var second = ""; private set
    var size = NOTE_DP; private set
    private var text = ""
    private var width = -1f
    private var height = -1f
    private var scale = 0f
    private var density = 0f

    val lines: Int get() = if (second.isEmpty()) 1 else 2

    /**
     * Układa [s] w prostokącie [availW] × [availH] (piksele). [availH] to wysokość dostępna
     * pod etykietą; przy zbyt małej mieści się jeden wiersz o rozmiarze [MIN_DP].
     */
    fun layout(ui: Ui, s: String, availW: Float, availH: Float): RowNote {
        if (text == s && width == availW && height == availH && scale == ui.textScale && density == ui.dp) return this
        text = s; width = availW; height = availH; scale = ui.textScale; density = ui.dp
        first = s; second = ""; size = NOTE_DP
        val floor = MIN_DP / ui.textScale
        val unit = ui.dp * ui.textScale * LINE
        // Największy rozmiar, przy którym jeden lub dwa wiersze mieszczą się w wysokości.
        val max1 = max(floor, min(NOTE_DP, availH / unit))
        val max2 = min(NOTE_DP, availH / (2f * unit))
        if (ui.measure(s, max1, true) <= availW) { size = max1; return this }
        if (max2 >= floor && s.indexOf(' ') > 0) {
            // Dwa wiersze: podział na spacji, przy którym dłuższy wiersz jest najkrótszy.
            var best = -1
            var bestW = Float.MAX_VALUE
            var i = s.indexOf(' ')
            while (i > 0) {
                val w = max(ui.measure(s.substring(0, i), max2, true),
                    ui.measure(s.substring(i + 1), max2, true))
                if (w < bestW) { bestW = w; best = i }
                i = s.indexOf(' ', i + 1)
            }
            val a = s.substring(0, best)
            val b = s.substring(best + 1)
            val sz = max(floor, min(ui.fit(a, max2, availW, MIN_DP), ui.fit(b, max2, availW, MIN_DP)))
            if (ui.measure(a, sz, true) <= availW && ui.measure(b, sz, true) <= availW) {
                first = a; second = b; size = sz; return this
            }
            // Nawet 9 sp nie wystarcza: pierwszy wiersz bierze tyle słów, ile się zmieści,
            // reszta idzie do drugiego z wielokropkiem.
            var cut = -1
            i = s.indexOf(' ')
            while (i > 0 && ui.measure(s.substring(0, i), floor, true) <= availW) { cut = i; i = s.indexOf(' ', i + 1) }
            if (cut > 0) {
                size = floor
                first = s.substring(0, cut)
                second = ellipsize(ui, s.substring(cut + 1), floor, availW)
                return this
            }
        }
        size = max(floor, min(max1, ui.fit(s, max1, availW, MIN_DP)))
        first = ellipsize(ui, s, size, availW)
        return this
    }

    /** Wysokość jednego wiersza podpisu w pikselach. */
    fun lineHeight(ui: Ui): Float = size * ui.dp * ui.textScale * LINE

    /** Rysuje podpis od górnej krawędzi [top]; zwraca jego wysokość. */
    fun draw(c: android.graphics.Canvas, ui: Ui, x: Float, top: Float): Float {
        val lh = lineHeight(ui)
        ui.textO(c, first, x, top + lh * 0.8f, size, Ui.DIM)
        if (second.isNotEmpty()) ui.textO(c, second, x, top + lh * 1.8f, size, Ui.DIM)
        return lh * lines
    }

    companion object {
        const val NOTE_DP = 10f
        /** Noc 8: najmniejszy rozmiar podpisu (dawniej 8 dp z [Ui.fit]). */
        const val MIN_DP = 9f
        private const val LINE = 1.15f
        private const val ELLIPSIS = "…"

        /** [s] skrócony do szerokości [w] z „…” na końcu (bez spacji przed wielokropkiem). */
        internal fun ellipsize(ui: Ui, s: String, sizeDp: Float, w: Float): String {
            if (ui.measure(s, sizeDp, true) <= w) return s
            // Wyszukiwanie binarne najdłuższego przedrostka, który mieści się z wielokropkiem.
            var lo = 0
            var hi = s.length
            while (lo < hi) {
                val mid = (lo + hi + 1) / 2
                if (ui.measure(s.substring(0, mid).trimEnd() + ELLIPSIS, sizeDp, true) <= w) lo = mid else hi = mid - 1
            }
            return s.substring(0, lo).trimEnd() + ELLIPSIS
        }
    }
}
