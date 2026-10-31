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
    private val replayListButton = btn(102)
    private val statsButton = btn(103)
    private val rankLabels = Array(20) { (it + 1).toString() + "." }
    private var cachedEntries: List<com.centurywar.meta.ScoreEntry> = emptyList()
    private var cachedScores = emptyArray<String>()
    private val global = btn(-3)
    private val globalScores = btn(-4)
    private val periodToggle = btn(104)
    private val tabs = Array(5) { btn(it) }
    private var tab = 0
    private val panel = RectF()
    private val row = RectF()
    private var dt = 0f
    private var existingReplayIds: Set<String> = emptySet()
    private var day = Daily.dayNumber()
    private var week = Weekly.weekNumber(day)
    private var previousWeek = false
    private var periodLabelWeek = Long.MIN_VALUE
    private var periodLabel = ""
    private var periodBoard = ""
    private var statsTimer = 0f
    private val tabIcons = intArrayOf(Icons.TOWER, Icons.CALENDAR, Icons.MAP, Icons.CROSSED_SWORDS, Icons.MEDAL)
    private val podium = intArrayOf(Color.rgb(255, 200, 50), Color.rgb(200, 210, 222), Color.rgb(205, 127, 60))
    private fun refreshReplayIds() {
        g.replays?.listAsync { records ->
            val ids = records.mapTo(HashSet()) { it.id }
            g.post { existingReplayIds = ids }
        }
    }

    private fun board(i: Int) = when (i) {
        0 -> Profile.BOARD_ENDLESS
        1 -> Profile.dailyBoard(day)
        2 -> Profile.BOARD_CAMPAIGN
        3 -> Profile.BOARD_SKIRMISH
        else -> periodBoard
    }

    private fun cacheRows() {
        cachedEntries = g.profile.top(board(tab)).toList()
        cachedScores = Array(cachedEntries.size) { cachedEntries[it].score.toString() }
    }

    override fun layout() {
        refreshReplayIds()
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
        replayListButton.label = L["replay_list"]
        replayListButton.iconId = Icons.PLAY
        replayListButton.set(panel.left + 12 * dp, panel.top + 8 * dp, aw, 38 * dp)
        replayListButton.visible = tab != 4
        periodToggle.set(panel.left + 12 * dp, panel.top + 8 * dp, min(160 * dp, pw * 0.43f), 38 * dp)
        periodToggle.iconId = Icons.CALENDAR
        periodToggle.visible = tab == 4
        periodToggle.label = L[if (previousWeek) "weekly_current" else "weekly_previous"]
        statsButton.label = L["stats"]
        statsButton.iconId = Icons.CHART
        val sw = min(200 * dp, pw * 0.48f)
        statsButton.set(panel.centerX() - sw / 2, panel.bottom - 50 * dp, sw, 31 * dp)
        cacheRows()
        global.visible = g.online.available && tab != 4
        globalScores.visible = g.scores != null && g.profile.globalRanking
        val both = global.visible && globalScores.visible
        val gw = min(if (both) 185 * dp else 270 * dp, (pw - 36 * dp - if (both) 12 * dp else 0f) / if (both) 2 else 1)
        if (global.visible) {
            global.label = L["play_games"]
            global.iconId = Icons.TROPHY
            global.set(panel.centerX() - if (both) gw + 6 * dp else gw / 2, panel.bottom - 96 * dp, gw, 38 * dp)
        }
        if (globalScores.visible) {
            globalScores.label = L["global_ranking"]
            globalScores.iconId = Icons.GLOBE
            globalScores.set(panel.centerX() + if (both) 6 * dp else -gw / 2, panel.bottom - 96 * dp, gw, 38 * dp)
        }
    }

    override fun update(dt: Float) {
        this.dt = dt
        statsTimer -= dt
        if (statsTimer <= 0f) { statsTimer = 1f; cacheRows() }
        // Dostępność PGS może zmienić się w tle (zamknięcie Activity): układ liczymy na nowo.
        val newDay = Daily.dayNumber()
        if (newDay != day) {
            day = newDay
            week = Weekly.weekNumber(day)
            g.profile.syncWeekly(week)
        }
        if (global.visible != (g.online.available && tab != 4) || periodToggle.visible != (tab == 4) ||
            globalScores.visible != (g.scores != null && g.profile.globalRanking)) layout()
        periodToggle.label = L[if (previousWeek) "weekly_current" else "weekly_previous"]
        val selectedWeek = if (previousWeek) week - 1L else week
        if (periodLabelWeek != selectedWeek) {
            periodLabelWeek = selectedWeek
            periodLabel = L.f("weekly_week_number", selectedWeek)
            periodBoard = Profile.weeklyBoard(selectedWeek)
            cacheRows()
        }
        for (i in 0..4) { tabs[i].selected = i == tab; tabs[i].color = if (i == tab) Color.rgb(84, 104, 140) else Ui.BTN }
    }

    override fun render(c: Canvas) {
        bg.draw(c, w, h, dt)
        chrome.dim(c, 120)
        ui.panel(c, panel)
        for (b in tabs) ui.button(c, b, 13f)
        ui.button(c, back, 16f); ui.button(c, rename, 14f)
        ui.button(c, achievementsButton, 13f)
        if (replayListButton.visible) ui.button(c, replayListButton, 13f)
        if (periodToggle.visible) ui.button(c, periodToggle, 13f)
        ui.button(c, statsButton, 12f)
        if (global.visible) ui.button(c, global, 12f)
        if (globalScores.visible) ui.button(c, globalScores, 14f)
        chrome.title(c, L["rankings"])
        val list = cachedEntries
        val inner = 16 * dp
        val l = panel.left + inner
        val r = panel.right - inner
        var y = panel.top + 54 * dp
        if (tab == 4) {
            ui.textO(c, periodLabel,
                panel.centerX(), y + 2 * dp, 12f, Ui.GOLD, Paint.Align.CENTER)
            y += 20 * dp
        }
        val bottomLimit = panel.bottom - (if (global.visible || globalScores.visible) 112 * dp else 62 * dp)
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
                ui.textO(c, rankLabels[i], l + 22 * dp, cy + 5 * dp, 13f, Ui.TEXT, Paint.Align.CENTER, 2f)
            } else {
                ui.textO(c, rankLabels[i], l + 22 * dp, cy + 5 * dp, 14f, Ui.DIM, Paint.Align.CENTER, 1.6f)
            }
            val col = if (i == 0) Ui.GOLD else if (me) Ui.XP else Ui.TEXT
            if (me) Icons.draw(c, Icons.HELMET, l + 52 * dp, cy, 20 * dp, dp = dp)
            ui.textO(c, e.name, l + (if (me) 68 else 48) * dp, cy + 5 * dp, 15f, col, Paint.Align.LEFT, 1.8f)
            val hasReplay = e.replay in existingReplayIds
            if (hasReplay) Icons.draw(c, Icons.PLAY, r - 15 * dp, cy, 17 * dp, Ui.GOLD, dp = dp)
            ui.textO(c, cachedScores[i], r - (if (hasReplay) 33 else 12) * dp,
                cy + 5 * dp, 15f, col, Paint.Align.RIGHT, 1.8f)
            y += lh
        }
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

    override fun onTap(x: Float, y: Float) {
        if (x < panel.left || x > panel.right) return
        val index = ((y - (panel.top + 54 * dp)) / (34 * dp)).toInt()
        if (y < panel.top + 54 * dp || y >= panel.top + 54 * dp + 34 * dp * (index + 1)) return
        val bottomLimit = panel.bottom - (if (global.visible) 102 * dp else 52 * dp)
        if (y + 34 * dp > bottomLimit) return
        val id = g.profile.top(board(tab)).getOrNull(index)?.replay ?: return
        if (id in existingReplayIds) g.push(ReplayScreen(g, id))
    }

    override fun onClick(b: Button) {
        when (b.id) {
            -1 -> g.pop()
            101 -> g.push(AchievementsScreen(g))
            102 -> g.push(ReplayListScreen(g))
            103 -> g.push(StatsScreen(g))
            104 -> previousWeek = !previousWeek
            -3 -> if (g.online.available && tab != 4) g.online.showUi(board(tab))
            -4 -> if (g.scores != null && g.profile.globalRanking) g.push(GlobalRankingsScreen(g))
            -2 -> g.host.requestText(L["name"], g.profile.name) { n -> g.profile.name = n; g.profile.save() }
            in 0..4 -> { tab = b.id; cacheRows() }
        }
    }
}
