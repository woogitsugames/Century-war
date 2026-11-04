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

/** Pomiar napisów Kodeksu na zewnętrznym Foldzie (eksploracja). */
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

    private fun game(lang: String, scale: Float): Game {
        val f = File.createTempFile("codex26", ".json").apply { delete() }
        val profile = Profile(f).apply { this.lang = lang; tutorialDone = true; hintsEnabled = false; eraIntrosEnabled = false }
        return Game(host, profile, Quality.LOW).apply {
            density = 2.7f
            resize(904, 2316, 904, 2316)
            setInsets(0, 88, 0, 0)
            if (scale != 1f) applySystemAccessibility(SystemA11y(scale, false, false))
        }
    }

    @Test
    fun explore() {
        val out = StringBuilder()
        for (scale in floatArrayOf(1f, 1.3f)) for (lang in L.CODES) {
            val g = game(lang, scale)
            val cx = CodexScreen(g, 1)
            g.setScreen(cx)
            val tiny = Canvas(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))
            repeat(3) { g.frame(tiny, 1f / 30f) }
            val dp = g.ui.dp
            val seen = HashSet<String>()
            for (page in intArrayOf(0, 1, 2, 3, 4, 10)) {
                cx.showCategory(page)
                repeat(14) {
                    val rec = Recorder(Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888))
                    g.frame(rec, 1f / 30f)
                    for (t in rec.texts) {
                        val sp = t.sizePx / dp / g.ui.textScale
                        if (sp < 9.95f && seen.add("$page ${t.s}"))
                            out.append("$lang ${(scale * 100).toInt()}% p$page '${t.s}' ${"%.1f".format(sp)} sp\n")
                    }
                    cx.onDrag(0f, -250f * dp)
                }
            }
        }
        assertTrue(out.toString(), out.isEmpty())
    }
}
