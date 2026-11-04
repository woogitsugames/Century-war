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

    /** Symuluje rzeczywistą liczbę klatek, a nie tylko wykonany czas CPU. */
    private fun driveMeasured(
        g: QualityGovernor, duration: Float, cpuMs: Float, fps: Int,
        displayHz: Float = 60f, fpsCap: Int = 0, max: Int = 12
    ): Int {
        var changes = 0
        repeat((duration * fps).toInt()) {
            if (g.record(cpuMs, 1f / fps, true, fpsCap, max,
                    1000f / fps, displayHz)) changes++
        }
        return changes
    }

    @Test fun cpuBoundUsesEffectsBeforeScale() {
        val g = QualityGovernor()
        assertEquals(1, driveMeasured(g, 4.5f, 24f, 40))
        assertEquals(0, g.gpuReduction)
        val q = Quality(Quality.HIGH, g.reduction, g.gpuReduction)
        assertTrue(q.maxParticles < Quality(Quality.HIGH).maxParticles)
        assertEquals(1f, q.baseScale, 0.001f)
        assertTrue(q.shadows)
    }

    @Test fun gpuBoundDropsAfterThreeSecondsAndReducesScaleFirst() {
        val g = QualityGovernor()
        assertEquals(0, driveMeasured(g, 2.8f, 4f, 38))
        assertEquals(1, driveMeasured(g, 0.5f, 4f, 38))
        assertEquals(1, g.reduction)
        assertEquals(1, g.gpuReduction)
        val q = Quality(Quality.HIGH, g.reduction, g.gpuReduction)
        assertEquals(0.95f, q.baseScale, 0.001f)
        assertFalse(q.shadows)
        assertEquals(Quality(Quality.HIGH).maxParticles, q.maxParticles)
        assertEquals(0, driveMeasured(g, 2f, 4f, 38))
        assertEquals(1, g.reduction)
    }

    @Test fun seventyFpsOn120HzDoesNotReduceQuality() {
        val g = QualityGovernor()
        assertEquals(0, driveMeasured(g, 20f, 4f, 70, displayHz = 120f))
        assertEquals(0, driveMeasured(g, 20f, 4f, 70, displayHz = 90f))
        assertEquals(0, g.reduction)
        assertEquals(1000f / 60f, QualityGovernor.targetFrameMs(0, 120f), 0.001f)
    }

    @Test fun explicitThirtyFpsCapDoesNotLookLikeGpuOverload() {
        val g = QualityGovernor()
        assertEquals(0, driveMeasured(g, 12f, 4f, 30, displayHz = 120f, fpsCap = 30))
        assertEquals(0, g.reduction)
        assertEquals(1000f / 30f, QualityGovernor.targetFrameMs(30, 120f), 0.001f)
    }

    @Test fun restoresGpuScaleAfterSustainedHealthyFrames() {
        val g = QualityGovernor()
        driveMeasured(g, 3.5f, 4f, 35)
        assertEquals(1, g.gpuReduction)
        assertEquals(0, driveMeasured(g, 9f, 4f, 60))
        assertEquals(1, g.reduction)
        assertEquals(1, driveMeasured(g, 2f, 4f, 60))
        assertEquals(0, g.reduction)
        assertEquals(0, g.gpuReduction)
    }

    @Test fun cannotRestoreWhileFramesStaySlow() {
        val g = QualityGovernor()
        driveMeasured(g, 3.5f, 4f, 35)
        driveMeasured(g, 15f, 4f, 48)
        assertTrue(g.reduction >= 1)
        assertTrue(g.gpuReduction >= 1)
    }

    // ---- R2-05: termika

    private fun driveThermal(g: QualityGovernor, duration: Float, cpuMs: Float, thermal: Int): Int {
        var changes = 0
        repeat((duration * 60f).toInt()) {
            if (g.record(cpuMs, 1f / 60f, true, 0, 12, thermal = thermal)) changes++
        }
        return changes
    }

    @Test fun moderateThermalStatusDropsOneCpuStepAfterThreeSecondsOnHealthyCpu() {
        val g = QualityGovernor()
        assertEquals(0, driveThermal(g, 2.9f, 8f, QualityGovernor.THERMAL_MODERATE))
        assertEquals(0, g.reduction)
        assertEquals(1, driveThermal(g, 0.2f, 8f, QualityGovernor.THERMAL_MODERATE))
        assertEquals(1, g.reduction)
        // Tylko stopień CPU: skala bufora (GPU) bez zmian.
        assertEquals(0, g.gpuReduction)
    }

    @Test fun moderateThermalForTwoMinutesGivesExactlyTwoStepsWithoutScale() {
        val g = QualityGovernor()
        driveThermal(g, 120f, 8f, QualityGovernor.THERMAL_MODERATE)
        assertEquals(2, g.reduction)
        assertEquals(2, g.thermalSteps)
        assertEquals(0, g.gpuReduction)
        assertEquals(1f, Quality(Quality.HIGH, g.reduction, g.gpuReduction).baseScale, 0.001f)
    }

    @Test fun secondThermalStepWaitsFifteenSeconds() {
        val g = QualityGovernor()
        assertEquals(1, driveThermal(g, 3.2f, 8f, QualityGovernor.THERMAL_MODERATE))
        assertEquals(0, driveThermal(g, 14.5f, 8f, QualityGovernor.THERMAL_MODERATE))
        assertEquals(1, g.reduction)
        assertEquals(1, driveThermal(g, 1f, 8f, QualityGovernor.THERMAL_MODERATE))
        assertEquals(2, g.reduction)
    }

    @Test fun thermalStepsReturnAfterCoolingAndCanRepeat() {
        val g = QualityGovernor()
        driveThermal(g, 60f, 8f, QualityGovernor.THERMAL_MODERATE)
        assertEquals(2, g.reduction)
        assertEquals(0, driveThermal(g, 9.5f, 8f, QualityGovernor.THERMAL_LIGHT))
        assertEquals(1, driveThermal(g, 1f, 8f, QualityGovernor.THERMAL_LIGHT))
        assertEquals(1, g.reduction)
        assertEquals(0, g.thermalSteps)
        driveThermal(g, 11f, 8f, QualityGovernor.THERMAL_LIGHT)
        assertEquals(0, g.reduction)
        // Ponowne nagrzanie znów daje najwyżej 2 stopnie.
        driveThermal(g, 60f, 8f, QualityGovernor.THERMAL_MODERATE)
        assertEquals(2, g.reduction)
    }

    @Test fun thermalDoesNotCrossIntoBufferScale() {
        val g = QualityGovernor()
        // Przeciążony CPU wyczerpał już 3 stopnie efektów.
        drive(g, 4.5f, 30f); drive(g, 4.5f, 30f); drive(g, 4.5f, 30f)
        assertEquals(3, g.reduction)
        driveThermal(g, 60f, 8f, QualityGovernor.THERMAL_MODERATE)
        // Tylko jeden stopień termiczny mieści się w efektach; skala bufora bez zmian.
        assertEquals(4, g.reduction)
        assertEquals(1f, Quality(Quality.HIGH, g.reduction, g.gpuReduction).baseScale, 0.001f)
    }

    @Test fun lightThermalStatusDoesNotDropQuality() {
        val g = QualityGovernor()
        assertEquals(0, driveThermal(g, 10f, 8f, QualityGovernor.THERMAL_LIGHT))
        assertEquals(0, g.reduction)
    }

    @Test fun qualityReturnsOnlyWhenPhoneCooledToLight() {
        val g = QualityGovernor()
        driveThermal(g, 3.2f, 8f, QualityGovernor.THERMAL_MODERATE)
        assertEquals(1, g.reduction)
        // Zdrowy CPU, ale wciąż gorąco (SEVERE): drugi stopień po 15 s, nigdy w górę.
        driveThermal(g, 16f, 8f, 3)
        assertEquals(2, g.reduction)
        val hot = g.reduction
        // Po ostygnięciu do LIGHT powrót dopiero po zwykłych 10 s zapasu.
        assertEquals(0, driveThermal(g, 9.5f, 8f, QualityGovernor.THERMAL_LIGHT))
        assertEquals(hot, g.reduction)
        assertEquals(1, driveThermal(g, 1f, 8f, QualityGovernor.THERMAL_LIGHT))
        assertEquals(hot - 1, g.reduction)
    }

    @Test fun thermalNoneKeepsPreviousResults() {
        val plain = QualityGovernor()
        val thermal = QualityGovernor()
        val loads = floatArrayOf(23f, 30f, 10f, 6f, 25f, 12f, 8f)
        repeat(40) { step ->
            val cpu = loads[step % loads.size]
            repeat(45) {
                assertEquals(plain.record(cpu, 1f / 60f, true, 0, 12, 22f, 60f),
                    thermal.record(cpu, 1f / 60f, true, 0, 12, 22f, 60f, thermal = 0))
            }
            assertEquals(plain.reduction, thermal.reduction)
            assertEquals(plain.gpuReduction, thermal.gpuReduction)
        }
    }

    @Test fun headroomSignalCountsAsModerateAndIgnoresNaN() {
        assertEquals(0, QualityGovernor.thermalSignal(0, Float.NaN))
        assertEquals(0, QualityGovernor.thermalSignal(0, 0.5f))
        assertEquals(2, QualityGovernor.thermalSignal(0, 0.85f))
        assertEquals(1, QualityGovernor.thermalSignal(1, 0.84f))
        assertEquals(4, QualityGovernor.thermalSignal(4, 0.9f))
        assertEquals(0, QualityGovernor.thermalSignal(0, Float.POSITIVE_INFINITY))
    }

    @Test fun manualQualityIgnoresThermal() {
        val g = QualityGovernor()
        repeat(600) { g.record(8f, 1f / 60f, false, 0, 12, thermal = 4) }
        assertEquals(0, g.reduction)
    }
}
