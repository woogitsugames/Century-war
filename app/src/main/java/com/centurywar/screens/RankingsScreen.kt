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
    private val tabs = Array(4) { btn(it) }
    private var tab = 0
    private val panel = RectF()
    private val row = RectF()
    private var dt = 0f
    private val day = Daily.dayNumber()
    private val tabIcons = intArrayOf(Icons.TOWER, Icons.CALENDAR, Icons.MAP, Icons.CROSSED_SWORDS)
    private val podium = intArrayOf(Color.rgb(255, 200, 50), Color.rgb(200, 210, 222), Color.rgb(205, 127, 60))
    private val podiumLabels = arrayOf("1", "2", "3")
    private val rankLabels = Array(101) { "${it + 1}." }
    private var entries = g.profile.top(Profile.BOARD_ENDLESS)
    private var scoreLabels = entries.map { it.score.toString() }
    private var statsText = ""
    private var statsTimer = 0f

    private fun refreshEntries() {
        entries = g.profile.top(board(tab))
        scoreLabels = entries.map { it.score.toString() }
    }

    private fun refreshStats() {
        val pr = g.profile
        statsText = "${L["stats"]}: ${pr.wins}/${pr.battles}  •  ${L["kills"]} ${pr.totalKills}  •  ${"%.1f".format(pr.playSeconds / 3600f)} h"
    }

    private fun board(i: Int) = when (i) {
        0 -> Profile.BOARD_ENDLESS
        1 -> Profile.dailyBoard(day)
        2 -> Profile.BOARD_CAMPAIGN
        else -> Profile.BOARD_SKIRMISH
    }

    override fun layout() {
        refreshEntries()
        refreshStats()
        chrome.layoutBack(back)
        rename.label = L["name"]
        rename.iconId = Icons.PENCIL
        val rw = min(200 * dp, ui.measure(rename.label, 14f, true) + 70 * dp)
        rename.set(w - g.insetR - rw - 12 * dp, g.insetT + 10 * dp, rw, 44 * dp)
        val keys = arrayOf("board_endless", "board_daily", "board_campaign", "board_skirmish")
        val pw = min(1040 * dp, w - g.insetL - g.insetR - 32 * dp)
        val gap = 6 * dp
        val tw = (pw - gap * 3) / 4
        val l = w / 2 - pw / 2
        for (i in 0..3) {
            tabs[i].label = L[keys[i]]
            tabs[i].iconId = tabIcons[i]
            tabs[i].set(l + i * (tw + gap), chrome.top + 4 * dp, tw, 42 * dp)
        }
        panel.set(l, chrome.top + 56 * dp, l + pw, h - g.insetB - 12 * dp)
        achievementsButton.label = L["achievements"]
        achievementsButton.iconId = Icons.MEDAL
        val aw = min(178 * dp, pw * 0.48f)
        achievementsButton.set(panel.right - aw - 12 * dp, panel.top + 8 * dp, aw, 38 * dp)
        global.visible = g.online.available
        if (global.visible) {
            global.label = L["global_ranking"]
            global.iconId = Icons.GLOBE
            val gw = min(270 * dp, pw - 32 * dp)
            global.set(panel.centerX() - gw / 2, panel.bottom - 96 * dp, gw, 38 * dp)
        }
    }

    override fun update(dt: Float) {
        this.dt = dt
        statsTimer -= dt
        if (statsTimer <= 0f) { statsTimer = 1f; refreshStats() }
        // Dostępność PGS może zmienić się w tle (zamknięcie Activity): układ liczymy na nowo.
        if (global.visible != g.online.available) layout()
        for (i in 0..3) { tabs[i].selected = i == tab; tabs[i].color = if (i == tab) Color.rgb(84, 104, 140) else Ui.BTN }
    }

    override fun render(c: Canvas) {
        bg.draw(c, w, h, dt)
        chrome.dim(c, 120)
        ui.panel(c, panel)
        for (b in tabs) ui.button(c, b, 13f)
        ui.button(c, back, 16f); ui.button(c, rename, 14f)
        ui.button(c, achievementsButton, 13f)
        if (global.visible) ui.button(c, global, 14f)
        chrome.title(c, L["rankings"])
        val list = entries
        val inner = 16 * dp
        val l = panel.left + inner
        val r = panel.right - inner
        var y = panel.top + 54 * dp
        val bottomLimit = panel.bottom - (if (global.visible) 102 * dp else 52 * dp)
        if (list.isEmpty()) {
            Icons.draw(c, Icons.TROPHY, panel.centerX(), y + 50 * dp, 60 * dp, dp = dp)
            ui.textO(c, L["no_scores"], panel.centerX(), y + 110 * dp, 17f, Ui.DIM, Paint.Align.CENTER)
        }
        val lh = 34 * dp
        for (i in list.indices) {
            if (y + lh > bottomLimit) break
            val e = list[i]
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
                ui.textO(c, podiumLabels[i], l + 22 * dp, cy + 5 * dp, 13f, Ui.TEXT, Paint.Align.CENTER, 2f)
            } else {
                ui.textO(c, rankLabels[i.coerceAtMost(100)], l + 22 * dp, cy + 5 * dp, 14f, Ui.DIM, Paint.Align.CENTER, 1.6f)
            }
            val col = if (i == 0) Ui.GOLD else if (me) Ui.XP else Ui.TEXT
            if (me) Icons.draw(c, Icons.HELMET, l + 52 * dp, cy, 20 * dp, dp = dp)
            ui.textO(c, e.name, l + (if (me) 68 else 48) * dp, cy + 5 * dp, 15f, col, Paint.Align.LEFT, 1.8f)
            ui.textO(c, scoreLabels[i], r - 12 * dp, cy + 5 * dp, 15f, col, Paint.Align.RIGHT, 1.8f)
            y += lh
        }
        row.set(l, panel.bottom - 48 * dp, r, panel.bottom - 46 * dp)
        ui.fill.color = Color.argb(60, 255, 255, 255); c.drawRect(row, ui.fill)
        ui.textO(c, statsText,
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
            -3 -> if (g.online.available) g.online.showUi(board(tab))
            -2 -> g.host.requestText(L["name"], g.profile.name) { n -> g.profile.name = n; g.profile.save() }
            in 0..3 -> { tab = b.id; refreshEntries() }
        }
    }
}
