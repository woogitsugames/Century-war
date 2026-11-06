package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.game.Campaign
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class StagePanel192Test {
    private var previousLang = 0
    @Before fun rememberLanguage() { previousLang = L.lang }
    @After fun restoreLanguage() { L.lang = previousLang }

    private data class Drawn(val text: String, val rect: RectF)
    private class Recorder(bitmap: Bitmap) : Canvas(bitmap) {
        val texts = ArrayList<Drawn>()
        private var modal = false
        override fun drawColor(color: Int) {
            if (color == android.graphics.Color.argb(150, 0, 0, 0)) modal = true
            super.drawColor(color)
        }
        override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
            if (modal && paint.style == Paint.Style.FILL && text.isNotBlank()) {
                val width = paint.measureText(text)
                val left = when (paint.textAlign) {
                    Paint.Align.CENTER -> x - width / 2f
                    Paint.Align.RIGHT -> x - width
                    else -> x
                }
                val fm = paint.fontMetrics
                texts.add(Drawn(text, RectF(left, y + fm.ascent * .8f, left + width, y + fm.descent * .6f)))
            }
            super.drawText(text, x, y, paint)
        }
    }

    @Test fun difficultyAndModifiersHaveTenDpGapInAllLayouts() {
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val devices = listOf(Triple(1600, 720, 2f), Triple(2400, 1080, 2.7f),
            Triple(904, 2316, 2.7f), Triple(2184, 1968, 2.7f))
        val stage = Campaign.stages.first { it.modifiers.isNotEmpty() }
        val problems = StringBuilder()
        for ((w, h, density) in devices) for (scale in listOf(100, 130)) for (lang in L.CODES) {
            val f = File.createTempFile("stage192", ".json").apply { delete() }
            val p = Profile(f).apply {
                this.lang = lang; tutorialDone = true; hintsEnabled = false; eraIntrosEnabled = false
                textScale = scale; textScaleManual = true
            }
            val game = Game(host, p, Quality.LOW)
            game.density = density; game.resize(w, h, w, h)
            if (w == 904) game.setInsets(0, 88, 0, 0)
            if (w == 2184) game.setInsets(0, 70, 0, 0)
            game.applyLanguage(); game.applyAccessibility()
            val screen = CampaignScreen(game, focusStage = stage.index)
            game.setScreen(screen)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Recorder(bmp)
                screen.render(canvas)
                val tag = "$w x $h / $lang / $scale%"
                val modifier = canvas.texts.lastOrNull { it.text == L["modifiers"] + ":" }
                val difficulty = canvas.texts.lastOrNull { it.text == L["campaign126_difficulty"] }
                if (modifier == null || difficulty == null) {
                    problems.append("$tag: brak wierszy modyfikatorów lub trudności\n")
                } else {
                    val gap = (difficulty.rect.top - modifier.rect.bottom) / game.ui.dp
                    if (gap < 9.95f) problems.append("$tag: odstęp $gap dp (${modifier.rect}, ${difficulty.rect})\n")
                }
                val info = screen.javaClass.getDeclaredField("info").apply { isAccessible = true }.get(screen) as RectF
                if (info.height() < 352 * game.ui.dp) {
                    val names = stage.modifiers.joinToString(" · ") { L[it.key] }
                    if (canvas.texts.none { it.text == names }) problems.append("$tag: brak nazw modyfikatorów\n")
                }
                for (t in canvas.texts) {
                    // Tytuł wstęgi ma osobną oprawę; pozostałe teksty są wewnątrz panelu.
                    if (t.text == L.f("stage_n", stage.index + 1)) continue
                    if (t.rect.left < info.left - 1 || t.rect.right > info.right + 1 ||
                        t.rect.top < info.top - 1 || t.rect.bottom > info.bottom + 1)
                        problems.append("$tag: '${t.text}' poza panelem ${t.rect} / $info\n")
                }
            } finally { bmp.recycle() }
        }
        assertTrue(problems.toString(), problems.isEmpty())
    }
}
