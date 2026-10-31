package com.centurywar.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicSynthTest {
    @Test fun allErasProduceFiniteUnclippedSamples() {
        val synth = MusicSynth(42)
        val buffer = FloatArray(2048)
        for (era in MusicSynth.MENU..7) {
            synth.setEra(era)
            synth.setIntensity(1f)
            repeat(48) {
                synth.render(buffer)
                for (sample in buffer) {
                    assertTrue("NaN lub nieskończoność (epoka $era)", sample.isFinite())
                    assertTrue("Przesterowanie (epoka $era): $sample", sample in -1f..1f)
                }
            }
        }
    }

    @Test fun deterministicForSameSeedIncludingTransitions() {
        val first = MusicSynth(157)
        val second = MusicSynth(157)
        val a = FloatArray(1024)
        val b = FloatArray(1024)
        for (era in intArrayOf(MusicSynth.MENU, 0, 2, 4, 6, 7, 3)) {
            first.setEra(era); second.setEra(era)
            first.setIntensity(0.72f); second.setIntensity(0.72f)
            repeat(50) {
                first.render(a); second.render(b)
                assertArrayEquals(a, b, 0f)
            }
        }
    }

    @Test fun crossfadeLastsExactlyTwoSeconds() {
        val synth = MusicSynth()
        synth.setEra(3)
        assertTrue(synth.isCrossfading)
        val sample = FloatArray(1)
        repeat(MusicSynth.CROSSFADE_SAMPLES - 1) { synth.render(sample) }
        assertTrue(synth.isCrossfading)
        synth.render(sample)
        assertFalse(synth.isCrossfading)
        assertEquals(3, synth.currentEra)
    }

    @Test fun pendingEraIsAppliedAfterPreviousFade() {
        val synth = MusicSynth()
        val buffer = FloatArray(MusicSynth.CROSSFADE_SAMPLES)
        synth.setEra(1)
        synth.setEra(7)
        synth.render(buffer)
        assertEquals(1, synth.currentEra)
        assertTrue(synth.isCrossfading)
        synth.render(buffer)
        assertEquals(7, synth.currentEra)
        assertFalse(synth.isCrossfading)
    }

    @Test fun pcmRemainsInRangeAndInvalidControlsAreSafe() {
        val synth = MusicSynth()
        synth.setIntensity(Float.NaN)
        val data = ShortArray(4096)
        synth.renderPcm(data, targetVolume = Float.POSITIVE_INFINITY)
        assertTrue(data.all { it.toInt() == 0 })
        synth.setIntensity(2f)
        synth.renderPcm(data)
        assertTrue(data.any { it.toInt() != 0 })
    }

    @Test fun differentSeedsAlterPercussion() {
        val a = MusicSynth(1)
        val b = MusicSynth(2)
        a.setEra(3); b.setEra(3)
        val first = FloatArray(4096)
        val second = FloatArray(4096)
        a.render(first); b.render(second)
        assertFalse(first.contentEquals(second))
    }

    @Test fun skipFadeJumpsStraightToTargetEra() {
        val synth = MusicSynth(7)
        synth.setEra(5)
        assertTrue(synth.isCrossfading)
        synth.skipFade()
        assertFalse(synth.isCrossfading)
        assertEquals(5, synth.currentEra)
    }
}
