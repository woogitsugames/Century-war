package com.centurywar.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Picture
import android.graphics.PorterDuff
import android.graphics.Path
import android.graphics.RectF
import com.centurywar.engine.Game
import com.centurywar.engine.Icons
import com.centurywar.engine.L
import com.centurywar.engine.Palette
import com.centurywar.engine.Ui
import com.centurywar.game.art.Backdrops
import com.centurywar.game.art.Bases
import com.centurywar.game.art.Machines
import com.centurywar.game.art.Pen
import com.centurywar.game.art.Soldiers
import kotlin.math.abs
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
    /**
     * 0..1: ułamek kroku symulacji, który upłynął od ostatniego Battle.step(). Renderer rysuje
     * stan pośredni między krokami, więc ruch jest płynny także przy 90/120 Hz i ×3/×5.
     */
    var lerp = 1f
    var k = 1f
    var groundY = 0f
    var viewW = 0f
    private var trauma = 0f
    private var shakeX = 0f
    private var shakeY = 0f
    /** Wspólne przełączniki dla efektów obecnych i przyszłych animacji. */
    var shakeScale = 1f
        private set
    var fxIntensity = 1f
        private set
    private var nextVisualFlashAt = 0f
    private val lastBaseFlash = FloatArray(2)
    private val baseFlashUntil = FloatArray(2)

    /** Wspólny limit rozbłysków (< 3 Hz) także dla nowych efektów. */
    private fun allowFlash(): Boolean {
        if (!g.profile.reduceFlash) return true
        if (time < nextVisualFlashAt) return false
        nextVisualFlashAt = time + 0.34f
        return true
    }
    private var time = 0f
    // Faza wygaszania także przy zatrzymaniu do ataku (symulacja nie zmienia wtedy stride).
    private val visualStride = FloatArray(b.units.size)
    private val visualSerial = IntArray(b.units.size) { -1 }
    private var renderDt = 0f
    /** Przesunięcia świata, bez wpływu na HUD i sterowanie. */
    internal val shakeOffsetX get() = shakeX
    internal val shakeOffsetY get() = shakeY
    fun worldX(screenX: Float): Float = camX + (screenX - shakeX) / k


    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val path = Path()
    private val rect = RectF()
    private val aimRect = RectF()
    private val aimFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val aimLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    // Deszcz i pył: stałe współrzędne cząstek, bez alokacji i RNG podczas renderowania.
    private val rainX = FloatArray(128) { ((it * 73 + 31) % 127) / 127f }
    private val rainY = FloatArray(128) { ((it * 89 + 17) % 131) / 131f }
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
    private val ptype = IntArray(PMAX) // 0 odłamek, 1 pierścień, 2 błysk, 3 dym, 4 fala przy ziemi
    private val pprio = IntArray(PMAX)
    private var pcount = 0
    internal val activeParticleCount get() = pcount

    // Emisja po czasie, nie po klatkach. Indeksy są indeksami pul symulacji.
    private val puffNext = FloatArray(Battle.MAX_PROJ) { 0.08f }
    private val puffLastTime = FloatArray(Battle.MAX_PROJ)
    private val motorSmoke = FloatArray(Battle.MAX_UNITS)
    private val baseSmoke = FloatArray(2)

    // Ślady na ziemi nie zajmują puli cząsteczek.
    private val SCARS = 16
    private val scarX = FloatArray(SCARS)
    private val scarSize = FloatArray(SCARS)
    private val scarLife = FloatArray(SCARS)
    private var nextScar = 0

    // Wraki poza symulacją; slot śmierci rozpoznajemy po numerze serial jednostki.
    private val seenDeaths = IntArray(Battle.MAX_UNITS)
    private val WMAX = 12
    private val wx = FloatArray(WMAX)
    private val wside = IntArray(WMAX)
    private val wdef = IntArray(WMAX)
    private val wtier = IntArray(WMAX)
    private val wage = FloatArray(WMAX)
    private val wsmoke = FloatArray(WMAX)
    private var wcount = 0

    // pływające napisy
    private val TMAX = 24
    private val tx = FloatArray(TMAX)
    private val th = FloatArray(TMAX)
    private val tlife = FloatArray(TMAX)
    private val ttext = arrayOfNulls<String>(TMAX)
    private val tcol = IntArray(TMAX)
    private val tsize = FloatArray(TMAX)

    /** Kolory i symbole zdolności: tablice współdzielone, nie tworzone w klatce. */
    private val bossColors = intArrayOf(0, 0xFFE4A85B.toInt(), 0xFF60C7E6.toInt(),
        0xFF8BBF88.toInt(), 0xFFE2B96E.toInt(), 0xFFF0785D.toInt())
    private val bossIcons = intArrayOf(Icons.CROWN, Icons.ADVANCE, Icons.SPEED,
        Icons.SHIELD, Icons.HELMET, Icons.FLAME)

    /** Przyrosty złota bez boxingu Int ani budowania tekstów podczas efektów. */
    private val goldExact = Array(256) { "+$it" }
    private val goldBuckets = Array(401) { "+${it * 25}" }
    private val goldOverflow = "+10000+"
    private val rankLabels = Array(21) { "" }
    private var rankLanguage = -1

    // rysowanie szczegółowe (art/*): wspólny pisak + moduły
    private val pen = Pen()
    private val backdrops = Backdrops(g, b)
    private val men = Soldiers(pen)
    private val mach = Machines(pen, men)
    private var poseOverride = false
    private val bases = Bases(pen)
    /** Nagrane korpusy baz: indeks = strona * liczba epok + epoka. */
    private val basePics = arrayOfNulls<Picture>(EraArt.entries.size * 2)
    private var basePicDetail = -1
    private var basePicPalette = -1
    private val PIC_OX = 420f
    private val PIC_OY = 540f

    fun layout(w: Float, h: Float, topReserve: Float, bottomReserve: Float) {
        viewW = w
        groundY = h - bottomReserve
        val visibleH = groundY - topReserve
        // Na prawie kwadratowych ekranach (rozłożony Fold, tablet) widok jest węższy niż
        // na telefonie, ale wciąż pokazuje sporą część pola. Nadmiar wysokości oddajemy
        // ziemi (pas przedpola pod jednostkami) zamiast pustemu niebu.
        val aspect = w / h
        val worldW = if (aspect < 1.5f) 880f else 1100f
        k = min(visibleH / 360f, w / worldW).coerceAtLeast(0.1f)
        val excess = visibleH - 360f * k
        if (excess > 0f) groundY -= excess * 0.4f
        backdrops.invalidate()
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

    private fun sx(x: Float) = (x - camX) * k
    /** Projekcja świata na ekran poza kamerą świata (HUD, samouczek), z aktualnym wstrząsem. */
    fun screenX(worldX: Float): Float = sx(worldX) + shakeX
    private fun sy(h: Float) = groundY - h * k

    /** Zakres ostrzału i rzeczywisty rozrzut celu, przycięty jak w symulacji. */
    fun drawBarrageAim(c: Canvas, worldTargetX: Float, field: RectF) {
        val near = b.player.baseFront + 220f
        val far = b.player.baseFront + 1370f
        val left = sx(near)
        val right = sx(far)
        val center = sx(worldTargetX.coerceIn(near, far))
        val scatterL = sx((worldTargetX - 120f).coerceIn(near, far))
        val scatterR = sx((worldTargetX + 120f).coerceIn(near, far))
        val d = g.ui.dp
        val top = max(field.top, groundY - 48f * d)
        val bottom = min(field.bottom, groundY + 28f * d)
        if (bottom - top <= 12f * d) return

        c.save()
        c.clipRect(field)
        aimFill.color = Color.argb(48, 64, 140, 235)
        aimRect.set(left, top, right, bottom)
        c.drawRoundRect(aimRect, 7f * d, 7f * d, aimFill)
        aimFill.color = Color.argb(110, 64, 140, 235)
        aimRect.set(scatterL, top, scatterR, bottom)
        c.drawRect(aimRect, aimFill)

        aimLine.color = Color.argb(175, 64, 140, 235)
        aimLine.strokeWidth = 2f * d
        c.drawLine(left, top, left, bottom, aimLine)
        c.drawLine(right, top, right, bottom, aimLine)
        // Wrogowie w zasięgu rozrzutu: znacznik nad głową, żeby było widać, kogo trafi salwa.
        val tx = worldTargetX.coerceIn(near, far)
        for (u in b.units) {
            if (!u.alive || u.side != 1 || abs(u.x - tx) > 120f) continue
            if (b.fog && u.x - b.player.front > 650f) continue
            val ux = sx(u.x)
            val uy = groundY - unitHeight(u.def) * k * u.def.size - 14f * d
            aimFill.color = Ui.OUTLINE
            c.drawCircle(ux, uy, 6.5f * d, aimFill)
            aimFill.color = Color.rgb(255, 196, 64)
            c.drawCircle(ux, uy, 4.5f * d, aimFill)
        }
        val cy = (groundY - 12f * d).coerceIn(top + 6f * d, bottom - 6f * d)
        aimLine.color = Ui.OUTLINE
        aimLine.strokeWidth = 5f * d
        c.drawCircle(center, cy, 18f * d, aimLine)
        c.drawLine(center - 25f * d, cy, center + 25f * d, cy, aimLine)
        c.drawLine(center, cy - 25f * d, center, cy + 25f * d, aimLine)
        aimLine.color = Ui.PLAYER
        aimLine.strokeWidth = 2.5f * d
        c.drawCircle(center, cy, 18f * d, aimLine)
        c.drawLine(center - 25f * d, cy, center + 25f * d, cy, aimLine)
        c.drawLine(center, cy - 25f * d, center, cy + 25f * d, aimLine)
        c.restore()
    }

    private fun col(c: Int): Int =
        if (alpha >= 1f) c else Color.argb((Color.alpha(c) * alpha).toInt(), Color.red(c), Color.green(c), Color.blue(c))

    private fun f(c: Int): Paint { fill.color = col(c); return fill }
    private fun l(c: Int, w: Float): Paint { line.color = col(c); line.strokeWidth = w; return line }

    // ---------------------------------------------------------------- główny rysunek

    fun render(c: Canvas, dt: Float, w: Float, h: Float) {
        time += dt.coerceAtLeast(0f)
        renderDt = dt.coerceIn(0f, 0.05f)
        val q = g.quality
        // Przy zmianie jakości istniejąca pula również musi spełniać nowy limit.
        if (pcount > q.maxParticles) pcount = q.maxParticles
        fill.isAntiAlias = q.antialias
        line.isAntiAlias = q.antialias
        pen.cv = c
        pen.setAntiAlias(q.antialias)
        pen.detail = q.level
        shakeScale = if (g.profile.reduceShake) 0f else 1f
        fxIntensity = if (g.profile.reduceFlash) 0.22f else 1f
        pen.flashMix = if (g.profile.reduceFlash) 0.12f else 0.78f
        pen.outlineScale = if (Palette.highContrast) 1.35f else 1f
        pen.alpha = 1f; pen.flash = false; pen.char = 0f; pen.tintAmt = 0f
        mach.time = time; bases.time = time

        detectDeaths()
        consumeFx()
        trauma = max(0f, trauma - dt.coerceAtLeast(0f) * 1.5f)
        val strength = if (g.profile.screenShake == 0) 0f
            else trauma * trauma * shakeScale * if (g.profile.screenShake == 1) 0.42f else 1f
        // Iloczyny sinusów zapewniają ciągłość i twardy limit 7/4 dp.
        shakeX = sin(time * 16.3f) * cos(time * 9.7f) * strength * 7f * g.ui.dp
        shakeY = sin(time * 13.1f + 1.4f) * cos(time * 7.7f) * strength * 4f * g.ui.dp

        backdrops.drawSky(c, w, h, groundY, time)
        c.save()
        c.translate(shakeX, shakeY)
        backdrops.drawLandscape(c, w, groundY, camX, k)
        backdrops.drawGround(c, w, h, groundY, camX, k)
        if (b.weather == Weather.NIGHT) drawNight(c, w)
        drawScars(c, dt)
        drawBarrageMarkers(c)
        emitAmbient(dt)
        drawBase(c, b.sides[0])
        drawBase(c, b.sides[1])
        drawWrecks(c, dt)
        var slot = 0
        while (slot < b.units.size) {
            val u = b.units[slot]
            if (u.active && u.side == 1) drawUnit(c, u, slot)
            slot++
        }
        slot = 0
        while (slot < b.units.size) {
            val u = b.units[slot]
            if (u.active && u.side == 0) drawUnit(c, u, slot)
            slot++
        }
        alpha = 1f
        drawWeather(c, w)
        for (i in b.projs.indices) {
            val p = b.projs[i]
            if (p.active && p.delay <= 0f) {
                drawProj(c, p)
                emitProjectilePuffs(i, p)
            }
        }
        updateParticles(c, dt)
        drawTexts(c, dt)
        if (b.fog) drawFog(c, w, h)
        c.restore()
    }

    // ---------------------------------------------------------------- bazy

    /** Korpus bazy nagrany raz do Picture (tekstury nie kosztują w kolejnych klatkach). */
    private fun basePicture(side: Int, art: EraArt, team: Int): Picture {
        if (basePicDetail != pen.detail || basePicPalette != Palette.selected) {
            for (i in basePics.indices) basePics[i] = null
            basePicDetail = pen.detail
            basePicPalette = Palette.selected
        }
        val idx = side * EraArt.entries.size + art.ordinal
        basePics[idx]?.let { return it }
        val pic = Picture()
        val rc = pic.beginRecording((PIC_OX + 40f).toInt(), (PIC_OY + 40f).toInt())
        rc.translate(PIC_OX, PIC_OY)
        val saved = pen.cv
        pen.cv = rc
        pen.alpha = 1f; pen.flash = false; pen.ow = 2.4f
        pen.setAntiAlias(true)
        bases.body(art, team)
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
        val art = Defs.sideArt(e, s.eraVariant)
        val pic = basePicture(s.index, art, team)
        c.save()
        c.translate(bx, groundY)
        c.scale(k * s.dir, k)
        pen.alpha = 1f; pen.flash = false; pen.ow = 2.4f
        if (g.quality.shadows) {
            fill.color = Color.argb(55, 0, 0, 0)
            rect.set(-345f, -10f, 10f, 12f); c.drawOval(rect, fill)
        }
        c.save(); c.translate(-PIC_OX, -PIC_OY)
        val flashAlpha = if (g.profile.reduceFlash) {
            // Pierwsza klatka kolejnego uderzenia; maks. 1 rozbłysk na 0,34 s.
            if (s.baseFlash > lastBaseFlash[s.index] + 0.005f && allowFlash()) {
                baseFlashUntil[s.index] = time + 0.15f
            }
            lastBaseFlash[s.index] = s.baseFlash
            if (time <= baseFlashUntil[s.index]) (s.baseFlash / 0.15f * 22f).toInt().coerceIn(0, 22) else 0
        } else (s.baseFlash / 0.15f * 110f).toInt().coerceIn(0, 255)
        if (flashAlpha > 0) {
            c.saveLayer(0f, 0f, PIC_OX + 40f, PIC_OY + 40f, null)
            c.drawPicture(pic)
            c.drawColor(Color.argb(flashAlpha, 255, 255, 255), PorterDuff.Mode.SRC_ATOP)
            c.restore()
        } else c.drawPicture(pic)
        c.restore()
        bases.live(art, team)

        // flaga drużyny
        val fx0 = bases.flagX(art)
        val flagTop = bases.flagTop(art)
        val wave = sin(time * 5f + s.index) * 6f
        val wave2 = sin(time * 5f + s.index + 1.3f) * 5f
        val flagDir = if (b.weather == Weather.WIND) b.windDirection() * s.dir else 1f
        val fp = pen.path
        fp.reset(); fp.moveTo(fx0, flagTop); fp.quadTo(fx0 + flagDir * 34f, flagTop - 4f + wave2, fx0 + flagDir * 70f, flagTop + 4f + wave)
        fp.lineTo(fx0 + flagDir * 70f, flagTop + 40f + wave); fp.quadTo(fx0 + flagDir * 34f, flagTop + 32f + wave2, fx0, flagTop + 40f); fp.close()
        pen.shape(team)
        if (g.quality.level >= 2) {
            pen.circle(fx0 + flagDir * 34f, flagTop + 18f + (wave + wave2) * 0.4f, 8f, 0xFFF4F0E4.toInt())
            pen.circleF(fx0 + flagDir * 34f, flagTop + 18f + (wave + wave2) * 0.4f, 3.5f, team)
        }
        pen.circle(fx0, flagTop - 3f, 5f, 0xFFE8C04A.toInt())

        // wieża z platformami na wieżyczki
        if (s.slots > 0) {
            val top = b.turretHeight(s.slots - 1) + 20f
            bases.tower(art, team, top)
            for (i in 0 until s.slots) {
                val hh = b.turretHeight(i)
                bases.platform(art, -hh + 8f)
                val te = s.turretEra[i]
                if (te >= 0) bases.turret(Defs.sideArt(te, s.turretVariant[i]), -40f, -hh + 8f, s.turretFire[i] > 0f, team)
            }
        }
        c.restore()
    }

    // ---------------------------------------------------------------- jednostki

    /** Rozpoznawanie śmierci po serial, bez zmian w buforze zdarzeń i zapisie bitwy. */
    private fun detectDeaths() {
        for (i in b.units.indices) {
            val u = b.units[i]
            if (!u.active || u.dying < 0f || seenDeaths[i] == u.serial) continue
            seenDeaths[i] = u.serial
            val d = u.def
            val low = g.quality.level == 1
            if (motorBody(d.body)) {
                addWreck(u)
                spawnParticle(u.x, 32f * d.size, 0f, 0f, 0.13f, 25f * d.size,
                    0xFFFFCC78.toInt(), 2)
                val n = if (low) 2 else 6
                for (j in 0 until n) {
                    spawnParticle(u.x, 30f * d.size, rnd(-170f, 170f), rnd(55f, 200f),
                        0.5f, 3.5f * d.size,
                        if (j % 2 == 0) 0xFFFFB050.toInt() else 0xFF666E76.toInt(), 0)
                }
            } else {
                val n = if (low) 2 else 5
                for (j in 0 until n) {
                    spawnParticle(u.x + rnd(-10f, 10f) * d.size, 2f,
                        rnd(-45f, 45f), 0f, 0.5f + rnd(0f, 0.25f),
                        (7f + rnd(0f, 7f)) * d.size, 0xA8A38E70.toInt(), 4)
                }
                if (d.body == Body.SIEGE || d.body == Body.CANNON) {
                    for (j in 0 until n) {
                        spawnParticle(u.x, 35f * d.size, rnd(-125f, 125f), rnd(50f, 200f),
                            0.6f, 4f * d.size,
                            if (d.body == Body.SIEGE) 0xFF865333.toInt() else 0xFF777C80.toInt(), 0)
                    }
                }
            }
        }
    }

    private fun addWreck(u: Fighter) {
        var slot = wcount
        if (wcount < WMAX) wcount++
        else {
            slot = 0
            for (i in 1 until WMAX) if (wage[i] > wage[slot]) slot = i
        }
        wx[slot] = u.x
        wside[slot] = u.side
        wdef[slot] = u.def.id
        wtier[slot] = u.tier
        wage[slot] = u.dying
        wsmoke[slot] = 0.12f
    }

    /** Wraki pozostają krócej na LOW. Nie ma ciał ani dodatkowych jednostek w symulacji. */
    private fun drawWrecks(c: Canvas, dt: Float) {
        val low = g.quality.level == 1
        val life = if (low) 1f else 2.5f
        val tail = if (low) 0.25f else 0.45f
        var i = 0
        while (i < wcount) {
            wage[i] += dt
            if (wage[i] >= life) {
                val last = --wcount
                wx[i] = wx[last]; wside[i] = wside[last]; wdef[i] = wdef[last]
                wtier[i] = wtier[last]; wage[i] = wage[last]; wsmoke[i] = wsmoke[last]
                continue
            }
            wsmoke[i] -= dt
            if (wsmoke[i] <= 0f) {
                wsmoke[i] += if (low) 0.48f else 0.24f
                spawnParticle(wx[i] + rnd(-12f, 12f), 18f,
                    rnd(-15f, 15f), rnd(35f, 75f), 0.75f, 10f,
                    0xA04D4B48.toInt(), 3)
            }
            val def = Defs.allUnits[wdef[i]]
            val x = sx(wx[i])
            val margin = 130f * k * def.size
            // Podczas pierwszych 0,8 s sylwetkę rysuje jeszcze żywy Fighter.
            if (wage[i] >= 0.8f && x >= -margin && x <= viewW + margin) {
                c.save()
                c.translate(x, groundY)
                c.scale(k * def.size * b.sides[wside[i]].dir, k * def.size)
                c.translate(0f, 13f)
                c.scale(1f, 0.72f)
                pen.alpha = ((life - wage[i]) / tail).coerceIn(0f, 1f)
                pen.flash = false; pen.tintAmt = 0f; pen.char = 1f
                men.stride = 0f; men.windup = -1f
                mach.crashed = 1f
                val team = if (wside[i] == 0) Ui.PLAYER else Ui.ENEMY
                drawBody(def, team, 0f, 0f, wtier[i])
                pen.alpha = 1f; pen.char = 0f
                mach.crashed = 0f
                c.restore()
            }
            i++
        }
    }

    private fun mix(a: Float, b: Float) = a + (b - a) * lerp

    private fun deathFade(dying: Float): Float =
        if (dying < 0f || dying <= 0.55f) 1f else ((0.8f - dying) / 0.25f).coerceIn(0f, 1f)

    private fun motorBody(body: Int) = body == Body.VEHICLE || body == Body.MECH || body == Body.HOVER

    private fun drawUnit(c: Canvas, u: Fighter, slot: Int) {
        val fresh = u.prevX.isNaN()
        val x = sx(if (fresh) u.x else mix(u.prevX, u.x))
        val margin = 130f * k * u.def.size
        if (x < -margin || x > viewW + margin) return
        if (b.fog && u.side == 1 && u.x - b.player.front > 650f) return
        val s = b.sides[u.side]
        val def = u.def
        val dying = if (u.dying >= 0f) u.dying + lerp * Battle.STEP else -1f
        alpha = deathFade(dying)
        val bossScale = if (u.isBoss) 1.25f else 1f
        if (u.isBoss) {
            val ring = bossColors[u.bossKind]
            // Zasięg aury pokazany cienkim pasem; pierścień pod stopami widoczny w marszu.
            if (u.hasBossAbility(EndlessBosses.AURA)) {
                fill.color = col(0x4460C7E6)
                c.drawRect(sx(u.x - EndlessBosses.AURA_RANGE), groundY - 7f * k,
                    sx(u.x + EndlessBosses.AURA_RANGE), groundY + 4f * k, fill)
            }
            fill.color = col(Ui.OUTLINE)
            rect.set(x - 38f * k * def.size, groundY - 11f * k,
                x + 38f * k * def.size, groundY + 8f * k)
            c.drawOval(rect, fill)
            fill.color = col(ring)
            rect.inset(3f * k, 3f * k)
            c.drawOval(rect, fill)
            if (u.bossSecondaryKind != EndlessBosses.NONE) {
                line.color = col(bossColors[u.bossSecondaryKind]); line.strokeWidth = 2.5f * k
                rect.inset(-7f * k, -3f * k)
                c.drawOval(rect, line)
            }
        }
        if (g.quality.shadows) {
            fill.color = col(Color.argb(70, 0, 0, 0))
            val sw = shadowW(def) * k * def.size * bossScale
            rect.set(x - sw, groundY - 3.5f * k, x + sw, groundY + 3.5f * k)
            c.drawOval(rect, fill)
        }
        c.save()
        c.translate(x, groundY)
        val sc = k * def.size * bossScale
        c.scale(sc * s.dir, sc)
        val team = if (u.side == 0) Ui.PLAYER else Ui.ENEMY
        if (visualSerial[slot] != u.serial) {
            visualSerial[slot] = u.serial
            visualStride[slot] = 0f
        }
        val prev = visualStride[slot]
        val next = if (u.moving) min(1f, prev + renderDt * Battle.STRIDE_RATE)
                   else max(0f, prev - renderDt * Battle.STRIDE_RATE)
        visualStride[slot] = next
        val simStride = if (fresh) u.stride else mix(u.prevStride, u.stride)
        val stride = min(simStride, next)
        // Faza toczenia zostaje zachowana po zatrzymaniu; krok wygasza tylko stride.
        val walk = if (fresh) u.anim else mix(u.prevAnim, u.anim)
        val attack = if (fresh) u.attackAnim else mix(u.prevAttack, u.attackAnim)
        val atk = if (attack > 0f) attack / 0.25f else 0f
        men.stride = if (dying >= 0f) 0f else stride
        mach.stride = men.stride
        // Stabilna indywidualna faza dla efektów, niezależna od czasu i kolejności renderowania.
        mach.phase = ((u.serial * 73) % 360) * 0.017453293f
        // Przygotowanie ataku w trakcie odnowienia (tylko w walce: stoi i ma odnowienie).
        val cool = def.cooldown
        men.windup = if (dying < 0f && !u.moving && u.cd > 0f && cool > 0.3f)
            ((cool - u.cd - 0.25f) / (cool - 0.25f)).coerceIn(0f, 1f) else -1f
        mach.windup = men.windup
        val hitAge = if (u.hitStep >= 0) (b.stepCount - u.hitStep + lerp) * Battle.STEP else -1f
        drawAnimatedBody(c, def, team, walk, atk, u.tier, hitAge, u.hitPow, dying,
            u.flash > 0f && allowFlash(), u.burnLeft > 0f)
        c.restore()
        alpha = 1f
        if (u.dying < 0f && g.profile.teamMarkers) {
            val top = groundY - unitHeight(def) * k * def.size * bossScale - 27f * k
            drawTeamMarker(c, x, top, u.side, 12f * k)
        }
        if (u.isBoss && u.dying < 0f) {
            val top = groundY - unitHeight(def) * k * def.size * bossScale - 35f * k
            val second = u.bossSecondaryKind
            val shift = if (second != EndlessBosses.NONE) 13f * k else 0f
            Icons.draw(c, bossIcons[u.bossKind], x - shift, top, 22f * k,
                bossColors[u.bossKind], dp = g.ui.dp)
            if (second != EndlessBosses.NONE) Icons.draw(c, bossIcons[second], x + shift, top,
                22f * k, bossColors[second], dp = g.ui.dp)
        }
        // Pasek HP tylko żywej jednostki; bossa pokazujemy zawsze.
        if (u.dying < 0f && (u.hp < u.maxHp || u.isBoss)) {
            val bw = 36f * k * def.size * bossScale
            val top = groundY - unitHeight(def) * k * def.size * bossScale - 12f * k
            val bh = 4.5f * k
            fill.color = Color.argb(200, 20, 18, 24)
            c.drawRect(x - bw / 2 - k, top - k, x + bw / 2 + k, top + bh + k, fill)
            fill.color = team
            c.drawRect(x - bw / 2, top, x - bw / 2 + bw * (u.hp / u.maxHp), top + bh, fill)
            fill.color = Color.argb(90, 255, 255, 255)
            c.drawRect(x - bw / 2, top, x - bw / 2 + bw * (u.hp / u.maxHp), top + bh * 0.4f, fill)
            if (u.side == 1 && g.profile.teamMarkers) {
                val end = x - bw / 2 + bw * (u.hp / u.maxHp)
                var xx = x - bw / 2 + 4f * k
                while (xx < end) {
                    c.drawLine(xx, top, xx, top + bh, l(Ui.OUTLINE, max(1f * g.ui.dp, k)))
                    xx += 7f * k
                }
            }
        }
    }

    /** Jedno źródło póz dla bitwy, ikon i galerii; nie modyfikuje stanu symulacji. */
    private fun drawAnimatedBody(
        c: Canvas, def: UnitDef, team: Int, walk: Float, atk: Float, tier: Int,
        hitAge: Float, hitPow: Float, dying: Float, directFlash: Boolean, burning: Boolean,
    ) {
        val dead = dying >= 0f
        val t = if (dead) (dying / 0.62f).coerceIn(0f, 1f) else 0f
        val remaining = 1f - t
        val eased = 1f - remaining * remaining * remaining
        val phase = if (!dead && hitAge >= 0f && hitAge < 0.18f) 1f - hitAge / 0.18f else 0f
        val recoil = phase * phase * hitPow.coerceIn(0f, 1f)
        val motor = motorBody(def.body)
        pen.alpha = if (dead) deathFade(dying) else 1f
        pen.flash = if (dead) motor && dying < 0.085f else directFlash && phase > 0f
        pen.char = if (dead && motor) (dying / 0.36f).coerceIn(0f, 1f) else 0f
        pen.tintAmt = if (burning && !dead && !pen.flash)
            0.13f + (0.5f + 0.5f * sin(time * 13f)) * 0.12f else 0f
        men.recoil = if (def.body == Body.HUMAN) recoil else 0f
        men.collapse = if (dead && def.body == Body.HUMAN) eased else 0f
        mach.recoil = if (def.body == Body.BEAST) (if (dead) eased else recoil) else 0f
        mach.crashed = if (dead && def.body == Body.HOVER) eased else 0f

        c.save()
        if (dead) {
            when (def.body) {
                Body.HUMAN -> {
                    // Łuk upadku: szybki początek, wyhamowanie i drobne odbicie.
                    val bounce = if (t > 0.6f) sin((t - 0.6f) * 7.853981f) * 3f else 0f
                    c.rotate(-88f * eased - bounce)
                }
                Body.BEAST -> {
                    c.translate(-6f * eased, 19f * eased)
                    c.rotate(-21f * eased)
                }
                Body.SIEGE, Body.CANNON -> {
                    c.translate(-5f * eased, 13f * eased)
                    c.rotate(-24f * eased)
                }
                Body.VEHICLE -> {
                    c.translate(-3f * eased, 10f * eased)
                    c.scale(1f, 1f - 0.12f * eased)
                }
                Body.MECH -> {
                    c.translate(-6f * eased, 14f * eased)
                    c.scale(1f, 1f - 0.26f * eased)
                }
                Body.HOVER -> c.translate(0f, 27f * eased)
            }
        } else if (recoil > 0f) {
            if (motor) c.translate(-2.5f * recoil, 1.2f * recoil)
            else {
                c.translate(-2.5f * recoil, 0f)
                c.rotate(-4f * recoil)
            }
        }
        drawBody(def, team, if (dead) 0f else walk, if (dead) 0f else atk, tier)
        c.restore()

        pen.alpha = 1f; pen.flash = false; pen.char = 0f; pen.tintAmt = 0f
        men.recoil = 0f; men.collapse = 0f; mach.recoil = 0f; mach.crashed = 0f
    }

    /** Trójkątny proporzec gracza i wcięty szewron wroga, niezależne od barwy. */
    fun drawTeamMarker(c: Canvas, x: Float, y: Float, side: Int, size: Float) {
        if (!g.profile.teamMarkers) return
        val half = size * 0.5f
        path.reset()
        if (side == 0) {
            path.moveTo(x, y - half)
            path.lineTo(x + half, y + half)
            path.lineTo(x - half, y + half)
        } else {
            path.moveTo(x - half, y - half)
            path.lineTo(x, y - half * 0.2f)
            path.lineTo(x + half, y - half)
            path.lineTo(x + half, y + half)
            path.lineTo(x, y + half * 0.3f)
            path.lineTo(x - half, y + half)
        }
        path.close()
        c.drawPath(path, f(if (side == 0) Ui.PLAYER else Ui.ENEMY))
        c.drawPath(path, l(Ui.OUTLINE, max(size * 0.15f, 1.5f * g.ui.dp)))
    }

    private fun drawBody(def: UnitDef, team: Int, walk: Float, atk: Float, tier: Int = 1) {
        pen.ow = 1.5f / def.size
        mach.size = def.size
        if (def.variant >= 0 && (def.role == Role.HEAVY || def.role == Role.ELITE)) {
            mach.variantMachine(def.art, def.role == Role.ELITE, team, walk, atk)
        } else when (def.body) {
            Body.HUMAN -> men.draw(def.art, def.role == Role.RANGED, def.weapon, team, walk, atk, false, def.role == Role.HEAVY)
            Body.BEAST -> mach.beast(def.era, def.role == Role.ELITE, team, walk, atk)
            Body.SIEGE -> if (Defs.spec(def.era).art == EraArt.IRON) mach.scorpion(team, walk, atk) else mach.trebuchet(team, walk, atk)
            Body.CANNON -> mach.cannon(team, walk, atk)
            Body.VEHICLE -> mach.vehicle(def.era, def.role == Role.ELITE, team, walk, atk)
            Body.MECH -> mach.mech(def.era, def.role == Role.ELITE, team, walk, atk)
            Body.HOVER -> mach.hover(team, atk)
        }
        if (tier > 1) {
            if (def.body == Body.HUMAN) men.tierMarks(tier, team)
            else mach.tierMarks(def.body, tier, team)
        }
    }

    private fun shadowW(d: UnitDef) = when (d.body) {
        Body.HUMAN -> 13f; Body.BEAST -> if (d.role == Role.ELITE) 40f else 32f
        Body.VEHICLE -> 50f; Body.HOVER -> 0f; Body.MECH -> 22f; else -> 38f
    }

    /** Podgląd jednostki na przycisku HUD (ten sam kod rysujący co na polu bitwy). */
    /** Poza w dowolnej fazie chodu i ataku (galeria animacji w testach). */
    fun drawUnitPose(
        c: Canvas, def: UnitDef, cx: Float, bottom: Float, height: Float, team: Int,
        walk: Float, stride: Float, atk: Float, windup: Float,
        hitAge: Float = -1f, dying: Float = -1f,
    ) {
        men.stride = if (dying >= 0f) 0f else stride
        men.windup = if (dying >= 0f) -1f else windup
        mach.stride = men.stride; mach.windup = men.windup
        mach.phase = (def.era * 41 + def.role.ordinal * 67) * 0.017453293f
        poseOverride = true
        try {
            drawUnitIcon(c, def, cx, bottom, height, team, 1, walk, atk, hitAge, dying)
        } finally {
            poseOverride = false
            men.stride = 1f; men.windup = -1f
            mach.stride = 1f; mach.windup = -1f
        }
    }

    fun drawUnitIcon(c: Canvas, def: UnitDef, cx: Float, bottom: Float, height: Float, team: Int, tier: Int = 1,
                     walk: Float = 0f, atk: Float = 0f, hitAge: Float = -1f, dying: Float = -1f,
                     maxWidth: Float = Float.POSITIVE_INFINITY) {
        if (!poseOverride) {
            val intensity = if (walk == 0f) 0f else 1f
            men.stride = intensity; men.windup = -1f
            mach.stride = intensity; mach.windup = -1f
            mach.phase = (def.era * 41 + def.role.ordinal * 67) * 0.017453293f
        }
        // Szerokie maszyny muszą mieścić się także w szerokości podglądu.
        val sc = min(height / (max(unitHeight(def), unitWidth(def) * 0.82f) * def.size + 8f),
            maxWidth / (unitWidth(def) * def.size + 8f))
        val save = alpha
        alpha = 1f
        val savedCv = if (pen.cvReady()) pen.cv else null
        pen.cv = c
        pen.alpha = 1f; pen.flash = false; pen.char = 0f; pen.tintAmt = 0f
        pen.detail = g.quality.level
        pen.outlineScale = if (Palette.highContrast) 1.35f else 1f
        pen.flashMix = if (g.profile.reduceFlash) 0.12f else 0.78f
        pen.setAntiAlias(true)
        c.save()
        c.translate(cx - iconShift(def) * sc * def.size, bottom)
        c.scale(sc * def.size, sc * def.size)
        drawAnimatedBody(c, def, team, walk, atk, tier, hitAge, 1f, dying,
            hitAge >= 0f && hitAge < 0.12f, false)
        c.restore()
        pen.setAntiAlias(g.quality.antialias)
        if (savedCv != null) pen.cv = savedCv
        alpha = save
    }

    private fun unitWidth(d: UnitDef) = when (d.body) {
        Body.HUMAN -> 50f; Body.BEAST -> if (d.role == Role.ELITE) 110f else 80f
        Body.VEHICLE -> if (d.role == Role.ELITE && d.art == EraArt.STEEL) 130f else 104f; Body.HOVER -> 96f
        Body.MECH -> if (d.art == EraArt.ORBITAL) 100f else 74f; Body.SIEGE -> 100f; else -> 92f
    }

    private fun iconShift(d: UnitDef) = when (d.body) {
        Body.HUMAN -> 4f; Body.VEHICLE -> 6f; Body.SIEGE -> 4f; Body.CANNON -> 0f; else -> 8f
    }

    private fun unitHeight(d: UnitDef) = when (d.body) {
        Body.HUMAN -> 68f; Body.BEAST -> if (d.role == Role.ELITE) (if (d.art == EraArt.BRONZE) 120f else 92f) else 80f
        Body.MECH -> if (d.art == EraArt.ORBITAL) 120f else 96f; Body.VEHICLE -> 66f; Body.HOVER -> 54f; Body.SIEGE -> 92f; else -> 46f
    }

    // ---------------------------------------------------------------- pociski

    private fun drawProj(c: Canvas, p: Projectile) {
        val fresh = p.prevX.isNaN()
        val wx = if (fresh) p.x else mix(p.prevX, p.x)
        val wh = if (fresh) p.h else mix(p.prevH, p.h)
        val x = sx(wx)
        if (x < -120f || x > viewW + 120f) return
        val y = sy(wh)
        val flightT = if (fresh) p.t else max(0f, p.t - (1f - lerp) * Battle.STEP)
        drawTrail(c, p, wx, wh, flightT)
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


    /** Smuga odtwarzana analitycznie z prędkości i grawitacji, bez bufora historii. */
    private fun drawTrail(c: Canvas, p: Projectile, x: Float, h: Float, elapsed: Float) {
        if (elapsed <= 0f) return
        val span = when (p.type) {
            Proj.STONE, Proj.BOULDER, Proj.BALL -> 0.055f
            Proj.JAVELIN, Proj.ARROW, Proj.BOLT -> 0.085f
            Proj.BULLET -> 0.13f
            Proj.SHELL -> 0.22f
            Proj.ROCKET -> 0.29f
            Proj.PLASMA, Proj.BEAM -> 0.19f
            else -> 0f
        }.coerceAtMost(elapsed)
        if (span <= 0f) return
        val steps = if (g.quality.level == 1) 1 else if (g.quality.level == 2) 3 else 6
        val rgb = when (p.type) {
            Proj.BULLET -> 0xFFFFE070.toInt()
            Proj.PLASMA, Proj.BEAM -> 0xFF6FEFFF.toInt()
            Proj.ROCKET -> 0xFFFFBC67.toInt()
            Proj.JAVELIN, Proj.ARROW, Proj.BOLT -> 0xFFE8E4CA.toInt()
            else -> 0xFFC0C0B9.toInt()
        }
        val vh = p.vh + if (p.prevX.isNaN()) 0f else p.g * (1f - lerp) * Battle.STEP
        var ox = x
        var oh = h
        for (j in 1..steps) {
            val tau = span * j / steps
            val nx = x - p.vx * tau
            val nh = h - vh * tau - 0.5f * p.g * tau * tau
            val fade = (steps - j + 1f) / steps
            val a = ((if (g.quality.level == 1) 115f else 180f) * fade).toInt()
            val color = Color.argb(a, Color.red(rgb), Color.green(rgb), Color.blue(rgb))
            val width = k * (if (p.type == Proj.BEAM || p.type == Proj.PLASMA) 6f else if (p.type == Proj.ROCKET) 4.5f else 2.5f) * fade
            c.drawLine(sx(ox), sy(oh), sx(nx), sy(nh), l(color, width.coerceAtLeast(0.6f)))
            ox = nx; oh = nh
        }
    }

    private fun isBarrage(p: Projectile): Boolean =
        p.splash >= 50f && p.siegeMul <= 0.26f && p.dur >= 0.8f && p.dur <= 0.9f

    /** Cel zapowiedziany przed nalotem; nie wyświetlamy pełnego ostrzeżenia przez cały delay. */
    private fun drawBarrageMarkers(c: Canvas) {
        for (p in b.projs) {
            if (!p.active || !isBarrage(p)) continue
            if (p.delay > 0.3f) continue
            val progress = if (p.delay > 0f) (0.3f - p.delay) / 0.3f * 0.12f
                else (0.12f + 0.88f * (p.t / p.dur).coerceIn(0f, 1f))
            val x = sx(p.tx)
            if (x < -80f || x > viewW + 80f) continue
            val radius = (18f + progress * 23f) * k
            fill.color = Color.argb((25f + 110f * progress).toInt(), 30, 22, 17)
            rect.set(x - radius, groundY - radius * 0.2f, x + radius, groundY + radius * 0.2f)
            c.drawOval(rect, fill)
            line.color = Color.argb((45f + 165f * progress).toInt(),
                if (Defs.spec(p.era).art == EraArt.ORBITAL) 105 else 245,
                if (Defs.spec(p.era).art == EraArt.ORBITAL) 230 else 165,
                if (Defs.spec(p.era).art == EraArt.ORBITAL) 255 else 85)
            line.strokeWidth = (1.2f + progress * 2.2f) * k
            c.drawOval(rect, line)
            if (p.delay <= 0f && p.t >= p.dur * 0.55f) {
                val a = ((p.t / p.dur - 0.55f) * 300f).toInt().coerceIn(0, 135)
                val orbital = Defs.spec(p.era).art == EraArt.ORBITAL
                line.color = Color.argb(a, if (orbital) 130 else 255, if (orbital) 235 else 220, if (orbital) 255 else 170)
                line.strokeWidth = if (orbital) 8f * k else 3f * k
                val beamTop = if (orbital) 0f else max(0f, sy(p.h))
                c.drawLine(if (orbital) x else sx(p.x), beamTop, x, groundY - 8f * k, line)
            }
        }
    }

    /** Kłęby lecące za ciężkimi pociskami. Próbkowanie czasu lotu nie zależy od FPS. */
    private fun emitProjectilePuffs(i: Int, p: Projectile) {
        if (p.t < puffLastTime[i]) puffNext[i] = 0.08f
        puffLastTime[i] = p.t
        if (g.quality.level == 1) return
        val interval = when (p.type) {
            Proj.ROCKET -> 0.055f
            Proj.SHELL -> 0.09f
            Proj.STONE, Proj.BOULDER, Proj.BALL -> 0.13f
            else -> return
        } * if (g.quality.level == 2) 1.6f else 1f
        if (p.t - puffNext[i] > 0.35f) puffNext[i] = p.t - 0.15f
        var emitted = 0
        while (p.t >= puffNext[i] && emitted < 4) {
            val tau = p.t - puffNext[i]
            val x = p.x - p.vx * tau
            val h = p.h - p.vh * tau - 0.5f * p.g * tau * tau
            val sz = if (p.type == Proj.ROCKET) 12f else if (p.type == Proj.SHELL) 10f else 6f
            val shade = if (p.type == Proj.ROCKET) 0x806B655E.toInt() else 0x707E7D7B
            spawnParticle(x, h, rnd(-7f, 7f), rnd(2f, 22f), 0.42f, sz, shade, 3)
            puffNext[i] += interval
            emitted++
        }
    }

    /** Kominy i wydechy: akumulatory czasu (ten sam przepływ przy 30/60/120 FPS). */
    private fun emitAmbient(dt: Float) {
        val step = dt.coerceIn(0f, 0.12f)
        for (s in b.sides) {
            if (Defs.sideArt(s.era, s.eraVariant) != EraArt.STEAM) { baseSmoke[s.index] = 0f; continue }
            val interval = if (g.quality.level == 1) 0.7f else 0.22f
            baseSmoke[s.index] = min(1f, baseSmoke[s.index] + step)
            while (baseSmoke[s.index] >= interval) {
                baseSmoke[s.index] -= interval
                spawnParticle(s.baseFront - s.dir * 260f, 420f, s.dir * 10f + rnd(-10f, 10f),
                    32f + rnd(0f, 18f), 1.8f, 22f, 0x80716C67.toInt(), 3)
            }
        }
        for (i in b.units.indices) {
            val u = b.units[i]
            if (!u.active || u.dying >= 0f ||
                (u.def.body != Body.VEHICLE && u.def.body != Body.MECH)) {
                motorSmoke[i] = 0f; continue
            }
            val art = u.def.art
            if (art == EraArt.ORBITAL || art == EraArt.BRONZE) continue
            val interval = (if (u.moving) 0.12f else 0.28f) * if (g.quality.level == 1) 2.5f else 1f
            motorSmoke[i] = min(1f, motorSmoke[i] + step)
            val size = u.def.size
            while (motorSmoke[i] >= interval) {
                motorSmoke[i] -= interval
                if (sx(u.x) < -60f || sx(u.x) > viewW + 60f) continue
                val dir = b.sides[u.side].dir
                spawnParticle(u.x - dir * 32f * size,
                    (if (u.def.body == Body.MECH) 92f else 62f) * size,
                    -dir * (14f + if (u.moving) 20f else 0f), 20f + rnd(0f, 22f),
                    0.72f, (if (art == EraArt.STEAM) 14f else 9f) * size,
                    if (art == EraArt.STEAM) 0x807E7772.toInt() else 0x706E737C, 3)
            }
        }
    }

    private fun drawScars(c: Canvas, dt: Float) {
        if (g.quality.level == 1) return
        for (i in 0 until SCARS) {
            if (scarLife[i] <= 0f) continue
            scarLife[i] = max(0f, scarLife[i] - dt)
            val a = (75f * (scarLife[i] / 3f)).toInt().coerceIn(0, 75)
            val x = sx(scarX[i]); val r = scarSize[i] * k
            rect.set(x - r, groundY - r * 0.19f, x + r, groundY + r * 0.19f)
            fill.color = Color.argb(a, 25, 22, 22)
            c.drawOval(rect, fill)
        }
    }

    private fun addScar(x: Float, size: Float) {
        if (g.quality.level == 1) return
        scarX[nextScar] = x
        scarSize[nextScar] = size
        scarLife[nextScar] = 3f
        nextScar = (nextScar + 1) % SCARS
    }

    // ---------------------------------------------------------------- efekty

    /** Typ trafionego ciała z najbliższej jednostki ofiary; FxBuffer pozostaje bez zmian. */
    private fun hitBody(x: Float, side: Int): Int {
        var closest = Float.MAX_VALUE
        var body = Body.HUMAN
        for (u in b.units) {
            if (!u.active || u.side != side) continue
            val d = abs(u.x - x)
            if (d < closest) { closest = d; body = u.def.body }
        }
        return body
    }

    private fun emitMuzzle(x: Float, h: Float, dir: Float, type: Int) {
        val energy = type == Proj.PLASMA || type == Proj.BEAM
        spawnParticle(x, h, 0f, 0f, 0.11f,
            if (energy) 15f else 10f, if (energy) 0xFF8FEFFF.toInt() else 0xFFFFE890.toInt(), 2)
        if (energy) return
        val powder = type == Proj.BULLET || type == Proj.BALL
        val heavy = type == Proj.SHELL || type == Proj.ROCKET
        if (!powder && !heavy) return
        val n = if (g.quality.level == 1) 1 else if (heavy) 7 else 4
        for (j in 0 until n) {
            val v = if (heavy) rnd(45f, 100f) else rnd(12f, 45f)
            spawnParticle(x, h, -dir * v + rnd(-14f, 14f),
                rnd(16f, 65f), if (heavy) 0.85f else 0.62f,
                if (heavy) rnd(11f, 18f) else rnd(6f, 12f),
                if (powder) 0xB0DFD8CC.toInt() else 0xAA97938E.toInt(), 3)
        }
    }

    private fun emitExplosion(x: Float, h: Float, size: Float, side: Int) {
        val scale = size.coerceIn(0.25f, 3.5f)
        val es = b.sides[side.coerceIn(0, 1)]
        val art = Defs.sideArt(es.era, es.eraVariant)
        val ancient = art == EraArt.TRIBAL || art == EraArt.BRONZE ||
            art == EraArt.IRON || art == EraArt.FEUDAL
        val orbital = art == EraArt.ORBITAL
        val flash = when {
            orbital -> 0xFFC0FCFF.toInt()
            ancient -> 0xFFE4C9A0.toInt()
            else -> 0xFFFFF0AD.toInt()
        }
        val ring = when {
            orbital -> 0xFF6CD8FF.toInt()
            ancient -> 0xFFB69B79.toInt()
            else -> 0xFFFFA24B.toInt()
        }
        val cloud = when {
            orbital -> 0x807A70B4.toInt()
            ancient -> 0xB09A8B79.toInt()
            else -> 0xB05D5551.toInt()
        }
        spawnParticle(x, h, 0f, 0f, 0.26f, 38f * scale, flash, 2)
        spawnParticle(x, h + 9f, 0f, 0f, 0.42f, 55f * scale, ring, 1)
        spawnParticle(x, 2f, 0f, 0f, 0.47f, 62f * scale, ring, 5)
        val n = if (g.quality.level == 1) 4 else if (g.quality.level == 2) 8 else 12
        for (j in 0 until n) {
            val debris = if (ancient) 0xFF877766.toInt() else if (orbital) 0xFF8DE5FC.toInt()
                else if (j % 2 == 0) 0xFFFF904A.toInt() else 0xFF514B45.toInt()
            spawnParticle(x, h, rnd(-210f, 210f) * scale,
                rnd(100f, 305f) * scale, 0.48f + rnd(0f, 0.32f),
                (if (ancient) 5f else 4f) * scale, debris, 0)
        }
        val clouds = if (g.quality.level == 1) 2 else if (g.quality.level == 2) 5 else 9
        for (j in 0 until clouds) {
            spawnParticle(x + rnd(-20f, 20f) * scale, h + rnd(0f, 20f),
                rnd(-45f, 45f) * scale, rnd(30f, 105f) * scale,
                rnd(0.7f, 1.5f), rnd(8f, 19f) * scale, cloud, 3)
        }
        if (g.quality.level >= 2) for (j in 0 until 5) {
            spawnParticle(x + rnd(-20f, 20f) * scale, 3f,
                rnd(-90f, 90f) * scale, rnd(5f, 25f),
                0.55f, rnd(6f, 13f) * scale, 0xA0AAA096.toInt(), 3)
        }
        addScar(x, 22f * scale)
        trauma = min(1f, trauma + when {
            scale >= 2f -> 0.8f
            scale >= 1.15f -> 0.075f
            scale >= 0.9f -> 0.035f
            else -> 0f
        })
    }

    private fun consumeFx() {
        val fx = b.fx
        for (i in 0 until fx.count) {
            val x = fx.x[i]; val h = fx.h[i]
            when (fx.type[i]) {
                FxBuffer.HIT -> {
                    val body = hitBody(x, fx.side[i])
                    val away = -b.sides[fx.side[i]].dir
                    val metal = body == Body.CANNON || motorBody(body)
                    val wood = body == Body.SIEGE
                    val n = if (g.quality.level == 1) 2 else 5
                    for (j in 0 until n) {
                        val color = when {
                            metal && j % 2 == 0 -> 0xFFFFD375.toInt()
                            metal -> 0xFF737B83.toInt()
                            wood -> 0xFF95643C.toInt()
                            else -> 0xFFC2A585.toInt()
                        }
                        spawnParticle(x, h, away * rnd(35f, 145f) + rnd(-25f, 25f),
                            rnd(25f, 150f), if (metal) 0.3f else 0.4f,
                            if (metal) 2.8f else 3.8f, color, 0)
                    }
                }
                // Zgon obsługujemy przez serial w detectDeaths, z podziałem na typ ciała.
                FxBuffer.DEATH -> { }
                FxBuffer.EXPLOSION -> emitExplosion(x, h, fx.value[i], fx.side[i])
                FxBuffer.MUZZLE -> emitMuzzle(x, h, b.sides[fx.side[i]].dir, fx.value[i].toInt())
                FxBuffer.BASE_HIT -> for (j in 0 until 3) spawnParticle(x, h + rnd(0f, 120f), -b.sides[fx.side[i]].dir * rnd(20f, 140f), rnd(60f, 200f), 0.6f, 5f, 0xFF8A7A6A.toInt(), 0)
                FxBuffer.EVOLVE -> {
                    val sd = b.sides[fx.side[i]]
                    for (j in 0 until 24) {
                        val a = j / 24f * 6.283f
                        spawnParticle(x - sd.dir * 150f, 150f, cos(a) * 260f, sin(a) * 260f, 1.0f, 7f, 0xFFFFE070.toInt(), 2)
                    }
                    if (rankLanguage != L.lang) {
                        for (rank in rankLabels.indices) rankLabels[rank] = L.f("rank", rank)
                        rankLanguage = L.lang
                    }
                    val label = if (fx.value[i] > 0f) rankLabels[sd.rank.coerceIn(0, 20)] else L["next_era"]
                    addText(x - sd.dir * 150f, 360f, label, if (sd.index == 0) 0xFFFFE070.toInt() else Ui.ENEMY, 22f)
                }
                FxBuffer.GOLD -> {
                    val v = fx.value[i].toInt()
                    val s = when {
                        v <= 0 -> goldExact[0]
                        v < goldExact.size -> goldExact[v]
                        v > 10_000 -> goldOverflow
                        else -> goldBuckets[(v + 12) / 25]
                    }
                    addText(x, h, s, Ui.GOLD, 13f)
                }
                FxBuffer.BARRAGE -> {
                    val side = b.sides[fx.side[i]]
                    val atX = side.baseFront - side.dir * 165f
                    spawnParticle(atX, 210f, 0f, 0f, 0.28f, 40f, 0xD0FFE4A1.toInt(), 2)
                    spawnParticle(atX, 210f, 0f, 0f, 0.4f, 65f, 0xA0E6C070.toInt(), 1)
                    for (j in 0 until if (g.quality.level == 1) 3 else 9) {
                        spawnParticle(atX, 210f, side.dir * rnd(30f, 180f),
                            rnd(35f, 230f), 0.7f, 6f, 0xFFFFBF72.toInt(), 0)
                    }
                    trauma = min(1f, trauma + 0.32f)
                }
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
        if (type == 2 && !allowFlash()) return
        val limit = min(PMAX, g.quality.maxParticles)
        if (limit <= 0) return
        val priority = if (type == 3) 0 else if (type == 0) 2 else 3
        var i = pcount
        if (pcount < limit) pcount++
        else {
            // Gdy pula pełna, błysk/fala/odłamek zastępuje mniej istotny dym.
            var lowest = priority
            i = -1
            for (j in 0 until pcount) {
                if (pprio[j] >= lowest) continue
                lowest = pprio[j]
                i = j
                if (lowest == 0) break
            }
            if (i < 0) return
        }
        px[i] = x; ph[i] = h; pvx[i] = vx; pvh[i] = vh
        plife[i] = life; pmax[i] = life; psize[i] = size; pcol[i] = color; ptype[i] = type
        pprio[i] = priority
    }

    private fun updateParticles(c: Canvas, dt: Float) {
        var i = 0
        while (i < pcount) {
            plife[i] -= dt
            if (plife[i] <= 0f) {
                val last = --pcount
                px[i] = px[last]; ph[i] = ph[last]; pvx[i] = pvx[last]; pvh[i] = pvh[last]; plife[i] = plife[last]
                pmax[i] = pmax[last]; psize[i] = psize[last]; pcol[i] = pcol[last]; ptype[i] = ptype[last]; pprio[i] = pprio[last]
                continue
            }
            val t = plife[i] / pmax[i]
            px[i] += pvx[i] * dt
            ph[i] += pvh[i] * dt
            when (ptype[i]) {
                0 -> { pvh[i] -= 700f * dt; if (ph[i] < 0f) { ph[i] = 0f; pvh[i] *= -0.3f; pvx[i] *= 0.6f } }
                3 -> { pvx[i] *= 0.98f }
                4 -> { pvx[i] *= 0.94f }
            }
            val x = sx(px[i]); val y = sy(ph[i])
            val a = Color.alpha(pcol[i]) / 255f
            fill.color = Color.argb((255 * a * min(1f, t * 1.5f) * (if (ptype[i] == 2) fxIntensity else 1f)).toInt(), Color.red(pcol[i]), Color.green(pcol[i]), Color.blue(pcol[i]))
            when (ptype[i]) {
                0 -> c.drawRect(x - psize[i] * k / 2, y - psize[i] * k / 2, x + psize[i] * k / 2, y + psize[i] * k / 2, fill)
                1 -> { line.color = fill.color; line.strokeWidth = 6f * k * t; c.drawCircle(x, y, psize[i] * k * (1.4f - t), line) }
                2 -> c.drawCircle(x, y, psize[i] * k * (0.5f + t * 0.5f), fill)
                3 -> c.drawCircle(x, y, psize[i] * k * (1.6f - t * 0.8f), fill)
                4 -> {
                    val rad = psize[i] * k * (1.3f - t * 0.3f)
                    rect.set(x - rad, y - rad * 0.22f, x + rad, y + rad * 0.22f)
                    c.drawOval(rect, fill)
                }
                5 -> {
                    val radius = psize[i] * k * (1.4f - t)
                    line.color = fill.color; line.strokeWidth = (2f + t * 4f) * k
                    rect.set(x - radius, groundY - radius * 0.2f, x + radius, groundY + radius * 0.2f)
                    c.drawOval(rect, line)
                }
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

    /** Przyciemnienie tylko tła; kontury postaci i paski HP pozostają czytelne. */
    private fun drawNight(c: Canvas, w: Float) {
        fill.color = Color.argb(155, 8, 13, 31)
        c.drawRect(0f, 0f, w, groundY + 6f, fill)
        for (s in b.sides) {
            val x = sx(s.baseFront)
            if (x < -220f * k || x > w + 220f * k) continue
            val cy = groundY - 110f * k
            fill.color = Color.argb(20, 246, 196, 105)
            c.drawCircle(x, cy, 170f * k, fill)
            fill.color = Color.argb(if (s.baseFlash > 0f) 85 else 35, 255, 224, 142)
            c.drawCircle(x, cy, 92f * k, fill)
        }
    }

    /** Nakładka pogody: współrzędne z prealokowanych tablic, limit zależny od jakości. */
    private fun drawWeather(c: Canvas, w: Float) {
        val weather = b.weather
        if (weather != Weather.RAIN && weather != Weather.WIND) return
        val dp = g.ui.dp
        val count = min(rainX.size, g.quality.maxParticles / 2)
        if (weather == Weather.RAIN) {
            line.color = Color.argb(140, 197, 224, 245)
            line.strokeWidth = max(1f, 1.1f * dp)
            for (i in 0 until count) {
                val x = ((rainX[i] * w - time * 110f * dp) % w + w) % w
                val y = (rainY[i] * groundY + time * (235f + (i % 7) * 13f) * dp) % groundY
                c.drawLine(x, y, x - 8f * dp, y + 20f * dp, line)
            }
        } else {
            val dir = b.windDirection().toFloat()
            line.color = Color.argb(115, 220, 209, 175)
            line.strokeWidth = max(1f, dp)
            for (i in 0 until count / 3) {
                val x = ((rainX[i] * w + dir * time * (85f + i % 5 * 12f) * dp) % w + w) % w
                val y = groundY - (20f + rainY[i] * 90f) * k
                c.drawLine(x, y, x + dir * 15f * dp, y - 3f * dp, line)
            }
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
