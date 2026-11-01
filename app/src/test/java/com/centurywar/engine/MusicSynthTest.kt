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
        for (era in MusicSynth.MENU..8) {
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

    @Test fun duckingFadesSmoothlyAndRestoresLevel() {
        val normal = MusicSynth(912)
        val quiet = MusicSynth(912)
        normal.setEra(2); quiet.setEra(2)
        normal.skipFade(); quiet.skipFade()
        val a = FloatArray(1024)
        val b = FloatArray(1024)
        repeat(12) { normal.render(a); quiet.render(b) }
        quiet.duck(0.24f, 0.4f)
        var normalPower = 0.0
        var quietPower = 0.0
        repeat(7) {
            normal.render(a); quiet.render(b)
            for (i in a.indices) {
                normalPower += a[i] * a[i]
                quietPower += b[i] * b[i]
            }
        }
        assertTrue("Ducking nie zmniejszył energii", quietPower < normalPower * 0.25)
        assertTrue("Brak ataku", quiet.duckGain < 0.3f)
        repeat(40) { normal.render(a); quiet.render(b) }
        assertTrue("Brak płynnego powrotu", quiet.duckGain > 0.98f)
        assertTrue(quiet.duckGain <= 1f)
    }

    @Test fun duckingDoesNotIntroduceClickAtTransition() {
        val synth = MusicSynth(113)
        synth.setEra(2); synth.skipFade()
        val frame = FloatArray(1)
        repeat(500) { synth.render(frame) }
        synth.duck(0.18f, 0.02f)
        var prev = 0f
        var maxJump = 0f
        repeat(8_000) {
            synth.render(frame)
            val jump = kotlin.math.abs(frame[0] - prev)
            if (jump > maxJump) maxJump = jump
            prev = frame[0]
        }
        assertTrue("Trzask w obwiedni: $maxJump", maxJump < 0.6f)
        assertTrue(synth.duckGain > 0.80f)
    }


    @Test fun weakerDuckNeverShortensOrReplacesActiveStrongerDuck() {
        val duck = DuckPriority()
        assertTrue(duck.request(0.35f, 1.5f, 1_000L))
        assertFalse(duck.request(0.60f, 0.3f, 1_200L))
        assertEquals(0.35f, duck.amount, 0f)
        assertEquals(1.3f, duck.remainingSeconds(1_200L), 0.001f)
        assertFalse(duck.request(0.80f, 3f, 1_300L))
        assertEquals(0.35f, duck.amount, 0f)
        assertEquals(1.2f, duck.remainingSeconds(1_300L), 0.001f)
    }

    @Test fun equalOrStrongerDucksKeepLongerHold() {
        val duck = DuckPriority()
        assertTrue(duck.request(0.5f, 1f, 100L))
        assertFalse(duck.request(0.5f, 0.2f, 200L))
        assertTrue(duck.request(0.5f, 2f, 300L))
        assertEquals(2f, duck.remainingSeconds(300L), 0.001f)
        assertTrue(duck.request(0.24f, 0.1f, 400L))
        assertEquals(0.24f, duck.amount, 0f)
        assertEquals(1.9f, duck.remainingSeconds(400L), 0.001f)
        assertTrue(duck.request(0.65f, 0.4f, 2_300L))
        assertEquals(0.65f, duck.amount, 0f)
        assertEquals(0.4f, duck.remainingSeconds(2_300L), 0.001f)
    }

    @Test fun invalidAndExpiredDucksAreHandledSafely() {
        val duck = DuckPriority()
        assertFalse(duck.request(Float.NaN, 1f, 100L))
        assertFalse(duck.request(0.2f, Float.POSITIVE_INFINITY, 100L))
        assertTrue(duck.request(-5f, 30f, 100L))
        assertEquals(0.12f, duck.amount, 0f)
        assertEquals(12f, duck.remainingSeconds(100L), 0f)
        assertEquals(0f, duck.remainingSeconds(12_100L), 0f)
        assertTrue(duck.request(0.8f, 0.5f, 12_101L))
        assertEquals(0.8f, duck.amount, 0f)
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
