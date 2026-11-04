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
        /** [r] w układzie rysowania, [sr] na ekranie (po obrocie/przesunięciu płótna, dzień 41). */
        class Drawn(val s: String, val r: RectF, val sizePx: Float, val sr: RectF = r, val color: Int = 0)
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
                val r = RectF(left, y + fm.ascent * 0.8f, left + w, y + fm.descent * 0.6f)
                @Suppress("DEPRECATION") val m = matrix
                val sr = RectF(r).also { m.mapRect(it) }
                texts.add(Drawn(text, r, paint.textSize, sr, paint.color))
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

    private fun sp(g: Game, t: Recorder.Drawn) = t.sizePx / g.ui.dp / g.ui.textScale

    /**
     * Dzień 36 (R12, poz. 8): „Wymaga: Doktryna elit” na karcie elity stoi w osobnych wierszach
     * (≥ 10 sp, cały tekst, bez nakładania), legenda porównania ma ≥ 10 sp (w razie potrzeby dwa
     * wiersze nad macierzą kontr), a zakładki listew Kodeksu przy tekście 130% nie maleją.
     */
    @Test
    fun eliteTagLegendAndRailsReadableOnFoldOuter() {
        val problems = StringBuilder()
        for (scale in floatArrayOf(1f, 1.3f)) for (lang in L.CODES) {
            val g = game(lang, scale)
            val cx = CodexScreen(g, 1)
            g.setScreen(cx)
            repeat(3) { g.frame(tiny, 1f / 30f) }
            val dp = g.ui.dp
            val tag = "$lang ${(scale * 100).toInt()}%"

            // Zakładki kategorii i epok (także przy 130%).
            val first = Recorder(Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888))
            g.frame(first, 1f / 30f)
            val rails = cx.buttons.filter { it.id in 100..299 }
            var railSeen = 0
            for (t in first.texts) if (rails.any { it.label == t.s && t.r.centerY() in it.r.top..it.r.bottom }) {
                railSeen++
                if (sp(g, t) < 11.5f - 0.05f) problems.append("$tag zakładka '${t.s}' ${"%.1f".format(sp(g, t))} sp\n")
            }
            if (railSeen < 2) problems.append("$tag: narysowano $railSeen zakładek\n")

            // Nagłówek epoki: nazwa, numer i plakietka awansu (≥ 10 sp, bez nakładania).
            val headerTexts = cx.eraHeaderTextsForTest()
            // Najwyższe wystąpienie każdego napisu poza listwami (nazwa epoki bywa też zakładką).
            val header = headerTexts.distinct().mapNotNull { s ->
                first.texts.filter { t ->
                    t.s == s && rails.none { it.label == t.s && t.r.centerY() in it.r.top..it.r.bottom }
                }.minByOrNull { it.r.top }
            }
            if (header.map { it.s }.toSet() != headerTexts.toSet()) problems.append("$tag: nagłówek epoki ${header.map { it.s }}\n")
            for ((i, t) in header.withIndex()) {
                if (sp(g, t) < 10f - 0.05f) problems.append("$tag nagłówek '${t.s}' ${"%.1f".format(sp(g, t))} sp\n")
                if (t.r.right > g.width - 10 * dp) problems.append("$tag nagłówek '${t.s}' poza ekranem\n")
                for (j in 0 until i) if (RectF.intersects(t.r, header[j].r))
                    problems.append("$tag nagłówek '${t.s}' nachodzi na '${header[j].s}'\n")
            }

            // Wymaganie elity na karcie elity (strona Jednostki).
            cx.showCategory(1)
            val full = L.f("cx_needs", L["r_elite"])
            val lines = cx.eliteTagLinesForTest()
            if (lines.isEmpty()) problems.append("$tag: wymaganie elity nie ma własnych wierszy\n")
            if (lines.joinToString(" ") != full) problems.append("$tag: wymaganie elity niepełne: $lines\n")
            val seen = HashSet<String>()
            // Tekst przewinięty pod listwy jest przycięty, ale płótno testowe i tak go zapisuje.
            val railBottom = rails.maxOf { it.r.bottom }
            repeat(14) {
                val rec = Recorder(Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888))
                g.frame(rec, 1f / 30f)
                for (t in rec.texts) if (t.s in lines && t.s !in seen && t.r.top > railBottom && t.r.bottom < g.height) {
                    seen.add(t.s)
                    if (sp(g, t) < 10f - 0.05f) problems.append("$tag elita '${t.s}' ${"%.1f".format(sp(g, t))} sp\n")
                    if (t.r.left < 0f || t.r.right > g.width - 10 * dp)
                        problems.append("$tag elita '${t.s}' poza kartą\n")
                    for (o in rec.texts) if (o !== t && o.s != t.s && RectF.intersects(o.r, t.r))
                        problems.append("$tag elita '${t.s}' nachodzi na '${o.s}'\n")
                }
                cx.onDrag(0f, -200f * dp)
            }
            if (seen.size != lines.toSet().size) problems.append("$tag: narysowano ${seen.size}/${lines.size} wierszy elity\n")

            // Legenda porównania jednostek.
            cx.showCategory(10)
            val legend = cx.comparePanelForTest.legendLinesForTest()
            if (legend.joinToString(" ") != L["cx134_legend"]) problems.append("$tag: legenda niepełna: $legend\n")
            val legendSeen = HashSet<String>()
            repeat(8) {
                val rec = Recorder(Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888))
                g.frame(rec, 1f / 30f)
                val heading = rec.texts.filter { it.s == L["cx134_counter"] || it.s == L["cx134_axis"] }
                for (t in rec.texts) if (t.s in legend) {
                    legendSeen.add(t.s)
                    if (sp(g, t) < 10f - 0.05f) problems.append("$tag legenda '${t.s}' ${"%.1f".format(sp(g, t))} sp\n")
                    if (t.r.left < 0f || t.r.right > g.width - 10 * dp) problems.append("$tag legenda '${t.s}' poza ekranem\n")
                    for (o in heading) if (RectF.intersects(o.r, t.r)) problems.append("$tag legenda nachodzi na '${o.s}'\n")
                    for (o in rec.texts) if (o !== t && o.s in legend && o.s != t.s && RectF.intersects(o.r, t.r))
                        problems.append("$tag wiersze legendy nachodzą na siebie\n")
                }
                if (legendSeen.size == legend.size) return@repeat
                cx.onDrag(0f, -120f * dp)
            }
            if (legendSeen.size != legend.size) problems.append("$tag: narysowano ${legendSeen.size}/${legend.size} wierszy legendy\n")
        }
        assertTrue(problems.toString(), problems.isEmpty())
    }

    /**
     * Dzień 36: zakładki Rekordów na zewnętrznym Foldzie mają podpis ≥ 10 sp w 10 językach przy
     * tekście 100% i 130% (siatka 2 × 2, gdy podpis pod ikoną byłby mniejszy) i nie nachodzą na
     * zakładki tablic rankingu. Telefon i rozłożony Fold zostają w jednym rzędzie.
     */
    @Test
    fun recordsTabsReadableOnFoldOuter() {
        val problems = StringBuilder()
        val keys = listOf("rankings", "stats", "replay_list", "achievements")
        for (scale in floatArrayOf(1f, 1.3f)) for (lang in L.CODES) {
            val g = game(lang, scale)
            val rs = RankingsScreen(g)
            g.setScreen(rs)
            repeat(3) { g.frame(tiny, 1f / 30f) }
            val tag = "$lang ${(scale * 100).toInt()}%"
            val rec = Recorder(Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888))
            g.frame(rec, 1f / 30f)
            val recTabs = rs.buttons.filter { it.id in 200..203 }
            val boardTabs = rs.buttons.filter { it.id in 0..4 }
            if (recTabs.size != 4) problems.append("$tag: ${recTabs.size} zakładek Rekordów\n")
            for ((i, b) in recTabs.withIndex()) {
                val name = L[keys[i]]
                if (b.label != name) { problems.append("$tag: zakładka '${b.label}' zamiast '$name'\n"); continue }
                val t = rec.texts.firstOrNull { it.s == name && RectF.intersects(it.r, b.r) }
                if (t == null) { problems.append("$tag: nie narysowano '$name'\n"); continue }
                if (sp(g, t) < 10f - 0.05f) problems.append("$tag '$name' ${"%.1f".format(sp(g, t))} sp\n")
                if (t.r.left < b.r.left - 0.5f || t.r.right > b.r.right + 0.5f) problems.append("$tag '$name' wychodzi poza zakładkę\n")
                for (o in recTabs) if (o !== b && RectF.intersects(o.r, b.r)) problems.append("$tag zakładki Rekordów nachodzą na siebie\n")
                for (o in boardTabs) if (o.visible && RectF.intersects(o.r, b.r))
                    problems.append("$tag '$name' nachodzi na zakładkę tablicy #${o.id}\n")
            }
        }
        for ((w, h, t) in listOf(Triple(1600, 720, 0), Triple(2184, 1968, 70))) {
            val g = game("de", 1.3f, w, h, t)
            val rs = RankingsScreen(g)
            g.setScreen(rs)
            repeat(3) { g.frame(tiny, 1f / 30f) }
            val tops = rs.buttons.filter { it.id in 200..203 }.map { it.r.top }.toSet()
            if (tops.size != 1) problems.append("$w×$h: zakładki Rekordów nie w jednym rzędzie\n")
        }
        assertTrue(problems.toString(), problems.isEmpty())
    }

    /**
     * Dzień 41 (d36 „Zostało”): nazwy ról w macierzy kontr na zewnętrznym Foldzie mają ≥ 10 sp
     * w 10 językach przy tekście 100% i 130% (nazwa roli wiersza nad komórkami, nagłówki kolumn
     * poziomo albo obrócone), mieszczą się na ekranie i nie nachodzą na inne napisy.
     */
    @Test
    fun counterRoleHeadersReadableOnFoldOuter() {
        val problems = StringBuilder()
        var rotatedSeen = 0
        var flatSeen = 0
        for (scale in floatArrayOf(1f, 1.3f)) for (lang in L.CODES) {
            val g = game(lang, scale)
            val cx = CodexScreen(g, 1)
            g.setScreen(cx)
            repeat(3) { g.frame(tiny, 1f / 30f) }
            cx.showCategory(10)
            val dp = g.ui.dp
            val tag = "$lang ${(scale * 100).toInt()}%"
            val panel = cx.comparePanelForTest
            if (!panel.counterNarrowForTest) problems.append("$tag: macierz kontr nie w układzie wąskim\n")
            if (panel.counterRotatedForTest) rotatedSeen++ else flatSeen++
            val roles = panel.roleNamesForTest()
            val visibleTop = panel.selectorTop + panel.controlHeight
            // Dla każdej roli: nagłówek kolumny (biały) i nazwa wiersza (złota).
            val seen = HashSet<String>()
            repeat(16) {
                val rec = Recorder(Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888))
                g.frame(rec, 1f / 30f)
                val onScreen = rec.texts.filter { it.sr.top > visibleTop && it.sr.bottom < g.height - g.insetB }
                // Napisy macierzy: nazwy ról pod podpisem osi, wewnątrz karty macierzy (karty
                // jednostek wyżej też pokazują nazwę roli).
                val matrixTop = panel.counterTopForTest + 52 * dp
                val matrixBottom = panel.counterTopForTest + panel.counterHeightForTest
                for (t in onScreen) if (t.s in roles && t.sr.top > matrixTop && t.sr.bottom < matrixBottom) {
                    val kind = if (t.color == com.centurywar.engine.Ui.GOLD) "wiersz" else "kolumna"
                    seen.add("${t.s}|$kind")
                    if (sp(g, t) < 10f - 0.05f) problems.append("$tag $kind '${t.s}' ${"%.1f".format(sp(g, t))} sp\n")
                    if (t.sr.left < 0f || t.sr.right > g.width - 8 * dp) problems.append("$tag $kind '${t.s}' poza ekranem\n")
                    for (o in onScreen) if (o !== t && RectF.intersects(o.sr, t.sr))
                        problems.append("$tag $kind '${t.s}' nachodzi na '${o.s}'\n")
                }
                cx.onDrag(0f, -150f * dp)
            }
            for (r in roles) for (kind in arrayOf("wiersz", "kolumna"))
                if ("$r|$kind" !in seen) problems.append("$tag: nie narysowano ($kind) '$r'\n")
        }
        // Oba warianty nagłówków są sprawdzone (np. pl 100% poziomo, es obrócone).
        if (rotatedSeen == 0 || flatSeen == 0) problems.append("obrócone: $rotatedSeen, poziome: $flatSeen\n")
        assertTrue(problems.toString(), problems.isEmpty())
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
            assertTrue("$w×$h macierz kontr", !cx.comparePanelForTest.counterNarrowForTest)
        }
    }
}
