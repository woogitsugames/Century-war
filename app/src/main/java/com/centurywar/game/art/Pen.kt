package com.centurywar.game.art

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * Wspólny „pisak” rysowania: każdy kształt dostaje wypełnienie i gruby ciemny kontur
 * (styl płaskiego cartoonu). Wszystkie pędzle, ścieżki i prostokąty są tworzone raz,
 * więc rysowanie klatki niczego nie alokuje.
 *
 * Kolory przechodzą przez [c]: uwzględnia przezroczystość (znikanie po śmierci)
 * oraz błysk trafienia (kolory wybielone), dzięki czemu rysujące funkcje nie muszą o tym myśleć.
 */
class Pen {
    companion object {
        const val OUTLINE = 0xFF1A1820.toInt()

        fun dark(c: Int, t: Float): Int = mix(c, 0xFF000000.toInt(), t)
        fun light(c: Int, t: Float): Int = mix(c, 0xFFFFFFFF.toInt(), t)

        fun mix(a: Int, b: Int, t: Float): Int {
            val it = 1f - t
            return Color.argb(
                (Color.alpha(a) * it + Color.alpha(b) * t).toInt(),
                (Color.red(a) * it + Color.red(b) * t).toInt(),
                (Color.green(a) * it + Color.green(b) * t).toInt(),
                (Color.blue(a) * it + Color.blue(b) * t).toInt(),
            )
        }
    }

    lateinit var cv: Canvas
    fun cvReady() = this::cv.isInitialized
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    val path = Path()
    val path2 = Path()
    val r = RectF()

    /** Przezroczystość całej postaci (0..1). */
    var alpha = 1f
    /** Postać właśnie trafiona: wszystko wybielone. */
    var flash = false
    /** Dodatkowy odcień płomieni (bez wybielania). */
    var tint = 0xFFFF8C40.toInt()
    var tintAmt = 0f
    /** Stopień zwęglenia maszyny, 0..1. */
    var char = 0f
    /** Grubość konturu w jednostkach lokalnych (canvas jest już przeskalowany). */
    var ow = 1.5f
    /** Poziom szczegółów: 1 = tani telefon, 3 = pełny. */
    var detail = 3
    val hi get() = detail >= 2
    val max get() = detail >= 3

    fun setAntiAlias(aa: Boolean) {
        fill.isAntiAlias = aa; stroke.isAntiAlias = aa; line.isAntiAlias = aa
    }

    fun c(color: Int): Int {
        var col = color
        if (char > 0f) col = mix(col, 0xFF32343A.toInt(), char)
        if (flash) col = mix(col, 0xFFFFFFFF.toInt(), 0.78f)
        else if (tintAmt > 0f) col = mix(col, tint, tintAmt)
        if (alpha >= 1f) return col
        return Color.argb((Color.alpha(col) * alpha).toInt(), Color.red(col), Color.green(col), Color.blue(col))
    }

    /** Kolor bez wybielenia przy trafieniu (np. świecące elementy), ale z przezroczystością. */
    fun raw(color: Int): Int {
        val col = if (char > 0f && color != OUTLINE) mix(color, 0xFF32343A.toInt(), char) else color
        return if (alpha >= 1f) col else Color.argb((Color.alpha(col) * alpha).toInt(), Color.red(col), Color.green(col), Color.blue(col))
    }

    fun fp(color: Int): Paint { fill.color = c(color); return fill }
    fun gp(color: Int): Paint { fill.color = raw(color); return fill }
    fun op(): Paint { stroke.color = raw(OUTLINE); stroke.strokeWidth = ow; return stroke }
    fun op(w: Float): Paint { stroke.color = raw(OUTLINE); stroke.strokeWidth = w; return stroke }
    fun lp(color: Int, w: Float): Paint { line.color = c(color); line.strokeWidth = w; return line }
    fun glp(color: Int, w: Float): Paint { line.color = raw(color); line.strokeWidth = w; return line }

    // ---------------------------------------------------------------- figury z konturem

    fun rect(l: Float, t: Float, rr: Float, b: Float, color: Int) {
        cv.drawRect(l, t, rr, b, fp(color)); cv.drawRect(l, t, rr, b, op())
    }

    fun rectF(l: Float, t: Float, rr: Float, b: Float, color: Int) = cv.drawRect(l, t, rr, b, fp(color))

    fun rr(l: Float, t: Float, rr: Float, b: Float, rad: Float, color: Int) {
        r.set(l, t, rr, b); cv.drawRoundRect(r, rad, rad, fp(color)); cv.drawRoundRect(r, rad, rad, op())
    }

    fun rrF(l: Float, t: Float, rr: Float, b: Float, rad: Float, color: Int) {
        r.set(l, t, rr, b); cv.drawRoundRect(r, rad, rad, fp(color))
    }

    fun circle(x: Float, y: Float, rad: Float, color: Int) {
        cv.drawCircle(x, y, rad, fp(color)); cv.drawCircle(x, y, rad, op())
    }

    fun circleF(x: Float, y: Float, rad: Float, color: Int) = cv.drawCircle(x, y, rad, fp(color))

    fun glow(x: Float, y: Float, rad: Float, color: Int) = cv.drawCircle(x, y, rad, gp(color))

    fun oval(l: Float, t: Float, rr: Float, b: Float, color: Int) {
        r.set(l, t, rr, b); cv.drawOval(r, fp(color)); cv.drawOval(r, op())
    }

    fun ovalF(l: Float, t: Float, rr: Float, b: Float, color: Int) {
        r.set(l, t, rr, b); cv.drawOval(r, fp(color))
    }

    /** Wypełnia i obrysowuje bieżącą [path]. */
    fun shape(color: Int) { cv.drawPath(path, fp(color)); cv.drawPath(path, op()) }
    fun shapeF(color: Int) = cv.drawPath(path, fp(color))

    /** Odcinek z konturem: najpierw szersza ciemna linia, potem kolor. */
    fun seg(x1: Float, y1: Float, x2: Float, y2: Float, w: Float, color: Int) {
        cv.drawLine(x1, y1, x2, y2, op(w + ow * 2f))
        cv.drawLine(x1, y1, x2, y2, lp(color, w))
    }

    fun segF(x1: Float, y1: Float, x2: Float, y2: Float, w: Float, color: Int) = cv.drawLine(x1, y1, x2, y2, lp(color, w))

    /** Łamana (bieżąca [path]) z konturem: kończyny, rury, kable. */
    fun limb(w: Float, color: Int) {
        cv.drawPath(path, op(w + ow * 2f))
        cv.drawPath(path, lp(color, w))
    }

    fun limbF(w: Float, color: Int) = cv.drawPath(path, lp(color, w))

    fun quad(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, x4: Float, y4: Float) {
        path.reset(); path.moveTo(x1, y1); path.lineTo(x2, y2); path.lineTo(x3, y3); path.lineTo(x4, y4); path.close()
    }

    fun tri(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        path.reset(); path.moveTo(x1, y1); path.lineTo(x2, y2); path.lineTo(x3, y3); path.close()
    }

    fun poly2(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        path.reset(); path.moveTo(x1, y1); path.lineTo(x2, y2); path.lineTo(x3, y3)
    }

    /** Nity: rząd kropek (tylko na wysokich detalach). */
    fun rivets(x0: Float, y: Float, x1: Float, n: Int, rad: Float, color: Int) {
        if (n < 2) return
        fill.color = c(color)
        val step = (x1 - x0) / (n - 1)
        for (i in 0 until n) cv.drawCircle(x0 + i * step, y, rad, fill)
    }
}
