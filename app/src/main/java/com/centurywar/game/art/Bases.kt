package com.centurywar.game.art

import android.graphics.Canvas
import com.centurywar.game.Defs
import com.centurywar.game.EraArt
import com.centurywar.game.art.Pen.Companion.dark
import com.centurywar.game.art.Pen.Companion.light
import kotlin.math.cos
import kotlin.math.sin

/**
 * Bazy wszystkich epok i modele wież. Statyczny korpus bazy ([body]) renderer nagrywa raz do
 * android.graphics.Picture (na epokę i drużynę), więc tekstury – bloki kamienne, cegły,
 * deski, panele betonowe, okna – nic nie kosztują CPU w kolejnych klatkach.
 * Układ: front bazy w x = 0 (strona wroga to +x), grunt y = 0, baza sięga do x ≈ −320.
 */
class Bases(private val p: Pen) {

    private val cv: Canvas get() = p.cv
    var time = 0f

    private val GOLD = 0xFFE8C04A.toInt()
    private val WOOD = 0xFF8A6038.toInt()
    private val WOOD_D = 0xFF5E3E22.toInt()
    private val IRON = 0xFF4A4C52.toInt()
    private val GLASS = 0xFF7CC4EC.toInt()
    private val WARM = 0xFFFFD27A.toInt()
    private val GLOW = 0xFF7FF4FF.toInt()

    /** Pozycja masztu flagi: (x, y szczytu). */
    fun flagX(e: Int) = flagX(Defs.spec(e).art)
    fun flagX(art: EraArt) = if (art == EraArt.IRON) -165f else when (art) {
        EraArt.COMBUSTION -> -144f
        EraArt.GALVANIC -> -170f
        EraArt.SWARM -> -190f
        EraArt.BIOTECH -> -178f
        else -> when (Defs.artPreset(art)) { 0 -> -150f; 1 -> -155f; 2 -> -195f; 3 -> -205f; 4 -> -120f; 5 -> -200f; 6 -> -195f; else -> -180f }
    }
    fun flagTop(e: Int) = flagTop(Defs.spec(e).art)
    fun flagTop(art: EraArt) = if (art == EraArt.IRON) -365f else when (art) {
        EraArt.COMBUSTION -> -365f
        EraArt.GALVANIC -> -440f
        EraArt.SWARM -> -350f
        EraArt.BIOTECH -> -435f
        else -> when (Defs.artPreset(art)) { 0 -> -330f; 1 -> -360f; 2 -> -470f; 3 -> -370f; 4 -> -270f; 5 -> -270f; 6 -> -470f; else -> -350f }
    }

    // ------------------------------------------------------------------ tekstury

    /** Mur z bloków / cegieł: poziome spoiny i przesunięte pionowe. */
    private fun masonry(l: Float, t: Float, r: Float, b: Float, rowH: Float, brickW: Float, mortar: Int, w: Float) {
        if (!p.hi) return
        val lp = p.lp(mortar, w)
        var y = b - rowH
        var row = 0
        while (y > t + 1f) { cv.drawLine(l + 1f, y, r - 1f, y, lp); y -= rowH }
        y = b
        while (y > t + 1f) {
            val top = maxOf(t, y - rowH)
            var x = l + (if (row % 2 == 0) brickW * 0.5f else brickW)
            while (x < r - 2f) { cv.drawLine(x, top, x, y, lp); x += brickW }
            y -= rowH; row++
        }
    }

    /** Losowo (deterministycznie) rozjaśnione bloki – faktura kamienia. */
    private fun blockTint(l: Float, t: Float, r: Float, b: Float, rowH: Float, brickW: Float, tint: Int) {
        if (!p.max) return
        var y = b; var row = 0
        while (y - rowH >= t - 0.5f) {
            var x = l + (if (row % 2 == 0) brickW * 0.5f else brickW) - brickW
            var i = 0
            while (x < r - 2f) {
                if (((row * 7 + i * 13) % 5) == 0) p.rectF(maxOf(l + 1.5f, x + 1.5f), y - rowH + 1.5f, minOf(r - 1.5f, x + brickW - 1.5f), y - 1.5f, tint)
                x += brickW; i++
            }
            y -= rowH; row++
        }
    }

    private fun planks(l: Float, t: Float, r: Float, b: Float, w: Float, line: Int) {
        if (!p.hi) return
        var x = l + w
        val lp = p.lp(line, 1.6f)
        while (x < r - 1f) { cv.drawLine(x, t + 1f, x, b - 1f, lp); x += w }
        if (p.max) { x = l + w * 0.5f; while (x < r) { p.circleF(x, t + 6f, 1.4f, IRON); p.circleF(x, b - 6f, 1.4f, IRON); x += w } }
    }

    private fun window(l: Float, t: Float, r: Float, b: Float, glass: Int, frame: Int, arch: Boolean) {
        if (arch) {
            val pa = p.path; pa.reset()
            pa.moveTo(l, b); pa.lineTo(l, t + (r - l) / 2f); pa.quadTo(l, t, (l + r) / 2f, t); pa.quadTo(r, t, r, t + (r - l) / 2f); pa.lineTo(r, b); pa.close()
            cv.drawPath(pa, p.fp(glass))
            if (p.hi) { cv.drawLine((l + r) / 2f, t, (l + r) / 2f, b, p.lp(frame, 2.4f)); cv.drawLine(l, (t + b) / 2f + 4f, r, (t + b) / 2f + 4f, p.line) }
            cv.drawPath(pa, p.op())
        } else {
            p.rectF(l, t, r, b, glass)
            if (p.hi) {
                p.tri(l + 2f, b - 3f, l + (r - l) * 0.55f, t + 2f, l + (r - l) * 0.75f, t + 2f); p.path.lineTo(l + 2f + (r - l) * 0.2f, b - 3f); p.path.close(); p.shapeF(light(glass, 0.35f))
                cv.drawLine((l + r) / 2f, t, (l + r) / 2f, b, p.lp(frame, 2f))
            }
            cv.drawRect(l, t, r, b, p.op())
        }
    }

    private fun banner(x: Float, top: Float, w: Float, h: Float, team: Int, emblem: Int) {
        val pa = p.path; pa.reset()
        pa.moveTo(x, top); pa.lineTo(x + w, top); pa.lineTo(x + w, top + h); pa.lineTo(x + w / 2f, top + h - w * 0.4f); pa.lineTo(x, top + h); pa.close()
        p.shape(team)
        if (p.hi) {
            p.rectF(x + 2f, top + 4f, x + w - 2f, top + 8f, GOLD)
            p.circle(x + w / 2f, top + h * 0.42f, w * 0.24f, emblem)
            p.rectF(x + 2f, top + 2f, x + w * 0.3f, top + h - 6f, light(team, 0.18f))
        }
        p.seg(x - 4f, top, x + w + 4f, top, 3f, WOOD_D)
    }

    private fun rock(x: Float, y: Float, r: Float, c: Int) {
        p.circle(x, y, r, c)
        if (p.hi) {
            p.r.set(x - r * 0.8f, y - r * 0.8f, x + r * 0.6f, y + r * 0.6f)
            cv.drawArc(p.r, 190f, 80f, false, p.lp(light(c, 0.3f), r * 0.12f))
            p.r.set(x - r * 0.9f, y - r * 0.9f, x + r * 0.9f, y + r * 0.9f)
            cv.drawArc(p.r, 20f, 110f, false, p.lp(dark(c, 0.25f), r * 0.14f))
        }
        if (p.max) {
            cv.drawLine(x + r * 0.1f, y - r * 0.3f, x + r * 0.35f, y + r * 0.05f, p.lp(dark(c, 0.4f), 2f))
            cv.drawLine(x + r * 0.35f, y + r * 0.05f, x + r * 0.25f, y + r * 0.3f, p.line)
        }
    }

    private fun stake(x: Float, h: Float, c: Int) {
        p.quad(x - 5f, 0f, x - 5f, -h, x, -h - 10f, x + 5f, -h); p.path.lineTo(x + 5f, 0f); p.path.close(); p.shape(c)
        if (p.hi) p.segF(x - 1.5f, -4f, x - 1.5f, -h + 2f, 1.6f, light(c, 0.25f))
    }

    // ------------------------------------------------------------------ korpusy baz

    /** Oryginalny fort żelazny: kamienna brama, drewniana palisada i blanki. */
    private fun ironFort(team: Int, pole: Float) {
        val stone = 0xFF918B7D.toInt()
        p.rect(-314f, -230f, -48f, 0f, stone)
        if (p.hi) masonry(-314f, -230f, -48f, 0f, 18f, 36f, 0xFF565349.toInt(), 1.5f)
        for (i in 0..7) {
            val x = -314f + i * 34f
            p.rect(x + 3f, -252f, x + 23f, -230f, stone)
        }
        p.rect(-319f, -72f, -268f, 0f, 0xFF80603D.toInt())
        for (i in 0..4) stake(-316f + i * 12f, 73f + (i % 2) * 10f, WOOD)
        p.rr(-206f, -144f, -125f, 0f, 19f, 0xFF48443E.toInt())
        p.rr(-195f, -130f, -136f, 0f, 10f, WOOD_D)
        if (p.hi) {
            for (i in 1..3) p.seg(-195f + i * 14f, -115f, -195f + i * 14f, 0f, 1.6f, 0xFFBC9870.toInt())
            p.seg(-208f, -147f, -124f, -147f, 7f, stone)
            p.circle(-165f, -120f, 4f, GOLD)
        }
        p.rect(-256f, -325f, -235f, -230f, 0xFF817B70.toInt())
        p.rect(-262f, -335f, -229f, -321f, stone)
        p.rect(-223f, -284f, -73f, -230f, 0xFF7A746B.toInt())
        for (i in 0..3) p.rect(-220f + i * 38f, -303f, -197f + i * 38f, -281f, stone)
        banner(-295f, -211f, 28f, 62f, team, 0xFFF5E6BC.toInt())
        p.seg(pole, -364f, pole, -253f, 5f, WOOD_D)
    }

    /** Budowle wariantów mają odrębne kontury zamiast znaczników na tej samej bazie. */
    private fun variantFort(art: EraArt, team: Int) {
        val dark = 0xFF343C46.toInt()
        val metal = 0xFF59656C.toInt()
        when (art) {
            EraArt.COMBUSTION -> {
                p.quad(-340f, 0f, -327f, -124f, -43f, -142f, -14f, 0f); p.shape(metal)
                p.rect(-302f, -140f, -50f, -84f, team)
                p.rect(-275f, -214f, -215f, -132f, dark)
                p.rect(-180f, -260f, -125f, -132f, dark)
                p.rect(-253f, -316f, -238f, -214f, 0xFF72746E.toInt())
                p.rect(-167f, -338f, -152f, -257f, 0xFF72746E.toInt())
                p.rr(-108f, -178f, -24f, -115f, 8f, dark)
                p.seg(-57f, -148f, 16f, -155f, 12f, metal)
                for (i in 0..3) p.circle(-285f + i * 65f, -38f, 17f, dark)
            }
            EraArt.GALVANIC -> {
                p.rr(-321f, -119f, -20f, 0f, 18f, metal)
                p.quad(-313f, -111f, -286f, -203f, -66f, -203f, -27f, -111f); p.shape(team)
                p.rr(-215f, -276f, -105f, -145f, 14f, dark)
                p.circle(-164f, -217f, 33f, 0xFF70D6D4.toInt())
                p.circleF(-164f, -217f, 17f, dark)
                for (i in 0..2) {
                    val x = -285f + i * 108f
                    p.seg(x, -155f, x, -390f, 9f, metal)
                    p.circle(x, -390f, 12f, team)
                    p.seg(x, -390f, x + 19f, -420f, 5f, 0xFF80DCD8.toInt())
                }
            }
            EraArt.SWARM -> {
                p.quad(-348f, -50f, -298f, -120f, -49f, -120f, 0f, -50f); p.shape(dark)
                p.rr(-315f, -169f, -30f, -82f, 12f, team)
                p.quad(-320f, -167f, -265f, -257f, -70f, -257f, -28f, -167f); p.shape(metal)
                for (i in 0..3) {
                    val x = -280f + i * 73f
                    p.rr(x - 25f, -310f + (i % 2) * 25f, x + 25f, -251f + (i % 2) * 25f, 8f, dark)
                    p.circle(x, -278f + (i % 2) * 25f, 12f, 0xFF80E5F0.toInt())
                }
                p.rr(-202f, -135f, -130f, -20f, 30f, dark)
                p.circle(-165f, -98f, 15f, team)
            }
            EraArt.BIOTECH -> {
                p.quad(-338f, 0f, -320f, -235f, -187f, -300f, -24f, 0f); p.shape(0xFF3D6450.toInt())
                p.quad(-315f, -20f, -262f, -197f, -124f, -200f, -36f, -20f); p.shape(team)
                p.rr(-232f, -191f, -125f, -55f, 27f, 0xFF315445.toInt())
                p.circle(-179f, -118f, 40f, 0xFF73CDA1.toInt())
                p.circleF(-179f, -118f, 25f, dark)
                for (i in 0..4) {
                    val x = -311f + i * 67f
                    p.seg(x, -194f, x + (i % 2) * 18f - 9f, -340f - (i % 3) * 22f, 14f, 0xFF476B55.toInt())
                    p.circle(x + (i % 2) * 18f - 9f, -340f - (i % 3) * 22f, 12f, team)
                }
            }
            else -> return
        }
    }

    fun body(e: Int, team: Int) = body(Defs.spec(e).art, team)

    fun body(art: EraArt, team: Int) {
        val pole = flagX(art)
        if (art == EraArt.IRON) { ironFort(team, pole); return }
        if (art == EraArt.COMBUSTION || art == EraArt.GALVANIC ||
            art == EraArt.SWARM || art == EraArt.BIOTECH) {
            variantFort(art, team); return
        }
        when (Defs.artPreset(art)) {
            0 -> {
                // namiot ze skór
                p.tri(-330f, 0f, -285f, -120f, -240f, 0f); p.shape(0xFFB08A5A.toInt())
                if (p.hi) { p.segF(-314f, -40f, -256f, -40f, 3f, team); p.segF(-285f, -120f, -298f, -135f, 3f, WOOD_D); p.segF(-285f, -120f, -272f, -136f, 3f, WOOD_D) }
                p.tri(-296f, 0f, -285f, -50f, -274f, 0f); p.shapeF(0xFF3A2A1C.toInt())
                rock(-225f, -112f, 100f, 0xFF8C8478.toInt())
                rock(-165f, -205f, 64f, 0xFF968E82.toInt())
                rock(-110f, -72f, 72f, 0xFF9A9286.toInt())
                rock(-270f, -50f, 54f, 0xFF847C70.toInt())
                // jaskinia z ogniskiem
                val pa = p.path; pa.reset()
                pa.moveTo(-205f, 0f); pa.lineTo(-205f, -60f); pa.quadTo(-200f, -118f, -160f, -120f); pa.quadTo(-118f, -118f, -115f, -60f); pa.lineTo(-115f, 0f); pa.close()
                p.shape(0xFF2A221C.toInt())
                p.ovalF(-190f, -46f, -130f, 0f, 0x60FF9A3A)
                p.tri(-172f, 0f, -160f, -30f, -148f, 0f); p.shapeF(0xFFFF9A3A.toInt())
                p.tri(-166f, 0f, -160f, -16f, -154f, 0f); p.shapeF(0xFFFFE07A.toInt())
                // łuk z kłów
                val tk = p.path; tk.reset(); tk.moveTo(-212f, -2f); tk.quadTo(-226f, -100f, -168f, -134f)
                cv.drawPath(tk, p.op(10f + p.ow * 2f)); cv.drawPath(tk, p.lp(0xFFF2ECDA.toInt(), 10f))
                tk.reset(); tk.moveTo(-108f, -2f); tk.quadTo(-94f, -100f, -152f, -134f)
                cv.drawPath(tk, p.op(10f + p.ow * 2f)); cv.drawPath(tk, p.lp(0xFFF2ECDA.toInt(), 10f))
                // totem z masztem flagi
                p.rect(pole - 7f, -330f, pole + 7f, -240f, WOOD)
                p.circle(pole, -258f, 13f, 0xFFF2ECDA.toInt()) // czaszka
                p.circleF(pole + 4f, -261f, 3f, 0xFF2A221C.toInt()); p.circleF(pole - 4f, -261f, 3f, 0xFF2A221C.toInt())
                // palisada
                for (i in 0..3) stake(-322f + i * 20f, 46f + (i % 2) * 10f, if (i % 2 == 0) WOOD else dark(WOOD, 0.12f))
                p.seg(-330f, -26f, -252f, -26f, 3f, 0xFF6E5034.toInt())
            }
            1 -> {
                val sand = 0xFFD6B878.toInt(); val sand2 = 0xFFC8A868.toInt()
                for (i in 0 until 4) {
                    val l = -290f + i * 30f; val r = -20f - i * 30f; val b = -i * 64f; val t = b - 64f
                    val c = if (i % 2 == 0) sand else sand2
                    p.rect(l, t, r, b, c)
                    if (p.hi) p.rectF(l + 2f, t + 2f, r - 2f, t + 8f, light(c, 0.25f))
                    masonry(l, t + 8f, r, b, 14f, 30f, dark(c, 0.25f), 1.6f)
                    if (p.hi) p.rectF(l + 2f, b - 8f, r - 2f, b - 1.5f, dark(c, 0.15f))
                }
                // schody środkowe
                p.quad(-185f, 0f, -170f, -256f, -140f, -256f, -125f, 0f); p.shape(light(sand, 0.15f))
                if (p.hi) { var y = -10f; while (y > -250f) { cv.drawLine(-183f + (-y) * 0.055f, y, -127f - (-y) * 0.055f, y, p.lp(dark(sand, 0.3f), 1.6f)); y -= 12f } }
                // świątynka na szczycie
                p.rect(-200f, -320f, -110f, -256f, 0xFFE4CC94.toInt())
                for (i in 0..3) p.rect(-196f + i * 25f, -312f, -186f + i * 25f, -256f, 0xFFF0E0B8.toInt())
                p.tri(-210f, -318f, -155f, -348f, -100f, -318f); p.shape(team)
                if (p.hi) p.segF(-200f, -322f, -110f, -322f, 3f, GOLD)
                // brama
                p.rr(-172f, -70f, -138f, 0f, 12f, 0xFF3A2A1A.toInt())
                p.seg(-180f, -72f, -130f, -72f, 6f, WOOD_D)
                // chorągwie na tarasach
                banner(-275f, -120f, 26f, 70f, team, 0xFFF4EEDC.toInt())
                banner(-60f, -120f, 26f, 70f, team, 0xFFF4EEDC.toInt())
                p.seg(pole, -256f, pole, -360f, 4f, WOOD_D)
            }
            2 -> {
                val st = 0xFFA4A49A.toInt(); val st2 = 0xFF92928A.toInt()
                // boczna wieża okrągła
                p.rect(-320f, -250f, -260f, 0f, st2)
                masonry(-320f, -250f, -260f, 0f, 18f, 30f, dark(st2, 0.3f), 1.6f)
                p.tri(-330f, -248f, -290f, -320f, -250f, -248f); p.shape(team)
                // mur kurtynowy
                p.rect(-300f, -190f, -60f, 0f, st)
                blockTint(-300f, -190f, -60f, 0f, 20f, 40f, light(st, 0.12f))
                masonry(-300f, -190f, -60f, 0f, 20f, 40f, dark(st, 0.3f), 1.6f)
                for (i in 0 until 6) p.rect(-300f + i * 42f, -212f, -278f + i * 42f, -190f, st)
                if (p.hi) p.rectF(-298f, -188f, -62f, -180f, light(st, 0.2f))
                // donżon
                p.rect(-240f, -340f, -150f, -190f, st2)
                blockTint(-240f, -340f, -150f, -190f, 20f, 30f, light(st2, 0.12f))
                masonry(-240f, -340f, -150f, -190f, 20f, 30f, dark(st2, 0.3f), 1.6f)
                p.tri(-252f, -338f, -195f, -440f, -138f, -338f); p.shape(team)
                if (p.hi) { p.tri(-195f, -440f, -138f, -338f, -170f, -338f); p.shapeF(dark(team, 0.2f)); p.segF(-250f, -340f, -140f, -340f, 3f, GOLD) }
                // strzelnice i okno
                for (i in 0..1) p.rr(-215f + i * 40f, -310f, -207f + i * 40f, -280f, 3f, 0xFF22201E.toInt())
                window(-208f, -250f, -182f, -215f, 0xFF3A3020.toInt(), WOOD_D, true)
                // brama z kratą
                val pa = p.path; pa.reset()
                pa.moveTo(-205f, 0f); pa.lineTo(-205f, -78f); pa.quadTo(-205f, -112f, -170f, -112f); pa.quadTo(-135f, -112f, -135f, -78f); pa.lineTo(-135f, 0f); pa.close()
                p.shape(0xFF2A221C.toInt())
                if (p.hi) {
                    val lp = p.lp(0xFF6A6A70.toInt(), 3f)
                    for (i in 1..4) cv.drawLine(-205f + i * 14f, -104f, -205f + i * 14f, -30f, lp)
                    for (i in 0..4) cv.drawLine(-203f, -96f + i * 15f, -137f, -96f + i * 15f, lp)
                }
                // chorągwie na murze
                banner(-290f, -178f, 30f, 84f, team, 0xFFF4EEDC.toInt())
                banner(-102f, -178f, 30f, 84f, team, 0xFFF4EEDC.toInt())
                p.seg(pole, -440f, pole, -470f, 4f, WOOD_D)
            }
            3 -> {
                val br = 0xFFA45A3E.toInt(); val trim = 0xFFE6DCC4.toInt()
                // bastion z dachem
                p.rect(-262f, -275f, -150f, -170f, dark(br, 0.08f))
                masonry(-262f, -275f, -150f, -170f, 10f, 22f, dark(br, 0.35f), 1.3f)
                p.tri(-272f, -273f, -206f, -320f, -140f, -273f); p.shape(0xFF4A4E58.toInt())
                if (p.hi) for (i in 0..5) p.rect(-262f + (i % 2) * 100f, -275f + (i / 2) * 35f, -250f + (i % 2) * 100f, -260f + (i / 2) * 35f, trim)
                window(-222f, -250f, -192f, -210f, WARM, 0xFF4A3020.toInt(), true)
                // mury
                p.rect(-300f, -170f, -50f, 0f, br)
                blockTint(-300f, -170f, -50f, 0f, 10f, 22f, light(br, 0.1f))
                masonry(-300f, -170f, -50f, 0f, 10f, 22f, dark(br, 0.35f), 1.3f)
                p.rect(-304f, -182f, -46f, -168f, trim)
                // strzelnice z lufami
                for (i in 0..2) {
                    val x = -280f + i * 76f
                    p.rr(x, -150f, x + 28f, -126f, 6f, 0xFF2A221C.toInt())
                    p.seg(x + 14f, -138f, x + 34f, -140f, 7f, 0xFF2A2A2E.toInt())
                }
                // brama z desek
                p.rect(-200f, -96f, -130f, 0f, WOOD)
                planks(-200f, -96f, -130f, 0f, 14f, WOOD_D)
                if (p.hi) { p.rectF(-200f, -76f, -130f, -70f, IRON); p.rectF(-200f, -26f, -130f, -20f, IRON) }
                p.rect(-206f, -104f, -124f, -96f, trim)
                // palisada przed murem
                for (i in 0..3) stake(-296f + i * 18f, 34f, WOOD)
                banner(-118f, -160f, 28f, 74f, team, trim)
                p.seg(pole, -275f, pole, -370f, 4f, 0xFF3A3A3A.toInt())
            }
            4 -> {
                val br = 0xFF9A4A34.toInt()
                // komin
                p.rect(-280f, -400f, -240f, -170f, 0xFF7A3E2E.toInt())
                masonry(-280f, -400f, -240f, -170f, 12f, 20f, dark(0xFF7A3E2E.toInt(), 0.35f), 1.2f)
                for (i in 0..2) p.rect(-284f, -330f + i * 70f, -236f, -322f + i * 70f, 0xFF3A3A3E.toInt())
                p.rect(-288f, -410f, -232f, -398f, 0xFF3A3A3E.toInt())
                p.rectF(-280f, -330f, -240f, -322f, team)
                // zbiornik wody na nogach
                p.seg(-90f, -180f, -90f, -250f, 4f, IRON); p.seg(-60f, -180f, -60f, -250f, 4f, IRON)
                p.rr(-104f, -300f, -46f, -246f, 10f, 0xFF6E6A62.toInt())
                if (p.hi) { p.rivets(-98f, -290f, -52f, 5, 1.6f, dark(0xFF6E6A62.toInt(), 0.4f)); p.rivets(-98f, -256f, -52f, 5, 1.6f, dark(0xFF6E6A62.toInt(), 0.4f)) }
                // hala
                p.rect(-310f, -180f, -50f, 0f, br)
                blockTint(-310f, -180f, -50f, 0f, 10f, 22f, light(br, 0.1f))
                masonry(-310f, -180f, -50f, 0f, 10f, 22f, dark(br, 0.35f), 1.2f)
                // dach szedowy
                for (i in 0 until 3) {
                    val x = -310f + i * 86.6f
                    p.tri(x, -180f, x + 86.6f, -238f, x + 86.6f, -180f); p.shape(0xFF4A4E58.toInt())
                    if (p.hi) { p.rectF(x + 70f, -232f, x + 85f, -184f, GLASS); cv.drawLine(x + 77f, -232f, x + 77f, -184f, p.lp(IRON, 1.6f)) }
                }
                // szyld w barwach drużyny z kołem zębatym
                p.rect(-290f, -168f, -70f, -138f, team)
                if (p.hi) {
                    p.circle(-180f, -153f, 11f, GOLD)
                    for (i in 0 until 8) { val a = i * 0.785f; p.circleF(-180f + cos(a) * 12f, -153f + sin(a) * 12f, 3f, GOLD) }
                    p.circleF(-180f, -153f, 4f, team)
                }
                // okna łukowe z ciepłym światłem
                for (i in 0 until 3) window(-290f + i * 80f, -124f, -250f + i * 80f, -70f, WARM, 0xFF3A3A3E.toInt(), true)
                // brama przesuwna
                p.rect(-150f, -60f, -90f, 0f, 0xFF5A5E66.toInt())
                if (p.hi) { planks(-150f, -60f, -90f, 0f, 12f, dark(0xFF5A5E66.toInt(), 0.35f)); p.seg(-150f, -30f, -90f, -60f, 2.4f, dark(0xFF5A5E66.toInt(), 0.2f)); p.seg(-150f, -60f, -90f, -30f, 2.4f, dark(0xFF5A5E66.toInt(), 0.2f)) }
                // rura
                p.poly2(-250f, -150f, -250f, -200f, -200f, -200f); p.limb(8f, 0xFF6E6A62.toInt())
                p.seg(pole, -180f, pole, -270f, 4f, 0xFF2A2A2A.toInt())
            }
            5 -> {
                val con = 0xFF868A80.toInt()
                val pa = p.path; pa.reset()
                pa.moveTo(-330f, 0f); pa.lineTo(-290f, -175f); pa.lineTo(-70f, -175f); pa.lineTo(-30f, 0f); pa.close()
                p.shape(con)
                if (p.hi) {
                    val lp = p.lp(dark(con, 0.22f), 1.8f)
                    for (i in 1..4) { val x = -290f + i * 44f; cv.drawLine(x, -173f, x + (x + 180f) * 0.23f, -2f, lp) }
                    cv.drawLine(-312f, -80f, -48f, -80f, lp)
                    p.quad(-290f, -175f, -70f, -175f, -66f, -158f, -294f, -158f); p.shapeF(light(con, 0.15f))
                    p.quad(-330f, 0f, -322f, -36f, -38f, -36f, -30f, 0f); p.shapeF(dark(con, 0.15f))
                    if (p.max) for (i in 0..9) p.circleF(-280f + i * 25f, -120f + (i % 3) * 12f, 2f, dark(con, 0.3f)) // ślady po kulach
                }
                // pas drużyny na wieńcu
                p.rect(-286f, -172f, -74f, -158f, team)
                if (p.hi) p.circle(-180f, -165f, 5f, 0xFFF4F0E4.toInt())
                // szczelina strzelnicza
                p.rect(-250f, -128f, -110f, -108f, 0xFF16181A.toInt())
                if (p.hi) p.rectF(-252f, -134f, -108f, -128f, dark(con, 0.35f))
                // stalowe drzwi
                p.rect(-220f, -70f, -170f, 0f, 0xFF4E5448.toInt())
                if (p.hi) { p.rivets(-214f, -64f, -176f, 4, 1.6f, 0xFF2A2E26.toInt()); p.rivets(-214f, -6f, -176f, 4, 1.6f, 0xFF2A2E26.toInt()); p.rect(-182f, -40f, -176f, -30f, 0xFF2A2E26.toInt()) }
                // worki z piaskiem
                for (row in 0..2) for (i in 0 until 5 - row) {
                    val x = -318f + i * 22f + row * 11f
                    p.rr(x, -14f - row * 13f, x + 22f, -1f - row * 13f, 6f, if ((i + row) % 2 == 0) 0xFFB4A078.toInt() else 0xFFA89470.toInt())
                }
                p.seg(pole, -175f, pole, -270f, 4f, 0xFF2A2A2A.toInt())
            }
            6 -> {
                val con = 0xFF7A8696.toInt()
                // wieża ze szkła
                p.rect(-252f, -392f, -138f, -150f, 0xFF4A86B4.toInt())
                if (p.hi) {
                    val lp = p.lp(0xFF2A4256.toInt(), 2f)
                    var y = -380f; while (y < -150f) { cv.drawLine(-252f, y, -138f, y, lp); y += 24f }
                    var x = -233f; while (x < -138f) { cv.drawLine(x, -392f, x, -150f, lp); x += 19f }
                    for (i in 0..2) { p.quad(-250f + i * 30f, -152f, -250f + i * 30f + 40f, -390f, -250f + i * 30f + 52f, -390f, -250f + i * 30f + 12f, -152f); p.shapeF(0x40FFFFFF) }
                }
                cv.drawRect(-252f, -392f, -138f, -150f, p.op())
                p.rect(-258f, -404f, -132f, -392f, con)
                p.rect(-210f, -430f, -180f, -404f, 0xFF5A6470.toInt()) // nadbudówka
                // antena satelitarna
                p.r.set(-252f, -440f, -222f, -410f); cv.drawArc(p.r, 120f, 180f, true, p.fp(0xFFE0E4EA.toInt())); cv.drawArc(p.r, 120f, 180f, true, p.op())
                // podium z oknami
                p.rect(-300f, -150f, -40f, 0f, con)
                if (p.hi) {
                    val lp = p.lp(dark(con, 0.25f), 1.6f)
                    for (i in 1..5) cv.drawLine(-300f + i * 43f, -150f, -300f + i * 43f, 0f, lp)
                }
                for (i in 0..5) window(-292f + i * 43f, -96f, -263f + i * 43f, -70f, GLASS, 0xFF3A4656.toInt(), false)
                p.rect(-304f, -150f, -36f, -126f, team) // pas z logo drużyny
                if (p.hi) { p.circle(-170f, -138f, 8f, 0xFFF4F6FA.toInt()); p.circleF(-170f, -138f, 4f, team) }
                // wejście
                window(-196f, -54f, -144f, 0f, 0xFF9AD6F2.toInt(), 0xFF3A4656.toInt(), false)
                p.rect(-204f, -60f, -136f, -54f, dark(con, 0.2f))
                p.seg(pole, -404f, pole, -470f, 3.6f, 0xFFB0B0B0.toInt())
            }
            else -> {
                val hull = 0xFF4A4070.toInt()
                // platforma
                p.rr(-330f, -24f, -30f, 0f, 8f, 0xFF3A3458.toInt())
                if (p.hi) for (i in 0..5) p.rectF(-314f + i * 48f, -14f, -290f + i * 48f, -8f, 0xFF7FF4FF.toInt())
                // kopuła zewnętrzna
                p.r.set(-320f, -270f, -40f, 10f)
                cv.drawArc(p.r, 180f, 180f, true, p.fp(hull)); cv.drawArc(p.r, 180f, 180f, true, p.op())
                if (p.hi) {
                    val lp = p.lp(dark(hull, 0.3f), 2f)
                    for (i in 1..3) { p.r.set(-180f - i * 46f, -270f, -180f + i * 46f, 10f); cv.drawArc(p.r, 180f, 180f, false, lp) }
                    p.r.set(-300f, -252f, -150f, -40f); cv.drawArc(p.r, 200f, 50f, false, p.lp(light(hull, 0.35f), 6f))
                }
                // kopuła wewnętrzna w barwach drużyny
                p.r.set(-262f, -190f, -98f, 10f)
                cv.drawArc(p.r, 180f, 180f, true, p.fp(dark(team, 0.15f))); cv.drawArc(p.r, 180f, 180f, true, p.op())
                // okna świecące pierścieniem
                for (i in 0..6) {
                    val a = 3.1416f + (i + 0.5f) * 3.1416f / 7f
                    p.circle(-180f + cos(a) * 118f, -130f + sin(a) * 118f + 0f, 6f, 0xFF2A2440.toInt())
                    p.glow(-180f + cos(a) * 118f, -130f + sin(a) * 118f, 3.6f, GLOW)
                }
                // brama
                p.rr(-206f, -76f, -154f, -2f, 22f, 0xFF20203A.toInt())
                p.rrF(-198f, -68f, -162f, -2f, 18f, 0x607FF4FF)
                // iglica
                p.seg(pole, -260f, pole, -350f, 4f, 0xFFB0B0FF.toInt())
            }
        }
    }

    // ------------------------------------------------------------------ dynamiczne detale bazy

    fun live(e: Int, team: Int) = live(Defs.spec(e).art, team)
    fun live(art: EraArt, team: Int) {
        when (Defs.artPreset(art)) {
            0 -> { // migotanie ogniska
                val f = 0.6f + 0.4f * sin(time * 17f) * sin(time * 7f)
                p.glow(-160f, -14f, 16f + f * 8f, ((0x50 * f).toInt() shl 24) or 0xFFB040)
            }
            6 -> p.glow(-195f, -472f, 6f, if (sin(time * 4f) > 0f) 0xFFFF3030.toInt() else 0xFF601010.toInt())
            7 -> {
                val pulse = 0.5f + 0.5f * sin(time * 3f)
                p.r.set(-340f, -175f, -20f, -105f)
                cv.drawOval(p.r, p.glp(((120 + 100 * pulse).toInt() shl 24) or 0x78F0FF, 8f))
                p.glow(-180f, -352f, 5f + pulse * 3f, GLOW)
            }
        }
    }

    // ------------------------------------------------------------------ wieża na działka

    fun tower(epoch: Int, team: Int, top: Float) = tower(Defs.spec(epoch).art, team, top)
    fun tower(art: EraArt, team: Int, top: Float) {
        val e = Defs.artPreset(art)
        val c = when (e) { 0, 1 -> WOOD; 2 -> 0xFF92928A.toInt(); 3 -> 0xFF9A5238.toInt(); 4 -> 0xFF8A4232.toInt(); 5 -> 0xFF7E8278.toInt(); 6 -> 0xFF6E7A8A.toInt(); else -> 0xFF4A4070.toInt() }
        if (e <= 1) {
            // dwa pnie z poprzeczkami
            p.rect(-72f, -top, -60f, 0f, c); p.rect(-26f, -top, -14f, 0f, c)
            var y = -30f
            while (y > -top + 10f) { p.seg(-66f, y, -20f, y - 28f, 3f, dark(c, 0.2f)); p.seg(-66f, y - 28f, -20f, y, 3f, dark(c, 0.1f)); y -= 56f }
        } else {
            p.rect(-70f, -top, -14f, 0f, c)
            when (e) {
                2, 3, 4 -> masonry(-70f, -top, -14f, 0f, if (e == 2) 18f else 10f, if (e == 2) 28f else 18f, dark(c, 0.32f), 1.3f)
                else -> if (p.hi) {
                    var y = -40f
                    while (y > -top) { cv.drawLine(-70f, y, -14f, y, p.lp(dark(c, 0.25f), 1.6f)); y -= 40f }
                    if (e >= 6) p.rectF(-62f, -top + 6f, -56f, -6f, light(c, 0.2f))
                }
            }
            if (p.hi) p.rectF(-24f, -top + 2f, -15.5f, -2f, dark(c, 0.2f))
            cv.drawRect(-70f, -top, -14f, 0f, p.op())
        }
        p.rect(-68f, -top + 14f, -16f, -top + 26f, team)
    }

    fun platform(epoch: Int, y: Float) = platform(Defs.spec(epoch).art, y)
    fun platform(art: EraArt, y: Float) {
        val e = Defs.artPreset(art)
        val c = when (e) { 0, 1 -> WOOD_D; 2, 3 -> 0xFF6E6E68.toInt(); else -> 0xFF3A3C42.toInt() }
        p.rect(-84f, y, 0f, y + 10f, c)
        if (p.hi) p.rectF(-82f, y + 1.5f, -2f, y + 3.5f, light(c, 0.25f))
        p.tri(-14f, y + 10f, -2f, y + 10f, -14f, y + 24f); p.shape(c)
    }

    // ------------------------------------------------------------------ działka (8 typów)

    fun turret(era: Int, x: Float, y: Float, firing: Boolean, team: Int) =
        turret(Defs.spec(era).art, x, y, firing, team)

    fun turret(art: EraArt, x: Float, y: Float, firing: Boolean, team: Int) {
        cv.save(); cv.translate(x, y); cv.scale(1.3f, 1.3f); cv.translate(-x, -y)
        turretModel(art, x, y, firing, team)
        cv.restore()
    }

    /** Żelazna balista osadzona na kamiennym przedpiersiu fortu. */
    private fun ironWallBallista(x: Float, y: Float, firing: Boolean, team: Int) {
        val stone = 0xFF817E78.toInt()
        p.rect(x - 23f, y - 22f, x + 12f, y, stone)
        if (p.hi) masonry(x - 23f, y - 22f, x + 12f, y, 7f, 12f, 0xFF4E4D47.toInt(), 1.3f)
        for (i in 0..2) p.rect(x - 24f + i * 13f, y - 29f, x - 16f + i * 13f, y - 21f, stone)
        p.seg(x - 11f, y - 26f, x + 37f, y - 28f, 4f, WOOD_D)
        p.seg(x - 9f, y - 20f, x + 12f, y - 39f, 3f, WOOD)
        val pa = p.path; pa.reset(); pa.moveTo(x + 12f, y - 45f); pa.quadTo(x + 31f, y - 27f, x + 12f, y - 8f)
        cv.drawPath(pa, p.op(4f)); cv.drawPath(pa, p.lp(0xFFB8A88A.toInt(), 3.3f))
        p.seg(x + 12f, y - 45f, x - 2f, y - 27f, 1.1f, 0xFFEAE4D2.toInt())
        p.seg(x + 12f, y - 8f, x - 2f, y - 27f, 1.1f, 0xFFEAE4D2.toInt())
        if (!firing) {
            p.seg(x - 3f, y - 27f, x + 49f, y - 29f, 2.2f, 0xFFD8D9CC.toInt())
            p.tri(x + 49f, y - 33f, x + 58f, y - 29f, x + 49f, y - 25f); p.shape(0xFF9DA1A0.toInt())
        }
        p.rectF(x - 12f, y - 19f, x + 5f, y - 15f, team)
    }

    private fun turretModel(art: EraArt, x: Float, y: Float, firing: Boolean, team: Int) {
        if (art == EraArt.COMBUSTION) {
            p.rr(x - 30f, y - 32f, x + 12f, y, 5f, 0xFF575F5D.toInt())
            p.rect(x - 25f, y - 28f, x + 8f, y - 19f, team)
            p.seg(x + 2f, y - 25f, x + 56f, y - 30f, 7f, 0xFF34383A.toInt())
        } else if (art == EraArt.GALVANIC) {
            p.rr(x - 22f, y - 21f, x + 8f, y, 5f, team)
            for (i in 0..2) {
                p.seg(x + i * 9f - 11f, y - 17f, x + i * 14f + 9f, y - 54f, 5f, 0xFF5E8888.toInt())
                p.circle(x + i * 14f + 9f, y - 54f, 5f, 0xFF8AE8ED.toInt())
            }
        } else if (art == EraArt.SWARM) {
            p.rr(x - 28f, y - 11f, x + 17f, y, 6f, team)
            for (i in 0..3) {
                val xx = x + (i - 1.5f) * 19f
                p.tri(xx - 9f, y - 31f, xx, y - 48f, xx + 9f, y - 31f)
                p.shape(team)
            }
        } else if (art == EraArt.BIOTECH) {
            p.quad(x - 21f, y, x - 19f, y - 49f, x + 13f, y - 46f, x + 18f, y)
            p.shape(0xFF47715B.toInt())
            p.circle(x + 1f, y - 31f, 13f, team)
            p.seg(x + 11f, y - 34f, x + 48f, y - 58f, 8f, 0xFF47715B.toInt())
        } else if (art == EraArt.IRON) {
            ironWallBallista(x, y, firing, team)
        } else when (Defs.artPreset(art)) {
            0 -> { // miotacz kamieni
                p.rect(x - 20f, y - 12f, x + 16f, y, WOOD)
                p.seg(x - 12f, y - 12f, x - 2f, y - 30f, 3f, WOOD_D); p.seg(x + 8f, y - 12f, x - 2f, y - 30f, 3f, WOOD_D)
                p.seg(x - 16f, y - 8f, x + 26f, y - 40f, 4f, WOOD)
                p.circle(x + 27f, y - 42f, 8f, 0xFF8E8E8E.toInt())
                p.seg(x - 20f, y - 6f, x + 16f, y - 6f, 1.6f, 0xFF6E5034.toInt())
            }
            1 -> { // balista
                p.rect(x - 20f, y - 10f, x + 10f, y, WOOD)
                p.seg(x - 22f, y - 16f, x + 34f, y - 20f, 4f, WOOD_D)
                val pa = p.path; pa.reset(); pa.moveTo(x + 14f, y - 40f); pa.quadTo(x + 24f, y - 18f, x + 14f, y + 2f)
                cv.drawPath(pa, p.op(3.4f + p.ow * 2f)); cv.drawPath(pa, p.lp(WOOD, 3.4f))
                cv.drawLine(x + 14f, y - 40f, x - 6f, y - 18f, p.lp(0xFFEDE8DA.toInt(), 1.2f)); cv.drawLine(x + 14f, y + 2f, x - 6f, y - 18f, p.line)
                if (!firing) { p.seg(x - 6f, y - 18f, x + 36f, y - 20f, 1.6f, WOOD_D); p.tri(x + 36f, y - 23f, x + 42f, y - 20f, x + 36f, y - 17f); p.shape(0xFFC8923A.toInt()) }
            }
            2 -> { // wieżyczka łucznicza z kuszą wałową
                p.rect(x - 22f, y - 34f, x + 18f, y, 0xFF92928A.toInt())
                masonry(x - 22f, y - 34f, x + 18f, y, 9f, 14f, 0xFF5E5E58.toInt(), 1f)
                cv.drawRect(x - 22f, y - 34f, x + 18f, y, p.op())
                for (i in 0..2) p.rect(x - 22f + i * 15f, y - 44f, x - 12f + i * 15f, y - 34f, 0xFF92928A.toInt())
                p.seg(x - 4f, y - 24f, x + 34f, y - 26f, 3.4f, WOOD_D)
                p.r.set(x + 18f, y - 38f, x + 28f, y - 12f); cv.drawArc(p.r, -90f, 180f, false, p.op(2.4f + p.ow * 2f)); cv.drawArc(p.r, -90f, 180f, false, p.lp(0xFFB4BCC6.toInt(), 2.4f))
                p.rectF(x - 10f, y - 30f, x + 6f, y - 27f, team)
            }
            3 -> { // armata
                p.quad(x - 22f, y, x - 18f, y - 8f, x + 10f, y - 18f, x + 12f, y); p.shape(team)
                cv.save(); cv.translate(x - 4f, y - 18f); cv.rotate(-8f)
                val rec = if (firing) 5f else 0f
                p.rr(-14f - rec, -6.5f, 34f - rec, 6.5f, 4f, 0xFF2E2E32.toInt())
                if (p.hi) { p.rectF(-6f - rec, -6f, -2f - rec, 6f, 0xFF4A4A50.toInt()); p.rectF(26f - rec, -6.5f, 30f - rec, 6.5f, 0xFF4A4A50.toInt()); p.segF(-10f - rec, -3f, 30f - rec, -3f, 1.4f, 0xFF6A6A70.toInt()) }
                cv.restore()
                p.circle(x - 2f, y - 8f, 9f, WOOD)
                if (p.hi) p.circleF(x - 2f, y - 8f, 3f, IRON)
            }
            4 -> { // działko wielolufowe
                p.rect(x - 18f, y - 22f, x + 4f, y, 0xFF6A6A5A.toInt())
                if (p.hi) p.rivets(x - 14f, y - 18f, x, 3, 1.1f, 0xFF3A3A30.toInt())
                p.rect(x - 22f, y - 30f, x + 8f, y - 22f, 0xFFB8883E.toInt())
                for (i in 0..3) p.seg(x + 2f, y - 18f + i * 4.5f, x + 38f, y - 18f + i * 4.5f, 2.2f, 0xFF3A3A3E.toInt())
                p.rect(x + 18f, y - 21f, x + 22f, y - 2f, 0xFFB8883E.toInt())
                p.seg(x - 18f, y - 11f, x - 26f, y - 4f, 2f, 0xFF3A3A3E.toInt())
                p.rectF(x - 16f, y - 14f, x + 2f, y - 8f, team)
            }
            5 -> { // przeciwlotnicze podwójne
                p.rect(x - 22f, y - 10f, x + 14f, y, 0xFF4E5440.toInt())
                p.seg(x - 2f, y - 16f, x + 34f, y - 44f, 4f, 0xFF2A2E22.toInt()); p.seg(x + 6f, y - 12f, x + 40f, y - 40f, 4f, 0xFF2A2E22.toInt())
                p.quad(x - 16f, y - 10f, x - 12f, y - 30f, x + 10f, y - 30f, x + 14f, y - 10f); p.shape(0xFF5E6648.toInt())
                p.rectF(x - 10f, y - 24f, x + 8f, y - 18f, team)
                if (p.hi) p.circle(x - 1f, y - 16f, 2f, 0xFFF4F0E4.toInt())
            }
            6 -> { // wyrzutnia rakiet
                p.rect(x - 8f, y - 10f, x + 4f, y, 0xFF3A4048.toInt())
                cv.save(); cv.translate(x - 2f, y - 14f); cv.rotate(-18f)
                p.rr(-20f, -14f, 22f, 8f, 3f, 0xFF7A8490.toInt())
                for (j in 0..1) p.circle(22f, -8f + j * 10f, 3.6f, 0xFF2A2A2E.toInt())
                if (!firing) for (j in 0..1) { p.tri(22f, -11f + j * 10f, 28f, -8f + j * 10f, 22f, -5f + j * 10f); p.shape(0xFFE04030.toInt()) }
                p.rectF(-16f, -10f, 6f, -4f, team)
                cv.restore()
            }
            else -> { // emiter laserowy
                p.rect(x - 10f, y - 8f, x + 6f, y, 0xFF3A3458.toInt())
                p.circle(x - 2f, y - 18f, 13f, 0xFF6A5AA0.toInt())
                if (p.hi) { p.r.set(x - 12f, y - 28f, x + 8f, y - 8f); cv.drawArc(p.r, 200f, 70f, false, p.lp(0xFFB0A8E8.toInt(), 2.4f)) }
                p.rr(x + 4f, y - 23f, x + 38f, y - 15f, 3f, 0xFFDADDE8.toInt())
                cv.drawLine(x + 8f, y - 19f, x + 36f, y - 19f, p.glp(GLOW, 2f))
                p.glow(x - 2f, y - 18f, 5f, team)
                p.glow(x + 40f, y - 19f, if (firing) 8f else 4f, GLOW)
            }
        }
        if (firing && art != EraArt.ORBITAL) { p.glow(x + 40f, y - 22f, 10f, 0xC0FFE080.toInt()); p.glow(x + 40f, y - 22f, 5f, 0xF0FFFFFF.toInt()) }
    }
}
