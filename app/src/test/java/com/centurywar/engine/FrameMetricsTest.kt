package com.centurywar.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameMetricsTest {
    @Test fun measuresOnlyCpuAndComputesPercentileWithoutNewBuffers() {
        val m = FrameMetrics(20)
        repeat(19) { m.add(2_000_000L, 3_000_000L, 5, 0) }
        m.add(12_000_000L, 8_000_000L, 3, 2)
        assertEquals(5.75f, m.avgCpuMs, 0.001f)
        assertEquals(5f, m.p95Ms(), 0.001f)
        assertEquals(3, m.steps)
        assertEquals(2, m.droppedSteps)
        assertEquals(2L, m.totalDroppedSteps)
        m.add(1_000_000L, 1_000_000L, 1, 0)
        assertEquals(20, m.samples)
    }

    @Test fun tracksPostAndActualIntervalsWithoutMixingCpu() {
        val m = FrameMetrics(3)
        m.add(2_000_000L, 3_000_000L, 1, 0)
        m.addPresentation(8_000_000L, 16_000_000L, 120f)
        m.addPresentation(6_000_000L, 20_000_000L, 120f)
        m.addPresentation(4_000_000L, 24_000_000L, 90f)
        assertEquals(3, m.presentationSamples)
        assertEquals(6f, m.avgPostMs, 0.001f)
        assertEquals(20f, m.avgFrameIntervalMs, 0.001f)
        assertEquals(90f, m.refreshHz, 0.001f)
        assertEquals(5f, m.avgCpuMs, 0.001f)

        // Nadpisanie najstarszego pomiaru nie zmienia rozmiaru bufora.
        m.addPresentation(2_000_000L, 12_000_000L, 60f)
        assertEquals(3, m.presentationSamples)
        assertEquals(4f, m.avgPostMs, 0.001f)
        assertEquals(56f / 3f, m.avgFrameIntervalMs, 0.001f)
        assertEquals(60f, m.refreshHz, 0.001f)
        assertEquals(5f, m.avgCpuMs, 0.001f)
    }

    @Test fun presentationMetricsIgnoreNegativeDurations() {
        val m = FrameMetrics(2)
        m.addPresentation(-1L, -1L, -1f)
        assertEquals(0f, m.avgPostMs, 0.001f)
        assertEquals(0f, m.avgFrameIntervalMs, 0.001f)
        assertEquals(60f, m.refreshHz, 0.001f)
    }
}
