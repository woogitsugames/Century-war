package com.centurywar.engine

import android.content.res.AssetManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import android.os.SystemClock

/**
 * Muzyka gry: nagranie z `assets/music` ([MusicCatalog]), gdy jest dla bieżącego menu/epoki,
 * w przeciwnym razie strumieniowa muzyka proceduralna; cała synteza i AudioTrack działają na własnym wątku
 * o niskim priorytecie. Wątek żyje tylko, gdy Activity jest na pierwszym planie
 * ([resume]/[pause]) i głośność > 0, więc wyłączona muzyka nie kosztuje CPU.
 * Sterowanie ([setEra], [setIntensity], [setVolume]) jest bezpieczne z dowolnego wątku.
 */
class Music internal constructor(
    private val catalog: MusicCatalog,
    factory: TrackPlayerFactory?,
    private val audio: AudioManager?,
    private val minBufferSize: (Int, Int, Int) -> Int = AudioTrack::getMinBufferSize,
) {
    /** Bez [assets] (testy) zachowanie jak dotąd: wyłącznie synteza. */
    constructor(assets: AssetManager? = null, audio: AudioManager? = null) : this(
        MusicCatalog.fromAssets(assets), assets?.let { MediaTrackFactory(it) }, audio
    )

    companion object {
        const val MENU = MusicSynth.MENU
        private const val CHUNK_SAMPLES = 2048
        /** Sen, gdy bufor AudioTrack jest pełny (~1/3 porcji). */
        private const val FULL_SLEEP_MS = 15L
    }

    @Volatile private var era = MENU
    @Volatile private var intensity = 0f
    @Volatile private var themeSeed = 0x43A72F19
    @Volatile private var volume = 0.5f
    private val duckPriority = DuckPriority()
    @Volatile private var duckSerial = 0
    private var foreground = false
    private var released = false
    /** Bieżący wątek; stary wątek kończy pracę, gdy przestaje być bieżącym. */
    @Volatile private var worker: Thread? = null

    private val recorded: RecordedMusic? = if (factory == null || catalog.isEmpty) null else
        RecordedMusic(catalog, factory, { refreshSource() }, { duckLevelNow() })
    /** Czy bieżący utwór (menu/epoka) gra z nagrania. */
    @Volatile var usesRecorded = recorded != null && catalog.covers(MENU)
        private set

    private var focusLost = false
    private var focusHeld = false
    /**
     * Decyzja właściciela 3.11: gdy przy wejściu do gry gra już cudza muzyka (np. Spotify),
     * muzyka gry milknie i nie zabiera fokusu; efekty dźwiękowe grają dalej. Sprawdzane przy każdym
     * powrocie na pierwszy plan.
     */
    private var yieldToOther = false
    private val focusRequest: AudioFocusRequest? = audio?.let {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener { change -> onFocusChange(change) }
            .build()
    }

    /** Activity wróciła na pierwszy plan. */
    @Synchronized
    fun resume() {
        foreground = true
        // Po trwałej utracie fokusu powrót do gry prosi o niego ponownie.
        if (focusLost) { focusLost = false; focusHeld = false }
        yieldToOther = !focusHeld && otherAppPlaying()
        sync()
    }

    /** Activity schodzi w tło: dźwięk milknie natychmiast, wątek kończy się sam. */
    @Synchronized
    fun pause() {
        foreground = false
        sync()
    }

    /**
     * R2-05: przy TRIM_MEMORY_BACKGROUND wątek nagrań zwalnia odtwarzacze, które nie grają
     * (wygasająca para, zakończony przerywnik). Po pauzie wątek i tak zwolnił wszystko,
     * a [resume] otwiera bieżący utwór od nowa, więc wznowienie się nie zmienia.
     */
    fun trimMemory() {
        recorded?.requestTrim()
    }

    /** Koniec życia Activity; po tym muzyka już nie wystartuje. */
    @Synchronized
    fun release() {
        released = true
        foreground = false
        sync()
    }

    fun setEra(newEra: Int) {
        // Motyw odpowiada rzeczywistej epoce, również dla epoki żelaza.
        val e = if (newEra == MENU) MENU else newEra.coerceIn(0, com.centurywar.game.Defs.ERA_COUNT - 1)
        if (e == era) return
        era = e
        // Wołane co klatkę: źródło przeliczamy tylko przy zmianie i tylko, gdy są nagrania.
        if (recorded != null) refreshSource()
    }

    /** Ziarno aranżacji pojedynczej bitwy, niezależne od symulacji. */
    fun setThemeSeed(seed: Int) {
        themeSeed = seed
    }

    fun setIntensity(value: Float) {
        intensity = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
        recorded?.intensity = intensity
    }

    @Synchronized
    fun setVolume(value: Float) {
        volume = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
        recorded?.volume = volume
        sync()
    }

    /** Słabsze zdarzenie nie skraca silniejszego wyciszenia; wątek audio odbiera tylko zmianę. */
    @Synchronized
    fun duck(amount: Float, seconds: Float) {
        if (duckPriority.request(amount, seconds, SystemClock.uptimeMillis())) duckSerial++
    }

    val isPlaying: Boolean get() = worker != null || (usesRecorded && recorded?.isActive == true)

    /**
     * Przerywnik zwycięstwa/porażki z nagrania; pętla cichnie na jego czas.
     * False, gdy brak pliku albo muzyka nie gra (wtedy ekran zostaje przy dotychczasowym efekcie).
     */
    @Synchronized
    fun playResultStinger(victory: Boolean): Boolean {
        val rec = recorded ?: return false
        val name = if (victory) MusicCatalog.VICTORY_TRACK else MusicCatalog.DEFEAT_TRACK
        if (!catalog.has(name) || !rec.isActive) return false
        rec.requestStinger(victory)
        // Synteza (gdy to ona gra pętlę) też schodzi w tło na czas przerywnika.
        if (duckPriority.request(0.12f, 12f, SystemClock.uptimeMillis())) duckSerial++
        return true
    }

    @Synchronized
    private fun refreshSource() { sync() }

    @Synchronized
    private fun duckLevelNow(): Float =
        if (duckPriority.remainingSeconds(SystemClock.uptimeMillis()) > 0f) duckPriority.amount else 1f

    @Synchronized
    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> focusLost = true
            AudioManager.AUDIOFOCUS_GAIN -> focusLost = false
            else -> return // LOSS_TRANSIENT_CAN_DUCK: system sam ścisza strumień.
        }
        sync()
    }

    private fun otherAppPlaying(): Boolean =
        try { audio?.isMusicActive == true } catch (_: RuntimeException) { false }

    /** Fokus audio trzymany tylko, gdy muzyka ma grać; bez AudioManagera (testy) pomijany. */
    private fun syncFocus(want: Boolean) {
        val am = audio ?: return
        val req = focusRequest ?: return
        try {
            if (want && !focusHeld && !focusLost) {
                focusHeld = true
                if (am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_FAILED) focusLost = true
            } else if (!want && focusHeld) {
                focusHeld = false
                focusLost = false
                am.abandonAudioFocusRequest(req)
            }
        } catch (_: RuntimeException) { }
    }

    private fun sync() {
        val base = foreground && !released && volume > 0f && !yieldToOther
        syncFocus(base)
        val want = base && !focusLost
        val rec = recorded
        val fromFile = rec != null && catalog.covers(era)
        usesRecorded = fromFile
        if (rec != null) {
            rec.volume = volume
            rec.intensity = intensity
            rec.target = if (fromFile) era else RecordedMixer.NONE
            // Wątek nagrań działa przy każdym źródle: wygasza parę i odtwarza przerywniki.
            rec.setActive(want)
        }
        syncSynth(want && !fromFile)
    }

    private fun syncSynth(want: Boolean) {
        val t = worker
        if (want && t == null) {
            val next = Thread({ stream() }, "music-stream").apply {
                priority = Thread.MIN_PRIORITY
                isDaemon = true
            }
            worker = next
            try {
                next.start()
            } catch (e: RuntimeException) {
                if (worker === next) worker = null
                throw e
            }
        } else if (!want && t != null) {
            worker = null
            t.interrupt()
        }
    }

    private fun stream() {
        val me = Thread.currentThread()
        var track: AudioTrack? = null
        try {
            try { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND) } catch (_: RuntimeException) { }
            val synth = MusicSynth()
            synth.setSeed(themeSeed)
            synth.setEra(era)
            synth.skipFade()
            val pcm = ShortArray(CHUNK_SAMPLES)
            val minBytes = minBufferSize(
                MusicSynth.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBytes <= 0) return
            // Co najmniej 500 ms bufora sprzętowego (PCM16 mono = 2 bajty na próbkę).
            val bufferBytes = (maxOf(minBytes, MusicSynth.SAMPLE_RATE * 2 / 2) + 1) and -2
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
                synth.setSeed(themeSeed)
                synth.setEra(era)
                synth.setIntensity(intensity)
                synth.renderPcm(pcm, pcm.size, volume)
                var written = 0
                // Zapis nieblokujący: pauza nie czeka na zwolnienie bufora sprzętowego.
                while (written < pcm.size && worker === me) {
                    val n = track.write(pcm, written, pcm.size - written, AudioTrack.WRITE_NON_BLOCKING)
                    if (n < 0) return
                    if (n == 0) Thread.sleep(FULL_SLEEP_MS) else written += n
                }
            }
        } catch (_: InterruptedException) {
            // pause()/release(): normalne zakończenie.
        } catch (_: RuntimeException) {
            // Błąd AudioTrack: następne resume()/setVolume() może ponowić próbę.
        } finally {
            track?.let {
                try { it.pause(); it.flush() } catch (_: RuntimeException) { }
                try { it.release() } catch (_: RuntimeException) { }
            }
            synchronized(this) {
                // Spóźniony worker nie może wyczyścić referencji do nowego utworu.
                if (worker === me) worker = null
            }
        }
    }
}
