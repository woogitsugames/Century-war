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
import com.centurywar.game.Mode
import com.centurywar.meta.Profile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.max

/**
 * D43: linia „Wersja testowa: pełne zasady wyzwań od premiery” w wyzwaniu dnia i tygodnia leżała
 * na złotej ramie panelu (wyglądała na przekreśloną). Teraz ma własny pas: cała (z konturem
 * i cieniem) mieści się w wewnętrznej lamówce, nie nachodzi na Graj i ma ≥ 11 sp — na telefonie
 * 720p, rozłożonym i zewnętrznym Foldzie, w 10 językach, przy tekście 100% i 130%.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class DailyRulesNoteLayoutTest {
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

    /** Pełny obrys napisu: ascent/descent czcionki, kontur textO i cień (y + 0,6 konturu). */
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
                val ow = max(2f, paint.textSize * 0.16f)
                texts.add(Drawn(text, RectF(left - ow / 2, y + fm.ascent - ow / 2,
                    left + w + ow / 2, y + fm.descent + ow * 1.1f), paint.textSize))
            }
            super.drawText(text, x, y, paint)
        }
    }

    private class Device(val name: String, val w: Int, val h: Int, val density: Float, val insetT: Int)
    private val devices = listOf(
        Device("phone720", 1600, 720, 1.8f, 0),
        Device("foldOpen", 2184, 1968, 2.7f, 0),
        Device("foldOuter", 904, 2316, 2.7f, 88),
    )

    private fun game(d: Device, lang: String, scale: Float, notes: Boolean): Game {
        val f = File.createTempFile("d43note", ".json").apply { delete() }
        val profile = Profile(f).apply {
            this.lang = lang; tutorialDone = true; hintsEnabled = false; eraIntrosEnabled = false
            hintsSeen = Profile.HINT_VALID_MASK
        }
        return Game(host, profile, Quality.LOW).apply {
            density = d.density
            resize(d.w, d.h, d.w, d.h)
            setInsets(0, d.insetT, 0, 0)
            if (scale != 1f) applySystemAccessibility(SystemA11y(scale, false, false))
            clearNotices()
            clock = { 1793461200000L } // 2026-10-31 UTC, przed Daily.RELEASE_DAY
            preReleaseNotesEnabled = notes
        }
    }

    private fun isNote(s: String): Boolean {
        val short = L["t203_rules_note_short"]
        return s == L["t203_rules_note"] || s == short ||
            (s.endsWith("…") && short.startsWith(s.removeSuffix("…").trimEnd()))
    }

    @Test fun noteInsideInnerTrimAboveFrameAndBelowPlay() {
        val problems = StringBuilder()
        val tiny = Canvas(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))
        for (d in devices) for (scale in floatArrayOf(1f, 1.3f)) for (lang in L.CODES) for (weekly in listOf(false, true)) {
            val g = game(d, lang, scale, notes = true)
            val ms = ModeScreen(g, Mode.DAILY, weekly)
            g.setScreen(ms)
            g.finishScreenTransition()
            repeat(3) { g.frame(tiny, 1f / 30f) }
            val rec = Recorder(Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888))
            g.frame(rec, 1f / 30f)
            val tag = "${d.name} $lang ${(scale * 100).toInt()}%${if (weekly) " tydzień" else ""}"
            val note = rec.texts.filter { isNote(it.s) }
            if (note.size != 1) { problems.append("$tag: linia narysowana ${note.size} razy\n"); continue }
            val t = note[0]
            val inner = g.ui.panelInner(ms.panelForTests, RectF())
            val sp = t.sizePx / g.ui.dp / g.ui.textScale
            if (sp < 11f - 0.05f) problems.append("$tag: ${"%.1f".format(sp)} sp\n")
            if (!inner.contains(t.r)) problems.append("$tag: poza wewnętrzną lamówką ${t.r} / $inner\n")
            if (RectF.intersects(t.r, ms.startForTests)) problems.append("$tag: nachodzi na Graj\n")
        }
        assertTrue(problems.toString(), problems.isEmpty())
    }

    /** Bez dopisku (wydanie, dzień premiery) Graj stoi jak dotąd: 24 dp nad dolną krawędzią panelu. */
    @Test fun withoutNotePlayButtonKeepsItsPlace() {
        for (d in devices) {
            val g = game(d, "pl", 1.3f, notes = false)
            val ms = ModeScreen(g, Mode.DAILY)
            g.setScreen(ms)
            assertFalse(ms.noteShown)
            assertEquals(d.name, ms.panelForTests.bottom - 24 * g.ui.dp, ms.startForTests.bottom, 0.5f)
        }
    }
}
