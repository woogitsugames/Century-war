package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
import com.centurywar.engine.TouchEvent
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.BattleController
import com.centurywar.game.BattleRenderer
import com.centurywar.game.Defs
import com.centurywar.game.Replay
import com.centurywar.game.Campaign
import com.centurywar.game.Mode
import com.centurywar.meta.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

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

    private fun tap(g: Game, x: Float, y: Float) {
        g.pushTouch(TouchEvent.DOWN, x, y, 0)
        g.pushTouch(TouchEvent.UP, x, y, 0)
        g.frame(canvas, 1f / 60f)
    }

    @Test
    fun tappingFieldAimsBarrageWithCameraCoordinates() {
        val g = game()
        val bs = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 332L, 1f))
        g.setScreen(bs)
        g.frame(canvas, 1f / 60f)
        val button = field(bs, "barrageBtn").get(bs) as Button
        val renderer = field(bs, "r").get(bs) as BattleRenderer
        val rect = field(bs, "fieldRect").get(bs) as RectF
        val battle = field(bs, "battle").get(bs) as Battle
        val controller = field(bs, "commands").get(bs) as BattleController
        tap(g, button.r.centerX(), button.r.centerY())
        assertTrue(field(bs, "aimingBarrage").getBoolean(bs))

        val x = rect.right * 0.69f
        val expected = renderer.worldX(x).roundToInt().coerceIn(0, Defs.LANE.toInt())
        tap(g, x, rect.centerY())
        assertEquals(1, battle.player.barragesUsed)
        assertEquals(Replay.Kind.BARRAGE_AT, controller.log().commands.last().kind)
        assertTrue(abs(controller.log().commands.last().argument - expected) <= 1)
        assertFalse(field(bs, "aimingBarrage").getBoolean(bs))
    }

    @Test
    fun twoButtonTapsUseClassicBarrage() {
        val g = game()
        val bs = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 333L, 1f))
        g.setScreen(bs)
        g.frame(canvas, 1f / 60f)
        val button = field(bs, "barrageBtn").get(bs) as Button
        tap(g, button.r.centerX(), button.r.centerY())
        tap(g, button.r.centerX(), button.r.centerY())
        val battle = field(bs, "battle").get(bs) as Battle
        val controller = field(bs, "commands").get(bs) as BattleController
        assertEquals(1, battle.player.barragesUsed)
        assertEquals(Replay.Kind.BARRAGE, controller.log().commands.last().kind)
        assertFalse(field(bs, "aimingBarrage").getBoolean(bs))
    }

    @Test
    fun draggingAndHudDoNotFireAimedBarrageAndBackCancelsIt() {
        val g = game()
        val bs = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 334L, 1f))
        g.setScreen(bs)
        g.frame(canvas, 1f / 60f)
        val button = field(bs, "barrageBtn").get(bs) as Button
        val rect = field(bs, "fieldRect").get(bs) as RectF
        val battle = field(bs, "battle").get(bs) as Battle
        val controller = field(bs, "commands").get(bs) as BattleController
        tap(g, button.r.centerX(), button.r.centerY())

        val x = rect.centerX()
        val y = rect.centerY()
        g.pushTouch(TouchEvent.DOWN, x, y, 0)
        g.pushTouch(TouchEvent.MOVE, x - 180f, y, 0)
        g.pushTouch(TouchEvent.UP, x - 180f, y, 0)
        g.frame(canvas, 1f / 60f)
        assertEquals(0, battle.player.barragesUsed)
        assertTrue(controller.log().commands.isEmpty())
        assertTrue(field(bs, "aimingBarrage").getBoolean(bs))

        // Dotknięcie paska górnego poza przyciskami tylko anuluje tryb.
        tap(g, g.width / 2f, 0f)
        assertFalse(field(bs, "aimingBarrage").getBoolean(bs))
        assertTrue(controller.log().commands.isEmpty())
        tap(g, button.r.centerX(), button.r.centerY())
        val speedButton = field(bs, "speedBtn").get(bs) as Button
        tap(g, speedButton.r.centerX(), speedButton.r.centerY())
        assertFalse(field(bs, "aimingBarrage").getBoolean(bs))
        assertTrue(controller.log().commands.isEmpty())
        tap(g, button.r.centerX(), button.r.centerY())
        g.back()
        assertFalse(field(bs, "aimingBarrage").getBoolean(bs))
        assertFalse(field(bs, "paused").getBoolean(bs))
        assertTrue(controller.log().commands.isEmpty())
    }

    @Test
    fun swipeFromHudIntoFieldNeverFires() {
        val g = game()
        val bs = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 335L, 1f))
        g.setScreen(bs)
        g.frame(canvas, 1f / 60f)
        val barrageButton = field(bs, "barrageBtn").get(bs) as Button
        tap(g, barrageButton.r.centerX(), barrageButton.r.centerY())
        val speedButton = field(bs, "speedBtn").get(bs) as Button
        val area = field(bs, "fieldRect").get(bs) as RectF
        g.pushTouch(TouchEvent.DOWN, speedButton.r.centerX(), speedButton.r.centerY(), 0)
        g.pushTouch(TouchEvent.MOVE, area.centerX(), area.centerY(), 0)
        g.pushTouch(TouchEvent.UP, area.centerX(), area.centerY(), 0)
        g.frame(canvas, 1f / 60f)
        assertEquals(0, (field(bs, "battle").get(bs) as Battle).player.barragesUsed)
        assertTrue((field(bs, "commands").get(bs) as BattleController).log().commands.isEmpty())
        assertFalse(field(bs, "aimingBarrage").getBoolean(bs))
    }

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
}
