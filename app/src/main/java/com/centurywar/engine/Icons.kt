package com.centurywar.engine

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Proceduralne ikony wektorowe w stylu gry: płaskie kolory, gruby ciemny kontur.
 * Zastępują znaki emoji (☄ ⚑ ⚒ ✕ ✎ 🔒 ★), które nie na każdym telefonie mają glif.
 *
 * Każda ikona jest rysowana w układzie jednostkowym [-1, 1] (oś Y w dół), który
 * [draw] skaluje do żądanego rozmiaru. Wszystkie Path/Paint/RectF są tworzone raz,
 * więc rysowanie nie alokuje pamięci (wywołuje je tylko wątek gry).
 */
object Icons {
    const val SWORD = 0
    const val CROSSED_SWORDS = 1
    const val MAP = 2
    const val TOWER = 3
    const val CALENDAR = 4
    const val FLASK = 5
    const val TROPHY = 6
    const val GEAR = 7
    const val STAR = 8
    const val STAR_EMPTY = 9
    const val MEDAL = 10
    const val COIN = 11
    const val XP = 12
    const val LOCK = 13
    const val CLOSE = 14
    const val CHECK = 15
    const val PENCIL = 16
    const val PAUSE = 17
    const val PLAY = 18
    const val BACK = 19
    const val RESTART = 20
    const val FLAG = 21
    const val METEOR = 22
    const val ANVIL = 23
    const val SHIELD = 24
    const val ADVANCE = 25
    const val SPEED = 26
    const val EVOLVE = 27
    const val HELMET = 28
    const val CROWN = 29
    const val COINS = 30
    const val CHEST = 31
    const val WALL = 32
    const val CROSSHAIR = 33
    const val STOPWATCH = 34
    const val BOOK = 35
    const val TENT = 36
    const val HOURGLASS = 37
    const val WRENCH = 38
    const val FLAME = 39
    const val BAG = 40
    const val BOLT = 41
    const val CHEVRONS = 42
    const val BOMB = 43
    const val WHITE_FLAG = 44
    const val SKULL = 45
    const val GLOBE = 46
    const val VIBRATE = 47
    const val MONITOR = 48
    const val PERSON = 49

    val OUTLINE = Color.rgb(26, 22, 32)
    private val GOLD = Color.rgb(255, 200, 50)
    private val GOLD_D = Color.rgb(214, 140, 30)
    private val GOLD_L = Color.rgb(255, 236, 140)
    private val STEEL = Color.rgb(200, 210, 222)
    private val STEEL_D = Color.rgb(130, 142, 160)
    private val STONE = Color.rgb(170, 168, 160)
    private val STONE_D = Color.rgb(120, 116, 110)
    private val WOOD = Color.rgb(150, 96, 52)
    private val WOOD_D = Color.rgb(104, 64, 34)
    private val RED = Color.rgb(222, 62, 52)
    private val RED_D = Color.rgb(160, 36, 36)
    private val BLUE = Color.rgb(64, 140, 235)
    private val GREEN = Color.rgb(96, 200, 90)
    private val WHITE = Color.rgb(246, 244, 236)
    private val PAPER = Color.rgb(240, 222, 176)
    private val PAPER_D = Color.rgb(212, 188, 136)

    private val p = Path()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    private val r = RectF()
    /** Grubość konturu w jednostkach ikony (ustawiana w [draw]). */
    private var ow = 0.12f
    private var tint = 0

    /**
     * Rysuje ikonę [id] o boku [size] px, ze środkiem w ([cx], [cy]).
     * [tint] (0 = domyślny) zmienia główny kolor ikon "drużynowych" (flaga, tarcza).
     * [alpha] < 255 przygasza ikonę (np. zablokowany przycisk).
     */
    fun draw(c: Canvas, id: Int, cx: Float, cy: Float, size: Float, tint: Int = 0, alpha: Int = 255, dp: Float = 0f) {
        if (size <= 1f) return
        val s = size / 2f
        // Kontur ~7% rozmiaru, ale nie cieńszy niż ~1.3 dp (czytelność małych ikon).
        val minPx = if (dp > 0f) 1.3f * dp else size * 0.06f
        ow = max(size * 0.075f, minPx) / s
        this.tint = tint
        fill.alpha = 255; line.alpha = 255
        val save = if (alpha < 255) c.saveLayerAlpha(cx - s * 1.3f, cy - s * 1.3f, cx + s * 1.3f, cy + s * 1.3f, alpha) else c.save()
        c.translate(cx, cy)
        c.scale(s, s)
        when (id) {
            SWORD -> { c.rotate(40f); sword(c, 1f) }
            CROSSED_SWORDS -> crossedSwords(c)
            MAP -> map(c)
            TOWER -> tower(c)
            CALENDAR -> calendar(c)
            FLASK -> flask(c)
            TROPHY -> trophy(c)
            GEAR -> gear(c, STEEL)
            STAR -> star(c, 0f, 0f, 0.95f, GOLD, true)
            STAR_EMPTY -> star(c, 0f, 0f, 0.95f, Color.rgb(70, 66, 84), false)
            MEDAL -> medal(c)
            COIN -> coin(c, 0f, 0f, 0.9f)
            XP -> gem(c)
            LOCK -> lock(c)
            CLOSE -> cross(c)
            CHECK -> check(c)
            PENCIL -> pencil(c)
            PAUSE -> pause(c)
            PLAY -> play(c)
            BACK -> back(c)
            RESTART -> restart(c)
            FLAG -> flag(c, if (tint != 0) tint else RED)
            WHITE_FLAG -> flag(c, WHITE)
            METEOR -> meteor(c)
            ANVIL -> anvil(c)
            SHIELD -> shield(c, if (tint != 0) tint else BLUE)
            ADVANCE -> advance(c)
            SPEED -> speed(c)
            EVOLVE -> evolve(c)
            HELMET -> helmet(c)
            CROWN -> crown(c)
            COINS -> coins(c)
            CHEST -> chest(c)
            WALL -> wall(c)
            CROSSHAIR -> crosshair(c)
            STOPWATCH -> stopwatch(c)
            BOOK -> book(c)
            TENT -> tent(c)
            HOURGLASS -> hourglass(c)
            WRENCH -> wrench(c)
            FLAME -> flame(c)
            BAG -> bag(c)
            BOLT -> bolt(c)
            CHEVRONS -> chevrons(c)
            BOMB -> bomb(c)
            SKULL -> skull(c)
            GLOBE -> globe(c)
            VIBRATE -> vibrate(c)
            MONITOR -> monitor(c)
            PERSON -> person(c)
        }
        c.restoreToCount(save)
    }

    // ------------------------------------------------------------ prymitywy

    private fun fillPath(c: Canvas, color: Int) {
        fill.color = color
        c.drawPath(p, fill)
    }

    private fun outline(c: Canvas, w: Float = ow) {
        line.color = OUTLINE
        line.strokeWidth = w
        c.drawPath(p, line)
    }

    /** Wypełnia bieżącą ścieżkę i obrysowuje ją konturem. */
    private fun shape(c: Canvas, color: Int) { fillPath(c, color); outline(c) }

    private fun circle(c: Canvas, x: Float, y: Float, rad: Float, color: Int, outlined: Boolean = true) {
        fill.color = color
        c.drawCircle(x, y, rad, fill)
        if (outlined) { line.color = OUTLINE; line.strokeWidth = ow; c.drawCircle(x, y, rad, line) }
    }

    private fun rect(c: Canvas, l: Float, t: Float, rr: Float, b: Float, color: Int, rad: Float = 0f, outlined: Boolean = true) {
        r.set(l, t, rr, b)
        fill.color = color
        c.drawRoundRect(r, rad, rad, fill)
        if (outlined) { line.color = OUTLINE; line.strokeWidth = ow; c.drawRoundRect(r, rad, rad, line) }
    }

    /** Linia z konturem (najpierw gruba ciemna, potem kolorowa). */
    private fun stroke(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, width: Float, color: Int) {
        line.color = OUTLINE
        line.strokeWidth = width + ow * 2f
        c.drawLine(x0, y0, x1, y1, line)
        line.color = color
        line.strokeWidth = width
        c.drawLine(x0, y0, x1, y1, line)
    }

    private fun plainLine(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, width: Float, color: Int) {
        line.color = color
        line.strokeWidth = width
        c.drawLine(x0, y0, x1, y1, line)
    }

    private fun shine(c: Canvas, x: Float, y: Float, rad: Float) {
        fill.color = Color.argb(150, 255, 255, 255)
        c.drawCircle(x, y, rad, fill)
    }

    private fun starPath(cx: Float, cy: Float, ro: Float, ri: Float) {
        p.reset()
        for (i in 0 until 10) {
            val a = -Math.PI / 2 + i * Math.PI / 5
            val rad = if (i % 2 == 0) ro else ri
            val x = cx + (cos(a) * rad).toFloat()
            val y = cy + (sin(a) * rad).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
    }

    fun star(c: Canvas, cx: Float, cy: Float, ro: Float, color: Int, filled: Boolean) {
        starPath(cx, cy + ro * 0.06f, ro, ro * 0.48f)
        if (filled) {
            fillPath(c, color)
            // lewa połowa jaśniejsza = fazowanie
            fill.color = Color.argb(90, 255, 255, 255)
            c.save(); c.clipRect(cx - ro, cy - ro, cx, cy + ro); c.drawPath(p, fill); c.restore()
            outline(c)
        } else {
            shape(c, color)
        }
    }

    // ------------------------------------------------------------ ikony

    private fun sword(c: Canvas, scale: Float) {
        c.save(); c.scale(scale, scale)
        p.reset()
        p.moveTo(-0.14f, 0.3f); p.lineTo(-0.14f, -0.72f); p.lineTo(0f, -0.98f); p.lineTo(0.14f, -0.72f); p.lineTo(0.14f, 0.3f); p.close()
        shape(c, STEEL)
        plainLine(c, 0f, -0.75f, 0f, 0.25f, ow * 0.7f, STEEL_D)
        rect(c, -0.45f, 0.28f, 0.45f, 0.44f, GOLD, 0.06f)
        rect(c, -0.09f, 0.44f, 0.09f, 0.78f, WOOD)
        circle(c, 0f, 0.86f, 0.12f, GOLD)
        c.restore()
    }

    private fun crossedSwords(c: Canvas) {
        c.save(); c.rotate(-40f); sword(c, 0.95f); c.restore()
        c.save(); c.rotate(40f); sword(c, 0.95f); c.restore()
    }

    private fun map(c: Canvas) {
        p.reset()
        p.moveTo(-0.9f, -0.6f); p.lineTo(-0.3f, -0.78f); p.lineTo(0.3f, -0.6f); p.lineTo(0.9f, -0.78f)
        p.lineTo(0.9f, 0.62f); p.lineTo(0.3f, 0.8f); p.lineTo(-0.3f, 0.62f); p.lineTo(-0.9f, 0.8f); p.close()
        fillPath(c, PAPER)
        p.reset(); p.moveTo(-0.3f, -0.78f); p.lineTo(0.3f, -0.6f); p.lineTo(0.3f, 0.8f); p.lineTo(-0.3f, 0.62f); p.close()
        fillPath(c, PAPER_D)
        // zielone "lądy"
        fill.color = Color.rgb(140, 190, 100)
        c.drawCircle(-0.6f, -0.2f, 0.18f, fill)
        c.drawCircle(0.6f, 0.3f, 0.16f, fill)
        // przerywana trasa
        fill.color = RED_D
        c.drawCircle(-0.6f, 0.45f, 0.07f, fill)
        c.drawCircle(-0.38f, 0.28f, 0.07f, fill)
        c.drawCircle(-0.12f, 0.2f, 0.07f, fill)
        c.drawCircle(0.12f, 0.05f, 0.07f, fill)
        c.drawCircle(0.32f, -0.12f, 0.07f, fill)
        stroke(c, 0.42f, -0.48f, 0.7f, -0.2f, 0.12f, RED)
        stroke(c, 0.7f, -0.48f, 0.42f, -0.2f, 0.12f, RED)
        p.reset()
        p.moveTo(-0.9f, -0.6f); p.lineTo(-0.3f, -0.78f); p.lineTo(0.3f, -0.6f); p.lineTo(0.9f, -0.78f)
        p.lineTo(0.9f, 0.62f); p.lineTo(0.3f, 0.8f); p.lineTo(-0.3f, 0.62f); p.lineTo(-0.9f, 0.8f); p.close()
        outline(c)
        plainLine(c, -0.3f, -0.78f, -0.3f, 0.62f, ow * 0.7f, OUTLINE)
        plainLine(c, 0.3f, -0.6f, 0.3f, 0.8f, ow * 0.7f, OUTLINE)
    }

    private fun tower(c: Canvas) {
        // maszt + flaga
        plainLine(c, 0f, -0.55f, 0f, -0.98f, ow * 1.1f, OUTLINE)
        p.reset(); p.moveTo(0.02f, -0.98f); p.lineTo(0.5f, -0.86f); p.lineTo(0.02f, -0.72f); p.close()
        shape(c, if (tint != 0) tint else RED)
        // korpus
        rect(c, -0.48f, -0.32f, 0.48f, 0.92f, STONE)
        // blanki
        p.reset()
        p.moveTo(-0.66f, -0.3f); p.lineTo(-0.66f, -0.66f); p.lineTo(-0.4f, -0.66f); p.lineTo(-0.4f, -0.5f)
        p.lineTo(-0.13f, -0.5f); p.lineTo(-0.13f, -0.66f); p.lineTo(0.13f, -0.66f); p.lineTo(0.13f, -0.5f)
        p.lineTo(0.4f, -0.5f); p.lineTo(0.4f, -0.66f); p.lineTo(0.66f, -0.66f); p.lineTo(0.66f, -0.3f); p.close()
        shape(c, Color.rgb(190, 188, 180))
        // cegły
        plainLine(c, -0.48f, 0.1f, 0.48f, 0.1f, ow * 0.6f, STONE_D)
        plainLine(c, -0.48f, 0.5f, 0.48f, 0.5f, ow * 0.6f, STONE_D)
        plainLine(c, -0.15f, -0.3f, -0.15f, 0.1f, ow * 0.6f, STONE_D)
        plainLine(c, 0.22f, 0.1f, 0.22f, 0.5f, ow * 0.6f, STONE_D)
        // brama
        p.reset(); p.moveTo(-0.22f, 0.92f); p.lineTo(-0.22f, 0.62f); p.quadTo(0f, 0.38f, 0.22f, 0.62f); p.lineTo(0.22f, 0.92f); p.close()
        shape(c, WOOD_D)
        rect(c, -0.07f, -0.2f, 0.07f, 0.02f, OUTLINE, 0.05f, false)
    }

    private fun calendar(c: Canvas) {
        rect(c, -0.8f, -0.62f, 0.8f, 0.88f, WHITE, 0.14f)
        r.set(-0.8f, -0.62f, 0.8f, -0.18f)
        fill.color = RED; c.drawRoundRect(r, 0.14f, 0.14f, fill)
        c.drawRect(-0.8f + ow / 2, -0.3f, 0.8f - ow / 2, -0.18f, fill)
        plainLine(c, -0.8f, -0.18f, 0.8f, -0.18f, ow, OUTLINE)
        rect(c, -0.8f, -0.62f, 0.8f, 0.88f, 0, 0.14f, false)
        r.set(-0.8f, -0.62f, 0.8f, 0.88f); line.color = OUTLINE; line.strokeWidth = ow; c.drawRoundRect(r, 0.14f, 0.14f, line)
        rect(c, -0.5f, -0.86f, -0.32f, -0.42f, STEEL, 0.08f)
        rect(c, 0.32f, -0.86f, 0.5f, -0.42f, STEEL, 0.08f)
        star(c, 0f, 0.36f, 0.4f, GOLD, true)
    }

    private fun flask(c: Canvas) {
        p.reset()
        p.moveTo(-0.2f, -0.72f); p.lineTo(-0.2f, -0.28f); p.lineTo(-0.74f, 0.66f)
        p.quadTo(-0.78f, 0.86f, -0.56f, 0.86f); p.lineTo(0.56f, 0.86f); p.quadTo(0.78f, 0.86f, 0.74f, 0.66f)
        p.lineTo(0.2f, -0.28f); p.lineTo(0.2f, -0.72f); p.close()
        fillPath(c, Color.rgb(214, 236, 246))
        // płyn
        c.save(); c.clipPath(p)
        fill.color = Color.rgb(110, 220, 120)
        c.drawRect(-1f, 0.18f, 1f, 1f, fill)
        fill.color = Color.rgb(160, 240, 160)
        c.drawRect(-1f, 0.18f, 1f, 0.3f, fill)
        c.restore()
        outline(c)
        circle(c, -0.18f, 0.5f, 0.09f, WHITE, false)
        circle(c, 0.2f, 0.62f, 0.06f, WHITE, false)
        circle(c, 0.05f, 0.05f, 0.07f, Color.rgb(160, 240, 160), true)
        rect(c, -0.3f, -0.92f, 0.3f, -0.68f, WOOD, 0.06f)
        plainLine(c, -0.12f, -0.1f, -0.3f, 0.2f, ow * 0.9f, Color.argb(200, 255, 255, 255))
    }

    private fun trophy(c: Canvas) {
        // uchwyty
        line.color = OUTLINE; line.strokeWidth = 0.24f + ow * 2
        r.set(-0.9f, -0.66f, -0.3f, 0.06f); c.drawArc(r, 90f, 180f, false, line)
        r.set(0.3f, -0.66f, 0.9f, 0.06f); c.drawArc(r, -90f, 180f, false, line)
        line.color = GOLD_D; line.strokeWidth = 0.24f
        r.set(-0.9f, -0.66f, -0.3f, 0.06f); c.drawArc(r, 90f, 180f, false, line)
        r.set(0.3f, -0.66f, 0.9f, 0.06f); c.drawArc(r, -90f, 180f, false, line)
        rect(c, -0.13f, 0.18f, 0.13f, 0.56f, GOLD_D)
        rect(c, -0.52f, 0.54f, 0.52f, 0.88f, WOOD, 0.08f)
        rect(c, -0.28f, 0.62f, 0.28f, 0.78f, GOLD_L, 0.04f)
        p.reset()
        p.moveTo(-0.62f, -0.84f); p.lineTo(0.62f, -0.84f); p.lineTo(0.56f, -0.3f)
        p.quadTo(0.46f, 0.2f, 0f, 0.24f); p.quadTo(-0.46f, 0.2f, -0.56f, -0.3f); p.close()
        fillPath(c, GOLD)
        fill.color = GOLD_D
        c.save(); c.clipPath(p); c.drawRect(0.2f, -1f, 1f, 1f, fill); c.restore()
        outline(c)
        star(c, 0f, -0.36f, 0.28f, GOLD_L, true)
        shine(c, -0.36f, -0.62f, 0.08f)
    }

    private fun gearPath(ro: Float, ri: Float, teeth: Int) {
        p.reset()
        val n = teeth * 4
        for (i in 0 until n) {
            val a = i * Math.PI * 2 / n
            val rad = if ((i / 2) % 2 == 0) ro else ri
            val x = (cos(a) * rad).toFloat()
            val y = (sin(a) * rad).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
    }

    private fun gear(c: Canvas, color: Int) {
        c.save(); c.rotate(11f)
        gearPath(0.95f, 0.72f, 8)
        p.addCircle(0f, 0f, 0.3f, Path.Direction.CCW)
        p.fillType = Path.FillType.EVEN_ODD
        shape(c, color)
        p.fillType = Path.FillType.WINDING
        line.color = STEEL_D; line.strokeWidth = ow * 0.8f
        c.drawCircle(0f, 0f, 0.52f, line)
        c.restore()
    }

    private fun medal(c: Canvas) {
        p.reset(); p.moveTo(-0.5f, -0.95f); p.lineTo(-0.1f, -0.95f); p.lineTo(0.18f, -0.2f); p.lineTo(-0.2f, -0.1f); p.close()
        shape(c, BLUE)
        p.reset(); p.moveTo(0.5f, -0.95f); p.lineTo(0.1f, -0.95f); p.lineTo(-0.18f, -0.2f); p.lineTo(0.2f, -0.1f); p.close()
        shape(c, RED)
        circle(c, 0f, 0.3f, 0.6f, GOLD)
        line.color = GOLD_D; line.strokeWidth = ow * 0.8f; c.drawCircle(0f, 0.3f, 0.44f, line)
        star(c, 0f, 0.3f, 0.3f, GOLD_L, true)
    }

    fun coin(c: Canvas, x: Float, y: Float, rad: Float) {
        circle(c, x, y, rad, GOLD)
        fill.color = GOLD_D
        c.save(); r.set(x - rad, y - rad, x + rad, y + rad); c.clipRect(x - rad, y, x + rad, y + rad)
        c.drawCircle(x, y, rad - ow / 2, fill); c.restore()
        circle(c, x, y, rad * 0.62f, GOLD, false)
        line.color = GOLD_D; line.strokeWidth = ow * 0.8f; c.drawCircle(x, y, rad * 0.62f, line)
        rect(c, x - rad * 0.12f, y - rad * 0.36f, x + rad * 0.12f, y + rad * 0.36f, GOLD_L, rad * 0.05f, false)
        line.color = OUTLINE; line.strokeWidth = ow; c.drawCircle(x, y, rad, line)
        shine(c, x - rad * 0.5f, y - rad * 0.5f, rad * 0.13f)
    }

    private fun gem(c: Canvas) {
        p.reset(); p.moveTo(-0.5f, -0.75f); p.lineTo(0.5f, -0.75f); p.lineTo(0.9f, -0.25f); p.lineTo(0f, 0.9f); p.lineTo(-0.9f, -0.25f); p.close()
        fillPath(c, Color.rgb(90, 180, 255))
        p.reset(); p.moveTo(-0.9f, -0.25f); p.lineTo(0.9f, -0.25f); p.lineTo(0f, 0.9f); p.close()
        fillPath(c, Color.rgb(50, 130, 230))
        p.reset(); p.moveTo(-0.35f, -0.25f); p.lineTo(0.35f, -0.25f); p.lineTo(0f, 0.9f); p.close()
        fillPath(c, Color.rgb(70, 155, 245))
        p.reset(); p.moveTo(-0.5f, -0.75f); p.lineTo(-0.35f, -0.25f); p.lineTo(0f, -0.75f); p.lineTo(0.35f, -0.25f); p.lineTo(0.5f, -0.75f); p.close()
        fillPath(c, Color.rgb(170, 220, 255))
        p.reset(); p.moveTo(-0.5f, -0.75f); p.lineTo(0.5f, -0.75f); p.lineTo(0.9f, -0.25f); p.lineTo(0f, 0.9f); p.lineTo(-0.9f, -0.25f); p.close()
        outline(c)
        plainLine(c, -0.9f, -0.25f, 0.9f, -0.25f, ow * 0.6f, OUTLINE)
    }

    private fun lock(c: Canvas) {
        line.color = OUTLINE; line.strokeWidth = 0.2f + ow * 2
        r.set(-0.42f, -0.92f, 0.42f, 0.0f); c.drawArc(r, 180f, 180f, false, line)
        c.drawLine(-0.42f, -0.46f, -0.42f, -0.05f, line); c.drawLine(0.42f, -0.46f, 0.42f, -0.05f, line)
        line.color = STEEL; line.strokeWidth = 0.2f
        c.drawArc(r, 180f, 180f, false, line)
        c.drawLine(-0.42f, -0.46f, -0.42f, -0.05f, line); c.drawLine(0.42f, -0.46f, 0.42f, -0.05f, line)
        rect(c, -0.72f, -0.12f, 0.72f, 0.9f, GOLD, 0.16f)
        rect(c, -0.72f, 0.5f, 0.72f, 0.9f, GOLD_D, 0.16f, false)
        rect(c, -0.72f, -0.12f, 0.72f, 0.9f, 0, 0.16f, false)
        r.set(-0.72f, -0.12f, 0.72f, 0.9f); line.color = OUTLINE; line.strokeWidth = ow; c.drawRoundRect(r, 0.16f, 0.16f, line)
        circle(c, 0f, 0.3f, 0.14f, OUTLINE, false)
        rect(c, -0.06f, 0.3f, 0.06f, 0.62f, OUTLINE, 0.04f, false)
    }

    private fun cross(c: Canvas) {
        stroke(c, -0.55f, -0.55f, 0.55f, 0.55f, 0.32f, WHITE)
        stroke(c, 0.55f, -0.55f, -0.55f, 0.55f, 0.32f, WHITE)
        plainLine(c, -0.55f, -0.55f, 0.55f, 0.55f, 0.32f, WHITE)
    }

    private fun check(c: Canvas) {
        p.reset(); p.moveTo(-0.7f, 0.0f); p.lineTo(-0.2f, 0.5f); p.lineTo(0.7f, -0.5f)
        line.color = OUTLINE; line.strokeWidth = 0.34f + ow * 2; c.drawPath(p, line)
        line.color = GREEN; line.strokeWidth = 0.34f; c.drawPath(p, line)
    }

    private fun pencil(c: Canvas) {
        c.save(); c.rotate(45f)
        p.reset(); p.moveTo(-0.22f, -0.6f); p.lineTo(0.22f, -0.6f); p.lineTo(0.22f, 0.5f); p.lineTo(0f, 0.95f); p.lineTo(-0.22f, 0.5f); p.close()
        shape(c, GOLD)
        p.reset(); p.moveTo(-0.22f, 0.5f); p.lineTo(0.22f, 0.5f); p.lineTo(0f, 0.95f); p.close()
        shape(c, PAPER)
        p.reset(); p.moveTo(-0.08f, 0.78f); p.lineTo(0.08f, 0.78f); p.lineTo(0f, 0.95f); p.close()
        fillPath(c, OUTLINE)
        rect(c, -0.22f, -0.9f, 0.22f, -0.6f, Color.rgb(240, 130, 150), 0.08f)
        plainLine(c, 0f, -0.55f, 0f, 0.45f, ow * 0.6f, GOLD_D)
        c.restore()
    }

    private fun pause(c: Canvas) {
        rect(c, -0.55f, -0.7f, -0.12f, 0.7f, WHITE, 0.1f)
        rect(c, 0.12f, -0.7f, 0.55f, 0.7f, WHITE, 0.1f)
    }

    private fun play(c: Canvas) {
        p.reset(); p.moveTo(-0.45f, -0.75f); p.lineTo(0.75f, 0f); p.lineTo(-0.45f, 0.75f); p.close()
        shape(c, if (tint != 0) tint else WHITE)
    }

    private fun back(c: Canvas) {
        p.reset()
        p.moveTo(-0.85f, 0f); p.lineTo(-0.1f, -0.72f); p.lineTo(-0.1f, -0.3f); p.lineTo(0.8f, -0.3f)
        p.lineTo(0.8f, 0.3f); p.lineTo(-0.1f, 0.3f); p.lineTo(-0.1f, 0.72f); p.close()
        shape(c, WHITE)
    }

    private fun restart(c: Canvas) {
        r.set(-0.62f, -0.62f, 0.62f, 0.62f)
        line.color = OUTLINE; line.strokeWidth = 0.24f + ow * 2; line.strokeCap = Paint.Cap.BUTT
        c.drawArc(r, -60f, 290f, false, line)
        line.color = WHITE; line.strokeWidth = 0.24f
        c.drawArc(r, -60f, 290f, false, line)
        line.strokeCap = Paint.Cap.ROUND
        p.reset(); p.moveTo(0.05f, -0.98f); p.lineTo(0.62f, -0.6f); p.lineTo(0.05f, -0.24f); p.close()
        shape(c, WHITE)
    }

    private fun flag(c: Canvas, color: Int) {
        rect(c, -0.7f, -0.9f, -0.54f, 0.92f, WOOD, 0.05f)
        circle(c, -0.62f, -0.9f, 0.1f, GOLD)
        p.reset()
        p.moveTo(-0.54f, -0.8f); p.quadTo(-0.1f, -0.98f, 0.25f, -0.78f); p.quadTo(0.55f, -0.62f, 0.86f, -0.76f)
        p.lineTo(0.86f, 0.06f); p.quadTo(0.55f, 0.2f, 0.25f, 0.04f); p.quadTo(-0.1f, -0.16f, -0.54f, 0.02f); p.close()
        fillPath(c, color)
        fill.color = Color.argb(60, 0, 0, 0)
        c.save(); c.clipPath(p); c.drawRect(0.25f, -1f, 0.6f, 1f, fill); c.restore()
        outline(c)
    }

    private fun meteor(c: Canvas) {
        p.reset()
        p.moveTo(-0.1f, -0.55f); p.lineTo(0.95f, -0.95f); p.lineTo(0.55f, 0.1f); p.close()
        shape(c, Color.rgb(255, 140, 40))
        p.reset()
        p.moveTo(-0.05f, -0.3f); p.lineTo(0.65f, -0.65f); p.lineTo(0.3f, 0.05f); p.close()
        fillPath(c, Color.rgb(255, 230, 90))
        circle(c, -0.3f, 0.3f, 0.55f, Color.rgb(140, 100, 80))
        circle(c, -0.45f, 0.18f, 0.13f, Color.rgb(100, 70, 56), false)
        circle(c, -0.15f, 0.5f, 0.1f, Color.rgb(100, 70, 56), false)
        circle(c, -0.08f, 0.15f, 0.07f, Color.rgb(100, 70, 56), false)
    }

    private fun anvil(c: Canvas) {
        p.reset()
        p.moveTo(-0.92f, -0.2f); p.lineTo(0.85f, -0.2f); p.lineTo(0.85f, 0.02f); p.lineTo(0.36f, 0.2f)
        p.lineTo(0.36f, 0.48f); p.lineTo(0.62f, 0.62f); p.lineTo(0.62f, 0.86f); p.lineTo(-0.62f, 0.86f)
        p.lineTo(-0.62f, 0.62f); p.lineTo(-0.36f, 0.48f); p.lineTo(-0.36f, 0.2f); p.lineTo(-0.55f, 0.08f)
        p.quadTo(-0.85f, 0.02f, -0.92f, -0.2f); p.close()
        shape(c, Color.rgb(96, 102, 116))
        plainLine(c, -0.75f, -0.08f, 0.75f, -0.08f, ow * 0.7f, Color.rgb(150, 156, 170))
        // młot
        c.save(); c.rotate(-30f, 0.3f, -0.55f)
        rect(c, 0.25f, -0.62f, 0.95f, -0.48f, WOOD)
        rect(c, -0.05f, -0.86f, 0.3f, -0.24f, STEEL_D, 0.06f)
        c.restore()
    }

    private fun shield(c: Canvas, color: Int) {
        p.reset()
        p.moveTo(-0.72f, -0.82f); p.lineTo(0.72f, -0.82f); p.lineTo(0.72f, -0.1f)
        p.quadTo(0.66f, 0.56f, 0f, 0.94f); p.quadTo(-0.66f, 0.56f, -0.72f, -0.1f); p.close()
        fillPath(c, color)
        fill.color = Color.argb(70, 0, 0, 0)
        c.save(); c.clipPath(p); c.drawRect(0f, -1f, 1f, 1f, fill); c.restore()
        outline(c)
        c.save(); c.scale(0.7f, 0.7f, 0f, -0.05f)
        line.color = GOLD; line.strokeWidth = ow * 1.2f; c.drawPath(p, line)
        c.restore()
        plainLine(c, 0f, -0.55f, 0f, 0.55f, 0.16f, GOLD)
        plainLine(c, -0.4f, -0.2f, 0.4f, -0.2f, 0.16f, GOLD)
    }

    private fun advance(c: Canvas) {
        p.reset()
        p.moveTo(0.9f, 0f); p.lineTo(0.15f, -0.72f); p.lineTo(0.15f, -0.32f); p.lineTo(-0.8f, -0.32f)
        p.lineTo(-0.8f, 0.32f); p.lineTo(0.15f, 0.32f); p.lineTo(0.15f, 0.72f); p.close()
        shape(c, GREEN)
        plainLine(c, -0.65f, -0.18f, 0.1f, -0.18f, ow * 0.8f, Color.argb(130, 255, 255, 255))
    }

    private fun speed(c: Canvas) {
        p.reset(); p.moveTo(-0.9f, -0.66f); p.lineTo(0.0f, 0f); p.lineTo(-0.9f, 0.66f); p.close()
        shape(c, WHITE)
        p.reset(); p.moveTo(-0.05f, -0.66f); p.lineTo(0.85f, 0f); p.lineTo(-0.05f, 0.66f); p.close()
        shape(c, WHITE)
    }

    private fun evolve(c: Canvas) {
        p.reset()
        p.moveTo(0f, -0.92f); p.lineTo(0.78f, -0.08f); p.lineTo(0.32f, -0.08f); p.lineTo(0.32f, 0.86f)
        p.lineTo(-0.32f, 0.86f); p.lineTo(-0.32f, -0.08f); p.lineTo(-0.78f, -0.08f); p.close()
        fillPath(c, GOLD)
        fill.color = GOLD_D
        c.save(); c.clipPath(p); c.drawRect(0.05f, -1f, 1f, 1f, fill); c.restore()
        outline(c)
    }

    private fun helmet(c: Canvas) {
        // grzebień
        p.reset(); p.moveTo(-0.55f, -0.62f); p.quadTo(0f, -1.08f, 0.6f, -0.6f); p.lineTo(0.42f, -0.42f); p.quadTo(0f, -0.72f, -0.4f, -0.44f); p.close()
        shape(c, RED)
        p.reset()
        p.moveTo(-0.7f, 0.5f); p.lineTo(-0.7f, -0.05f); p.quadTo(-0.66f, -0.72f, 0f, -0.72f)
        p.quadTo(0.66f, -0.72f, 0.7f, -0.05f); p.lineTo(0.7f, 0.5f); p.lineTo(0.35f, 0.82f)
        p.lineTo(0.3f, 0.12f); p.lineTo(-0.3f, 0.12f); p.lineTo(-0.35f, 0.82f); p.close()
        fillPath(c, STEEL)
        fill.color = STEEL_D
        c.save(); c.clipPath(p); c.drawRect(0.15f, -1f, 1f, 1f, fill); c.restore()
        outline(c)
        plainLine(c, -0.7f, -0.05f, 0.7f, -0.05f, ow * 0.8f, OUTLINE)
        shine(c, -0.36f, -0.4f, 0.09f)
    }

    private fun crown(c: Canvas) {
        p.reset()
        p.moveTo(-0.82f, 0.62f); p.lineTo(-0.9f, -0.5f); p.lineTo(-0.42f, -0.06f); p.lineTo(0f, -0.72f)
        p.lineTo(0.42f, -0.06f); p.lineTo(0.9f, -0.5f); p.lineTo(0.82f, 0.62f); p.close()
        fillPath(c, GOLD)
        fill.color = GOLD_D
        c.save(); c.clipPath(p); c.drawRect(-1f, 0.3f, 1f, 1f, fill); c.restore()
        outline(c)
        circle(c, -0.9f, -0.56f, 0.12f, GOLD)
        circle(c, 0f, -0.8f, 0.12f, GOLD)
        circle(c, 0.9f, -0.56f, 0.12f, GOLD)
        circle(c, 0f, 0.15f, 0.15f, RED)
        circle(c, -0.48f, 0.3f, 0.1f, BLUE)
        circle(c, 0.48f, 0.3f, 0.1f, GREEN)
    }

    private fun coins(c: Canvas) {
        for (i in 0..2) {
            val y = 0.62f - i * 0.26f
            r.set(-0.85f, y - 0.2f, 0.25f, y + 0.2f)
            fill.color = if (i % 2 == 0) GOLD_D else GOLD
            c.drawOval(r, fill)
            line.color = OUTLINE; line.strokeWidth = ow; c.drawOval(r, line)
        }
        coin(c, 0.32f, -0.2f, 0.6f)
    }

    private fun chest(c: Canvas) {
        p.reset(); p.moveTo(-0.85f, -0.1f); p.lineTo(-0.85f, -0.35f); p.quadTo(0f, -0.95f, 0.85f, -0.35f); p.lineTo(0.85f, -0.1f); p.close()
        shape(c, WOOD)
        rect(c, -0.85f, -0.1f, 0.85f, 0.8f, WOOD_D, 0.06f)
        rect(c, -0.62f, -0.1f, -0.42f, 0.8f, GOLD, 0f)
        rect(c, 0.42f, -0.1f, 0.62f, 0.8f, GOLD, 0f)
        rect(c, -0.16f, -0.25f, 0.16f, 0.15f, GOLD_L, 0.05f)
        circle(c, 0f, -0.04f, 0.05f, OUTLINE, false)
        circle(c, -0.3f, -0.42f, 0.11f, GOLD_L)
        circle(c, 0.12f, -0.52f, 0.11f, GOLD_L)
    }

    private fun wall(c: Canvas) {
        p.reset()
        p.moveTo(-0.92f, 0.85f); p.lineTo(-0.92f, -0.6f); p.lineTo(-0.58f, -0.6f); p.lineTo(-0.58f, -0.36f)
        p.lineTo(-0.2f, -0.36f); p.lineTo(-0.2f, -0.6f); p.lineTo(0.2f, -0.6f); p.lineTo(0.2f, -0.36f)
        p.lineTo(0.58f, -0.36f); p.lineTo(0.58f, -0.6f); p.lineTo(0.92f, -0.6f); p.lineTo(0.92f, 0.85f); p.close()
        shape(c, STONE)
        for (row in 0..3) {
            val y = -0.15f + row * 0.25f
            plainLine(c, -0.92f, y, 0.92f, y, ow * 0.6f, STONE_D)
            var x = if (row % 2 == 0) -0.6f else -0.35f
            while (x < 0.92f) { plainLine(c, x, y, x, y + 0.25f, ow * 0.6f, STONE_D); x += 0.5f }
        }
        outline(c)
        c.save(); c.translate(0.45f, 0.35f); c.scale(0.5f, 0.5f)
        stroke(c, 0f, -0.7f, 0f, 0.7f, 0.4f, GREEN); stroke(c, -0.7f, 0f, 0.7f, 0f, 0.4f, GREEN); plainLine(c, 0f, -0.7f, 0f, 0.7f, 0.4f, GREEN)
        c.restore()
    }

    private fun crosshair(c: Canvas) {
        circle(c, 0f, 0f, 0.82f, WHITE)
        circle(c, 0f, 0f, 0.58f, RED)
        circle(c, 0f, 0f, 0.34f, WHITE)
        circle(c, 0f, 0f, 0.13f, RED)
        // strzała
        stroke(c, 0.95f, -0.95f, 0.05f, -0.05f, 0.08f, WOOD)
        p.reset(); p.moveTo(0.95f, -0.95f); p.lineTo(0.62f, -0.92f); p.lineTo(0.92f, -0.62f); p.close()
        shape(c, RED)
    }

    private fun stopwatch(c: Canvas) {
        rect(c, -0.16f, -0.98f, 0.16f, -0.72f, STEEL_D, 0.04f)
        stroke(c, 0.52f, -0.62f, 0.66f, -0.76f, 0.12f, STEEL_D)
        circle(c, 0f, 0.1f, 0.82f, STEEL)
        circle(c, 0f, 0.1f, 0.64f, WHITE)
        for (i in 0 until 12) {
            val a = i * Math.PI / 6
            val x = (cos(a) * 0.52f).toFloat(); val y = (sin(a) * 0.52f).toFloat() + 0.1f
            fill.color = OUTLINE; c.drawCircle(x, y, 0.04f, fill)
        }
        plainLine(c, 0f, 0.1f, 0f, -0.36f, 0.1f, RED)
        plainLine(c, 0f, 0.1f, 0.28f, 0.26f, 0.1f, OUTLINE)
        circle(c, 0f, 0.1f, 0.08f, OUTLINE, false)
    }

    private fun book(c: Canvas) {
        p.reset(); p.moveTo(0f, -0.55f); p.quadTo(-0.45f, -0.82f, -0.92f, -0.6f); p.lineTo(-0.92f, 0.62f)
        p.quadTo(-0.45f, 0.42f, 0f, 0.7f); p.close()
        shape(c, WHITE)
        p.reset(); p.moveTo(0f, -0.55f); p.quadTo(0.45f, -0.82f, 0.92f, -0.6f); p.lineTo(0.92f, 0.62f)
        p.quadTo(0.45f, 0.42f, 0f, 0.7f); p.close()
        shape(c, PAPER)
        for (i in 0..3) {
            val y = -0.35f + i * 0.22f
            plainLine(c, -0.75f, y, -0.18f, y + 0.04f, ow * 0.6f, STEEL_D)
            plainLine(c, 0.18f, y + 0.04f, 0.75f, y, ow * 0.6f, STEEL_D)
        }
        p.reset(); p.moveTo(-0.95f, 0.66f); p.quadTo(-0.45f, 0.5f, 0f, 0.8f); p.quadTo(0.45f, 0.5f, 0.95f, 0.66f)
        p.lineTo(0.95f, 0.82f); p.quadTo(0.45f, 0.66f, 0f, 0.95f); p.quadTo(-0.45f, 0.66f, -0.95f, 0.82f); p.close()
        shape(c, RED_D)
        rect(c, 0.32f, -0.8f, 0.46f, -0.2f, RED, 0f)
    }

    private fun tent(c: Canvas) {
        plainLine(c, 0f, -0.6f, 0f, -0.98f, ow * 1.1f, OUTLINE)
        p.reset(); p.moveTo(0.02f, -0.98f); p.lineTo(0.45f, -0.88f); p.lineTo(0.02f, -0.76f); p.close()
        shape(c, BLUE)
        p.reset(); p.moveTo(0f, -0.66f); p.lineTo(0.95f, 0.82f); p.lineTo(-0.95f, 0.82f); p.close()
        fillPath(c, Color.rgb(214, 180, 120))
        fill.color = Color.rgb(180, 144, 92)
        c.save(); c.clipPath(p); c.drawRect(0f, -1f, 1f, 1f, fill); c.restore()
        outline(c)
        p.reset(); p.moveTo(0f, -0.1f); p.lineTo(0.3f, 0.82f); p.lineTo(-0.3f, 0.82f); p.close()
        shape(c, WOOD_D)
    }

    private fun hourglass(c: Canvas) {
        p.reset(); p.moveTo(-0.55f, -0.72f); p.lineTo(0.55f, -0.72f); p.quadTo(0.5f, -0.2f, 0.08f, 0f)
        p.quadTo(0.5f, 0.2f, 0.55f, 0.72f); p.lineTo(-0.55f, 0.72f); p.quadTo(-0.5f, 0.2f, -0.08f, 0f)
        p.quadTo(-0.5f, -0.2f, -0.55f, -0.72f); p.close()
        fillPath(c, Color.rgb(214, 236, 246))
        c.save(); c.clipPath(p)
        fill.color = GOLD
        c.drawRect(-1f, 0.38f, 1f, 1f, fill)
        c.drawRect(-1f, -0.4f, 1f, -0.1f, fill)
        c.drawRect(-0.03f, -0.1f, 0.03f, 0.4f, fill)
        c.restore()
        outline(c)
        rect(c, -0.75f, -0.95f, 0.75f, -0.72f, WOOD, 0.06f)
        rect(c, -0.75f, 0.72f, 0.75f, 0.95f, WOOD, 0.06f)
    }

    private fun wrench(c: Canvas) {
        c.save(); c.rotate(45f)
        rect(c, -0.15f, -0.25f, 0.15f, 0.92f, STEEL_D, 0.12f)
        p.reset()
        p.addCircle(0f, -0.52f, 0.42f, Path.Direction.CW)
        r.set(-0.15f, -1.0f, 0.15f, -0.5f)
        p.addRect(r, Path.Direction.CCW)
        p.fillType = Path.FillType.EVEN_ODD
        shape(c, STEEL)
        p.fillType = Path.FillType.WINDING
        c.restore()
        c.save(); c.translate(-0.45f, 0.45f); c.scale(0.45f, 0.45f); gear(c, GOLD); c.restore()
    }

    private fun flame(c: Canvas) {
        p.reset()
        p.moveTo(0f, 0.92f); p.quadTo(-0.78f, 0.88f, -0.72f, 0.12f); p.quadTo(-0.62f, -0.32f, -0.3f, -0.5f)
        p.quadTo(-0.36f, -0.12f, -0.1f, 0f); p.quadTo(-0.12f, -0.6f, 0.2f, -0.95f); p.quadTo(0.3f, -0.45f, 0.6f, -0.2f)
        p.quadTo(0.86f, 0.2f, 0.66f, 0.56f); p.quadTo(0.45f, 0.9f, 0f, 0.92f); p.close()
        shape(c, Color.rgb(255, 120, 40))
        p.reset()
        p.moveTo(0f, 0.82f); p.quadTo(-0.42f, 0.78f, -0.38f, 0.36f); p.quadTo(-0.3f, 0.06f, -0.06f, -0.06f)
        p.quadTo(0f, 0.2f, 0.18f, 0.24f); p.quadTo(0.18f, -0.1f, 0.3f, -0.3f); p.quadTo(0.52f, 0.1f, 0.45f, 0.45f)
        p.quadTo(0.36f, 0.8f, 0f, 0.82f); p.close()
        fillPath(c, Color.rgb(255, 220, 80))
    }

    private fun bag(c: Canvas) {
        p.reset()
        p.moveTo(-0.3f, -0.45f); p.quadTo(-0.95f, 0.05f, -0.8f, 0.6f); p.quadTo(-0.7f, 0.92f, 0f, 0.92f)
        p.quadTo(0.7f, 0.92f, 0.8f, 0.6f); p.quadTo(0.95f, 0.05f, 0.3f, -0.45f); p.close()
        shape(c, Color.rgb(196, 150, 92))
        p.reset(); p.moveTo(-0.3f, -0.45f); p.lineTo(-0.5f, -0.88f); p.lineTo(0f, -0.7f); p.lineTo(0.5f, -0.88f); p.lineTo(0.3f, -0.45f); p.close()
        shape(c, Color.rgb(176, 130, 76))
        stroke(c, -0.36f, -0.45f, 0.36f, -0.45f, 0.08f, RED)
        coin(c, 0f, 0.3f, 0.36f)
    }

    private fun bolt(c: Canvas) {
        p.reset()
        p.moveTo(0.2f, -0.95f); p.lineTo(-0.6f, 0.12f); p.lineTo(-0.05f, 0.12f); p.lineTo(-0.25f, 0.95f)
        p.lineTo(0.62f, -0.18f); p.lineTo(0.08f, -0.18f); p.close()
        shape(c, Color.rgb(255, 220, 60))
    }

    private fun chevrons(c: Canvas) {
        for (i in 0..2) {
            val y = -0.62f + i * 0.5f
            p.reset(); p.moveTo(-0.8f, y + 0.25f); p.lineTo(0f, y - 0.2f); p.lineTo(0.8f, y + 0.25f)
            p.lineTo(0.8f, y + 0.55f); p.lineTo(0f, y + 0.1f); p.lineTo(-0.8f, y + 0.55f); p.close()
            shape(c, if (i == 0) GOLD else GOLD_D)
        }
    }

    private fun bomb(c: Canvas) {
        stroke(c, 0.3f, -0.45f, 0.55f, -0.75f, 0.12f, WOOD)
        circle(c, 0.66f, -0.84f, 0.16f, Color.rgb(255, 200, 60))
        circle(c, -0.08f, 0.18f, 0.72f, Color.rgb(60, 64, 76))
        rect(c, 0.08f, -0.6f, 0.46f, -0.32f, STEEL_D, 0.04f)
        shine(c, -0.36f, -0.1f, 0.14f)
    }

    private fun skull(c: Canvas) {
        p.reset(); p.addCircle(0f, -0.15f, 0.72f, Path.Direction.CW)
        r.set(-0.4f, 0.2f, 0.4f, 0.85f); p.addRoundRect(r, 0.12f, 0.12f, Path.Direction.CW)
        shape(c, WHITE)
        fill.color = WHITE; c.drawCircle(0f, -0.15f, 0.72f - ow / 2, fill)
        circle(c, -0.28f, -0.1f, 0.18f, OUTLINE, false)
        circle(c, 0.28f, -0.1f, 0.18f, OUTLINE, false)
        plainLine(c, -0.15f, 0.55f, -0.15f, 0.8f, ow, OUTLINE)
        plainLine(c, 0.15f, 0.55f, 0.15f, 0.8f, ow, OUTLINE)
    }

    private fun globe(c: Canvas) {
        circle(c, 0f, 0f, 0.86f, Color.rgb(90, 170, 240))
        fill.color = Color.rgb(110, 200, 110)
        c.drawCircle(-0.3f, -0.25f, 0.3f, fill)
        c.drawCircle(0.35f, 0.3f, 0.26f, fill)
        line.color = OUTLINE; line.strokeWidth = ow * 0.7f
        r.set(-0.4f, -0.86f, 0.4f, 0.86f); c.drawOval(r, line)
        c.drawLine(-0.86f, 0f, 0.86f, 0f, line)
        line.strokeWidth = ow; c.drawCircle(0f, 0f, 0.86f, line)
    }

    private fun vibrate(c: Canvas) {
        rect(c, -0.42f, -0.82f, 0.42f, 0.82f, Color.rgb(60, 66, 84), 0.14f)
        rect(c, -0.3f, -0.64f, 0.3f, 0.5f, Color.rgb(120, 190, 240), 0.04f, false)
        circle(c, 0f, 0.66f, 0.06f, STEEL, false)
        plainLine(c, -0.65f, -0.35f, -0.65f, 0.35f, ow * 1.1f, WHITE)
        plainLine(c, 0.65f, -0.35f, 0.65f, 0.35f, ow * 1.1f, WHITE)
        plainLine(c, -0.85f, -0.2f, -0.85f, 0.2f, ow * 1.1f, WHITE)
        plainLine(c, 0.85f, -0.2f, 0.85f, 0.2f, ow * 1.1f, WHITE)
    }

    private fun monitor(c: Canvas) {
        rect(c, -0.9f, -0.72f, 0.9f, 0.42f, Color.rgb(60, 66, 84), 0.1f)
        rect(c, -0.76f, -0.58f, 0.76f, 0.28f, Color.rgb(110, 190, 240), 0.02f, false)
        fill.color = Color.rgb(120, 200, 110)
        p.reset(); p.moveTo(-0.76f, 0.28f); p.lineTo(-0.3f, -0.2f); p.lineTo(0.05f, 0.1f); p.lineTo(0.4f, -0.3f); p.lineTo(0.76f, 0.05f); p.lineTo(0.76f, 0.28f); p.close()
        c.drawPath(p, fill)
        rect(c, -0.12f, 0.42f, 0.12f, 0.7f, STEEL_D)
        rect(c, -0.5f, 0.68f, 0.5f, 0.86f, STEEL, 0.06f)
    }

    private fun person(c: Canvas) {
        p.reset(); p.moveTo(-0.8f, 0.92f); p.quadTo(-0.8f, 0.12f, 0f, 0.12f); p.quadTo(0.8f, 0.12f, 0.8f, 0.92f); p.close()
        shape(c, BLUE)
        circle(c, 0f, -0.38f, 0.42f, Color.rgb(240, 200, 160))
    }
}
