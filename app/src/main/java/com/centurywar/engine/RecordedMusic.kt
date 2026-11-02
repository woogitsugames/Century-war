package com.centurywar.engine

import android.content.res.AssetManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Process
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Nagrana muzyka na własnym wątku sterującym: otwieranie plików (MediaPlayer.prepare),
 * głośności i przenikania nigdy nie dzieją się na wątku gry. Wątek żyje tylko,
 * gdy muzyka ma grać ([setActive]); zatrzymanie zwalnia wszystkie odtwarzacze.
 */
internal class RecordedMusic(
    catalog: MusicCatalog,
    private val factory: TrackPlayerFactory,
    private val onBroken: (String) -> Unit,
    /** Bieżące wyciszenie efektów 0..1 (1 = brak). */
    private val duckLevel: () -> Float,
) {
    companion object {
        private const val TICK_MS = 40L
        private const val STINGER_NONE = 0
        private const val STINGER_VICTORY = 1
        private const val STINGER_DEFEAT = 2
    }

    private val catalogRef = catalog
    @Volatile var target = RecordedMixer.NONE
    @Volatile var intensity = 0f
    @Volatile var volume = 0.5f
    private val stingerRequest = AtomicInteger(STINGER_NONE)
    @Volatile private var worker: Thread? = null

    val isActive: Boolean get() = worker != null

    @Synchronized
    fun setActive(on: Boolean) {
        val t = worker
        if (on && t == null) {
            worker = Thread({ run() }, "music-recorded").apply {
                priority = Thread.MIN_PRIORITY
                isDaemon = true
                start()
            }
        } else if (!on && t != null) {
            worker = null
            stingerRequest.set(STINGER_NONE)
            t.interrupt()
        }
    }

    fun requestStinger(victory: Boolean) {
        stingerRequest.set(if (victory) STINGER_VICTORY else STINGER_DEFEAT)
    }

    /** Tylko testy: czeka na zakończenie wskazanego wątku. */
    internal fun workerForTest(): Thread? = worker

    private fun run() {
        val me = Thread.currentThread()
        try { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND) } catch (_: RuntimeException) { }
        val mixer = RecordedMixer(catalogRef, factory, onBroken)
        var last = System.nanoTime()
        try {
            while (worker === me) {
                val now = System.nanoTime()
                val dt = ((now - last) / 1e9f).coerceIn(0f, 0.25f)
                last = now
                when (stingerRequest.getAndSet(STINGER_NONE)) {
                    STINGER_VICTORY -> mixer.playStinger(true)
                    STINGER_DEFEAT -> mixer.playStinger(false)
                }
                mixer.update(dt, target, intensity, volume, duckLevel())
                Thread.sleep(TICK_MS)
            }
        } catch (_: InterruptedException) {
            // setActive(false): normalne zakończenie.
        } catch (_: RuntimeException) {
            // Błąd odtwarzacza nie zatrzymuje gry.
        } finally {
            mixer.releaseAll()
        }
    }
}

/** MediaPlayer z pliku w assets (bez kompresji w APK, więc openFd działa). */
internal class MediaTrackFactory(private val assets: AssetManager) : TrackPlayerFactory {
    override fun open(name: String, looping: Boolean): TrackPlayer? {
        var mp: MediaPlayer? = null
        return try {
            val player = MediaPlayer()
            mp = player
            player.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            assets.openFd(MusicCatalog.path(name)).use { afd ->
                player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
            player.isLooping = looping
            player.setVolume(0f, 0f)
            player.prepare()
            MediaTrack(player)
        } catch (_: IOException) {
            mp?.release(); null
        } catch (_: RuntimeException) {
            mp?.release(); null
        }
    }

    private class MediaTrack(private val mp: MediaPlayer) : TrackPlayer {
        private var released = false
        private var lastVolume = -1f
        override fun start() { mp.start() }
        override fun setVolume(value: Float) {
            if (released || kotlin.math.abs(value - lastVolume) < 0.001f) return
            lastVolume = value
            mp.setVolume(value, value)
        }
        override fun release() {
            if (released) return
            released = true
            try { mp.stop() } catch (_: RuntimeException) { }
            mp.release()
        }
        override val isPlaying: Boolean
            get() = !released && try { mp.isPlaying } catch (_: RuntimeException) { false }
    }
}
