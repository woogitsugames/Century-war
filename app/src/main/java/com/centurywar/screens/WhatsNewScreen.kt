package com.centurywar.screens

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.centurywar.BuildConfig
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.Icons
import com.centurywar.engine.L
import com.centurywar.engine.Screen
import com.centurywar.engine.Ui
import com.centurywar.meta.WhatsNewCatalog
import kotlin.math.max
import kotlin.math.min

/** Krótka lista zmian po aktualizacji; z O grze dostępne jest pełne archiwum. */
class WhatsNewScreen(g: Game, private val automatic: Boolean = false) : Screen(g) {
    override val crashName get() = "WhatsNewScreen"
    override val blocksNotices get() = true

    private val bg = MenuBg()
    private val chrome = Chrome(this)
    private val close = btn(1)
    private val panel = RectF()
    private val versions = if (automatic)
        listOfNotNull(WhatsNewCatalog.forVersion(BuildConfig.VERSION_NAME))
    else WhatsNewCatalog.releases

    private data class Line(val text: String, val y: Float, val heading: Boolean, val bullet: Boolean)
    private val lines = ArrayList<Line>(24)
    private var contentTop = 0f
    private var contentBottom = 0f
    private var scroll = 0f
    private var maxScroll = 0f
    private var linesHeight = 0f
    private var dt = 0f

    override fun layout() {
        close.label = L[if (automatic) "wn_continue" else "back"]
        close.iconId = if (automatic) Icons.CHECK else Icons.BACK
        close.highlight = true

        // Wykrywanie tabletu bazuje na fizycznym dp, a nie powiększonym ui.dp.
        val largeTablet = (w - g.insetL - g.insetR) / g.density >= 960f
        val width = min((if (largeTablet) 620 else 760) * dp,
            w - g.insetL - g.insetR - 24 * dp).coerceAtLeast(1f)
        val cx = (w + g.insetL - g.insetR) * 0.5f
        panel.set(cx - width * 0.5f, chrome.top + 8 * dp,
            cx + width * 0.5f, max(chrome.top + 90 * dp, h - g.insetB - 12 * dp))

        val buttonWidth = min(220 * dp, (width - 36 * dp).coerceAtLeast(1f))
        close.set(cx - buttonWidth * 0.5f, panel.bottom - 57 * dp,
            buttonWidth, 44 * dp)
        contentTop = panel.top + 18 * dp
        contentBottom = close.r.top - 14 * dp
        rebuildLines()
        // Na dużym tablecie krótka lista zmian nie wypełnia pustego panelu.
        // Wymiary telefonów i Folda pozostają bez zmian.
        if (largeTablet) {
            val naturalHeight = max(205 * dp, linesHeight + 89 * dp)
            if (naturalHeight < panel.height()) {
                val newTop = panel.top + (panel.height() - naturalHeight) * 0.5f
                panel.set(panel.left, newTop, panel.right, newTop + naturalHeight)
                close.set(cx - buttonWidth * 0.5f, panel.bottom - 57 * dp,
                    buttonWidth, 44 * dp)
                contentTop = panel.top + 18 * dp
                contentBottom = close.r.top - 14 * dp
                rebuildLines()
            }
        }
    }

    private fun rebuildLines() {
        lines.clear()
        val lineWidth = (panel.width() - 95 * dp).coerceAtLeast(24 * dp)
        var y = 16 * dp

        if (versions.isEmpty()) {
            lines.add(Line(L["wn_empty"], y, false, false))
            y += 26 * dp * ui.textScale
        } else {
            val wrapped = arrayOfNulls<String>(20)
            for (release in versions) {
                lines.add(Line(release.version, y, true, false))
                y += 32 * dp * ui.textScale
                for (key in release.textKeys) {
                    val n = HintFrame.wrapHintInto(ui, L[key], lineWidth, 16f, wrapped)
                    for (i in 0 until n) {
                        lines.add(Line(wrapped[i] ?: "", y, false, i == 0))
                        y += 21 * dp * ui.textScale
                    }
                    y += 11 * dp
                }
                y += 16 * dp
            }
        }

        linesHeight = y
        maxScroll = max(0f, y - (contentBottom - contentTop))
        scroll = scroll.coerceIn(0f, maxScroll)
    }

    override fun isDragScreen() = true

    override fun onDrag(dx: Float, dy: Float) {
        scroll = (scroll - dy).coerceIn(0f, maxScroll)
    }

    override fun onLanguageChanged() { layout() }

    override fun update(dt: Float) { this.dt = dt }

    override fun render(c: Canvas) {
        bg.draw(c, w, h, dt)
        chrome.dim(c, 125)
        ui.panel(c, panel)
        chrome.title(c, L[if (automatic) "wn_title" else "wn_history"])

        val saved = c.save()
        c.clipRect(panel.left + 9 * dp, contentTop, panel.right - 9 * dp, contentBottom)
        for (i in lines.indices) {
            val line = lines[i]
            val y = contentTop + line.y - scroll
            if (y < contentTop - 24 * dp || y > contentBottom + 4 * dp) continue
            if (line.heading) {
                ui.textO(c, line.text, panel.left + 28 * dp, y, 20f,
                    Ui.GOLD, Paint.Align.LEFT)
            } else {
                if (line.bullet) {
                    ui.fill.color = Ui.GOLD
                    c.drawCircle(panel.left + 37 * dp, y - 5 * dp, 3.5f * dp, ui.fill)
                }
                ui.text(c, line.text, panel.left + 53 * dp, y, 16f, Ui.TEXT)
            }
        }
        c.restoreToCount(saved)
        ui.button(c, close, 16f)
    }

    private fun dismiss() {
        if (automatic) {
            val currentVersion = BuildConfig.VERSION_NAME
            if (g.profile.whatsNewSeenVersion != currentVersion) {
                g.profile.whatsNewSeenVersion = currentVersion
                g.profile.save()
            }
            g.setScreen(MenuScreen(g))
        } else {
            g.pop()
        }
    }

    override fun onClick(b: Button) {
        if (b === close) dismiss()
    }

    override fun onBack(): Boolean {
        dismiss()
        return true
    }
}
