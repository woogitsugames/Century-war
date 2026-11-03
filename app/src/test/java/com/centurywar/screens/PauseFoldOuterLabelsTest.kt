package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.engine.SystemA11y
import com.centurywar.engine.Ui
import com.centurywar.game.BattleConfig
import com.centurywar.game.Mode
import com.centurywar.meta.Profile
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Dzień 7: napisy przycisków pauzy („Wznów”, „Od nowa”, „Poddaj się”) na zewnętrznym ekranie
 * Folda mają co najmniej 10 sp (przy 100% i 130% tekstu) we wszystkich 10 językach, mieszczą się
 * w przyciskach, a przyciski zachowują 12–16 dp odstępu od złotej lamówki panelu.
 * Pozostałe ekrany (telefon, rozłożony Fold) zachowują dotychczasowy układ rzędu przycisków.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PauseFoldOuterLabelsTest {
    private val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
    }

    private class Recorder(bmp: Bitmap) : Canvas(bmp) {
        class Drawn(val s: String, val r: RectF, val sizePx: Float)
        val texts = ArrayList<Drawn>()
        override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
            if (paint.style == Paint.Style.FILL && text.isNotBlank()) {
                val w = paint.measureText(text)
                val left = when (paint.textAlign) {
                    Paint.Align.CENTER -> x - w / 2f
                    Paint.Align.RIGHT -> x - w
                    else -> x
                }
                val fm = paint.fontMetrics
                texts.add(Drawn(text, RectF(left, y + fm.ascent * 0.8f, left + w, y + fm.descent * 0.6f),
                    paint.textSize))
            }
            super.drawText(text, x, y, paint)
        }
    }

    private fun game(w: Int, h: Int, density: Float, insetT: Int, lang: String, scale: Float): Game {
        val f = File.createTempFile("pause7", ".json").apply { delete() }
        val profile = Profile(f).apply { this.lang = lang; tutorialDone = true; hintsEnabled = false; eraIntrosEnabled = false }
        return Game(host, profile, Quality.LOW).apply {
            this.density = density
            resize(w, h, w, h)
            setInsets(0, insetT, 0, 0)
            if (scale != 1f) applySystemAccessibility(SystemA11y(scale, false, false))
        }
    }

    private fun pausedBattle(g: Game): BattleScreen {
        val bs = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 7L, 1f))
        g.setScreen(bs)
        val tiny = Canvas(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))
        repeat(3) { g.frame(tiny, 1f / 30f) }
        bs.onClick(bs.pauseBtn)
        g.frame(tiny, 1f / 30f)
        return bs
    }

    private fun panel(bs: BattleScreen): RectF =
        BattleScreen::class.java.getDeclaredField("pausePanel").apply { isAccessible = true }.get(bs) as RectF

    @Test
    fun foldOuterPauseLabelsReadableInAllLanguages() {
        val old = L.lang
        val problems = StringBuilder()
        val report = StringBuilder()
        try {
            for (scale in floatArrayOf(1f, 1.3f)) for (lang in L.CODES) {
                val g = game(904, 2316, 2.7f, 88, lang, scale)
                val bs = pausedBattle(g)
                val dp = g.ui.dp
                val bmp = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
                val rec = Recorder(bmp)
                g.frame(rec, 1f / 30f)
                val p = panel(bs)
                val trim = Ui.PANEL_TRIM_INSET * dp
                for (b: Button in bs.pauseMenu) {
                    val r = b.r
                    val t = rec.texts.lastOrNull { it.s == b.label && r.contains(it.r.centerX(), it.r.centerY()) }
                    val where = "$lang ${(scale * 100).toInt()}% '${b.label}'"
                    if (t == null) { problems.append("$where: brak napisu\n"); continue }
                    // Rozmiar w sp: px / gęstość / skala tekstu z telefonu.
                    val sp = t.sizePx / dp / g.ui.textScale
                    report.append("$where: ${"%.1f".format(sp)} sp, przycisk ${"%.0f".format(r.width() / dp)}×${"%.0f".format(r.height() / dp)} dp\n")
                    if (sp < 10f - 0.05f) problems.append("$where: ${"%.1f".format(sp)} sp < 10\n")
                    if (t.r.left < r.left + 1f || t.r.right > r.right - 1f) problems.append("$where: napis wychodzi poza przycisk\n")
                    val gapL = (r.left - (p.left + trim)) / dp
                    val gapR = ((p.right - trim) - r.right) / dp
                    val gapB = ((p.bottom - trim) - r.bottom) / dp
                    for ((side, gap) in listOf("lewo" to gapL, "prawo" to gapR, "dół" to gapB)) {
                        // Kolumna: boki każdego przycisku, dół tylko ostatniego.
                        if (side == "dół" && b !== bs.pauseMenu.last()) continue
                        if (gap < 12f - 0.05f || gap > 16f + 0.05f)
                            problems.append("$where: odstęp od lamówki ($side) ${"%.1f".format(gap)} dp\n")
                    }
                    if (r.height() < 48f * dp - 0.5f) problems.append("$where: przycisk niższy niż 48 dp\n")
                }
                if (!bs.pauseStacked) problems.append("$lang: przyciski pauzy nie są w kolumnie\n")
                for (i in 1 until bs.pauseMenu.size) {
                    if (RectF.intersects(bs.pauseMenu[i - 1].r, bs.pauseMenu[i].r)) problems.append("$lang: przyciski nachodzą\n")
                }
                // Szybkie ustawienia i panel nad przyciskami, wszystko w bezpiecznym obszarze.
                val speed = (BattleScreen::class.java.getDeclaredField("pauseSpeedBtn")
                    .apply { isAccessible = true }.get(bs) as Button).r
                if (speed.bottom >= bs.pauseMenu[0].r.top) problems.append("$lang: tempo nachodzi na przyciski\n")
                if (p.top < g.insetT || p.bottom > g.height - g.insetB) problems.append("$lang: panel poza ekranem\n")
            }
        } finally {
            L.lang = old
            File("build/screenshots").mkdirs()
            File("build/screenshots/day7_pause_foldOuter.txt").writeText(report.toString() + "\n" + problems)
        }
        assertTrue("Pauza na zewnętrznym ekranie Folda:\n$problems", problems.isEmpty())
    }

    /** Telefon 720p, 20:9 i rozłożony Fold: rząd przycisków pauzy bez zmian (jedna linia, 48 dp). */
    @Test
    fun otherScreensKeepSingleRowOfPauseButtons() {
        val old = L.lang
        try {
            for ((w, h, d) in listOf(Triple(1600, 720, 2f), Triple(2400, 1080, 2.7f), Triple(2184, 1968, 2.7f))) {
                val g = game(w, h, d, 0, "pl", 1f)
                val bs = pausedBattle(g)
                val dp = g.ui.dp
                val p = panel(bs)
                val row = bs.pauseMenu.map { it.r }
                val side = (Ui.PANEL_TRIM_INSET + Ui.PANEL_BUTTON_MARGIN) * dp
                assertTrue("$w×$h", !bs.pauseStacked)
                assertTrue("$w×$h", row.all { kotlin.math.abs(it.top - row[0].top) < 0.5f })
                assertTrue("$w×$h", kotlin.math.abs(row[0].left - (p.left + side)) < 0.5f)
                assertTrue("$w×$h", kotlin.math.abs(row[2].right - (p.right - side)) < 0.5f)
                assertTrue("$w×$h", kotlin.math.abs(row[0].bottom - (p.bottom - side)) < 0.5f)
                assertTrue("$w×$h", row.all { kotlin.math.abs(it.height() - 48 * dp) < 0.5f })
            }
        } finally {
            L.lang = old
        }
    }
}
