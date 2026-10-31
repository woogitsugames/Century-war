package com.centurywar.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process

/** Strumieniowa muzyka proceduralna; cała synteza i AudioTrack działają poza wątkiem gry. */
class Music {
    companion object {
        const val MENU = MusicSynth.MENU
        private const val CHUNK_SAMPLES = 1024
    }

    @Volatile private var running = false
    @Volatile private var era = MENU
    @Volatile private var intensity = 0f
    @Volatile private var volume = 0.5f
    private var worker: Thread? = null

    @Synchronized
    fun start() {
        if (worker?.isAlive == true) return
        running = true
        worker = Thread({ stream() }, "music-stream").apply {
            priority = Thread.MIN_PRIORITY
            start()
        }
    }

    @Synchronized
    fun stop() {
        running = false
        val t = worker ?: return
        t.interrupt()
        if (t !== Thread.currentThread()) {
            try { t.join(1200L) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        }
        if (!t.isAlive) worker = null
    }

    fun setEra(newEra: Int) {
        era = if (newEra == MENU) MENU else newEra.coerceIn(0, 7)
    }

    fun setIntensity(value: Float) {
        intensity = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
    }

    fun setVolume(value: Float) {
        volume = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
    }

    private fun stream() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        val synth = MusicSynth()
        val pcm = ShortArray(CHUNK_SAMPLES)
        val bytesPerSample = 2
        val minBytes = AudioTrack.getMinBufferSize(
            MusicSynth.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBytes <= 0) return
        val bufferBytes = (maxOf(minBytes, MusicSynth.SAMPLE_RATE * bytesPerSample / 4) + 1) and -2
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
            if (track.state != AudioTrack.STATE_INITIALIZED || !running) return
            track.play()
            while (running) {
                synth.setEra(era)
                synth.setIntensity(intensity)
                synth.renderPcm(pcm, pcm.size, volume)
                var written = 0
                while (written < pcm.size && running) {
                    val n = track.write(pcm, written, pcm.size - written, AudioTrack.WRITE_BLOCKING)
                    if (n <= 0) return
                    written += n
                }
            }
        } catch (_: RuntimeException) {
            // Brak wyjścia audio albo nieobsługiwany format nie zatrzymuje gry.
        } finally {
            track?.let {
                try { if (it.playState == AudioTrack.PLAYSTATE_PLAYING) it.stop() } catch (_: IllegalStateException) { }
                it.release()
            }
        }
    }
}
