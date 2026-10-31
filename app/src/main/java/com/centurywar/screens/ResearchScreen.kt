package com.centurywar.screens

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.L
import com.centurywar.engine.Screen
import com.centurywar.engine.Ui
import com.centurywar.meta.Profile
import com.centurywar.meta.ResearchDef
import kotlin.math.max

/** Drzewko badań: trwałe ulepszenia kupowane za medale z każdej bitwy (także przegranej). */
class ResearchScreen(g: Game) : Screen(g) {
    private val bg = MenuBg()
    private val back = btn(-1)
    private val cards = Profile.research.mapIndexed { i, d -> btn(i).also { it.tag = d } }
    private var scroll = 0f
    private var maxScroll = 0f
    private var cols = 3
    private var cw = 0f
    private var ch = 0f
    private var top = 0f
    private var dt = 0f

    override fun isDragScreen() = true

    override fun layout() {
        back.label = L["back"]
        back.set(g.insetL + 12 * dp, g.insetT + 10 * dp, 100 * dp, 38 * dp)
        val avail = w - g.insetL - g.insetR - 32 * dp
        cols = max(2, (avail / (230 * dp)).toInt())
        cw = (avail - (cols - 1) * 10 * dp) / cols
        ch = 92 * dp
        top = g.insetT + 60 * dp
        val rows = (cards.size + cols - 1) / cols
        maxScroll = max(0f, top + rows * (ch + 10 * dp) + 10 * dp - h)
        place()
    }

    private fun place() {
        for ((i, b) in cards.withIndex()) {
            val d = b.tag as ResearchDef
            val lv = g.profile.level(d.key)
            b.set(g.insetL + 16 * dp + (i % cols) * (cw + 10 * dp), top + (i / cols) * (ch + 10 * dp) - scroll, cw, ch)
            b.enabled = g.profile.canBuy(d)
            b.label = ""
            b.highlight = b.enabled
            b.color = if (lv >= d.max) Color.rgb(50, 90, 60) else Ui.BTN
        }
    }

    override fun update(dt: Float) { this.dt = dt; place() }
    override fun onDrag(dx: Float, dy: Float) { scroll = (scroll - dy).coerceIn(0f, maxScroll) }

    override fun render(c: Canvas) {
        bg.draw(c, w, h, dt)
        c.drawColor(Color.argb(110, 0, 0, 0))
        c.save()
        c.clipRect(0f, top - 6 * dp, w, h)
        for (b in cards) {
            if (b.r.bottom < top - 10 * dp || b.r.top > h) continue
            ui.button(c, b)
            val d = b.tag as ResearchDef
            val lv = g.profile.level(d.key)
            val x = b.r.left + 12 * dp
            ui.text(c, L[d.key], x, b.r.top + 24 * dp, 15f, Ui.TEXT, Paint.Align.LEFT, true, true)
            ui.text(c, L[d.key + "_d"], x, b.r.top + 44 * dp, 11.5f, Ui.TEXT)
            ui.text(c, L.f("level", lv, d.max), x, b.r.bottom - 14 * dp, 12f, Ui.DIM)
            val cost = if (lv >= d.max) L["max"] else "${d.cost(lv)} ✪"
            ui.text(c, cost, b.r.right - 12 * dp, b.r.bottom - 14 * dp, 14f, if (lv >= d.max) Ui.GOOD else Ui.GOLD, Paint.Align.RIGHT, true, true)
            ui.bar(c, x, b.r.bottom - 34 * dp, b.r.width() - 24 * dp, 5 * dp, lv / d.max.toFloat(), Ui.GOOD)
        }
        c.restore()
        ui.button(c, back)
        ui.text(c, "${g.profile.medals} ✪ ${L["medals"]}", w - g.insetR - 16 * dp, g.insetT + 34 * dp, 17f, Ui.GOLD, Paint.Align.RIGHT, true, true)
    }

    override fun onClick(b: Button) {
        if (b.id == -1) { g.pop(); return }
        val d = b.tag as? ResearchDef ?: return
        if (g.profile.buy(d)) g.haptic(30)
    }
}
