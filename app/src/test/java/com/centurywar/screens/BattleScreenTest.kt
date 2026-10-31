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
import com.centurywar.game.Modifier
import com.centurywar.game.AiPersonality
import com.centurywar.meta.OnlineLeaderboards
import com.centurywar.meta.OnlineState
import com.centurywar.meta.SkirmishSetup
import com.centurywar.game.Mode
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

    @Test
    fun restartKeepsEveryCustomSkirmishSetting() {
        val g = game()
        val setup = SkirmishSetup(2, 5, AiPersonality.SIEGER.ordinal, 185,
            (1 shl Modifier.FOG_OF_WAR.ordinal) or (1 shl Modifier.SLOW_XP.ordinal))
        val cfg = setup.toConfig(88L, g.profile.bonuses())
        val bs = BattleScreen(g, cfg)
        g.setScreen(bs)
        val restart = field(bs, "restartEnd").get(bs) as Button
        bs.onClick(restart)
        val next = g.current as BattleScreen
        val actual = field(next, "cfg").get(next) as BattleConfig
        assertEquals(89L, actual.seed)
        assertEquals(cfg.mode, actual.mode)
        assertEquals(cfg.aiPower, actual.aiPower, 0.0001f)
        assertEquals(cfg.startEra, actual.startEra)
        assertEquals(cfg.maxEra, actual.maxEra)
        assertEquals(cfg.modifiers, actual.modifiers)
        assertEquals(cfg.aiPersonality, actual.aiPersonality)
    }

    @Test
    fun customWinIsRecordedButCannotSubmitLocalOrOnlineRanking() {
        val g = game()
        val submissions = ArrayList<Pair<String, Long>>()
        g.online = object : OnlineLeaderboards {
            override val available = true
            override val state = OnlineState.CONNECTED
            override fun submit(board: String, score: Long) {
                submissions.add(board to score)
            }
            override fun showUi(board: String) {}
        }
        val cfg = SkirmishSetup(3, 3, AiPersonality.ASSAULT.ordinal, 60,
            1 shl Modifier.FOG_OF_WAR.ordinal).toConfig(22L, g.profile.bonuses())
        val bs = BattleScreen(g, cfg)
        g.setScreen(bs)
        val battle = field(bs, "battle").get(bs) as Battle
        battle.enemy.baseHp = 0f
        battle.step()
        assertTrue(battle.over)
        g.frame(canvas, 1f / 60f)
        assertTrue(g.profile.top(Profile.BOARD_SKIRMISH).isEmpty())
        assertTrue(submissions.isEmpty())
        assertEquals(1, g.profile.skirmishHistory.size)
        assertTrue(g.profile.skirmishHistory.first().won)
        assertTrue(!g.profile.skirmishHistory.first().ranked)
    }
}
