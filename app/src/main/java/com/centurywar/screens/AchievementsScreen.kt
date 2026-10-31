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
import com.centurywar.meta.Achievements
import kotlin.math.max
import kotlin.math.min

/** Lista osiągnięć z postępem, nagrodami i ukrytymi wyzwaniami. */
class AchievementsScreen(g: Game) : Screen(g) {
    private val bg = MenuBg()
    private val chrome = Chrome(this)
    private val back = btn(-1)
    private val defs = Achievements.all
    private val panel = RectF()
    private val row = RectF()
    private val names = Array(defs.size) { "" }
    private val descriptions = Array(defs.size) { "" }
    private val rewardText = Array(defs.size) { "" }
    private val progressText = Array(defs.size) { "" }
    private val lastProgress = LongArray(defs.size) { -1L }
    private var completeText = ""
    private var lastComplete = -1
    private var scroll = 0f
    private var maxScroll = 0f
    private var rowH = 90f
    private var dt = 0f

    override fun isDragScreen() = true

    override fun layout() {
        chrome.layoutBack(back)
        val width = min(920 * dp, w - g.insetL - g.insetR - 24 * dp)
        val cx = (w + g.insetL - g.insetR) / 2f
        panel.set(cx - width / 2, chrome.top + 8 * dp,
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

    override fun update(dt: Float) {
        this.dt = dt
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
        chrome.title(c, L["achievements"])
        chrome.badges(c, g.profile.medals)
    }

    private fun drawAchievement(c: Canvas, i: Int, y: Float) {
        val a = defs[i]
        val unlocked = Achievements.isUnlocked(g.profile, a.id)
        val hidden = a.hidden && !unlocked
        row.set(panel.left + 12 * dp, y, panel.right - 12 * dp, y + rowH)
        ui.card(c, row, if (unlocked) Color.rgb(43, 82, 61) else Color.rgb(39, 48, 70), 10f)
        Icons.draw(c, if (unlocked) Icons.CHECK else if (hidden) Icons.LOCK else Icons.MEDAL,
            row.left + 25 * dp, y + 33 * dp, 32 * dp, dp = dp)
        val tx = row.left + 52 * dp
        val textWidth = row.width() - 113 * dp
        val title = if (hidden) L["achievement_hidden"] else names[i]
        val desc = if (hidden) L["achievement_hidden_desc"] else descriptions[i]
        ui.textO(c, title, tx, y + 24 * dp, ui.fit(title, 15f, textWidth), if (unlocked) Ui.GOLD else Ui.TEXT)
        ui.text(c, desc, tx, y + 44 * dp, ui.fit(desc, 12f, textWidth), Ui.DIM)
        if (!hidden) {
            ui.bar(c, tx, y + 56 * dp, max(12 * dp, textWidth - 90 * dp), 7 * dp,
                lastProgress[i].toFloat() / a.threshold.toFloat(), if (unlocked) Ui.GOOD else Ui.XP)
            ui.textO(c, progressText[i], tx, y + 78 * dp, 11f, Ui.DIM)
        }
        val right = row.right - 23 * dp
        Icons.draw(c, Icons.MEDAL, right, y + 30 * dp, 21 * dp, dp = dp)
        ui.textO(c, if (hidden) "?" else rewardText[i], right, y + 56 * dp,
            13f, Ui.GOLD, Paint.Align.CENTER)
    }

    override fun onClick(b: Button) {
        if (b.id == -1) g.pop()
    }
}
