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
    private val trimRequest = java.util.concurrent.atomic.AtomicBoolean(false)
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

    /** Wykonywane przez wątek nagrań; bez wątku odtwarzacze są już zwolnione. */
    fun requestTrim() {
        if (worker != null) trimRequest.set(true)
    }

    fun requestStinger(victory: Boolean) {
        stingerRequest.set(if (victory) STINGER_VICTORY else STINGER_DEFEAT)
    }

    /** Tylko testy: czeka na zakończenie wskazanego wątku. */
    internal fun workerForTest(): Thread? = worker

    private fun run() {
        val me = Thread.currentThread()
        try { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND) } catch (_: RuntimeException) { }
        val mixer = RecordedMixer(catalogRef, factory, onBroken, RecordedMixer.LOOP_GAIN, RecordedMixer.STINGER_GAIN)
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
                if (trimRequest.getAndSet(false)) mixer.releaseIdle()
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

/**
 * MediaPlayer z pliku w assets (bez kompresji w APK, więc openFd działa).
 *
 * Pętle nie używają `setLooping`: na wielu urządzeniach NuPlayer zapętla przez opróżnienie
 * bufora i `seekTo(0)`, co daje kilkadziesiąt ms ciszy na złączeniu. Zamiast tego przed
 * końcem okrążenia przygotowujemy drugi odtwarzacz tego samego pliku i łączymy je przez
 * `setNextMediaPlayer` (MediaPlayerService przekazuje temu samemu AudioTrackowi dane
 * następnego odtwarzacza, bez przerwy; API 16+, ta sama ścieżka od Androida 8 do 15).
 * Gdy to się nie uda, odtwarzacz wraca do `setLooping(true)`.
 */
internal class MediaTrackFactory(private val assets: AssetManager) : TrackPlayerFactory {
    companion object {
        /** Następne okrążenie przygotowujemy tyle przed końcem bieżącego. */
        const val PREPARE_AHEAD_MS = 12_000
    }

    private fun prepare(name: String): MediaPlayer? {
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
            player.setVolume(0f, 0f)
            player.prepare()
            player
        } catch (_: IOException) {
            mp?.release(); null
        } catch (_: RuntimeException) {
            mp?.release(); null
        }
    }

    override fun open(name: String, looping: Boolean): TrackPlayer? {
        // Pętla zaczyna bez setLooping; w razie kłopotu z łańcuchem włącza go sama.
        val mp = prepare(name) ?: return null
        return if (looping) {
            ChainedLoop(MediaSegment(mp), PREPARE_AHEAD_MS) { prepare(name)?.let { MediaSegment(it) } }
        } else MediaTrack(mp)
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

    /** Okrążenie pętli na MediaPlayerze. */
    private class MediaSegment(val mp: MediaPlayer) : LoopSegment {
        override fun start() = mp.start()
        override fun setVolume(value: Float) = mp.setVolume(value, value)
        override val isPlaying: Boolean get() = mp.isPlaying
        override val remainingMs: Int get() = mp.duration.let { if (it <= 0) Int.MAX_VALUE else it - mp.currentPosition }
        override fun chain(next: LoopSegment?) = mp.setNextMediaPlayer((next as MediaSegment?)?.mp)
        override fun loopInPlace() { mp.isLooping = true }
        override fun restart() { mp.seekTo(0); mp.start() }
        override fun release() {
            try { mp.stop() } catch (_: RuntimeException) { }
            mp.release()
        }
    }
}

/** Jedno okrążenie pętli ([ChainedLoop]); na urządzeniu MediaPlayer, w testach atrapa. */
internal interface LoopSegment {
    fun start()
    fun setVolume(value: Float)
    val isPlaying: Boolean
    /** Ile ms zostało do końca okrążenia. */
    val remainingMs: Int
    /** setNextMediaPlayer: [next] zacznie grać bez przerwy po końcu tego okrążenia. */
    fun chain(next: LoopSegment?)
    /** Zapasowo: zwykłe setLooping(true). */
    fun loopInPlace()
    /** Od początku pliku (gdy następnik się nie uruchomił). */
    fun restart()
    fun release()
}

/**
 * Pętla bez przerwy z łańcucha okrążeń: [aheadMs] przed końcem bieżącego okrążenia otwiera
 * następne i łączy je przez [LoopSegment.chain]; po przejściu zwalnia stare. Najwyżej dwa
 * odtwarzacze naraz i tylko pod koniec okrążenia. Każdy błąd przełącza na setLooping(true).
 * Wszystkie wywołania z jednego wątku (sterującego muzyką), przez [maintain] co krok miksu.
 */
internal class ChainedLoop(
    first: LoopSegment,
    private val aheadMs: Int,
    private val reopen: () -> LoopSegment?,
) : TrackPlayer {
    private var seg = first
    private var next: LoopSegment? = null
    private var released = false
    private var started = false
    private var volume = 0f
    /** Łańcuch zawiódł: dalej zwykłe setLooping(true). */
    var fallback = false
        private set
    /** Ile razy pętla przeszła na następne okrążenie (testy, diagnostyka). */
    var laps = 0
        private set

    override fun start() {
        started = true
        seg.start()
    }

    override fun setVolume(value: Float) {
        if (released || kotlin.math.abs(value - volume) < 0.001f) return
        volume = value
        seg.setVolume(value)
        next?.setVolume(value)
    }

    override fun maintain() {
        if (released || !started || fallback) return
        try {
            val n = next
            if (n == null) {
                if (seg.remainingMs < aheadMs) chainNext()
                else if (!seg.isPlaying) seg.restart() // koniec bez następnika (nie powinno się zdarzyć)
            } else if (!seg.isPlaying) {
                // Okrążenie skończone: następnik już gra (albo uruchamiamy go sami, gdy system nie zdążył).
                if (!n.isPlaying) n.start()
                val old = seg
                seg = n
                next = null
                laps++
                try { old.release() } catch (_: RuntimeException) { }
            }
        } catch (_: RuntimeException) {
            toFallback()
        }
    }

    private fun chainNext() {
        val n = reopen()
        if (n == null) { toFallback(); return }
        try {
            n.setVolume(volume)
            seg.chain(n)
            next = n
        } catch (_: RuntimeException) {
            try { n.release() } catch (_: RuntimeException) { }
            toFallback()
        }
    }

    private fun toFallback() {
        fallback = true
        next?.let {
            try { seg.chain(null) } catch (_: RuntimeException) { }
            try { it.release() } catch (_: RuntimeException) { }
        }
        next = null
        try {
            seg.loopInPlace()
            if (!seg.isPlaying) seg.restart()
        } catch (_: RuntimeException) { }
    }

    override fun release() {
        if (released) return
        released = true
        for (p in listOfNotNull(next, seg)) {
            try { p.release() } catch (_: RuntimeException) { }
        }
        next = null
    }

    override val isPlaying: Boolean get() = !released
}
