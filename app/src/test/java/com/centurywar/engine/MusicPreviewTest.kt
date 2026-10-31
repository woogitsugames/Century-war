package com.centurywar.engine

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Odsłuch ręczny: MUSIC_PREVIEW=true ./gradlew :app:testDebugUnitTest --tests '*MusicPreviewTest*' --rerun-tasks. */
class MusicPreviewTest {
    @Test fun exportSixFamiliesAtTwoTensionLevels() {
        assumeTrue("Eksport tylko przy MUSIC_PREVIEW=true", System.getenv("MUSIC_PREVIEW") == "true")
        val folder = File("build/music")
        assertTrue(folder.isDirectory || folder.mkdirs())
        val families = arrayOf("primitive", "ancient", "medieval", "powder", "industrial", "future")
        val eras = intArrayOf(0, 1, 2, 3, 4, 6)
        for (i in families.indices) {
            for (high in arrayOf(false, true)) {
                val synth = MusicSynth(5500 + i)
                synth.setEra(eras[i])
                synth.skipFade()
                synth.setIntensity(if (high) 0.95f else 0.10f)
                val out = File(folder, "${families[i]}_${if (high) "high" else "low"}.wav")
                writeWav(out, synth, 60)
                assertEquals(44L + MusicSynth.SAMPLE_RATE * 60L * 2L, out.length())
            }
        }
    }

    private fun writeWav(file: File, synth: MusicSynth, seconds: Int) {
        val samples = MusicSynth.SAMPLE_RATE * seconds
        val block = ShortArray(2048)
        val bytes = ByteArray(block.size * 2)
        BufferedOutputStream(FileOutputStream(file), 32 * 1024).use { out ->
            val header = ByteArray(44)
            fun word(offset: Int, value: Int) {
                header[offset] = value.toByte()
                header[offset + 1] = (value ushr 8).toByte()
            }
            fun dword(offset: Int, value: Int) {
                word(offset, value)
                word(offset + 2, value ushr 16)
            }
            "RIFF".toByteArray(Charsets.US_ASCII).copyInto(header, 0)
            dword(4, 36 + samples * 2)
            "WAVEfmt ".toByteArray(Charsets.US_ASCII).copyInto(header, 8)
            dword(16, 16)
            word(20, 1)
            word(22, 1)
            dword(24, MusicSynth.SAMPLE_RATE)
            dword(28, MusicSynth.SAMPLE_RATE * 2)
            word(32, 2)
            word(34, 16)
            "data".toByteArray(Charsets.US_ASCII).copyInto(header, 36)
            dword(40, samples * 2)
            out.write(header)
            var left = samples
            while (left > 0) {
                val count = minOf(left, block.size)
                synth.renderPcm(block, count)
                for (j in 0 until count) {
                    val v = block[j].toInt()
                    bytes[j * 2] = v.toByte()
                    bytes[j * 2 + 1] = (v ushr 8).toByte()
                }
                out.write(bytes, 0, count * 2)
                left -= count
            }
        }
    }
}
