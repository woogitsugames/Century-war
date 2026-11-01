package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.BattleController
import com.centurywar.game.Mode
import com.centurywar.meta.Profile
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class BattleSummaryScreenshotTest {
    private val out = File("build/screenshots").apply { mkdirs() }

    private fun game(width: Int, height: Int, density: Float, quality: Int): Game {
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val path = File.createTempFile("summary-profile", ".json").apply { delete() }
        val game = Game(host, Profile(path).apply { tutorialDone = true }, quality)
        game.density = density
        game.resize(width, height, width, height)
        return game
    }

    private fun field(owner: Any, name: String): Any =
        owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)

    /** Zrzuty 720p i rozłożonego Folda wraz z kontrolą położenia panelu i zakładek. */
    @Test fun battleResultChartsPhoneAndFold() {
        val previousLanguage = L.lang
        try {
            L.lang = L.PL
            for ((name, dims) in mapOf(
                "phone720" to intArrayOf(1600, 720, 2),
                "foldOpen" to intArrayOf(2184, 1968, 3),
            )) {
                val density = dims[2] * 0.9f
                val g = game(dims[0], dims[1], density,
                    if (name == "phone720") Quality.LOW else Quality.HIGH)
                val screen = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 104L, 1f))
                g.setScreen(screen)
                val battle = field(screen, "battle") as Battle
                val samples = field(screen, "battleSummary") as BattleSummarySamples
                repeat(240) {
                    if (!battle.over) {
                        battle.step()
                        samples.capture(battle)
                    }
                }
                if (!battle.over) {
                    val commands = field(screen, "commands") as BattleController
                    assertTrue(commands.surrender())
                }
                val tiny = Canvas(Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888))
                repeat(70) { g.frame(tiny, 1f / 60f) }
                assertTrue("Wynik powinien być widoczny", samples.count >= 2)
                val chart = field(screen, "resultChartTab") as Button
                val stats = field(screen, "resultStatsTab") as Button
                assertTrue("Zakładka wykresu musi być dostępna", chart.visible)
                assertTrue("Zakładka wyniku musi być dostępna", stats.visible)
                val area = field(screen, "resultChartArea") as RectF
                val buttons = field(screen, "continueBtn") as Button
                assertTrue("Wykres nie może nakładać się na przyciski",
                    area.top > chart.r.bottom && area.bottom < buttons.r.top)
                assertTrue("Wykres musi zachować wysokość", area.height() >= 95f * g.ui.dp)
                g.finishScreenTransition()
                val bitmap = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                // Zakładka wyniku: treść pod zakładkami (73 dp) nie może gubić wierszy.
                g.frame(canvas, 1f / 60f)
                assertEquals("$name: pominięte wiersze wyniku", 0, field(screen, "resultRowsClipped"))
                File(out, "battle104_${name}_stats.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                screen.onClick(chart)
                g.frame(canvas, 1f / 60f)
                File(out, "battle104_${name}_chart.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
        } finally {
            L.lang = previousLanguage
        }
    }

    /** Powiadomienie (np. seria dni) czeka w kolejce i nie zasłania zakładek wyników. */
    @Test fun noticesWaitWhileBattleSummaryIsShown() {
        val g = game(1600, 720, 1.8f, Quality.LOW)
        val screen = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 104L, 1f))
        g.setScreen(screen)
        g.finishScreenTransition()
        val tiny = Canvas(Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888))
        g.frame(tiny, 1f / 60f)
        val commands = field(screen, "commands") as BattleController
        assertTrue(commands.surrender())
        repeat(70) { g.frame(tiny, 1f / 60f) }
        assertTrue("Ekran wyników musi być widoczny", (field(screen, "resultStatsTab") as Button).visible)
        val queue = field(g, "noticeQueue") as Collection<*>
        queue.size.let { before ->
            g.showNotice("Seria dni 3/7")
            repeat(240) { g.frame(tiny, 1f / 60f) }
            assertTrue("Podsumowanie bitwy blokuje powiadomienia", screen.blocksNotices)
            assertEquals("Powiadomienie nie może zostać pobrane z kolejki", before + 1, queue.size)
            assertTrue("Baner nie może być rysowany", (field(g, "achievementSeconds") as Float) <= 0f)
        }
        val menu = MenuScreen(g)
        g.setScreen(menu)
        g.finishScreenTransition()
        repeat(3) { g.frame(tiny, 1f / 60f) }
        assertTrue("Po wyjściu z wyników baner się pokazuje", (field(g, "achievementSeconds") as Float) > 0f)
    }

    /** Najdłuższa lista wyników (tygodniowy Endless z nagrodą) na 720p: nic nie znika. */
    @Test fun weeklyEndlessResultRowsFitPhone720() {
        val g = game(1600, 720, 1.8f, Quality.LOW)
        com.centurywar.meta.Achievements.all.forEach { g.profile.unlockedAchievements.add(it.id) }
        val week = com.centurywar.game.Weekly.weekNumber(com.centurywar.game.Daily.dayNumber())
        val screen = BattleScreen(g, com.centurywar.game.Weekly.config(week))
        g.setScreen(screen)
        g.finishScreenTransition()
        val tiny = Canvas(Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888))
        g.frame(tiny, 1f / 60f)
        val battle = field(screen, "battle") as Battle
        battle.wave = 20
        battle.surrender()
        repeat(70) { g.frame(tiny, 1f / 60f) }
        val bitmap = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
        g.frame(Canvas(bitmap), 1f / 60f)
        assertEquals("pominięte wiersze wyniku", 0, field(screen, "resultRowsClipped"))
        File(out, "battle104_phone720_weekly_stats.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
