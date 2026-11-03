package com.centurywar.engine

import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** TYMCZASOWE: zapis efektów do WAV do pomiaru ffmpeg-iem. */
class SfxLevelProbeTmp {
    @Test fun dump() {
        val dir = File(System.getenv("SFX_DUMP") ?: return)
        dir.mkdirs()
        for (id in 0 until SoundSynth.COUNT) {
            val data = SoundSynth.render(id, 0)
            val bytes = data.size * 2
            val b = ByteBuffer.allocate(44 + bytes).order(ByteOrder.LITTLE_ENDIAN)
            b.put("RIFF".toByteArray()); b.putInt(36 + bytes); b.put("WAVEfmt ".toByteArray())
            b.putInt(16); b.putShort(1); b.putShort(1)
            b.putInt(SoundSynth.SAMPLE_RATE); b.putInt(SoundSynth.SAMPLE_RATE * 2)
            b.putShort(2); b.putShort(16); b.put("data".toByteArray()); b.putInt(bytes)
            for (v in data) b.putShort((v.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
            File(dir, "s%02d.wav".format(id)).writeBytes(b.array())
        }
        for ((name, era, inten) in listOf(Triple("synth_menu", MusicSynth.MENU, 0f), Triple("synth_e3_calm", 3, 0f), Triple("synth_e3_battle", 3, 1f))) {
            val s = MusicSynth(); s.setSeed(1); s.setEra(era); s.skipFade(); s.setIntensity(inten)
            val n = MusicSynth.SAMPLE_RATE * 30
            val out = FloatArray(n)
            // rozgrzewka wygładzenia intensywności
            val warm = FloatArray(MusicSynth.SAMPLE_RATE * 5); s.render(warm, warm.size, 1f)
            s.render(out, n, 1f)
            val bytes = n * 2
            val b = ByteBuffer.allocate(44 + bytes).order(ByteOrder.LITTLE_ENDIAN)
            b.put("RIFF".toByteArray()); b.putInt(36 + bytes); b.put("WAVEfmt ".toByteArray())
            b.putInt(16); b.putShort(1); b.putShort(1)
            b.putInt(MusicSynth.SAMPLE_RATE); b.putInt(MusicSynth.SAMPLE_RATE * 2)
            b.putShort(2); b.putShort(16); b.put("data".toByteArray()); b.putInt(bytes)
            for (v in out) b.putShort((v.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
            File(dir, "$name.wav").writeBytes(b.array())
        }
    }
}
