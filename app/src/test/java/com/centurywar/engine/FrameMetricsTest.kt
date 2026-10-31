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
}
