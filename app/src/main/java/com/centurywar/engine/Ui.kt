package com.centurywar.engine

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

class Button(val id: Int) {
    val r = RectF()
    var label = ""
    var sub = ""
    var enabled = true
    var visible = true
    var pressed = false
    var highlight = false
    var selected = false
    /** 0..1 pasek postępu na przycisku (np. odnowienie zdolności), < 0 = brak. */
    var progress = -1f
    var color = Ui.BTN
    var tag: Any? = null
    /** Własne rysowanie zawartości (ikona jednostki itp.). */
    var icon: ((Canvas, RectF) -> Unit)? = null

    fun set(l: Float, t: Float, w: Float, h: Float): Button { r.set(l, t, l + w, t + h); return this }
    fun contains(x: Float, y: Float) = visible && r.contains(x, y)
}

/**
 * Wspólne pędzle i rysowanie interfejsu. Wszystkie wymiary liczymy w "dp"
 * (gęstość ekranu × skala renderowania), więc przyciski mają ten sam fizyczny
 * rozmiar na tanim telefonie, flagowcu i rozłożonym Foldzie.
 */
class Ui {
    companion object {
        val BTN = Color.rgb(58, 74, 98)
        val BTN_HI = Color.rgb(214, 160, 54)
        val PANEL = Color.argb(225, 20, 28, 42)
        val GOLD = Color.rgb(255, 205, 64)
        val XP = Color.rgb(110, 200, 255)
        val TEXT = Color.rgb(240, 240, 235)
        val DIM = Color.rgb(150, 160, 175)
        val GOOD = Color.rgb(110, 220, 120)
        val BAD = Color.rgb(235, 85, 75)
        val PLAYER = Color.rgb(64, 140, 235)
        val ENEMY = Color.rgb(220, 70, 60)

        fun blend(a: Int, b: Int, t: Float): Int {
            val it = 1f - t
            return Color.argb(
                (Color.alpha(a) * it + Color.alpha(b) * t).toInt(),
                (Color.red(a) * it + Color.red(b) * t).toInt(),
                (Color.green(a) * it + Color.green(b) * t).toInt(),
                (Color.blue(a) * it + Color.blue(b) * t).toInt(),
            )
        }
    }

    var dp = 1f
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEXT }
    private val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    private val tmp = RectF()

    fun text(c: Canvas, s: String, x: Float, y: Float, sizeDp: Float, color: Int = TEXT,
             align: Paint.Align = Paint.Align.LEFT, isBold: Boolean = false, shadow: Boolean = false) {
        text.textSize = sizeDp * dp
        text.textAlign = align
        text.typeface = if (isBold) bold else Typeface.DEFAULT
        if (shadow) {
            text.color = Color.argb(160, 0, 0, 0)
            c.drawText(s, x + 1.5f * dp, y + 1.5f * dp, text)
        }
        text.color = color
        c.drawText(s, x, y, text)
    }

    fun measure(s: String, sizeDp: Float, isBold: Boolean = false): Float {
        text.textSize = sizeDp * dp
        text.typeface = if (isBold) bold else Typeface.DEFAULT
        return text.measureText(s)
    }

    /** Tekst zawijany do szerokości; zwraca wysokość. */
    fun wrap(c: Canvas?, s: String, x: Float, y: Float, width: Float, sizeDp: Float, color: Int = TEXT,
             align: Paint.Align = Paint.Align.LEFT): Float {
        val words = s.split(' ')
        var line = StringBuilder()
        var yy = y
        val lh = sizeDp * dp * 1.3f
        for (w in words) {
            val trial = if (line.isEmpty()) w else "$line $w"
            if (measure(trial, sizeDp) > width && line.isNotEmpty()) {
                c?.let { text(it, line.toString(), x, yy, sizeDp, color, align) }
                yy += lh
                line = StringBuilder(w)
            } else line = StringBuilder(trial)
        }
        if (line.isNotEmpty()) { c?.let { text(it, line.toString(), x, yy, sizeDp, color, align) }; yy += lh }
        return yy - y
    }

    fun panel(c: Canvas, r: RectF, color: Int = PANEL, radiusDp: Float = 12f) {
        fill.color = color
        c.drawRoundRect(r, radiusDp * dp, radiusDp * dp, fill)
        stroke.color = Color.argb(90, 255, 255, 255)
        stroke.strokeWidth = 1.2f * dp
        c.drawRoundRect(r, radiusDp * dp, radiusDp * dp, stroke)
    }

    fun bar(c: Canvas, l: Float, t: Float, w: Float, h: Float, frac: Float, color: Int, back: Int = Color.argb(160, 0, 0, 0)) {
        fill.color = back
        tmp.set(l, t, l + w, t + h)
        c.drawRoundRect(tmp, h / 2, h / 2, fill)
        if (frac > 0f) {
            fill.color = color
            tmp.set(l, t, l + w * frac.coerceIn(0f, 1f), t + h)
            c.drawRoundRect(tmp, h / 2, h / 2, fill)
        }
    }

    fun button(c: Canvas, b: Button, labelDp: Float = 15f) {
        if (!b.visible) return
        val r = b.r
        val rad = 10f * dp
        var col = if (b.highlight) BTN_HI else b.color
        if (b.selected) col = blend(col, Color.WHITE, 0.25f)
        if (!b.enabled) col = blend(col, Color.BLACK, 0.45f)
        if (b.pressed && b.enabled) col = blend(col, Color.WHITE, 0.2f)
        // cień + korpus + górny połysk
        fill.color = Color.argb(110, 0, 0, 0)
        tmp.set(r.left, r.top + 3 * dp, r.right, r.bottom + 3 * dp)
        c.drawRoundRect(tmp, rad, rad, fill)
        fill.color = col
        c.drawRoundRect(r, rad, rad, fill)
        fill.color = Color.argb(40, 255, 255, 255)
        tmp.set(r.left + 2 * dp, r.top + 2 * dp, r.right - 2 * dp, r.top + r.height() * 0.45f)
        c.drawRoundRect(tmp, rad * 0.8f, rad * 0.8f, fill)
        stroke.color = if (b.selected) Color.WHITE else Color.argb(120, 0, 0, 0)
        stroke.strokeWidth = (if (b.selected) 2.2f else 1.4f) * dp
        c.drawRoundRect(r, rad, rad, stroke)

        if (b.progress in 0f..0.999f) {
            fill.color = Color.argb(150, 0, 0, 0)
            tmp.set(r.left, r.top, r.right, r.top + r.height() * (1f - b.progress))
            c.save(); c.clipRect(tmp); c.drawRoundRect(r, rad, rad, fill); c.restore()
        }
        val ic = b.icon
        if (ic != null) ic(c, r)
        val tc = if (b.enabled) TEXT else DIM
        if (b.sub.isEmpty()) {
            text(c, b.label, r.centerX(), r.centerY() + labelDp * dp * 0.36f, labelDp, tc, Paint.Align.CENTER, true, true)
        } else if (ic == null) {
            text(c, b.label, r.centerX(), r.centerY() - 2 * dp, labelDp, tc, Paint.Align.CENTER, true, true)
            text(c, b.sub, r.centerX(), r.centerY() + labelDp * dp * 0.95f, labelDp * 0.78f, if (b.enabled) GOLD else DIM, Paint.Align.CENTER, false, true)
        } else {
            text(c, b.sub, r.centerX(), r.bottom - 5 * dp, labelDp * 0.8f, if (b.enabled) GOLD else DIM, Paint.Align.CENTER, true, true)
        }
    }

}
