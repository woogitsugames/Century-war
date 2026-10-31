package com.centurywar.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import android.os.SystemClock

/**
 * Strumieniowa muzyka proceduralna; cała synteza i AudioTrack działają na własnym wątku
 * o niskim priorytecie. Wątek żyje tylko, gdy Activity jest na pierwszym planie
 * ([resume]/[pause]) i głośność > 0, więc wyłączona muzyka nie kosztuje CPU.
 * Sterowanie ([setEra], [setIntensity], [setVolume]) jest bezpieczne z dowolnego wątku.
 */
class Music {
    companion object {
        const val MENU = MusicSynth.MENU
        private const val CHUNK_SAMPLES = 1024
        /** Sen, gdy bufor AudioTrack jest pełny (~1/3 porcji). */
        private const val FULL_SLEEP_MS = 15L
    }

    @Volatile private var era = MENU
    @Volatile private var intensity = 0f
    @Volatile private var volume = 0.5f
    private val duckPriority = DuckPriority()
    @Volatile private var duckSerial = 0
    private var foreground = false
    private var released = false
    /** Bieżący wątek; stary wątek kończy pracę, gdy przestaje być bieżącym. */
    @Volatile private var worker: Thread? = null

    /** Activity wróciła na pierwszy plan. */
    @Synchronized
    fun resume() {
        foreground = true
        sync()
    }

    /** Activity schodzi w tło: dźwięk milknie natychmiast, wątek kończy się sam. */
    @Synchronized
    fun pause() {
        foreground = false
        sync()
    }

    /** Koniec życia Activity; po tym muzyka już nie wystartuje. */
    @Synchronized
    fun release() {
        released = true
        foreground = false
        sync()
    }

    fun setEra(newEra: Int) {
        // 8 motywów odpowiada stylom grafiki; Epoka żelaza gra motyw feudalny (Defs.artPreset).
        era = if (newEra == MENU) MENU else com.centurywar.game.Defs.artPreset(newEra.coerceIn(0, com.centurywar.game.Defs.ERA_COUNT - 1))
    }

    fun setIntensity(value: Float) {
        intensity = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
    }

    @Synchronized
    fun setVolume(value: Float) {
        volume = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
        sync()
    }

    /** Słabsze zdarzenie nie skraca silniejszego wyciszenia; wątek audio odbiera tylko zmianę. */
    @Synchronized
    fun duck(amount: Float, seconds: Float) {
        if (duckPriority.request(amount, seconds, SystemClock.uptimeMillis())) duckSerial++
    }

    val isPlaying: Boolean get() = worker != null

    private fun sync() {
        val want = foreground && !released && volume > 0f
        val t = worker
        if (want && t == null) {
            worker = Thread({ stream() }, "music-stream").apply {
                priority = Thread.MIN_PRIORITY
                isDaemon = true
                start()
            }
        } else if (!want && t != null) {
            worker = null
            t.interrupt()
        }
    }

    private fun stream() {
        val me = Thread.currentThread()
        try { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND) } catch (_: RuntimeException) { }
        val synth = MusicSynth()
        synth.setEra(era)
        synth.skipFade()
        val pcm = ShortArray(CHUNK_SAMPLES)
        val minBytes = AudioTrack.getMinBufferSize(
            MusicSynth.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBytes <= 0) return
        // ~250 ms bufora (PCM16 mono = 2 bajty na próbkę).
        val bufferBytes = (maxOf(minBytes, MusicSynth.SAMPLE_RATE * 2 / 4) + 1) and -2
        var track: AudioTrack? = null
        try {
            track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(MusicSynth.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            if (track.state != AudioTrack.STATE_INITIALIZED) return
            track.play()
            var seenDuckSerial = -1
            while (worker === me) {
                if (duckSerial != seenDuckSerial) synchronized(this) {
                    val serial = duckSerial
                    if (serial != seenDuckSerial) {
                        seenDuckSerial = serial
                        val remaining = duckPriority.remainingSeconds(SystemClock.uptimeMillis())
                        if (remaining > 0f) synth.duck(duckPriority.amount, remaining)
                    }
                }
                synth.setEra(era)
                synth.setIntensity(intensity)
                synth.renderPcm(pcm, pcm.size, volume)
                var written = 0
                // Zapis nieblokujący: pauza/zatrzymanie nie może utknąć w write() na pełnym buforze.
                while (written < pcm.size && worker === me) {
                    val n = track.write(pcm, written, pcm.size - written, AudioTrack.WRITE_NON_BLOCKING)
                    if (n < 0) return
                    if (n == 0) Thread.sleep(FULL_SLEEP_MS) else written += n
                }
            }
        } catch (_: InterruptedException) {
            // pause()/release(): normalne zakończenie.
        } catch (_: RuntimeException) {
            // Brak wyjścia audio albo nieobsługiwany format nie zatrzymuje gry.
        } finally {
            track?.let {
                try { it.pause(); it.flush() } catch (_: RuntimeException) { }
                it.release()
            }
        }
    }
}
