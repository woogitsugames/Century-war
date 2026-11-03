package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.Bonuses
import com.centurywar.game.Daily
import com.centurywar.game.DailyEvent
import com.centurywar.game.EndlessCard
import com.centurywar.game.Mode
import com.centurywar.meta.Profile
import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Zrzuty nowości ze zlecenia 170: wybór nowych kart Endless i nowe wydarzenia dnia. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class Content170ScreenshotTest {
    private val out = File("build/screenshots").apply { mkdirs() }
    private val devices = linkedMapOf(
        "phone720" to floatArrayOf(1600f, 720f, 2f),
        "foldOuter" to floatArrayOf(904f, 2316f, 2.7f),
    )

    private fun game(w: Int, h: Int, density: Float, lang: String): Game {
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val f = File.createTempFile("profile170", ".json").apply { delete() }
        val profile = Profile(f).apply { this.lang = lang; tutorialDone = true; medals = 340 }
        return Game(host, profile, 1).also { it.density = density; it.resize(w, h, w, h) }
    }

    private fun shot(g: Game, name: String) {
        g.finishScreenTransition()
        val tiny = Canvas(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))
        repeat(2) { g.frame(tiny, 1f / 60f) }
        val bmp = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
        g.frame(Canvas(bmp), 1f / 60f)
        File(out, "$name.png").outputStream().use { check(bmp.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bmp.recycle()
    }

    private fun firstDay(event: DailyEvent): Long {
        var day = Daily.EVENTS_V2_DAY
        while (Daily.config(day, Bonuses()).dailyEvent != event) day++
        return day
    }

    @Test fun newCardsAndDailyEvents() {
        val old = L.lang
        try {
            val langs = System.getenv("CW_TEXT_LANGS")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?.toTypedArray() ?: arrayOf("pl", "de")
            for ((tag, d) in devices) for (lang in langs) {
                val g = game(d[0].toInt(), d[1].toInt(), d[2], lang)
                val es = BattleScreen(g, BattleConfig(Mode.ENDLESS, 2026L, 1f))
                g.setScreen(es)
                val f = BattleScreen::class.java.getDeclaredField("battle").apply { isAccessible = true }
                val battle = f.get(es) as Battle
                repeat(5) { battle.waveTimer = 0f; battle.step() }
                assertNotNull("Karty po piątej fali", battle.pendingCards)
                battle.pendingCards = arrayOf(EndlessCard.QUARTERMASTER, EndlessCard.MASTER_MASONS, EndlessCard.ECONOMY)
                shot(g, "content170_${tag}_${lang}_cards")
                for (event in arrayOf(DailyEvent.SIEGE_WORKS, DailyEvent.FORCED_MARCH, DailyEvent.SPOILS_OF_WAR)) {
                    val day = firstDay(event)
                    assertTrue(day >= Daily.EVENTS_V2_DAY)
                    g.setScreen(ModeScreen(g, Mode.DAILY, previewDailyDay = day))
                    shot(g, "content170_${tag}_${lang}_${event.name.lowercase()}")
                }
            }
        } finally {
            L.lang = old
        }
    }
}
