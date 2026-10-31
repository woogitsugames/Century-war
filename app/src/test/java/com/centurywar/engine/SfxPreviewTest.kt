package com.centurywar.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Opcjonalny bank WAV do odsłuchu: SFX_PREVIEW=true ./gradlew :app:testDebugUnitTest. */
class SfxPreviewTest {
    @Test fun exportEverySoundWhenRequested() {
        if (System.getenv("SFX_PREVIEW") != "true") return
        val dir = File("build/sfx").apply { mkdirs() }
        val clips = ArrayList<FloatArray>(SoundSynth.COUNT * SoundSynth.VARIANTS)
        val start = System.nanoTime()
        var samples = 0
        for (id in 0 until SoundSynth.COUNT) {
            for (variant in 0 until SoundSynth.VARIANTS) {
                val pcm = SoundSynth.render(id, variant)
                samples += pcm.size
                clips.add(pcm)
                val wav = File(dir, "s" + id + "_" + variant + ".wav")
                writeWav(wav, pcm)
                assertEquals(SoundSynth.wavBytes(id).toLong(), wav.length())
            }
        }
        val synthesisMs = (System.nanoTime() - start) / 1_000_000.0
        val gap = (SoundSynth.SAMPLE_RATE * 0.08f).toInt()
        val together = FloatArray(samples + (clips.size - 1) * gap)
        var offset = 0
        for (clip in clips) {
            clip.copyInto(together, offset)
            offset += clip.size + gap
        }
        val all = File(dir, "all.wav")
        writeWav(all, together)
        assertTrue(all.length() > SoundSynth.totalWavBytes())
        println("SFX: " + clips.size + " próbek, bank " + SoundSynth.totalWavBytes() +
            " B, synteza JVM " + "%.1f".format(java.util.Locale.ROOT, synthesisMs) +
            " ms, podgląd " + all.length() + " B")
    }

    private fun writeWav(file: File, pcm: FloatArray) {
        val bytes = pcm.size * 2
        val b = ByteBuffer.allocate(44 + bytes).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray(Charsets.US_ASCII))
        b.putInt(36 + bytes)
        b.put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
        b.putInt(16)
        b.putShort(1); b.putShort(1)
        b.putInt(SoundSynth.SAMPLE_RATE)
        b.putInt(SoundSynth.SAMPLE_RATE * 2)
        b.putShort(2); b.putShort(16)
        b.put("data".toByteArray(Charsets.US_ASCII))
        b.putInt(bytes)
        for (x in pcm) b.putShort((x.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
        file.writeBytes(b.array())
    }
}
