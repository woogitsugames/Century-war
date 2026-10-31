package com.centurywar.screens

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.cos
import kotlin.math.sin

/**
 * Znak Century War: koło mechanizmu epok, miecz i dwubarwna tarcza.
 * Geometria jest zapamiętana; rysowanie nie tworzy obiektów.
 */
internal class MenuEmblem {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val cog = Path().apply {
        val tau = 6.2831855f
        for (i in 0 until 48) {
            val phase = i % 4
            val radius = if (phase == 1 || phase == 2) 0.55f else 0.44f
            val a = i * tau / 48f - tau / 4f
            val xx = cos(a) * radius
            val yy = sin(a) * radius
            if (i == 0) moveTo(xx, yy) else lineTo(xx, yy)
        }
        close()
    }
    private val shield = Path().apply {
        moveTo(-0.34f, -0.31f)
        quadTo(0f, -0.39f, 0.34f, -0.31f)
        lineTo(0.31f, 0.13f)
        quadTo(0.23f, 0.38f, 0f, 0.48f)
        quadTo(-0.23f, 0.38f, -0.31f, 0.13f)
        close()
    }
    private val sword = Path().apply {
        moveTo(-0.08f, -0.38f)
        lineTo(0f, -0.70f)
        lineTo(0.08f, -0.38f)
        lineTo(0.055f, 0.22f)
        lineTo(-0.055f, 0.22f)
        close()
    }
    private val bevel = Path().apply {
        moveTo(-0.26f, -0.23f)
        quadTo(-0.15f, -0.27f, 0f, -0.25f)
        lineTo(0f, 0.37f)
        quadTo(-0.20f, 0.24f, -0.26f, 0.07f)
        close()
    }

    fun draw(c: Canvas, x: Float, y: Float, size: Float) {
        val saved = c.save()
        c.translate(x, y)
        c.scale(size, size)

        // Zęby epok i pierścień, bez cienia rozmywanego przez GPU.
        fill.color = Color.rgb(83, 52, 29)
        c.drawCircle(0f, 0.07f, 0.59f, fill)
        fill.color = Color.rgb(230, 167, 60)
        c.drawPath(cog, fill)
        stroke.color = Color.rgb(30, 25, 36)
        stroke.strokeWidth = 0.055f
        c.drawPath(cog, stroke)
        fill.color = Color.rgb(75, 65, 72)
        c.drawCircle(0f, 0f, 0.35f, fill)
        stroke.color = Color.rgb(255, 218, 122)
        stroke.strokeWidth = 0.025f
        c.drawCircle(0f, 0f, 0.34f, stroke)

        // Miecz biegnie ukośnie za tarczą.
        val bladeSaved = c.save()
        c.rotate(37f)
        fill.color = Color.rgb(207, 220, 225)
        c.drawPath(sword, fill)
        stroke.color = Color.rgb(30, 25, 36)
        stroke.strokeWidth = 0.055f
        c.drawPath(sword, stroke)
        stroke.color = Color.rgb(255, 252, 227)
        stroke.strokeWidth = 0.02f
        c.drawLine(-0.015f, -0.55f, -0.015f, 0.17f, stroke)
        stroke.color = Color.rgb(241, 184, 60)
        stroke.strokeWidth = 0.085f
        c.drawLine(-0.17f, 0.25f, 0.17f, 0.25f, stroke)
        stroke.color = Color.rgb(86, 54, 30)
        stroke.strokeWidth = 0.09f
        c.drawLine(0f, 0.27f, 0f, 0.52f, stroke)
        fill.color = Color.rgb(246, 206, 84)
        c.drawCircle(0f, 0.54f, 0.064f, fill)
        stroke.color = Color.rgb(30, 25, 36)
        stroke.strokeWidth = 0.035f
        c.drawCircle(0f, 0.54f, 0.064f, stroke)
        c.restoreToCount(bladeSaved)

        // Tarcza łączy barwy obu stron jedną złotą osią.
        fill.color = Color.rgb(50, 112, 194)
        c.drawPath(shield, fill)
        val clipSaved = c.save()
        c.clipPath(shield)
        fill.color = Color.rgb(187, 58, 54)
        c.drawRect(0f, -0.4f, 0.4f, 0.55f, fill)
        fill.color = Color.argb(115, 192, 225, 255)
        c.drawPath(bevel, fill)
        c.restoreToCount(clipSaved)
        stroke.color = Color.rgb(30, 25, 36)
        stroke.strokeWidth = 0.075f
        c.drawPath(shield, stroke)
        stroke.color = Color.rgb(250, 206, 102)
        stroke.strokeWidth = 0.028f
        c.drawPath(shield, stroke)
        stroke.strokeWidth = 0.045f
        c.drawLine(0f, -0.27f, 0f, 0.34f, stroke)
        fill.color = Color.rgb(254, 234, 154)
        c.drawCircle(0f, 0.04f, 0.065f, fill)
        stroke.color = Color.rgb(30, 25, 36)
        stroke.strokeWidth = 0.027f
        c.drawCircle(0f, 0.04f, 0.065f, stroke)
        c.restoreToCount(saved)
    }
}
