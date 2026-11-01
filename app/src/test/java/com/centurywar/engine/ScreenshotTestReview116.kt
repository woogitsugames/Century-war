package com.centurywar.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.Mode
import com.centurywar.meta.Profile
import com.centurywar.screens.BattleScreen
import com.centurywar.screens.CodexScreen
import com.centurywar.screens.SettingsScreen
import com.centurywar.screens.StatsScreen
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Zlecenie 116: rzeczywiste ekrany gry i arkusze najdłuższych tłumaczeń na 720p.
 * Języki są wybierane na podstawie szerokości tekstów nowych kluczy, nie na sztywno.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ScreenshotTestReview116 {
    private val output = File("build/screenshots/l10n116").apply { mkdirs() }

    private fun game(): Game {
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val file = File.createTempFile("l10n116", ".json").apply { delete() }
        val g = Game(host, Profile(file).apply {
            tutorialDone = true
            medals = 340
            hintsSeen = Profile.HINT_VALID_MASK
        }, Quality.LOW)
        g.density = 1.8f
        g.resize(1600, 720, 1600, 720)
        return g
    }

    private fun screenshot(g: Game, code: String, screen: String) {
        g.finishScreenTransition()
        val bitmap = Bitmap.createBitmap(1600, 720, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        repeat(3) { g.frame(canvas, 1f / 60f) }
        val image = File(output, "phone720_" + code + "_" + screen + ".png")
        image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        assertTrue("Pusty zrzut: " + image.name, image.length() > 1000L)
    }

    private fun widthPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 25f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private fun longestLabels(keys: List<String>, i: Int, paint: Paint): List<Pair<String, String>> {
        return keys.mapNotNull { key ->
            val translation = L.raw(key)?.getOrNull(i) ?: return@mapNotNull null
            val longest = translation.split('|').maxByOrNull { paint.measureText(it) }
                ?: return@mapNotNull null
            key to longest
        }.sortedByDescending { paint.measureText(it.second) }
    }

    private fun contactSheet(keys: List<String>, i: Int, paint: Paint) {
        val bitmap = Bitmap.createBitmap(1600, 720, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(19, 25, 35))

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 19f
            color = Color.rgb(139, 172, 207)
        }
        val textPaint = Paint(paint).apply {
            textSize = 22f
            color = Color.WHITE
        }
        val warningPaint = Paint(textPaint).apply { color = Color.rgb(255, 159, 108) }
        val headerPaint = Paint(textPaint).apply { textSize = 32f }
        canvas.drawText("L10N 116 / " + L.CODES[i] + " / 1600 x 720",
            24f, 43f, headerPaint)

        for ((index, item) in longestLabels(keys, i, paint).take(12).withIndex()) {
            val y = 86f + index * 49f
            val available = 1600f - 305f - 28f
            val valueWidth = textPaint.measureText(item.second)
            canvas.drawText(item.first, 24f, y, labelPaint)
            canvas.drawText(item.second, 305f, y,
                if (valueWidth > available) warningPaint else textPaint)
            if (valueWidth > available) {
                canvas.drawText("!", 1580f, y, warningPaint)
            }
        }
        val image = File(output, "phone720_" + L.CODES[i] + "_longest.png")
        image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        assertTrue("Pusty arkusz: " + image.name, image.length() > 1000L)
    }

    @Test
    fun phone720ThreeLongestLanguages() {
        val keys = Review116Scope.keys()
        assumeTrue("Brak tekstów z tury 92–115; zrzuty po ich dołączeniu",
            keys.isNotEmpty())

        val old = L.lang
        try {
            val paint = widthPaint()
            // Ten sam zestaw kluczy dla każdego języka: długi przekład nie
            // może wypaść z pomiaru tylko dlatego, że przekracza limit długości.
            val labelKeys = keys.filter { key ->
                val english = L.raw(key)?.getOrNull(L.EN) ?: return@filter false
                english.length <= 48 && '|' !in english && '\n' !in english
            }.ifEmpty { keys }
            val ranking = L.CODES.indices.map { i ->
                val texts = longestLabels(labelKeys, i, paint)
                    .map { it.second }
                    .take(12)
                val score = texts.sumOf { paint.measureText(it).toDouble() }
                i to score
            }.sortedWith(compareByDescending<Pair<Int, Double>> { it.second }
                .thenBy { it.first })

            val widthReport = buildString {
                appendLine("jezyk\tsuma_szerokosci_12_etykiet_px")
                for ((i, width) in ranking) {
                    appendLine(L.CODES[i] + "\t" + width.toInt())
                }
            }
            File(output, "widths.tsv").writeText(widthReport)

            for ((i, _) in ranking.take(3)) {
                val code = L.CODES[i]
                L.setCode(code)
                contactSheet(keys, i, paint)

                val g = game()
                g.profile.lang = code
                g.setScreen(SettingsScreen(g))
                screenshot(g, code, "settings")
                g.setScreen(StatsScreen(g))
                screenshot(g, code, "stats")
                val codex = CodexScreen(g, 5)
                g.setScreen(codex)
                codex.showCategory(8)
                screenshot(g, code, "codex_cards")

                val battleScreen = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 116L, 1f))
                g.setScreen(battleScreen)
                screenshot(g, code, "battle")
                val field = battleScreen.javaClass.getDeclaredField("battle")
                    .apply { isAccessible = true }
                val battle = field.get(battleScreen) as Battle
                battle.surrender()
                screenshot(g, code, "results")
            }
        } finally {
            L.lang = old
        }
    }
}
