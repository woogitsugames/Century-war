package com.centurywar.engine

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.centurywar.game.Defs
import kotlin.math.max
import kotlin.math.min

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
    /** Ikona wektorowa z [Icons] (-1 = brak). */
    var iconId = -1
    /** Kolor "drużynowy" ikony (0 = domyślny). */
    var iconTint = 0
    /** Mała ikona przed podpisem [sub] (np. moneta przy koszcie, gwiazdka). */
    var subIcon = -1
    /** Kolor podpisu [sub] (0 = złoty). */
    var subColor = 0
    /** Tekst opisu przytrzymania (może zostać ustawiony dopiero przy otwieraniu dymka). */
    var tooltipTitle = ""
    var tooltipBody = ""

    fun set(l: Float, t: Float, w: Float, h: Float): Button { r.set(l, t, l + w, t + h); return this }
    fun contains(x: Float, y: Float) = visible && r.contains(x, y)
}

/** Bufor dwóch wierszy opisu, przeliczany tylko po zmianie treści lub szerokości. */
class TooltipBubble {
    var title = ""
        private set
    var body = ""
        private set
    val lines = arrayOf("", "")
    var lineCount = 0
        private set
    private var lastWidth = -1f
    private var lastScale = -1f

    fun set(newTitle: String, newBody: String) {
        if (title == newTitle && body == newBody) return
        title = newTitle; body = newBody; lastWidth = -1f
    }

    fun prepare(ui: Ui, width: Float) {
        if (width == lastWidth && lastScale == ui.textScale) return
        lastWidth = width; lastScale = ui.textScale
        lines[0] = ""; lines[1] = ""; lineCount = 0
        if (body.isEmpty()) return
        // Dzielenie napisów odbywa się przy zmianie opisu/wymiarów, nie w każdej klatce.
        val words = body.split(' ')
        var first = ""
        var second = ""
        var onSecond = false
        for (word in words) {
            if (word.isEmpty()) continue
            val next = if (first.isEmpty()) word else "$first $word"
            if (!onSecond && ui.measure(next, 12.5f) <= width) {
                first = next
            } else {
                onSecond = true
                second = if (second.isEmpty()) word else "$second $word"
            }
        }
        lines[0] = first
        lines[1] = second
        lineCount = if (second.isNotEmpty()) 2 else 1
    }
}

/**
 * Wspólne pędzle i rysowanie interfejsu. Wszystkie wymiary liczymy w "dp"
 * (gęstość ekranu × skala renderowania), więc przyciski mają ten sam fizyczny
 * rozmiar na tanim telefonie, flagowcu i rozłożonym Foldzie.
 *
 * Styl: płaski cartoon z grubym ciemnym konturem. Przyciski mają fazowanie
 * (jasna górna część, ciemna dolna krawędź), panele metalową ramę ze złotą
 * lamówką, nagłówki to wstęgi. Żadnych alokacji podczas rysowania.
 */
class Ui {
    companion object {
        val BTN = Color.rgb(58, 74, 98)
        val BTN_HI = Color.rgb(226, 162, 44)
        val BTN_GREEN: Int get() = Palette.active.buttonGood
        val BTN_RED: Int get() = Palette.active.buttonBad
        val PANEL: Int get() = Palette.active.panel
        val GOLD = Color.rgb(255, 205, 64)
        val XP = Color.rgb(110, 200, 255)
        val TEXT = Color.rgb(244, 242, 236)
        val DIM = Color.rgb(160, 168, 184)
        val GOOD: Int get() = Palette.active.good
        val BAD: Int get() = Palette.active.bad
        val PLAYER: Int get() = Palette.active.player
        val ENEMY: Int get() = Palette.active.enemy
        val OUTLINE: Int get() = Palette.active.outline
        val FRAME: Int get() = Palette.active.frame
        val FRAME_L = Color.rgb(120, 132, 158)
        val TRIM = Color.rgb(222, 170, 70)
        val RIBBON = Color.rgb(196, 54, 46)

        /** Barwy epok pochodzą z ich definicji, nie z indeksów UI. */
        val ERA: IntArray = IntArray(Defs.ERA_COUNT) { Defs.spec(it).accent }

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
    /** 100%, 115% lub 130% bez dodatkowych alokacji. */
    var textScale = 1f
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEXT }
    /** Pędzle ramek UI: zawsze z antyaliasingiem (kontury bez schodków nawet na niskiej jakości). */
    private val pf = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ps = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val textLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeMiter = 2f
    }
    private val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    val heavy: Typeface = Typeface.create("sans-serif-black", Typeface.BOLD) ?: bold
    private val tmp = RectF()
    private val tmp2 = RectF()
    private val path = Path()
    private val tooltipRect = RectF()

    /** Wektorowy dymek przy przycisku, zawsze wewnątrz bezpiecznego obszaru ekranu. */
    fun tooltip(c: Canvas, anchor: RectF, content: TooltipBubble,
                safeL: Float, safeT: Float, safeR: Float, safeB: Float) {
        val margin = 8f * dp
        val width = min(355f * dp, safeR - safeL - 2f * margin)
        if (width <= 55f * dp) return
        val pad = 14f * dp
        content.prepare(this, width - 2f * pad)
        val lineH = 17f * dp * textScale
        val height = (38f * dp + content.lineCount * lineH).coerceAtMost(safeB - safeT - margin * 2)
        if (height <= 0f) return
        val left = (anchor.centerX() - width / 2f).coerceIn(safeL + margin,
            (safeR - margin - width).coerceAtLeast(safeL + margin))
        val above = anchor.top - height - 9f * dp
        val below = anchor.bottom + 9f * dp
        val top = (if (above >= safeT + margin) above else below)
            .coerceIn(safeT + margin, (safeB - margin - height).coerceAtLeast(safeT + margin))
        tooltipRect.set(left, top, left + width, top + height)
        panel(c, tooltipRect)
        textO(c, content.title, left + width / 2f, top + 23f * dp,
            fit(content.title, 16f, width - 2f * pad), GOLD, Paint.Align.CENTER, 1.8f)
        for (i in 0 until content.lineCount) {
            val line = content.lines[i]
            text(c, line, left + width / 2f, top + 39f * dp + i * lineH,
                fit(line, 12.5f, width - 2f * pad, 10f), TEXT, Paint.Align.CENTER)
        }
    }

    // ------------------------------------------------------------ tekst

    fun text(c: Canvas, s: String, x: Float, y: Float, sizeDp: Float, color: Int = TEXT,
             align: Paint.Align = Paint.Align.LEFT, isBold: Boolean = false, shadow: Boolean = false) {
        text.textSize = sizeDp * dp * textScale
        text.textAlign = align
        text.typeface = if (isBold) bold else Typeface.DEFAULT
        text.shader = null
        if (shadow) {
            text.color = Color.argb(160, 0, 0, 0)
            c.drawText(s, x + 1.5f * dp, y + 1.5f * dp, text)
        }
        text.color = color
        c.drawText(s, x, y, text)
    }

    /** Pogrubiony tekst z ciemnym konturem (styl cartoon) i lekkim cieniem. */
    fun textO(c: Canvas, s: String, x: Float, y: Float, sizeDp: Float, color: Int = TEXT,
              align: Paint.Align = Paint.Align.LEFT, outlineDp: Float = 0f, face: Typeface? = null) {
        val sz = sizeDp * dp * textScale
        val ow = if (outlineDp > 0f) outlineDp * dp else max(2f * dp, sz * 0.16f)
        val tf = face ?: bold
        textLine.textSize = sz; textLine.textAlign = align; textLine.typeface = tf
        textLine.strokeWidth = ow
        textLine.color = Color.argb(120, 0, 0, 0)
        c.drawText(s, x, y + ow * 0.6f, textLine)
        textLine.color = OUTLINE
        c.drawText(s, x, y, textLine)
        text.textSize = sz; text.textAlign = align; text.typeface = tf; text.shader = null
        text.color = color
        c.drawText(s, x, y, text)
    }

    fun measure(s: String, sizeDp: Float, isBold: Boolean = false): Float {
        text.textSize = sizeDp * dp * textScale
        text.typeface = if (isBold) bold else Typeface.DEFAULT
        return text.measureText(s)
    }

    /** Największy rozmiar ≤ [sizeDp], przy którym pogrubiony tekst mieści się w [width]. */
    fun fit(s: String, sizeDp: Float, width: Float, minDp: Float = 8f): Float {
        if (width <= 0f) return sizeDp
        val m = measure(s, sizeDp, true)
        return if (m <= width) sizeDp else max(minDp / textScale, sizeDp * width / m)
    }

    /** Tekst zawijany do szerokości; zwraca wysokość. */
    fun wrap(c: Canvas?, s: String, x: Float, y: Float, width: Float, sizeDp: Float, color: Int = TEXT,
             align: Paint.Align = Paint.Align.LEFT): Float {
        val words = s.split(' ')
        var line = StringBuilder()
        var yy = y
        val lh = sizeDp * dp * textScale * 1.3f
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

    // ------------------------------------------------------------ panele

    /** Panel z ramą: cień, ciemny kontur, metalowa rama, złota lamówka, ciemne wnętrze. */
    fun panel(c: Canvas, r: RectF, color: Int = PANEL, radiusDp: Float = 14f) {
        val rad = radiusDp * dp
        val o = (if (Palette.highContrast) 3.4f else 2.5f) * dp
        // cień
        pf.color = Color.argb(110, 0, 0, 0)
        tmp.set(r.left, r.top + 5 * dp, r.right, r.bottom + 6 * dp)
        c.drawRoundRect(tmp, rad, rad, pf)
        // kontur + rama
        pf.color = OUTLINE
        c.drawRoundRect(r, rad, rad, pf)
        tmp.set(r.left + o, r.top + o, r.right - o, r.bottom - o)
        pf.color = FRAME
        c.drawRoundRect(tmp, rad - o, rad - o, pf)
        // górna jaśniejsza krawędź ramy
        ps.color = FRAME_L; ps.strokeWidth = (if (Palette.highContrast) 2.7f else 1.6f) * dp
        tmp.set(r.left + o * 1.6f, r.top + o * 1.6f, r.right - o * 1.6f, r.bottom - o * 1.6f)
        c.save(); c.clipRect(r.left, r.top, r.right, r.top + rad * 1.5f)
        c.drawRoundRect(tmp, rad - o, rad - o, ps); c.restore()
        // wnętrze
        val f = 6.5f * dp
        tmp.set(r.left + f, r.top + f, r.right - f, r.bottom - f)
        pf.color = OUTLINE
        c.drawRoundRect(tmp, rad - f * 0.6f, rad - f * 0.6f, pf)
        tmp.inset(1.4f * dp, 1.4f * dp)
        pf.color = color
        c.drawRoundRect(tmp, rad - f * 0.7f, rad - f * 0.7f, pf)
        // złota lamówka
        ps.color = Color.argb(150, Color.red(TRIM), Color.green(TRIM), Color.blue(TRIM)); ps.strokeWidth = 1.2f * dp
        tmp.inset(2.5f * dp, 2.5f * dp)
        c.drawRoundRect(tmp, rad - f, rad - f, ps)
        // nity w rogach
        val n = 3.2f * dp
        rivet(c, r.left + o + n * 0.9f, r.top + o + n * 0.9f, n * 0.55f)
        rivet(c, r.right - o - n * 0.9f, r.top + o + n * 0.9f, n * 0.55f)
        rivet(c, r.left + o + n * 0.9f, r.bottom - o - n * 0.9f, n * 0.55f)
        rivet(c, r.right - o - n * 0.9f, r.bottom - o - n * 0.9f, n * 0.55f)
    }

    private fun rivet(c: Canvas, x: Float, y: Float, rad: Float) {
        pf.color = TRIM; c.drawCircle(x, y, rad, pf)
        pf.color = Color.argb(160, 255, 255, 255); c.drawCircle(x - rad * 0.3f, y - rad * 0.3f, rad * 0.35f, pf)
    }

    /** Prosta karta (bez ramy metalowej): kontur, wnętrze, jaśniejsza górna krawędź. */
    fun card(c: Canvas, r: RectF, color: Int, radiusDp: Float = 12f, border: Int = 0) {
        val rad = radiusDp * dp
        pf.color = Color.argb(100, 0, 0, 0)
        tmp.set(r.left, r.top + 3 * dp, r.right, r.bottom + 4 * dp)
        c.drawRoundRect(tmp, rad, rad, pf)
        pf.color = OUTLINE
        c.drawRoundRect(r, rad, rad, pf)
        val o = 2.2f * dp
        tmp.set(r.left + o, r.top + o, r.right - o, r.bottom - o)
        pf.color = blend(color, Color.BLACK, 0.3f)
        c.drawRoundRect(tmp, rad - o, rad - o, pf)
        tmp.bottom -= 3 * dp
        pf.color = color
        c.drawRoundRect(tmp, rad - o, rad - o, pf)
        ps.color = Color.argb(70, 255, 255, 255); ps.strokeWidth = 1.2f * dp
        tmp.inset(1.5f * dp, 1.5f * dp)
        c.save(); c.clipRect(r.left, r.top, r.right, r.top + rad * 1.2f)
        c.drawRoundRect(tmp, rad - o, rad - o, ps); c.restore()
        if (border != 0) {
            ps.color = border; ps.strokeWidth = 2.4f * dp
            tmp.set(r.left + o * 0.5f, r.top + o * 0.5f, r.right - o * 0.5f, r.bottom - o * 0.5f)
            c.drawRoundRect(tmp, rad, rad, ps)
        }
    }

    /**
     * Wstęga nagłówka z rozciętymi końcami. Szerokość dopasowana do tekstu
     * (co najmniej [minW]); zwraca prostokąt środkowej części w [out] (jeśli podano).
     */
    fun ribbon(c: Canvas, cx: Float, cy: Float, s: String, sizeDp: Float, color: Int = RIBBON, minW: Float = 0f,
               textColor: Int = GOLD, maxW: Float = 0f) {
        var sz = sizeDp
        val hh = sz * dp * textScale * 1.05f
        var tw = measure(s, sz, true)
        if (maxW > 0f && tw + hh * 2.4f > maxW) { sz = max(9f, sz * (maxW - hh * 2.4f) / tw); tw = measure(s, sz, true) }
        val half = max(minW / 2, tw / 2 + hh * 1.1f)
        val dark = blend(color, Color.BLACK, 0.4f)
        val tail = hh * 1.3f
        // ogony
        for (side in 0..1) {
            val sg = if (side == 0) -1f else 1f
            val x0 = cx + sg * (half - hh * 0.4f)
            val x1 = cx + sg * (half + tail)
            path.reset()
            path.moveTo(x0, cy - hh * 0.55f)
            path.lineTo(x1, cy - hh * 0.55f)
            path.lineTo(x1 - sg * hh * 0.55f, cy + hh * 0.2f)
            path.lineTo(x1, cy + hh * 0.95f)
            path.lineTo(x0, cy + hh * 0.95f)
            path.close()
            pf.color = blend(color, Color.BLACK, 0.2f); c.drawPath(path, pf)
            ps.color = OUTLINE; ps.strokeWidth = 2.2f * dp; c.drawPath(path, ps)
            // zakładka
            path.reset()
            path.moveTo(cx + sg * half, cy + hh * 0.7f)
            path.lineTo(cx + sg * (half - hh * 0.4f), cy + hh * 0.95f)
            path.lineTo(cx + sg * (half - hh * 0.4f), cy + hh * 0.7f)
            path.close()
            pf.color = dark; c.drawPath(path, pf)
            ps.strokeWidth = 1.6f * dp; c.drawPath(path, ps)
        }
        // pas główny
        tmp.set(cx - half, cy - hh * 0.75f, cx + half, cy + hh * 0.7f)
        pf.color = OUTLINE
        tmp2.set(tmp.left - 1.2f * dp, tmp.top - 1.2f * dp, tmp.right + 1.2f * dp, tmp.bottom + 1.2f * dp)
        c.drawRoundRect(tmp2, 3 * dp, 3 * dp, pf)
        pf.color = color
        c.drawRoundRect(tmp, 2 * dp, 2 * dp, pf)
        pf.color = blend(color, Color.WHITE, 0.22f)
        c.drawRect(tmp.left + 1.2f * dp, tmp.top + 1.2f * dp, tmp.right - 1.2f * dp, tmp.top + hh * 0.35f, pf)
        pf.color = dark
        c.drawRect(tmp.left, tmp.bottom - hh * 0.22f, tmp.right, tmp.bottom, pf)
        ps.color = Color.argb(120, 255, 230, 160); ps.strokeWidth = 1f * dp
        c.drawLine(tmp.left + 4 * dp, tmp.top + 3.5f * dp, tmp.right - 4 * dp, tmp.top + 3.5f * dp, ps)
        textO(c, s, cx, cy + sz * dp * textScale * 0.36f - hh * 0.03f, sz, textColor, Paint.Align.CENTER)
    }

    /** Pigułka z ikoną po lewej i wartością (złoto, medale, gwiazdki). Zwraca prawą krawędź. */
    fun badge(c: Canvas, l: Float, cy: Float, iconId: Int, s: String, sizeDp: Float, color: Int = TEXT,
              alignRight: Boolean = false, minTextW: Float = 0f): Float {
        val h = sizeDp * dp * 1.9f * textScale.coerceAtMost(1.15f)
        val tw = max(measure(s, sizeDp, true), minTextW)
        val w = h * 0.55f + tw + h * 0.55f + 6 * dp
        val left = if (alignRight) l - w else l
        tmp.set(left + h * 0.3f, cy - h * 0.42f, left + w, cy + h * 0.42f)
        pf.color = OUTLINE
        tmp2.set(tmp.left - 2 * dp, tmp.top - 2 * dp, tmp.right + 2 * dp, tmp.bottom + 2 * dp)
        c.drawRoundRect(tmp2, h, h, pf)
        pf.color = Color.argb(235, 20, 24, 36)
        c.drawRoundRect(tmp, h, h, pf)
        pf.color = Color.argb(40, 255, 255, 255)
        tmp2.set(tmp.left + 2 * dp, tmp.top + 1.5f * dp, tmp.right - 2 * dp, tmp.centerY())
        c.drawRoundRect(tmp2, h, h, pf)
        Icons.draw(c, iconId, left + h * 0.45f, cy, h * 0.95f, dp = dp)
        textO(c, s, left + h * 0.95f + 2 * dp, cy + sizeDp * dp * 0.36f, sizeDp, color)
        return left + w
    }

    fun badgeWidth(s: String, sizeDp: Float, minTextW: Float = 0f): Float {
        val h = sizeDp * dp * 1.9f * textScale.coerceAtMost(1.15f)
        return h * 1.1f + max(measure(s, sizeDp, true), minTextW) + 6 * dp
    }

    /** Okrągły medalion z ikoną (karty badań, doktryny, nagłówki). */
    fun medallion(c: Canvas, cx: Float, cy: Float, rad: Float, color: Int, iconId: Int, ring: Int = TRIM, iconTint: Int = 0) {
        pf.color = Color.argb(90, 0, 0, 0); c.drawCircle(cx, cy + 2.5f * dp, rad, pf)
        pf.color = OUTLINE; c.drawCircle(cx, cy, rad, pf)
        pf.color = ring; c.drawCircle(cx, cy, rad - 2 * dp, pf)
        pf.color = OUTLINE; c.drawCircle(cx, cy, rad - 4.5f * dp, pf)
        pf.color = color; c.drawCircle(cx, cy, rad - 5.5f * dp, pf)
        pf.color = blend(color, Color.WHITE, 0.18f)
        c.save(); c.clipRect(cx - rad, cy - rad, cx + rad, cy - rad * 0.1f)
        c.drawCircle(cx, cy, rad - 5.5f * dp, pf); c.restore()
        if (iconId >= 0) Icons.draw(c, iconId, cx, cy, (rad - 5 * dp) * 1.35f, iconTint, dp = dp)
    }

    /** Rząd gwiazdek (pełne/puste). */
    fun stars(c: Canvas, cx: Float, cy: Float, size: Float, n: Int, max: Int = 3, gap: Float = 0.92f) {
        val step = size * gap
        var x = cx - step * (max - 1) / 2f
        for (i in 0 until max) {
            Icons.draw(c, if (i < n) Icons.STAR else Icons.STAR_EMPTY, x, cy, size, dp = dp)
            x += step
        }
    }

    fun bar(c: Canvas, l: Float, t: Float, w: Float, h: Float, frac: Float, color: Int, back: Int = Color.argb(200, 12, 14, 22)) {
        val o = max(1.2f * dp, h * 0.18f)
        val rad = h / 2 + o
        tmp.set(l - o, t - o, l + w + o, t + h + o)
        pf.color = OUTLINE
        c.drawRoundRect(tmp, rad, rad, pf)
        pf.color = back
        tmp.set(l, t, l + w, t + h)
        c.drawRoundRect(tmp, h / 2, h / 2, pf)
        val f = frac.coerceIn(0f, 1f)
        if (f > 0f) {
            tmp.set(l, t, l + max(h, w * f), t + h)
            c.save(); c.clipRect(l, t - o, l + w * f, t + h + o)
            pf.color = blend(color, Color.BLACK, 0.22f)
            c.drawRoundRect(tmp, h / 2, h / 2, pf)
            tmp.bottom = t + h * 0.72f
            pf.color = color
            c.drawRoundRect(tmp, h / 2, h / 2, pf)
            if (h > 5 * dp) {
                pf.color = Color.argb(110, 255, 255, 255)
                tmp.set(l + h * 0.35f, t + h * 0.14f, l + max(h, w * f) - h * 0.35f, t + h * 0.36f)
                c.drawRoundRect(tmp, h, h, pf)
            }
            c.restore()
        }
    }

    // ------------------------------------------------------------ przyciski

    /** Rysuje korpus przycisku (kontur, fazowanie, połysk). Zwraca przesunięcie treści w dół. */
    private fun body(c: Canvas, r: RectF, col: Int, pressed: Boolean, selected: Boolean, rad: Float): Float {
        val o = 2.2f * dp
        val bevel = if (pressed) 1.5f * dp else 4.5f * dp
        val down = if (pressed) 3f * dp else 0f
        pf.color = Color.argb(100, 0, 0, 0)
        tmp.set(r.left, r.top + 3 * dp, r.right, r.bottom + 3.5f * dp)
        c.drawRoundRect(tmp, rad, rad, pf)
        pf.color = OUTLINE
        c.drawRoundRect(r, rad, rad, pf)
        tmp.set(r.left + o, r.top + o + down, r.right - o, r.bottom - o)
        pf.color = blend(col, Color.BLACK, 0.38f)
        c.drawRoundRect(tmp, rad - o, rad - o, pf)
        tmp.bottom -= bevel
        pf.color = col
        c.drawRoundRect(tmp, rad - o, rad - o, pf)
        // połysk górnej części
        tmp2.set(tmp.left + 1.5f * dp, tmp.top + 1.5f * dp, tmp.right - 1.5f * dp, tmp.top + tmp.height() * 0.46f)
        pf.color = blend(col, Color.WHITE, 0.16f)
        c.drawRoundRect(tmp2, rad * 0.7f, rad * 0.7f, pf)
        ps.color = Color.argb(110, 255, 255, 255); ps.strokeWidth = 1.3f * dp
        c.drawLine(tmp.left + rad * 0.7f, tmp.top + 2 * dp, tmp.right - rad * 0.7f, tmp.top + 2 * dp, ps)
        if (selected) {
            ps.color = GOLD; ps.strokeWidth = 2.6f * dp
            tmp.set(r.left + 1.3f * dp, r.top + 1.3f * dp, r.right - 1.3f * dp, r.bottom - 1.3f * dp)
            c.drawRoundRect(tmp, rad, rad, ps)
        }
        return down - bevel / 2 + 1.5f * dp
    }

    fun button(c: Canvas, b: Button, labelDp: Float = 15f) {
        if (!b.visible) return
        val r = b.r
        val rad = min(12f * dp, r.height() * 0.3f)
        var col = if (b.highlight) BTN_HI else b.color
        if (!b.enabled) col = blend(col, Color.rgb(30, 32, 40), 0.55f)
        val dy = body(c, r, col, b.pressed && b.enabled, b.selected, rad)
        val cy = r.centerY() + dy
        val tc = if (b.enabled) TEXT else DIM
        val subC = if (!b.enabled) DIM else if (b.subColor != 0) b.subColor else GOLD

        val ic = b.icon
        if (ic != null) {
            if (b.progress in 0f..0.999f) cooldown(c, r, b.progress, rad)
            ic(c, r)
            subLine(c, b, r.centerX(), r.bottom - 8 * dp + dy * 0.5f, labelDp * 0.86f, subC)
            if (!b.enabled) dim(c, r, rad)
            return
        }
        val id = b.iconId
        val wide = r.width() > r.height() * 1.7f
        if (id >= 0 && wide && b.label.isNotEmpty()) {
            // ikona w medalionie po lewej, tekst obok (wyrównany do lewej)
            val big = r.height() >= 42 * dp
            val ir = r.height() * (if (big) 0.36f else 0.3f)
            val icx = r.left + r.height() * (if (big) 0.52f else 0.42f)
            if (big) {
                pf.color = OUTLINE; c.drawCircle(icx, cy, ir, pf)
                pf.color = blend(col, Color.BLACK, 0.35f); c.drawCircle(icx, cy, ir - 2 * dp, pf)
            }
            Icons.draw(c, id, icx, cy, ir * (if (big) 1.45f else 1.7f), b.iconTint, dp = dp)
            val tx = icx + ir + 8 * dp
            val avail = r.right - 8 * dp - tx
            if (b.sub.isEmpty()) {
                val sz = fit(b.label, labelDp, avail)
                textO(c, b.label, tx, cy + sz * dp * textScale * 0.36f, sz, tc)
            } else {
                val sz = fit(b.label, labelDp, avail)
                textO(c, b.label, tx, cy - 1 * dp, sz, tc)
                subLine(c, b, tx, cy + labelDp * dp * 0.86f, labelDp * 0.74f, subC, left = true)
            }
        } else if (id >= 0) {
            // ikona wyśrodkowana; podpis pod spodem
            if (b.progress in 0f..0.999f) cooldown(c, r, b.progress, rad)
            val hasBottom = b.sub.isNotEmpty() || b.label.isNotEmpty() || b.subIcon >= 0
            val sz = min(r.width(), r.height())
            val iy = if (hasBottom) r.top + r.height() * 0.42f + dy else cy
            Icons.draw(c, id, r.centerX(), iy, sz * (if (hasBottom) 0.5f else 0.58f), b.iconTint, dp = dp)
            if (b.sub.isNotEmpty() || b.subIcon >= 0) {
                subLine(c, b, r.centerX(), r.bottom - 8 * dp + dy * 0.5f, labelDp * 0.86f, subC)
                if (b.label.isNotEmpty()) cornerTag(c, b.label, r.right - 4 * dp, r.top + 4 * dp)
            } else if (b.label.isNotEmpty()) {
                val s = fit(b.label, labelDp * 0.86f, r.width() - 8 * dp)
                textO(c, b.label, r.centerX(), r.bottom - 8 * dp + dy * 0.5f, s, tc, Paint.Align.CENTER)
            }
        } else {
            if (b.progress in 0f..0.999f) cooldown(c, r, b.progress, rad)
            val avail = r.width() - 12 * dp
            if (b.sub.isEmpty() && b.subIcon < 0) {
                val s = fit(b.label, labelDp, avail)
                textO(c, b.label, r.centerX(), cy + s * dp * 0.36f, s, tc, Paint.Align.CENTER)
            } else {
                val s = fit(b.label, labelDp, avail)
                textO(c, b.label, r.centerX(), cy - 1 * dp, s, tc, Paint.Align.CENTER)
                subLine(c, b, r.centerX(), cy + labelDp * dp * 0.88f, labelDp * 0.76f, subC)
            }
        }
        if (!b.enabled) dim(c, r, rad)
    }

    private fun cooldown(c: Canvas, r: RectF, progress: Float, rad: Float) {
        pf.color = Color.argb(150, 0, 0, 0)
        tmp.set(r.left, r.top, r.right, r.top + r.height() * (1f - progress))
        c.save(); c.clipRect(tmp)
        tmp2.set(r.left + 2 * dp, r.top + 2 * dp, r.right - 2 * dp, r.bottom - 2 * dp)
        c.drawRoundRect(tmp2, rad, rad, pf); c.restore()
        ps.color = Color.argb(200, 255, 220, 120); ps.strokeWidth = 1.5f * dp
        val y = r.top + r.height() * (1f - progress)
        c.drawLine(r.left + 3 * dp, y, r.right - 3 * dp, y, ps)
    }

    private fun dim(c: Canvas, r: RectF, rad: Float) {
        pf.color = Color.argb(90, 16, 18, 26)
        tmp.set(r.left + 2 * dp, r.top + 2 * dp, r.right - 2 * dp, r.bottom - 2 * dp)
        c.drawRoundRect(tmp, rad, rad, pf)
    }

    /** Podpis z opcjonalną ikoną (wyśrodkowany albo od lewej). */
    private fun subLine(c: Canvas, b: Button, x: Float, baseY: Float, sizeDp: Float, color: Int, left: Boolean = false) {
        val hasIcon = b.subIcon >= 0
        val isz = sizeDp * dp * 1.25f
        val tw = if (b.sub.isEmpty()) 0f else measure(b.sub, sizeDp, true)
        val gap = if (hasIcon && tw > 0f) 3 * dp else 0f
        val total = (if (hasIcon) isz else 0f) + gap + tw
        var xx = if (left) x else x - total / 2
        if (hasIcon) {
            Icons.draw(c, b.subIcon, xx + isz / 2, baseY - sizeDp * dp * 0.36f, isz, dp = dp)
            xx += isz + gap
        }
        if (tw > 0f) textO(c, b.sub, xx, baseY, sizeDp, color)
    }

    /** Mała plakietka w rogu przycisku (np. poziom Zbrojowni). */
    fun cornerTag(c: Canvas, s: String, right: Float, top: Float) {
        val sz = 11f
        val tw = measure(s, sz, true)
        val hh = sz * dp * 1.5f
        tmp.set(right - tw - hh * 0.8f, top, right, top + hh)
        pf.color = OUTLINE
        c.drawRoundRect(tmp, hh / 2, hh / 2, pf)
        tmp.inset(1.5f * dp, 1.5f * dp)
        pf.color = RIBBON
        c.drawRoundRect(tmp, hh / 2, hh / 2, pf)
        textO(c, s, tmp.centerX(), tmp.centerY() + sz * dp * textScale * 0.36f, sz, TEXT, Paint.Align.CENTER, 1.6f)
    }

    /** Okrągły przycisk zamykania (czerwony z białym krzyżykiem). */
    fun closeButton(c: Canvas, b: Button) {
        if (!b.visible) return
        val cx = b.r.centerX(); val cy = b.r.centerY()
        val rad = min(b.r.width(), b.r.height()) / 2
        pf.color = Color.argb(100, 0, 0, 0); c.drawCircle(cx, cy + 2.5f * dp, rad, pf)
        pf.color = OUTLINE; c.drawCircle(cx, cy, rad, pf)
        pf.color = blend(BTN_RED, Color.BLACK, 0.3f); c.drawCircle(cx, cy, rad - 2 * dp, pf)
        pf.color = if (b.pressed) blend(BTN_RED, Color.WHITE, 0.2f) else BTN_RED
        c.drawCircle(cx, cy - 1.5f * dp, rad - 3.5f * dp, pf)
        Icons.draw(c, Icons.CLOSE, cx, cy - 1.5f * dp, rad * 1.05f, dp = dp)
    }
}
