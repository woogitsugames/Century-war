package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.BattleController
import com.centurywar.game.BattleRenderer
import com.centurywar.game.Bonuses
import com.centurywar.game.Mode
import com.centurywar.game.ReplayPlayer
import com.centurywar.game.ReplayRecord
import com.centurywar.meta.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ReplayScreenTest {
    private fun recording(): ByteArray {
        val cfg = BattleConfig(Mode.SKIRMISH, 901L, 0.5f,
            playerBonuses = Bonuses(baseHpMul = 50f))
        val battle = Battle(cfg)
        val commands = BattleController(battle)
        repeat(180) { if (!battle.over) battle.step() }
        if (!battle.over) commands.surrender()
        assertTrue("Powtórka musi mieć klatki do odtworzenia", commands.log().steps >= 120)
        return ReplayRecord.encode(cfg, commands.log(), ReplayRecord.Meta(
            cfg.mode, -1, battle.winner, battle.score(), battle.time, battle.wave,
            1_793_000_000_000L, "Tester",
        ))
    }

    private fun screen(): Pair<ReplayScreen, Canvas> {
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val profile = Profile(File.createTempFile("replay-profile", ".json").apply { delete() })
        val game = Game(host, profile, Quality.LOW)
        game.density = 1f
        game.resize(600, 320, 600, 320)
        val screen = ReplayScreen(game, recording())
        game.setScreen(screen)
        return screen to Canvas(Bitmap.createBitmap(600, 320, Bitmap.Config.ARGB_8888))
    }

    private fun field(obj: Any, name: String): Any =
        obj.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(obj)

    @Test fun interpolatesFramesWithoutSimulationStepAndFreezesDuringPauseAndSeek() {
        val (screen, canvas) = screen()
        val renderer = field(screen, "renderer") as BattleRenderer
        val player = field(screen, "player") as ReplayPlayer
        val startStep = player.step

        screen.update(Battle.STEP * 0.25f)
        screen.render(canvas)
        val first = renderer.lerp
        screen.update(Battle.STEP * 0.25f)
        screen.render(canvas)
        val second = renderer.lerp
        assertEquals(startStep, player.step)
        assertTrue("Interpolacja musi wzrastać pomiędzy klatkami", first > 0f && second > first)
        assertTrue(second < 1f)

        screen.onClick(screen.buttons.first { it.id == 2 })
        screen.render(canvas)
        assertEquals(1f, renderer.lerp, 0.0001f)

        assertEquals("−", screen.buttons.first { it.id == 3 }.label)
        assertEquals("+", screen.buttons.first { it.id == 4 }.label)
        screen.onClick(screen.buttons.first { it.id == 4 })
        assertEquals("×3", field(screen, "speedText"))
        screen.onClick(screen.buttons.first { it.id == 6 })
        screen.render(canvas)
        assertEquals(1f, renderer.lerp, 0.0001f)
    }

    @Test fun statusTextRefreshIsLimitedToFourTimesPerSecond() {
        val (screen, _) = screen()
        screen.onClick(screen.buttons.first { it.id == 2 })
        screen.update(0f)
        val initial = field(screen, "statusText") as String
        val player = field(screen, "player") as ReplayPlayer
        player.battle.player.gold += 10f
        screen.update(0.1f)
        assertEquals(initial, field(screen, "statusText"))
        screen.update(0.15f)
        assertTrue(initial != field(screen, "statusText"))
    }

    @Test fun updateAndRenderDoNotAllocateWithUnchangedReplayState() {
        // android.jar nie eksportuje com.sun.management; identyczny pomiar jak w AllocationTest.
        val mx = Class.forName("java.lang.management.ManagementFactory")
            .getMethod("getThreadMXBean").invoke(null)!!
        val api = runCatching { Class.forName("com.sun.management.ThreadMXBean") }.getOrNull()
        assumeTrue("Pomiar wymaga ThreadMXBean", api != null && api.isInstance(mx) &&
            api.getMethod("isThreadAllocatedMemorySupported").invoke(mx) == true)
        api!!.getMethod("setThreadAllocatedMemoryEnabled", java.lang.Boolean.TYPE).invoke(mx, true)
        val allocated = api.getMethod("getThreadAllocatedBytes", java.lang.Long.TYPE)

        val (screen, canvas) = screen()
        screen.onClick(screen.buttons.first { it.id == 2 })
        repeat(350) { screen.update(0f); screen.render(canvas) }
        val threadId = Thread.currentThread().id
        val before = allocated.invoke(mx, threadId) as Long
        repeat(600) { screen.update(0f); screen.render(canvas) }
        val bytes = (allocated.invoke(mx, threadId) as Long) - before
        assertTrue("Alokacje ReplayScreen.update/render: $bytes B / 600 klatek",
            bytes in 0L..8192L)
    }
}
