package com.centurywar.game.art

import android.graphics.Canvas
import com.centurywar.game.Weapon
import com.centurywar.game.art.Pen.Companion.dark
import com.centurywar.game.art.Pen.Companion.light
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Piechota wszystkich 8 epok: anatomia (uda, golenie, buty, zwężony tułów, ręce z dłońmi,
 * głowa z okiem, brwią, ustami i uchem), dwutonowe cieniowanie oraz nakrycia głowy,
 * pancerze i broń charakterystyczne dla epoki. Barwa drużyny zawsze dominuje (tunika/płaszcz).
 *
 * Układ lokalny: stopy na y = 0, postać patrzy w stronę +x, wysokość ok. 66 jednostek.
 */
class Soldiers(private val p: Pen) {

    private val cv: Canvas get() = p.cv

    // Paleta epok
    private val skin = intArrayOf(0xFFC48A5E.toInt(), 0xFFD49C6C.toInt(), 0xFFE8B990.toInt(), 0xFFEBC09A.toInt(), 0xFFE2B088.toInt(), 0xFFE0AE84.toInt(), 0xFFDCA67C.toInt(), 0xFFE0B088.toInt())
    private val hair = intArrayOf(0xFF3A2414.toInt(), 0xFF2A1A10.toInt(), 0xFF6A4424.toInt(), 0xFFF2EEE4.toInt(), 0xFF4A3020.toInt(), 0xFF5A3C22.toInt(), 0xFF2A2420.toInt(), 0xFF2A2420.toInt())
    private val pants = intArrayOf(0xFFC48A5E.toInt(), 0xFFD49C6C.toInt(), 0xFF6A5038.toInt(), 0xFFECE6D4.toInt(), 0xFF7A6446.toInt(), 0xFF626A48.toInt(), 0xFF3C434C.toInt(), 0xFFC4C8D6.toInt())
    private val boots = intArrayOf(0xFF7A5434.toInt(), 0xFF8A6438.toInt(), 0xFF4A3424.toInt(), 0xFF26242A.toInt(), 0xFF4A3222.toInt(), 0xFF3E3428.toInt(), 0xFF26282C.toInt(), 0xFF8C90A4.toInt())

    private val BRONZE = 0xFFC8923A.toInt()
    private val STEEL = 0xFFB4BCC6.toInt()
    private val MAIL = 0xFF9AA2AC.toInt()
    private val WOOD = 0xFF7A5232.toInt()
    private val WOOD_D = 0xFF5A3A22.toInt()
    private val GUNMETAL = 0xFF3A3E46.toInt()
    private val LEATHER = 0xFF6E4A2C.toInt()
    private val OLIVE = 0xFF5E6646.toInt()
    private val GOLD = 0xFFE8C04A.toInt()
    private val GLOW = 0xFF7FF4FF.toInt()
    private val FLASH = 0xE0FFE27A.toInt()
    private val ARMOR = 0xFFD2D6E2.toInt()

    // Kształty (punkty względem środka głowy, promień głowy 8) – stałe tablice, zero alokacji.
    private val hairWild = floatArrayOf(6f, -5f, 4f, -9.5f, 0f, -11f, -4.5f, -10.5f, -8.5f, -8f, -10.8f, -3f, -9.8f, 1f, -11.5f, 4.5f, -8f, 4.5f, -9f, 8.5f, -5.5f, 6.5f, -3.8f, 1.5f, -2.5f, -2.5f, 2.5f, -4.6f)
    private val beard = floatArrayOf(7.6f, 1.6f, 8f, 6.5f, 5f, 11f, 0.5f, 11.5f, -3.5f, 8f, -3.2f, 3.5f, -0.5f, 5f, 3.5f, 4.6f)
    private val crestHelm = floatArrayOf(8.6f, -1.5f, 7.5f, -7f, 3f, -10.5f, -3f, -10.8f, -8.2f, -7f, -9.4f, 0f, -8.6f, 7f, -4f, 7.5f, -3.6f, 2.5f, -1f, 0f, 3.5f, -0.5f, 4.5f, 3.5f, 8.8f, 3.6f)
    private val crest = floatArrayOf(4f, -10.2f, 2f, -15.5f, -3f, -17.5f, -9.5f, -15.5f, -13.5f, -10f, -10f, -8.5f, -6.5f, -11.5f, -1f, -12.5f)
    private val coif = floatArrayOf(5.5f, -6.5f, 2f, -9.6f, -4f, -9.6f, -8.8f, -5f, -9.8f, 2f, -8.6f, 9f, -3f, 11f, 5f, 10f, 6f, 6.5f, 1.5f, 5f, 1f, -3.5f, 5.5f, -4f)
    private val nasal = floatArrayOf(9f, -3.5f, 5f, -10f, 0f, -14.5f, -5f, -10f, -9.6f, -3.5f)
    private val tricorn = floatArrayOf(-11.5f, -3.5f, -9.5f, -9.5f, -4.5f, -8f, 0.5f, -13.5f, 5f, -8.5f, 10.5f, -10f, 12.5f, -4f, 0.5f, -6f)
    private val morion = floatArrayOf(-13f, -1.5f, -9f, -5f, -7f, -9f, -3f, -11f, 3f, -11f, 7f, -8.5f, 9f, -5f, 13f, -2f, 8f, -3.5f, -8f, -3.5f)
    private val combMorion = floatArrayOf(-6f, -9.5f, -4f, -15f, 3f, -15.5f, 6f, -9f)
    private val capCrown = floatArrayOf(-9f, -3.5f, -9.5f, -9f, -6f, -12f, 5f, -12.5f, 8.5f, -9.5f, 7.5f, -3.5f)
    private val visor = floatArrayOf(5f, -4.5f, 13f, -3f, 12.5f, -1.8f, 5f, -2.4f)
    private val brodie = floatArrayOf(-12f, -2.5f, -8f, -8f, -3f, -11.5f, 3.5f, -11.5f, 8.5f, -8f, 12.5f, -2.5f, 8f, -3.6f, -8f, -3.6f)
    private val tacHelm = floatArrayOf(-9.5f, 1f, -10f, -5f, -6f, -10.5f, 1f, -12f, 7f, -9f, 9.8f, -3.5f, 9.6f, -1.5f, 4f, -3.2f, -3f, -2f, -4f, 2f)
    private val orbHelm = floatArrayOf(-9.5f, 5f, -10.5f, -3f, -7.5f, -10f, 0f, -12.5f, 7f, -10f, 10.5f, -3f, 11f, 3.5f, 8f, 8f, 1f, 9.5f, -5f, 9f)

    /** Buduje [Pen.path] z par (x, y) przesuniętych o (ox, oy). */
    private fun pts(a: FloatArray, ox: Float, oy: Float, close: Boolean = true) {
        val pa = p.path
        pa.reset(); pa.moveTo(ox + a[0], oy + a[1])
        var i = 2
        while (i < a.size) { pa.lineTo(ox + a[i], oy + a[i + 1]); i += 2 }
        if (close) pa.close()
    }

    /**
     * @param walk faza chodu (0 = stoi), @param atk 1..0 animacja ataku,
     * @param mounted jeździec (nogi zgięte w siodle), @param heavy osiłek epoki plemion.
     */
    fun draw(era: Int, ranged: Boolean, weapon: Int, team: Int, walk: Float, atk: Float, mounted: Boolean, heavy: Boolean) {
        val sk = skin[era]
        val teamD = dark(team, 0.28f)
        val sleeve = when (era) { 0, 1 -> sk; 2 -> MAIL; 7 -> ARMOR; else -> team }
        val hand = when (era) { 6 -> 0xFF30343A.toInt(); 7 -> 0xFFA8AEC0.toInt(); else -> sk }
        val gun = isGun(weapon)
        val spear = weapon == Weapon.SPEAR || weapon == Weapon.PIKE

        // ---- tylna noga
        if (mounted) mountedLeg(-2f, era, true) else leg(walk + Math.PI.toFloat(), era, true, walk != 0f)

        // ---- tylna ręka
        val swing = when {
            gun -> -0.05f + atk * 0.06f
            spear -> -0.05f
            weapon == Weapon.BOW -> 0f
            weapon == Weapon.SLING -> if (atk > 0f) -2.6f + (1f - atk) * 3.2f else 0.5f
            atk > 0f -> -atk * 1.6f + 0.7f
            else -> 0.2f + sin(walk) * 0.08f
        }
        val thrust = if (spear && atk > 0f) sin(atk * 3.1416f) * 9f else 0f
        val recoil = if (gun) atk * atk * 2.5f else 0f
        val sleeveB = dark(sleeve, 0.22f)
        val handB = dark(hand, 0.15f)
        when {
            gun || spear -> {
                cv.save(); cv.translate(-1f + thrust - recoil, -45f); cv.rotate(deg(swing))
                val gx = if (spear) 17f else if (weapon == Weapon.LAUNCHER) 14f else 20f
                val gy = if (weapon == Weapon.LAUNCHER) -2f else 3f
                p.poly2(0f, 0f, gx * 0.5f, 7f, gx, gy); p.limb(4.4f, sleeveB); p.circle(gx, gy, 2.3f, handB)
                cv.restore()
            }
            weapon == Weapon.BOW -> {
                p.poly2(-1f, -46f, 7f, -46.5f, 16f, -46f); p.limb(4.4f, sleeveB); p.circle(16f, -46f, 2.3f, handB)
            }
            else -> {
                val sw = sin(walk) * 4f
                p.poly2(-1f, -46f, -3f - sw, -37f, -1f - sw * 1.5f, -30f); p.limb(4.4f, sleeveB); p.circle(-1f - sw * 1.5f, -30f, 2.3f, handB)
            }
        }

        // ---- przednia noga
        if (mounted) mountedLeg(2f, era, false) else leg(walk, era, false, walk != 0f)

        // ---- spódnica / poły
        skirt(era, team, teamD)

        // ---- tułów
        torso(era, team, teamD, heavy)

        // ---- głowa
        head(era, ranged, heavy, sk)

        // ---- tarcza (przed tułowiem, za przednią ręką)
        if (!ranged && !mounted && weapon == Weapon.SPEAR && era == 1) aspis(team)
        if (!ranged && !mounted && weapon == Weapon.SWORD && era == 2) kite(team)

        // ---- przednia ręka z bronią
        cv.save()
        cv.translate(1.5f + thrust - recoil, -46f)
        if (weapon != Weapon.BOW) cv.rotate(deg(swing))
        if (weapon == Weapon.BOW) {
            // łuk w tylnej ręce, cięciwa ciągnięta przednią
            val pull = if (atk > 0f) 0.35f + (1f - atk) * 0.6f else 0.25f
            bow(14.5f, 0f, pull)
            val hx = 14.5f - 6f - pull * 10f
            p.poly2(0f, 0f, (hx - 1f) * 0.5f - 3f, 2.5f, hx, 0f); p.limb(4.6f, sleeve); p.circle(hx, 0f, 2.4f, hand)
        } else {
            weapon(weapon, era, atk, heavy)
            val hx = if (gun) 7f else if (spear) 7f else 11f
            val hy = when { weapon == Weapon.LAUNCHER -> -1f; gun -> 4f; spear -> 4.5f; weapon == Weapon.SLING -> 2f; else -> 3.5f }
            p.poly2(0f, 0f, hx * 0.35f, 7.5f, hx, hy); p.limb(if (heavy) 5.6f else 4.6f, sleeve)
            if (era == 7 && p.hi) p.circle(0.5f, 1f, 4.6f, team) // naramiennik pancerza
            if ((era == 3 || era == 4) && p.hi) p.segF(hx * 0.8f, hy + 0.8f, hx * 0.92f, hy + 0.3f, 4.2f, if (era == 3) 0xFFECE6D4.toInt() else teamD) // mankiet
            p.circle(hx, hy, if (heavy) 2.9f else 2.4f, hand)
        }
        cv.restore()
    }

    /** Drobne naszywki i okucia: odróżniają stopnie bez zmiany sylwetki. */
    fun tierMarks(tier: Int, team: Int) {
        if (tier < 2) return
        val metal = 0xFFE8C04A.toInt()
        p.rr(-12f, -49f, -7f, -43f, 1.5f, metal)
        p.rr(7f, -49f, 12f, -43f, 1.5f, metal)
        if (tier >= 3) {
            p.segF(-8f, -53f, 8f, -53f, 1.5f, metal)
            p.circle(0f, -40f, 4.5f, metal)
            p.circleF(0f, -40f, 2.2f, team)
        }
    }

    private fun deg(rad: Float) = rad * 57.29578f

    fun isGun(w: Int) = w == Weapon.MUSKET || w == Weapon.RIFLE || w == Weapon.MG || w == Weapon.LAUNCHER ||
        w == Weapon.BLASTER || w == Weapon.RAIL || w == Weapon.CROSSBOW

    // ------------------------------------------------------------------ nogi

    private fun leg(phase: Float, era: Int, back: Boolean, moving: Boolean) {
        val th: Float
        val lift: Float
        if (moving) {
            th = sin(phase) * 0.5f
            lift = max(0f, cos(phase))
        } else {
            th = if (back) -0.2f else 0.2f; lift = 0f
        }
        val hx = if (back) -1.5f else 1.5f
        val hy = -27f
        val kx = hx + sin(th) * 13.5f
        val ky = hy + cos(th) * 13.5f
        val sa = th - 0.12f - lift * 0.75f
        val fx = kx + sin(sa) * 13f
        val fy = ky + cos(sa) * 13f - 0.5f
        val shadeT = if (back) 0.25f else 0f
        val thigh = dark(pants[era], shadeT)
        val ex = kx + (fx - kx) * 0.8f
        val ey = ky + (fy - ky) * 0.8f
        p.poly2(hx, hy, kx, ky, ex, ey); p.limb(5.8f, thigh)
        // golenie: nagolenniki / getry / buty z cholewą
        val shin = when (era) { 1 -> BRONZE; 3 -> 0xFF2A2A30.toInt(); 4, 5 -> boots[era]; 7 -> pants[era]; else -> 0 }
        if (shin != 0 && p.hi) {
            val t0 = if (era == 1 || era == 3) 0.15f else 0.55f
            p.segF(kx + (fx - kx) * t0, ky + (fy - ky) * t0, ex, ey, 5.4f, dark(shin, shadeT))
            if (era == 5) p.segF(kx + (fx - kx) * 0.55f, ky + (fy - ky) * 0.55f, kx + (fx - kx) * 0.6f, ky + (fy - ky) * 0.6f, 6f, dark(OLIVE, shadeT + 0.1f))
        }
        if (p.hi && (era == 6 || era == 7)) p.circle(kx + 0.6f, ky, 3.2f, dark(if (era == 6) 0xFF24272C.toInt() else 0xFF9AA0B4.toInt(), shadeT))
        if (era == 0 && p.hi) p.segF(fx - 0.5f, fy - 4.5f, fx + 0.5f, fy - 2.5f, 6.4f, dark(0xFF9A7448.toInt(), shadeT)) // owijacze z futra
        // but
        val bc = dark(boots[era], shadeT)
        val big = era == 7
        p.rr(fx - 4.2f, fy - (if (big) 5.5f else 4.6f), fx + (if (big) 7f else 6.4f), fy + 1.2f, 1.8f, bc)
    }

    private fun mountedLeg(dx: Float, era: Int, back: Boolean) {
        val shadeT = if (back) 0.25f else 0f
        val c = dark(pants[era], shadeT)
        p.poly2(dx, -27f, dx + 11f, -25f, dx + 9f, -13f); p.limb(5.8f, c)
        p.rr(dx + 6f, -16f, dx + 15f, -11.5f, 1.8f, dark(boots[era], shadeT))
    }

    // ------------------------------------------------------------------ tułów

    private fun skirt(era: Int, team: Int, teamD: Int) {
        when (era) {
            0 -> { // futrzana przepaska z poszarpanym brzegiem
                val pa = p.path; pa.reset(); pa.moveTo(-7.5f, -30f); pa.lineTo(7.5f, -30f); pa.lineTo(7.5f, -20f)
                pa.lineTo(4.5f, -22.5f); pa.lineTo(2f, -18.5f); pa.lineTo(-1f, -22f); pa.lineTo(-3.5f, -18.5f); pa.lineTo(-5.5f, -22f); pa.lineTo(-8f, -19f); pa.close()
                p.shape(0xFF9A7448.toInt())
                if (p.hi) { p.circleF(-3f, -25f, 1.1f, 0xFF6E4E2C.toInt()); p.circleF(3.5f, -24f, 1.1f, 0xFF6E4E2C.toInt()) }
            }
            1 -> { // lniane pteruges
                p.rect(-7.5f, -30f, 7.5f, -18.5f, 0xFFEDE4CC.toInt())
                if (p.hi) for (i in 1..3) p.segF(-7.5f + i * 3.75f, -28f, -7.5f + i * 3.75f, -18.5f, 0.9f, 0xFF9A8E70.toInt())
            }
            2 -> { // kolczuga wystająca spod opończy
                p.rect(-7.5f, -30f, 7.5f, -19.5f, MAIL)
                p.quad(-7.5f, -30f, 7.8f, -30f, 8.5f, -21.5f, -8f, -21.5f); p.shape(team)
                if (p.hi) p.segF(0.5f, -28f, 0.5f, -21.5f, 1f, teamD)
            }
            3 -> { // poły płaszcza
                p.quad(-7.5f, -31f, 3f, -31f, -2f, -17f, -10.5f, -18.5f); p.shape(teamD)
            }
            7 -> { // płyty biodrowe
                p.rr(-7.5f, -30f, 7.5f, -22f, 2f, 0xFFA8AEC0.toInt())
                if (p.hi) p.segF(0f, -30f, 0f, -22.5f, 1f, 0xFF7A8094.toInt())
            }
        }
    }

    private fun torso(era: Int, team: Int, teamD: Int, heavy: Boolean) {
        val pa = p.path2
        val w = if (heavy) 1.18f else 1f
        pa.reset()
        pa.moveTo(-6.8f * w, -50f); pa.lineTo(6.6f * w, -50f); pa.lineTo(8.6f * w, -42f)
        pa.lineTo(if (heavy) 9.5f else 6.8f, -33f); pa.lineTo(6.8f, -26.5f); pa.lineTo(-6.8f, -26.5f); pa.lineTo(-7.8f * w, -40f); pa.close()
        val tc = if (era == 7) team else team
        cv.drawPath(pa, p.fp(tc))
        // cień po stronie pleców + światło na piersi
        if (p.hi) { p.quad(-6.8f * w, -50f, -2f, -50f, -3f, -26.5f, -6.8f, -26.5f); p.shapeF(teamD) }
        if (p.max) { p.quad(3f, -49f, 6.2f * w, -49f, 7.6f * w, -43f, 4f, -43f); p.shapeF(light(team, 0.22f)) }
        when (era) {
            0 -> { // naszyjnik z kłów
                if (p.hi) for (i in 0..3) p.circle(-1f + i * 2.4f, -47.5f + i * 0.8f, 1.1f, 0xFFF2ECDA.toInt())
                p.segF(-6f, -50f, 6f, -26.5f, 1.6f, 0xFF7A5434.toInt())
            }
            1 -> { // brązowy kołnierz (pektorał)
                p.quad(-6.8f, -50f, 6.8f, -50f, 7.8f, -45f, -7.2f, -45f); p.shape(BRONZE)
                if (p.hi) p.segF(-5f, -48.5f, 6f, -48.5f, 1f, light(BRONZE, 0.4f))
            }
            2 -> { // opończa z herbem (krzyż)
                p.quad(-6.8f, -50f, 6.6f, -50f, 6.9f, -47f, -7f, -47f); p.shapeF(MAIL)
                if (p.hi) { p.rectF(1.2f, -45f, 3.6f, -32f, light(team, 0.75f)); p.rectF(-2f, -41f, 7.2f, -38.6f, light(team, 0.75f)) }
            }
            3 -> { // białe pasy krzyżowe, guziki
                p.segF(-5.5f, -49.5f, 6.5f, -28f, 2.4f, 0xFFF4F0E4.toInt())
                if (p.hi) { p.segF(5.5f, -49f, -5f, -29f, 2.4f, 0xFFE6E0D0.toInt()); p.rivets(7f, -46f, 7.2f, 2, 0.9f, GOLD); p.circleF(7.1f, -38f, 0.9f, GOLD) }
            }
            4 -> { // pas na amunicję, mosiężne guziki, kołnierz
                p.segF(-5.5f, -49.5f, 6.5f, -29f, 2.6f, LEATHER)
                if (p.hi) { p.rivets(6.8f, -46f, 6.8f, 2, 0.9f, GOLD); p.circleF(6.6f, -36.5f, 0.9f, GOLD); p.rectF(-3f, -51f, 6.5f, -48.5f, teamD) }
            }
            5 -> { // oporządzenie: szelki i ładownice
                p.segF(2.5f, -50f, 2.5f, -29f, 2f, OLIVE)
                if (p.hi) { p.rect(3.5f, -34f, 7.5f, -29.5f, OLIVE); p.rect(-6f, -34f, -2.5f, -29.5f, dark(OLIVE, 0.2f)) }
            }
            6 -> { // kamizelka taktyczna
                p.rr(-6f, -48.5f, 8f, -29f, 2.5f, Pen.mix(team, 0xFF2A3038.toInt(), 0.62f))
                if (p.hi) {
                    p.rect(1.5f, -38f, 5.5f, -31.5f, 0xFF4A525C.toInt()); p.rect(-4.5f, -38f, 0.5f, -31.5f, 0xFF4A525C.toInt())
                    p.rectF(-3f, -46f, 5f, -43f, team) // naszywka drużyny
                }
            }
            7 -> { // płyty pancerza + rdzeń energii
                if (p.hi) {
                    p.segF(-6f, -42f, 8f, -42f, 1.2f, light(team, 0.55f))
                    p.segF(-6f, -35f, 7f, -35f, 1.2f, teamD)
                }
                p.circle(4.5f, -46f, 2.2f, 0xFF9AA0B4.toInt()); p.glow(4.5f, -46f, 1.3f, GLOW)
            }
        }
        // pas z klamrą
        if (era != 6 && era != 7) {
            p.rect(-7f, -30f, 7.2f, -26.8f, if (era == 3) 0xFFF4F0E4.toInt() else if (era == 5) OLIVE else LEATHER)
            if (p.hi) p.rectF(2f, -29.6f, 4.8f, -27.2f, if (era <= 1) BRONZE else GOLD)
        }
        cv.drawPath(pa, p.op())
    }

    // ------------------------------------------------------------------ głowa

    private fun head(era: Int, ranged: Boolean, heavy: Boolean, sk: Int) {
        val hx = 1.5f
        val hy = -57.5f
        val R = 8f
        // szyja
        p.rectF(-1.5f, -51f, 3.8f, -48.5f, dark(sk, 0.2f))
        if (era == 7) { orbitalHead(hx, hy, R); return }
        if (era == 0) { pts(hairWild, hx, hy); p.shape(hair[0]) } // grzywa za głową
        if (era == 3 && ranged) { p.circle(hx - 8.5f, hy + 1.5f, 2.8f, hair[3]); p.circleF(hx - 7f, hy + 0.5f, 1.4f, 0xFF20202A.toInt()) } // warkoczyk
        p.circle(hx, hy, R, sk)
        // cień policzka z tyłu
        // nos
        if (p.hi) {
            p.circleF(hx + 7.6f, hy + 0.4f, 2f, sk)
            p.r.set(hx + 5.6f, hy - 1.6f, hx + 9.6f, hy + 2.4f); cv.drawArc(p.r, -100f, 210f, false, p.op(p.ow * 0.8f))
        }
        // ucho
        p.r.set(hx - 4.6f, hy - 1.4f, hx - 1.2f, hy + 3.2f); cv.drawArc(p.r, 60f, 240f, false, p.lp(dark(sk, 0.35f), 1.1f))
        // oko, brew, usta
        val eyeC = 0xFF1A1418.toInt()
        p.ovalF(hx + 3.3f, hy - 2.4f, hx + 5.5f, hy + 0.4f, eyeC)
        if (p.max) p.circleF(hx + 4.7f, hy - 1.5f, 0.55f, 0xFFFFFFFF.toInt())
        if (p.hi) {
            p.segF(hx + 2.2f, hy - 4.3f, hx + 6.4f, hy - 3.6f, 1.3f, if (era == 3) 0xFF6A5A4A.toInt() else dark(hair[era], 0.1f))
            p.segF(hx + 4.2f, hy + 4f, hx + 6.8f, hy + 3.5f, 1f, dark(sk, 0.5f))
        }
        when (era) {
            0 -> {
                if (heavy) { pts(beard, hx, hy); p.shape(hair[0]) }
                // grzywka + opaska / kość
                p.tri(hx - 3f, hy - 8f, hx + 6.5f, hy - 6f, hx + 1f, hy - 3.5f); p.shapeF(hair[0])
                if (ranged) { p.seg(hx - 5f, hy - 9f, hx + 3f, hy - 12f, 1.8f, 0xFFF2ECDA.toInt()) }
                else p.seg(hx - 7.5f, hy - 4.5f, hx + 7f, hy - 6f, 2f, 0xFFB0302A.toInt())
            }
            1 -> {
                if (!ranged) {
                    pts(crest, hx, hy); p.shape(0xFFB02A20.toInt())
                    pts(crestHelm, hx, hy); p.shape(BRONZE)
                    if (p.hi) { p.segF(hx - 4f, hy - 8.5f, hx + 3f, hy - 9.6f, 1.4f, light(BRONZE, 0.45f)); p.segF(hx + 3.5f, hy - 0.5f, hx + 8.6f, hy - 1f, 1f, dark(BRONZE, 0.35f)) }
                } else { // opaska i krótka broda
                    p.tri(hx - 7.5f, hy - 3f, hx + 6f, hy - 8.5f, hx - 4f, hy - 9.5f); p.shape(hair[1])
                    p.seg(hx - 7f, hy - 4.5f, hx + 6.5f, hy - 6f, 1.8f, 0xFFEDE4CC.toInt())
                    p.quad(hx + 3f, hy + 4.6f, hx + 8f, hy + 3.2f, hx + 6f, hy + 8.5f, hx + 1f, hy + 7.5f); p.shape(hair[1])
                }
            }
            2 -> {
                pts(coif, hx, hy); p.shape(MAIL)
                if (p.max) for (i in 0..2) p.segF(hx - 7.5f, hy - 4f + i * 4f, hx - 2f, hy - 4f + i * 4f, 0.7f, dark(MAIL, 0.3f))
                if (!ranged) {
                    pts(nasal, hx, hy); p.shape(STEEL)
                    p.seg(hx + 7.4f, hy - 3.5f, hx + 8f, hy + 1f, 1.6f, STEEL)
                    if (p.hi) p.segF(hx - 2f, hy - 11f, hx + 1f, hy - 13f, 1.2f, 0xFFFFFFFF.toInt())
                } else { // kapalin
                    p.r.set(hx - 8f, hy - 14f, hx + 8f, hy - 1f); cv.drawArc(p.r, 180f, 180f, true, p.fp(STEEL)); cv.drawArc(p.r, 180f, 180f, true, p.op())
                    p.rr(hx - 12f, hy - 8.5f, hx + 12.5f, hy - 5.5f, 1.5f, dark(STEEL, 0.1f))
                }
            }
            3 -> {
                if (ranged) {
                    p.tri(hx - 7f, hy - 3f, hx + 5f, hy - 7.5f, hx - 5f, hy - 8f); p.shapeF(hair[3])
                    pts(tricorn, hx, hy); p.shape(0xFF24222A.toInt())
                    if (p.hi) { p.segF(hx - 11f, hy - 4f, hx + 12f, hy - 4.5f, 1.1f, GOLD); p.circle(hx - 4f, hy - 7.5f, 1.4f, 0xFF404050.toInt()) }
                } else {
                    pts(combMorion, hx, hy); p.shape(STEEL)
                    pts(morion, hx, hy); p.shape(STEEL)
                    if (p.hi) p.segF(hx - 5f, hy - 9.5f, hx + 2f, hy - 10f, 1.3f, 0xFFFFFFFF.toInt())
                }
            }
            4 -> {
                // wąsy
                p.quad(hx + 3.5f, hy + 2.6f, hx + 9f, hy + 2f, hx + 9.6f, hy + 4f, hx + 3f, hy + 4.2f); p.shapeF(hair[4])
                val capC = if (ranged) 0xFF5A4A36.toInt() else 0xFF6E5E44.toInt()
                pts(capCrown, hx, hy); p.shape(capC)
                pts(visor, hx, hy); p.shape(0xFF26221E.toInt())
                if (p.hi) p.rectF(hx - 9f, hy - 6f, hx + 7.8f, hy - 4f, dark(capC, 0.3f))
                // gogle mosiężne
                val gy = if (ranged) hy - 1.2f else hy - 8.5f
                val gx = if (ranged) hx + 4.6f else hx + 1.5f
                if (ranged) p.segF(hx - 8f, gy, gx, gy, 1.4f, LEATHER)
                p.circle(gx, gy, 2.6f, 0xFFC8923A.toInt()); p.circleF(gx, gy, 1.5f, if (ranged) 0xFF9ADCF0.toInt() else 0xFF3A6A80.toInt())
            }
            5 -> {
                pts(brodie, hx, hy); p.shape(OLIVE)
                if (p.hi) {
                    p.segF(hx + 6f, hy - 3f, hx + 5f, hy + 6f, 0.9f, 0xFF3A3424.toInt()) // pasek
                    if (p.max) { p.segF(hx - 6f, hy - 8f, hx + 2f, hy - 4f, 0.7f, dark(OLIVE, 0.35f)); p.segF(hx - 2f, hy - 10.5f, hx + 6f, hy - 5f, 0.7f, dark(OLIVE, 0.35f)) }
                    p.segF(hx - 4f, hy - 10f, hx + 2f, hy - 11f, 1.2f, light(OLIVE, 0.35f))
                }
            }
            6 -> {
                pts(tacHelm, hx, hy); p.shape(0xFF2E343C.toInt())
                p.rr(hx + 2.5f, hy - 3.5f, hx + 10.5f, hy - 0.2f, 1.2f, 0xFF1C2A34.toInt())
                p.segF(hx + 3.5f, hy - 2.6f, hx + 9f, hy - 2.6f, 1f, 0xFF60D8FF.toInt())
                if (p.hi) {
                    p.rectF(hx - 6f, hy - 9.5f, hx + 2f, hy - 7.6f, 0xFF4A525C.toInt()) // szyna
                    p.rectF(hx - 8.5f, hy - 5f, hx - 4f, hy - 3f, if (ranged) 0xFFE0E0E0.toInt() else 0xFF4A525C.toInt())
                }
            }
        }
    }

    private fun orbitalHead(hx: Float, hy: Float, R: Float) {
        pts(orbHelm, hx, hy); p.shape(ARMOR)
        if (p.hi) {
            p.segF(hx - 6f, hy - 10.5f, hx + 3f, hy - 11.2f, 2f, 0xFFFFFFFF.toInt())
            p.segF(hx - 8.5f, hy + 2f, hx - 4f, hy + 7.5f, 1.2f, 0xFF8A90A4.toInt())
        }
        p.rr(hx + 1.5f, hy - 3.5f, hx + 11.5f, hy + 1.8f, 2.4f, 0xFF20264A.toInt())
        p.cv.drawLine(hx + 3f, hy - 1f, hx + 10f, hy - 1f, p.glp(GLOW, 2f))
        p.seg(hx - 5f, hy - 9f, hx - 7f, hy - 16f, 1.1f, 0xFF8A90A4.toInt())
        p.glow(hx - 7f, hy - 16.5f, 1.4f, 0xFFFF6A6A.toInt())
    }

    // ------------------------------------------------------------------ tarcze

    private fun aspis(team: Int) {
        p.circle(8.5f, -37f, 11f, BRONZE)
        p.circle(8.5f, -37f, 8.8f, team)
        if (p.hi) {
            p.circleF(8.5f, -37f, 3.4f, light(team, 0.7f))
            p.r.set(1f, -45f, 14f, -32f); cv.drawArc(p.r, 200f, 70f, false, p.lp(light(team, 0.35f), 1.4f))
        }
    }

    private fun kite(team: Int) {
        val pa = p.path; pa.reset()
        pa.moveTo(3f, -48f); pa.quadTo(9.5f, -50f, 16f, -48f); pa.lineTo(15f, -36f); pa.quadTo(13f, -27f, 9.5f, -21f); pa.quadTo(5f, -27f, 4f, -36f); pa.close()
        p.shape(team)
        if (p.hi) {
            p.segF(9.5f, -46.5f, 9.5f, -25f, 2.2f, 0xFFF4EEDC.toInt())
            p.segF(4.8f, -40f, 14.6f, -40f, 2.2f, 0xFFF4EEDC.toInt())
            cv.drawPath(pa, p.lp(light(team, 0.25f), 0.8f))
        }
    }

    // ------------------------------------------------------------------ broń
    // Układ: początek w barku, ręka przy (11, 3.5) dla broni białej, (7, 4) dla palnej.

    private fun weapon(w: Int, era: Int, atk: Float, heavy: Boolean) {
        when (w) {
            Weapon.CLUB -> {
                val s = if (heavy) 1.35f else 1f
                val pa = p.path; pa.reset()
                pa.moveTo(9.5f, 6f); pa.lineTo(12.5f, 6.5f); pa.lineTo(16f * s, -18f * s); pa.quadTo(13f * s, -24f * s, 8.5f * s, -19f * s); pa.close()
                p.shape(WOOD)
                if (p.hi) { p.segF(12f, 2f, 10.5f * s, -15f * s, 1.2f, light(WOOD, 0.3f)); p.circleF(12.5f * s, -8f * s, 1f, WOOD_D) }
                if (heavy) for (i in 0..2) { // kamienne kolce
                    val yy = -12f * s - i * 4f
                    p.tri(15f * s, yy, 19.5f * s, yy - 1.5f, 15.5f * s, yy - 3f); p.shape(0xFFE8E0CC.toInt())
                }
                p.seg(10f, 3f, 12.5f, 4f, 2.5f, LEATHER)
            }
            Weapon.SLING -> {
                p.seg(11f, 2f, 18f, -8f, 0.9f, 0xFF9A7A50.toInt())
                p.seg(11f, 2f, 20f, -5f, 0.9f, 0xFF9A7A50.toInt())
                p.circle(20f, -8f, 2.8f, 0xFF8E8E8E.toInt())
            }
            Weapon.SPEAR, Weapon.PIKE -> {
                val len = if (w == Weapon.PIKE) 50f else if (era <= 1) 34f else 38f
                p.seg(-16f, 6.5f, len, 2f, 2.4f, WOOD)
                val head = if (era == 0) 0xFF6E6A66.toInt() else if (era == 1) BRONZE else STEEL
                val pa = p.path; pa.reset()
                pa.moveTo(len - 1f, -1.2f); pa.quadTo(len + 5f, -1.5f, len + 11f, 1.6f); pa.quadTo(len + 5f, 5f, len - 1f, 5f); pa.close()
                p.shape(head)
                if (p.hi) {
                    p.segF(len, 1.8f, len + 8f, 1.6f, 0.9f, light(head, 0.5f))
                    p.seg(len - 3f, 2.4f, len - 0.5f, 2.2f, 3f, if (era == 0) 0xFFB89A66.toInt() else dark(head, 0.25f))
                }
                if (w == Weapon.PIKE && p.hi) p.segF(len - 10f, 3f, len - 4f, 2.6f, 2.8f, 0xFFB02A20.toInt()) // proporczyk
            }
            Weapon.SWORD -> {
                val pa = p.path; pa.reset()
                pa.moveTo(12f, -1f); pa.lineTo(14.5f, -1.2f); pa.lineTo(16.5f, -26f); pa.lineTo(14f, -29f); pa.lineTo(11.6f, -25.5f); pa.close()
                p.shape(0xFFDCE2EA.toInt())
                if (p.hi) p.segF(13.5f, -3f, 14.4f, -25f, 0.9f, 0xFFFFFFFF.toInt())
                p.seg(8f, -0.5f, 18.5f, -1.5f, 2f, if (era == 2) STEEL else GOLD) // jelec
                p.seg(12.5f, 1f, 12.8f, 6.5f, 2.6f, LEATHER)
                p.circle(12.9f, 7.5f, 1.6f, GOLD)
            }
            Weapon.CROSSBOW -> {
                p.seg(-3f, 4f, 28f, 2.5f, 3.2f, WOOD)
                p.r.set(22f, -8f, 30f, 13f); cv.drawArc(p.r, -90f, 180f, false, p.op(2.6f + p.ow * 2f)); cv.drawArc(p.r, -90f, 180f, false, p.lp(STEEL, 2.6f))
                cv.drawLine(26f, -8f, 20f, 2.8f, p.lp(0xFFE8E4D8.toInt(), 0.8f)); cv.drawLine(26f, 13f, 20f, 2.8f, line())
                p.seg(6f, 6f, 5.5f, 8f, 1.2f, GUNMETAL)
                if (atk > 0f) p.seg(18f, 1.5f, 30f, 1.4f, 1.2f, WOOD_D)
            }
            Weapon.MUSKET -> {
                gunBody(-5f, 41f, WOOD, 0xFF4A4A52.toInt(), 3.2f, 2f)
                if (p.hi) { p.rect(5f, 0.5f, 9f, 3.5f, GUNMETAL); p.rivets(18f, 2.2f, 34f, 3, 0.8f, GOLD) }
                flash(atk, 43f, 2f, 1f)
            }
            Weapon.RIFLE -> when (era) {
                6 -> { // karabinek szturmowy
                    p.rr(-4f, 0.5f, 24f, 5.5f, 1.2f, 0xFF2C3036.toInt())
                    p.seg(24f, 2.5f, 34f, 2.5f, 1.8f, 0xFF2C3036.toInt())
                    p.rect(11f, 5f, 15f, 11f, 0xFF3C4148.toInt()) // magazynek
                    p.rr(6f, -3.5f, 16f, 0.5f, 1f, 0xFF1E2226.toInt()) // celownik
                    if (p.hi) p.glow(15.6f, -1.5f, 0.9f, 0xFFFF4040.toInt())
                    flash(atk, 36f, 2.5f, 0.8f)
                }
                else -> {
                    gunBody(-5f, 38f, if (era == 4) 0xFF8A5A30.toInt() else WOOD, GUNMETAL, 3f, 1.8f)
                    if (p.hi) {
                        p.seg(9f, 0f, 12f, -1.5f, 1f, GUNMETAL) // zamek
                        if (era == 4) { p.rr(8f, -3.4f, 20f, -0.6f, 1f, 0xFFB8862E.toInt()) } // luneta mosiężna
                    }
                    flash(atk, 40f, 2f, 1f)
                }
            }
            Weapon.MG -> {
                p.rr(-4f, 0f, 26f, 6f, 1.5f, 0xFF2E3228.toInt())
                p.seg(26f, 3f, 40f, 3f, 2.2f, GUNMETAL)
                if (p.hi) {
                    p.rr(8f, -6f, 18f, 0.5f, 2f, dark(OLIVE, 0.1f)) // magazynek talerzowy
                    p.seg(32f, 4f, 30f, 11f, 1f, GUNMETAL); p.seg(32f, 4f, 35f, 11f, 1f, GUNMETAL) // dwójnóg
                    p.seg(-4f, 3f, -8f, 6f, 3f, WOOD)
                }
                flash(atk, 42f, 3f, 1.1f)
            }
            Weapon.LAUNCHER -> {
                p.rr(-16f, -5.5f, 26f, 1f, 3f, 0xFF4E5A3A.toInt())
                if (p.hi) {
                    p.rect(-17f, -6.5f, -12f, 2f, dark(0xFF4E5A3A.toInt(), 0.3f))
                    p.rect(22f, -6.5f, 27f, 2f, dark(0xFF4E5A3A.toInt(), 0.3f))
                    p.rr(2f, -10f, 8f, -5.5f, 1f, 0xFF2A2E26.toInt())
                    p.rect(4f, -1f, 7f, 4f, 0xFF2A2E26.toInt())
                }
                if (atk <= 0.1f) { p.tri(27f, -5f, 32f, -2.2f, 27f, 0.5f); p.shape(0xFFD0D0C8.toInt()) }
            }
            Weapon.BLASTER -> {
                val pa = p.path; pa.reset()
                pa.moveTo(-3f, 1f); pa.lineTo(22f, -1.5f); pa.lineTo(30f, 1.5f); pa.lineTo(30f, 4.5f); pa.lineTo(12f, 6f); pa.lineTo(8f, 10f); pa.lineTo(4f, 10f); pa.lineTo(4f, 6f); pa.lineTo(-3f, 6f); pa.close()
                p.shape(0xFFE6E8F2.toInt())
                p.rect(12f, 0.5f, 24f, 3.8f, 0xFF5A5080.toInt())
                if (p.hi) { p.cv.drawLine(13.5f, 2.1f, 22.5f, 2.1f, p.glp(GLOW, 1.4f)) }
                p.glow(31f, 3f, if (atk > 0.5f) 5f else 2f, GLOW)
            }
            Weapon.RAIL -> {
                p.rr(-5f, 0f, 16f, 6.5f, 2f, 0xFFE6E8F2.toInt())
                p.seg(14f, 1f, 48f, 1f, 1.8f, 0xFF6A6A90.toInt())
                p.seg(14f, 5f, 48f, 5f, 1.8f, 0xFF6A6A90.toInt())
                p.cv.drawLine(16f, 3f, 46f, 3f, p.glp(GLOW, if (atk > 0.3f) 2.2f else 1.1f))
                if (p.hi) { p.rect(4f, -4f, 12f, 0f, 0xFF3A3A5A.toInt()); p.rect(30f, -0.5f, 33f, 6.5f, 0xFF4A4A70.toInt()) }
                if (atk > 0.6f) p.glow(50f, 3f, 5f, 0xC0B0FFFF.toInt())
            }
        }
    }

    private fun line() = p.line

    private fun gunBody(x0: Float, x1: Float, wood: Int, metal: Int, stockH: Float, barrelW: Float) {
        // kolba + łoże drewniane, lufa stalowa
        val pa = p.path; pa.reset()
        pa.moveTo(x0, 2f); pa.lineTo(x0 + 9f, 1f); pa.lineTo(x1 - 12f, 1.2f); pa.lineTo(x1 - 12f, 1.2f + stockH * 0.7f)
        pa.lineTo(x0 + 9f, 1f + stockH); pa.lineTo(x0, 2f + stockH + 1.5f); pa.close()
        p.seg(x0 + 6f, 1.2f, x1, 1.2f, barrelW, metal)
        p.shape(wood)
        if (p.hi) p.segF(x0 + 2f, 2.6f, x1 - 14f, 2.2f, 0.8f, light(wood, 0.3f))
    }

    private fun flash(atk: Float, x: Float, y: Float, s: Float) {
        if (atk < 0.65f) return
        p.glow(x + 2f * s, y, 5.5f * s, FLASH)
        p.tri(x, y - 2.5f * s, x + 12f * s, y, x, y + 2.5f * s); p.cv.drawPath(p.path, p.gp(0xF0FFFFFF.toInt()))
    }

    private fun bow(x: Float, y: Float, pull: Float) {
        val pa = p.path; pa.reset()
        pa.moveTo(x - 4f, y - 18f); pa.quadTo(x + 8f, y, x - 4f, y + 18f)
        cv.drawPath(pa, p.op(2.6f + p.ow * 2f)); cv.drawPath(pa, p.lp(WOOD, 2.6f))
        val sx = x - 6f - pull * 10f
        cv.drawLine(x - 4f, y - 18f, sx, y, p.lp(0xFFEDE8DA.toInt(), 0.8f)); cv.drawLine(x - 4f, y + 18f, sx, y, p.line)
        p.seg(sx, y, x + 6f, y, 1f, WOOD_D) // strzała
        p.tri(x + 6f, y - 1.5f, x + 9.5f, y, x + 6f, y + 1.5f); p.shapeF(STEEL)
    }
}
