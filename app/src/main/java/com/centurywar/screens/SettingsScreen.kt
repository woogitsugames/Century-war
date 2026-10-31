package com.centurywar.screens

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.L
import com.centurywar.engine.Screen
import com.centurywar.engine.Ui
import kotlin.math.min

class SettingsScreen(g: Game) : Screen(g) {
    private val bg = MenuBg()
    private val back = btn(-1)
    private val quality = btn(1)
    private val fps = btn(2)
    private val lang = btn(3)
    private val vib = btn(4)
    private val showFps = btn(5)
    private val name = btn(6)
    private val reset = btn(7)
    private val rows = listOf(quality, fps, lang, vib, showFps, name, reset)
    private var resetArmed = 0f
    private var dt = 0f

    override fun layout() {
        back.set(g.insetL + 12 * dp, g.insetT + 10 * dp, 100 * dp, 38 * dp)
        val bw = min(260 * dp, w * 0.4f)
        val bh = 40 * dp
        val colX = w / 2 + 10 * dp
        val avail = h - g.insetT - 70 * dp
        val step = min(bh + 8 * dp, avail / rows.size)
        for ((i, b) in rows.withIndex()) b.set(colX, g.insetT + 64 * dp + i * step, bw, step - 6 * dp)
    }

    override fun update(dt: Float) {
        this.dt = dt
        if (resetArmed > 0f) resetArmed -= dt
        val p = g.profile
        back.label = L["back"]
        quality.label = when (p.quality) { 1 -> L["q_low"]; 2 -> L["q_med"]; 3 -> L["q_high"]; else -> "${L["q_auto"]} (${g.quality.level})" }
        fps.label = when (p.fpsCap) { 30 -> "30"; 60 -> "60"; else -> L["fps_max"] }
        lang.label = when (p.lang) { "pl" -> "Polski"; "en" -> "English"; else -> "Auto" }
        vib.label = if (p.vibration) L["on"] else L["off"]
        showFps.label = if (p.showFps) L["on"] else L["off"]
        name.label = p.name
        reset.label = if (resetArmed > 0f) L["reset_confirm"] else L["reset"]
        reset.color = Color.rgb(120, 50, 45)
    }

    override fun render(c: Canvas) {
        bg.draw(c, w, h, dt)
        c.drawColor(Color.argb(120, 0, 0, 0))
        ui.text(c, L["settings"], w / 2, g.insetT + 38 * dp, 24f, Ui.GOLD, Paint.Align.CENTER, true, true)
        val labels = listOf(L["quality"], L["fps"], L["language"], L["vibration"], L["show_fps"], L["name"], "")
        for ((i, b) in rows.withIndex()) {
            ui.text(c, labels[i], w / 2 - 10 * dp, b.r.centerY() + 5 * dp, 15f, Ui.TEXT, Paint.Align.RIGHT, false, true)
            ui.button(c, b, 14f)
        }
        ui.button(c, back)
    }

    override fun onClick(b: Button) {
        val p = g.profile
        when (b.id) {
            -1 -> { p.save(); g.pop() }
            1 -> { p.quality = (p.quality + 1) % 4; g.setQuality(g.effectiveQualityLevel()) }
            2 -> { p.fpsCap = when (p.fpsCap) { 0 -> 60; 60 -> 30; else -> 0 }; g.applyFrameRate() }
            3 -> { p.lang = when (p.lang) { "" -> "pl"; "pl" -> "en"; else -> "" }; g.applyLanguage() }
            4 -> p.vibration = !p.vibration
            5 -> p.showFps = !p.showFps
            6 -> g.host.requestText(L["name"], p.name) { n -> p.name = n; p.save() }
            7 -> if (resetArmed > 0f) { p.reset(); resetArmed = 0f } else resetArmed = 3f
        }
        p.save()
    }
}
