package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import com.centurywar.engine.Game
import com.centurywar.engine.HapticKind
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
import com.centurywar.game.Mode
import com.centurywar.meta.Achievements
import com.centurywar.meta.Missions
import com.centurywar.meta.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.lang.reflect.Method

/** Nagrody, buforowanie opisu Endless i budżet alokacji obu ekranów. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RewardFeedback90Test {
    private class TestHost : Host {
        var lastHaptic: HapticKind? = null
        var pulses = 0
        override fun vibrate(ms: Long) {}
        override fun haptic(kind: HapticKind) { lastHaptic = kind; pulses++ }
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
    }

    private fun game(): Pair<Game, TestHost> {
        val host = TestHost()
        val profile = Profile(File.createTempFile("reward90-", ".json").apply { delete() }).apply {
            tutorialDone = true
            hintsEnabled = false
            vibrationLevel = 2
        }
        val g = Game(host, profile, Quality.LOW)
        g.density = 2f
        g.resize(1600, 720, 1600, 720)
        g.finishScreenTransition()
        return g to host
    }

    @Test fun missionClaimRewardsOnceAndCreatesFlight() {
        val (g, host) = game()
        val mission = Missions(g.profile).refresh(System.currentTimeMillis()).daily.first()
        mission.progress = mission.target
        val s = MissionsScreen(g)
        g.setScreen(s)
        val button = s.buttons.first { it.id == 100 }
        assertTrue(button.enabled)
        val before = g.profile.medals
        s.onClick(button)
        assertTrue(mission.claimed)
        assertEquals(before + mission.reward, g.profile.medals)
        assertTrue(g.rewardFeedback.activeCount > 0)
        assertEquals(HapticKind.EVOLVE, host.lastHaptic)
        val pulses = host.pulses
        s.onClick(button)
        assertEquals(before + mission.reward, g.profile.medals)
        assertEquals(pulses, host.pulses)
    }

    @Test fun achievementsAndLoginTriggerRewardFeedback() {
        val (g, host) = game()
        g.showAchievements(listOf(Achievements.all.first()))
        assertTrue(g.rewardFeedback.activeCount > 0)
        assertEquals(HapticKind.EVOLVE, host.lastHaptic)
        g.rewardFeedback.clear()
        val now = System.currentTimeMillis()
        val state = g.profile.missionState
        val yesterday = Missions.utcDay(now) - 1
        state.lastSeenDay = yesterday
        state.loginDay = yesterday
        state.loginClaimedDay = yesterday
        val medalsBefore = g.profile.medals
        val screen = MissionsScreen(g)
        g.setScreen(screen)
        assertTrue(g.profile.medals > medalsBefore)
        assertTrue(g.rewardFeedback.activeCount > 0)
    }

    @Test fun endlessLinesReusedBetweenFramesAndInvalidatedOnLayout() {
        val (g, _) = game()
        val screen = ModeScreen(g, Mode.ENDLESS)
        g.setScreen(screen)
        val initial = screen.cachedEndlessInfoLines
        assertTrue(initial.isNotEmpty())
        val c = Canvas(Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888))
        repeat(80) { screen.update(1f / 60f); screen.render(c) }
        assertSame(initial, screen.cachedEndlessInfoLines)
        screen.layout()
        assertNotSame(initial, screen.cachedEndlessInfoLines)
    }

    private class Meter(private val bean: Any, private val getter: Method) {
        fun bytes(): Long = getter.invoke(bean, Thread.currentThread().id) as Long
    }

    private fun meter(): Meter {
        // Android.jar nie ma com.sun.management: API JVM tylko przez refleksję.
        val bean = Class.forName("java.lang.management.ManagementFactory")
            .getMethod("getThreadMXBean").invoke(null)!!
        val api = runCatching { Class.forName("com.sun.management.ThreadMXBean") }.getOrNull()
        assumeTrue("Wymagany licznik alokacji JVM", api != null && api.isInstance(bean) &&
            api.getMethod("isThreadAllocatedMemorySupported").invoke(bean) == true)
        api!!.getMethod("setThreadAllocatedMemoryEnabled", java.lang.Boolean.TYPE).invoke(bean, true)
        return Meter(bean, api.getMethod("getThreadAllocatedBytes", java.lang.Long.TYPE))
    }

    private fun measure(label: String, frame: () -> Unit) {
        repeat(250) { frame() }
        val m = meter()
        val before = m.bytes()
        repeat(600) { frame() }
        val allocated = m.bytes() - before
        println("PERF90 $label: $allocated B / 600 klatek")
        assertTrue("$label: $allocated B / 600 klatek (limit < 16 KiB)", allocated < 16 * 1024)
    }

    @Test fun missionsUpdateAndRenderDoNotAllocatePerFrame() {
        val (g, _) = game()
        val s = MissionsScreen(g)
        g.setScreen(s)
        val c = Canvas(Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888))
        measure("MissionsScreen") { s.update(1f / 60f); s.render(c) }
    }

    @Test fun endlessUpdateAndRenderDoNotAllocatePerFrame() {
        val (g, _) = game()
        val s = ModeScreen(g, Mode.ENDLESS)
        g.setScreen(s)
        val c = Canvas(Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888))
        measure("ModeScreen Endless") { s.update(1f / 60f); s.render(c) }
    }

    @Test fun particlePoolDoesNotAllocateWhileFlying() {
        val (g, _) = game()
        val c = Canvas(Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888))
        val fx = g.rewardFeedback
        var frames = 0
        measure("RewardFeedback") {
            if (frames % 45 == 0) fx.launch(g, 420f, 420f, 30)
            fx.draw(c, g, 1f / 60f)
            frames++
        }
        assertTrue(fx.activeCount in 0..16)
    }

    @Test fun reduceShakeDisablesDecorativeFlight() {
        val (g, host) = game()
        g.profile.reduceShake = true
        g.celebrateReward(300f, 400f, 10)
        assertEquals(0, g.rewardFeedback.activeCount)
        assertEquals(HapticKind.EVOLVE, host.lastHaptic)
    }
}
