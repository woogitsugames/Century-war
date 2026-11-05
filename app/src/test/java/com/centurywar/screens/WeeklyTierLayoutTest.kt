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
 * D46: wiersz progów medali w wyzwaniu tygodnia („Gold: 30 Wellen (+120)”) wychodził na 720p
 * przy de 130% w prawo poza lamówkę. Teraz każdy wiersz progu (z konturem i cieniem) mieści się
 * w wewnętrznej lamówce, ma ≥ 11 sp, nie nachodzi na sąsiedni próg, inne napisy karty ani Graj —
 * 10 języków × tekst 100/130% × phone720/foldOpen/foldOuter.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class WeeklyTierLayoutTest {
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

    private fun game(d: Device, lang: String, scale: Float): Game {
        val f = File.createTempFile("d46tier", ".json").apply { delete() }
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
            clock = { 1793461200000L } // 2026-10-31 UTC: dopisek „od premiery” podnosi Graj
            preReleaseNotesEnabled = true
        }
    }

    @Test fun tierRowsInsideInnerTrimAtLeast11sp() {
        val problems = StringBuilder()
        val tiny = Canvas(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))
        val shots = File("build/screenshots").apply { mkdirs() }
        for (d in devices) for (scale in floatArrayOf(1f, 1.3f)) for (lang in L.CODES) {
            val g = game(d, lang, scale)
            val ms = ModeScreen(g, Mode.DAILY, true)
            g.setScreen(ms)
            g.finishScreenTransition()
            repeat(3) { g.frame(tiny, 1f / 30f) }
            val bmp = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
            val rec = Recorder(bmp)
            g.frame(rec, 1f / 30f)
            val pct = (scale * 100).toInt()
            val tag = "${d.name} $lang $pct%"
            if (lang == "de") File(shots, "d46_${d.name}_de${pct}_wyzwanie_tygodnia.png").outputStream()
                .use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val lines = ms.tierLinesForTests
            val tiers = rec.texts.filter { it.s in lines }
            if (tiers.size != lines.size) {
                problems.append("$tag: progi narysowane ${tiers.size} z ${lines.size}\n"); continue
            }
            val inner = g.ui.panelInner(ms.panelForTests, RectF())
            for (t in tiers) {
                val sp = t.sizePx / g.ui.dp / g.ui.textScale
                if (sp < 11f - 0.05f) problems.append("$tag: '${t.s}' ${"%.1f".format(sp)} sp\n")
                if (!inner.contains(t.r)) problems.append("$tag: '${t.s}' poza lamówką ${t.r} / $inner\n")
                if (RectF.intersects(t.r, ms.startForTests)) problems.append("$tag: '${t.s}' nachodzi na Graj\n")
                for (o in rec.texts) if (o !== t && RectF.intersects(t.r, o.r) &&
                    !(o.s in lines && o.r.left == t.r.left)) {
                    problems.append("$tag: '${t.s}' nachodzi na '${o.s}'\n")
                }
            }
            bmp.recycle()
        }
        assertTrue(problems.toString(), problems.isEmpty())
    }
}
