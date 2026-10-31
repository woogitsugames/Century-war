package com.centurywar.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Picture
import android.graphics.PorterDuff
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.centurywar.engine.Game
import com.centurywar.engine.L
import com.centurywar.engine.Ui
import com.centurywar.game.art.Bases
import com.centurywar.game.art.Machines
import com.centurywar.game.art.Pen
import com.centurywar.game.art.Soldiers
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

    // rysowanie szczegółowe (art/*): wspólny pisak + moduły
    private val pen = Pen()
    private val men = Soldiers(pen)
    private val mach = Machines(pen, men)
    private val bases = Bases(pen)
    /** Nagrane korpusy baz: indeks = strona * 8 + epoka. */
    private val basePics = arrayOfNulls<Picture>(16)
    private var basePicDetail = -1
    private val PIC_OX = 420f
    private val PIC_OY = 540f

    fun layout(w: Float, h: Float, topReserve: Float, bottomReserve: Float) {
        viewW = w
        groundY = h - bottomReserve
        val visibleH = groundY - topReserve
        // Na prawie kwadratowych ekranach (rozłożony Fold, tablet) przybliżamy pole,
        // żeby jednostki nie ginęły pod ogromnym niebem.
        val aspect = w / h
        val worldW = if (aspect < 1.5f) 630f else 1100f
        k = min(visibleH / 360f, w / worldW).coerceAtLeast(0.1f)
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
        pen.cv = c
        pen.setAntiAlias(q.antialias)
        pen.detail = q.level
        pen.alpha = 1f; pen.flash = false
        mach.time = time; bases.time = time
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

    /** Korpus bazy nagrany raz do Picture (tekstury nie kosztują w kolejnych klatkach). */
    private fun basePicture(side: Int, era: Int, team: Int): Picture {
        if (basePicDetail != pen.detail) { for (i in basePics.indices) basePics[i] = null; basePicDetail = pen.detail }
        val idx = side * 8 + era
        basePics[idx]?.let { return it }
        val pic = Picture()
        val rc = pic.beginRecording((PIC_OX + 40f).toInt(), (PIC_OY + 40f).toInt())
        rc.translate(PIC_OX, PIC_OY)
        val saved = pen.cv
        pen.cv = rc
        pen.alpha = 1f; pen.flash = false; pen.ow = 2.4f
        pen.setAntiAlias(true)
        bases.body(era, team)
        pic.endRecording()
        pen.cv = saved
        pen.setAntiAlias(g.quality.antialias)
        basePics[idx] = pic
        return pic
    }

    private fun drawBase(c: Canvas, s: Side) {
        val bx = sx(s.baseFront)
        if (s.index == 0 && bx < -400 * k) return
        if (s.index == 1 && bx > viewW + 400 * k) return
        val team = if (s.index == 0) Ui.PLAYER else Ui.ENEMY
        val e = s.era
        val pic = basePicture(s.index, e, team)
        c.save()
        c.translate(bx, groundY)
        c.scale(k * s.dir, k)
        pen.alpha = 1f; pen.flash = false; pen.ow = 2.4f
        if (g.quality.shadows) {
            fill.color = Color.argb(55, 0, 0, 0)
            rect.set(-345f, -10f, 10f, 12f); c.drawOval(rect, fill)
        }
        c.save(); c.translate(-PIC_OX, -PIC_OY)
        if (s.baseFlash > 0f) {
            c.saveLayer(0f, 0f, PIC_OX + 40f, PIC_OY + 40f, null)
            c.drawPicture(pic)
            c.drawColor(Color.argb((s.baseFlash / 0.15f * 110).toInt().coerceIn(0, 255), 255, 255, 255), PorterDuff.Mode.SRC_ATOP)
            c.restore()
        } else c.drawPicture(pic)
        c.restore()
        bases.live(e, team)
        if (e == 4 && g.quality.level >= 2 && (time * 10f).toInt() % 6 == 0) spawnParticle(s.baseFront - s.dir * 260f, 420f, s.dir * 10f, 40f, 2.2f, 26f, 0x80585050.toInt(), 3)

        // flaga drużyny
        val fx0 = bases.flagX(e)
        val flagTop = bases.flagTop(e)
        val wave = sin(time * 5f + s.index) * 6f
        val wave2 = sin(time * 5f + s.index + 1.3f) * 5f
        val fp = pen.path
        fp.reset(); fp.moveTo(fx0, flagTop); fp.quadTo(fx0 + 34f, flagTop - 4f + wave2, fx0 + 70f, flagTop + 4f + wave)
        fp.lineTo(fx0 + 70f, flagTop + 40f + wave); fp.quadTo(fx0 + 34f, flagTop + 32f + wave2, fx0, flagTop + 40f); fp.close()
        pen.shape(team)
        if (g.quality.level >= 2) {
            pen.circle(fx0 + 34f, flagTop + 18f + (wave + wave2) * 0.4f, 8f, 0xFFF4F0E4.toInt())
            pen.circleF(fx0 + 34f, flagTop + 18f + (wave + wave2) * 0.4f, 3.5f, team)
        }
        pen.circle(fx0, flagTop - 3f, 5f, 0xFFE8C04A.toInt())

        // wieża z platformami na wieżyczki
        if (s.slots > 0) {
            val top = b.turretHeight(s.slots - 1) + 20f
            bases.tower(e, team, top)
            for (i in 0 until s.slots) {
                val hh = b.turretHeight(i)
                bases.platform(e, -hh + 8f)
                val te = s.turretEra[i]
                if (te >= 0) bases.turret(te, -40f, -hh + 8f, s.turretFire[i] > 0f, team)
            }
        }
        c.restore()
    }

    // ---------------------------------------------------------------- jednostki

    private fun drawUnit(c: Canvas, u: Fighter) {
        val x = sx(u.x)
        val margin = 130f * k * u.def.size
        if (x < -margin || x > viewW + margin) return
        if (b.fog && u.side == 1 && u.x - b.player.front > 650f) return
        val s = b.sides[u.side]
        alpha = if (u.dying >= 0f) max(0f, 1f - u.dying / 0.8f) else 1f
        val def = u.def
        if (g.quality.shadows) {
            fill.color = col(Color.argb(70, 0, 0, 0))
            val sw = shadowW(def) * k * def.size
            rect.set(x - sw, groundY - 3.5f * k, x + sw, groundY + 3.5f * k)
            c.drawOval(rect, fill)
        }
        c.save()
        c.translate(x, groundY)
        val sc = k * def.size
        c.scale(sc * s.dir, sc)
        if (u.dying >= 0f) c.rotate(-min(80f, u.dying * 200f))
        val team = if (u.side == 0) Ui.PLAYER else Ui.ENEMY
        val walk = if (u.moving) u.anim else 0f
        val atk = if (u.attackAnim > 0f) u.attackAnim / 0.25f else 0f
        pen.alpha = alpha
        pen.flash = u.flash > 0f
        drawBody(def, team, walk, atk)
        pen.flash = false
        c.restore()
        // pasek HP, gdy ranny
        if (u.dying < 0f && u.hp < u.maxHp) {
            val bw = 36f * k * def.size
            val top = groundY - unitHeight(def) * k * def.size - 12f * k
            val bh = 4.5f * k
            fill.color = Color.argb(200, 20, 18, 24)
            c.drawRect(x - bw / 2 - k, top - k, x + bw / 2 + k, top + bh + k, fill)
            fill.color = team
            c.drawRect(x - bw / 2, top, x - bw / 2 + bw * (u.hp / u.maxHp), top + bh, fill)
            fill.color = Color.argb(90, 255, 255, 255)
            c.drawRect(x - bw / 2, top, x - bw / 2 + bw * (u.hp / u.maxHp), top + bh * 0.4f, fill)
        }
    }

    private fun drawBody(def: UnitDef, team: Int, walk: Float, atk: Float) {
        pen.ow = 1.5f / def.size
        when (def.body) {
            Body.HUMAN -> men.draw(def.era, def.role == Role.RANGED, def.weapon, team, walk, atk, false, def.role == Role.HEAVY)
            Body.BEAST -> mach.beast(def.era, def.role == Role.ELITE, team, walk, atk)
            Body.SIEGE -> mach.trebuchet(team, walk, atk)
            Body.CANNON -> mach.cannon(team, walk, atk)
            Body.VEHICLE -> mach.vehicle(def.era, def.role == Role.ELITE, team, walk, atk)
            Body.MECH -> mach.mech(def.era, def.role == Role.ELITE, team, walk, atk)
            Body.HOVER -> mach.hover(team, atk)
        }
    }

    private fun shadowW(d: UnitDef) = when (d.body) {
        Body.HUMAN -> 13f; Body.BEAST -> if (d.role == Role.ELITE) 40f else 32f
        Body.VEHICLE -> 50f; Body.HOVER -> 0f; Body.MECH -> 22f; else -> 38f
    }

    /** Podgląd jednostki na przycisku HUD (ten sam kod rysujący co na polu bitwy). */
    fun drawUnitIcon(c: Canvas, def: UnitDef, cx: Float, bottom: Float, height: Float, team: Int) {
        val sc = height / (max(unitHeight(def), unitWidth(def) * 0.82f) * def.size + 8f)
        val save = alpha
        alpha = 1f
        val savedCv = if (pen.cvReady()) pen.cv else null
        pen.cv = c
        pen.alpha = 1f; pen.flash = false
        pen.detail = g.quality.level
        pen.setAntiAlias(true)
        c.save()
        c.translate(cx - iconShift(def) * sc * def.size, bottom)
        c.scale(sc * def.size, sc * def.size)
        drawBody(def, team, 0f, 0f)
        c.restore()
        pen.setAntiAlias(g.quality.antialias)
        if (savedCv != null) pen.cv = savedCv
        alpha = save
    }

    private fun unitWidth(d: UnitDef) = when (d.body) {
        Body.HUMAN -> 50f; Body.BEAST -> if (d.role == Role.ELITE) 110f else 80f
        Body.VEHICLE -> if (d.role == Role.ELITE && d.era == 5) 130f else 104f; Body.HOVER -> 96f
        Body.MECH -> if (d.era == 7) 100f else 74f; Body.SIEGE -> 100f; else -> 92f
    }

    private fun iconShift(d: UnitDef) = when (d.body) {
        Body.HUMAN -> 4f; Body.VEHICLE -> 6f; Body.SIEGE -> 4f; Body.CANNON -> 0f; else -> 8f
    }

    private fun unitHeight(d: UnitDef) = when (d.body) {
        Body.HUMAN -> 68f; Body.BEAST -> if (d.role == Role.ELITE) (if (d.era == 1) 120f else 92f) else 80f
        Body.MECH -> if (d.era == 7) 120f else 96f; Body.VEHICLE -> 66f; Body.HOVER -> 54f; Body.SIEGE -> 92f; else -> 46f
    }

    // ---------------------------------------------------------------- pociski

    private fun drawProj(c: Canvas, p: Projectile) {
        val x = sx(p.x)
        if (x < -60f || x > viewW + 60f) return
        val y = sy(p.h)
        val ang = atan2(-p.vh, p.vx)
        c.save()
        c.translate(x, y)
        c.rotate(ang * 57.29578f)
        c.scale(k, k)
        pen.alpha = 1f; pen.flash = false; pen.ow = 1.2f
        when (p.type) {
            Proj.STONE -> pen.circle(0f, 0f, 4f, 0xFF8E8A84.toInt())
            Proj.BOULDER -> { pen.circle(0f, 0f, 10f, 0xFF7E7A74.toInt()); if (pen.hi) pen.circleF(-3f, -3f, 3.5f, 0xFFA8A49C.toInt()) }
            Proj.JAVELIN, Proj.ARROW -> {
                val jav = p.type == Proj.JAVELIN
                pen.seg(-18f, 0f, 8f, 0f, if (jav) 2.2f else 1.6f, 0xFF7A5232.toInt())
                pen.tri(8f, -2.6f, 15f, 0f, 8f, 2.6f); pen.shape(if (jav) 0xFFC8923A.toInt() else 0xFFB4BCC6.toInt())
                if (!jav) { pen.tri(-18f, 0f, -13f, -4f, -11f, 0f); pen.shapeF(0xFFE8E4DA.toInt()); pen.tri(-18f, 0f, -13f, 4f, -11f, 0f); pen.shapeF(0xFFD0302A.toInt()) }
            }
            Proj.BOLT -> { pen.seg(-11f, 0f, 5f, 0f, 2.4f, 0xFF4A3420.toInt()); pen.tri(5f, -3f, 11f, 0f, 5f, 3f); pen.shape(0xFFB4BCC6.toInt()) }
            Proj.BALL -> { pen.circle(0f, 0f, 6f, 0xFF26262A.toInt()); if (pen.hi) pen.circleF(-2f, -2f, 1.8f, 0xFF6A6A70.toInt()) }
            Proj.BULLET -> { pen.glow(-8f, 0f, 3f, 0x60FFE070); c.drawLine(-16f, 0f, 0f, 0f, l(0xFFFFE070.toInt(), 2.4f)); pen.glow(0f, 0f, 1.8f, 0xFFFFFFE0.toInt()) }
            Proj.SHELL -> {
                c.drawLine(-20f, 0f, -6f, 0f, l(0x80FFFFFF.toInt(), 2.4f))
                pen.rr(-6f, -3.5f, 6f, 3.5f, 3.4f, 0xFF4A4A3A.toInt()); pen.rectF(-5f, -3f, -2f, 3f, 0xFFC8923A.toInt())
            }
            Proj.ROCKET -> {
                pen.glow(-18f, 0f, 6f, 0xC0FF9030.toInt()); pen.glow(-16f, 0f, 3f, 0xFFFFF0A0.toInt())
                pen.tri(-14f, -6f, -8f, 0f, -14f, 6f); pen.shape(0xFF5A6446.toInt())
                pen.rr(-14f, -3f, 8f, 3f, 2f, 0xFFD8D8D0.toInt())
                pen.tri(8f, -3f, 14f, 0f, 8f, 3f); pen.shape(0xFFD0302A.toInt())
            }
            Proj.PLASMA -> { pen.glow(0f, 0f, 11f, 0x5080FFFF); pen.glow(-6f, 0f, 7f, 0x4080FFFF); pen.glow(0f, 0f, 6f, 0xFFB0FFFF.toInt()); pen.glow(0f, 0f, 3f, 0xFFFFFFFF.toInt()) }
            Proj.BEAM -> { c.drawLine(-52f, 0f, 0f, 0f, l(0x6090F8FF, 8f)); c.drawLine(-50f, 0f, 0f, 0f, l(0xFFE0FFFF.toInt(), 2.6f)) }
        }
        c.restore()
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
