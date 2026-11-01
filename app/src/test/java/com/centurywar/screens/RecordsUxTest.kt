package com.centurywar.screens

import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.engine.Screen
import com.centurywar.meta.MissionItem
import com.centurywar.meta.MissionType
import com.centurywar.meta.Missions
import com.centurywar.meta.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RecordsUxTest {
    private fun game(width: Int, height: Int, density: Float): Game {
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val file = File.createTempFile("records", ".json").apply { delete() }
        val game = Game(host, Profile(file).apply { tutorialDone = true }, Quality.LOW)
        game.density = density
        game.resize(width, height, width, height)
        return game
    }

    @Test
    fun menuShowsUnclaimedMissionCountAndRecordsEntry() {
        val g = game(1600, 720, 1.8f)
        val now = System.currentTimeMillis()
        val state = g.profile.missionState
        state.dailyDay = Missions.utcDay(now)
        state.weeklyWeek = Missions.utcWeek(now)
        state.lastSeenDay = state.dailyDay
        state.daily.clear()
        state.weekly.clear()
        state.daily.add(MissionItem("d_test", MissionType.BATTLES, 1, 10, progress = 1))
        val menu = MenuScreen(g)
        g.setScreen(menu)
        assertEquals(1, menu.claimableMissions)
        assertEquals(L["records"], menu.buttons.first { it.id == 6 }.label)
        assertEquals(com.centurywar.engine.Icons.MISSIONS, menu.buttons.first { it.id == 9 }.iconId)
        state.daily[0].claimed = true
        menu.layout()
        assertEquals(0, menu.claimableMissions)
    }

    @Test
    fun fourRecordsTabsUseOneBackStackOnFourLayouts() {
        for (dims in arrayOf(
            intArrayOf(1600, 720, 2),
            intArrayOf(2400, 1080, 3),
            intArrayOf(2184, 1968, 3),
            intArrayOf(1968, 2184, 3),
        )) {
            val g = game(dims[0], dims[1], dims[2] * 0.9f)
            val menu = MenuScreen(g)
            g.setScreen(menu)
            menu.onClick(menu.buttons.first { it.id == 6 })
            assertTrue(g.current is RankingsScreen)
            for ((target, klass) in listOf(
                201 to StatsScreen::class.java,
                202 to ReplayListScreen::class.java,
                203 to AchievementsScreen::class.java,
                200 to RankingsScreen::class.java,
            )) {
                val s = g.current as Screen
                val buttons = s.buttons.filter { it.id in 200..203 }
                assertEquals(4, buttons.size)
                for (b in buttons) {
                    assertTrue("Zakładka ma 48 dp", b.r.height() + 0.1f >= 48 * g.ui.dp)
                    assertTrue("Zakładka w granicach ekranu", b.r.left >= g.insetL - 1f &&
                        b.r.right <= g.width - g.insetR + 1f)
                }
                s.onClick(buttons.first { it.id == target })
                assertTrue("Wybrana zakładka", klass.isInstance(g.current))
            }
            g.back()
            assertTrue("Wstecz wraca do menu po zmianach zakładek", g.current is MenuScreen)
        }
    }
}
