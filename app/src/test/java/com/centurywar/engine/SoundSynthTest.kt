package com.centurywar.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

class SoundSynthTest {
    @Test fun allSamplesAreBoundedAndCacheFitsBudget() {
        var bytes = 0
        for (id in 0 until SoundSynth.COUNT) {
            val first = SoundSynth.render(id, 0)
            for (variant in 0 until SoundSynth.VARIANTS) {
                val wave = if (variant == 0) first else SoundSynth.render(id, variant)
                assertEquals((SoundSynth.SAMPLE_RATE * SoundSynth.duration(id)).toInt(), wave.size)
                assertTrue("Zbyt długi efekt $id", wave.size <= SoundSynth.SAMPLE_RATE)
                var sum = 0.0
                for (v in wave) {
                    assertTrue("NaN/Infinity: $id/$variant", v.isFinite())
                    assertTrue("Przesterowanie $id/$variant", v in -0.761f..0.761f)
                    sum += v.toDouble() * v
                }
                val rms = sqrt(sum / wave.size)
                assertTrue("Zbyt cicho $id/$variant: $rms", rms >= 0.05 && rms <= 0.18)
                assertArrayEquals("Brak determinizmu $id/$variant",
                    wave, SoundSynth.render(id, variant), 0f)
                bytes += 44 + wave.size * 2
            }
            assertFalse("Takie same warianty $id", first.contentEquals(SoundSynth.render(id, 1)))
        }
        assertEquals(SoundSynth.totalWavBytes(), bytes)
        assertTrue("Za duży bank WAV: $bytes", bytes <= SoundSynth.MAX_CACHE_BYTES)
    }

    @Test fun epochsHaveDifferentSpectralProfiles() {
        val wood = highBandEnergy(SoundSynth.render(SoundSynth.HIT_WOOD))
        val bronze = highBandEnergy(SoundSynth.render(SoundSynth.HIT_BRONZE))
        val steel = highBandEnergy(SoundSynth.render(SoundSynth.HIT_STEEL))
        val energy = highBandEnergy(SoundSynth.render(SoundSynth.HIT_ENERGY))
        assertTrue(abs(wood - bronze) > 0.00005)
        assertTrue(abs(bronze - steel) > 0.00005)
        assertTrue(abs(steel - energy) > 0.00005)
        assertFalse(SoundSynth.render(SoundSynth.BARRAGE_STONE)
            .contentEquals(SoundSynth.render(SoundSynth.BARRAGE_ORBITAL)))
    }

    private fun highBandEnergy(data: FloatArray): Double {
        var sum = 0.0
        for (i in 1 until data.size) {
            val d = (data[i] - data[i - 1]).toDouble()
            sum += d * d
        }
        return sum / (data.size - 1).coerceAtLeast(1)
    }

    @Test fun wavHeaderMatchesPcm16Data() {
        val pcm = SoundSynth.render(SoundSynth.EVOLVE_MEDIEVAL, 1)
        val b = ByteBuffer.allocate(SoundSynth.wavBytes(SoundSynth.EVOLVE_MEDIEVAL))
            .order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray(Charsets.US_ASCII)); b.putInt(36 + pcm.size * 2)
        b.put("WAVEfmt ".toByteArray(Charsets.US_ASCII)); b.putInt(16)
        b.putShort(1); b.putShort(1)
        b.putInt(SoundSynth.SAMPLE_RATE); b.putInt(SoundSynth.SAMPLE_RATE * 2)
        b.putShort(2); b.putShort(16)
        b.put("data".toByteArray(Charsets.US_ASCII)); b.putInt(pcm.size * 2)
        for (v in pcm) b.putShort((v * 32767f).toInt().toShort())
        assertEquals(SoundSynth.wavBytes(SoundSynth.EVOLVE_MEDIEVAL), b.position())
        assertEquals('R'.code.toByte(), b.get(0))
        assertEquals('I'.code.toByte(), b.get(1))
        assertEquals(SoundSynth.SAMPLE_RATE, b.getInt(24))
    }

    @Test fun firstUnloadedSamplePlaysOnlyWithin150Milliseconds() {
        val pending = PendingSfx(3)
        pending.offer(0, 0.6f, 45L, 1.2f, -0.3f, 2, 1_000L)
        assertTrue(pending.pop(0, 1_149L))
        assertFalse(pending.pop(0, 1_149L))
        assertEquals(0.6f, pending.volume(0), 0f)
        assertEquals(45L, pending.gap(0))
        assertEquals(1.2f, pending.pitch(0), 0f)
        assertEquals(-0.3f, pending.pan(0), 0f)
        pending.offer(1, 0.5f, 0L, 1f, 0f, 1, 2_000L)
        assertFalse(pending.pop(1, 2_150L))
        assertFalse(pending.pop(1, 2_151L))
    }

    @Test fun pendingSamplesAreIndependentAndDoNotOverwriteHigherPriority() {
        val pending = PendingSfx(3)
        pending.offer(0, 0.9f, 80L, 1f, 0.5f, 3, 100L)
        pending.offer(1, 0.4f, 10L, 1f, 0f, 1, 101L)
        pending.offer(0, 0.1f, 5L, 1f, -0.5f, 1, 110L)
        assertTrue(pending.pop(0, 249L))
        assertEquals(3, pending.priority(0))
        assertEquals(0.9f, pending.volume(0), 0f)
        assertTrue(pending.pop(1, 249L))
        pending.offer(2, 0.4f, 20L, 1f, 0f, 1, 300L)
        pending.offer(2, 0.7f, 40L, 1.1f, 0.2f, 1, 350L)
        assertTrue(pending.pop(2, 499L))
        assertEquals(0.7f, pending.volume(2), 0f)
        pending.offer(2, 0.4f, 30L, 1f, 0f, 1, 600L)
        pending.clear(2)
        assertFalse(pending.pop(2, 601L))
    }

}
