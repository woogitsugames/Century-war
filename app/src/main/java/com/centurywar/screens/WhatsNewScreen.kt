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
/** [fromMenu]: wejście przyciskiem „Co nowego” w menu — taki sam tytuł jak przycisk (decyzja właściciela 3.11). */
class WhatsNewScreen(
    g: Game,
    private val automatic: Boolean = false,
    private val fromMenu: Boolean = false,
) : Screen(g) {
    override val crashName get() = "WhatsNewScreen"
    override val blocksNotices get() = true

    private val bg = MenuBg()
    private val chrome = Chrome(this)
    private val close = btn(1)
    private val panel = RectF()
    /** Dzień 4: strzałka „więcej niżej” — na telefonie poziomo najnowszy wpis bywa pod krawędzią. */
    private val scrollCue = ScrollCue()
    private val versions = if (automatic)
        listOfNotNull(WhatsNewCatalog.forVersion(BuildConfig.VERSION_NAME))
    else WhatsNewCatalog.releases

    init {
        // Dzień 2: otwarcie historii zmian (z menu lub z O grze) to przeczytanie nowości,
        // więc przycisk „Co nowego” w menu znika.
        if (!automatic) markRead(g)
    }

    internal companion object {
        /** Czy menu ma pokazać przycisk „Co nowego” (nieprzeczytane notatki tej wersji). */
        fun unread(g: Game): Boolean = WhatsNewCatalog.shouldShow(g.profile.hasExistingSave,
            g.profile.whatsNewSeenVersion, BuildConfig.VERSION_NAME)

        fun markRead(g: Game) {
            if (!unread(g)) return
            g.profile.whatsNewSeenVersion = BuildConfig.VERSION_NAME
            g.profile.save()
        }
    }

    /** [release]: indeks wydania w [versions] (−1 = brak wpisów); czytnik czyta całe wydanie naraz. */
    private data class Line(val text: String, val y: Float, val heading: Boolean, val bullet: Boolean, val release: Int = -1)
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
        // Tura 10: 48 dp jak pozostałe przyciski (wcześniej 44 dp).
        close.set(cx - buttonWidth * 0.5f, panel.bottom - 61 * dp,
            buttonWidth, 48 * dp)
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
                close.set(cx - buttonWidth * 0.5f, panel.bottom - 61 * dp,
                    buttonWidth, 48 * dp)
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
            lines.add(Line(L["wn_empty"], y, false, false, 0))
            y += 26 * dp * ui.textScale
        } else {
            val wrapped = arrayOfNulls<String>(20)
            for ((index, release) in versions.withIndex()) {
                lines.add(Line(release.version, y, true, false, index))
                y += 32 * dp * ui.textScale
                for (key in release.textKeys) {
                    val n = HintFrame.wrapHintInto(ui, L[key], lineWidth, 16f, wrapped)
                    for (i in 0 until n) {
                        lines.add(Line(wrapped[i] ?: "", y, false, i == 0, index))
                        y += 21 * dp * ui.textScale
                    }
                    y += 11 * dp
                }
                y += 16 * dp
            }
        }

        linesHeight = y
        a11yLang = -1
        maxScroll = max(0f, y - (contentBottom - contentTop))
        scroll = scroll.coerceIn(0f, maxScroll)
    }

    override fun isDragScreen() = true

    // ------------------------------------------------ R11-01: czytnik ekranu

    private var a11yLang = -1
    private var a11yTexts = emptyArray<String>()

    override fun accessibilityTitle(): String = L[if (automatic || fromMenu) "wn_title" else "wn_history"]

    /** Jedna informacja na wydanie (numer wersji i jego punkty), gdy choć wiersz jest widoczny. */
    override fun accessibilityInfoCount(): Int = if (versions.isEmpty()) 1 else versions.size

    override fun accessibilityInfo(i: Int): String {
        if (a11yLang != L.lang) {
            a11yLang = L.lang
            a11yTexts = if (versions.isEmpty()) arrayOf(L["wn_empty"])
            else Array(versions.size) { r ->
                val release = versions[r]
                accessibilitySentences(release.version, *Array(release.textKeys.size) { L[release.textKeys[it]] })
            }
        }
        if (i >= a11yTexts.size || !releaseBounds(i, a11yArea)) return ""
        return a11yTexts[i]
    }

    private val a11yArea = RectF()

    /** Obszar widocznych wierszy wydania [release]; false, gdy żaden wiersz nie jest widoczny. */
    private fun releaseBounds(release: Int, out: RectF): Boolean {
        var top = Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        for (k in lines.indices) {
            val line = lines[k]
            if (line.release != release) continue
            val y = contentTop + line.y - scroll
            val lineTop = y - 20 * dp
            val lineBottom = y + 6 * dp
            if (lineBottom <= contentTop || lineTop >= contentBottom) continue
            if (lineTop < top) top = lineTop
            if (lineBottom > bottom) bottom = lineBottom
        }
        if (top > bottom) return false
        out.set(panel.left + 9 * dp, max(top, contentTop), panel.right - 9 * dp, min(bottom, contentBottom))
        return true
    }

    override fun accessibilityInfoBounds(i: Int, out: RectF) {
        if (!releaseBounds(i, out)) out.setEmpty()
    }

    override fun accessibilityCanScroll(direction: Int): Boolean =
        if (direction > 0) scroll < maxScroll - 0.5f else scroll > 0.5f

    override fun accessibilityScroll(direction: Int): Boolean {
        val moved = accessibilityStep(scroll, maxScroll, contentBottom - contentTop, direction)
        if (moved) scroll = scrollResult
        return moved
    }

    override fun onDrag(dx: Float, dy: Float) {
        scroll = (scroll - dy).coerceIn(0f, maxScroll)
    }

    override fun onLanguageChanged() { layout() }

    override fun update(dt: Float) { this.dt = dt }

    override fun render(c: Canvas) {
        bg.draw(c, w, h, dt)
        chrome.dim(c, 125)
        ui.panel(c, panel)
        chrome.title(c, L[if (automatic || fromMenu) "wn_title" else "wn_history"])

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
        scrollCue.vertical(c, panel.right - 22 * dp, contentTop, contentBottom, scroll, maxScroll, dp)
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
