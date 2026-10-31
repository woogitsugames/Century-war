package com.centurywar.screens

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Ilustrowane tło menu: pole bitwy o zmierzchu. Niebo z gradientem, słońce z
 * promieniami, chmury, trzy warstwy gór i wzgórz z paralaksą (sosny), dwie
 * twierdze z różnych epok (średniowieczny zamek gracza i stalowy bastion wroga)
 * z łopoczącymi sztandarami, dym z komina, ptaki, pierwszy plan z trawą,
 * kamieniami i wbitymi włóczniami.
 *
 * Ścieżki warstw i gradienty liczone są raz przy zmianie rozmiaru; w każdej
 * klatce tylko je przesuwamy, więc rysowanie nie alokuje pamięci.
 */
class MenuBg {
    companion object {
        /** Wspólny czas animacji: tło nie "skacze" przy przejściu między ekranami. */
        private var time = 0f
        private var lastFrameNanos = 0L

        private val SKY_TOP = Color.rgb(30, 40, 92)
        private val SKY_MID = Color.rgb(112, 82, 140)
        private val SKY_LOW = Color.rgb(250, 168, 98)
        private val OUT = Color.rgb(26, 22, 32)
        private val MOUNT = Color.rgb(104, 96, 150)
        private val MOUNT_D = Color.rgb(84, 76, 128)
        private val SNOW = Color.rgb(238, 214, 228)
        private val HILL = Color.rgb(66, 84, 106)
        private val PINE = Color.rgb(48, 64, 86)
        private val NEAR = Color.rgb(54, 76, 70)
        private val NEAR_D = Color.rgb(40, 58, 56)
        private val GROUND = Color.rgb(70, 94, 56)
        private val GRASS = Color.rgb(104, 136, 66)
        private val DIRT = Color.rgb(124, 92, 62)
        private val STONE = Color.rgb(160, 154, 146)
        private val STONE_D = Color.rgb(118, 112, 106)
        private val STEEL = Color.rgb(112, 122, 138)
        private val STEEL_D = Color.rgb(78, 86, 102)
        private val BLUE = Color.rgb(64, 130, 225)
        private val RED = Color.rgb(214, 64, 54)
        private val LIT = Color.rgb(255, 214, 120)
        private val CYAN = Color.rgb(110, 236, 255)
    }

    private val sky = Paint()
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shade = Paint()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    private val p = Path()
    private val far = Path()
    private val farSnow = Path()
    private val mid = Path()
    private val near = Path()
    private val ground = Path()
    private val tufts = Path()
    private val rect = RectF()
    private var lw = -1f
    private var lh = -1f
    private var k = 1f
    private var tile = 1f

    /** [dt] jest ignorowane, jeśli w tej klatce czas już przesunięto (wspólny zegar). */
    fun draw(c: Canvas, w: Float, h: Float, dt: Float, animated: Boolean = true): Float {
        if (animated) {
            val now = System.nanoTime()
            if (now - lastFrameNanos > 4_000_000L) { time += dt; lastFrameNanos = now }
        }
        if (w != lw || h != lh) build(w, h)
        val t = if (animated) time else 0f
        c.drawRect(0f, 0f, w, h, sky)
        drawStars(c, w, h, t)
        drawSun(c, w, h, t)
        drawClouds(c, w, h, t, 0)
        scroll(c, far, t * 5f * k, MOUNT, MOUNT_D, 1.6f)
        scrollFill(c, farSnow, t * 5f * k, SNOW)
        drawClouds(c, w, h, t, 1)
        scroll(c, mid, t * 11f * k, HILL, Color.rgb(44, 58, 78), 1.8f)
        drawBirds(c, w, h, t)
        scroll(c, near, t * 18f * k, NEAR, OUT, 2.2f)
        val gy = h * 0.8f
        castle(c, w * 0.13f, gy + 4 * k, t)
        bastion(c, w * 0.87f, gy + 4 * k, t)
        // pierwszy plan
        fill.color = GROUND; c.drawPath(ground, fill)
        line.color = OUT; line.strokeWidth = 2.4f * k; c.drawPath(ground, line)
        fill.color = GRASS; c.drawPath(tufts, fill)
        foreground(c, w, h, t)
        c.drawRect(0f, h * 0.45f, w, h, shade)
        return t
    }

    // ---------------------------------------------------------------- budowa warstw

    private fun hash(i: Int): Float {
        val x = sin(i * 127.1f + 311.7f) * 43758.547f
        return x - kotlin.math.floor(x)
    }

    private fun build(w: Float, h: Float) {
        lw = w; lh = h
        k = min(h / 360f, w / 620f)
        tile = max(w, 900f * k)
        sky.shader = LinearGradient(0f, 0f, 0f, h * 0.8f, intArrayOf(SKY_TOP, SKY_MID, SKY_LOW), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        glow.shader = RadialGradient(w * 0.6f, h * 0.56f, h * 0.42f, intArrayOf(Color.argb(150, 255, 214, 140), Color.argb(0, 255, 180, 120)), null, Shader.TileMode.CLAMP)
        shade.shader = LinearGradient(0f, h * 0.45f, 0f, h, Color.argb(0, 8, 8, 18), Color.argb(150, 8, 8, 18), Shader.TileMode.CLAMP)

        // dalekie góry: ostre szczyty ze śniegiem
        far.reset(); farSnow.reset()
        val base = h * 0.66f
        far.moveTo(-tile, h); far.lineTo(-tile, base)
        val peaks = 7
        for (copy in -1..1) {
            for (i in 0 until peaks) {
                val cx = copy * tile + (i + 0.5f) * tile / peaks + (hash(i) - 0.5f) * tile / peaks * 0.5f
                val ph = h * (0.16f + hash(i + 20) * 0.14f)
                val pw = tile / peaks * (0.75f + hash(i + 40) * 0.35f)
                peak(cx, base, ph, pw)
            }
        }
        far.lineTo(2 * tile, base); far.lineTo(2 * tile, h); far.close()

        // środkowe wzgórza z sosnami
        mid.reset()
        val mb = h * 0.72f
        mid.moveTo(-tile, h)
        var x = -tile
        val step = tile / 48f
        while (x <= 2 * tile + 1f) {
            mid.lineTo(x, mb - wave(x, 2, 3, h * 0.035f, h * 0.018f))
            x += step
        }
        mid.lineTo(2 * tile, h); mid.close()
        for (copy in -1..1) for (i in 0 until 22) {
            val tx = copy * tile + hash(i + 70) * tile
            val ty = mb - wave(tx, 2, 3, h * 0.035f, h * 0.018f) + 2 * k
            val th = (14f + hash(i + 90) * 12f) * k
            pine(mid, tx, ty, th)
        }

        // bliskie wzgórza, na których stoją twierdze
        near.reset()
        val nb = h * 0.8f
        near.moveTo(-tile, h)
        x = -tile
        while (x <= 2 * tile + 1f) {
            near.lineTo(x, nb - wave(x, 1, 4, h * 0.03f, h * 0.012f) - h * 0.02f)
            x += step
        }
        near.lineTo(2 * tile, h); near.close()

        // pierwszy plan (statyczny)
        ground.reset()
        val gb = h * 0.88f
        ground.moveTo(-10f, h + 10f); ground.lineTo(-10f, gb)
        x = 0f
        while (x <= w + step) {
            ground.lineTo(x, gb - sin(x / w * PI.toFloat() * 3f) * h * 0.012f - abs(sin(x / w * 7.3f)) * h * 0.008f)
            x += w / 40f
        }
        ground.lineTo(w + 10f, gb); ground.lineTo(w + 10f, h + 10f); ground.close()
        tufts.reset()
        for (i in 0 until 46) {
            val tx = hash(i + 200) * w
            val ty = gb - sin(tx / w * PI.toFloat() * 3f) * h * 0.012f - abs(sin(tx / w * 7.3f)) * h * 0.008f + 2 * k
            val s = (3f + hash(i + 260) * 4f) * k
            tufts.moveTo(tx - s, ty); tufts.lineTo(tx - s * 0.5f, ty - s * 1.6f); tufts.lineTo(tx, ty - s * 0.3f)
            tufts.lineTo(tx + s * 0.4f, ty - s * 2f); tufts.lineTo(tx + s * 0.8f, ty - s * 0.2f); tufts.lineTo(tx + s * 1.3f, ty - s * 1.3f)
            tufts.lineTo(tx + s * 1.4f, ty); tufts.close()
        }
    }

    private fun wave(x: Float, f1: Int, f2: Int, a1: Float, a2: Float): Float {
        val u = x / tile * 2f * PI.toFloat()
        return sin(u * f1 + 1.3f) * a1 + sin(u * f2 + 0.4f) * a2 + a1
    }

    private fun peak(cx: Float, base: Float, ph: Float, pw: Float) {
        val top = base - ph
        val l = cx - pw / 2
        val r = cx + pw / 2
        val skew = pw * 0.08f
        far.lineTo(l, base)
        far.lineTo(cx - pw * 0.18f, top + ph * 0.42f)
        far.lineTo(cx + skew, top)
        far.lineTo(cx + pw * 0.2f, top + ph * 0.36f)
        far.lineTo(r, base)
        // czapa śnieżna z ząbkowanym dołem
        val sy = top + ph * 0.3f
        farSnow.moveTo(cx + skew, top)
        farSnow.lineTo(cx + skew + (cx + pw * 0.2f - cx - skew) * 0.9f, top + ph * 0.32f)
        farSnow.lineTo(cx + pw * 0.08f, sy - ph * 0.04f)
        farSnow.lineTo(cx, sy + ph * 0.05f)
        farSnow.lineTo(cx - pw * 0.06f, sy - ph * 0.02f)
        farSnow.lineTo(cx - pw * 0.14f, sy + ph * 0.06f)
        farSnow.close()
    }

    private fun pine(path: Path, x: Float, y: Float, hgt: Float) {
        val wdt = hgt * 0.5f
        path.moveTo(x - wdt * 0.12f, y); path.lineTo(x - wdt * 0.12f, y - hgt * 0.2f)
        path.lineTo(x - wdt * 0.5f, y - hgt * 0.2f); path.lineTo(x - wdt * 0.22f, y - hgt * 0.5f)
        path.lineTo(x - wdt * 0.38f, y - hgt * 0.5f); path.lineTo(x, y - hgt)
        path.lineTo(x + wdt * 0.38f, y - hgt * 0.5f); path.lineTo(x + wdt * 0.22f, y - hgt * 0.5f)
        path.lineTo(x + wdt * 0.5f, y - hgt * 0.2f); path.lineTo(x + wdt * 0.12f, y - hgt * 0.2f)
        path.lineTo(x + wdt * 0.12f, y); path.close()
    }

    private fun scroll(c: Canvas, path: Path, off: Float, color: Int, outline: Int, lwK: Float) {
        val o = off % tile
        c.save()
        c.translate(-o, 0f)
        fill.color = color
        c.drawPath(path, fill)
        line.color = outline; line.strokeWidth = lwK * k
        c.drawPath(path, line)
        c.restore()
    }

    private fun scrollFill(c: Canvas, path: Path, off: Float, color: Int) {
        val o = off % tile
        c.save(); c.translate(-o, 0f)
        fill.color = color
        c.drawPath(path, fill)
        c.restore()
    }

    // ---------------------------------------------------------------- niebo

    private fun drawStars(c: Canvas, w: Float, h: Float, t: Float) {
        for (i in 0 until 26) {
            val a = (0.35f + 0.35f * sin(t * (0.8f + hash(i + 500)) + i)) * 255
            fill.color = Color.argb(a.toInt().coerceIn(0, 255), 255, 250, 230)
            c.drawCircle(hash(i + 400) * w, hash(i + 450) * h * 0.3f, (0.8f + hash(i + 480) * 1.2f) * k, fill)
        }
    }

    private fun drawSun(c: Canvas, w: Float, h: Float, t: Float) {
        val sx = w * 0.6f
        val sy = h * 0.56f
        val sr = h * 0.11f
        c.drawCircle(sx, sy, h * 0.42f, glow)
        // obracające się promienie
        c.save()
        c.translate(sx, sy)
        c.rotate(t * 3f)
        fill.color = Color.argb(28, 255, 236, 180)
        val len = h * 0.6f
        for (i in 0 until 12) {
            p.reset()
            p.moveTo(0f, 0f)
            p.lineTo(len, -len * 0.07f)
            p.lineTo(len, len * 0.07f)
            p.close()
            c.drawPath(p, fill)
            c.rotate(30f)
        }
        c.restore()
        fill.color = Color.argb(90, 255, 230, 160)
        c.drawCircle(sx, sy, sr * 1.25f, fill)
        fill.color = Color.rgb(255, 222, 140)
        c.drawCircle(sx, sy, sr, fill)
        fill.color = Color.rgb(255, 240, 190)
        c.drawCircle(sx - sr * 0.15f, sy - sr * 0.15f, sr * 0.75f, fill)
    }

    private fun drawClouds(c: Canvas, w: Float, h: Float, t: Float, layer: Int) {
        val n = if (layer == 0) 3 else 3
        val span = w + 360f * k
        for (i in 0 until n) {
            val seed = i + layer * 10
            val speed = (if (layer == 0) 4f else 8f) * k
            val x = ((hash(seed + 600) * span + t * speed) % span) - 180f * k
            val y = h * (if (layer == 0) 0.12f + hash(seed + 620) * 0.18f else 0.3f + hash(seed + 640) * 0.12f)
            val s = (if (layer == 0) 0.8f else 1.1f) * k * (0.8f + hash(seed + 660) * 0.5f)
            cloud(c, x, y, s, if (layer == 0) Color.rgb(196, 170, 206) else Color.rgb(250, 214, 200))
        }
    }

    private fun cloud(c: Canvas, x: Float, y: Float, s: Float, color: Int) {
        val o = 2.2f * k
        for (pass in 0..1) {
            fill.color = if (pass == 0) Color.argb(130, 60, 44, 90) else color
            val e = if (pass == 0) o else 0f
            c.drawCircle(x, y, 22 * s + e, fill)
            c.drawCircle(x + 26 * s, y - 10 * s, 26 * s + e, fill)
            c.drawCircle(x + 56 * s, y, 20 * s + e, fill)
            c.drawCircle(x - 22 * s, y + 6 * s, 15 * s + e, fill)
            rect.set(x - 36 * s - e, y + 2 * s - e, x + 74 * s + e, y + 20 * s + e)
            c.drawRoundRect(rect, 10 * s, 10 * s, fill)
        }
        fill.color = Color.argb(70, 255, 255, 255)
        c.drawCircle(x + 22 * s, y - 18 * s, 12 * s, fill)
    }

    private fun drawBirds(c: Canvas, w: Float, h: Float, t: Float) {
        line.color = Color.argb(200, 40, 30, 60)
        line.strokeWidth = 1.6f * k
        val span = w + 200f * k
        for (i in 0 until 5) {
            val x = ((hash(i + 700) * span + t * (16f + i * 3f) * k) % span) - 100f * k
            val y = h * (0.2f + hash(i + 720) * 0.16f) + sin(t * 0.8f + i) * 6f * k
            val flap = sin(t * 6f + i * 1.7f) * 3f * k
            val s = (5f + hash(i + 740) * 3f) * k
            c.drawLine(x - s, y - flap, x, y + s * 0.3f, line)
            c.drawLine(x, y + s * 0.3f, x + s, y - flap, line)
        }
    }

    // ---------------------------------------------------------------- twierdze

    private fun rectO(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int, x0: Float, y0: Float) {
        rect.set(x0 + l * k, y0 + t * k, x0 + r * k, y0 + b * k)
        fill.color = color; c.drawRect(rect, fill)
        line.color = OUT; line.strokeWidth = 1.8f * k; c.drawRect(rect, line)
    }

    private fun merlons(c: Canvas, l: Float, r: Float, top: Float, color: Int, x0: Float, y0: Float) {
        var x = l
        while (x + 6f <= r + 0.1f) { rectO(c, x, top - 7f, x + 6f, top, color, x0, y0); x += 11f }
    }

    private fun banner(c: Canvas, x: Float, y: Float, color: Int, t: Float, dir: Float, phase: Float) {
        // x, y: wierzchołek masztu (px)
        line.color = OUT; line.strokeWidth = 2f * k
        c.drawLine(x, y, x, y + 26 * k, line)
        p.reset()
        val len = 24f * k * dir
        val w1 = sin(t * 4f + phase) * 3f * k
        val w2 = sin(t * 4f + phase + 1.6f) * 3f * k
        p.moveTo(x, y + 1 * k)
        p.quadTo(x + len * 0.5f, y + w1, x + len, y + 2 * k + w2)
        p.lineTo(x + len * 0.8f, y + 7 * k + w2)
        p.lineTo(x + len, y + 12 * k + w2)
        p.quadTo(x + len * 0.5f, y + 11 * k + w1, x, y + 12 * k)
        p.close()
        fill.color = color; c.drawPath(p, fill)
        line.strokeWidth = 1.6f * k; c.drawPath(p, line)
        fill.color = Color.rgb(255, 205, 64)
        c.drawCircle(x, y, 2.4f * k, fill)
    }

    private fun castle(c: Canvas, x0: Float, y0: Float, t: Float) {
        // mur kurtynowy
        rectO(c, -62f, -54f, 62f, 0f, STONE, x0, y0)
        merlons(c, -60f, 60f, -54f, STONE, x0, y0)
        // cegły
        line.color = STONE_D; line.strokeWidth = 1.2f * k
        for (row in 1..3) {
            val yy = y0 - row * 13f * k
            c.drawLine(x0 - 60 * k, yy, x0 + 60 * k, yy, line)
        }
        // donżon
        rectO(c, -26f, -112f, 26f, -54f, Color.rgb(176, 170, 162), x0, y0)
        merlons(c, -25f, 25f, -112f, Color.rgb(176, 170, 162), x0, y0)
        rectO(c, -6f, -96f, 6f, -80f, LIT, x0, y0)
        // wieże z dachami
        for (side in 0..1) {
            val cx = if (side == 0) -66f else 66f
            rectO(c, cx - 15f, -96f, cx + 15f, 0f, STONE, x0, y0)
            rectO(c, cx - 4f, -76f, cx + 4f, -64f, LIT, x0, y0)
            rectO(c, cx - 4f, -44f, cx + 4f, -32f, LIT, x0, y0)
            p.reset()
            p.moveTo(x0 + (cx - 20f) * k, y0 - 96f * k)
            p.lineTo(x0 + cx * k, y0 - 136f * k)
            p.lineTo(x0 + (cx + 20f) * k, y0 - 96f * k)
            p.close()
            fill.color = BLUE; c.drawPath(p, fill)
            fill.color = Color.argb(70, 0, 0, 20)
            c.save(); c.clipRect(x0 + cx * k, y0 - 140 * k, x0 + (cx + 22) * k, y0 - 90 * k); c.drawPath(p, fill); c.restore()
            line.color = OUT; line.strokeWidth = 1.8f * k; c.drawPath(p, line)
            banner(c, x0 + cx * k, y0 - 160f * k, BLUE, t, 1f, side * 2f)
        }
        // brama
        p.reset()
        p.moveTo(x0 - 14 * k, y0)
        p.lineTo(x0 - 14 * k, y0 - 24 * k)
        p.quadTo(x0, y0 - 40 * k, x0 + 14 * k, y0 - 24 * k)
        p.lineTo(x0 + 14 * k, y0)
        p.close()
        fill.color = Color.rgb(70, 44, 28); c.drawPath(p, fill)
        line.color = Color.rgb(40, 26, 18); line.strokeWidth = 1.4f * k
        for (i in -1..1) c.drawLine(x0 + i * 6 * k, y0 - 30 * k, x0 + i * 6 * k, y0, line)
        line.color = OUT; line.strokeWidth = 1.8f * k; c.drawPath(p, line)
        // tarcza nad bramą
        fill.color = BLUE; c.drawCircle(x0, y0 - 44 * k, 5 * k, fill)
        line.strokeWidth = 1.4f * k; c.drawCircle(x0, y0 - 44 * k, 5 * k, line)
    }

    private fun bastion(c: Canvas, x0: Float, y0: Float, t: Float) {
        // dym z komina
        for (i in 0 until 5) {
            val ph = (t * 0.25f + i / 5f) % 1f
            val sx = x0 + (46f + ph * 26f) * k
            val sy = y0 - (112f + ph * 70f) * k
            fill.color = Color.argb((150 * (1f - ph)).toInt(), 70, 66, 80)
            c.drawCircle(sx, sy, (6f + ph * 12f) * k, fill)
        }
        // komin
        rectO(c, 40f, -112f, 54f, -50f, STEEL_D, x0, y0)
        rectO(c, 37f, -118f, 57f, -110f, STEEL, x0, y0)
        // działo (celuje w zamek)
        c.save()
        c.translate(x0 - 30 * k, y0 - 74 * k)
        c.rotate(8f + sin(t * 0.7f) * 2f)
        rect.set(-62 * k, -5 * k, 0f, 5 * k)
        fill.color = STEEL_D; c.drawRect(rect, fill)
        line.color = OUT; line.strokeWidth = 1.8f * k; c.drawRect(rect, line)
        rect.set(-66 * k, -7 * k, -58 * k, 7 * k)
        fill.color = STEEL; c.drawRect(rect, fill); c.drawRect(rect, line)
        c.restore()
        // korpus bunkra
        p.reset()
        p.moveTo(x0 - 78 * k, y0)
        p.lineTo(x0 - 66 * k, y0 - 52 * k)
        p.lineTo(x0 + 66 * k, y0 - 52 * k)
        p.lineTo(x0 + 78 * k, y0)
        p.close()
        fill.color = STEEL; c.drawPath(p, fill)
        fill.color = STEEL_D
        c.save(); c.clipRect(x0 - 80 * k, y0 - 16 * k, x0 + 80 * k, y0); c.drawPath(p, fill); c.restore()
        line.color = OUT; line.strokeWidth = 1.8f * k; c.drawPath(p, line)
        // nity
        fill.color = Color.rgb(170, 178, 192)
        var rx = -60f
        while (rx <= 60f) { c.drawCircle(x0 + rx * k, y0 - 46 * k, 1.6f * k, fill); rx += 12f }
        // okna świecące
        for (i in 0..3) rectO(c, -54f + i * 30f, -36f, -40f + i * 30f, -28f, CYAN, x0, y0)
        // brama
        rectO(c, -12f, -24f, 12f, 0f, Color.rgb(50, 54, 66), x0, y0)
        line.color = Color.rgb(255, 205, 64); line.strokeWidth = 1.4f * k
        c.drawLine(x0 - 12 * k, y0 - 12 * k, x0 + 12 * k, y0 - 12 * k, line)
        // wieża dowodzenia z kopułą
        rectO(c, -22f, -110f, 22f, -52f, Color.rgb(128, 138, 154), x0, y0)
        rectO(c, -14f, -96f, 14f, -88f, CYAN, x0, y0)
        rect.set(x0 - 22 * k, y0 - 132 * k, x0 + 22 * k, y0 - 88 * k)
        fill.color = STEEL_D; c.drawArc(rect, 180f, 180f, true, fill)
        line.color = OUT; line.strokeWidth = 1.8f * k; c.drawArc(rect, 180f, 180f, true, line)
        // antena z migającym światłem
        c.drawLine(x0, y0 - 132 * k, x0, y0 - 156 * k, line)
        val blink = if ((t * 1.5f) % 1f < 0.5f) 255 else 90
        fill.color = Color.argb(blink, 255, 70, 60)
        c.drawCircle(x0, y0 - 157 * k, 3.4f * k, fill)
        banner(c, x0 - 66 * k, y0 - 78 * k, RED, t, -1f, 0.8f)
        banner(c, x0 + 66 * k, y0 - 78 * k, RED, t, -1f, 2.1f)
    }

    // ---------------------------------------------------------------- pierwszy plan

    private fun foreground(c: Canvas, w: Float, h: Float, t: Float) {
        val gb = h * 0.88f
        // ścieżka
        p.reset()
        p.moveTo(w * 0.38f, h + 2f)
        p.quadTo(w * 0.47f, gb + h * 0.05f, w * 0.5f, gb + 2 * k)
        p.quadTo(w * 0.53f, gb + h * 0.05f, w * 0.62f, h + 2f)
        p.close()
        fill.color = DIRT; c.drawPath(p, fill)
        line.color = OUT; line.strokeWidth = 2f * k; c.drawPath(p, line)
        // kamienie
        for (i in 0 until 6) {
            val x = hash(i + 800) * w
            if (x > w * 0.36f && x < w * 0.64f) continue
            val y = gb + (8f + hash(i + 820) * 22f) * k
            val s = (5f + hash(i + 840) * 6f) * k
            rect.set(x - s, y - s * 0.6f, x + s, y + s * 0.6f)
            fill.color = STONE_D; c.drawOval(rect, fill)
            fill.color = STONE
            rect.set(x - s * 0.8f, y - s * 0.6f, x + s * 0.6f, y + s * 0.1f); c.drawOval(rect, fill)
            rect.set(x - s, y - s * 0.6f, x + s, y + s * 0.6f)
            line.color = OUT; line.strokeWidth = 1.6f * k; c.drawOval(rect, line)
        }
        // wbite włócznie i miecz (pamiątki dawnych bitew)
        spear(c, w * 0.3f, gb + 14 * k, -12f)
        spear(c, w * 0.33f, gb + 18 * k, 8f)
        spear(c, w * 0.7f, gb + 16 * k, 14f)
        swordInGround(c, w * 0.75f, gb + 20 * k, -6f)
        // świetliki
        for (i in 0 until 8) {
            val x = hash(i + 900) * w + sin(t * 0.7f + i) * 10 * k
            val y = h * (0.82f + hash(i + 920) * 0.14f) + sin(t * 1.3f + i * 2f) * 6 * k
            val a = (0.5f + 0.5f * sin(t * 3f + i * 1.3f))
            fill.color = Color.argb((180 * a).toInt(), 255, 236, 140)
            c.drawCircle(x, y, 1.8f * k, fill)
        }
    }

    private fun spear(c: Canvas, x: Float, y: Float, angle: Float) {
        c.save(); c.translate(x, y); c.rotate(angle)
        line.color = OUT; line.strokeWidth = 4f * k
        c.drawLine(0f, 0f, 0f, -44 * k, line)
        line.color = Color.rgb(150, 100, 56); line.strokeWidth = 2f * k
        c.drawLine(0f, 0f, 0f, -44 * k, line)
        p.reset(); p.moveTo(-3.5f * k, -44 * k); p.lineTo(0f, -56 * k); p.lineTo(3.5f * k, -44 * k); p.close()
        fill.color = Color.rgb(200, 206, 216); c.drawPath(p, fill)
        line.color = OUT; line.strokeWidth = 1.4f * k; c.drawPath(p, line)
        c.restore()
    }

    private fun swordInGround(c: Canvas, x: Float, y: Float, angle: Float) {
        c.save(); c.translate(x, y); c.rotate(angle)
        rect.set(-2.5f * k, -34 * k, 2.5f * k, 0f)
        fill.color = Color.rgb(206, 212, 222); c.drawRect(rect, fill)
        line.color = OUT; line.strokeWidth = 1.4f * k; c.drawRect(rect, line)
        rect.set(-9 * k, -38 * k, 9 * k, -34 * k)
        fill.color = Color.rgb(255, 200, 50); c.drawRect(rect, fill); c.drawRect(rect, line)
        rect.set(-2f * k, -48 * k, 2f * k, -38 * k)
        fill.color = Color.rgb(120, 76, 40); c.drawRect(rect, fill); c.drawRect(rect, line)
        fill.color = Color.rgb(255, 200, 50); c.drawCircle(0f, -50 * k, 3 * k, fill); c.drawCircle(0f, -50 * k, 3 * k, line)
        c.restore()
    }
}
