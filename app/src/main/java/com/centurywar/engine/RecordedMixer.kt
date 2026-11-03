package com.centurywar.engine

import kotlin.math.exp

/** Odtwarzacz jednego pliku (na urządzeniu MediaPlayer, w testach atrapa). */
interface TrackPlayer {
    fun start()
    fun setVolume(value: Float)
    fun release()
    /** Fałsz po zakończeniu utworu bez pętli. */
    val isPlaying: Boolean
    /** Wołane co krok miksu na wątku sterującym (np. przygotowanie następnego okrążenia pętli). */
    fun maintain() {}
}

/** Otwiera i przygotowuje utwór; null = nie da się odtworzyć. Wołana poza wątkiem gry. */
fun interface TrackPlayerFactory {
    fun open(name: String, looping: Boolean): TrackPlayer?
}

/**
 * Logika miksu nagrań, bez wątków i zegara: menu = jedna pętla, epoka = para
 * spokojna/bojowa przenikana według intensywności, zmiana epoki = przenikanie par,
 * przerywnik zwycięstwa/porażki wycisza pętle. Najwyżej 4 odtwarzacze naraz.
 */
internal class RecordedMixer(
    private val catalog: MusicCatalog,
    private val factory: TrackPlayerFactory,
    private val onBroken: (String) -> Unit = {},
    /** Mnożnik pętli względem suwaka (na urządzeniu [LOOP_GAIN]; testy logiki: 1). */
    private val loopGain: Float = 1f,
    /** Mnożnik przerywnika względem suwaka (na urządzeniu [STINGER_GAIN]). */
    private val stingerGain: Float = 1f,
) {
    companion object {
        const val NONE = Int.MIN_VALUE
        const val ERA_FADE_S = 2f
        /**
         * Poziom nagrań względem efektów. Pliki mają -16 LUFS, a efekty bitwy (SoundSynth przez
         * Sound, suwak efektów 0,8) dają ok. -35 LUFS; MediaPlayer.setVolume jest liniowe. Przy
         * suwaku muzyki 0,5 pętla ma więc -16 - 6 - 20 = ok. -42 LUFS, czyli ~7 dB pod efektami
         * (pomiar w docs/music/README.md). Suwak 1,0 = -36 LUFS.
         */
        const val LOOP_GAIN = 0.1f
        /** Przerywnik wyniku zastępuje efekt zwycięstwa/porażki: +6 dB nad pętlą. */
        const val STINGER_GAIN = 0.2f
        /**
         * Start pętli z ciszy. Pliki nie mają już własnego wejścia (pętla jest zszyta
         * crossfade'em w pliku i zaczyna się w środku frazy), więc wejście robi mikser.
         */
        const val START_FADE_S = 0.6f
        const val DUCK_ATTACK_S = 0.06f
        const val DUCK_RELEASE_S = 0.5f
        const val STINGER_MUTE_S = 0.4f
        /** Cisza po końcu przerywnika, zanim pętla zacznie wracać. */
        const val STINGER_GAP_S = 0.6f
        /** Powrót pętli po przerywniku (krzywa równej mocy). */
        const val STINGER_RETURN_S = 3f

        private fun smoothstep(a: Float, b: Float, x: Float): Float {
            val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }

        /** Docelowy udział wersji bojowej dla intensywności 0..1. */
        fun battleWeight(intensity: Float): Float =
            if (intensity.isFinite()) smoothstep(0.25f, 0.75f, intensity) else 0f

        /**
         * Wzmocnienie równej mocy dla postępu 0..1 (sin ćwiartki): dwie nieskorelowane
         * nagrania w połowie przenikania dają tę samą moc co jedno (liniowe dawało −3 dB).
         */
        fun equalPower(x: Float): Float = kotlin.math.sin(x.coerceIn(0f, 1f) * (Math.PI.toFloat() / 2f))
    }

    private class Group(val key: Int, val calm: TrackPlayer?, val battle: TrackPlayer?) {
        var fade = 0f
        /** Czas narastania [fade] w sekundach (przenikanie epok albo start z ciszy). */
        var fadeInS = ERA_FADE_S
        fun release() { calm?.release(); battle?.release() }
        val size: Int get() = (if (calm != null) 1 else 0) + (if (battle != null) 1 else 0)
    }

    private var current: Group? = null
    private var outgoing: Group? = null
    private var stinger: TrackPlayer? = null
    /** Ostatni żądany cel; nieudane otwarcie nie jest ponawiane co krok. */
    private var requested = NONE

    private val follower = BattleMixFollower()
    /** Wygładzony udział wersji bojowej (z histerezą, [BattleMixFollower]). */
    val mix: Float get() = follower.value
    var duckLevel = 1f
        private set
    /** Postęp powrotu pętli po przerywniku 0..1; 0 = wyciszona. */
    private var loopProgress = 1f
    /** Czas od końca przerywnika (cisza [STINGER_GAP_S] przed powrotem pętli). */
    private var sinceStinger = Float.MAX_VALUE
    /** Mnożnik pętli podczas przerywnika i po nim (0 = wyciszone), gładka krzywa S. */
    val loopLevel: Float get() = (1f - kotlin.math.cos(loopProgress * Math.PI.toFloat())) * 0.5f

    val openPlayers: Int get() = (current?.size ?: 0) + (outgoing?.size ?: 0) + (if (stinger != null) 1 else 0)
    val currentKey: Int get() = current?.key ?: NONE
    val stingerPlaying: Boolean get() = stinger != null

    private fun open(name: String, looping: Boolean): TrackPlayer? {
        val p = try { factory.open(name, looping) } catch (_: RuntimeException) { null }
        if (p == null) {
            catalog.markBroken(name)
            onBroken(name)
        }
        return p
    }

    private fun openGroup(key: Int): Group? {
        if (key == NONE || !catalog.covers(key)) return null
        val g = if (key == Music.MENU) {
            Group(key, open(MusicCatalog.MENU_TRACK, true), null)
        } else Group(
            key,
            if (catalog.hasCalm(key)) open(MusicCatalog.calmName(key), true) else null,
            if (catalog.hasBattle(key)) open(MusicCatalog.battleName(key), true) else null,
        )
        if (g.size == 0) return null
        // Obie wersje startują razem, na razie w ciszy.
        g.calm?.setVolume(0f); g.battle?.setVolume(0f)
        g.calm?.start(); g.battle?.start()
        return g
    }

    /** Jednorazowy przerywnik; false, gdy brak pliku. */
    fun playStinger(victory: Boolean): Boolean {
        val name = if (victory) MusicCatalog.VICTORY_TRACK else MusicCatalog.DEFEAT_TRACK
        if (!catalog.has(name)) return false
        stinger?.release()
        stinger = null
        // Wyciszona pętla wychodząca nie jest potrzebna: limit 4 odtwarzaczy.
        outgoing?.release(); outgoing = null
        val p = open(name, false) ?: return false
        p.setVolume(0f)
        p.start()
        stinger = p
        return true
    }

    /**
     * Jeden krok miksu. [target]: epoka, [Music.MENU] albo [NONE] (gra synteza).
     * [duckTarget] 0..1 z bieżącego wyciszenia efektów.
     */
    fun update(dt: Float, target: Int, intensity: Float, volume: Float, duckTarget: Float) {
        val step = if (dt.isFinite()) dt.coerceIn(0f, 0.5f) else 0f
        if (target != requested) {
            requested = target
            switchTo(target)
        }

        current?.let { it.fade = (it.fade + step / it.fadeInS).coerceAtMost(1f) }
        outgoing?.let {
            it.fade -= step / ERA_FADE_S
            if (it.fade <= 0f) { it.release(); outgoing = null }
        }

        follower.update(step, battleWeight(intensity))

        val duck = if (duckTarget.isFinite()) duckTarget.coerceIn(0f, 1f) else 1f
        val tau = if (duck < duckLevel) DUCK_ATTACK_S else DUCK_RELEASE_S
        duckLevel += (duck - duckLevel) * (1f - exp(-step / tau))
        if (step > 0f && kotlin.math.abs(duck - duckLevel) < 0.002f) duckLevel = duck

        val s = stinger
        if (s != null && !s.isPlaying) { s.release(); stinger = null; sinceStinger = 0f }
        if (stinger != null) {
            loopProgress = (loopProgress - step / STINGER_MUTE_S).coerceAtLeast(0f)
        } else {
            // Pętla wraca dopiero po krótkiej ciszy, żeby nie nachodzić na wybrzmienie przerywnika.
            if (sinceStinger < STINGER_GAP_S) sinceStinger += step
            else loopProgress = (loopProgress + step / STINGER_RETURN_S).coerceAtMost(1f)
        }

        val vol = if (volume.isFinite()) volume.coerceIn(0f, 1f) else 0f
        val base = vol * loopGain * duckLevel * loopLevel
        current?.let { apply(it, base) }
        outgoing?.let { apply(it, base) }
        stinger?.setVolume(vol * stingerGain)
    }

    private fun switchTo(target: Int) {
        val out = outgoing
        if (out != null && out.key == target && target != NONE) {
            // Powrót do poprzedniej pary w trakcie przenikania: zamiana ról bez otwierania plików.
            outgoing = current
            current = out
            out.fadeInS = ERA_FADE_S
            return
        }
        out?.release()
        outgoing = null
        var prev = current
        // W trakcie przerywnika pętla jest wyciszona: zwalniamy ją przed otwarciem nowej (limit 4).
        if (prev != null && stinger != null) { prev.release(); prev = null; current = null }
        current = openGroup(target)
        if (prev != null) outgoing = prev
        else current?.fadeInS = START_FADE_S // nic nie gra w tle: krótkie wejście z ciszy
    }

    private fun apply(g: Group, base: Float) {
        g.calm?.maintain(); g.battle?.maintain()
        val gain = base * equalPower(g.fade)
        val calm = g.calm
        val battle = g.battle
        if (calm != null && battle != null) {
            val m = mix
            calm.setVolume(gain * equalPower(1f - m))
            battle.setVolume(gain * equalPower(m))
        } else {
            calm?.setVolume(gain)
            battle?.setVolume(gain)
        }
    }

    /**
     * R2-05 (brak pamięci w tle): zwalnia odtwarzacze, które nie grają: wygasającą parę
     * i zakończony przerywnik. Bieżąca para gra dalej bez przerwy.
     */
    fun releaseIdle() {
        outgoing?.release(); outgoing = null
        val s = stinger
        if (s != null && !s.isPlaying) { s.release(); stinger = null; sinceStinger = 0f }
    }

    /** Pauza/zwolnienie: wszystkie odtwarzacze zwolnione, następny start od początku utworu. */
    fun releaseAll() {
        current?.release(); current = null
        outgoing?.release(); outgoing = null
        stinger?.release(); stinger = null
        requested = NONE
        loopProgress = 1f
        sinceStinger = Float.MAX_VALUE
        duckLevel = 1f
        follower.reset()
    }
}

/**
 * Udział wersji bojowej z histerezą: wzrost napięcia podąża szybko ([RISE_S] na pełną
 * skalę), spadek dopiero po [HOLD_S] bez nowego szczytu, potem powoli ([FALL_S]).
 * Krótkie przerwy w walce (fala zginęła, następna za chwilę) nie „pompują” miksu.
 */
internal class BattleMixFollower {
    companion object {
        const val RISE_S = 1.5f
        const val HOLD_S = 7f
        const val FALL_S = 4f
        /** Spadek celu o mniej niż tyle nie zdejmuje podtrzymania. */
        const val HOLD_TOLERANCE = 0.15f
    }

    /** Bieżący udział 0..1. */
    var value = 0f
        private set
    private var held = 0f
    private var hold = 0f
    /** Podtrzymanie minęło i miks schodzi; tolerancja już go nie zatrzymuje. */
    private var falling = false

    fun update(dt: Float, want: Float) {
        val w = if (want.isFinite()) want.coerceIn(0f, 1f) else 0f
        if (w >= held) {
            held = w
            hold = HOLD_S
            falling = false
        } else if (!falling && w >= held - HOLD_TOLERANCE) {
            hold = HOLD_S
        } else if (!falling && hold > 0f) {
            hold -= dt
            if (hold <= 0f) falling = true
        } else {
            falling = true
            held = maxOf(w, held - dt / FALL_S)
        }
        value = if (held > value) minOf(held, value + dt / RISE_S) else held
    }

    fun reset() { value = 0f; held = 0f; hold = 0f; falling = false }
}
