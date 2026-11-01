package com.centurywar.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ograniczanie geometrii ekranu działa także przy automatycznej redukcji CPU/GPU. */
class MenuQualityTest {
    @Test fun lowModeHasStaticSimplifiedScene() {
        val q = Quality(Quality.LOW)
        assertEquals(Quality.LOW, q.menuDetail)
        assertFalse(q.menuAnimated)
        assertEquals(Quality.MED, Quality(Quality.MED).menuDetail)
        assertTrue(Quality(Quality.MED).menuAnimated)
        assertEquals(Quality.HIGH, Quality(Quality.HIGH).menuDetail)
        assertTrue(Quality(Quality.HIGH).menuAnimated)
    }

    @Test fun cpuGovernorReducesMenuComplexityWithoutChangingBaseLevel() {
        val gov = QualityGovernor()
        val max = Quality(Quality.HIGH).maxReductionSteps
        repeat(90) { gov.record(30f, 0.1f, true, 60, max) }
        assertTrue("Redukcja CPU nie nastąpiła", gov.reduction >= 2)
        val q = Quality(Quality.HIGH, gov.reduction, gov.gpuReduction)
        assertEquals(Quality.HIGH, q.level)
        assertEquals(Quality.MED, q.menuDetail)
        assertFalse(q.menuAnimated)
    }

    @Test fun gpuGovernorReducesMenuLayersAsFrameIntervalsGrow() {
        val gov = QualityGovernor()
        val max = Quality(Quality.HIGH).maxReductionSteps
        repeat(75) { gov.record(3f, 0.1f, true, 60, max, 30f, 60f) }
        assertTrue("Redukcja GPU nie nastąpiła", gov.gpuReduction >= 2)
        val q = Quality(Quality.HIGH, gov.reduction, gov.gpuReduction)
        assertEquals(Quality.LOW, q.menuDetail)
        assertFalse(q.menuAnimated)
    }

    @Test fun manualQualityResetsAutomaticSceneReductions() {
        val gov = QualityGovernor()
        val max = Quality(Quality.HIGH).maxReductionSteps
        repeat(50) { gov.record(30f, 0.1f, true, 60, max) }
        assertTrue(gov.reduction > 0)
        assertTrue(gov.record(30f, 0.1f, false, 60, max))
        assertEquals(0, gov.reduction)
        assertEquals(0, gov.gpuReduction)
        assertEquals(Quality.HIGH, Quality(Quality.HIGH).menuDetail)
    }
}
