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
import com.centurywar.game.art.Banners
import com.centurywar.meta.OnlineState
import com.centurywar.meta.Profile
import kotlin.math.min

class RankingsScreen(g: Game) : Screen(g) {
    override val crashName get() = "RankingsScreen"
    override val blocksNotices: Boolean get() = true
    private val bg = MenuBg()
    private val chrome = Chrome(this)
    private val banners = Banners()
    private val recordsTabs = RecordsTabs(this, 0)
    private val back = btn(-1)
    private val rename = btn(-2)
    private val rankLabels = Array(20) { (it + 1).toString() + "." }
    private var cachedEntries: List<com.centurywar.meta.ScoreEntry> = emptyList()
    private var cachedScores = emptyArray<String>()
    private var cachedDates = emptyArray<String>()
    private val global = btn(-3)
    private val globalScores = btn(-4)
    private val periodToggle = btn(104)
    private val tabs = Array(5) { btn(it) }
    private var tab = 0
    private val panel = RectF()
    private val row = RectF()
    private val titleRect = RectF()
    private var dt = 0f
    private var rowScroll = 0f
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
        cachedDates = Array(cachedEntries.size) {
            val dayValue = cachedEntries[it].day
            if (dayValue in 0L..100_000L) java.time.LocalDate.ofEpochDay(dayValue).toString()
            else L["records_result"]
        }
        rowScroll = rowScroll.coerceIn(0f, maxRowScroll())
    }

    override fun layout() {
        refreshReplayIds()
        chrome.layoutBack(back)
        recordsTabs.layout()
        // Pionowy Fold: przycisk nazwiska nie może nachodzić na wstęgę.
        rename.iconId = Icons.PENCIL
        rename.label = L["name"]
        val fullW = min(200 * dp, ui.measure(rename.label, 14f, true) + 70 * dp)
        rename.set(w - g.insetR - fullW - 12 * dp, g.insetT + 10 * dp, fullW, 48 * dp)
        // Przy 130% tekstu wstęga tytułu rośnie: wtedy zostaje sama ikona ołówka.
        chrome.titleBounds(L["records"], titleRect)
        val narrow = w - g.insetL - g.insetR < 700 * dp || RectF.intersects(titleRect, rename.r)
        if (narrow) {
            rename.label = ""
            rename.set(w - g.insetR - 56 * dp, g.insetT + 10 * dp, 48 * dp, 48 * dp)
        }
        val keys = arrayOf("endless", "board_daily", "board_campaign", "board_skirmish", "board_weekly")
        val shortKeys = arrayOf("endless", "daily", "campaign", "skirmish", "board_weekly")
        val pw = min(1040 * dp, w - g.insetL - g.insetR - 32 * dp)
        val gap = 6 * dp
        val tw = (pw - gap * 4) / 5
        val l = (w + g.insetL - g.insetR - pw) / 2f
        for (i in 0..4) {
            tabs[i].label = chrome.tabLabel(L[keys[i]], L[shortKeys[i]], tw, 48 * dp)
            tabs[i].iconId = tabIcons[i]
            tabs[i].set(l + i * (tw + gap), recordsTabs.contentTop + 4 * dp, tw, 48 * dp)
        }
        panel.set(l, recordsTabs.contentTop + 60 * dp, l + pw, h - g.insetB - 12 * dp)
        periodToggle.set(panel.left + 12 * dp, panel.top + 44 * dp,
            min(174 * dp, pw * 0.50f), 48 * dp)
        periodToggle.iconId = Icons.CALENDAR
        periodToggle.visible = tab == 4
        periodToggle.label = L[if (previousWeek) "weekly_current" else "weekly_previous"]
        cacheRows()
        global.visible = g.online.available && g.online.state == OnlineState.CONNECTED && tab != 4
        globalScores.visible = g.scores != null && g.profile.globalRanking
        val both = global.visible && globalScores.visible
        val gw = min(if (both) 185 * dp else 270 * dp, (pw - 36 * dp - if (both) 12 * dp else 0f) / if (both) 2 else 1)
        if (global.visible) {
            global.label = L["play_games"]
            global.iconId = Icons.TROPHY
            global.set(panel.centerX() - if (both) gw + 6 * dp else gw / 2, panel.bottom - 79 * dp, gw, 48 * dp)
        }
        if (globalScores.visible) {
            globalScores.label = L["global_ranking"]
            globalScores.iconId = Icons.GLOBE
            globalScores.set(panel.centerX() + if (both) 6 * dp else -gw / 2, panel.bottom - 79 * dp, gw, 48 * dp)
        }
        rowScroll = rowScroll.coerceIn(0f, maxRowScroll())
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
        if (global.visible != (g.online.available && g.online.state == OnlineState.CONNECTED && tab != 4) || periodToggle.visible != (tab == 4) ||
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
        if (periodToggle.visible) ui.button(c, periodToggle, 13f)
        if (global.visible) ui.button(c, global, 12f)
        if (globalScores.visible) ui.button(c, globalScores, 14f)
        recordsTabs.draw(c)
        chrome.title(c, L["records"])
        ui.textO(c, L["records_local"], panel.centerX(), panel.top + 29 * dp,
            ui.fit(L["records_local"], 16f, panel.width() - 28 * dp), Ui.GOLD, Paint.Align.CENTER)
        val list = cachedEntries
        val inner = 16 * dp
        val l = panel.left + inner
        val r = panel.right - inner
        var y = rowsTop() - rowScroll
        if (tab == 4) {
            ui.textO(c, periodLabel, panel.centerX(), panel.top + 114 * dp,
                12f, Ui.GOLD, Paint.Align.CENTER)
        }
        val bottomLimit = rowsBottom()
        if (list.isEmpty()) {
            val available = (bottomLimit - y).coerceAtLeast(0f)
            if (available > 95 * dp)
                Icons.draw(c, Icons.TROPHY, panel.centerX(), y + available * 0.37f,
                    min(60 * dp, available * 0.54f), dp = dp)
            if (available > 20 * dp)
                ui.textO(c, L["no_scores"], panel.centerX(), y + available * 0.74f,
                    ui.fit(L["no_scores"], 15f, panel.width() - 24 * dp),
                    Ui.DIM, Paint.Align.CENTER)
        }
        val lh = 34 * dp
        c.save()
        c.clipRect(panel.left + 3 * dp, rowsTop(), panel.right - 3 * dp, bottomLimit)
        for (i in list.indices) {
            if (y + lh > bottomLimit) break
            if (y + lh < rowsTop()) { y += lh; continue }
            val e = list[i]
            row.set(l, y, r, y + lh - 4 * dp)
            ui.fill.color = when {
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
            val col = if (i == 0) Ui.GOLD else Ui.TEXT
            banners.draw(c, e.bannerId, l + 49 * dp, cy - 9 * dp, 24 * dp, 18 * dp)
            ui.textO(c, cachedDates[i], l + 81 * dp, cy + 5 * dp,
                ui.fit(cachedDates[i], 14f, (r - l) * 0.45f), col, Paint.Align.LEFT, 1.8f)
            val hasReplay = e.replay in existingReplayIds
            if (hasReplay) Icons.draw(c, Icons.PLAY, r - 15 * dp, cy, 17 * dp, Ui.GOLD, dp = dp)
            ui.textO(c, cachedScores[i], r - (if (hasReplay) 33 else 12) * dp,
                cy + 5 * dp, 15f, col, Paint.Align.RIGHT, 1.8f)
            y += lh
        }
        c.restore()
        // Stan usługi jest pokazywany wyłącznie po połączeniu.
        if (g.online.available && g.online.state == OnlineState.CONNECTED && tab != 4)
            ui.text(c, L["pgs_connected"], panel.centerX(),
                panel.bottom - 10 * dp, 10f, Ui.DIM, Paint.Align.CENTER)
        drawHeldTooltip(c)
    }

    private fun rowsTop(): Float = panel.top + (if (tab == 4) 126f else 56f) * dp

    private fun rowsBottom(): Float =
        panel.bottom - (if (global.visible || globalScores.visible) 88f else 12f) * dp

    private fun maxRowScroll(): Float =
        (cachedEntries.size * 34 * dp - (rowsBottom() - rowsTop())).coerceAtLeast(0f)

    override fun isDragScreen() = true

    override fun onDrag(dx: Float, dy: Float) {
        rowScroll = (rowScroll - dy).coerceIn(0f, maxRowScroll())
    }

    override fun onTap(x: Float, y: Float) {
        if (x < panel.left || x > panel.right) return
        val top = rowsTop()
        if (y < top || y >= rowsBottom()) return
        val index = ((y - top + rowScroll) / (34 * dp)).toInt()
        if (index !in cachedEntries.indices || top + (index + 1) * 34 * dp - rowScroll > rowsBottom()) return
        val id = cachedEntries[index].replay ?: return
        if (id in existingReplayIds) g.push(ReplayScreen(g, id))
    }

    override fun onClick(b: Button) {
        if (recordsTabs.onClick(b)) return
        when (b.id) {
            -1 -> g.pop()
            104 -> { previousWeek = !previousWeek; rowScroll = 0f }
            -3 -> if (g.online.available && g.online.state == OnlineState.CONNECTED && tab != 4) g.online.showUi(board(tab))
            -4 -> if (g.scores != null && g.profile.globalRanking) g.push(GlobalRankingsScreen(g))
            -2 -> g.host.requestText(L["name"], g.profile.name) { n -> g.profile.name = n; g.profile.save() }
            in 0..4 -> { tab = b.id; rowScroll = 0f; cacheRows() }
        }
    }
}
