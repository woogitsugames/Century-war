package com.centurywar.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupTimingsTest {
    @Test fun reportsFirstMenuFrameAndSeparateCosts() {
        val timings = StartupTimings(1_000_000_000L)
        timings.record(StartupTimings.Phase.APPLICATION, 1_000_000_000L, 1_008_000_000L)
        timings.record(StartupTimings.Phase.PROFILE, 1_050_000_000L, 1_070_000_000L)
        timings.record(StartupTimings.Phase.LANGUAGES, 1_075_000_000L, 1_081_000_000L)
        val text = timings.report(1_300_000_000L)
        assertTrue(text, text.contains("300.0 ms od Application.onCreate"))
        assertTrue(text, text.contains("Application=8.0 ms"))
        assertTrue(text, text.contains("profil=20.0 ms"))
        assertTrue(text, text.contains("tabela języków=6.0 ms"))
        assertFalse(text, text.contains("SurfaceView="))
    }

    @Test fun negativeOrUnorderedTimesAreClamped() {
        val timings = StartupTimings(100L)
        timings.record(StartupTimings.Phase.SNAPSHOT, 200L, 100L)
        val text = timings.report(50L)
        assertTrue(text, text.contains("0.0 ms od Application.onCreate"))
        assertTrue(text, text.contains("zapis bitwy=0.0 ms"))
    }
}
