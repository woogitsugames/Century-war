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
    private val eraToggle = btn(4)
    private val eraReplay = btn(5)
    private val rows = arrayOf(toggle, tutorial, reset, eraToggle, eraReplay)
    private val keys = arrayOf("hint_enabled", "hint_replay_label", "hint_reset_label",
        "era_intro_enabled", "era_intro_again_label")
    private val icons = intArrayOf(Icons.BOOK, Icons.RESTART, Icons.CHEVRONS, Icons.EVOLVE, Icons.RESTART)
    private val panel = RectF()
    private val row = RectF()
    private var tutorialArmed = 0f
    private var resetArmed = 0f
    private var eraArmed = 0f
    private var dt = 0f
    private var rowPad = 0f

    override fun layout() {
        chrome.layoutBack(back)
        val pw = min(660 * dp, w - g.insetL - g.insetR - 26 * dp)
        val ph = min(424 * dp, h - chrome.top - g.insetB - 26 * dp)
        val top = chrome.top + 10 * dp + max(0f, (h - chrome.top - g.insetB - ph - 16 * dp) * 0.3f)
        panel.set(w / 2f - pw / 2, top, w / 2f + pw / 2, top + ph)
        var step = (ph - 44 * dp) / rows.size
        var first = panel.top + 29 * dp
        // Tura 10: na 720p przyciski miały 41 dp; ciaśniejsze marginesy dają pełne 48 dp.
        if (step - 4 * dp < 48 * dp) {
            step = (ph - 24 * dp) / rows.size
            first = panel.top + 12 * dp
        }
        val bw = min(220 * dp, pw * 0.46f)
        for (i in rows.indices) rows[i].set(panel.right - bw - 13 * dp,
            first + i * step, bw, min(48 * dp, step - 0.5f * dp))
        rowPad = min(3 * dp, max(0f, (step - rows[0].r.height()) / 2f))
        tutorial.iconId = Icons.RESTART
        reset.iconId = Icons.CHEVRONS
        eraToggle.iconId = Icons.EVOLVE
        eraReplay.iconId = Icons.RESTART
    }

    override fun update(dt: Float) {
        this.dt = dt
        if (tutorialArmed > 0f) tutorialArmed -= dt
        if (resetArmed > 0f) resetArmed -= dt
        if (eraArmed > 0f) eraArmed -= dt
        back.label = L["back"]
        toggle.label = L[if (g.profile.hintsEnabled) "on" else "off"]
        toggle.color = if (g.profile.hintsEnabled) Ui.BTN_GREEN else Ui.BTN
        tutorial.label = L[if (tutorialArmed > 0f) "hint_confirm" else "hint_replay"]
        tutorial.color = if (tutorialArmed > 0f) Ui.BTN_RED else Ui.BTN
        reset.label = L[if (resetArmed > 0f) "hint_confirm" else "hint_reset"]
        reset.color = if (resetArmed > 0f) Ui.BTN_RED else Ui.BTN
        eraToggle.label = L[if (g.profile.eraIntrosEnabled) "on" else "off"]
        eraToggle.color = if (g.profile.eraIntrosEnabled) Ui.BTN_GREEN else Ui.BTN
        eraReplay.label = L[if (eraArmed > 0f) "hint_confirm" else "era_intro_again"]
        eraReplay.color = if (eraArmed > 0f) Ui.BTN_RED else Ui.BTN
    }

    override fun render(c: Canvas) {
        bg.draw(c, w, h, dt)
        chrome.dim(c, 120)
        ui.panel(c, panel)
        chrome.title(c, L["hint_settings"])
        for (i in rows.indices) {
            val b = rows[i]
            val pad = rowPad
            row.set(panel.left + 10 * dp, b.r.top - pad, panel.right - 10 * dp, b.r.bottom + pad)
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

    override fun accessibilityLabel(b: Button): String {
        if (b.id == -1) return L["back"]
        if (b.id !in 1..5) return super.accessibilityLabel(b)
        return L[keys[b.id - 1]] + ": " + b.label
    }

    override fun accessibilityCheckable(b: Button): Boolean = b === toggle || b === eraToggle

    override fun accessibilityChecked(b: Button): Boolean = when (b) {
        toggle -> g.profile.hintsEnabled
        eraToggle -> g.profile.eraIntrosEnabled
        else -> super.accessibilityChecked(b)
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
                if (resetArmed <= 0f) { resetArmed = 4f; tutorialArmed = 0f; eraArmed = 0f }
                else {
                    g.profile.replayHints()
                    resetArmed = 0f
                    g.showNotice(L["hint_reset_done"], Icons.CHECK)
                }
            }
            eraToggle -> {
                g.profile.eraIntrosEnabled = !g.profile.eraIntrosEnabled
                g.profile.save()
            }
            eraReplay -> {
                if (eraArmed <= 0f) { eraArmed = 4f; tutorialArmed = 0f; resetArmed = 0f }
                else {
                    g.profile.replayEraIntros()
                    eraArmed = 0f
                    g.showNotice(L["era_intro_again_done"], Icons.CHECK)
                }
            }
        }
    }
}
