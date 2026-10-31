package com.centurywar.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityGovernorTest {
    private fun drive(g: QualityGovernor, duration: Float, cpuMs: Float,
                      automatic: Boolean = true, fpsCap: Int = 0, max: Int = 12): Int {
        var changes = 0
        repeat((duration * 60f).toInt()) {
            if (g.record(cpuMs, 1f / 60f, automatic, fpsCap, max)) changes++
        }
        return changes
    }

    @Test fun dropsInStepsWithCooldown() {
        val g = QualityGovernor()
        assertEquals(0, drive(g, 3f, 23f))
        assertEquals(0, g.reduction)
        assertEquals(1, drive(g, 2f, 23f))
        assertEquals(1, g.reduction)
        assertEquals(0, drive(g, 2f, 23f))
        assertEquals(1, g.reduction)
        assertEquals(1, drive(g, 2f, 23f))
        assertEquals(2, g.reduction)
    }

    @Test fun restoresOnlyAfterTenSecondsOfSpareBudget() {
        val g = QualityGovernor()
        drive(g, 4.5f, 30f)
        assertEquals(1, g.reduction)
        drive(g, 9f, 10f)
        assertEquals(1, g.reduction)
        assertEquals(1, drive(g, 2f, 10f))
        assertEquals(0, g.reduction)
    }

    @Test fun doesNotOscillateUnderAlternatingLoad() {
        val g = QualityGovernor()
        repeat(12) {
            drive(g, 1f, 25f)
            drive(g, 1f, 6f)
        }
        assertEquals(0, g.reduction)
    }

    @Test fun respectsManualQualityAndFpsCap() {
        val g = QualityGovernor()
        drive(g, 5f, 25f, fpsCap = 30)
        assertEquals(0, g.reduction)
        drive(g, 5f, 30f)
        assertTrue(g.reduction > 0)
        assertTrue(g.record(30f, 0.016f, false, 0, 12))
        assertEquals(0, g.reduction)
        assertFalse(g.record(30f, 0.016f, false, 0, 12))
    }

    @Test fun highRefreshDoesNotSet120FpsAsItsTarget() {
        val g = QualityGovernor()
        // 12 ms CPU to dobry wynik dla docelowych 60 FPS na ekranie 120 Hz.
        repeat(120 * 20) { g.record(12f, 1f / 120f, true, 0, 12) }
        assertEquals(0, g.reduction)
    }

    @Test fun scaleAndEffectsDegradeInOrder() {
        val start = Quality(Quality.HIGH)
        val particles = Quality(Quality.HIGH, 1)
        val clouds = Quality(Quality.HIGH, 2)
        val scenery = Quality(Quality.HIGH, 3)
        val shadows = Quality(Quality.HIGH, 4)
        val scale = Quality(Quality.HIGH, 5)
        assertTrue(particles.maxParticles < start.maxParticles)
        assertTrue(start.clouds && !clouds.clouds)
        assertTrue(scenery.parallaxLayers < start.parallaxLayers)
        assertTrue(start.shadows && !shadows.shadows)
        assertEquals(0.95f, scale.baseScale, 0.001f)
        assertEquals(0.6f, Quality(Quality.HIGH, start.maxReductionSteps).baseScale, 0.001f)
    }
}
