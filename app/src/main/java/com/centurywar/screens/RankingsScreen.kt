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
import com.centurywar.game.Daily
import com.centurywar.game.Weekly
import com.centurywar.meta.OnlineState
import com.centurywar.meta.Profile
import kotlin.math.min

class RankingsScreen(g: Game) : Screen(g) {
    private val bg = MenuBg()
    private val chrome = Chrome(this)
    private val back = btn(-1)
    private val rename = btn(-2)
    private val achievementsButton = btn(101)
    private val global = btn(-3)
    private val periodToggle = btn(102)
    private val tabs = Array(5) { btn(it) }
    private var tab = 0
    private val panel = RectF()
    private val row = RectF()
    private var dt = 0f
    private var day = Daily.dayNumber()
    private var week = Weekly.weekNumber(day)
    private var previousWeek = false
    private val tabIcons = intArrayOf(Icons.TOWER, Icons.CALENDAR, Icons.MAP, Icons.CROSSED_SWORDS, Icons.MEDAL)
    private val podium = intArrayOf(Color.rgb(255, 200, 50), Color.rgb(200, 210, 222), Color.rgb(205, 127, 60))

    private fun board(i: Int) = when (i) {
        0 -> Profile.BOARD_ENDLESS
        1 -> Profile.dailyBoard(day)
        2 -> Profile.BOARD_CAMPAIGN
        3 -> Profile.BOARD_SKIRMISH
        else -> Profile.weeklyBoard(if (previousWeek) week - 1L else week)
    }

    override fun layout() {
        chrome.layoutBack(back)
        rename.label = L["name"]
        rename.iconId = Icons.PENCIL
        val rw = min(200 * dp, ui.measure(rename.label, 14f, true) + 70 * dp)
        rename.set(w - g.insetR - rw - 12 * dp, g.insetT + 10 * dp, rw, 44 * dp)
        val keys = arrayOf("board_endless", "board_daily", "board_campaign", "board_skirmish", "board_weekly")
        val pw = min(1040 * dp, w - g.insetL - g.insetR - 32 * dp)
        val gap = 6 * dp
        val tw = (pw - gap * 4) / 5
        val l = w / 2 - pw / 2
        for (i in 0..4) {
            tabs[i].label = L[keys[i]]
            tabs[i].iconId = tabIcons[i]
            tabs[i].set(l + i * (tw + gap), chrome.top + 4 * dp, tw, 42 * dp)
        }
        panel.set(l, chrome.top + 56 * dp, l + pw, h - g.insetB - 12 * dp)
        achievementsButton.label = L["achievements"]
        achievementsButton.iconId = Icons.MEDAL
        val aw = min(178 * dp, pw * 0.48f)
        achievementsButton.set(panel.right - aw - 12 * dp, panel.top + 8 * dp, aw, 38 * dp)
        periodToggle.set(panel.left + 12 * dp, panel.top + 8 * dp, min(160 * dp, pw * 0.43f), 38 * dp)
        periodToggle.iconId = Icons.CALENDAR
        periodToggle.visible = tab == 4
        periodToggle.label = L[if (previousWeek) "weekly_current" else "weekly_previous"]
        global.visible = g.online.available && tab != 4
        if (global.visible) {
            global.label = L["global_ranking"]
            global.iconId = Icons.GLOBE
            val gw = min(270 * dp, pw - 32 * dp)
            global.set(panel.centerX() - gw / 2, panel.bottom - 96 * dp, gw, 38 * dp)
        }
    }

    override fun update(dt: Float) {
        this.dt = dt
        // Dostępność PGS może zmienić się w tle (zamknięcie Activity): układ liczymy na nowo.
        val newDay = Daily.dayNumber()
        if (newDay != day) {
            day = newDay
            week = Weekly.weekNumber(day)
            g.profile.syncWeekly(week)
        }
        if (global.visible != (g.online.available && tab != 4) || periodToggle.visible != (tab == 4)) layout()
        periodToggle.label = L[if (previousWeek) "weekly_current" else "weekly_previous"]
        for (i in 0..4) { tabs[i].selected = i == tab; tabs[i].color = if (i == tab) Color.rgb(84, 104, 140) else Ui.BTN }
    }

    override fun render(c: Canvas) {
        bg.draw(c, w, h, dt)
        chrome.dim(c, 120)
        ui.panel(c, panel)
        for (b in tabs) ui.button(c, b, 13f)
        ui.button(c, back, 16f); ui.button(c, rename, 14f)
        ui.button(c, achievementsButton, 13f)
        if (periodToggle.visible) ui.button(c, periodToggle, 13f)
        if (global.visible) ui.button(c, global, 14f)
        chrome.title(c, L["rankings"])
        val list = g.profile.top(board(tab))
        val inner = 16 * dp
        val l = panel.left + inner
        val r = panel.right - inner
        var y = panel.top + 54 * dp
        if (tab == 4) {
            ui.textO(c, L.f("weekly_week_number", if (previousWeek) week - 1L else week),
                panel.centerX(), y + 2 * dp, 12f, Ui.GOLD, Paint.Align.CENTER)
            y += 20 * dp
        }
        val bottomLimit = panel.bottom - (if (global.visible) 102 * dp else 52 * dp)
        if (list.isEmpty()) {
            Icons.draw(c, Icons.TROPHY, panel.centerX(), y + 50 * dp, 60 * dp, dp = dp)
            ui.textO(c, L["no_scores"], panel.centerX(), y + 110 * dp, 17f, Ui.DIM, Paint.Align.CENTER)
        }
        val lh = 34 * dp
        for ((i, e) in list.withIndex()) {
            if (y + lh > bottomLimit) break
            val me = e.name == g.profile.name
            row.set(l, y, r, y + lh - 4 * dp)
            ui.fill.color = when {
                me -> Color.argb(110, 64, 140, 235)
                i % 2 == 0 -> Color.argb(40, 255, 255, 255)
                else -> Color.argb(14, 255, 255, 255)
            }
            c.drawRoundRect(row, 8 * dp, 8 * dp, ui.fill)
            val cy = row.centerY()
            if (i < 3) {
                val rr = 12 * dp
                ui.fill.color = Ui.OUTLINE; c.drawCircle(l + 22 * dp, cy, rr, ui.fill)
                ui.fill.color = podium[i]; c.drawCircle(l + 22 * dp, cy, rr - 2 * dp, ui.fill)
                ui.textO(c, (i + 1).toString(), l + 22 * dp, cy + 5 * dp, 13f, Ui.TEXT, Paint.Align.CENTER, 2f)
            } else {
                ui.textO(c, "${i + 1}.", l + 22 * dp, cy + 5 * dp, 14f, Ui.DIM, Paint.Align.CENTER, 1.6f)
            }
            val col = if (i == 0) Ui.GOLD else if (me) Ui.XP else Ui.TEXT
            if (me) Icons.draw(c, Icons.HELMET, l + 52 * dp, cy, 20 * dp, dp = dp)
            ui.textO(c, e.name, l + (if (me) 68 else 48) * dp, cy + 5 * dp, 15f, col, Paint.Align.LEFT, 1.8f)
            ui.textO(c, e.score.toString(), r - 12 * dp, cy + 5 * dp, 15f, col, Paint.Align.RIGHT, 1.8f)
            y += lh
        }
        val pr = g.profile
        val hrs = pr.playSeconds / 3600f
        row.set(l, panel.bottom - 48 * dp, r, panel.bottom - 46 * dp)
        ui.fill.color = Color.argb(60, 255, 255, 255); c.drawRect(row, ui.fill)
        ui.textO(c, "${L["stats"]}: ${pr.wins}/${pr.battles}  •  ${L["kills"]} ${pr.totalKills}  •  ${"%.1f".format(hrs)} h",
            panel.centerX(), panel.bottom - 28 * dp, 12f, Ui.TEXT, Paint.Align.CENTER, 1.6f)
        val onlineStatus = if (!global.visible) "online_note" else when (g.online.state) {
            OnlineState.CONNECTED -> "pgs_connected"
            OnlineState.SYNCING -> "pgs_syncing"
            OnlineState.CHECKING -> "pgs_checking"
            OnlineState.SIGNED_OUT -> "pgs_signed_out"
            OnlineState.ERROR -> "pgs_error"
            else -> "pgs_disabled"
        }
        ui.text(c, L[onlineStatus], panel.centerX(), panel.bottom - 13 * dp, 10f, Ui.DIM, Paint.Align.CENTER)
    }

    override fun onClick(b: Button) {
        when (b.id) {
            -1 -> g.pop()
            101 -> g.push(AchievementsScreen(g))
            -3 -> if (g.online.available && tab != 4) g.online.showUi(board(tab))
            -2 -> g.host.requestText(L["name"], g.profile.name) { n -> g.profile.name = n; g.profile.save() }
            in 0..4 -> tab = b.id
            102 -> previousWeek = !previousWeek
        }
    }
}
