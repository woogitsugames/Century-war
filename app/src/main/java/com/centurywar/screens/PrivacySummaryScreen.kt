package com.centurywar.screens

import android.graphics.Canvas
import android.graphics.RectF
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.L
import com.centurywar.engine.Screen
import com.centurywar.engine.Ui
import kotlin.math.max
import kotlin.math.min

/** Krótkie informacje o prywatności dostępne także bez adresu strony i bez internetu. */
class PrivacySummaryScreen(g: Game) : Screen(g) {
    override val crashName get() = "PrivacySummaryScreen"
    private val bg = MenuBg()
    private val chrome = Chrome(this)
    private val back = btn(-1)
    private val panel = RectF()
    private val lines = ArrayList<String>()
    private var scroll = 0f
    private var maxScroll = 0f
    private var lineHeight = 0f
    private var dt = 0f

    override fun isDragScreen() = true

    override fun onDrag(dx: Float, dy: Float) {
        scroll = (scroll - dy).coerceIn(0f, maxScroll)
    }

    override fun onLanguageChanged() {
        layout()
    }

    override fun layout() {
        chrome.layoutBack(back)
        back.label = L["back"]
        val pw = min(820f * dp, w - g.insetL - g.insetR - 24f * dp)
        val left = (w - pw) / 2f
        panel.set(left, chrome.top + 7f * dp, left + pw, h - g.insetB - 11f * dp)

        // Zawijanie tylko przy zmianie rozmiaru/języka, bez alokacji na klatkę.
        lines.clear()
        val width = panel.width() - 44f * dp
        for (key in SECTIONS) {
            if (lines.isNotEmpty()) lines.add("")
            val words = L[key].split(' ')
            var line = ""
            for (word in words) {
                val candidate = if (line.isEmpty()) word else "$" + "{line} $" + "{word}"
                if (line.isNotEmpty() && ui.measure(candidate, 15f) > width) {
                    lines.add(line)
                    line = word
                } else {
                    line = candidate
                }
            }
            if (line.isNotEmpty()) lines.add(line)
        }
        lineHeight = 21f * dp * ui.textScale
        val viewHeight = panel.height() - 28f * dp
        maxScroll = max(0f, lines.size * lineHeight + 22f * dp - viewHeight)
        scroll = scroll.coerceIn(0f, maxScroll)
    }

    override fun update(dt: Float) { this.dt = dt }

    override fun render(c: Canvas) {
        bg.draw(c, w, h, dt)
        chrome.dim(c, 120)
        ui.panel(c, panel)
        c.save()
        c.clipRect(panel.left + 9f * dp, panel.top + 10f * dp,
            panel.right - 9f * dp, panel.bottom - 10f * dp)
        val x = panel.left + 22f * dp
        var y = panel.top + 31f * dp - scroll
        for (line in lines) {
            if (line.isNotEmpty() && y >= panel.top + 7f * dp && y <= panel.bottom) {
                ui.text(c, line, x, y, 15f, Ui.TEXT)
            }
            y += lineHeight
        }
        c.restore()
        chrome.title(c, L["privacy93_title"])
        ui.button(c, back, 16f)
    }

    override fun onClick(b: Button) {
        if (b.id == -1) g.pop()
    }

    companion object {
        private val SECTIONS = arrayOf(
            "privacy93_intro",
            "privacy93_local",
            "privacy93_google",
            "privacy93_server",
            "privacy93_remove",
            "privacy93_summary_note",
        )
    }
}
