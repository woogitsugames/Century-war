package com.centurywar.engine

import android.content.Context
import android.os.Looper
import android.os.PowerManager
import com.centurywar.meta.Profile
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThermalWatchTest {
    private val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
    }
    private fun game(): Game {
        val file = File.createTempFile("cw-thermal-", ".json").apply { delete() }
        return Game(host, Profile(file), Quality.LOW)
    }
    private fun power(): PowerManager = RuntimeEnvironment.getApplication()
        .getSystemService(Context.POWER_SERVICE) as PowerManager

    @Test fun statusChangesReachGameAndGovernorSignal() {
        val pm = power(); val shadow = shadowOf(pm); val g = game()
        shadow.setCurrentThermalStatus(PowerManager.THERMAL_STATUS_NONE)
        val watch = ThermalWatch(pm, g)
        try {
            watch.start()
            assertEquals(PowerManager.THERMAL_STATUS_NONE, g.thermalStatus)
            shadow.setCurrentThermalStatus(PowerManager.THERMAL_STATUS_SEVERE)
            assertEquals(PowerManager.THERMAL_STATUS_SEVERE, g.thermalStatus)
            assertEquals(PowerManager.THERMAL_STATUS_SEVERE,
                QualityGovernor.thermalSignal(g.thermalStatus, g.thermalHeadroom))
            val governor = QualityGovernor()
            repeat(60 * 20) {
                governor.record(8f, 1f / 60f, true, 0, 12,
                    thermal = QualityGovernor.thermalSignal(g.thermalStatus, g.thermalHeadroom))
            }
            assertTrue(governor.reduction > 0)
            shadow.setCurrentThermalStatus(PowerManager.THERMAL_STATUS_NONE)
            assertEquals(PowerManager.THERMAL_STATUS_NONE, g.thermalStatus)
        } finally { watch.stop() }
    }

    @Test fun stopUnregistersAndStopsUpdates() {
        val pm = power(); val shadow = shadowOf(pm); val g = game(); val watch = ThermalWatch(pm, g)
        watch.start(); assertEquals(1, shadow.thermalStatusListeners.size)
        watch.stop(); assertEquals(0, shadow.thermalStatusListeners.size)
        val before = g.thermalStatus
        shadow.setCurrentThermalStatus(PowerManager.THERMAL_STATUS_CRITICAL)
        shadowOf(Looper.getMainLooper()).idleFor(30, TimeUnit.SECONDS)
        assertEquals(before, g.thermalStatus)
        assertTrue(g.thermalHeadroom.isNaN())
    }

    @Test fun repeatedStartAndStopAreIdempotent() {
        val pm = power(); val shadow = shadowOf(pm); val watch = ThermalWatch(pm, game())
        watch.start(); watch.start(); assertEquals(1, shadow.thermalStatusListeners.size)
        watch.stop(); watch.stop(); assertEquals(0, shadow.thermalStatusListeners.size)
    }

    @Test fun absentPowerManagerIsSafe() {
        val g = game(); g.thermalHeadroom = 0.9f
        val watch = ThermalWatch(null, g)
        watch.start(); watch.start(); watch.stop(); watch.stop()
        assertTrue(g.thermalHeadroom.isNaN())
    }
}
