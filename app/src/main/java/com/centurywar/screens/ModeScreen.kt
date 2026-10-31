package com.centurywar.screens

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.L
import com.centurywar.engine.Screen
import com.centurywar.engine.Ui
import com.centurywar.game.BattleConfig
import com.centurywar.game.Daily
import com.centurywar.game.Defs
import com.centurywar.game.Mode
import com.centurywar.meta.Profile
import kotlin.math.min

/** Ekran przed bitwą dla trybów: bez końca, wyzwanie dnia, potyczka. */
class ModeScreen(g: Game, private val mode: Mode) : Screen(g) {
    private val bg = MenuBg()
    private val back = btn(-1)
    private val start = btn(-2)
    private val diff = Array(4) { btn(it) }
    private val panel = RectF()
    private var dt = 0f
    private val day = Daily.dayNumber()

    override fun layout() {
        back.label = L["back"]
        back.set(g.insetL + 12 * dp, g.insetT + 10 * dp, 100 * dp, 38 * dp)
        val pw = min(560 * dp, w - g.insetL - g.insetR - 40 * dp)
        panel.set(w / 2 - pw / 2, g.insetT + 60 * dp, w / 2 + pw / 2, h - 20 * dp)
        start.set(w / 2 - 90 * dp, panel.bottom - 62 * dp, 180 * dp, 48 * dp)
        start.highlight = true
        val dw = (pw - 50 * dp) / 4
        for (i in 0..3) {
            diff[i].set(panel.left + 20 * dp + i * (dw + 3.3f * dp), panel.bottom - 120 * dp, dw, 40 * dp)
            diff[i].label = L["diff_$i"]
            diff[i].visible = mode == Mode.SKIRMISH
        }
    }

    override fun update(dt: Float) {
        this.dt = dt
        start.label = L["play"]
        for (i in 0..3) diff[i].selected = g.profile.skirmishDiff == i
    }

    override fun render(c: Canvas) {
        bg.draw(c, w, h, dt)
        c.drawColor(Color.argb(80, 0, 0, 0))
        ui.panel(c, panel)
        val x = panel.left + 22 * dp
        var y = panel.top + 40 * dp
        val pr = g.profile
        val title = when (mode) { Mode.ENDLESS -> L["endless"]; Mode.DAILY -> L["daily"]; else -> L["skirmish"] }
        ui.text(c, title, x, y, 24f, Ui.GOLD, Paint.Align.LEFT, true); y += 34 * dp
        val tw = panel.width() - 44 * dp
        when (mode) {
            Mode.ENDLESS -> {
                y += ui.wrap(c, L["endless_info"], x, y, tw, 15f)
                y += 10 * dp
                ui.text(c, "${L["best"]}: ${L.f("wave", pr.bestWave)}", x, y, 16f, Ui.GOLD, Paint.Align.LEFT, true); y += 26 * dp
                val top = pr.top(Profile.BOARD_ENDLESS).firstOrNull()
                if (top != null) ui.text(c, "${L["score"]}: ${top.score}", x, y, 14f)
            }
            Mode.DAILY -> {
                val cfg = Daily.config(day, pr.bonuses())
                y += ui.wrap(c, L["daily_info"], x, y, tw, 14f)
                y += 6 * dp
                ui.text(c, L.f("eras_range", L[Defs.eraKeys[cfg.startEra]], L[Defs.eraKeys[cfg.maxEra]]), x, y, 14f); y += 22 * dp
                ui.text(c, L.f("ai_power", (cfg.aiPower * 100).toInt()), x, y, 14f, Ui.BAD); y += 22 * dp
                y += ui.wrap(c, "${L["modifiers"]}: " + cfg.modifiers.joinToString(", ") { L[it.key] }, x, y, tw, 14f, Ui.GOLD)
                val ranked = pr.lastRankedDaily != day
                ui.text(c, L.f("daily_ranked", if (ranked) L["daily_free"] else L["daily_used"]), x, y + 4 * dp, 14f, if (ranked) Ui.GOOD else Ui.DIM, Paint.Align.LEFT, true)
                y += 26 * dp
                pr.top(Profile.dailyBoard(day)).firstOrNull()?.let { ui.text(c, "${L["best"]}: ${it.score}", x, y, 14f) }
            }
            else -> {
                ui.text(c, L.f("eras_range", L[Defs.eraKeys[0]], L[Defs.eraKeys[7]]), x, y, 15f); y += 26 * dp
                ui.text(c, L.f("ai_power", (aiPower() * 100).toInt()), x, y, 15f); y += 26 * dp
                pr.top(Profile.BOARD_SKIRMISH).firstOrNull()?.let { ui.text(c, "${L["best"]}: ${it.score}", x, y, 14f) }
                ui.text(c, L["difficulty"], x, diff[0].r.top - 10 * dp, 14f, Ui.DIM)
            }
        }
        for (b in buttons) ui.button(c, b, 15f)
    }

    private fun aiPower() = floatArrayOf(0.8f, 1.05f, 1.3f, 1.6f)[g.profile.skirmishDiff]

    override fun onClick(b: Button) {
        val pr = g.profile
        when (b.id) {
            -1 -> g.pop()
            -2 -> {
                val seed = System.nanoTime()
                when (mode) {
                    Mode.ENDLESS -> g.setScreen(BattleScreen(g, BattleConfig(Mode.ENDLESS, seed, 1f, playerBonuses = pr.bonuses())))
                    Mode.DAILY -> {
                        val ranked = pr.lastRankedDaily != day
                        if (ranked) { pr.lastRankedDaily = day; pr.save() }
                        g.setScreen(BattleScreen(g, Daily.config(day, pr.bonuses()), ranked))
                    }
                    else -> g.setScreen(BattleScreen(g, BattleConfig(Mode.SKIRMISH, seed, aiPower(), playerBonuses = pr.bonuses())))
                }
            }
            in 0..3 -> { pr.skirmishDiff = b.id; pr.save() }
        }
    }
}
