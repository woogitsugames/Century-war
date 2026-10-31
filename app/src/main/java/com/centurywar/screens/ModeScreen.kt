package com.centurywar.screens

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.Icons
import com.centurywar.engine.L
import com.centurywar.engine.Screen
import com.centurywar.engine.Ui
import com.centurywar.game.BattleConfig
import com.centurywar.game.Daily
import com.centurywar.game.Defs
import com.centurywar.game.Mode
import com.centurywar.meta.Profile
import kotlin.math.max
import kotlin.math.min

/** Ekran przed bitwą dla trybów: bez końca, wyzwanie dnia, potyczka. */
class ModeScreen(g: Game, private val mode: Mode) : Screen(g) {
    private val bg = MenuBg()
    private val chrome = Chrome(this)
    private val back = btn(-1)
    private val start = btn(-2)
    private val diff = Array(4) { btn(it) }
    private val panel = RectF()
    private val row = RectF()
    private var dt = 0f
    /** Dzień UTC: odświeżany w update(), żeby ekran otwarty przez północ nie startował wczorajszego wyzwania. */
    private var day = Daily.dayNumber()
    private var dailyCfg: BattleConfig? = null
    private var dailyMods = ""
    private var labelTimer = 0f
    private var endlessBest = ""
    private var endlessScore = ""
    private var dailyRange = ""
    private var dailyPower = ""
    private var dailyRanked = ""
    private var dailyScore = ""
    private var skirmishRange = ""
    private var skirmishPower = ""
    private var skirmishScore = ""

    private fun refreshLabels() {
        val pr = g.profile
        when (mode) {
            Mode.ENDLESS -> {
                endlessBest = "${L["best"]}: ${L.f("wave", pr.bestWave)}"
                val best = pr.top(Profile.BOARD_ENDLESS).firstOrNull()
                endlessScore = if (best == null) "" else "${L["score"]}: ${best.score}"
            }
            Mode.DAILY -> {
                val cfg = dailyCfg ?: Daily.config(day, pr.bonuses()).also {
                    dailyCfg = it
                    dailyMods = L["modifiers"] + ": " + it.modifiers.joinToString(", ") { modifier -> L[modifier.key] }
                }
                dailyRange = L.f("eras_range", L[Defs.eraKeys[cfg.startEra]], L[Defs.eraKeys[cfg.maxEra]])
                dailyPower = L.f("ai_power", (cfg.aiPower * 100).toInt())
                val ranked = pr.lastRankedDaily != day
                dailyRanked = L.f("daily_ranked", if (ranked) L["daily_free"] else L["daily_used"])
                val best = pr.top(Profile.dailyBoard(day)).firstOrNull()
                dailyScore = if (best == null) "" else "${L["best"]}: ${best.score}"
            }
            else -> {
                skirmishRange = L.f("eras_range", L[Defs.eraKeys[0]], L[Defs.eraKeys[Defs.ERA_COUNT - 1]])
                skirmishPower = L.f("ai_power", (aiPower() * 100).toInt())
                val best = pr.top(Profile.BOARD_SKIRMISH).firstOrNull()
                skirmishScore = if (best == null) "" else "${L["best"]}: ${best.score}"
            }
        }
    }

    private val modeIcon = when (mode) { Mode.ENDLESS -> Icons.TOWER; Mode.DAILY -> Icons.CALENDAR; else -> Icons.CROSSED_SWORDS }
    private val diffIcons = intArrayOf(Icons.SHIELD, Icons.CROSSED_SWORDS, Icons.FLAME, Icons.SKULL)

    override fun layout() {
        chrome.layoutBack(back)
        val pw = min(600 * dp, w - g.insetL - g.insetR - 40 * dp)
        val avail = h - chrome.top - g.insetB - 26 * dp
        val ph = min(avail, 440 * dp)
        val top = chrome.top + 14 * dp + max(0f, (avail - ph) * 0.4f)
        panel.set(w / 2 - pw / 2, top, w / 2 + pw / 2, top + ph)
        start.set(w / 2 - 110 * dp, panel.bottom - 70 * dp, 220 * dp, 52 * dp)
        start.highlight = true
        start.iconId = Icons.PLAY
        val gap = 8 * dp
        val dw = (pw - 44 * dp - gap * 3) / 4
        for (i in 0..3) {
            diff[i].set(panel.left + 22 * dp + i * (dw + gap), start.r.top - 58 * dp, dw, 44 * dp)
            diff[i].label = L["diff_$i"]
            diff[i].iconId = diffIcons[i]
            diff[i].visible = mode == Mode.SKIRMISH
        }
    }

    override fun update(dt: Float) {
        this.dt = dt
        val today = Daily.dayNumber()
        if (today != day) { day = today; dailyCfg = null; labelTimer = 0f }
        labelTimer -= dt
        if (labelTimer <= 0f) { labelTimer = 1f; refreshLabels() }
        start.label = L["play"]
        for (i in 0..3) diff[i].selected = g.profile.skirmishDiff == i
    }

    private fun line(c: Canvas, icon: Int, s: String, x: Float, y: Float, color: Int = Ui.TEXT, size: Float = 15f): Float {
        val isz = 22 * dp
        Icons.draw(c, icon, x + isz / 2, y - 5 * dp, isz, dp = dp)
        ui.textO(c, s, x + isz + 10 * dp, y, size, color)
        return 30 * dp
    }

    override fun render(c: Canvas) {
        bg.draw(c, w, h, dt)
        chrome.dim(c, 100)
        ui.panel(c, panel)
        val pr = g.profile
        val title = when (mode) { Mode.ENDLESS -> L["endless"]; Mode.DAILY -> L["daily"]; else -> L["skirmish"] }
        ui.ribbon(c, panel.centerX(), panel.top + 4 * dp, title, 22f, Ui.RIBBON, panel.width() * 0.5f, Ui.GOLD, panel.width() * 0.9f)

        val mr = 42 * dp
        val mx = panel.left + 26 * dp + mr
        val my = panel.top + 52 * dp + mr
        ui.medallion(c, mx, my, mr, Ui.ERA[when (mode) { Mode.ENDLESS -> 3; Mode.DAILY -> Defs.ERA_COUNT - 1; else -> 2 }], modeIcon)
        val x = mx + mr + 18 * dp
        var y = panel.top + 58 * dp
        val tw = panel.right - 24 * dp - x
        val lowLimit = if (mode == Mode.SKIRMISH) diff[0].r.top - 26 * dp else start.r.top - 10 * dp
        when (mode) {
            Mode.ENDLESS -> {
                y += ui.wrap(c, L["endless_info"], x, y, tw, 15f)
                y += 12 * dp
                y += line(c, Icons.TROPHY, endlessBest, x, y, Ui.GOLD, 16f)
                if (endlessScore.isNotEmpty()) line(c, Icons.STAR, endlessScore, x, y)
            }
            Mode.DAILY -> {
                // Konfiguracja i tekst modyfikatorów raz na dzień, nie w każdej klatce.
                y += ui.wrap(c, L["daily_info"], x, y, tw, 14f)
                y += 8 * dp
                y += line(c, Icons.TOWER, dailyRange, x, y, Ui.TEXT, 14f)
                y += line(c, Icons.SKULL, dailyPower, x, y, Ui.BAD, 14f)
                val isz = 22 * dp
                Icons.draw(c, Icons.FLAG, x + isz / 2, y - 5 * dp, isz, dp = dp)
                y += ui.wrap(c, dailyMods, x + isz + 10 * dp, y, tw - isz - 10 * dp, 14f, Ui.GOLD)
                y += 4 * dp
                val ranked = pr.lastRankedDaily != day
                if (y < lowLimit) y += line(c, if (ranked) Icons.CHECK else Icons.LOCK, dailyRanked, x, y, if (ranked) Ui.GOOD else Ui.DIM, 14f)
                if (y < lowLimit && dailyScore.isNotEmpty()) line(c, Icons.TROPHY, dailyScore, x, y, Ui.TEXT, 14f)
            }
            else -> {
                y += line(c, Icons.TOWER, skirmishRange, x, y)
                y += line(c, Icons.SKULL, skirmishPower, x, y, if (aiPower() > 1f) Ui.BAD else Ui.GOOD)
                ui.bar(c, x + 32 * dp, y - 14 * dp, min(tw - 32 * dp, 220 * dp), 9 * dp, aiPower() / 1.8f, if (aiPower() > 1f) Ui.BAD else Ui.GOOD)
                y += 14 * dp
                if (y < lowLimit && skirmishScore.isNotEmpty()) line(c, Icons.TROPHY, skirmishScore, x, y, Ui.TEXT, 14f)
                // separator + etykieta trudności
                row.set(panel.left + 22 * dp, diff[0].r.top - 30 * dp, panel.right - 22 * dp, diff[0].r.top - 28 * dp)
                ui.fill.color = Color.argb(70, 255, 255, 255); c.drawRect(row, ui.fill)
                ui.textO(c, L["difficulty"], panel.centerX(), diff[0].r.top - 10 * dp, 14f, Ui.GOLD, Paint.Align.CENTER)
            }
        }
        for (b in buttons) if (b !== start) ui.button(c, b, if (b === back) 16f else 14f)
        ui.button(c, start, 20f)
    }

    private fun aiPower() = SKIRMISH_POWER[g.profile.skirmishDiff.coerceIn(0, 3)]

    private companion object {
        val SKIRMISH_POWER = floatArrayOf(0.8f, 1.05f, 1.3f, 1.6f)
    }

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
            in 0..3 -> { pr.skirmishDiff = b.id; pr.save(); labelTimer = 0f }
        }
    }
}
