package com.centurywar.screens

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.Icons
import com.centurywar.engine.L
import com.centurywar.engine.RowNote
import com.centurywar.engine.Screen
import com.centurywar.engine.Ui
import com.centurywar.game.Daily
import com.centurywar.meta.Achievements
import com.centurywar.meta.PreReleaseRules
import kotlin.math.max
import kotlin.math.min

/** Lista osiągnięć z postępem, nagrodami i ukrytymi wyzwaniami. */
class AchievementsScreen(g: Game) : Screen(g) {
    override val crashName get() = "AchievementsScreen"
    override val blocksNotices: Boolean get() = true
    private val bg = MenuBg()
    private val chrome = Chrome(this)
    private val recordsTabs = RecordsTabs(this, 3)
    private val back = btn(-1)
    private val defs = Achievements.all
    private val panel = RectF()
    private val row = RectF()
    private val names = Array(defs.size) { "" }
    private val descriptions = Array(defs.size) { "" }
    private val rewardText = Array(defs.size) { "" }
    private val progressText = Array(defs.size) { "" }
    /** Noc 3: opis, który w jednym wierszu zmalałby poniżej 10,5 sp, łamiemy na dwa wiersze. */
    private val descLines = Array(defs.size) { arrayOfNulls<String>(3) }
    private val descCount = IntArray(defs.size)
    private val descSize = FloatArray(defs.size) { 11f }
    private val lastProgress = LongArray(defs.size) { -1L }
    private var completeText = ""
    private var lastComplete = -1
    private var scroll = 0f
    private var maxScroll = 0f
    private var rowH = 90f
    private var dt = 0f
    private val rowA11y = Array(defs.size) { "" }
    private val rowA11yKey = LongArray(defs.size) { -1L }
    private val a11yRow = RectF()
    /**
     * R12 (pozycja 5): „Dostępne od premiery” w wierszu postępu (wersja testowa przed premierą).
     * Stałe 11 sp, za długi tekst kończy się „…” (zewnętrzny Fold). Czytnik ekranu bez zmian.
     */
    private val noteText = Array(defs.size) { "" }
    private val noteX = FloatArray(defs.size)
    /** Dzień UTC z zegara gry (D41: `g.clock()`, jak menu i misje, nie zegar systemowy). */
    private var today = Daily.dayNumber(g.clock())
    /** Testy D41. */
    internal val dayForTests: Long get() = today

    override fun isDragScreen() = true

    override fun layout() {
        chrome.layoutBack(back)
        recordsTabs.layout()
        val width = min(920 * dp, w - g.insetL - g.insetR - 24 * dp)
        val cx = (w + g.insetL - g.insetR) / 2f
        panel.set(cx - width / 2, recordsTabs.contentTop + 8 * dp,
            cx + width / 2, h - g.insetB - 12 * dp)
        rowH = 88 * dp
        val contentH = defs.size * (rowH + 8 * dp) + 20 * dp
        maxScroll = max(0f, contentH - (panel.height() - 46 * dp))
        scroll = scroll.coerceIn(0f, maxScroll)
        for (i in defs.indices) {
            val a = defs[i]
            names[i] = L["ach_" + a.id]
            descriptions[i] = L["ach_" + a.id + "_d"]
            rewardText[i] = "+" + a.medalReward
        }
        val textWidth = width - 24 * dp - 113 * dp
        for (i in defs.indices) {
            descCount[i] = 0
            if (ui.fit(descriptions[i], 12f, textWidth) * ui.textScale >= 10.5f) continue
            for (size in floatArrayOf(11f, 10.5f)) {
                if (HintFrame.wrapHintInto(ui, descriptions[i], textWidth, size, descLines[i]) == 2) {
                    descCount[i] = 2; descSize[i] = size; break
                }
            }
        }
        val note = L["t203_ach_release"]
        for (i in defs.indices) {
            noteText[i] = ""
            if (!PreReleaseRules.lockedBeforeRelease(defs[i].metric)) continue
            val threshold = defs[i].threshold.toString()
            noteX[i] = ui.measure("$threshold/$threshold", NOTE_SP, true) + 10 * dp
            val avail = textWidth - noteX[i]
            if (avail > 24 * dp) noteText[i] = RowNote.ellipsize(ui, note, NOTE_SP, avail)
        }
        refreshProgress()
    }

    private fun refreshProgress() {
        for (i in defs.indices) {
            val a = defs[i]
            val n = Achievements.progress(g.profile, a).coerceIn(0L, a.threshold)
            if (n != lastProgress[i]) {
                lastProgress[i] = n
                progressText[i] = n.toString() + "/" + a.threshold
            }
        }
        val complete = defs.count { Achievements.isUnlocked(g.profile, it.id) }
        if (complete != lastComplete) {
            lastComplete = complete
            completeText = L.f("achievement_completed", complete, defs.size)
        }
    }

    override fun onLanguageChanged() {
        // Wymuś przeliczenie napisu postępu mimo niezmienionej liczby osiągnięć.
        lastComplete = -1
        super.onLanguageChanged()
    }

    override fun update(dt: Float) {
        this.dt = dt
        today = Daily.dayNumber(g.clock())
        refreshProgress()
    }

    override fun onDrag(dx: Float, dy: Float) {
        scroll = (scroll - dy).coerceIn(0f, maxScroll)
    }

    override fun render(c: Canvas) {
        bg.draw(c, w, h, dt)
        chrome.dim(c, 120)
        ui.panel(c, panel)
        val bottom = panel.bottom - 42 * dp
        c.save()
        c.clipRect(panel.left + 3 * dp, panel.top + 3 * dp, panel.right - 3 * dp, bottom)
        val gap = 8 * dp
        for (i in defs.indices) {
            val y = panel.top + 12 * dp + i * (rowH + gap) - scroll
            if (y > bottom || y + rowH < panel.top) continue
            drawAchievement(c, i, y)
        }
        c.restore()
        ui.textO(c, completeText, panel.centerX(), panel.bottom - 16 * dp,
            14f, Ui.GOLD, Paint.Align.CENTER)
        ui.button(c, back, 16f)
        recordsTabs.draw(c)
        chrome.title(c, L["records"])
        chrome.badges(c, g.profile.medals)
        drawHeldTooltip(c)
    }

    /** Nowe osiągnięcia 176 mają własne symbole; pozostałe zachowują medal. */
    private fun lockedIcon(a: com.centurywar.meta.AchievementDef): Int = when (a.metric) {
        com.centurywar.meta.AchievementMetric.COMMANDER_SUMMONED,
        com.centurywar.meta.AchievementMetric.COMMANDER_SURVIVOR,
        com.centurywar.meta.AchievementMetric.COMMANDER_WINS -> Icons.CROWN
        com.centurywar.meta.AchievementMetric.COMMANDER_PULSES -> Icons.COMMANDER_PULSE
        com.centurywar.meta.AchievementMetric.TURRET_TIER_FOUR -> Icons.TOWER_TIERS
        com.centurywar.meta.AchievementMetric.LATE_NO_TURRET_WINS -> Icons.SHIELD
        else -> Icons.MEDAL
    }

    private fun drawAchievement(c: Canvas, i: Int, y: Float) {
        val a = defs[i]
        val unlocked = Achievements.isUnlocked(g.profile, a.id)
        val hidden = a.hidden && !unlocked
        row.set(panel.left + 12 * dp, y, panel.right - 12 * dp, y + rowH)
        ui.card(c, row, if (unlocked) Color.rgb(43, 82, 61) else Color.rgb(39, 48, 70), 10f)
        Icons.draw(c, if (unlocked) Icons.CHECK else if (hidden) Icons.LOCK else lockedIcon(a),
            row.left + 25 * dp, y + 33 * dp, 32 * dp, dp = dp)
        val tx = row.left + 52 * dp
        val textWidth = row.width() - 113 * dp
        val title = if (hidden) L["achievement_hidden"] else names[i]
        val desc = if (hidden) L["achievement_hidden_desc"] else descriptions[i]
        ui.textO(c, title, tx, y + 24 * dp, ui.fit(title, 15f, textWidth), if (unlocked) Ui.GOLD else Ui.TEXT)
        val twoLines = !hidden && descCount[i] == 2
        if (twoLines) {
            ui.text(c, descLines[i][0] ?: "", tx, y + 41 * dp, descSize[i], Ui.DIM)
            ui.text(c, descLines[i][1] ?: "", tx, y + 55 * dp, descSize[i], Ui.DIM)
        } else ui.text(c, desc, tx, y + 44 * dp, ui.fit(desc, 12f, textWidth), Ui.DIM)
        if (!hidden) {
            ui.bar(c, tx, y + (if (twoLines) 63 else 56) * dp, max(12 * dp, textWidth - 90 * dp), 7 * dp,
                lastProgress[i].toFloat() / a.threshold.toFloat(), if (unlocked) Ui.GOOD else Ui.XP)
            ui.textO(c, progressText[i], tx, y + (if (twoLines) 81 else 78) * dp, 11f, Ui.DIM)
            if (noteText[i].isNotEmpty() &&
                PreReleaseRules.achievementNote(a, unlocked, g.preReleaseNotesEnabled, today))
                ui.textO(c, noteText[i], tx + noteX[i], y + (if (twoLines) 81 else 78) * dp, NOTE_SP, Ui.GOLD)
        }
        val right = row.right - 23 * dp
        Icons.draw(c, Icons.MEDAL, right, y + 30 * dp, 21 * dp, dp = dp)
        ui.textO(c, if (hidden) "?" else rewardText[i], right, y + 56 * dp,
            13f, Ui.GOLD, Paint.Align.CENTER)
    }

    // ------------------------------------------------ R11-01: czytnik ekranu

    override fun accessibilityTitle(): String = L["records"]

    /** Wiersze 0..n−1 (tylko widoczne), potem licznik zdobytych. */
    override fun accessibilityInfoCount(): Int = defs.size + 1

    private fun rowTop(i: Int) = panel.top + 12 * dp + i * (rowH + 8 * dp) - scroll

    private fun rowVisible(i: Int): Boolean {
        val y = rowTop(i)
        return y + rowH > panel.top + 3 * dp && y < panel.bottom - 42 * dp
    }

    override fun accessibilityInfo(i: Int): String {
        if (i == defs.size) return completeText
        if (!rowVisible(i)) return ""
        val a = defs[i]
        val unlocked = Achievements.isUnlocked(g.profile, a.id)
        val hidden = a.hidden && !unlocked
        val key = lastProgress[i] * 64 + (if (unlocked) 32 else 0) + L.lang
        if (rowA11yKey[i] != key) {
            rowA11yKey[i] = key
            rowA11y[i] = if (hidden) accessibilitySentences(L["achievement_hidden"], L["achievement_hidden_desc"])
            else accessibilitySentences(names[i], descriptions[i],
                if (unlocked) L["a196_unlocked"] else L.f("a196_progress", lastProgress[i], a.threshold),
                L.f("a196_reward", L.plural("medals_n", a.medalReward.toLong())))
        }
        return rowA11y[i]
    }

    override fun accessibilityInfoBounds(i: Int, out: RectF) {
        if (i == defs.size) {
            out.set(panel.left, panel.bottom - 42 * dp, panel.right, panel.bottom)
            return
        }
        val y = rowTop(i)
        a11yRow.set(panel.left + 12 * dp, y, panel.right - 12 * dp, y + rowH)
        out.set(a11yRow)
        if (!out.intersect(panel.left, panel.top + 3 * dp, panel.right, panel.bottom - 42 * dp)) out.setEmpty()
    }

    override fun accessibilityCanScroll(direction: Int): Boolean =
        if (direction > 0) scroll < maxScroll - 0.5f else scroll > 0.5f

    override fun accessibilityScroll(direction: Int): Boolean {
        val moved = accessibilityStep(scroll, maxScroll, panel.height() - 46 * dp, direction)
        if (moved) scroll = scrollResult
        return moved
    }

    override fun onClick(b: Button) {
        if (recordsTabs.onClick(b)) return
        if (b.id == -1) g.pop()
    }

    private companion object {
        /** R12-05: dopisek nigdy mniejszy niż 11 sp; zamiast zmniejszania — wielokropek. */
        const val NOTE_SP = 11f
    }
}
