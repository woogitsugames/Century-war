package com.centurywar.screens

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.sin

/** Animowane tło menu: zmierzch nad polem bitwy, wzgórza z paralaksą. */
class MenuBg {
    private val sky = Paint()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var lastH = -1f
    private var t = 0f

    fun draw(c: Canvas, w: Float, h: Float, dt: Float) {
        t += dt
        if (h != lastH) {
            lastH = h
            sky.shader = LinearGradient(0f, 0f, 0f, h, Color.rgb(28, 36, 70), Color.rgb(214, 120, 70), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w, h, sky)
        fill.color = Color.argb(200, 255, 220, 150)
        c.drawCircle(w * 0.72f, h * 0.62f, h * 0.12f, fill)
        layer(c, w, h, 0.55f, 0.18f, t * 6f, Color.rgb(70, 52, 78), 1)
        layer(c, w, h, 0.68f, 0.14f, t * 14f, Color.rgb(48, 36, 56), 2)
        layer(c, w, h, 0.80f, 0.10f, t * 26f, Color.rgb(28, 22, 34), 3)
    }

    private fun layer(c: Canvas, w: Float, h: Float, base: Float, amp: Float, off: Float, color: Int, seed: Int) {
        path.reset()
        path.moveTo(0f, h)
        var x = 0f
        val step = w / 24f
        while (x <= w + step) {
            val v = sin((x + off) / w * 6.28f * (1 + seed * 0.6f) + seed) * 0.5f + sin((x + off) / w * 17f + seed * 2) * 0.25f
            path.lineTo(x, h * (base - amp * v * 0.5f))
            x += step
        }
        path.lineTo(w, h)
        path.close()
        fill.color = color
        c.drawPath(path, fill)
    }
}
