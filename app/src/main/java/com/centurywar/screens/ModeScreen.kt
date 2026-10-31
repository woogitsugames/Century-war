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
import com.centurywar.game.Weekly
import com.centurywar.meta.Profile
import kotlin.math.max
import kotlin.math.min
import java.util.Locale

/** Ekran przed bitwą dla trybów: bez końca, wyzwanie dnia, potyczka. */
class ModeScreen(g: Game, private val mode: Mode, private var weeklySelected: Boolean = false) : Screen(g) {
    private val bg = MenuBg()
    private val chrome = Chrome(this)
    private val back = btn(-1)
    private val start = btn(-2)
    private val diff = Array(4) { btn(it) }
    private val dailyTab = btn(10)
    private val weeklyTab = btn(11)
    private val panel = RectF()
    private val row = RectF()
    private var dt = 0f
    /** Dzień UTC: odświeżany w update(), żeby ekran otwarty przez północ nie startował wczorajszego wyzwania. */
    private var day = Daily.dayNumber()
    private var dailyCfg: BattleConfig? = null
    private var dailyMods = ""
    private var week = -1L
    private var weeklyCfg: BattleConfig? = null
    private var weeklyLang = -1
    private var weeklySecond = -1L
    private var weeklyInfo = ""
    private var weeklyEras = ""
    private var weeklyMods = ""
    private var weeklyCard = ""
    private var weeklyBest = ""
    private var weeklyCountdown = ""
    private val tierNames = Array(3) { "" }
    private val tierColors = intArrayOf(
        Color.rgb(191, 117, 62), Color.rgb(184, 194, 211), Color.rgb(236, 193, 62),
    )

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
        val tabWidth = min(118 * dp, (pw - 42 * dp) / 2f)
        dailyTab.set(panel.centerX() - tabWidth - 4 * dp, panel.top + 35 * dp, tabWidth, 32 * dp)
        weeklyTab.set(panel.centerX() + 4 * dp, panel.top + 35 * dp, tabWidth, 32 * dp)
        dailyTab.label = L["weekly_day_tab"]
        weeklyTab.label = L["weekly_week_tab"]
        dailyTab.iconId = Icons.CALENDAR
        weeklyTab.iconId = Icons.MEDAL
        dailyTab.visible = mode == Mode.DAILY
        weeklyTab.visible = mode == Mode.DAILY
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
        if (today != day) { day = today; dailyCfg = null }
        if (mode == Mode.DAILY) {
            if (week != Weekly.weekNumber(today) || weeklyLang != L.lang) rebuildWeekly(today)
            val second = System.currentTimeMillis() / 1000L
            if (second != weeklySecond) {
                weeklySecond = second
                val remaining = ((week * 7L + 4L) * 86_400L - second).coerceAtLeast(0L)
                val clock = String.format(Locale.ROOT, "%dd %02d:%02d:%02d",
                    remaining / 86_400L, remaining / 3600L % 24L,
                    remaining / 60L % 60L, remaining % 60L)
                weeklyCountdown = L.f("weekly_ends", clock)
            }
        }
        dailyTab.selected = !weeklySelected
        weeklyTab.selected = weeklySelected
        start.label = L["play"]
        for (i in 0..3) diff[i].selected = g.profile.skirmishDiff == i
    }

    /** Odświeżenie opisów wyłącznie przy zmianie tygodnia lub języka. */
    private fun rebuildWeekly(today: Long) {
        week = Weekly.weekNumber(today)
        weeklyLang = L.lang
        weeklySecond = -1L
        g.profile.syncWeekly(week)
        val cfg = Weekly.config(week)
        weeklyCfg = cfg
        weeklyInfo = L["weekly_info"]
        weeklyEras = L.f("eras_range", L[Defs.eraKeys[cfg.startEra]], L[Defs.eraKeys[cfg.maxEra]])
        weeklyMods = L["modifiers"] + ": " + cfg.modifiers.joinToString(", ") { L[it.key] }
        weeklyCard = L["weekly_start_card"] + ": " + L[cfg.weeklyCard!!.textKey]
        val score = g.profile.top(Profile.weeklyBoard(week)).firstOrNull()?.score
        weeklyBest = L["best"] + ": " + (score?.toString() ?: L["no_scores"])
        for (i in 0 until Weekly.TIER_COUNT) {
            tierNames[i] = L.f("weekly_tier", L[when (i) {
                0 -> "weekly_bronze"; 1 -> "weekly_silver"; else -> "weekly_gold"
            }], Weekly.threshold(i), Weekly.medalReward(i))
        }
    }

    /** Karta tygodnia ma zwartą siatkę, by zmieścić się także na 720p. */
    private fun drawWeekly(c: Canvas, pr: Profile) {
        val x = panel.left + 20 * dp
        val full = panel.width() - 40 * dp
        val colWidth = (full - 16 * dp) / 2f
        var y = panel.top + 81 * dp
        y += ui.wrap(c, weeklyInfo, x, y, full, 13f)
        y += 11 * dp
        val leftX = x
        val rightX = x + colWidth + 16 * dp
        val leftTop = y
        Icons.draw(c, Icons.TOWER, leftX + 9 * dp, y - 4 * dp, 18 * dp, dp = dp)
        ui.textO(c, weeklyEras, leftX + 23 * dp, y, ui.fit(weeklyEras, 13f, colWidth - 23 * dp), Ui.TEXT)
        y += 28 * dp
        Icons.draw(c, Icons.CROWN, leftX + 9 * dp, y - 4 * dp, 18 * dp, dp = dp)
        ui.textO(c, weeklyCard, leftX + 23 * dp, y, ui.fit(weeklyCard, 13f, colWidth - 23 * dp), Ui.GOLD)
        y += 28 * dp
        Icons.draw(c, Icons.HOURGLASS, leftX + 9 * dp, y - 4 * dp, 18 * dp, dp = dp)
        ui.textO(c, weeklyCountdown, leftX + 23 * dp, y, ui.fit(weeklyCountdown, 13f, colWidth - 23 * dp), Ui.TEXT)

        var ry = leftTop
        Icons.draw(c, Icons.FLAG, rightX + 9 * dp, ry - 4 * dp, 18 * dp, dp = dp)
        ry += ui.wrap(c, weeklyMods, rightX + 23 * dp, ry, colWidth - 23 * dp, 12f, Ui.TEXT)
        ry += 8 * dp
        Icons.draw(c, Icons.TROPHY, rightX + 9 * dp, ry - 4 * dp, 18 * dp, dp = dp)
        ui.textO(c, weeklyBest, rightX + 23 * dp, ry, ui.fit(weeklyBest, 13f, colWidth - 23 * dp), Ui.GOLD)

        val medalY = start.r.top - 42 * dp
        val mask = pr.weeklyClaimedFor(week)
        for (i in 0 until Weekly.TIER_COUNT) {
            val centerX = x + (i + 0.5f) * full / Weekly.TIER_COUNT
            val claimed = mask and (1 shl i) != 0
            Icons.draw(c, Icons.MEDAL, centerX - 38 * dp, medalY - 1 * dp,
                24 * dp, tint = if (claimed) tierColors[i] else Ui.DIM, dp = dp)
            ui.textO(c, tierNames[i], centerX - 23 * dp, medalY + 4 * dp,
                ui.fit(tierNames[i], 11f, full / 3f - 35 * dp),
                if (claimed) tierColors[i] else Ui.DIM)
        }
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
        ui.ribbon(c, panel.centerX(), panel.top + 4 * dp, if (mode == Mode.DAILY && weeklySelected) L["weekly"] else title, 22f, Ui.RIBBON, panel.width() * 0.5f, Ui.GOLD, panel.width() * 0.9f)
        if (mode == Mode.DAILY) {
            ui.button(c, dailyTab, 13f)
            ui.button(c, weeklyTab, 13f)
        }
        if (mode == Mode.DAILY && weeklySelected) {
            drawWeekly(c, pr)
            for (b in buttons) if (b !== start && b !== dailyTab && b !== weeklyTab) ui.button(c, b, 14f)
            ui.button(c, start, 20f)
            return
        }

        val mr = 42 * dp
        val mx = panel.left + 26 * dp + mr
        val my = panel.top + (if (mode == Mode.DAILY) 87 else 52) * dp + mr
        ui.medallion(c, mx, my, mr, Ui.ERA[when (mode) { Mode.ENDLESS -> 3; Mode.DAILY -> Defs.ERA_COUNT - 1; else -> 2 }], modeIcon)
        val x = mx + mr + 18 * dp
        var y = panel.top + (if (mode == Mode.DAILY) 93 else 58) * dp
        val tw = panel.right - 24 * dp - x
        val lowLimit = if (mode == Mode.SKIRMISH) diff[0].r.top - 26 * dp else start.r.top - 10 * dp
        when (mode) {
            Mode.ENDLESS -> {
                y += ui.wrap(c, L["endless_info"], x, y, tw, 15f)
                y += 12 * dp
                y += line(c, Icons.TROPHY, "${L["best"]}: ${L.f("wave", pr.bestWave)}", x, y, Ui.GOLD, 16f)
                val top = pr.top(Profile.BOARD_ENDLESS).firstOrNull()
                if (top != null) line(c, Icons.STAR, "${L["score"]}: ${top.score}", x, y)
            }
            Mode.DAILY -> {
                // Konfiguracja i tekst modyfikatorów raz na dzień, nie w każdej klatce.
                val cfg = dailyCfg ?: Daily.config(day, pr.bonuses()).also { dc ->
                    dailyCfg = dc
                    dailyMods = L["modifiers"] + ": " + dc.modifiers.joinToString(", ") { L[it.key] }
                }
                y += ui.wrap(c, L["daily_info"], x, y, tw, 14f)
                y += 8 * dp
                y += line(c, Icons.TOWER, L.f("eras_range", L[Defs.eraKeys[cfg.startEra]], L[Defs.eraKeys[cfg.maxEra]]), x, y, Ui.TEXT, 14f)
                y += line(c, Icons.SKULL, L.f("ai_power", (cfg.aiPower * 100).toInt()), x, y, Ui.BAD, 14f)
                val isz = 22 * dp
                Icons.draw(c, Icons.FLAG, x + isz / 2, y - 5 * dp, isz, dp = dp)
                y += ui.wrap(c, dailyMods, x + isz + 10 * dp, y, tw - isz - 10 * dp, 14f, Ui.GOLD)
                y += 4 * dp
                val ranked = pr.lastRankedDaily != day
                if (y < lowLimit) y += line(c, if (ranked) Icons.CHECK else Icons.LOCK, L.f("daily_ranked", if (ranked) L["daily_free"] else L["daily_used"]), x, y, if (ranked) Ui.GOOD else Ui.DIM, 14f)
                if (y < lowLimit) pr.top(Profile.dailyBoard(day)).firstOrNull()?.let { line(c, Icons.TROPHY, "${L["best"]}: ${it.score}", x, y, Ui.TEXT, 14f) }
            }
            else -> {
                y += line(c, Icons.TOWER, L.f("eras_range", L[Defs.eraKeys[0]], L[Defs.eraKeys[Defs.ERA_COUNT - 1]]), x, y)
                y += line(c, Icons.SKULL, L.f("ai_power", (aiPower() * 100).toInt()), x, y, if (aiPower() > 1f) Ui.BAD else Ui.GOOD)
                ui.bar(c, x + 32 * dp, y - 14 * dp, min(tw - 32 * dp, 220 * dp), 9 * dp, aiPower() / 1.8f, if (aiPower() > 1f) Ui.BAD else Ui.GOOD)
                y += 14 * dp
                if (y < lowLimit) pr.top(Profile.BOARD_SKIRMISH).firstOrNull()?.let { line(c, Icons.TROPHY, "${L["best"]}: ${it.score}", x, y, Ui.TEXT, 14f) }
                // separator + etykieta trudności
                row.set(panel.left + 22 * dp, diff[0].r.top - 30 * dp, panel.right - 22 * dp, diff[0].r.top - 28 * dp)
                ui.fill.color = Color.argb(70, 255, 255, 255); c.drawRect(row, ui.fill)
                ui.textO(c, L["difficulty"], panel.centerX(), diff[0].r.top - 10 * dp, 14f, Ui.GOLD, Paint.Align.CENTER)
            }
        }
        for (b in buttons) if (b !== start && b !== dailyTab && b !== weeklyTab) ui.button(c, b, if (b === back) 16f else 14f)
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
                        if (weeklySelected) {
                            val currentWeek = Weekly.currentWeek()
                            g.setScreen(BattleScreen(g, Weekly.config(currentWeek)))
                        } else {
                            val today = Daily.dayNumber()
                            val ranked = pr.lastRankedDaily != today
                            if (ranked) { pr.lastRankedDaily = today; pr.save() }
                            g.setScreen(BattleScreen(g, Daily.config(today, pr.bonuses()), ranked))
                        }
                    }
                    else -> g.setScreen(BattleScreen(g, BattleConfig(Mode.SKIRMISH, seed, aiPower(), playerBonuses = pr.bonuses())))
                }
            }
            in 0..3 -> { pr.skirmishDiff = b.id; pr.save() }
            10 -> weeklySelected = false
            11 -> weeklySelected = true
        }
    }
}
