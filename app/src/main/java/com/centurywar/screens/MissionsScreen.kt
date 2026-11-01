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
import com.centurywar.engine.Sound
import com.centurywar.engine.Ui
import com.centurywar.meta.MissionItem
import com.centurywar.meta.MissionTexts
import com.centurywar.meta.Missions
import kotlin.math.max
import kotlin.math.min

/** Misje i seria logowań, odświeżane tylko po wejściu lub zmianie dnia UTC. */
class MissionsScreen(g: Game) : Screen(g) {
    override val crashName get() = "MissionsScreen"
    private val bg = MenuBg()
    private val chrome = Chrome(this)
    private val back = btn(-1)
    private val claimButtons = Array(6) { btn(100 + it) }
    private val missions = Missions(g.profile)
    private val items = ArrayList<MissionItem>(6)
    private val titles = Array(6) { "" }
    private val progress = Array(6) { "" }
    private val rewards = Array(6) { "" }
    private val rects = Array(6) { RectF() }
    private val loginRect = RectF()
    private val hints = HintOverlay(g)

    init { buttons.add(0, hints.okBtn) }
    private val loginBar = RectF()
    private var left = 0f
    private var cardWidth = 0f
    private var contentTop = 0f
    private var bottom = 0f
    private var dailyY = 0f
    private var weeklyY = 0f
    private var scroll = 0f
    private var maxScroll = 0f
    private var elapsed = 0f
    private var dt = 0f
    private var shownDay = Long.MIN_VALUE
    private var streakText = ""
    private var loginText = ""

    override fun isDragScreen() = true

    override fun layout() {
        chrome.layoutBack(back)
        cardWidth = min(760 * dp, w - g.insetL - g.insetR - 32 * dp)
        left = (w - cardWidth + g.insetL - g.insetR) / 2
        contentTop = chrome.top + 8 * dp
        bottom = h - g.insetB - 8 * dp
        updateData()
        hints.showScreen(HintOverlay.MISSIONS, loginRect)
    }

    private fun updateData() {
        val now = System.currentTimeMillis()
        missions.refresh(now)
        val loginReward = missions.claimLogin(now)
        shownDay = Missions.utcDay(now)
        items.clear()
        items.addAll(missions.state.daily)
        items.addAll(missions.state.weekly)
        for (i in 0 until 6) {
            val item = items.getOrNull(i)
            if (item == null) {
                titles[i] = ""; progress[i] = ""; rewards[i] = ""
                claimButtons[i].visible = false
                continue
            }
            titles[i] = MissionTexts.label(item)
            progress[i] = "${item.progress}/${item.target}"
            rewards[i] = "+${item.reward}"
            claimButtons[i].label = L[if (item.claimed) "ms_claimed" else "ms_claim"]
            claimButtons[i].enabled = item.completed && !item.claimed
            claimButtons[i].iconId = if (item.claimed) Icons.CHECK else Icons.MEDAL
            claimButtons[i].highlight = item.completed && !item.claimed
        }
        streakText = L.f("ms_login_day", missions.state.loginStreak)
        loginText = L.f("ms_login_paid",
            L.plural("medals_n", missions.state.loginRewardToday.toLong()))
        place()
        if (loginReward > 0) {
            g.celebrateReward(loginRect.centerX(), loginRect.centerY(), loginReward, Sound.MEDALS)
        }
    }

    private fun place() {
        val gap = 9 * dp
        val rowH = (if (ui.textScale > 1.15f) 96f else 79f) * dp
        val titleH = (if (ui.textScale > 1.15f) 37f else 29f) * dp
        val loginH = (if (ui.textScale > 1.15f) 124f else 106f) * dp
        val start = contentTop - scroll
        loginRect.set(left, start, left + cardWidth, start + loginH)
        loginBar.set(left + 14 * dp, loginRect.bottom - 26 * dp, left + cardWidth - 14 * dp, loginRect.bottom - 18 * dp)
        dailyY = loginRect.bottom + 13 * dp
        weeklyY = dailyY + titleH + 3 * (rowH + gap) + 12 * dp
        val fullBottom = weeklyY + titleH + 3 * (rowH + gap) + 8 * dp + scroll
        maxScroll = max(0f, fullBottom - bottom)
        scroll = scroll.coerceIn(0f, maxScroll)
        if (start != contentTop - scroll) {
            val corrected = contentTop - scroll
            loginRect.offset(0f, corrected - start)
            loginBar.offset(0f, corrected - start)
            dailyY += corrected - start
            weeklyY += corrected - start
        }
        for (i in 0 until 6) {
            val header = if (i < 3) dailyY else weeklyY
            val y = header + titleH + i.rem(3) * (rowH + gap)
            val r = rects[i]
            r.set(left, y, left + cardWidth, y + rowH)
            val bw = min(120 * dp, cardWidth * 0.26f)
            claimButtons[i].set(r.right - bw - 10 * dp, y + 17 * dp, bw, 42 * dp)
            claimButtons[i].visible = i < items.size &&
                claimButtons[i].r.top >= contentTop && claimButtons[i].r.bottom <= bottom
        }
    }

    override fun onDrag(dx: Float, dy: Float) {
        hints.onAction()
        scroll = (scroll - dy).coerceIn(0f, maxScroll)
        place()
    }

    override fun update(dt: Float) {
        hints.update(dt)
        this.dt = dt
        elapsed += dt
        if (elapsed >= 30f) {
            elapsed = 0f
            if (Missions.utcDay(System.currentTimeMillis()) != shownDay) updateData()
        }
    }

    override fun render(c: Canvas) {
        bg.draw(c, w, h, dt)
        chrome.dim(c, 125)
        c.save()
        c.clipRect(g.insetL, contentTop, w - g.insetR, bottom)
        ui.panel(c, loginRect)
        val tx = loginRect.left + 16 * dp
        ui.textO(c, L["ms_streak"], tx, loginRect.top + 23 * dp, 18f, Ui.GOLD)
        ui.textO(c, streakText, tx, loginRect.top + 49 * dp, 17f, Ui.TEXT)
        ui.textO(c, loginText, loginRect.right - 14 * dp, loginRect.top + 49 * dp,
            ui.fit(loginText, 13f, cardWidth * 0.56f), Ui.GOOD, Paint.Align.RIGHT)
        ui.textO(c, L["ms_streak_info"], tx, loginRect.top + 68 * dp,
            ui.fit(L["ms_streak_info"], 12f, cardWidth - 32 * dp), Ui.DIM)
        ui.bar(c, loginBar.left, loginBar.top, loginBar.width(), loginBar.height(),
            missions.state.loginStreak / 7f, Ui.GOLD)
        ui.textO(c, L["ms_daily"], left + 10 * dp, dailyY + 22 * dp, 19f, Ui.GOLD)
        ui.textO(c, "UTC", left + cardWidth - 12 * dp, dailyY + 22 * dp, 12f, Ui.DIM, Paint.Align.RIGHT)
        ui.textO(c, L["ms_weekly"], left + 10 * dp, weeklyY + 22 * dp, 19f, Ui.GOLD)
        ui.textO(c, "UTC", left + cardWidth - 12 * dp, weeklyY + 22 * dp, 12f, Ui.DIM, Paint.Align.RIGHT)
        for (i in items.indices) {
            val r = rects[i]
            if (r.bottom < contentTop || r.top > bottom) continue
            ui.panel(c, r, Color.argb(240, 33, 42, 61), 11f)
            val b = claimButtons[i]
            val available = b.r.left - r.left - 26 * dp
            ui.textO(c, titles[i], r.left + 13 * dp, r.top + 26 * dp,
                ui.fit(titles[i], 15f, available, 10f), Ui.TEXT)
            ui.bar(c, r.left + 13 * dp, r.top + 40 * dp,
                max(10 * dp, available), 9 * dp,
                items[i].progress.toFloat() / items[i].target, if (items[i].completed) Ui.GOOD else Ui.XP)
            ui.textO(c, progress[i], r.left + 13 * dp, r.bottom - 12 * dp, 12f, Ui.DIM)
            ui.textO(c, rewards[i], b.r.left - 6 * dp, r.bottom - 12 * dp, 12f, Ui.GOLD, Paint.Align.RIGHT)
            ui.button(c, b, 13f)
        }
        c.restore()
        ui.button(c, back, 16f)
        chrome.title(c, L["ms_missions"])
        chrome.badges(c, g.profile.medals)
        hints.draw(c)
    }

    override fun onClick(b: Button) {
        if (hints.onClick(b)) return
        hints.onAction()
        if (b.id == -1) { g.pop(); return }
        val index = b.id - 100
        if (index !in 0 until items.size) return
        val reward = missions.claim(items[index].id)
        if (reward > 0) {
            val source = claimButtons[index].r
            g.celebrateReward(source.centerX(), source.centerY(), reward)
            updateData()
        }
    }
}
