package com.centurywar.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Regresja aranżacji, dynamiki i ciągłości PCM między porcjami. */
class MusicArrangementTest {
    @Test fun allStylesAndSectionsStayFiniteAndUnclipped() {
        val buffer = FloatArray(2048)
        for (era in MusicSynth.MENU..8) {
            val synth = MusicSynth(55)
            synth.setEra(era)
            synth.skipFade()
            synth.setIntensity(1f)
            repeat(200) {
                synth.render(buffer)
                for (sample in buffer) {
                    assertTrue("Nieprawidłowa próbka epoki $era", sample.isFinite())
                    assertTrue("Przesterowanie epoki $era", sample in -1f..1f)
                }
            }
        }
    }

    @Test fun differentRenderChunkSizesProduceIdenticalSamples() {
        val whole = MusicSynth(55)
        val split = MusicSynth(55)
        whole.setEra(6); split.setEra(6)
        whole.skipFade(); split.skipFade()
        whole.setIntensity(0.89f); split.setIntensity(0.89f)
        val a = FloatArray(8192)
        val b = FloatArray(8192)
        whole.render(a)
        val block = FloatArray(1024)
        for (offset in b.indices step block.size) {
            split.render(block)
            block.copyInto(b, offset)
        }
        assertArrayEquals(a, b, 0f)
        assertTrue("Trzask na styku bloków", abs(b[1024] - b[1023]) < 0.4f)
    }

    @Test fun changingIntensityAddsLayersSmoothly() {
        val calm = MusicSynth(55)
        val busy = MusicSynth(55)
        calm.setEra(4); busy.setEra(4)
        calm.skipFade(); busy.skipFade()
        calm.setIntensity(0.05f); busy.setIntensity(1f)
        val a = FloatArray(1024)
        val b = FloatArray(1024)
        var calmEnergy = 0.0
        var busyEnergy = 0.0
        repeat(140) {
            calm.render(a); busy.render(b)
            if (it > 25) for (i in a.indices) {
                calmEnergy += a[i] * a[i]
                busyEnergy += b[i] * b[i]
            }
        }
        assertTrue("Wysokie napięcie nie dodaje warstw", busyEnergy > calmEnergy * 1.14)
        busy.setIntensity(0f)
        var previous = b.last()
        repeat(16) {
            busy.render(b)
            for (sample in b) {
                assertTrue("Skok próbki podczas zmiany napięcia", abs(sample - previous) < 0.4f)
                previous = sample
            }
        }
    }

    @Test fun eachEraFamilyHasDistinctArrangement() {
        val first = MusicSynth(123)
        val second = MusicSynth(123)
        first.setEra(0); second.setEra(1)
        first.skipFade(); second.skipFade()
        val a = FloatArray(4096)
        val b = FloatArray(4096)
        first.render(a); second.render(b)
        assertFalse(a.contentEquals(b))
    }
}
