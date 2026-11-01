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
import com.centurywar.meta.GlobalEntry
import com.centurywar.meta.ScoreSendStatus
import kotlin.math.min

/** Własne rankingi serwera, niezależne od Google Play Games. */
class GlobalRankingsScreen(g: Game) : Screen(g) {
    override val crashName get() = "GlobalRankingsScreen"
    private val bg = MenuBg()
    private val chrome = Chrome(this)
    private val back = btn(-1)
    private val periodBtn = btn(10)
    private val stageBtn = btn(11)
    private val tabs = Array(4) { btn(it) }
    private val names = arrayOf("board_endless", "board_daily", "board_campaign", "board_skirmish")
    private val shortNames = arrayOf("endless", "daily", "campaign", "skirmish")
    private val periods = arrayOf("day", "week", "all")
    private val periodNames = arrayOf("global_period_day", "global_period_week", "global_period_all")
    private val icons = intArrayOf(Icons.TOWER, Icons.CALENDAR, Icons.MAP, Icons.CROSSED_SWORDS)
    private val panel = RectF()
    private val rowRect = RectF()
    private val reportBtn = btn(20)
    private val reportRows = Array(32) { btn(100 + it) }
    private val reportEntries = arrayOfNulls<GlobalEntry>(32)
    private var drawnRows = 0
    private var reportTarget: String? = null
    private var reportNotice: String? = null
    private var selfPublicId = ""
    init { for (b in reportRows) b.visible = false }
    private var tab = 0
    private var period = 2
    private var stage = g.profile.unlockedStage().coerceIn(0, 35)
    private var dt = 0f

    private fun board(): String = when (tab) {
        0 -> "endless"
        1 -> "daily"
        2 -> "campaign_" + stage
        else -> "skirmish"
    }

    private fun refresh() {
        g.scores?.refresh(board(), periods[period], g.profile.playerId)
    }

    override fun layout() {
        chrome.layoutBack(back)
        val pw = min(1040 * dp, w - g.insetL - g.insetR - 32 * dp)
        val gap = 6 * dp
        val tw = (pw - 3 * gap) / 4
        val left = (w - pw) / 2f
        for (i in 0..3) {
            tabs[i].label = chrome.tabLabel(L[names[i]], L[shortNames[i]], tw, 42 * dp)
            tabs[i].iconId = icons[i]
            tabs[i].set(left + i * (tw + gap), chrome.top + 4 * dp, tw, 42 * dp)
        }
        panel.set(left, chrome.top + 56 * dp, left + pw, h - g.insetB - 12 * dp)
        val filterWidth = min(150 * dp, (pw - 40 * dp) / 2f)
        periodBtn.set(panel.left + 12 * dp, panel.top + 8 * dp, filterWidth, 38 * dp)
        stageBtn.set(panel.left + 22 * dp + filterWidth, panel.top + 8 * dp, filterWidth, 38 * dp)
        periodBtn.iconId = Icons.CALENDAR
        stageBtn.iconId = Icons.MAP
        val reportWidth = min(180 * dp, (pw - 35 * dp) / 3f)
        reportBtn.set(panel.right - 12 * dp - reportWidth, panel.top + 8 * dp,
            reportWidth, 38 * dp)
        update(0f)
        refresh()
    }

    override fun update(dt: Float) {
        this.dt = dt
        periodBtn.label = L[periodNames[period]]
        stageBtn.label = L.f("stage_n", stage + 1)
        stageBtn.visible = tab == 2
        reportBtn.label = L["global_report_nick"]
        reportBtn.visible = reportTarget != null
        for (i in tabs.indices) {
            tabs[i].selected = i == tab
            tabs[i].color = if (i == tab) Color.rgb(84, 104, 140) else Ui.BTN
        }
    }

    override fun render(c: Canvas) {
        bg.draw(c, w, h, dt)
        chrome.dim(c, 120)
        ui.panel(c, panel)
        // Niewidoczne przyciski są obszarami gestów przytrzymania wiersza.
        drawnRows = 0
        for (b in reportRows) b.visible = false
        selfPublicId = g.scores?.view(board(), periods[period], g.profile.playerId)?.selfPublicId ?: ""
        chrome.title(c, L["global_ranking"])
        for (button in tabs) ui.button(c, button, 13f)
        ui.button(c, back, 16f)
        ui.button(c, periodBtn, 13f)
        if (stageBtn.visible) ui.button(c, stageBtn, 13f)
        if (reportBtn.visible) ui.button(c, reportBtn, 12f)

        val view = g.scores?.view(board(), periods[period], g.profile.playerId)
        val list = view?.top ?: emptyList()
        val around = view?.around ?: emptyList()
        val l = panel.left + 16 * dp
        val r = panel.right - 16 * dp
        var y = panel.top + 52 * dp
        val limit = panel.bottom - 120 * dp
        if (list.isEmpty()) {
            Icons.draw(c, Icons.TROPHY, panel.centerX(), y + 30 * dp, 42 * dp, dp = dp)
            ui.textO(c, L[if (view == null || view.loading) "global_loading" else
                if (view.error) "global_offline" else "global_empty"],
                panel.centerX(), y + 83 * dp, 14f, Ui.DIM, Paint.Align.CENTER)
        } else {
            ui.textO(c, L["global_top"], l, y + 12 * dp, 14f, Ui.GOLD)
            y += 20 * dp
            for (i in list.indices) {
                if (y + 31 * dp > limit) break
                drawEntry(c, list[i], l, r, y, i)
                y += 31 * dp
            }
        }

        if (around.isNotEmpty()) {
            val aroundTop = panel.bottom - 115 * dp
            ui.textO(c, L["global_around"], l, aroundTop, 13f, Ui.GOLD)
            var ay = aroundTop + 9 * dp
            for (e in around) {
                if (ay + 25 * dp > panel.bottom - 35 * dp) break
                if (e.player == selfPublicId || around.size <= 3) {
                    drawEntry(c, e, l, r, ay, e.rank.toInt())
                    ay += 26 * dp
                }
            }
        }
        if (reportNotice != null) ui.text(c, L[reportNotice!!],
            panel.centerX(), panel.bottom - 43 * dp, 11f, Ui.GOLD, Paint.Align.CENTER)
        val status = when {
            view?.loading == true -> "global_loading"
            view?.error == true -> "global_offline"
            else -> null
        }
        if (status != null && list.isNotEmpty())
            ui.text(c, L[status], panel.centerX(), panel.bottom - 26 * dp,
                11f, Ui.DIM, Paint.Align.CENTER)
        val sendStatus = when (g.scores?.status) {
            ScoreSendStatus.QUEUED, ScoreSendStatus.OFFLINE -> "global_queued"
            ScoreSendStatus.SENDING -> "global_sending"
            ScoreSendStatus.SENT -> "global_sent"
            ScoreSendStatus.REJECTED -> "global_rejected"
            ScoreSendStatus.INVALID_NICK -> "global_nick_error"
            else -> null
        }
        if (sendStatus != null) ui.text(c, L[sendStatus],
            panel.centerX(), panel.bottom - 12 * dp, 11f, Ui.DIM, Paint.Align.CENTER)
    }

    private fun drawEntry(c: Canvas, entry: GlobalEntry, left: Float, right: Float, y: Float, index: Int) {
        val mine = entry.player == selfPublicId
        rowRect.set(left, y, right, y + 28 * dp)
        if (drawnRows < reportRows.size) {
            val button = reportRows[drawnRows]
            reportEntries[drawnRows] = entry
            button.set(left, y, right - left, 28 * dp)
            button.visible = true
            drawnRows++
        }
        ui.fill.color = if (mine) Color.argb(100, 64, 140, 235)
            else if (index % 2 == 0) Color.argb(35, 255, 255, 255)
            else Color.argb(15, 255, 255, 255)
        c.drawRoundRect(rowRect, 7 * dp, 7 * dp, ui.fill)
        val col = if (mine) Ui.XP else Ui.TEXT
        val baseline = rowRect.centerY() + 5 * dp
        ui.textO(c, entry.rankLabel, left + 10 * dp, baseline, 13f, Ui.DIM)
        ui.textO(c, entry.nick, left + 50 * dp, baseline,
            ui.fit(entry.nick, 14f, rowRect.width() - 150 * dp), col)
        ui.textO(c, entry.scoreLabel, right - 10 * dp, baseline, 14f, col, Paint.Align.RIGHT)
    }

    override fun onLongPress(b: Button): Boolean {
        if (b.id in 100 until 100 + reportRows.size) {
            val entry = reportEntries[b.id - 100] ?: return false
            if (!com.centurywar.meta.ScoreClient.validPublicId(entry.player)) return false
            reportTarget = entry.player
            reportNotice = null
            reportBtn.visible = true
            return true
        }
        return super.onLongPress(b)
    }

    override fun onClick(b: Button) {
        when (b.id) {
            20 -> {
                val target = reportTarget ?: return
                reportTarget = null
                g.scores?.reportNick(target) { accepted ->
                    g.post(Runnable {
                        reportNotice = if (accepted) "global_report_sent" else "global_report_failed"
                    })
                }
            }
            -1 -> g.pop()
            10 -> { period = (period + 1) % periods.size; refresh() }
            11 -> { stage = (stage + 1) % (g.profile.unlockedStage() + 1).coerceAtLeast(1); refresh() }
            in 0..3 -> { tab = b.id; refresh() }
        }
    }
}
