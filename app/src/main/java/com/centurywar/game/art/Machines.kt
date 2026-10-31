package com.centurywar.game.art

import android.graphics.Canvas
import com.centurywar.game.Body
import com.centurywar.game.Weapon
import com.centurywar.game.Defs
import com.centurywar.game.EraArt
import com.centurywar.game.art.Pen.Companion.dark
import com.centurywar.game.art.Pen.Companion.light
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Zwierzęta bojowe, machiny, pojazdy, mechy i poduszkowiec. Wszystkie z konturem,
 * pasami cieniowania, liniami paneli, nitami, włazami, gąsienicami i poświatą.
 * Układ lokalny jak u piechoty: grunt y = 0, przód w stronę +x.
 */
class Machines(private val p: Pen, private val men: Soldiers) {

    private val cv: Canvas get() = p.cv
    var time = 0f
    /** 0..1: płynny udział ruchu w pozie, także w klatce zatrzymania. */
    var stride = 1f
    /** -1 poza walką; 0..1 podczas przygotowania kolejnego ataku. */
    var windup = -1f
    /** Stałe przesunięcie fazy jednostki (nie wpływa na przebytą drogę kół). */
    var phase = 0f
    /** Skala UnitDef.size: odległość świata przeliczana na lokalne współrzędne. */
    var size = 1f

    private fun smooth(t: Float): Float = t * t * (3f - 2f * t)
    private fun ready(atk: Float): Float =
        if (atk > 0f) atk.coerceIn(0f, 1f)
        else if (windup >= 0f) smooth(windup.coerceIn(0f, 1f)) else 0f
    private fun rock(walk: Float, atk: Float): Float =
        if (p.hi) Motion.hullTilt(atk) + sin(walk * 1.7f + phase) * stride * 0.7f else 0f

    private val WOOD = 0xFF8A6038.toInt()
    private val WOOD_D = 0xFF5E3E22.toInt()
    private val IRON = 0xFF4A4C52.toInt()
    private val BRONZE = 0xFFC8923A.toInt()
    private val TRACK = 0xFF2C2C30.toInt()
    private val WHEEL = 0xFF5C5E64.toInt()
    private val GLOW = 0xFF7FF4FF.toInt()
    private val FLASH = 0xE0FFE27A.toInt()
    private val GLASS = 0xFF8ED0EE.toInt()

    /** Emblemat na pancerzu/oporządzeniu, bez przebudowy konturu maszyny. */
    fun tierMarks(body: Int, tier: Int, team: Int) {
        if (tier < 2) return
        val x = if (body == Body.SIEGE) -18f else if (body == Body.CANNON) 8f else 0f
        val y = when (body) {
            Body.BEAST -> -42f
            Body.SIEGE -> -32f
            Body.CANNON -> -25f
            Body.MECH -> -65f
            Body.HOVER -> -28f
            else -> -38f
        }
        val metal = 0xFFE8C04A.toInt()
        p.rr(x - 9f, y - 4f, x + 9f, y + 4f, 2f, metal)
        if (tier >= 3) {
            p.circle(x, y, 5f, 0xFFF8E2A0.toInt())
            p.circleF(x, y, 2.3f, team)
        }
    }

    // ================================================================== zwierzęta

    fun beast(era: Int, elite: Boolean, team: Int, walk: Float, atk: Float) {
        when {
            elite -> elephant(Defs.spec(era).art == EraArt.TRIBAL, team, walk, atk)
            Defs.spec(era).art == EraArt.BRONZE -> chariot(team, walk, atk)
            Defs.spec(era).art == EraArt.IRON -> {
                horse(0xFF73624E.toInt(), team, walk, atk, true)
                rider(era, Weapon.SWORD, team, walk, atk)
            }
            Defs.spec(era).art == EraArt.FEUDAL -> {
                horse(0xFFE2DCD0.toInt(), team, walk, atk, true)
                rider(era, Weapon.PIKE, team, walk, atk)
            }
            else -> {
                horse(0xFF6A4630.toInt(), team, walk, atk, false)
                rider(era, Weapon.SWORD, team, walk, atk)
            }
        }
    }

    private fun rider(era: Int, weapon: Int, team: Int, walk: Float, atk: Float) {
        val bob = if (p.hi) sin((walk + phase) * 2f) * stride * 1.4f else 0f
        cv.save(); cv.translate(-4f, -26f + bob); cv.scale(0.86f, 0.86f)
        men.draw(era, false, weapon, team, 0f, atk, true, false)
        cv.restore()
    }

    private fun horseLeg(x: Float, stepPhase: Float, c: Int, hoof: Int) {
        val a = sin(stepPhase) * 0.48f * stride
        val lift = max(0f, -sin(stepPhase)) * stride
        val kx = x + sin(a) * 13f
        val ky = -33f + cos(a) * 13f
        val sa = a - lift * 0.42f
        val fx = kx + sin(sa) * 14f
        val fy = ky + cos(sa) * 14f - 1f - lift * 5f
        p.poly2(kx, ky, (kx + fx) * 0.5f, (ky + fy) * 0.5f, fx, fy); p.limb(5f, c)
        p.quad(x - 5.5f, -33f, x + 5.5f, -33f, kx + 3f, ky, kx - 3f, ky); p.shape(c)
        p.circleF(kx, ky, 3.2f, c)
        p.rr(fx - 3f, fy - 3.5f, fx + 3.8f, fy + 1f, 1f, hoof)
    }

    private fun horse(hide: Int, team: Int, walk: Float, atk: Float, armored: Boolean) {
        val stepPhase = walk + phase
        val bob = if (p.hi) sin(stepPhase * 2f) * stride * 1.4f else 0f
        val hd = dark(hide, 0.28f)
        val mane = if (armored) 0xFF8A8478.toInt() else 0xFF2A1A12.toInt()
        // tylne nogi (dalsze)
        horseLeg(16f, stepPhase + 1.57f, hd, 0xFF2A2420.toInt())
        horseLeg(-20f, stepPhase + 3.14159f, hd, 0xFF2A2420.toInt())
        cv.save(); cv.translate(0f, bob)
        // ogon
        p.path.reset(); p.path.moveTo(-25f, -46f); p.path.quadTo(-38f, -40f, -34f, -20f); p.path.quadTo(-30f, -34f, -24f, -40f); p.path.close(); p.shape(mane)
        // Szyja i łeb kołyszą się razem; przed uderzeniem koń unosi głowę.
        cv.save()
        cv.rotate(sin(stepPhase) * stride * 2f - ready(atk) * 4f, 16f, -50f)
        p.quad(12f, -50f, 26f, -68f, 34f, -62f, 24f, -38f); p.shape(hide)
        val pa = p.path; pa.reset()
        pa.moveTo(24f, -70f); pa.lineTo(31f, -71f); pa.lineTo(44f, -60f); pa.quadTo(46f, -55f, 42f, -54f); pa.lineTo(33f, -56f); pa.lineTo(26f, -60f); pa.close()
        p.shape(hide)
        p.tri(26f, -69f, 27f, -76f, 30.5f, -70f); p.shape(hide) // ucho
        p.circleF(41.5f, -57f, 0.9f, 0xFF1A1418.toInt()) // nozdrze
        p.circleF(32f, -65f, 1.3f, 0xFF1A1418.toInt()) // oko
        // grzywa
        pa.reset(); pa.moveTo(24f, -70f); pa.lineTo(12f, -52f); pa.lineTo(14f, -48f); pa.lineTo(18f, -52f); pa.lineTo(20f, -55f); pa.lineTo(23f, -60f); pa.lineTo(27f, -66f); pa.close(); p.shape(mane)
        cv.restore()
        // tułów
        p.oval(-28f, -52f, 24f, -28f, hide)
        if (p.hi) {
            p.ovalF(-22f, -36f, 18f, -29.5f, hd) // cień brzucha
            p.segF(-18f, -49f, 10f, -50f, 1.6f, light(hide, 0.3f))
        }
        // uprząż
        if (p.hi) { p.segF(28f, -66f, 40f, -59f, 1.2f, 0xFF3A2A1E.toInt()); p.segF(29f, -60f, 4f, -46f, 1f, 0xFF3A2A1E.toInt()) }
        if (armored) {
            // kropierz w barwach drużyny z bordiurą
            val cp = p.path; cp.reset()
            cp.moveTo(-26f, -50f); cp.lineTo(18f, -51f); cp.lineTo(23f, -40f); cp.lineTo(21f, -27f); cp.lineTo(14f, -24f); cp.lineTo(8f, -27f); cp.lineTo(2f, -24f); cp.lineTo(-4f, -27f); cp.lineTo(-10f, -24f); cp.lineTo(-16f, -27f); cp.lineTo(-22f, -24f); cp.lineTo(-28f, -30f); cp.close()
            p.shape(team)
            if (p.hi) {
                p.segF(-26f, -30f, 20f, -29f, 2f, 0xFFE8C04A.toInt())
                p.circle(-2f, -40f, 4.6f, light(team, 0.7f))
                p.ovalF(-24f, -50f, 0f, -44f, dark(team, 0.2f))
            }
            // chanfron (stalowa maska)
            p.quad(26f, -69f, 32f, -70f, 42f, -61f, 34f, -61f); p.shape(0xFFB4BCC6.toInt())
        } else {
            // siodło z czaprakiem w barwach drużyny
            p.rr(-12f, -52f, 8f, -40f, 3f, team)
            if (p.hi) p.segF(-11f, -41.5f, 7f, -41.5f, 1.5f, 0xFFE8C04A.toInt())
            p.rr(-9f, -55f, 4f, -50f, 2f, 0xFF4A3020.toInt())
        }
        cv.restore()
        // Bliższe nogi mają przeciwne fazy do dalszych.
        horseLeg(13f, stepPhase + 4.71239f, hide, 0xFF2A2420.toInt())
        horseLeg(-23f, stepPhase, hide, 0xFF2A2420.toInt())
    }

    private fun wheel(x: Float, y: Float, r: Float, spin: Float, rim: Int, spokes: Int) {
        p.circle(x, y, r, rim)
        p.circle(x, y, r * 0.72f, dark(rim, 0.35f))
        p.circleF(x, y, r * 0.6f, light(rim, 0.15f))
        val count = if (p.hi) spokes else 2
        val rotation = Motion.wheelAngle(spin, size, r)
        for (i in 0 until count) {
            val a = rotation + i * (Motion.TAU / count)
            cv.drawLine(x, y, x + cos(a) * r * 0.66f, y + sin(a) * r * 0.66f, p.lp(dark(rim, 0.45f), r * 0.14f))
        }
        p.circle(x, y, r * 0.2f, BRONZE)
    }

    private fun chariot(team: Int, walk: Float, atk: Float) {
        // koń z przodu, rydwan z łucznikiem z tyłu
        cv.save(); cv.translate(16f, 0f); cv.scale(0.9f, 0.9f)
        horse(0xFFC09060.toInt(), team, walk, 0f, false)
        cv.restore()
        p.seg(-6f, -30f, 18f, -38f, 2.4f, WOOD_D) // dyszel
        // skrzynia rydwanu
        p.quad(-36f, -44f, -8f, -40f, -6f, -18f, -38f, -18f); p.shape(team)
        if (p.hi) {
            p.segF(-36f, -40f, -7f, -37f, 1.8f, BRONZE)
            p.circleF(-22f, -29f, 3.2f, BRONZE)
            p.segF(-30f, -22f, -14f, -22f, 1.2f, dark(team, 0.3f))
        }
        // łucznik w rydwanie
        cv.save(); cv.translate(-22f, -16f); cv.scale(0.8f, 0.8f)
        men.draw(1, true, Weapon.BOW, team, 0f, atk, false, false)
        cv.restore()
        // przód skrzyni zasłania nogi łucznika
        p.quad(-34f, -38f, -10f, -36f, -8f, -18f, -36f, -18f); p.shape(dark(team, 0.08f))
        if (p.hi) p.segF(-34f, -35f, -9f, -33.5f, 1.8f, BRONZE)
        wheel(-22f, -13f, 13f, walk, WOOD, 8)
    }

    private fun eleLeg(x: Float, stepPhase: Float, c: Int) {
        val swing = max(0f, -sin(stepPhase)) * stride
        val shift = sin(stepPhase) * stride * 7f
        val fx = x + shift
        val footY = -1f - swing * 6f
        val knee = x + shift * 0.4f - swing * 4f
        p.quad(x - 6.5f, -36f, x + 6.5f, -36f, knee + 7f, -17f, knee - 7f, -17f); p.shape(c)
        p.quad(knee - 7f, -19f, knee + 7f, -19f, fx + 6f, footY, fx - 6.5f, footY); p.shape(c)
        if (p.hi) for (i in 0..2) p.circleF(fx - 3.5f + i * 3.5f, footY - 1.5f, 1.4f, 0xFFE8E0CC.toInt())
    }

    private fun elephant(tusker: Boolean, team: Int, walk: Float, atk: Float) {
        val hide = if (tusker) 0xFF7A5434.toInt() else 0xFF8C8C92.toInt()
        val hd = dark(hide, 0.25f)
        val stepPhase = walk * 0.65f + phase
        eleLeg(14f, stepPhase + 1.57f, hd); eleLeg(-26f, stepPhase + 3.14159f, hd)
        cv.save()
        if (p.hi) cv.translate(0f, sin(stepPhase * 2f) * stride * 1.3f)
        // ogon
        p.seg(-36f, -60f, -42f, -38f, 2f, hd)
        // tułów
        val pa = p.path; pa.reset()
        pa.moveTo(-38f, -40f); pa.quadTo(-42f, -70f, -18f, -80f); pa.quadTo(8f, -86f, 26f, -74f); pa.quadTo(32f, -60f, 28f, -34f)
        pa.quadTo(0f, -26f, -30f, -30f); pa.close()
        p.shape(hide)
        if (p.hi) {
            pa.reset(); pa.moveTo(-34f, -36f); pa.quadTo(0f, -30f, 26f, -38f); pa.lineTo(28f, -34f); pa.quadTo(0f, -26f, -30f, -30f); pa.close(); p.shapeF(hd)
            p.segF(-20f, -77f, 10f, -80f, 2f, light(hide, 0.25f))
        }
        if (tusker && p.hi) { // kudły
            for (i in 0..5) { val x = -32f + i * 10f; p.tri(x, -36f, x + 5f, -24f, x + 9f, -35f); p.shapeF(hd) }
        }
        // głowa
        p.circle(34f, -66f, 15f, hide)
        if (tusker) { p.circle(30f, -79f, 9f, hide) } // kopuła czaszki mamuta
        // trąba
        val tr = p.path; tr.reset()
        tr.moveTo(44f, -66f); tr.quadTo(56f, -50f, 50f, -26f - atk * 6f - ready(atk) * 5f + sin(stepPhase) * stride * 2f); tr.quadTo(52f, -18f - ready(atk) * 4f, 58f, -20f - ready(atk) * 4f)
        cv.drawPath(tr, p.op(9f + p.ow * 2f)); cv.drawPath(tr, p.lp(hide, 9f))
        if (p.hi) for (i in 0..3) p.segF(47f + i * 1.6f, -56f + i * 7f, 53f + i * 0.8f, -57f + i * 7f, 0.8f, hd)
        // ucho
        pa.reset(); pa.moveTo(26f, -74f); pa.quadTo(14f, -76f, 14f, -60f); pa.quadTo(18f, -50f, 28f, -56f); pa.close(); p.shape(dark(hide, 0.12f))
        p.circleF(38f, -70f, 1.6f, 0xFF1A1418.toInt())
        // kły
        val tk = p.path; tk.reset()
        tk.moveTo(42f, -56f); tk.quadTo(54f, -38f + atk * 4f, 70f, -46f + atk * 6f)
        cv.drawPath(tk, p.op(5f + p.ow * 2f)); cv.drawPath(tk, p.lp(0xFFF4EEDC.toInt(), 5f))
        if (!tusker) { // brązowa płyta na czole
            p.quad(28f, -80f, 42f, -78f, 46f, -64f, 30f, -66f); p.shape(BRONZE)
            if (p.hi) p.rivets(31f, -72f, 43f, 3, 1f, 0xFF8A5A20.toInt())
        }
        // grzbiet: kosz / wieża w barwach drużyny
        if (tusker) {
            p.quad(-26f, -82f, 16f, -84f, 20f, -60f, -24f, -58f); p.shape(team)
            if (p.hi) {
                p.segF(-24f, -62f, 19f, -64f, 2.4f, 0xFFE8C04A.toInt())
                for (i in 0..4) { p.tri(-22f + i * 9f, -61f, -18f + i * 9f, -54f, -14f + i * 9f, -61f); p.shapeF(0xFFE8C04A.toInt()) }
            }
            // jeździec
            cv.save(); cv.translate(-6f, -60f); cv.scale(0.7f, 0.7f)
            men.draw(0, false, Weapon.SPEAR, team, 0f, atk, true, false)
            cv.restore()
        } else {
            p.rect(-22f, -112f, 14f, -80f, team)
            for (i in 0..3) p.rect(-22f + i * 10f, -118f, -16f + i * 10f, -111f, team)
            if (p.hi) {
                p.segF(-22f, -86f, 14f, -86f, 2f, 0xFFE8C04A.toInt())
                p.rectF(-18f, -106f, 10f, -100f, dark(team, 0.35f))
                p.segF(-20f, -109f, -20f, -82f, 1.2f, dark(team, 0.25f))
                p.seg(-6f, -118f, 6f, -138f, 1.4f, WOOD_D); p.tri(6f, -138f, 4f, -132f, 9f, -134f); p.shapeF(0xFFB4BCC6.toInt())
            }
            p.quad(-32f, -84f, 22f, -84f, 26f, -64f, -34f, -66f); p.shape(dark(team, 0.15f)) // czaprak
            if (p.hi) p.segF(-32f, -68f, 24f, -67f, 2.2f, 0xFFE8C04A.toInt())
        }
        cv.restore()
        eleLeg(20f, stepPhase + 4.71239f, hide); eleLeg(-20f, stepPhase, hide)
    }

    // ================================================================== machiny

    /** Lekka polowa balista na dwukołowej ramie, elita Epoki żelaza. */
    fun scorpion(team: Int, walk: Float, atk: Float) {
        p.rect(-39f, -26f, 38f, -15f, WOOD)
        p.seg(-25f, -22f, -10f, -63f, 5f, WOOD_D)
        p.seg(24f, -22f, 6f, -63f, 5f, WOOD)
        p.rect(-14f, -62f, 24f, -51f, team)
        p.seg(-25f, -54f, 58f, -54f, 5f, WOOD_D)
        p.seg(-20f, -35f, 10f, -62f, 2.4f, IRON)
        val pa = p.path; pa.reset(); pa.moveTo(32f, -84f); pa.quadTo(50f, -58f, 32f, -30f)
        cv.drawPath(pa, p.op(4.2f)); cv.drawPath(pa, p.lp(WOOD, 3.8f))
        val draw = ready(atk)
        p.seg(32f, -84f, -6f - draw * 16f, -54f, 1.4f, 0xFFF0E0BC.toInt())
        p.seg(32f, -30f, -6f - draw * 16f, -54f, 1.4f, 0xFFF0E0BC.toInt())
        if (atk < 0.65f) {
            p.seg(-7f, -54f, 67f, -54f, 2.7f, 0xFFB5BBC0.toInt())
            p.tri(67f, -58f, 78f, -54f, 67f, -50f); p.shape(0xFFDCE1E2.toInt())
        }
        if (p.hi) p.rivets(-30f, -21f, 30f, 5, 1.3f, IRON)
        wheel(-28f, -12f, 12f, walk, WOOD, 7)
        wheel(28f, -12f, 12f, walk, WOOD, 7)
    }

    fun trebuchet(team: Int, walk: Float, atk: Float) {
        val wd = dark(WOOD, 0.25f)
        // rama na kołach
        p.rect(-40f, -24f, 40f, -14f, WOOD)
        if (p.hi) { p.segF(-38f, -20f, 38f, -20f, 0.8f, wd); p.rivets(-34f, -19f, 34f, 5, 1.1f, IRON) }
        // podpory A
        p.seg(-18f, -22f, -2f, -78f, 5f, wd)
        p.seg(16f, -22f, 2f, -78f, 5f, WOOD)
        p.seg(-12f, -46f, 11f, -46f, 3f, WOOD)
        // ramię miotające
        // W trakcie odnowienia ramię wraca do naciągu; przy strzale płynnie opada.
        val ang = -28f - 122f * ready(atk)
        cv.save(); cv.translate(0f, -76f); cv.rotate(ang)
        p.seg(-22f, 0f, 52f, 0f, 4.4f, WOOD_D)
        if (p.hi) p.rivets(-12f, 0f, 40f, 4, 0.9f, IRON)
        // przeciwwaga w barwach drużyny, okuta
        p.rect(-34f, -6f, -16f, 16f, team)
        if (p.hi) { p.segF(-34f, 0f, -16f, 0f, 1.6f, IRON); p.segF(-34f, 10f, -16f, 10f, 1.6f, IRON) }
        // proca z kamieniem
        p.seg(52f, 0f, 58f, 12f, 0.9f, 0xFFB8A070.toInt())
        if (atk <= 0f) p.circle(58f, 14f, 5f, 0xFF8E8E8E.toInt())
        cv.restore()
        p.circle(0f, -76f, 4f, IRON)
        // proporzec
        p.seg(-36f, -24f, -36f, -60f, 1.6f, WOOD_D)
        val wv = sin(time * 6f) * 2f
        p.quad(-36f, -60f, -22f, -57f + wv, -36f, -50f, -36f, -50f); p.shape(team)
        wheel(-28f, -11f, 11f, walk, WOOD, 6)
        wheel(28f, -11f, 11f, walk, WOOD, 6)
    }

    fun cannon(team: Int, walk: Float, atk: Float) {
        val kick = Motion.recoil(atk)
        val rec = kick * 7f
        // Laweta odskakuje pod lufą, ale koła zachowują kontakt z gruntem.
        cv.save(); cv.translate(-rec * 0.35f, -kick * 2.2f)
        // ogon lawety
        p.quad(-42f, -4f, -36f, -12f, 10f, -36f, 8f, -18f); p.shape(team)
        if (p.hi) { p.segF(-36f, -8f, 2f, -26f, 1f, dark(team, 0.35f)); p.rivets(-28f, -13f, -6f, 3, 1f, IRON) }
        // lufa z brązu
        cv.save(); cv.translate(-rec, -30f); cv.rotate(-12f - ready(atk) * 3f)
        val pa = p.path; pa.reset()
        pa.moveTo(-20f, -8.5f); pa.lineTo(38f, -5.5f); pa.lineTo(44f, -7f); pa.lineTo(44f, 7f); pa.lineTo(38f, 5.5f); pa.lineTo(-20f, 8.5f); pa.close()
        p.shape(BRONZE)
        if (p.hi) {
            p.segF(-16f, -4.5f, 38f, -3f, 1.8f, light(BRONZE, 0.45f))
            p.segF(-16f, 5f, 38f, 3.6f, 1.6f, dark(BRONZE, 0.3f))
            p.rect(-8f, -8.5f, -4f, 8.5f, dark(BRONZE, 0.15f))
            p.rect(18f, -7f, 21f, 7f, dark(BRONZE, 0.15f))
        }
        p.circle(-22f, 0f, 4.5f, BRONZE) // guz
        p.circleF(44f, 0f, 3.2f, 0xFF1A1A1A.toInt())
        cv.restore()
        cv.restore()
        // koło
        wheel(0f, -16f, 16f, walk, WOOD, 10)
        if (p.hi) { p.r.set(-16f, -32f, 16f, 0f); cv.drawOval(p.r, p.lp(IRON, 2f)) }
        // kule
        p.circle(-30f, -4f, 4f, 0xFF2A2A2E.toInt()); p.circle(-22f, -4f, 4f, 0xFF2A2A2E.toInt()); p.circle(-26f, -10f, 4f, 0xFF2A2A2E.toInt())
        if (atk > 0.75f) { p.glow(48f, -42f, 12f, FLASH); p.glow(48f, -42f, 6f, 0xF0FFFFFF.toInt()) }
    }

    // ================================================================== pojazdy

    private fun tracks(x0: Float, x1: Float, top: Float, bottom: Float, walk: Float, wheels: Int) {
        val h = bottom - top
        p.rr(x0, top, x1, bottom, h / 2f, TRACK)
        // ogniwa gąsienicy przesuwające się z ruchem
        run {
            val step = if (p.hi) 5f else 10f
            val off = Motion.trackOffset(walk, size, step)
            var x = x0 + h / 2f + off
            p.lp(0xFF4A4A50.toInt(), 1.2f)
            while (x < x1 - h / 2f) { cv.drawLine(x, top + 0.8f, x, top + 2.6f, p.line); cv.drawLine(x, bottom - 2.6f, x, bottom - 0.8f, p.line); x += step }
        }
        val r = h * 0.34f
        for (i in 0 until wheels) {
            val wx = x0 + h / 2f + i * ((x1 - x0 - h) / (wheels - 1))
            p.circle(wx, (top + bottom) / 2f, r, WHEEL)
            if (p.hi) p.circleF(wx, (top + bottom) / 2f, r * 0.4f, dark(WHEEL, 0.4f))
            val a = Motion.wheelAngle(walk, size, r)
            cv.drawLine(wx, (top + bottom) / 2f, wx + cos(a) * r * 0.8f, (top + bottom) / 2f + sin(a) * r * 0.8f, p.lp(light(WHEEL, 0.35f), 1.2f))
        }
    }

    private fun roadWheel(x: Float, y: Float, r: Float, walk: Float) {
        p.circle(x, y, r, 0xFF26262A.toInt())
        p.circle(x, y, r * 0.6f, 0xFF6A6C72.toInt())
        val a = Motion.wheelAngle(walk, size, r)
        cv.drawLine(x, y, x + cos(a) * r * 0.46f, y + sin(a) * r * 0.46f, p.lp(0xFF3A3A40.toInt(), 1.4f))
        if (p.hi) for (i in 0..2) {
            val b = a + i * 2.094f
            p.circleF(x + cos(b) * r * 0.35f, y + sin(b) * r * 0.35f, r * 0.1f, 0xFF3A3A40.toInt())
        }
    }

    fun vehicle(epoch: Int, elite: Boolean, team: Int, walk: Float, atk: Float) {
        when (Defs.spec(epoch).art) {
            EraArt.STEAM -> ironclad(team, walk, atk)
            EraArt.STEEL -> if (elite) heavyTank(team, walk, atk) else halftrack(team, walk, atk)
            else -> apc(team, walk, atk)
        }
    }

    private fun muzzle(x: Float, y: Float, atk: Float, s: Float) {
        if (atk < 0.72f) return
        p.glow(x + 4f * s, y, 9f * s, FLASH)
        p.glow(x + 3f * s, y, 4.5f * s, 0xF0FFFFFF.toInt())
    }

    private fun ironclad(team: Int, walk: Float, atk: Float) {
        val hull = 0xFF5E5A54.toInt()
        val hd = dark(hull, 0.3f)
        tracks(-48f, 48f, -20f, 0f, walk, 5)
        cv.save()
        cv.rotate(-rock(walk, atk), -39f, -18f)
        // komin z dymem
        p.rect(-28f, -76f, -18f, -46f, 0xFF3A3A3E.toInt())
        p.rect(-30f, -80f, -16f, -75f, 0xFF2A2A2E.toInt())
        if (p.hi) p.rectF(-27f, -70f, -19f, -66f, team)
        if (walk != 0f || atk > 0f) for (i in 0..2) {
            val t = ((time * 0.8f + i * 0.33f) % 1f)
            p.glow(-23f - t * 10f, -84f - t * 26f, 4f + t * 8f, ((0x90 * (1f - t)).toInt() shl 24) or 0x5A5650)
        }
        // kadłub opancerzony
        val pa = p.path; pa.reset()
        pa.moveTo(-50f, -18f); pa.lineTo(-44f, -48f); pa.lineTo(34f, -48f); pa.lineTo(52f, -26f); pa.lineTo(50f, -18f); pa.close()
        p.shape(hull)
        if (p.hi) {
            p.quad(-50f, -18f, -48f, -26f, 51f, -26f, 50f, -18f); p.shapeF(hd)
            p.segF(-42f, -46f, 33f, -46f, 1.6f, light(hull, 0.3f))
            p.rivets(-42f, -44f, 32f, 9, 0.9f, 0xFF2E2C2A.toInt())
            p.rivets(-46f, -22f, 48f, 11, 0.9f, 0xFF2E2C2A.toInt())
        }
        // pas w barwach drużyny + iluminatory
        p.rect(-46f, -38f, 44f, -29f, team)
        if (p.hi) for (i in 0..2) { p.circle(-30f + i * 22f, -33.5f, 3f, 0xFFC8923A.toInt()); p.circleF(-30f + i * 22f, -33.5f, 1.8f, 0xFFFFD27A.toInt()) }
        // wieża z działem
        val rec = Motion.recoil(atk) * 5f
        p.seg(8f - rec, -58f, 50f - rec, -60f, 4.4f, IRON)
        p.rect(44f - rec, -62.5f, 50f - rec, -56f, IRON)
        p.rr(-8f, -66f, 22f, -47f, 6f, light(hull, 0.06f))
        if (p.hi) { p.rivets(-4f, -61f, 18f, 4, 0.9f, 0xFF2E2C2A.toInt()); p.rr(0f, -70f, 10f, -66f, 1.5f, hd) }
        // flaga
        p.seg(-40f, -48f, -40f, -74f, 1.2f, 0xFF2A2A2A.toInt())
        p.quad(-40f, -74f, -28f, -71f + sin(time * 6f) * 1.5f, -40f, -66f, -40f, -66f); p.shape(team)
        muzzle(52f - rec, -60f, atk, 1f)
        cv.restore()
    }

    private fun halftrack(team: Int, walk: Float, atk: Float) {
        val hull = 0xFF5A6446.toInt()
        val hd = dark(hull, 0.3f)
        // gąsienica tylna
        tracks(-40f, -2f, -16f, 0f, walk, 3)
        // kadłub
        val pa = p.path; pa.reset()
        pa.moveTo(-42f, -14f); pa.lineTo(-42f, -36f); pa.lineTo(8f, -36f); pa.lineTo(14f, -44f); pa.lineTo(26f, -44f); pa.lineTo(30f, -32f); pa.lineTo(44f, -28f); pa.lineTo(44f, -14f); pa.close()
        p.shape(hull)
        if (p.hi) {
            p.quad(-42f, -14f, -42f, -20f, 44f, -20f, 44f, -14f); p.shapeF(hd)
            p.segF(-40f, -34f, 8f, -34f, 1.4f, light(hull, 0.25f))
            p.segF(30f, -30f, 44f, -26f, 1f, hd) // maska silnika
            for (i in 0..2) p.segF(34f + i * 3f, -26f, 34f + i * 3f, -21f, 1f, hd) // żaluzje
        }
        p.rect(-38f, -32f, 4f, -24f, team) // pas drużyny
        if (p.hi) p.circle(-17f, -28f, 2.6f, 0xFFF4F0E4.toInt())
        // szyba kabiny
        p.quad(15f, -42f, 25f, -42f, 27f, -34f, 15f, -34f); p.shape(GLASS)
        // koło przednie
        roadWheel(32f, -10f, 10f, walk)
        if (p.hi) p.circleF(32f, -10f, 2f, hd)
        // strzelec z karabinem maszynowym
        cv.save(); cv.translate(-18f, -22f); cv.scale(0.72f, 0.72f)
        men.draw(5, true, Weapon.MG, team, 0f, atk, true, false)
        cv.restore()
        p.rect(-44f, -42f, 6f, -34f, hull) // burta zasłania nogi strzelca
        if (p.hi) p.rivets(-40f, -38f, 2f, 6, 0.8f, hd)
    }

    private fun heavyTank(team: Int, walk: Float, atk: Float) {
        val hull = 0xFF56603F.toInt()
        val hd = dark(hull, 0.3f)
        val hl = light(hull, 0.22f)
        // gąsienice + koła jezdne
        p.rr(-50f, -22f, 50f, 0f, 11f, TRACK)
        run {
            val step = if (p.hi) 6f else 12f
            val off = Motion.trackOffset(walk, size, step)
            var x = -44f + off
            p.lp(0xFF4A4A50.toInt(), 1.4f)
            while (x < 46f) { cv.drawLine(x, -21.5f, x, -19f, p.line); cv.drawLine(x, -2.5f, x, 0f, p.line); x += step }
        }
        for (i in 0 until 6) roadWheel(-40f + i * 16f, -10f, 7f, walk)
        cv.save()
        cv.rotate(-rock(walk, atk), -40f, -22f)
        // osłona gąsienic
        p.quad(-54f, -22f, -48f, -30f, 50f, -30f, 56f, -22f); p.shape(hd)
        // kadłub pochyły
        p.quad(-50f, -30f, -44f, -46f, 36f, -46f, 54f, -30f); p.shape(hull)
        if (p.hi) {
            p.segF(-42f, -44f, 35f, -44f, 1.4f, hl)
            p.rivets(-40f, -33f, 46f, 9, 0.9f, hd)
            p.rect(-46f, -44f, -38f, -38f, hd) // skrzynka
        }
        p.rect(-28f, -44f, 22f, -36f, team) // pas drużyny
        // wieża
        val rec = Motion.recoil(atk) * 6f
        p.seg(16f - rec, -55f, 70f - rec, -56f, 5f, hd)
        p.rect(66f - rec, -59.5f, 74f - rec, -51.5f, dark(hull, 0.4f)) // hamulec wylotowy
        val pa = p.path; pa.reset()
        pa.moveTo(-24f, -46f); pa.lineTo(-20f, -64f); pa.lineTo(14f, -66f); pa.lineTo(26f, -56f); pa.lineTo(22f, -46f); pa.close()
        p.shape(hull)
        if (p.hi) {
            p.quad(-22f, -52f, 24f, -52f, 22f, -46f, -24f, -46f); p.shapeF(hd)
            p.segF(-18f, -62f, 12f, -64f, 1.4f, hl)
            p.circle(4f, -54f, 3f, 0xFFF4F0E4.toInt()) // gwiazda / znak
        }
        p.rr(-14f, -71f, 2f, -65f, 2f, hd) // właz
        if (p.max) p.seg(-18f, -66f, -26f, -86f, 0.8f, 0xFF2A2A2A.toInt()) // antena
        muzzle(74f - rec, -55.5f, atk, 1.2f)
        cv.restore()
    }

    private fun apc(team: Int, walk: Float, atk: Float) {
        val hull = 0xFF6A7480.toInt()
        val hd = dark(hull, 0.3f)
        val hl = light(hull, 0.25f)
        // Koła pozostają na ziemi; nadwozie buja się wokół tylnej osi.
        for (i in 0..3) roadWheel(-32f + i * 22f, -10f, 9.5f, walk)
        cv.save(); cv.rotate(-rock(walk, atk), -32f, -10f)
        // kadłub z pochyłym przodem
        val pa = p.path; pa.reset()
        pa.moveTo(-46f, -12f); pa.lineTo(-46f, -40f); pa.lineTo(22f, -42f); pa.lineTo(48f, -26f); pa.lineTo(46f, -12f); pa.close()
        p.shape(hull)
        if (p.hi) {
            p.quad(-46f, -12f, -46f, -18f, 47f, -18f, 46f, -12f); p.shapeF(hd)
            p.segF(-44f, -38f, 20f, -40f, 1.4f, hl)
            p.segF(-10f, -40f, -10f, -18f, 0.9f, hd); p.segF(20f, -41f, 20f, -18f, 0.9f, hd) // linie paneli
            p.rect(-42f, -36f, -34f, -22f, hd) // drzwi
        }
        p.rect(-30f, -34f, 16f, -24f, team) // panel drużyny
        if (p.hi) { p.rr(26f, -36f, 36f, -31f, 1f, 0xFF1C2A34.toInt()); p.glow(44f, -22f, 1.8f, 0xFFFFF0A0.toInt()) }
        // wieżyczka z działkiem
        val rec = Motion.recoil(atk) * 4f
        p.seg(8f - rec, -50f, 46f - rec, -50f, 2.6f, 0xFF2C3036.toInt())
        p.rr(-10f, -56f, 14f, -41f, 4f, light(hull, 0.06f))
        if (p.hi) { p.rect(-6f, -54f, 2f, -48f, 0xFF1C2A34.toInt()); p.seg(-12f, -48f, -20f, -64f, 0.8f, 0xFF2A2A2A.toInt()) }
        muzzle(46f - rec, -50f, atk, 0.8f)
        cv.restore()
    }

    // ================================================================== mechy

    fun mech(epoch: Int, elite: Boolean, team: Int, walk: Float, atk: Float) {
        when (Defs.spec(epoch).art) {
            EraArt.STEAM -> steamWalker(team, walk, atk)
            EraArt.ORBITAL -> titan(team, walk, atk)
            else -> battleMech(team, walk, atk)
        }
    }

    private fun mechLeg(hx: Float, hy: Float, stepPhase: Float, s: Float, c: Int, foot: Int, digi: Boolean) {
        // Podparcie trwa połowę cyklu: stopa cofa się o -drogę/size bez poślizgu.
        val xStep = Motion.mechFootX(stepPhase, size) * stride
        val lift = Motion.mechFootLift(stepPhase) * stride * 9f * s
        val fx = hx + xStep + if (hx < 0f) -2f * s else 2f * s
        val fy = -3f * s - lift
        val kx = hx + (fx - hx) * 0.48f + if (digi) 6f * s else 0f
        val ky = hy + 17f * s + lift * 0.15f
        p.poly2(hx, hy, kx, ky, fx, fy); p.limb(10f * s, c)
        if (p.hi) { p.poly2(hx, hy, kx, ky, fx, fy); p.cv.drawPath(p.path, p.lp(light(c, 0.18f), 2.6f * s)) }
        p.circle(hx, hy, 6f * s, dark(c, 0.1f))
        p.circle(kx, ky, 5.6f * s, dark(c, 0.15f))
        p.quad(fx - 9f * s, fy + 3f * s, fx - 5f * s, fy - 4f * s, fx + 7f * s, fy - 4f * s, fx + 12f * s, fy + 3f * s); p.shape(foot)
    }

    private fun steamWalker(team: Int, walk: Float, atk: Float) {
        val brass = 0xFFB8883E.toInt()
        val iron = 0xFF55524E.toInt()
        val m = stride > 0.01f
        mechLeg(-6f, -40f, walk + phase + Motion.PI, 1f, dark(iron, 0.25f), dark(brass, 0.3f), false)
        val kick = Motion.recoil(atk)
        cv.save()
        cv.translate(-kick * 3f, kick * 2.5f + if (p.hi) sin(walk * 2f + phase) * stride else 0f)
        // komin
        p.rect(-20f, -98f, -12f, -76f, 0xFF3A3A3E.toInt()); p.rect(-22f, -101f, -10f, -97f, 0xFF2A2A2E.toInt())
        if (m || atk > 0f) for (i in 0..1) { val t = ((time * 0.9f + i * 0.5f) % 1f); p.glow(-16f - t * 8f, -104f - t * 20f, 3f + t * 7f, ((0x90 * (1f - t)).toInt() shl 24) or 0x605A54) }
        // kocioł (tułów)
        p.rr(-22f, -82f, 22f, -40f, 10f, brass)
        if (p.hi) {
            p.rrF(-22f, -52f, 22f, -40f, 8f, dark(brass, 0.25f))
            p.segF(-18f, -78f, 14f, -78f, 1.6f, light(brass, 0.4f))
            p.segF(-22f, -62f, 22f, -62f, 1.4f, dark(brass, 0.4f))
            p.rivets(-18f, -58f, 18f, 6, 0.9f, dark(brass, 0.5f))
        }
        p.rect(-20f, -72f, -2f, -63f, team) // płyta drużyny
        // kabina z pilotem
        p.circle(10f, -70f, 7f, 0xFF3A3A3E.toInt())
        p.circleF(10f, -70f, 5f, GLASS)
        if (p.hi) { p.circleF(9f, -70f, 2.6f, 0xFFE2B088.toInt()); p.circleF(11f, -73f, 1.4f, 0xFFFFFFFF.toInt()) }
        // manometr
        if (p.hi) { p.circle(-11f, -50f, 3.4f, 0xFFF0EAD8.toInt()); p.segF(-11f, -50f, -9f, -52f, 0.8f, 0xFFB02A20.toInt()) }
        // karabin maszynowy
        val rec = kick * 4f
        p.rect(16f - rec, -60f, 30f - rec, -52f, iron)
        p.seg(28f - rec, -56f, 46f - rec, -56f, 2.6f, 0xFF2A2A2E.toInt())
        if (p.hi) for (i in 0..2) p.segF(18f - rec + i * 4f, -59f, 18f - rec + i * 4f, -53f, 0.8f, dark(iron, 0.4f))
        muzzle(48f - rec, -56f, atk, 0.9f)
        cv.restore()
        mechLeg(6f, -40f, walk + phase, 1f, iron, dark(brass, 0.1f), false)
    }

    private fun battleMech(team: Int, walk: Float, atk: Float) {
        val metal = 0xFF6C7682.toInt()
        val md = dark(metal, 0.3f)
        mechLeg(-6f, -42f, walk + phase + Motion.PI, 1f, md, dark(metal, 0.4f), true)
        val kick = Motion.recoil(atk)
        cv.save()
        cv.translate(-kick * 3.5f, kick * 2.5f + if (p.hi) sin(walk * 2f + phase) * stride else 0f)
        // wyrzutnia rakiet na plecach
        p.rr(-24f, -92f, -4f, -74f, 2f, dark(metal, 0.15f))
        if (p.hi) for (i in 0..1) for (j in 0..1) p.circle(-19f + i * 9f, -87f + j * 8f, 2.4f, if (atk > 0.3f && i + j == 0) 0xFFFF9030.toInt() else 0xFF2A2A2E.toInt())
        // tułów kanciasty
        val pa = p.path; pa.reset()
        pa.moveTo(-20f, -44f); pa.lineTo(-22f, -76f); pa.lineTo(8f, -82f); pa.lineTo(22f, -70f); pa.lineTo(18f, -48f); pa.lineTo(6f, -40f); pa.close()
        p.shape(metal)
        if (p.hi) {
            p.quad(-20f, -44f, -21f, -54f, 19f, -54f, 18f, -48f); p.shapeF(md)
            p.segF(-20f, -74f, 6f, -79f, 1.4f, light(metal, 0.3f))
        }
        p.quad(-16f, -72f, 6f, -76f, 8f, -58f, -16f, -58f); p.shape(team) // pancerz drużyny
        if (p.hi) p.segF(-14f, -66f, 6f, -68f, 1f, dark(team, 0.3f))
        // głowa-sensor
        p.rr(6f, -88f, 22f, -78f, 3f, md)
        p.glow(17f, -83f, 2.6f, GLOW)
        // ramię z działem
        val rec = kick * 4f
        p.rr(10f - rec, -62f, 34f - rec, -52f, 3f, md)
        p.seg(32f - rec, -57f, 48f - rec, -57f, 3f, 0xFF2A2E34.toInt())
        if (p.hi) p.rect(18f - rec, -64f, 24f - rec, -50f, team)
        muzzle(50f - rec, -57f, atk, 1f)
        cv.restore()
        mechLeg(6f, -42f, walk + phase, 1f, metal, dark(metal, 0.2f), true)
    }

    private fun titan(team: Int, walk: Float, atk: Float) {
        val s = 1.22f
        val armor = 0xFFDADDE8.toInt()
        val frame = 0xFF5A5480.toInt()
        mechLeg(-8f * s, -42f * s, walk + phase + Motion.PI, s, dark(frame, 0.2f), dark(armor, 0.35f), false)
        val kick = Motion.recoil(atk)
        cv.save()
        cv.translate(-kick * 3f, kick * 3f + if (p.hi) sin(walk * 2f + phase) * stride else 0f)
        // pierścień tarczy energetycznej
        val pulse = 0.5f + 0.5f * sin(time * 3f + phase)
        p.r.set(-36f * s, -110f * s, 36f * s, -26f * s)
        cv.drawOval(p.r, p.glp((((60 + 60 * pulse).toInt()) shl 24) or 0x78F0FF, 2.6f))
        // tułów
        val pa = p.path; pa.reset()
        pa.moveTo(-22f * s, -44f * s); pa.lineTo(-26f * s, -80f * s); pa.lineTo(-8f * s, -90f * s); pa.lineTo(14f * s, -88f * s); pa.lineTo(26f * s, -72f * s); pa.lineTo(20f * s, -46f * s); pa.close()
        p.shape(armor)
        if (p.hi) {
            p.quad(-22f * s, -44f * s, -23f * s, -54f * s, 21f * s, -54f * s, 20f * s, -46f * s); p.shapeF(dark(armor, 0.22f))
            p.segF(-22f * s, -80f * s, 10f * s, -86f * s, 1.6f, 0xFFFFFFFF.toInt())
        }
        p.quad(-18f * s, -78f * s, 8f * s, -82f * s, 12f * s, -58f * s, -18f * s, -58f * s); p.shape(team)
        if (p.hi) { p.segF(-17f * s, -68f * s, 10f * s, -70f * s, 1.2f, light(team, 0.5f)); p.segF(-3f * s, -80f * s, -3f * s, -58f * s, 1f, dark(team, 0.3f)) }
        // rdzeń
        p.circle(14f * s, -66f * s, 5f * s, frame)
        p.glow(14f * s, -66f * s, (3f + pulse) * s, GLOW)
        // głowa
        p.rr(4f * s, -98f * s, 20f * s, -86f * s, 4f * s, armor)
        cv.drawLine(9f * s, -92f * s, 19f * s, -92f * s, p.glp(GLOW, 2.4f))
        // naramiennik + działo szynowe
        val rec = kick * 5f
        p.rr(-30f * s, -90f * s, -10f * s, -74f * s, 5f * s, team)
        p.rr(8f * s - rec, -64f * s, 40f * s - rec, -52f * s, 3f * s, frame)
        p.seg(38f * s - rec, -61f * s, 58f * s - rec, -61f * s, 2f, 0xFF8A86B0.toInt())
        p.seg(38f * s - rec, -55f * s, 58f * s - rec, -55f * s, 2f, 0xFF8A86B0.toInt())
        cv.drawLine(12f * s - rec, -58f * s, 56f * s - rec, -58f * s, p.glp(GLOW, if (atk > 0.3f) 3f else 1.6f))
        if (atk > 0.7f) { p.glow(60f * s, -58f * s, 10f, 0xC080F0FF.toInt()); p.glow(60f * s, -58f * s, 5f, 0xF0FFFFFF.toInt()) }
        cv.restore()
        mechLeg(8f * s, -42f * s, walk + phase, s, frame, armor, false)
    }

    // ================================================================== poduszkowiec

    fun hover(team: Int, atk: Float) {
        val bob = sin(time * 4f + phase) * 2.5f
        val hull = 0xFF6A6290.toInt()
        val armor = 0xFFDADDE8.toInt()
        // poduszka energii
        val pulse = 0.6f + 0.4f * sin(time * 9f + phase * 1.7f)
        p.r.set(-40f, -10f, 40f, 4f); cv.drawOval(p.r, p.gp((((90 * pulse).toInt()) shl 24) or 0x78DCFF))
        p.r.set(-28f, -7f, 28f, 1f); cv.drawOval(p.r, p.gp((((150 * pulse).toInt()) shl 24) or 0xB0F4FF))
        cv.save(); cv.translate(0f, bob)
        // kadłub
        val pa = p.path; pa.reset()
        pa.moveTo(-44f, -14f); pa.lineTo(-38f, -34f); pa.lineTo(26f, -36f); pa.lineTo(48f, -20f); pa.lineTo(40f, -12f); pa.close()
        p.shape(armor)
        if (p.hi) {
            p.quad(-44f, -14f, -42f, -20f, 46f, -20f, 40f, -12f); p.shapeF(hull)
            p.segF(-36f, -32f, 24f, -34f, 1.4f, 0xFFFFFFFF.toInt())
        }
        p.quad(-34f, -31f, 14f, -32f, 18f, -22f, -36f, -22f); p.shape(team)
        // dysze
        for (i in 0..2) { val x = -30f + i * 26f; p.rr(x - 6f, -14f, x + 6f, -9f, 2f, 0xFF3A3658.toInt()); p.glow(x, -8f, 2.6f, GLOW) }
        // wieża z działem plazmowym
        val rec = Motion.recoil(atk) * 4f
        p.rr(10f - rec, -48f, 44f - rec, -41f, 3f, hull)
        cv.drawLine(14f - rec, -44.5f, 42f - rec, -44.5f, p.glp(GLOW, 1.6f))
        p.rr(-16f, -52f, 18f, -35f, 8f, armor)
        if (p.hi) { p.rr(0f, -49f, 14f, -43f, 2f, 0xFF20264A.toInt()); p.rectF(-12f, -46f, -2f, -42f, team) }
        if (atk > 0.6f) { p.glow(48f, -44.5f, 9f, 0xC080FFFF.toInt()); p.glow(48f, -44.5f, 4f, 0xF0FFFFFF.toInt()) }
        cv.restore()
    }
}
