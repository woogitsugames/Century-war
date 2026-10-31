package com.centurywar.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Efekty dźwiękowe syntezowane w kodzie (szum, fale, obwiednie). Gra nie ma plików audio,
 * więc APK pozostaje malutki. Próbki generujemy raz w tle do cache aplikacji (WAV)
 * i odtwarzamy przez SoundPool (niskie opóźnienie, miksowanie wielu dźwięków).
 */
class Sound(private val ctx: Context) {
    companion object {
        const val HIT = 0
        const val SHOT = 1
        const val ARROW = 2
        const val EXPLOSION = 3
        const val DEATH = 4
        const val LASER = 5
        const val EVOLVE = 6
        const val COIN = 7
        const val CLICK = 8
        const val VICTORY = 9
        const val DEFEAT = 10
        const val THUMP = 11
        private const val COUNT = 12
        private const val RATE = 22050
        private const val VERSION = 1
    }

    private val pool = SoundPool.Builder()
        .setMaxStreams(10)
        .setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    private val ids = IntArray(COUNT) { -1 }
    private val lastPlay = LongArray(COUNT)
    @Volatile var enabled = true
    @Volatile var volume = 0.8f

    init {
        Thread({ generateAll() }, "sfx-gen").apply { priority = Thread.MIN_PRIORITY; start() }
    }

    /** Odtwarza dźwięk; [minGapMs] ogranicza spam, gdy w jednej klatce trafia 20 strzał. */
    fun play(id: Int, vol: Float = 1f, minGapMs: Long = 45L, pitch: Float = 1f) {
        if (!enabled || vol <= 0.02f) return
        val sid = ids[id]
        if (sid <= 0) return
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastPlay[id] < minGapMs) return
        lastPlay[id] = now
        val v = (vol * volume).coerceIn(0f, 1f)
        pool.play(sid, v, v, 1, 0, pitch.coerceIn(0.5f, 2f))
    }

    fun release() = pool.release()

    // ---------------------------------------------------------------- synteza

    private fun generateAll() {
        val dir = File(ctx.cacheDir, "sfx").apply { mkdirs() }
        for (i in 0 until COUNT) {
            val f = File(dir, "s${i}_v$VERSION.wav")
            if (!f.exists()) writeWav(f, synth(i))
            ids[i] = pool.load(f.absolutePath, 1)
        }
    }

    private var noiseSeed = 0x1234567
    private fun noise(): Float {
        noiseSeed = noiseSeed * 1103515245 + 12345
        return ((noiseSeed ushr 9) and 0xFFFF) / 32768f - 1f
    }

    private fun synth(id: Int): FloatArray = when (id) {
        HIT -> render(0.09f) { t, _ -> lowNoise(0.35f) * exp(-t * 45f) * 0.9f + sin(2 * PI * 180 * t).toFloat() * exp(-t * 60f) * 0.4f }
        SHOT -> render(0.16f) { t, _ -> noise() * exp(-t * 35f) * 0.8f + sin(2 * PI * 90 * t).toFloat() * exp(-t * 25f) * 0.6f }
        ARROW -> render(0.18f) { t, d -> lowNoise(0.08f + t / d * 0.3f) * sin(PI * t / d).toFloat() * 0.6f }
        EXPLOSION -> render(0.75f) { t, _ -> lowNoise(0.06f) * exp(-t * 5f) * 1.1f + sin(2 * PI * (55 - t * 30) * t).toFloat() * exp(-t * 6f) * 0.7f }
        DEATH -> render(0.22f) { t, d -> sin(2 * PI * (320 - 200 * t / d) * t).toFloat() * exp(-t * 12f) * 0.35f + lowNoise(0.2f) * exp(-t * 20f) * 0.3f }
        LASER -> render(0.2f) { t, d -> square(1300f - 1000f * t / d, t) * exp(-t * 10f) * 0.28f }
        EVOLVE -> arpeggio(floatArrayOf(523f, 659f, 784f, 1047f), 0.11f, 0.45f)
        COIN -> render(0.14f) { t, _ -> (sin(2 * PI * (if (t < 0.05f) 1320 else 1760) * t).toFloat()) * exp(-t * 18f) * 0.35f }
        CLICK -> render(0.025f) { t, _ -> sin(2 * PI * 900 * t).toFloat() * exp(-t * 200f) * 0.4f }
        VICTORY -> arpeggio(floatArrayOf(392f, 523f, 659f, 784f, 1047f), 0.14f, 0.7f)
        DEFEAT -> arpeggio(floatArrayOf(440f, 392f, 330f, 262f), 0.2f, 0.7f)
        else -> render(0.12f) { t, _ -> sin(2 * PI * 70 * t).toFloat() * exp(-t * 30f) * 0.8f + lowNoise(0.3f) * exp(-t * 50f) * 0.3f }
    }

    private var lp = 0f
    /** Szum przepuszczony przez prosty filtr dolnoprzepustowy (a = 0..1, mniejsze = ciemniejszy). */
    private fun lowNoise(a: Float): Float { lp += (noise() - lp) * a; return lp * 2.2f }

    private fun square(freq: Float, t: Float): Float = if (sin(2 * PI * freq * t) >= 0) 1f else -1f

    private inline fun render(dur: Float, fn: (Float, Float) -> Float): FloatArray {
        lp = 0f
        val n = (RATE * dur).toInt()
        return FloatArray(n) { i ->
            val t = i / RATE.toFloat()
            val fade = if (i > n - 200) (n - i) / 200f else 1f
            fn(t, dur) * fade
        }
    }

    private fun arpeggio(notes: FloatArray, step: Float, tail: Float): FloatArray {
        val dur = notes.size * step + tail
        return render(dur) { t, _ ->
            var s = 0f
            for ((i, f) in notes.withIndex()) {
                val st = i * step
                if (t >= st) {
                    val lt = t - st
                    s += (sin(2 * PI * f * lt).toFloat() * 0.6f + sin(2 * PI * f * 2 * lt).toFloat() * 0.2f) * exp(-lt * 4f)
                }
            }
            s * 0.25f
        }
    }

    private fun writeWav(f: File, data: FloatArray) {
        val bytes = data.size * 2
        val b = ByteBuffer.allocate(44 + bytes).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()); b.putInt(36 + bytes); b.put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()); b.putInt(16); b.putShort(1); b.putShort(1)
        b.putInt(RATE); b.putInt(RATE * 2); b.putShort(2); b.putShort(16)
        b.put("data".toByteArray()); b.putInt(bytes)
        for (v in data) b.putShort((v.coerceIn(-1f, 1f) * 32000f).toInt().toShort())
        val tmp = File(f.parentFile, f.name + ".tmp")
        FileOutputStream(tmp).use { it.write(b.array()) }
        tmp.renameTo(f)
    }
}
