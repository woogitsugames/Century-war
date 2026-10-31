package com.centurywar.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class SoundSynthTest {
    @Test fun allSamplesAreBoundedAndCacheFitsBudget() {
        var bytes = 0
        for (id in 0 until SoundSynth.COUNT) {
            val wave = SoundSynth.render(id)
            assertEquals((SoundSynth.SAMPLE_RATE * SoundSynth.duration(id)).toInt(), wave.size)
            assertTrue("Zbyt długi wariant $id", wave.size <= SoundSynth.SAMPLE_RATE)
            for (v in wave) {
                assertTrue("Nieprawidłowa próbka $id", v.isFinite())
                assertTrue("Przesterowanie wariantu $id", v in -1f..1f)
            }
            bytes += 44 + wave.size * 2
        }
        assertEquals(SoundSynth.totalWavBytes(), bytes)
        assertTrue("Przekroczony budżet WAV: $bytes", bytes <= SoundSynth.MAX_CACHE_BYTES)
    }

    @Test fun everyVariantIsDeterministicAndAudiblyDistinct() {
        for (id in 0 until SoundSynth.COUNT) {
            assertArrayEquals("Niedeterministyczny wariant $id",
                SoundSynth.render(id), SoundSynth.render(id), 0f)
        }
        // Różnica średniej energii w paśmie wysokim (różniczka próbki),
        // a nie tylko inny poziom głośności sygnału.
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

    @Test fun wavPreviewsUsePcm16AndHaveValidHeaders() {
        val dir = File("build/sfx-preview").apply { mkdirs() }
        val choices = intArrayOf(
            SoundSynth.HIT_WOOD, SoundSynth.HIT_BRONZE, SoundSynth.HIT_STEEL, SoundSynth.HIT_ENERGY,
            SoundSynth.SHOT_BOW, SoundSynth.SHOT_CANNON, SoundSynth.SHOT_LASER,
            SoundSynth.TURRET_POWDER, SoundSynth.BARRAGE_STONE, SoundSynth.BARRAGE_ORBITAL,
            SoundSynth.EVOLVE_PRIMITIVE, SoundSynth.EVOLVE_FUTURE, SoundSynth.ACHIEVEMENT,
        )
        for (id in choices) {
            val samples = SoundSynth.render(id)
            val bytes = samples.size * 2
            val wav = ByteBuffer.allocate(44 + bytes).order(ByteOrder.LITTLE_ENDIAN)
            wav.put("RIFF".toByteArray()); wav.putInt(36 + bytes)
            wav.put("WAVEfmt ".toByteArray()); wav.putInt(16)
            wav.putShort(1); wav.putShort(1)
            wav.putInt(SoundSynth.SAMPLE_RATE); wav.putInt(SoundSynth.SAMPLE_RATE * 2)
            wav.putShort(2); wav.putShort(16)
            wav.put("data".toByteArray()); wav.putInt(bytes)
            for (sample in samples) wav.putShort((sample * 32767f).toInt().toShort())
            val path = File(dir, "sample_$id.wav")
            path.writeBytes(wav.array())
            assertEquals(SoundSynth.wavBytes(id).toLong(), path.length())
        }
    }
}
