package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.Campaign
import com.centurywar.game.Mode
import com.centurywar.game.Weekly
import com.centurywar.game.Daily
import com.centurywar.meta.Achievements
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
class BattleScreenTest {

    private fun game(): Game {
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val f = File.createTempFile("profile", ".json").apply { delete() }
        val g = Game(host, Profile(f).apply { tutorialDone = true }, Quality.LOW)
        g.density = 2f
        g.resize(1600, 720, 1600, 720)
        return g
    }

    private fun field(o: Any, name: String) = o.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private val canvas = Canvas(Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888))

    @Test
    fun everyBattleSpeedCanBeSelected() {
        val g = game()
        val bs = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 3L, 1f))
        g.setScreen(bs)
        val speedBtn = field(bs, "speedBtn").get(bs) as Button
        val seen = HashSet<String>()
        repeat(4) {
            bs.onClick(speedBtn)
            g.frame(canvas, 1f / 60f) // wcześniej ×5 rzucało ArrayIndexOutOfBounds w każdej klatce
            seen.add(speedBtn.label)
        }
        assertEquals(setOf("×1", "×3", "×5"), seen)
    }

    @Test
    fun weeklyResultsNeverAlterEndlessRecordsAndClaimsAreOnceOnly() {
        val g = game()
        val p = g.profile
        p.bestWave = 9
        p.submit(Profile.BOARD_ENDLESS, 30000L, 1L)
        Achievements.all.forEach { p.unlockedAchievements.add(it.id) }
        val week = Weekly.weekNumber(Daily.dayNumber())

        fun finish(waves: Int) {
            val bs = BattleScreen(g, Weekly.config(week))
            g.setScreen(bs)
            g.frame(canvas, 1f / 60f)
            val battle = field(bs, "battle").get(bs) as Battle
            battle.wave = waves
            battle.surrender()
            g.frame(canvas, 1f / 60f)
        }

        finish(20)
        assertEquals(9, p.bestWave)
        assertEquals(30000L, p.top(Profile.BOARD_ENDLESS).first().score)
        assertEquals(3, p.weeklyClaimedFor(week))
        assertEquals(1, p.top(Profile.weeklyBoard(week)).size)
        val first = p.medals
        finish(20)
        assertEquals(first + 30, p.medals)
        assertEquals(3, p.weeklyClaimedFor(week))
        assertEquals(9, p.bestWave)
        assertEquals(30000L, p.top(Profile.BOARD_ENDLESS).first().score)
    }

    @Test
    fun instantSurrenderDoesNotFarmMedals() {
        val g = game()
        val stage = Campaign.stages[12]
        val bs = BattleScreen(g, Campaign.config(stage, g.profile.bonuses(), 1L))
        g.setScreen(bs)
        g.frame(canvas, 1f / 60f)
        val before = g.profile.medals
        (field(bs, "battle").get(bs) as Battle).surrender()
        g.frame(canvas, 1f / 60f)
        assertTrue("natychmiastowa kapitulacja nie powinna dawać medali", g.profile.medals - before == 0)
    }

    @Test
    fun practiceDailySurrenderDoesNotFarmMedals() {
        val g = game()
        // Jednorazowe osiągnięcia (np. pierwsza bitwa) to nie farmienie: wyłącz je z porównania.
        com.centurywar.meta.Achievements.all.forEach { g.profile.unlockedAchievements.add(it.id) }
        val bs = BattleScreen(g, com.centurywar.game.Daily.config(20000L, g.profile.bonuses()), rankedDaily = false)
        g.setScreen(bs)
        g.frame(canvas, 1f / 60f)
        val before = g.profile.medals
        (field(bs, "battle").get(bs) as Battle).surrender()
        g.frame(canvas, 1f / 60f)
        assertEquals(before, g.profile.medals)
    }
}
