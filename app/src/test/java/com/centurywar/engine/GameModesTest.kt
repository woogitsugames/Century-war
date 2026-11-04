package com.centurywar.engine

import com.centurywar.meta.Profile
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** R10-04: tryb baterii Androida = 30 FPS i jakość ≤ średnia; po wyjściu wraca wybór gracza. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GameModesTest {
    private class FpsHost : Host {
        var fps = -1
        var scales = 0
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) { scales++ }
        override fun applyFrameRate(fps: Int) { this.fps = fps }
        override fun exit() {}
    }

    @Test fun effectiveFpsPureFunction() {
        assertEquals(0, GameModes.effectiveFps(0, GameModes.STANDARD))
        assertEquals(60, GameModes.effectiveFps(60, GameModes.PERFORMANCE))
        assertEquals(60, GameModes.effectiveFps(60, GameModes.UNSUPPORTED))
        assertEquals(30, GameModes.effectiveFps(0, GameModes.BATTERY))
        assertEquals(30, GameModes.effectiveFps(60, GameModes.BATTERY))
        assertEquals(30, GameModes.effectiveFps(30, GameModes.BATTERY))
        assertEquals(0, GameModes.effectiveFps(0, GameModes.CUSTOM))
    }

    @Test fun effectiveQualityPureFunction() {
        assertEquals(Quality.HIGH, GameModes.effectiveQuality(Quality.HIGH, GameModes.STANDARD))
        assertEquals(Quality.HIGH, GameModes.effectiveQuality(Quality.HIGH, GameModes.PERFORMANCE))
        assertEquals(Quality.MED, GameModes.effectiveQuality(Quality.HIGH, GameModes.BATTERY))
        assertEquals(Quality.MED, GameModes.effectiveQuality(Quality.MED, GameModes.BATTERY))
        assertEquals(Quality.LOW, GameModes.effectiveQuality(Quality.LOW, GameModes.BATTERY))
    }

    @Test fun reportNames() {
        assertEquals("standard", GameModes.reportName(GameModes.STANDARD))
        assertEquals("battery", GameModes.reportName(GameModes.BATTERY))
        assertEquals("performance", GameModes.reportName(GameModes.PERFORMANCE))
        assertEquals("unsupported", GameModes.reportName(GameModes.UNSUPPORTED))
        assertEquals("standard", GameModes.reportName(GameModes.CUSTOM))
    }

    @Test fun batteryModeLimitsGameAndRestoresPlayerChoice() {
        val host = FpsHost()
        val dir = Files.createTempDirectory("cw-r1004").toFile()
        val profile = Profile(File(dir, "profile.json")).apply { fpsCap = 60; quality = Quality.HIGH }
        val g = Game(host, profile, Quality.HIGH).apply {
            density = 2f
            resize(1600, 720, 1600, 720)
        }
        g.setQuality(g.effectiveQualityLevel())
        g.applyFrameRate()
        assertEquals(60, host.fps)
        assertEquals(Quality.HIGH, g.quality.level)

        g.applyGameMode(GameModes.BATTERY)
        assertEquals(30, host.fps)
        assertEquals(30, g.effectiveFpsCap())
        assertEquals(Quality.MED, g.quality.level)
        assertEquals("Wybór gracza w profilu bez zmian", 60, profile.fpsCap)
        assertEquals(Quality.HIGH, profile.quality)
        assertEquals(1000f / 30f, g.cpuBudgetMs, 0.01f)

        // Ten sam tryb drugi raz nic nie przelicza.
        val scales = host.scales
        host.fps = -1
        g.applyGameMode(GameModes.BATTERY)
        assertEquals(-1, host.fps)
        assertEquals(scales, host.scales)

        g.applyGameMode(GameModes.STANDARD)
        assertEquals(60, host.fps)
        assertEquals(Quality.HIGH, g.quality.level)

        // Tryb automatyczny jakości: wykryta wysoka też spada do średniej.
        profile.quality = 0
        g.applyGameMode(GameModes.BATTERY)
        assertEquals(Quality.MED, g.effectiveQualityLevel())
        g.applyGameMode(GameModes.PERFORMANCE)
        assertEquals(Quality.HIGH, g.effectiveQualityLevel())
    }
}
