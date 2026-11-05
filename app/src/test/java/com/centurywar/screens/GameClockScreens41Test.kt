package com.centurywar.screens

import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
import com.centurywar.game.Daily
import com.centurywar.game.Mode
import com.centurywar.meta.Profile
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D41 (d34 pkt 4): ekrany osiągnięć i trybów liczą dzień z zegara gry (`g.clock()`),
 * tak jak menu i misje, a nie z zegara systemowego.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GameClockScreens41Test {
    private val fixedDay = Daily.RELEASE_DAY + 10L
    private val fixedMillis = fixedDay * 86_400_000L + 3_600_000L

    private fun game(): Game {
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val file = File.createTempFile("clock41", ".json").apply { delete() }
        val g = Game(host, Profile(file).apply { tutorialDone = true }, Quality.LOW)
        g.density = 2f
        g.resize(1600, 720, 1600, 720)
        g.clock = { fixedMillis }
        return g
    }

    @Test fun achievementsUseGameClock() {
        assertNotEquals(fixedDay, Daily.dayNumber())
        val g = game()
        val screen = AchievementsScreen(g)
        g.setScreen(screen)
        assertEquals(fixedDay, screen.dayForTests)
        screen.update(0.016f)
        assertEquals(fixedDay, screen.dayForTests)
        g.clock = { fixedMillis + 86_400_000L }
        screen.update(0.016f)
        assertEquals(fixedDay + 1L, screen.dayForTests)
    }

    @Test fun modeScreenDailyUsesGameClock() {
        val g = game()
        val screen = ModeScreen(g, Mode.DAILY)
        g.setScreen(screen)
        assertEquals(fixedDay, screen.dayForTests)
        screen.update(0.016f)
        assertEquals(fixedDay, screen.dayForTests)
        // Start wyzwania dnia zapisuje próbę rankingową na dzień zegara gry.
        screen.onClick(screen.buttons.first { it.id == -2 })
        assertEquals(fixedDay, g.profile.lastRankedDaily)
        assertEquals(true, g.current is BattleScreen)
    }
}
