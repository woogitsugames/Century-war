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
import com.centurywar.game.AiPersonality
import com.centurywar.game.Campaign
import com.centurywar.meta.Profile
import com.centurywar.meta.SkirmishSetup
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
 * Dzień 68: „Wróg: <styl>” w oknie etapu kampanii i na ekranie potyczki oraz nazwy nowych
 * stylów na przyciskach — mieszczą się na telefonie 720p, zewnętrznym Foldzie (904×2316),
 * telefonie 1080p i rozłożonym Foldzie przy 100% i 130% tekstu we wszystkich językach.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class EnemyStyleD68Test {
    private var previousLang = 0
    @Before fun rememberLanguage() { previousLang = L.lang }
    @After fun restoreLanguage() { L.lang = previousLang }

    private class Drawn(val text: String, val rect: RectF, val size: Float)
    private class Recorder(bitmap: Bitmap, private val onlyModal: Boolean) : Canvas(bitmap) {
        val texts = ArrayList<Drawn>()
        private var modal = !onlyModal
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
                texts.add(Drawn(text, RectF(left, y + fm.ascent * .8f, left + width, y + fm.descent * .6f), paint.textSize))
            }
            super.drawText(text, x, y, paint)
        }
    }

    private val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
    }
    private val devices = listOf(Triple(1600, 720, 2f), Triple(2400, 1080, 2.7f),
        Triple(904, 2316, 2.7f), Triple(2184, 1968, 2.7f))

    private fun game(w: Int, h: Int, density: Float, lang: String, scale: Int,
                     setup: SkirmishSetup? = null): Game {
        val f = File.createTempFile("d68", ".json").apply { delete() }
        val p = Profile(f).apply {
            this.lang = lang; tutorialDone = true; hintsEnabled = false; eraIntrosEnabled = false
            textScale = scale; textScaleManual = true
            if (setup != null) skirmishSetup = setup
        }
        val game = Game(host, p, Quality.LOW)
        game.density = density; game.resize(w, h, w, h)
        if (w == 904) game.setInsets(0, 88, 0, 0)
        if (w == 2184) game.setInsets(0, 70, 0, 0)
        game.applyLanguage(); game.applyAccessibility()
        return game
    }

    @Test fun campaignPanelShowsEnemyStyleWithoutOverlap() {
        val styled = Campaign.stages.filter { it.personality != null }
        assertTrue("Brak etapów kampanii z nowym stylem", styled.isNotEmpty())
        for (s in styled) assertTrue("Styl na wczesnym etapie ${s.index + 1}", s.index >= 8)
        val problems = StringBuilder()
        for (stage in styled) for ((w, h, density) in devices) for (scale in listOf(100, 130)) for (lang in L.CODES) {
            val game = game(w, h, density, lang, scale)
            val screen = CampaignScreen(game, focusStage = stage.index)
            game.setScreen(screen)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Recorder(bmp, onlyModal = true)
                screen.render(canvas)
                val tag = "etap ${stage.index + 1} / $w x $h / $lang / $scale%"
                val dp = game.ui.dp
                val expected = L.f("d68_enemy", L["sk_person_" + stage.personality!!.ordinal])
                val info = screen.javaClass.getDeclaredField("info").apply { isAccessible = true }.get(screen) as RectF
                val enemy = canvas.texts.lastOrNull { it.text == expected }
                if (enemy == null) {
                    problems.append("$tag: brak „$expected”\n")
                } else {
                    if (enemy.size < 10f * dp) problems.append("$tag: za mały napis ${enemy.size / dp} dp\n")
                    if (enemy.rect.left < info.left || enemy.rect.right > info.right - 20 * dp)
                        problems.append("$tag: „$expected” poza panelem ${enemy.rect} / $info\n")
                    for (t in canvas.texts) {
                        if (t === enemy || t.text == L.f("stage_n", stage.index + 1)) continue
                        if (RectF.intersects(t.rect, enemy.rect))
                            problems.append("$tag: „$expected” nachodzi na „${t.text}”\n")
                    }
                }
                val modifier = canvas.texts.lastOrNull { it.text == L["modifiers"] + ":" }
                val difficulty = canvas.texts.lastOrNull { it.text == L["campaign126_difficulty"] }
                if (modifier == null || difficulty == null) {
                    problems.append("$tag: brak wierszy modyfikatorów lub trudności\n")
                } else {
                    val gap = (difficulty.rect.top - modifier.rect.bottom) / dp
                    if (gap < 9.95f) problems.append("$tag: odstęp $gap dp\n")
                }
                // Nazwy modyfikatorów nadal widoczne (chipy w wysokim panelu, wiersz w niskim).
                for (m in stage.modifiers) {
                    if (canvas.texts.none { it.text.contains(L[m.key]) })
                        problems.append("$tag: brak modyfikatora ${L[m.key]}\n")
                }
            } finally { bmp.recycle() }
        }
        assertTrue(problems.toString(), problems.isEmpty())
    }

    @Test fun skirmishSetupNamesFitButtonsAndShowEnemyLine() {
        val problems = StringBuilder()
        for ((w, h, density) in devices) for (scale in listOf(100, 130)) for (lang in L.CODES) {
            for (personality in listOf(-1) + AiPersonality.entries.filter { it !in AiPersonality.LEGACY }.map { it.ordinal }) {
                val game = game(w, h, density, lang, scale, SkirmishSetup(personality = personality))
                val screen = SkirmishSetupScreen(game)
                game.setScreen(screen)
                val tag = "$w x $h / $lang / $scale% / $personality"
                val dp = game.ui.dp
                val expected = L.f("d68_enemy",
                    if (personality < 0) L["sk_random"] else L["sk_person_$personality"])
                val buttons = screen.javaClass.getDeclaredField("personalities")
                    .apply { isAccessible = true }.get(screen) as Array<*>
                var seenEnemy = false
                // Przewijamy całą listę, żeby sprawdzić każdy przycisk i wiersz „Wróg: …”.
                var guard = 0
                while (guard++ < 40) {
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    try {
                        val canvas = Recorder(bmp, onlyModal = false)
                        screen.render(canvas)
                        val enemy = canvas.texts.firstOrNull { it.text == expected }
                        if (enemy != null) {
                            seenEnemy = true
                            if (enemy.size < 10f * dp) problems.append("$tag: mały „$expected” ${enemy.size / dp} dp\n")
                            for (t in canvas.texts) {
                                if (t !== enemy && RectF.intersects(t.rect, enemy.rect))
                                    problems.append("$tag: „$expected” nachodzi na „${t.text}”\n")
                            }
                        }
                        for (b in buttons) {
                            b as Button
                            if (!b.visible) continue
                            val label = canvas.texts.firstOrNull { it.text == b.label &&
                                it.rect.centerY() in b.r.top..b.r.bottom && it.rect.left >= b.r.left - 1 } ?: continue
                            if (label.rect.right > b.r.right + 1)
                                problems.append("$tag: „${b.label}” wychodzi poza przycisk\n")
                            if (label.size < 9f * dp) problems.append("$tag: mała nazwa „${b.label}” ${label.size / dp} dp\n")
                        }
                    } finally { bmp.recycle() }
                    val before = screen.javaClass.getDeclaredField("scroll").apply { isAccessible = true }.getFloat(screen)
                    screen.onDrag(0f, -200 * dp)
                    val after = screen.javaClass.getDeclaredField("scroll").apply { isAccessible = true }.getFloat(screen)
                    if (after == before) break
                }
                if (!seenEnemy) problems.append("$tag: brak „$expected”\n")
            }
        }
        assertTrue(problems.toString(), problems.isEmpty())
    }
}
