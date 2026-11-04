package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.engine.SystemA11y
import com.centurywar.meta.Profile
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Dzień 26 (R11, pozycja 8): na zewnętrznym Foldzie siatka statystyk kart Kodeksu („Odstęp 2 / 3”
 * przy „0.9 s / 0.7 s”) i selektory epoki/roli w porównaniu jednostek mają ≥ 10 sp i nie nachodzą
 * na siebie ani na strzałki, w 10 językach przy tekście 100% i 130%. Listwy zakładek mają znak
 * przewijania. Telefon i rozłożony Fold zachowują dotychczasowy układ.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CodexFoldOuterLabelsTest {
    private val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
    }
    private var savedLang = 0
    @Before fun saveLang() { savedLang = L.lang }
    @After fun restoreLang() { L.lang = savedLang }

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
                texts.add(Drawn(text, RectF(left, y + fm.ascent * 0.8f, left + w, y + fm.descent * 0.6f), paint.textSize))
            }
            super.drawText(text, x, y, paint)
        }
    }

    private fun game(lang: String, scale: Float, w: Int = 904, h: Int = 2316, insetT: Int = 88): Game {
        val f = File.createTempFile("codex26", ".json").apply { delete() }
        val profile = Profile(f).apply { this.lang = lang; tutorialDone = true; hintsEnabled = false; eraIntrosEnabled = false }
        return Game(host, profile, Quality.LOW).apply {
            density = 2.7f
            resize(w, h, w, h)
            setInsets(0, insetT, 0, 0)
            if (scale != 1f) applySystemAccessibility(SystemA11y(scale, false, false))
            clearNotices()
        }
    }

    private val tiny = Canvas(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))

    private fun narrowStats(cx: CodexScreen): Boolean = cx.narrowStats

    @Test
    fun statsAndCompareSelectorsReadableOnFoldOuter() {
        val problems = StringBuilder()
        var reloadSeen = 0
        for (scale in floatArrayOf(1f, 1.3f)) for (lang in L.CODES) {
            val g = game(lang, scale)
            val cx = CodexScreen(g, 1)
            g.setScreen(cx)
            repeat(3) { g.frame(tiny, 1f / 30f) }
            val dp = g.ui.dp
            val tag = "$lang ${(scale * 100).toInt()}%"
            for (page in intArrayOf(0, 1, 2, 3, 4)) {
                cx.showCategory(page)
                if (!narrowStats(cx)) problems.append("$tag p$page: brak jednej kolumny\n")
                val stats = cx.statTextsForTest()
                repeat(16) {
                    val rec = Recorder(Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888))
                    g.frame(rec, 1f / 30f)
                    val drawn = rec.texts.filter { it.s in stats }
                    for ((i, t) in drawn.withIndex()) {
                        val sp = t.sizePx / dp / g.ui.textScale
                        if (t.s == L["turret140_reload"]) reloadSeen++
                        if (sp < 10f - 0.05f) problems.append("$tag p$page '${t.s}' ${"%.1f".format(sp)} sp\n")
                        for (j in 0 until i) if (RectF.intersects(t.r, drawn[j].r))
                            problems.append("$tag p$page '${t.s}' nachodzi na '${drawn[j].s}'\n")
                    }
                    cx.onDrag(0f, -250f * dp)
                }
            }
            cx.showCategory(10)
            val panel = cx.comparePanelForTest
            if (!panel.stacked) problems.append("$tag: selektory porównania nie są jeden pod drugim\n")
            val rec = Recorder(Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888))
            g.frame(rec, 1f / 30f)
            val names = panel.selectorTextsForTest().toSet()
            val bottom = panel.selectorTop + panel.controlHeight
            val drawn = rec.texts.filter { it.s in names && it.r.centerY() in panel.selectorTop..bottom }
            if (drawn.size < 4) problems.append("$tag: selektory narysowane ${drawn.size}/4\n")
            for (t in drawn) {
                val sp = t.sizePx / dp / g.ui.textScale
                if (sp < 10f - 0.05f) problems.append("$tag selektor '${t.s}' ${"%.1f".format(sp)} sp\n")
                for (b in cx.buttons) if (b.id in 300..307 && RectF.intersects(t.r, b.r))
                    problems.append("$tag selektor '${t.s}' na strzałce\n")
            }
        }
        if (reloadSeen == 0) problems.append("Nie narysowano „Odstęp 2 / 3”\n")
        assertTrue(problems.toString(), problems.isEmpty())
    }

    /** Listwy kategorii i epok przewijają się i mają strzałkę przewijania (ScrollCue). */
    @Test
    fun railsShowScrollCueOnFoldOuter() {
        val g = game("de", 1f)
        val cx = CodexScreen(g, 0)
        g.setScreen(cx)
        repeat(3) { g.frame(tiny, 1f / 30f) }
        // Strzałka to zaokrąglony prostokąt 22×20 dp przy prawej krawędzi listwy.
        var cues = 0
        val dp = g.ui.dp
        val rec = object : Canvas(Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)) {
            override fun drawRoundRect(l: Float, t: Float, r: Float, b: Float, rx: Float, ry: Float, p: Paint) {
                if (kotlin.math.abs((r - l) - 22 * dp) < 0.5f && kotlin.math.abs((b - t) - 20 * dp) < 0.5f) cues++
                super.drawRoundRect(l, t, r, b, rx, ry, p)
            }
        }
        g.frame(rec, 1f / 30f)
        assertTrue("strzałki listew: $cues", cues >= 2)
    }

    /** Inne ekrany bez zmian: telefon i rozłożony Fold. */
    @Test
    fun otherScreensKeepLayout() {
        for ((w, h, t) in listOf(Triple(1600, 720, 0), Triple(2400, 1080, 0), Triple(2184, 1968, 70))) {
            val g = game("de", 1f, w, h, t)
            val cx = CodexScreen(g, 1)
            g.setScreen(cx)
            repeat(3) { g.frame(tiny, 1f / 30f) }
            cx.showCategory(3)
            assertTrue("$w×$h", !cx.narrowStats)
            cx.showCategory(10)
            assertTrue("$w×$h", !cx.comparePanelForTest.stacked)
        }
    }
}
