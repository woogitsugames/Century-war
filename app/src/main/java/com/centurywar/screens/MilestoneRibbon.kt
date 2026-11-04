package com.centurywar.screens

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import com.centurywar.engine.Ui

/**
 * R11-03: złota wstęga kamienia milowego Endless (próg tygodnia, rekord fali) pod wierszem
 * „Następna fala”. Najwyżej jedna naraz, dwie czekają w kolejce; teksty powstają raz przy
 * zdarzeniu. Gdy wstęgę zasłoniłaby inna nakładka (panel wież, wybór karty, baner awansu,
 * dymek, pauza), czas stoi i nic nie jest rysowane. Stan interfejsu, poza zrzutem i powtórką.
 */
internal class MilestoneRibbon {
    companion object {
        const val SHOW_TIME = 3f
        const val IN_TIME = 0.25f
        const val OUT_TIME = 0.5f
        const val QUEUE = 2
        private val FILL = Color.rgb(184, 128, 26)
    }

    private var text: String? = null
    private var elapsed = 0f
    private val queue = arrayOfNulls<String>(QUEUE)
    private var queued = 0
    /** Ostatnio narysowana wstęga (współrzędne ekranu); pusta, gdy niewidoczna. Do testów. */
    val drawn = RectF()

    val active: Boolean get() = text != null
    val current: String? get() = text
    val pending: Int get() = queued

    fun push(s: String) {
        if (text == null) { text = s; elapsed = 0f; return }
        if (queued < QUEUE) queue[queued++] = s
        else { queue[QUEUE - 1] = s } // najnowsze zdarzenie zastępuje ostatnie w kolejce
    }

    /** [hold] = inna nakładka zajmuje ekran: wstęga czeka (czas stoi). */
    fun update(dt: Float, hold: Boolean) {
        if (text == null || hold) return
        elapsed += dt
        if (elapsed < SHOW_TIME) return
        if (queued > 0) {
            text = queue[0]
            for (i in 1 until queued) queue[i - 1] = queue[i]
            queue[--queued] = null
            elapsed = 0f
        } else text = null
    }

    /** Koniec bitwy: wstęga nie może przypominać powiadomienia na wynikach. */
    fun clear() {
        text = null
        queued = 0
        queue.fill(null)
        drawn.setEmpty()
    }

    /** [cy] = środek wstęgi w pełni wsuniętej; [width] = najszersza dopuszczalna (z ogonami). */
    fun draw(c: Canvas, ui: Ui, cx: Float, cy: Float, width: Float, dp: Float) {
        val s = text
        if (s == null) { drawn.setEmpty(); return }
        val inK = (elapsed / IN_TIME).coerceIn(0f, 1f)
        val outK = ((SHOW_TIME - elapsed) / OUT_TIME).coerceIn(0f, 1f)
        val alpha = (255 * kotlin.math.min(inK, outK)).toInt()
        if (alpha <= 0) { drawn.setEmpty(); return }
        val ease = 1f - (1f - inK) * (1f - inK)
        val y = cy - (1f - ease) * 14 * dp
        val size = 14f
        val hh = size * dp * ui.textScale * 1.05f
        val maxW = width - hh * 2.6f
        val tw = kotlin.math.min(ui.measure(s, size, true), maxW - hh * 2.4f)
        val half = tw / 2 + hh * 1.1f + hh * 1.3f
        drawn.set(cx - half, y - hh * 0.75f - 1.5f * dp, cx + half, y + hh * 0.95f + 1.5f * dp)
        val save = if (alpha < 255) c.saveLayerAlpha(drawn.left - 4 * dp, drawn.top - 4 * dp,
            drawn.right + 4 * dp, drawn.bottom + 4 * dp, alpha) else -1
        ui.ribbon(c, cx, y, s, size, FILL, 0f, Color.WHITE, maxW)
        if (save >= 0) c.restoreToCount(save)
    }
}
