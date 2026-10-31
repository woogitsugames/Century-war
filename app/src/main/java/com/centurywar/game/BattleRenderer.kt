package com.centurywar.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.centurywar.engine.Game
import com.centurywar.engine.L
import com.centurywar.engine.Ui
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Rysuje bitwę proceduralnie (kształty wektorowe na Canvas). Gra nie ma plików graficznych,
 * więc APK jest malutki, a obraz jest ostry w każdej rozdzielczości, od 720p po Folda.
 * W pętli renderowania nic nie jest alokowane: pędzle, ścieżki i cząsteczki są tworzone raz.
 */
class BattleRenderer(private val g: Game, private val b: Battle) {

    // kamera
    var camX = 0f
    var k = 1f
    var groundY = 0f
    var viewW = 0f
    private var shake = 0f
    private var shakeX = 0f
    private var time = 0f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val sky = Paint()
    private val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val path = Path()
    private val rect = RectF()
    private var skyEra = -1
    private var skyH = 0f
    private var alpha = 1f

    // cząsteczki (struktura tablic: zero obiektów na cząsteczkę)
    private val PMAX = 320
    private val px = FloatArray(PMAX)
    private val ph = FloatArray(PMAX)
    private val pvx = FloatArray(PMAX)
    private val pvh = FloatArray(PMAX)
    private val plife = FloatArray(PMAX)
    private val pmax = FloatArray(PMAX)
    private val psize = FloatArray(PMAX)
    private val pcol = IntArray(PMAX)
    private val ptype = IntArray(PMAX) // 0 kropka, 1 pierścień, 2 błysk, 3 dym
    private var pcount = 0

    // pływające napisy
    private val TMAX = 24
    private val tx = FloatArray(TMAX)
    private val th = FloatArray(TMAX)
    private val tlife = FloatArray(TMAX)
    private val ttext = arrayOfNulls<String>(TMAX)
    private val tcol = IntArray(TMAX)
    private val tsize = FloatArray(TMAX)

    private val goldStrings = HashMap<Int, String>()

    fun layout(w: Float, h: Float, topReserve: Float, bottomReserve: Float) {
        viewW = w
        groundY = h - bottomReserve
        val visibleH = groundY - topReserve
        // Na prawie kwadratowych ekranach (rozłożony Fold, tablet) przybliżamy pole,
        // żeby jednostki nie ginęły pod ogromnym niebem.
        val aspect = w / h
        val worldW = if (aspect < 1.5f) 820f else 1150f
        k = min(visibleH / 430f, w / worldW).coerceAtLeast(0.1f)
        skyEra = -1
        clampCam()
    }

    val worldVisible get() = viewW / k

    fun clampCam() {
        val maxCam = Defs.LANE + 120f - worldVisible
        camX = if (maxCam < -120f) (Defs.LANE - worldVisible) / 2f else camX.coerceIn(-120f, maxCam)
    }

    fun follow(dt: Float) {
        // Delikatnie śledź własną linię frontu, gdy gracz nie przesuwa widoku.
        val target = b.player.front - worldVisible * 0.45f
        camX += (target - camX) * min(1f, dt * 1.5f)
        clampCam()
    }

    private fun sx(x: Float) = (x - camX) * k + shakeX
    private fun sy(h: Float) = groundY - h * k

    private fun col(c: Int): Int =
        if (alpha >= 1f) c else Color.argb((Color.alpha(c) * alpha).toInt(), Color.red(c), Color.green(c), Color.blue(c))

    private fun f(c: Int): Paint { fill.color = col(c); return fill }
    private fun l(c: Int, w: Float): Paint { line.color = col(c); line.strokeWidth = w; return line }

    // ---------------------------------------------------------------- główny rysunek

    fun render(c: Canvas, dt: Float, w: Float, h: Float) {
        time += dt
        val q = g.quality
        fill.isAntiAlias = q.antialias
        line.isAntiAlias = q.antialias
        if (shake > 0f) {
            shake = max(0f, shake - dt * 3f)
            shakeX = sin(time * 70f) * shake * 6f * g.ui.dp
        } else shakeX = 0f

        consumeFx()
        drawSky(c, w, h)
        drawBackdrop(c, w, h)
        drawGround(c, w, h)
        drawBase(c, b.sides[0])
        drawBase(c, b.sides[1])
        for (u in b.units) if (u.active && u.side == 1) drawUnit(c, u)
        for (u in b.units) if (u.active && u.side == 0) drawUnit(c, u)
        alpha = 1f
        for (p in b.projs) if (p.active && p.delay <= 0f) drawProj(c, p)
        updateParticles(c, dt)
        drawTexts(c, dt)
        if (b.fog) drawFog(c, w, h)
    }

    // ---------------------------------------------------------------- tło

    private val skyTop = intArrayOf(0xFF6EBEF0.toInt(), 0xFF78B4E6.toInt(), 0xFF64AAE6.toInt(), 0xFF82AAD2.toInt(), 0xFF969696.toInt(), 0xFF78828C.toInt(), 0xFF3C6EAA.toInt(), 0xFF140F32.toInt())
    private val skyBot = intArrayOf(0xFFC8E6FA.toInt(), 0xFFF0DCB4.toInt(), 0xFFD2E6F5.toInt(), 0xFFE6D2BE.toInt(), 0xFFD2BEA0.toInt(), 0xFFC8BEAA.toInt(), 0xFFAAC8E6.toInt(), 0xFF5A3278.toInt())
    private val hillFar = intArrayOf(0xFF7FA0B8.toInt(), 0xFFC8AA7A.toInt(), 0xFF7E9EB0.toInt(), 0xFF8C9AA0.toInt(), 0xFF7C7A78.toInt(), 0xFF6E7478.toInt(), 0xFF46506E.toInt(), 0xFF3A2860.toInt())
    private val hillMid = intArrayOf(0xFF5A8C50.toInt(), 0xFFB48C5A.toInt(), 0xFF50825A.toInt(), 0xFF6E8250.toInt(), 0xFF646458.toInt(), 0xFF5A644B.toInt(), 0xFF3C465A.toInt(), 0xFF2C2050.toInt())
    private val hillNear = intArrayOf(0xFF3C6E3C.toInt(), 0xFF9C7448.toInt(), 0xFF326440.toInt(), 0xFF506440.toInt(), 0xFF4E4E46.toInt(), 0xFF464E3A.toInt(), 0xFF2A3246.toInt(), 0xFF22183E.toInt())
    private val groundC = intArrayOf(0xFF967846.toInt(), 0xFFC8AA6E.toInt(), 0xFF788C46.toInt(), 0xFF8C7850.toInt(), 0xFF6E5F50.toInt(), 0xFF696450.toInt(), 0xFF5A5A5F.toInt(), 0xFF463C5A.toInt())

    private fun themeEra() = b.player.era

    private fun drawSky(c: Canvas, w: Float, h: Float) {
        val e = themeEra()
        if (e != skyEra || skyH != groundY) {
            skyEra = e; skyH = groundY
            sky.shader = LinearGradient(0f, 0f, 0f, groundY, skyTop[e], skyBot[e], Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w, groundY + 2f, sky)
        if (e == 7) { // gwiazdy
            fill.color = Color.argb(200, 255, 255, 255)
            for (i in 0 until 60) {
                val x = ((i * 977) % 1000) / 1000f * w
                val y = ((i * 613) % 1000) / 1000f * groundY * 0.7f
                val tw = 0.6f + 0.4f * sin(time * 2f + i)
                c.drawCircle(x, y, (1f + (i % 3)) * g.ui.dp * 0.6f * tw, fill)
            }
        }
        if (g.quality.clouds && e != 7) {
            val cloud = if (e == 4 || e == 5) Color.argb(110, 90, 85, 80) else Color.argb(150, 255, 255, 255)
            for (i in 0 until 5) {
                val span = w + 400f * g.ui.dp
                val x = ((i * 397f + time * (8f + i * 3f) * g.ui.dp) % span) - 200f * g.ui.dp
                val y = groundY * (0.12f + 0.09f * (i % 3))
                val s = (40f + 18f * (i % 3)) * g.ui.dp
                f(cloud)
                c.drawCircle(x, y, s * 0.6f, fill)
                c.drawCircle(x + s * 0.6f, y + s * 0.1f, s * 0.5f, fill)
                c.drawCircle(x - s * 0.6f, y + s * 0.15f, s * 0.45f, fill)
                rect.set(x - s, y, x + s, y + s * 0.4f)
                c.drawRoundRect(rect, s * 0.2f, s * 0.2f, fill)
            }
        }
    }

    private fun drawBackdrop(c: Canvas, w: Float, h: Float) {
        val e = themeEra()
        val layers = g.quality.parallaxLayers
        if (layers >= 3) ridge(c, w, 0.15f, 300f, 220f, hillFar[e], 1)
        if (layers >= 2) ridge(c, w, 0.35f, 190f, 140f, hillMid[e], 2)
        props(c, w, 0.7f, hillNear[e], e)
    }

    /** Pasmo wzgórz/gór z paralaksą. Kształt jest deterministyczny (z indeksu), bez losowania w klatce. */
    private fun ridge(c: Canvas, w: Float, par: Float, height: Float, amp: Float, color: Int, seed: Int) {
        val e = themeEra()
        val off = camX * par
        val step = 140f
        path.reset()
        path.moveTo(0f, groundY)
        val first = (off / step).toInt() - 1
        val count = (w / (step * k)).toInt() + 3
        for (i in first..first + count) {
            val hx = (i * step - off) * k
            val hash = (((i * 7349 + seed * 1597) and 0x7fffffff) % 1000) / 1000f
            val hh = if (e == 6 && seed == 2) (height * (0.4f + hash)) else height * 0.5f + amp * hash
            if (e == 6 && seed == 2) { // panorama miasta: prostokąty
                path.lineTo(hx, groundY - hh * k * 0.8f)
                path.lineTo(hx + step * k * 0.8f, groundY - hh * k * 0.8f)
                path.lineTo(hx + step * k * 0.8f, groundY - 40f * k)
            } else if (seed == 1) {
                path.lineTo(hx, groundY - hh * k * 0.75f)
                path.lineTo(hx + step * k * 0.5f, groundY - hh * k * 0.35f)
            } else {
                path.quadTo(hx - step * k * 0.5f, groundY - hh * k * 0.6f, hx, groundY - hh * k * 0.45f)
            }
        }
        path.lineTo(w, groundY)
        path.close()
        c.drawPath(path, f(color))
    }

    /** Najbliższa warstwa: drzewa (epoki dawne), słupy i ruiny (przemysł), wieżowce/anteny (przyszłość). */
    private fun props(c: Canvas, w: Float, par: Float, color: Int, e: Int) {
        val off = camX * par
        val step = 260f
        val first = (off / step).toInt() - 1
        val count = (w / (step * k)).toInt() + 3
        val dark = Ui.blend(color, Color.BLACK, 0.25f)
        for (i in first..first + count) {
            val hash = (((i * 9137 + 77) and 0x7fffffff) % 1000) / 1000f
            val x = (i * step + hash * 120f - off) * k
            val s = (0.7f + hash * 0.6f) * k
            when {
                e <= 3 || e == 1 -> {
                    if (e == 1 && hash > 0.5f) { // palma
                        c.drawRect(x - 4 * s, groundY - 110 * s, x + 4 * s, groundY, f(0xFF7A5A3A.toInt()))
                        for (j in 0 until 5) {
                            val a = -2.6f + j * 0.55f
                            c.drawLine(x, groundY - 110 * s, x + cos(a) * 45 * s, groundY - 110 * s + sin(a) * 30 * s + 20 * s, l(color, 9 * s))
                        }
                    } else if (e == 2 || hash < 0.4f) { // świerk
                        c.drawRect(x - 4 * s, groundY - 30 * s, x + 4 * s, groundY, f(0xFF5A3F28.toInt()))
                        for (j in 0..2) {
                            path.reset()
                            val yy = groundY - (30 + j * 30) * s
                            path.moveTo(x - (40 - j * 9) * s, yy)
                            path.lineTo(x + (40 - j * 9) * s, yy)
                            path.lineTo(x, yy - 55 * s)
                            path.close()
                            c.drawPath(path, f(if (j % 2 == 0) color else dark))
                        }
                    } else { // liściaste
                        c.drawRect(x - 5 * s, groundY - 60 * s, x + 5 * s, groundY, f(0xFF6A4A30.toInt()))
                        c.drawCircle(x, groundY - 80 * s, 34 * s, f(color))
                        c.drawCircle(x - 22 * s, groundY - 64 * s, 24 * s, f(dark))
                        c.drawCircle(x + 24 * s, groundY - 68 * s, 26 * s, f(color))
                    }
                }
                e <= 5 -> {
                    if (hash < 0.5f) { // słup telegraficzny
                        c.drawLine(x, groundY, x, groundY - 120 * s, l(0xFF4A3A2A.toInt(), 5 * s))
                        c.drawLine(x - 18 * s, groundY - 110 * s, x + 18 * s, groundY - 110 * s, l(0xFF4A3A2A.toInt(), 4 * s))
                    } else { // ruina / komin
                        c.drawRect(x - 20 * s, groundY - 90 * s, x + 20 * s, groundY, f(dark))
                        c.drawRect(x + 6 * s, groundY - 140 * s, x + 18 * s, groundY - 90 * s, f(color))
                    }
                }
                else -> {
                    if (hash < 0.5f) { // antena / pylon
                        c.drawLine(x, groundY, x, groundY - 150 * s, l(dark, 4 * s))
                        val blink = if (sin(time * 3f + i) > 0f) 0xFFFF4040.toInt() else 0xFF601010.toInt()
                        c.drawCircle(x, groundY - 150 * s, 4 * s, f(if (e == 7) 0xFF60F0FF.toInt() else blink))
                    } else {
                        rect.set(x - 26 * s, groundY - 120 * s, x + 26 * s, groundY)
                        c.drawRoundRect(rect, 6 * s, 6 * s, f(color))
                        for (yy in 0 until 4) for (xx in 0 until 2) {
                            if (((i + yy + xx) % 3) == 0) continue
                            c.drawRect(x - 18 * s + xx * 20 * s, groundY - 108 * s + yy * 26 * s, x - 8 * s + xx * 20 * s, groundY - 96 * s + yy * 26 * s,
                                f(if (e == 7) 0xFF9060FF.toInt() else 0xFFFFE08A.toInt()))
                        }
                    }
                }
            }
        }
    }

    private fun drawGround(c: Canvas, w: Float, h: Float) {
        val e = themeEra()
        c.drawRect(0f, groundY, w, h, f(groundC[e]))
        c.drawRect(0f, groundY, w, groundY + 5f * k, f(Ui.blend(groundC[e], Color.WHITE, 0.15f)))
        // kamyki / kępki trawy przewijane z polem
        val step = 90f
        val first = (camX / step).toInt() - 1
        val count = (w / (step * k)).toInt() + 3
        val dark = Ui.blend(groundC[e], Color.BLACK, 0.2f)
        for (i in first..first + count) {
            val hash = (((i * 4049 + 13) and 0x7fffffff) % 1000) / 1000f
            val x = sx(i * step + hash * 50f)
            val y = groundY + (10f + hash * 40f) * k
            if (y > h) continue
            c.drawCircle(x, y, (2f + hash * 3f) * k, f(dark))
        }
    }

    // ---------------------------------------------------------------- bazy

    private fun drawBase(c: Canvas, s: Side) {
        val bx = sx(s.baseFront)
        if (s.index == 0 && bx < -400 * k) return
        if (s.index == 1 && bx > viewW + 400 * k) return
        c.save()
        c.translate(bx, groundY)
        c.scale(k * s.dir, k)
        val team = if (s.index == 0) Ui.PLAYER else Ui.ENEMY
        val e = s.era
        when (e) {
            0 -> { // stos głazów z jaskinią
                f(0xFF8A8A8A.toInt())
                c.drawCircle(-150f, -90f, 95f, fill)
                c.drawCircle(-60f, -60f, 65f, f(0xFF9A9A9A.toInt()))
                c.drawCircle(-210f, -150f, 80f, f(0xFF7A7A7A.toInt()))
                c.drawCircle(-120f, -190f, 60f, f(0xFF8E8E8E.toInt()))
                rect.set(-130f, -95f, -60f, 0f); c.drawRoundRect(rect, 35f, 35f, f(0xFF2A2420.toInt()))
                c.drawLine(-100f, -250f, -100f, -320f, l(0xFF6A4A2A.toInt(), 6f))
            }
            1 -> { // schodkowa świątynia z cegły
                for (i in 0 until 4) {
                    c.drawRect(-260f + i * 25f, -70f - i * 60f, -20f - i * 25f, -i * 60f, f(if (i % 2 == 0) 0xFFD2B47A.toInt() else 0xFFC4A26A.toInt()))
                }
                rect.set(-150f, -80f, -110f, 0f); c.drawRoundRect(rect, 18f, 18f, f(0xFF3A2A1A.toInt()))
                c.drawLine(-140f, -240f, -140f, -330f, l(0xFF6A4A2A.toInt(), 5f))
            }
            2 -> { // zamek
                c.drawRect(-280f, -200f, -60f, 0f, f(0xFF9C9C94.toInt()))
                c.drawRect(-230f, -320f, -140f, -200f, f(0xFF8A8A82.toInt()))
                for (i in 0 until 5) c.drawRect(-280f + i * 48f, -225f, -256f + i * 48f, -200f, f(0xFF9C9C94.toInt()))
                for (i in 0 until 3) c.drawRect(-230f + i * 36f, -345f, -212f + i * 36f, -320f, f(0xFF8A8A82.toInt()))
                rect.set(-195f, -100f, -135f, 0f); c.drawRoundRect(rect, 30f, 30f, f(0xFF4A3420.toInt()))
                c.drawLine(-185f, -345f, -185f, -420f, l(0xFF4A3A2A.toInt(), 5f))
            }
            3 -> { // fort z cegły i palisady
                c.drawRect(-290f, -170f, -40f, 0f, f(0xFFA0583C.toInt()))
                c.drawRect(-250f, -270f, -150f, -170f, f(0xFF8E4C34.toInt()))
                for (i in 0 until 9) c.drawRect(-40f + i * 0f, -110f, -20f, 0f, f(0xFF7A5A36.toInt()))
                for (i in 0 until 6) { path.reset(); path.moveTo(-290f + i * 42f, -170f); path.lineTo(-269f + i * 42f, -195f); path.lineTo(-248f + i * 42f, -170f); c.drawPath(path, f(0xFF7A3E2A.toInt())) }
                rect.set(-180f, -90f, -120f, 0f); c.drawRoundRect(rect, 8f, 8f, f(0xFF3A2A1A.toInt()))
                c.drawLine(-200f, -270f, -200f, -360f, l(0xFF3A3A3A.toInt(), 5f))
            }
            4 -> { // fabryka z kominem
                c.drawRect(-300f, -180f, -50f, 0f, f(0xFF8A4A36.toInt()))
                for (i in 0 until 3) { path.reset(); path.moveTo(-300f + i * 83f, -180f); path.lineTo(-217f + i * 83f, -180f); path.lineTo(-217f + i * 83f, -240f); path.close(); c.drawPath(path, f(0xFF6E3A2A.toInt())) }
                c.drawRect(-270f, -380f, -235f, -180f, f(0xFF5A3A30.toInt()))
                for (i in 0 until 3) c.drawRect(-280f + i * 70f, -130f, -240f + i * 70f, -90f, f(0xFFFFD27A.toInt()))
                if (g.quality.level >= 2 && (time * 10f).toInt() % 6 == 0) spawnParticle(s.baseFront - s.dir * 250f, 390f, s.dir * 10f, 40f, 2.2f, 26f, 0x80585050.toInt(), 3)
                c.drawLine(-120f, -180f, -120f, -270f, l(0xFF2A2A2A.toInt(), 5f))
            }
            5 -> { // bunkier
                path.reset(); path.moveTo(-320f, 0f); path.lineTo(-280f, -170f); path.lineTo(-70f, -170f); path.lineTo(-30f, 0f); path.close()
                c.drawPath(path, f(0xFF7E8278.toInt()))
                c.drawRect(-250f, -120f, -100f, -100f, f(0xFF1E201C.toInt()))
                for (i in 0 until 6) { rect.set(-40f + 0f, -24f - i * 0f, 10f, 0f); c.drawRoundRect(rect, 10f, 10f, f(0xFFB4A078.toInt())) }
                c.drawLine(-200f, -170f, -200f, -270f, l(0xFF2A2A2A.toInt(), 5f))
            }
            6 -> { // szklana kwatera główna
                c.drawRect(-300f, -150f, -40f, 0f, f(0xFF6E7C8C.toInt()))
                c.drawRect(-250f, -390f, -140f, -150f, f(0xFF5A90B8.toInt()))
                for (yy in 0 until 8) c.drawRect(-245f, -380f + yy * 29f, -145f, -370f + yy * 29f, f(0xFF8CC8F0.toInt()))
                c.drawLine(-195f, -390f, -195f, -470f, l(0xFFB0B0B0.toInt(), 4f))
                c.drawCircle(-195f, -470f, 6f, f(if (sin(time * 4f) > 0) 0xFFFF3030.toInt() else 0xFF601010.toInt()))
            }
            else -> { // kopuła z pierścieniem energii
                rect.set(-320f, -260f, -40f, 20f); c.drawArc(rect, 180f, 180f, true, f(0xFF4A4070.toInt()))
                rect.set(-290f, -230f, -70f, 10f); c.drawArc(rect, 180f, 180f, true, f(0xFF6A5AA0.toInt()))
                val pulse = 0.5f + 0.5f * sin(time * 3f)
                l(Color.argb((120 + 100 * pulse).toInt(), 120, 240, 255), 8f)
                rect.set(-330f, -170f, -30f, -110f); c.drawOval(rect, line)
                c.drawLine(-180f, -260f, -180f, -340f, l(0xFFB0B0FF.toInt(), 4f))
            }
        }
        // flaga drużyny
        val flagTop = when (e) { 0 -> -320f; 1 -> -330f; 2 -> -420f; 3 -> -360f; 4 -> -270f; 5 -> -270f; 6 -> -440f; else -> -340f }
        val fx0 = when (e) { 0 -> -100f; 1 -> -140f; 2 -> -185f; 3 -> -200f; 4 -> -120f; 5 -> -200f; 6 -> -195f; else -> -180f }
        val wave = sin(time * 5f + s.index) * 6f
        path.reset(); path.moveTo(fx0, flagTop); path.lineTo(fx0 + 60f, flagTop + 12f + wave); path.lineTo(fx0, flagTop + 34f); path.close()
        c.drawPath(path, f(team))

        // wieża z platformami na wieżyczki
        if (s.slots > 0) {
            val top = b.turretHeight(s.slots - 1) + 20f
            c.drawRect(-70f, -top, -14f, 0f, f(Ui.blend(team, 0xFF404040.toInt(), 0.65f)))
            for (i in 0 until s.slots) {
                val hh = b.turretHeight(i)
                c.drawRect(-82f, -hh + 8f, -2f, -hh + 18f, f(0xFF3A3A3A.toInt()))
                val te = s.turretEra[i]
                if (te >= 0) drawTurret(c, te, -40f, -hh + 8f, s.turretFire[i] > 0f)
            }
        }
        if (s.baseFlash > 0f) {
            fill.color = Color.argb((s.baseFlash / 0.15f * 90).toInt(), 255, 255, 255)
            c.drawRect(-300f, -300f, 0f, 0f, fill)
        }
        c.restore()
    }

    private fun drawTurret(c: Canvas, era: Int, x: Float, y: Float, firing: Boolean) {
        when (era) {
            0 -> { c.drawRect(x - 18f, y - 14f, x + 18f, y, f(0xFF6A4A2A.toInt())); c.drawLine(x - 10f, y - 12f, x + 26f, y - 38f, l(0xFF8A6A3A.toInt(), 6f)); c.drawCircle(x + 26f, y - 40f, 8f, f(0xFF8A8A8A.toInt())) }
            1 -> { c.drawRect(x - 20f, y - 12f, x + 10f, y, f(0xFF7A5A36.toInt())); c.drawLine(x - 20f, y - 16f, x + 34f, y - 22f, l(0xFF5A3A20.toInt(), 6f)); c.drawLine(x + 6f, y - 36f, x + 6f, y - 4f, l(0xFF5A3A20.toInt(), 4f)) }
            2 -> { c.drawRect(x - 20f, y - 34f, x + 20f, y, f(0xFF8A8A82.toInt())); for (i in 0..2) c.drawRect(x - 20f + i * 15f, y - 44f, x - 12f + i * 15f, y - 34f, f(0xFF8A8A82.toInt())) }
            3 -> { c.drawCircle(x - 6f, y - 8f, 10f, f(0xFF5A3A20.toInt())); c.drawLine(x - 6f, y - 16f, x + 36f, y - 22f, l(0xFF2A2A2A.toInt(), 11f)) }
            4 -> { c.drawRect(x - 16f, y - 22f, x + 6f, y, f(0xFF6A6A5A.toInt())); for (i in 0..2) c.drawLine(x, y - 18f + i * 5f, x + 36f, y - 18f + i * 5f, l(0xFF3A3A3A.toInt(), 3.5f)) }
            5 -> { c.drawRect(x - 18f, y - 18f, x + 8f, y, f(0xFF5A6248.toInt())); c.drawLine(x, y - 14f, x + 32f, y - 40f, l(0xFF2A2E22.toInt(), 6f)); c.drawLine(x + 6f, y - 10f, x + 38f, y - 36f, l(0xFF2A2E22.toInt(), 6f)) }
            6 -> { rect.set(x - 20f, y - 30f, x + 22f, y); c.drawRoundRect(rect, 6f, 6f, f(0xFF7A8490.toInt())); for (i in 0..1) for (j in 0..1) c.drawCircle(x - 6f + i * 16f, y - 20f + j * 12f, 5f, f(0xFF2A2A2A.toInt())) }
            else -> { c.drawCircle(x - 4f, y - 14f, 14f, f(0xFF6A5AA0.toInt())); c.drawLine(x, y - 14f, x + 40f, y - 20f, l(0xFFB8B0FF.toInt(), 7f)); c.drawCircle(x + 40f, y - 20f, 5f, f(0xFF80F0FF.toInt())) }
        }
        if (firing) c.drawCircle(x + 38f, y - 24f, 10f, f(0xC0FFE080.toInt()))
    }

    // ---------------------------------------------------------------- jednostki

    private val OUTLINE = 0xFF1C1A20.toInt()
    private val skin = intArrayOf(0xFFE0B48A.toInt(), 0xFFD8A878.toInt(), 0xFFE6BE96.toInt(), 0xFFE6BE96.toInt(), 0xFFE0B48A.toInt(), 0xFFD8A878.toInt(), 0xFFE0B48A.toInt(), 0xFFC8C8D8.toInt())
    private val gear = intArrayOf(0xFF8A5A30.toInt(), 0xFFB08040.toInt(), 0xFF9AA0A8.toInt(), 0xFF3A3A5A.toInt(), 0xFF5A4A3A.toInt(), 0xFF5A6248.toInt(), 0xFF3E4650.toInt(), 0xFF8E86C8.toInt())

    private fun drawUnit(c: Canvas, u: Fighter) {
        val x = sx(u.x)
        val margin = 120f * k * u.def.size
        if (x < -margin || x > viewW + margin) return
        if (b.fog && u.side == 1 && u.x - b.player.front > 650f) return
        val s = b.sides[u.side]
        alpha = if (u.dying >= 0f) max(0f, 1f - u.dying / 0.8f) else 1f
        if (g.quality.shadows) {
            fill.color = col(Color.argb(60, 0, 0, 0))
            rect.set(x - 22f * k * u.def.size, groundY - 4f * k, x + 22f * k * u.def.size, groundY + 4f * k)
            c.drawOval(rect, fill)
        }
        c.save()
        c.translate(x, groundY)
        val sc = k * u.def.size
        c.scale(sc * s.dir, sc)
        if (u.dying >= 0f) c.rotate(-min(80f, u.dying * 200f))
        val team = if (u.side == 0) Ui.PLAYER else Ui.ENEMY
        val flash = u.flash > 0f
        val def = u.def
        val walk = if (u.moving) u.anim else 0f
        val atk = if (u.attackAnim > 0f) u.attackAnim / 0.25f else 0f
        when (def.body) {
            Body.HUMAN -> human(c, def.era, def.weapon, team, walk, atk, flash, def.role == Role.HEAVY)
            Body.BEAST -> beast(c, def.era, def.role, team, walk, atk, flash)
            Body.SIEGE -> siege(c, team, walk, atk, flash)
            Body.CANNON -> cannon(c, team, walk, atk, flash)
            Body.VEHICLE -> vehicle(c, def.era, def.role, team, walk, atk, flash)
            Body.MECH -> mech(c, def.era, def.role, team, walk, atk, flash)
            Body.HOVER -> hover(c, team, atk, flash)
        }
        c.restore()
        // pasek HP, gdy ranny
        if (u.dying < 0f && u.hp < u.maxHp) {
            val bw = 34f * k * u.def.size
            val top = groundY - unitHeight(def) * k * def.size - 10f * k
            fill.color = Color.argb(170, 0, 0, 0)
            c.drawRect(x - bw / 2, top, x + bw / 2, top + 4f * k, fill)
            fill.color = team
            c.drawRect(x - bw / 2, top, x - bw / 2 + bw * (u.hp / u.maxHp), top + 4f * k, fill)
        }
    }

    /** Podgląd jednostki na przycisku HUD (ten sam kod rysujący co na polu bitwy). */
    fun drawUnitIcon(c: Canvas, def: UnitDef, cx: Float, bottom: Float, height: Float, team: Int) {
        val sc = height / (unitHeight(def) * def.size + 8f)
        val save = alpha
        alpha = 1f
        c.save()
        c.translate(cx - 6f * sc * def.size, bottom)
        c.scale(sc * def.size, sc * def.size)
        when (def.body) {
            Body.HUMAN -> human(c, def.era, def.weapon, team, 0f, 0f, false, def.role == Role.HEAVY)
            Body.BEAST -> beast(c, def.era, def.role, team, 0f, 0f, false)
            Body.SIEGE -> siege(c, team, 0f, 0f, false)
            Body.CANNON -> cannon(c, team, 0f, 0f, false)
            Body.VEHICLE -> vehicle(c, def.era, def.role, team, 0f, 0f, false)
            Body.MECH -> mech(c, def.era, def.role, team, 0f, 0f, false)
            Body.HOVER -> hover(c, team, 0f, false)
        }
        c.restore()
        alpha = save
    }

    private fun unitHeight(d: UnitDef) = when (d.body) {
        Body.HUMAN -> 66f; Body.BEAST -> if (d.role == Role.ELITE) 80f else 92f
        Body.MECH -> 90f; Body.VEHICLE -> 60f; Body.HOVER -> 56f; Body.SIEGE -> 85f; else -> 50f
    }

    private fun human(c: Canvas, era: Int, weapon: Int, team: Int, walk: Float, atk: Float, flash: Boolean, armored: Boolean) {
        val a = sin(walk) * 0.55f
        val pants = gear[era]
        // nogi
        l(pants, 5.5f)
        c.drawLine(0f, -26f, sin(a) * 15f, 0f, line)
        c.drawLine(0f, -26f, -sin(a) * 15f, 0f, line)
        // tułów
        rect.set(-8f, -50f, 8f, -24f)
        c.drawRoundRect(rect, 4f, 4f, f(if (flash) Color.WHITE else team))
        c.drawRoundRect(rect, 4f, 4f, l(OUTLINE, 1.6f))
        if (armored || era >= 2 && era != 3) {
            rect.set(-8f, -50f, 8f, -40f); c.drawRoundRect(rect, 3f, 3f, f(Ui.blend(gear[era], Color.WHITE, 0.2f)))
        }
        // głowa + nakrycie
        c.drawCircle(1f, -57f, 7.5f, f(if (flash) Color.WHITE else skin[era]))
        c.drawCircle(1f, -57f, 7.5f, l(OUTLINE, 1.4f))
        helmet(c, era)
        // ramię z bronią
        val swing = if (atk > 0f) -atk * 1.4f + 0.6f else 0.15f
        c.save()
        c.translate(2f, -45f)
        c.rotate(Math.toDegrees(swing.toDouble()).toFloat())
        c.drawLine(0f, 0f, 12f, 4f, l(skin[era], 4.5f))
        weaponShape(c, weapon, era, atk)
        c.restore()
        if (armored) { // tarcza
            rect.set(6f, -46f, 16f, -22f); c.drawRoundRect(rect, 4f, 4f, f(Ui.blend(team, Color.BLACK, 0.3f)))
        }
    }

    private fun helmet(c: Canvas, era: Int) {
        when (era) {
            0 -> { c.drawLine(-6f, -63f, 6f, -66f, l(0xFF5A3A20.toInt(), 3f)) } // opaska
            1 -> { rect.set(-7f, -66f, 9f, -56f); c.drawArc(rect, 180f, 180f, true, f(0xFFC08A3A.toInt())); c.drawLine(1f, -66f, -6f, -72f, l(0xFFB03020.toInt(), 3f)) }
            2 -> { rect.set(-8f, -67f, 10f, -54f); c.drawArc(rect, 180f, 200f, true, f(0xFFA8AEB6.toInt())) }
            3 -> { path.reset(); path.moveTo(-10f, -61f); path.lineTo(12f, -61f); path.lineTo(1f, -71f); path.close(); c.drawPath(path, f(0xFF2A2A3A.toInt())) }
            4 -> { rect.set(-7f, -67f, 9f, -60f); c.drawRect(rect, f(0xFF4A3A2A.toInt())); c.drawLine(4f, -60f, 12f, -60f, l(0xFF2A2A2A.toInt(), 2f)) }
            5 -> { rect.set(-9f, -68f, 11f, -54f); c.drawArc(rect, 180f, 180f, true, f(0xFF5A6248.toInt())) }
            6 -> { rect.set(-8f, -68f, 10f, -54f); c.drawArc(rect, 180f, 200f, true, f(0xFF2E3640.toInt())); c.drawLine(3f, -58f, 9f, -58f, l(0xFF60D0FF.toInt(), 2.5f)) }
            else -> { c.drawCircle(1f, -58f, 9f, f(0xFF8E86C8.toInt())); c.drawLine(2f, -58f, 10f, -58f, l(0xFF80FFFF.toInt(), 3f)) }
        }
    }

    private fun weaponShape(c: Canvas, w: Int, era: Int, atk: Float) {
        when (w) {
            Weapon.CLUB -> { c.drawLine(10f, 4f, 26f, -10f, l(0xFF6A4A2A.toInt(), 6f)); c.drawCircle(27f, -11f, 5f, f(0xFF6A4A2A.toInt())) }
            Weapon.SLING -> { c.drawLine(10f, 4f, 20f, -6f, l(0xFF8A6A4A.toInt(), 2f)); c.drawCircle(21f, -7f, 3f, f(0xFF8A8A8A.toInt())) }
            Weapon.SPEAR, Weapon.PIKE -> {
                val len = if (w == Weapon.PIKE) 46f else 34f
                c.drawLine(-6f, 6f, len, 2f, l(0xFF7A5A3A.toInt(), 3f))
                path.reset(); path.moveTo(len, -2f); path.lineTo(len + 9f, 2f); path.lineTo(len, 6f); path.close(); c.drawPath(path, f(0xFFC0C0C8.toInt()))
            }
            Weapon.SWORD -> { c.drawLine(10f, 4f, 34f, -4f, l(0xFFD0D4DA.toInt(), 3.5f)); c.drawLine(12f, -1f, 12f, 9f, l(0xFF6A4A2A.toInt(), 3f)) }
            Weapon.BOW -> { rect.set(8f, -12f, 22f, 20f); c.drawArc(rect, -90f, 180f, false, l(0xFF7A5A3A.toInt(), 2.5f)); c.drawLine(15f, -12f, 15f, 20f, l(0xFFE0E0E0.toInt(), 1f)) }
            Weapon.CROSSBOW -> { c.drawLine(8f, 4f, 28f, 2f, l(0xFF6A4A2A.toInt(), 4f)); c.drawLine(24f, -7f, 24f, 11f, l(0xFF5A3A20.toInt(), 3f)) }
            Weapon.MUSKET, Weapon.RIFLE -> {
                c.drawLine(4f, 6f, 36f, 2f, l(if (w == Weapon.MUSKET) 0xFF5A3A20.toInt() else 0xFF3A3A3A.toInt(), 4f))
                if (atk > 0.7f) c.drawCircle(39f, 2f, 5f, f(0xC0FFE070.toInt()))
            }
            Weapon.MG -> { rect.set(6f, -1f, 30f, 8f); c.drawRect(rect, f(0xFF2A2A2A.toInt())); c.drawLine(28f, 3f, 42f, 3f, l(0xFF2A2A2A.toInt(), 3f)); if (atk > 0.5f) c.drawCircle(44f, 3f, 5f, f(0xC0FFE070.toInt())) }
            Weapon.LAUNCHER -> { rect.set(-6f, -6f, 32f, 4f); c.drawRoundRect(rect, 4f, 4f, f(0xFF4A5A3A.toInt())) }
            Weapon.BLASTER -> { rect.set(6f, -2f, 30f, 7f); c.drawRoundRect(rect, 3f, 3f, f(0xFF5A5080.toInt())); c.drawCircle(31f, 2.5f, if (atk > 0.5f) 5f else 2.5f, f(0xFF80FFFF.toInt())) }
            Weapon.RAIL -> { c.drawLine(2f, 3f, 46f, 1f, l(0xFF6A6A90.toInt(), 5f)); c.drawLine(10f, 3f, 40f, 1f, l(0xFF90F0FF.toInt(), 1.5f)) }
        }
    }

    private fun beast(c: Canvas, era: Int, role: Role, team: Int, walk: Float, atk: Float, flash: Boolean) {
        val big = role == Role.ELITE
        val hide = when { era == 0 -> 0xFF7A5A3A.toInt(); era == 1 && big -> 0xFF8A8A8A.toInt(); era == 1 -> 0xFFB08A5A.toInt(); era == 2 -> 0xFF6A4A3A.toInt(); else -> 0xFF4A3A2A.toInt() }
        val bodyC = if (flash) Color.WHITE else hide
        val a = sin(walk) * 0.5f
        // nogi
        l(Ui.blend(hide, Color.BLACK, 0.25f), if (big) 9f else 6f)
        val lx = if (big) 22f else 18f
        c.drawLine(lx, -24f, lx + sin(a) * 10f, 0f, line)
        c.drawLine(lx - 8f, -24f, lx - 8f - sin(a) * 10f, 0f, line)
        c.drawLine(-lx, -24f, -lx - sin(a) * 10f, 0f, line)
        c.drawLine(-lx + 8f, -24f, -lx + 8f + sin(a) * 10f, 0f, line)
        // korpus
        if (big) {
            rect.set(-36f, -62f, 34f, -20f); c.drawRoundRect(rect, 22f, 22f, f(bodyC))
            c.drawCircle(36f, -50f, 16f, f(bodyC))
            // kły
            c.drawLine(44f, -42f, 58f, -34f + atk * 8f, l(0xFFF0E8D0.toInt(), 4f))
            if (era == 0) { c.drawLine(48f, -46f, 52f, -18f, l(bodyC, 6f)) } // trąba
            // kosz bojowy z barwami drużyny
            rect.set(-20f, -80f, 14f, -60f); c.drawRect(rect, f(team))
            c.drawLine(-20f, -80f, 14f, -80f, l(0xFF5A3A20.toInt(), 3f))
        } else {
            rect.set(-28f, -46f, 26f, -22f); c.drawRoundRect(rect, 12f, 12f, f(bodyC))
            path.reset(); path.moveTo(20f, -40f); path.lineTo(38f, -58f); path.lineTo(44f, -50f); path.lineTo(28f, -32f); path.close()
            c.drawPath(path, f(bodyC))
            if (era == 0) c.drawLine(42f, -54f, 50f, -62f, l(0xFFF0E8D0.toInt(), 3f)) // róg
            rect.set(-12f, -50f, 10f, -42f); c.drawRect(rect, f(team)) // czaprak
            // jeździec
            c.save(); c.translate(-2f, -32f); c.scale(0.85f, 0.85f)
            human(c, era, if (era == 1) Weapon.BOW else if (era == 2) Weapon.PIKE else Weapon.SWORD, team, 0f, atk, flash, false)
            c.restore()
            if (era == 1) { // rydwan: koło
                c.drawCircle(-30f, -12f, 12f, l(0xFF5A3A20.toInt(), 3f))
            }
        }
    }

    private fun siege(c: Canvas, team: Int, walk: Float, atk: Float, flash: Boolean) {
        val wood = if (flash) Color.WHITE else 0xFF7A5A36.toInt()
        c.drawRect(-34f, -22f, 34f, -12f, f(wood))
        c.drawCircle(-24f, -10f, 10f, f(0xFF4A3420.toInt()))
        c.drawCircle(24f, -10f, 10f, f(0xFF4A3420.toInt()))
        c.drawLine(-10f, -20f, 0f, -70f, l(wood, 6f))
        c.drawLine(10f, -20f, 0f, -70f, l(wood, 6f))
        val ang = if (atk > 0f) -150f + atk * 120f else -30f
        c.save(); c.translate(0f, -66f); c.rotate(ang)
        c.drawLine(-20f, 0f, 46f, 0f, l(0xFF5A3A20.toInt(), 5f))
        c.drawRect(-30f, -8f, -16f, 8f, f(0xFF5A5A5A.toInt()))
        c.restore()
        c.drawRect(-34f, -30f, -18f, -22f, f(team))
    }

    private fun cannon(c: Canvas, team: Int, walk: Float, atk: Float, flash: Boolean) {
        c.drawLine(-36f, -8f, 0f, -20f, l(0xFF5A3A20.toInt(), 6f))
        c.save(); c.translate(0f, -24f); c.rotate(-12f - atk * 6f)
        rect.set(-18f - atk * 6f, -9f, 42f - atk * 6f, 9f); c.drawRoundRect(rect, 8f, 8f, f(if (flash) Color.WHITE else 0xFF2E2E2E.toInt()))
        c.restore()
        c.drawCircle(0f, -16f, 16f, l(0xFF6A4A2A.toInt(), 5f))
        c.drawCircle(0f, -16f, 4f, f(team))
        if (atk > 0.75f) c.drawCircle(48f, -36f, 12f, f(0xC0FFE070.toInt()))
    }

    private fun vehicle(c: Canvas, era: Int, role: Role, team: Int, walk: Float, atk: Float, flash: Boolean) {
        val hull = if (flash) Color.WHITE else when (era) { 4 -> 0xFF6A5A48.toInt(); 5 -> 0xFF5A6248.toInt(); else -> 0xFF6E7884.toInt() }
        val big = role == Role.ELITE
        val len = if (big) 46f else 38f
        // gąsienice / koła
        rect.set(-len, -18f, len, 0f); c.drawRoundRect(rect, 9f, 9f, f(0xFF2A2A2A.toInt()))
        val n = if (big) 5 else 4
        for (i in 0 until n) {
            val wx = -len + 9f + i * ((2 * len - 18f) / (n - 1))
            c.drawCircle(wx, -9f, 6f, f(0xFF5A5A5A.toInt()))
            c.drawLine(wx, -9f, wx + cos(walk * 2f) * 5f, -9f + sin(walk * 2f) * 5f, l(0xFF3A3A3A.toInt(), 1.5f))
        }
        // kadłub
        path.reset(); path.moveTo(-len - 2f, -18f); path.lineTo(-len + 6f, -38f); path.lineTo(len - 8f, -38f); path.lineTo(len + 4f, -18f); path.close()
        c.drawPath(path, f(hull))
        rect.set(-len + 10f, -34f, -len + 26f, -26f); c.drawRect(rect, f(team))
        // wieża + lufa
        val recoil = atk * 6f
        rect.set(-18f, -54f, 16f, -36f); c.drawRoundRect(rect, 8f, 8f, f(Ui.blend(hull, Color.BLACK, 0.12f)))
        c.drawLine(10f - recoil, -46f, (if (big) 62f else 50f) - recoil, -48f, l(Ui.blend(hull, Color.BLACK, 0.3f), if (big) 8f else 6f))
        if (era == 4) { // komin parowy
            c.drawRect(-30f, -60f, -22f, -38f, f(0xFF3A3A3A.toInt()))
            if (walk != 0f && g.quality.level >= 2 && ((time * 20f).toInt() % 5 == 0)) { /* dym rysuje system cząsteczek z fabryki */ }
        }
        if (atk > 0.75f) c.drawCircle((if (big) 66f else 54f), -48f, 9f, f(0xC0FFE070.toInt()))
    }

    private fun mech(c: Canvas, era: Int, role: Role, team: Int, walk: Float, atk: Float, flash: Boolean) {
        val metal = if (flash) Color.WHITE else when (era) { 4 -> 0xFFB08A4A.toInt(); 7 -> 0xFF8E86C8.toInt(); else -> 0xFF6E7884.toInt() }
        val big = role == Role.ELITE && era == 7
        val s = if (big) 1.25f else 1f
        val a = sin(walk) * 0.5f
        l(Ui.blend(metal, Color.BLACK, 0.35f), 8f * s)
        c.drawLine(-8f * s, -40f * s, -8f * s + sin(a) * 14f, -20f * s, line); c.drawLine(-8f * s + sin(a) * 14f, -20f * s, -4f * s + sin(a) * 10f, 0f, line)
        c.drawLine(8f * s, -40f * s, 8f * s - sin(a) * 14f, -20f * s, line); c.drawLine(8f * s - sin(a) * 14f, -20f * s, 12f * s - sin(a) * 10f, 0f, line)
        rect.set(-22f * s, -80f * s, 22f * s, -40f * s); c.drawRoundRect(rect, 8f, 8f, f(metal))
        rect.set(-14f * s, -74f * s, 0f, -64f * s); c.drawRect(rect, f(team))
        c.drawRect(4f * s, -76f * s, 18f * s, -68f * s, f(if (era == 4) 0xFFFFC060.toInt() else 0xFF60E0FF.toInt()))
        c.drawLine(16f * s, -56f * s, (44f - atk * 6f) * s, -58f * s, l(Ui.blend(metal, Color.BLACK, 0.4f), 9f * s))
        if (era == 4) c.drawRect(-20f * s, -96f * s, -12f * s, -80f * s, f(0xFF3A3A3A.toInt()))
        if (big) { l(Color.argb(140, 120, 240, 255), 4f); rect.set(-30f, -110f, 30f, -30f); c.drawOval(rect, line) }
        if (atk > 0.75f) c.drawCircle(48f * s, -58f * s, 10f, f(if (era >= 6) 0xC080F0FF.toInt() else 0xC0FFE070.toInt()))
    }

    private fun hover(c: Canvas, team: Int, atk: Float, flash: Boolean) {
        val bob = sin(time * 4f) * 3f
        f(Color.argb(120, 120, 220, 255)); rect.set(-34f, -10f, 34f, 2f); c.drawOval(rect, fill)
        path.reset(); path.moveTo(-40f, -16f + bob); path.lineTo(-30f, -36f + bob); path.lineTo(30f, -36f + bob); path.lineTo(44f, -16f + bob); path.close()
        c.drawPath(path, f(if (flash) Color.WHITE else 0xFF6A6290.toInt()))
        c.drawRect(-24f, -32f + bob, -8f, -24f + bob, f(team))
        rect.set(-14f, -50f + bob, 16f, -34f + bob); c.drawRoundRect(rect, 8f, 8f, f(0xFF8E86C8.toInt()))
        c.drawLine(12f, -42f + bob, 52f - atk * 5f, -44f + bob, l(0xFFB8B0FF.toInt(), 6f))
        if (atk > 0.6f) c.drawCircle(56f, -44f + bob, 8f, f(0xC080FFFF.toInt()))
    }

    // ---------------------------------------------------------------- pociski

    private fun drawProj(c: Canvas, p: Projectile) {
        val x = sx(p.x)
        if (x < -50f || x > viewW + 50f) return
        val y = sy(p.h)
        val ang = atan2(-p.vh, p.vx)
        val ca = cos(ang); val sa = sin(ang)
        val kk = k
        when (p.type) {
            Proj.STONE -> c.drawCircle(x, y, 4f * kk, f(0xFF8A8A8A.toInt()))
            Proj.BOULDER -> c.drawCircle(x, y, 10f * kk, f(0xFF7A7A7A.toInt()))
            Proj.JAVELIN, Proj.ARROW -> c.drawLine(x - ca * 16f * kk, y - sa * 16f * kk, x + ca * 10f * kk, y + sa * 10f * kk, l(0xFF6A4A2A.toInt(), 2.5f * kk))
            Proj.BOLT -> c.drawLine(x - ca * 10f * kk, y - sa * 10f * kk, x + ca * 6f * kk, y + sa * 6f * kk, l(0xFF3A2A1A.toInt(), 3f * kk))
            Proj.BALL -> c.drawCircle(x, y, 6f * kk, f(0xFF1E1E1E.toInt()))
            Proj.BULLET -> c.drawLine(x - ca * 14f * kk, y - sa * 14f * kk, x, y, l(0xFFFFE070.toInt(), 2.5f * kk))
            Proj.SHELL -> { c.drawCircle(x, y, 5f * kk, f(0xFF3A3A30.toInt())); c.drawLine(x - ca * 14f * kk, y - sa * 14f * kk, x, y, l(0x80FFFFFF.toInt(), 2f * kk)) }
            Proj.ROCKET -> {
                c.drawLine(x - ca * 14f * kk, y - sa * 14f * kk, x + ca * 6f * kk, y + sa * 6f * kk, l(0xFFD0D0D0.toInt(), 5f * kk))
                c.drawCircle(x - ca * 16f * kk, y - sa * 16f * kk, 5f * kk, f(0xFFFF9030.toInt()))
            }
            Proj.PLASMA -> { c.drawCircle(x, y, 9f * kk, f(0x6080FFFF)); c.drawCircle(x, y, 5f * kk, f(0xFFE0FFFF.toInt())) }
            Proj.BEAM -> c.drawLine(x - ca * 50f * kk, y - sa * 50f * kk, x, y, l(0xFF90F8FF.toInt(), 4f * kk))
        }
    }

    // ---------------------------------------------------------------- efekty

    private fun consumeFx() {
        val fx = b.fx
        val dp = 1f
        for (i in 0 until fx.count) {
            val x = fx.x[i]; val h = fx.h[i]
            when (fx.type[i]) {
                FxBuffer.HIT -> for (j in 0 until 3) spawnParticle(x, h, rnd(-80f, 80f), rnd(40f, 160f), 0.35f, 3f, 0xFFFFD080.toInt(), 0)
                FxBuffer.DEATH -> {
                    val s = fx.value[i]
                    for (j in 0 until 6) spawnParticle(x, 20f, rnd(-60f, 60f), rnd(20f, 90f), 0.7f, 6f * s, 0xC0A09080.toInt(), 3)
                }
                FxBuffer.EXPLOSION -> {
                    val s = fx.value[i]
                    spawnParticle(x, h, 0f, 0f, 0.35f, 60f * s, 0xFFFFC060.toInt(), 1)
                    spawnParticle(x, h, 0f, 0f, 0.18f, 40f * s, 0xFFFFF0C0.toInt(), 2)
                    val n = if (g.quality.level == 1) 4 else 9
                    for (j in 0 until n) spawnParticle(x, h, rnd(-200f, 200f) * s, rnd(80f, 320f) * s, 0.6f, 5f * s, if (j % 2 == 0) 0xFFFF8030.toInt() else 0xFF5A5040.toInt(), 0)
                    for (j in 0 until n / 2) spawnParticle(x, h + 10f, rnd(-30f, 30f), rnd(20f, 60f), 1.2f, 18f * s, 0x90505050.toInt(), 3)
                    if (s > 1.2f) shake = min(1.5f, shake + 0.6f) else if (s > 0.9f) shake = min(1f, shake + 0.15f)
                }
                FxBuffer.MUZZLE -> spawnParticle(x, h, 0f, 0f, 0.07f, 9f * dp, 0xFFFFE890.toInt(), 2)
                FxBuffer.BASE_HIT -> for (j in 0 until 3) spawnParticle(x, h + rnd(0f, 120f), -b.sides[fx.side[i]].dir * rnd(20f, 140f), rnd(60f, 200f), 0.6f, 5f, 0xFF8A7A6A.toInt(), 0)
                FxBuffer.EVOLVE -> {
                    val sd = b.sides[fx.side[i]]
                    for (j in 0 until 24) {
                        val a = j / 24f * 6.283f
                        spawnParticle(x - sd.dir * 150f, 150f, cos(a) * 260f, sin(a) * 260f, 1.0f, 7f, 0xFFFFE070.toInt(), 2)
                    }
                    val label = if (fx.value[i] > 0f) L.f("rank", sd.rank) else L["next_era"]
                    addText(x - sd.dir * 150f, 360f, label, if (sd.index == 0) 0xFFFFE070.toInt() else Ui.ENEMY, 22f)
                }
                FxBuffer.GOLD -> {
                    val v = fx.value[i].toInt()
                    val s = goldStrings.getOrPut(v) { "+$v" }
                    addText(x, h, s, Ui.GOLD, 13f)
                }
                FxBuffer.BARRAGE -> shake = min(1.2f, shake + 0.4f)
            }
        }
        fx.count = 0
    }

    private var seed = 12345
    private fun rnd(a: Float, b2: Float): Float {
        seed = seed * 1103515245 + 12345
        return a + ((seed ushr 8) and 0xFFFF) / 65535f * (b2 - a)
    }

    private fun spawnParticle(x: Float, h: Float, vx: Float, vh: Float, life: Float, size: Float, color: Int, type: Int) {
        val maxP = g.quality.maxParticles
        if (pcount >= maxP || pcount >= PMAX) return
        val i = pcount++
        px[i] = x; ph[i] = h; pvx[i] = vx; pvh[i] = vh; plife[i] = life; pmax[i] = life; psize[i] = size; pcol[i] = color; ptype[i] = type
    }

    private fun updateParticles(c: Canvas, dt: Float) {
        var i = 0
        while (i < pcount) {
            plife[i] -= dt
            if (plife[i] <= 0f) {
                val last = --pcount
                px[i] = px[last]; ph[i] = ph[last]; pvx[i] = pvx[last]; pvh[i] = pvh[last]; plife[i] = plife[last]
                pmax[i] = pmax[last]; psize[i] = psize[last]; pcol[i] = pcol[last]; ptype[i] = ptype[last]
                continue
            }
            val t = plife[i] / pmax[i]
            px[i] += pvx[i] * dt
            ph[i] += pvh[i] * dt
            when (ptype[i]) {
                0 -> { pvh[i] -= 700f * dt; if (ph[i] < 0f) { ph[i] = 0f; pvh[i] *= -0.3f; pvx[i] *= 0.6f } }
                3 -> { pvx[i] *= 0.98f }
            }
            val x = sx(px[i]); val y = sy(ph[i])
            val a = Color.alpha(pcol[i]) / 255f
            fill.color = Color.argb((255 * a * min(1f, t * 1.5f)).toInt(), Color.red(pcol[i]), Color.green(pcol[i]), Color.blue(pcol[i]))
            when (ptype[i]) {
                0 -> c.drawRect(x - psize[i] * k / 2, y - psize[i] * k / 2, x + psize[i] * k / 2, y + psize[i] * k / 2, fill)
                1 -> { line.color = fill.color; line.strokeWidth = 6f * k * t; c.drawCircle(x, y, psize[i] * k * (1.4f - t), line) }
                2 -> c.drawCircle(x, y, psize[i] * k * (0.5f + t * 0.5f), fill)
                3 -> c.drawCircle(x, y, psize[i] * k * (1.6f - t * 0.8f), fill)
            }
            i++
        }
    }

    private fun addText(x: Float, h: Float, s: String, color: Int, size: Float) {
        var slot = 0
        var minLife = Float.MAX_VALUE
        for (i in 0 until TMAX) { if (tlife[i] < minLife) { minLife = tlife[i]; slot = i } }
        tx[slot] = x; th[slot] = h; ttext[slot] = s; tcol[slot] = color; tlife[slot] = 1.2f; tsize[slot] = size
    }

    private fun drawTexts(c: Canvas, dt: Float) {
        for (i in 0 until TMAX) {
            if (tlife[i] <= 0f) continue
            tlife[i] -= dt
            th[i] += 40f * dt
            val s = ttext[i] ?: continue
            val a = min(1f, tlife[i] * 2f)
            textP.textSize = tsize[i] * g.ui.dp
            textP.color = Color.argb((180 * a).toInt(), 0, 0, 0)
            c.drawText(s, sx(tx[i]) + 1.5f * g.ui.dp, sy(th[i]) + 1.5f * g.ui.dp, textP)
            textP.color = Color.argb((255 * a).toInt(), Color.red(tcol[i]), Color.green(tcol[i]), Color.blue(tcol[i]))
            c.drawText(s, sx(tx[i]), sy(th[i]), textP)
        }
    }

    private fun drawFog(c: Canvas, w: Float, h: Float) {
        val start = sx(b.player.front + 500f)
        if (start >= w) return
        fill.color = Color.argb(150, 30, 30, 40)
        c.drawRect(max(0f, start + 150f * k), 0f, w, groundY + 4f, fill)
        fill.color = Color.argb(80, 30, 30, 40)
        c.drawRect(max(0f, start), 0f, max(0f, start + 150f * k), groundY + 4f, fill)
    }

    /** Pozycje jednostek w skali minimapy (rysuje HUD). */
    fun worldToMini(x: Float, l: Float, width: Float) = l + x / Defs.LANE * width

}
