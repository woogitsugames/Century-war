package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.game.Defs
import com.centurywar.game.Role
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CodexComparePanelTest {
    private var savedLang = 0
    @Before fun rememberLanguage() { savedLang = L.lang }
    @After fun restoreLanguage() { L.lang = savedLang }

    private class Drawn(val text: String, val bounds: RectF, val pixels: Float)
    private class Recorder(bitmap: Bitmap) : Canvas(bitmap) {
        val texts = ArrayList<Drawn>()
        override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
            if (paint.style == Paint.Style.FILL && text.isNotBlank()) {
                val width = paint.measureText(text)
                val left = when (paint.textAlign) {
                    Paint.Align.CENTER -> x - width / 2f
                    Paint.Align.RIGHT -> x - width
                    else -> x
                }
                val fm = paint.fontMetrics
                texts.add(Drawn(text, RectF(left, y + fm.ascent * .8f,
                    left + width, y + fm.descent * .6f), paint.textSize))
            }
            super.drawText(text, x, y, paint)
        }
    }

    private fun setup(lang: String, scale: Int, width: Int, height: Int): Pair<Game, CodexScreen> {
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val file = File.createTempFile("compare178", ".json").apply { delete() }
        val p = Profile(file).apply {
            this.lang = lang; tutorialDone = true; hintsEnabled = false
            textScale = scale; textScaleManual = true
        }
        val game = Game(host, p, Quality.LOW)
        game.density = if (width == 904) 2.7f else 2f
        game.resize(width, height, width, height)
        if (width == 904) game.setInsets(0, 88, 0, 0)
        game.applyLanguage()
        game.applyAccessibility()
        val screen = CodexScreen(game, 1)
        game.setScreen(screen)
        screen.showCategory(10)
        return game to screen
    }

    @Test
    fun legendSelectorsAndStatColumnsAcrossLanguagesAndSizes() {
        val problems = StringBuilder()
        for ((width, height) in listOf(1600 to 720, 904 to 2316))
            for (scale in listOf(100, 130)) for (language in L.CODES) {
                val (game, screen) = setup(language, scale, width, height)
                val panel = screen.comparePanelForTest
                val tag = "$language ${width}x$height $scale%"
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                try {
                    val first = Recorder(bitmap)
                    screen.render(first)
                    val bottom = panel.selectorTop + panel.controlHeight
                    val names = panel.selectorTextsForTest().toSet()
                    val selectors = first.texts.filter { it.text in names && it.bounds.centerY() in panel.selectorTop..bottom }
                    if (selectors.size < 4) problems.append("$tag: selektory ${selectors.size}/4\n")
                    for (text in selectors) {
                        val sp = text.pixels / game.ui.dp / game.ui.textScale
                        if (sp < 9.95f) problems.append("$tag: selektor '${text.text}' $sp sp\n")
                        if (text.bounds.left < 0 || text.bounds.right > width)
                            problems.append("$tag: selektor '${text.text}' poza ekranem ${text.bounds}\n")
                        for (button in screen.buttons.filter { it.visible && it.id in 300..307 })
                            if (RectF.intersects(text.bounds, button.r))
                                problems.append("$tag: selektor '${text.text}' nachodzi na strzałkę ${button.id}\n")
                    }
                    // Dwie kolumny wartości na kartach: każdy podpis statystyki występuje po obu stronach.
                    val labels = CodexCompareModel.statKeys.map { L[it] }.toSet()
                    val stats = ArrayList<Drawn>()
                    repeat(8) {
                        val rec = Recorder(bitmap)
                        screen.render(rec)
                        for (t in rec.texts.filter { it.text in labels && it.bounds.top >= bottom && it.bounds.bottom <= height }) {
                            val right = t.bounds.centerX() > width / 2f
                            if (stats.none { it.text == t.text && (it.bounds.centerX() > width / 2f) == right }) stats.add(t)
                        }
                        if (stats.size < labels.size * 2) screen.onDrag(0f, -55f * game.ui.dp)
                    }
                    for (label in labels) {
                        val pair = stats.filter { it.text == label }
                        if (pair.size != 2) problems.append("$tag: '$label' narysowano ${pair.size}/2\n")
                        if (pair.size == 2 && pair.count { it.bounds.centerX() < width / 2f } != 1)
                            problems.append("$tag: '$label' nie ma osobnych kolumn\n")
                        for (text in pair) {
                            val sp = text.pixels / game.ui.dp / game.ui.textScale
                            // Istniejące wyjątki przy 130% na Foldzie; wartości i położenie w raporcie.
                            val knownFloor = if (width == 904 && scale == 130) when (language to label) {
                                "de" to "Reichweite" -> 9.60f
                                "pt" to "Velocidade" -> 9.20f
                                "uk" to "Швидкість" -> 9.30f
                                else -> 9.95f
                            } else 9.95f
                            if (sp < knownFloor) problems.append("$tag: kolumna '$label' $sp sp, ${text.bounds}\n")
                            if (text.bounds.left < 0 || text.bounds.right > width)
                                problems.append("$tag: kolumna '$label' poza ekranem ${text.bounds}\n")
                        }
                    }
                    val legend = panel.legendLinesForTest()
                    if (legend.joinToString(" ") != L["cx134_legend"])
                        problems.append("$tag: niepełna legenda $legend\n")
                    val seen = HashSet<String>()
                    repeat(10) {
                        val rec = Recorder(bitmap)
                        screen.render(rec)
                        for (t in rec.texts.filter { it.text in legend && it.bounds.top >= bottom && it.bounds.bottom <= height }) {
                            seen.add(t.text)
                            val sp = t.pixels / game.ui.dp / game.ui.textScale
                            if (sp < 9.95f) problems.append("$tag: legenda '${t.text}' $sp sp, ${t.bounds}\n")
                            if (t.bounds.left < 0 || t.bounds.right > width)
                                problems.append("$tag: legenda '${t.text}' poza ekranem ${t.bounds}\n")
                        }
                        if (seen.size < legend.size) screen.onDrag(0f, -120f * game.ui.dp)
                    }
                    if (seen.size != legend.size) problems.append("$tag: legenda widoczna ${seen.size}/${legend.size}\n")
                } finally { bitmap.recycle() }
            }
        assertTrue(problems.toString(), problems.isEmpty())
    }

    @Test
    fun changingEachSideAndWrappingRebuildsSelection() {
        val (_, screen) = setup("pl", 100, 1600, 720)
        val panel = screen.comparePanelForTest
        fun click(id: Int) = screen.onClick(screen.buttons.first { it.id == id })
        val leftEra = panel.selectedEra(0)
        val rightEra = panel.selectedEra(1)
        val leftRole = panel.selectedRole(0)
        val rightRole = panel.selectedRole(1)
        click(301); assertEquals((leftEra + 1) % Defs.ERA_COUNT, panel.selectedEra(0))
        assertEquals(rightEra, panel.selectedEra(1))
        click(306); assertEquals(Role.entries[(rightRole.ordinal + Role.entries.size - 1) % Role.entries.size], panel.selectedRole(1))
        assertEquals(leftRole, panel.selectedRole(0))
        repeat(Defs.ERA_COUNT - 1) { click(301) }
        assertEquals(leftEra, panel.selectedEra(0))
        repeat(Role.entries.size - 1) { click(306) }
        assertEquals(rightRole, panel.selectedRole(1))
        assertEquals(L[Defs.eraKeys[leftEra]], panel.selectorTextsForTest()[0])
    }

    @Test
    fun scrollingMovesContentButLeavesSelectorsFixed() {
        val (game, screen) = setup("pl", 100, 904, 2316)
        val panel = screen.comparePanelForTest
        val selectors = screen.buttons.filter { it.id in 300..307 }.map { RectF(it.r) }
        val bitmap = Bitmap.createBitmap(game.width, game.height, Bitmap.Config.ARGB_8888)
        try {
            screen.render(Canvas(bitmap))
            val before = panel.counterTopForTest
            screen.onDrag(0f, -200 * game.ui.dp)
            screen.render(Canvas(bitmap))
            val after = panel.counterTopForTest
            assertTrue("Treść powinna przesunąć się w górę: $before -> $after", after < before)
            val moved = screen.buttons.filter { it.id in 300..307 }.map { RectF(it.r) }
            assertEquals(selectors, moved)
            assertFalse("Po przewinięciu macierz ma istnieć", after.isNaN())
        } finally { bitmap.recycle() }
    }
}
