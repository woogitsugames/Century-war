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
import kotlin.math.max
import kotlin.math.min

/** Dodatkowe ustawienia: nie ściskamy głównej listy na telefonach 720p. */
class HintSettingsScreen(g: Game) : Screen(g) {
    override val crashName get() = "HintSettingsScreen"
    private val bg = MenuBg()
    private val chrome = Chrome(this)
    private val back = btn(-1)
    private val toggle = btn(1)
    private val tutorial = btn(2)
    private val reset = btn(3)
    private val rows = arrayOf(toggle, tutorial, reset)
    private val keys = arrayOf("hint_enabled", "hint_replay_label", "hint_reset_label")
    private val icons = intArrayOf(Icons.BOOK, Icons.RESTART, Icons.CHEVRONS)
    private val panel = RectF()
    private val row = RectF()
    private var tutorialArmed = 0f
    private var resetArmed = 0f
    private var dt = 0f

    override fun layout() {
        chrome.layoutBack(back)
        val pw = min(660 * dp, w - g.insetL - g.insetR - 26 * dp)
        val ph = min(302 * dp, h - chrome.top - g.insetB - 26 * dp)
        val top = chrome.top + 10 * dp + max(0f, (h - chrome.top - g.insetB - ph - 16 * dp) * 0.3f)
        panel.set(w / 2f - pw / 2, top, w / 2f + pw / 2, top + ph)
        val step = (ph - 44 * dp) / rows.size
        val bw = min(220 * dp, pw * 0.46f)
        for (i in rows.indices) rows[i].set(panel.right - bw - 13 * dp,
            panel.top + 24 * dp + i * step + 5 * dp, bw, min(44 * dp, step - 10 * dp))
        tutorial.iconId = Icons.RESTART
        reset.iconId = Icons.CHEVRONS
    }

    override fun update(dt: Float) {
        this.dt = dt
        if (tutorialArmed > 0f) tutorialArmed -= dt
        if (resetArmed > 0f) resetArmed -= dt
        back.label = L["back"]
        toggle.label = L[if (g.profile.hintsEnabled) "on" else "off"]
        toggle.color = if (g.profile.hintsEnabled) Ui.BTN_GREEN else Ui.BTN
        tutorial.label = L[if (tutorialArmed > 0f) "hint_confirm" else "hint_replay"]
        tutorial.color = if (tutorialArmed > 0f) Ui.BTN_RED else Ui.BTN
        reset.label = L[if (resetArmed > 0f) "hint_confirm" else "hint_reset"]
        reset.color = if (resetArmed > 0f) Ui.BTN_RED else Ui.BTN
    }

    override fun render(c: Canvas) {
        bg.draw(c, w, h, dt)
        chrome.dim(c, 120)
        ui.panel(c, panel)
        chrome.title(c, L["hint_settings"])
        for (i in rows.indices) {
            val b = rows[i]
            row.set(panel.left + 10 * dp, b.r.top - 3 * dp, panel.right - 10 * dp, b.r.bottom + 3 * dp)
            ui.fill.color = if (i % 2 == 0) Color.argb(30, 255, 255, 255) else Color.argb(12, 255, 255, 255)
            c.drawRoundRect(row, 10 * dp, 10 * dp, ui.fill)
            Icons.draw(c, icons[i], row.left + 19 * dp, row.centerY(), 23 * dp, dp = dp)
            val label = L[keys[i]]
            ui.textO(c, label, row.left + 42 * dp, row.centerY() + 5 * dp,
                ui.fit(label, 15f, b.r.left - row.left - 50 * dp, 9f), Ui.TEXT, Paint.Align.LEFT)
            ui.button(c, b, 13f)
        }
        ui.button(c, back, 16f)
    }

    override fun onClick(b: Button) {
        when (b) {
            back -> g.pop()
            toggle -> {
                g.profile.hintsEnabled = !g.profile.hintsEnabled
                g.profile.save()
            }
            tutorial -> {
                if (tutorialArmed <= 0f) { tutorialArmed = 4f; resetArmed = 0f }
                else {
                    g.profile.replayTutorial()
                    tutorialArmed = 0f
                    g.showNotice(L["hint_tutorial_ready"], Icons.BOOK)
                }
            }
            reset -> {
                if (resetArmed <= 0f) { resetArmed = 4f; tutorialArmed = 0f }
                else {
                    g.profile.replayHints()
                    resetArmed = 0f
                    g.showNotice(L["hint_reset_done"], Icons.CHECK)
                }
            }
        }
    }
}
