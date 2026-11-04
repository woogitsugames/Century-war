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
    override val blocksNotices: Boolean get() = true
    private companion object { val HEADER_BODY = Color.rgb(52, 46, 38) }
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
    private val headerStrip = RectF()
    private val streakCells = Array(7) { RectF() }
    private val streakDayTexts = Array(7) { "" }
    private val streakRewardTexts = Array(7) { "" }
    private val hints = HintOverlay(g)
    /** R11-01: wersja danych misji; teksty dla czytnika powstają dopiero po zmianie. */
    private var dataVersion = 0
    private val missionA11y = Array(6) { "" }
    private val claimA11y = Array(6) { "" }
    private val a11yKeys = IntArray(6) { -1 }
    private var loginA11y = ""
    private var loginA11yKey = -1

    init { buttons.add(0, hints.okBtn) }
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
    /** Tura 10: zamiast samego „UTC” — ile zostało do nowych misji (odświeżane co 30 s). */
    internal var dailyResetText = ""
        private set
    internal var weeklyResetText = ""
        private set

    /** D7: stały zegar dla powtarzalnych zrzutów sklepu; w grze zawsze czas systemowy. */
    internal var clock: () -> Long = { System.currentTimeMillis() }

    private fun refreshResetTexts(now: Long) {
        val sec = Math.floorDiv(now, 1000L)
        val dayLeft = (Missions.utcDay(now) + 1L) * 86_400L - sec
        val weekStartDay = (Missions.utcWeek(now) + 1L) * 7L - 3L
        val weekLeft = weekStartDay * 86_400L - sec
        val minutesDay = (dayLeft + 59L) / 60L
        dailyResetText = L.f("ms150_reset", String.format(java.util.Locale.ROOT, "%02d:%02d",
            minutesDay / 60L, minutesDay % 60L))
        val minutesWeek = (weekLeft + 59L) / 60L
        weeklyResetText = L.f("ms150_reset", String.format(java.util.Locale.ROOT, "%dd %02d:%02d",
            minutesWeek / 1440L, minutesWeek / 60L % 24L, minutesWeek % 60L))
    }

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
        val now = clock()
        missions.refresh(now)
        val loginReward = missions.claimLogin(now)
        shownDay = Missions.utcDay(now)
        refreshResetTexts(now)
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
        dataVersion++
        streakText = L.f("ms_login_day", missions.state.loginStreak)
        loginText = L.f("ms_login_paid",
            L.plural("medals_n", missions.state.loginRewardToday.toLong()))
        val stars = g.profile.totalStars()
        for (i in streakCells.indices) {
            streakDayTexts[i] = L.f("records_day_short", i + 1)
            streakRewardTexts[i] = "+" + Missions.loginRewardFor(i + 1, stars)
        }
        place()
        if (loginReward > 0) {
            g.celebrateReward(loginRect.centerX(), loginRect.centerY(), loginReward, Sound.MEDALS)
        }
    }

    private fun place() {
        val gap = 9 * dp
        val rowH = (if (ui.textScale > 1.15f) 96f else 79f) * dp
        val titleH = (if (ui.textScale > 1.15f) 37f else 29f) * dp
        val loginH = (if (ui.textScale > 1.15f) 172f else 150f) * dp
        val start = contentTop - scroll
        loginRect.set(left, start, left + cardWidth, start + loginH)
        val dayGap = 5 * dp
        val dayWidth = (cardWidth - 28 * dp - dayGap * 6) / 7
        val dayY = loginRect.bottom - 56 * dp
        for (i in streakCells.indices) {
            val x = left + 14 * dp + i * (dayWidth + dayGap)
            streakCells[i].set(x, dayY, x + dayWidth, loginRect.bottom - 9 * dp)
        }
        dailyY = loginRect.bottom + 13 * dp
        weeklyY = dailyY + titleH + 3 * (rowH + gap) + 12 * dp
        val fullBottom = weeklyY + titleH + 3 * (rowH + gap) + 8 * dp + scroll
        maxScroll = max(0f, fullBottom - bottom)
        scroll = scroll.coerceIn(0f, maxScroll)
        if (start != contentTop - scroll) {
            val corrected = contentTop - scroll
            loginRect.offset(0f, corrected - start)
            for (cell in streakCells) cell.offset(0f, corrected - start)
            dailyY += corrected - start
            weeklyY += corrected - start
        }
        for (i in 0 until 6) {
            val header = if (i < 3) dailyY else weeklyY
            val y = header + titleH + i.rem(3) * (rowH + gap)
            val r = rects[i]
            r.set(left, y, left + cardWidth, y + rowH)
            val bw = min(120 * dp, cardWidth * 0.26f)
            claimButtons[i].set(r.right - bw - 10 * dp, y + (rowH - 48 * dp) / 2, bw, 48 * dp)
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
            val now = clock()
            if (Missions.utcDay(now) != shownDay) updateData() else refreshResetTexts(now)
        }
    }

    override fun render(c: Canvas) {
        bg.draw(c, w, h, dt)
        chrome.dim(c, 125)
        c.save()
        c.clipRect(g.insetL, contentTop, w - g.insetR, bottom)
        ui.panel(c, loginRect)
        val tx = loginRect.left + 16 * dp
        // Tura 10: nagłówek 6 dp niżej — wcześniej leżał na złotej lamówce panelu.
        ui.textO(c, L["ms_streak"], tx, loginRect.top + 29 * dp, 18f, Ui.GOLD)
        ui.textO(c, streakText, tx, loginRect.top + 53 * dp, 17f, Ui.TEXT)
        ui.textO(c, loginText, loginRect.right - 14 * dp, loginRect.top + 53 * dp,
            ui.fit(loginText, 13f, cardWidth * 0.56f), Ui.GOOD, Paint.Align.RIGHT)
        ui.textO(c, L["ms_streak_info"], tx, loginRect.top + 72 * dp,
            ui.fit(L["ms_streak_info"], 12f, cardWidth - 32 * dp), Ui.DIM)
        for (i in streakCells.indices) {
            val cell = streakCells[i]
            val current = i + 1 == missions.state.loginStreak
            val previous = i + 1 < missions.state.loginStreak
            ui.card(c, cell, when {
                current -> Color.rgb(94, 78, 39)
                previous -> Color.rgb(41, 89, 62)
                else -> Color.rgb(41, 49, 70)
            }, 7f)
            ui.textO(c, streakDayTexts[i], cell.centerX(), cell.top + 17 * dp,
                ui.fit(streakDayTexts[i], 12f, cell.width() - 7 * dp),
                if (current) Ui.GOLD else Ui.TEXT, Paint.Align.CENTER)
            ui.textO(c, streakRewardTexts[i], cell.centerX(), cell.bottom - 8 * dp,
                ui.fit(streakRewardTexts[i], 12f, cell.width() - 7 * dp),
                if (previous || current) Ui.GOLD else Ui.DIM, Paint.Align.CENTER)
        }
        // Tura 10 (szlif 2): nagłówki sekcji mają własny podkład, nie stoją na krajobrazie.
        val titleH = (if (ui.textScale > 1.15f) 37f else 29f) * dp
        headerStrip.set(left, dailyY - 1 * dp, left + cardWidth, dailyY + titleH - 2 * dp)
        ui.card(c, headerStrip, HEADER_BODY, 9f)
        headerStrip.offset(0f, weeklyY - dailyY)
        ui.card(c, headerStrip, HEADER_BODY, 9f)
        ui.textO(c, L["ms_daily"], left + 10 * dp, dailyY + 22 * dp, 19f, Ui.GOLD)
        val resetW = cardWidth - ui.measure(L["ms_daily"], 19f, true) - 40 * dp
        ui.textO(c, dailyResetText, left + cardWidth - 12 * dp, dailyY + 22 * dp,
            ui.fit(dailyResetText, 12f, resetW), Ui.DIM, Paint.Align.RIGHT)
        ui.textO(c, L["ms_weekly"], left + 10 * dp, weeklyY + 22 * dp, 19f, Ui.GOLD)
        ui.textO(c, weeklyResetText, left + cardWidth - 12 * dp, weeklyY + 22 * dp,
            ui.fit(weeklyResetText, 12f, cardWidth - ui.measure(L["ms_weekly"], 19f, true) - 40 * dp), Ui.DIM, Paint.Align.RIGHT)
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
            // Tura 10: postęp i nagroda nad lamówką (wcześniej na niej, 12 dp od dołu).
            ui.textO(c, progress[i], r.left + 13 * dp, r.bottom - 15 * dp, 12f, Ui.DIM)
            ui.textO(c, rewards[i], b.r.left - 6 * dp, r.bottom - 15 * dp, 12f, Ui.GOLD, Paint.Align.RIGHT)
            ui.button(c, b, 13f)
        }
        c.restore()
        ui.button(c, back, 16f)
        chrome.title(c, L["ms_missions"])
        chrome.badges(c, g.profile.medals)
        chrome.titleBounds(L["ms_missions"], hints.titleRect)
        hints.draw(c)
    }

    // ------------------------------------------------ R11-01: czytnik ekranu

    override fun accessibilityTitle(): String = L["ms_missions"]

    override fun accessibilityEligible(b: Button): Boolean =
        if (hints.accessibilityOpen) b === hints.okBtn else b !== hints.okBtn

    override fun accessibilityKey(b: Button): Int = if (b.id in 100..105) dataVersion else 0

    /** Wiersz misji czytany przed jej przyciskiem; seria logowań na początku. */
    override fun accessibilityOrder(b: Button, index: Int): Float =
        if (b.id in 100..105) 2f + (b.id - 100) * 2f + 1f else 0f

    override fun accessibilityInfoOrder(i: Int): Float = if (i == 0) 1f else 2f + (i - 1) * 2f

    /** „Odbierz: Wygraj 3 bitwy, 40 medali”; odebrana albo niezaliczona misja ma własny opis. */
    override fun accessibilityLabel(b: Button): String {
        val index = b.id - 100
        if (index !in 0..5 || index >= items.size) return super.accessibilityLabel(b)
        refreshMissionText(index)
        return claimA11y[index]
    }

    private fun refreshMissionText(index: Int) {
        val key = dataVersion * 16 + L.lang
        if (a11yKeys[index] == key) return
        a11yKeys[index] = key
        val item = items[index]
        val reward = L.plural("medals_n", item.reward.toLong())
        claimA11y[index] = when {
            item.claimed -> titles[index] + ", " + L["ms_claimed"]
            item.completed -> L.f("a196_claim", titles[index], reward)
            else -> L["ms_claim"] + ": " + titles[index]
        }
        missionA11y[index] = accessibilitySentences(
            L[if (index < 3) "ms_daily" else "ms_weekly"] + ": " + titles[index],
            L.f("a196_progress", item.progress, item.target) + ", " + L.f("a196_reward", reward),
            if (item.claimed) L["ms_claimed"] else if (item.completed) L["a196_done"] else "")
    }

    /** 0: seria logowań; 1..6: widoczne misje. */
    override fun accessibilityInfoCount(): Int = if (hints.accessibilityOpen) 1 else 1 + items.size.coerceAtMost(6)

    override fun accessibilityInfo(i: Int): String {
        if (hints.accessibilityOpen) return if (i == 0) hints.accessibilityBody else ""
        if (i == 0) {
            if (loginRect.bottom <= contentTop || loginRect.top >= bottom) return ""
            val key = dataVersion * 16 + L.lang
            if (loginA11yKey != key) {
                loginA11yKey = key
                loginA11y = accessibilitySentences(L["ms_streak"], streakText, loginText, L["ms_streak_info"])
            }
            return loginA11y
        }
        val index = i - 1
        if (index >= items.size) return ""
        val r = rects[index]
        if (r.bottom <= contentTop || r.top >= bottom) return ""
        refreshMissionText(index)
        return missionA11y[index]
    }

    override fun accessibilityInfoBounds(i: Int, out: RectF) {
        if (hints.accessibilityOpen) { hints.accessibilityBounds(out); return }
        out.set(if (i == 0) loginRect else rects[i - 1])
        if (!out.intersect(left, contentTop, left + cardWidth, bottom)) out.setEmpty()
    }

    override fun accessibilityCanScroll(direction: Int): Boolean =
        !hints.accessibilityOpen && if (direction > 0) scroll < maxScroll - 0.5f else scroll > 0.5f

    override fun accessibilityScroll(direction: Int): Boolean {
        if (!accessibilityCanScroll(direction)) return false
        val moved = accessibilityStep(scroll, maxScroll, bottom - contentTop, direction)
        scroll = scrollResult
        place()
        return moved
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
