package com.centurywar.engine

import kotlin.math.exp

/** Odtwarzacz jednego pliku (na urządzeniu MediaPlayer, w testach atrapa). */
interface TrackPlayer {
    fun start()
    fun setVolume(value: Float)
    fun release()
    /** Fałsz po zakończeniu utworu bez pętli. */
    val isPlaying: Boolean
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
) {
    companion object {
        const val NONE = Int.MIN_VALUE
        const val ERA_FADE_S = 2f
        const val MIX_SMOOTH_S = 1.5f
        const val DUCK_ATTACK_S = 0.06f
        const val DUCK_RELEASE_S = 0.5f
        const val STINGER_MUTE_S = 0.4f
        const val STINGER_RETURN_S = 1.5f

        private fun smoothstep(a: Float, b: Float, x: Float): Float {
            val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }

        /** Docelowy udział wersji bojowej dla intensywności 0..1. */
        fun battleWeight(intensity: Float): Float =
            if (intensity.isFinite()) smoothstep(0.25f, 0.75f, intensity) else 0f
    }

    private class Group(val key: Int, val calm: TrackPlayer?, val battle: TrackPlayer?) {
        var fade = 0f
        fun release() { calm?.release(); battle?.release() }
        val size: Int get() = (if (calm != null) 1 else 0) + (if (battle != null) 1 else 0)
    }

    private var current: Group? = null
    private var outgoing: Group? = null
    private var stinger: TrackPlayer? = null
    /** Ostatni żądany cel; nieudane otwarcie nie jest ponawiane co krok. */
    private var requested = NONE

    /** Wygładzony udział wersji bojowej. */
    var mix = 0f
        private set
    var duckLevel = 1f
        private set
    /** Mnożnik pętli podczas przerywnika (0 = wyciszone). */
    var loopLevel = 1f
        private set

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

        current?.let { it.fade = (it.fade + step / ERA_FADE_S).coerceAtMost(1f) }
        outgoing?.let {
            it.fade -= step / ERA_FADE_S
            if (it.fade <= 0f) { it.release(); outgoing = null }
        }

        val wantMix = battleWeight(intensity)
        val maxMove = step / MIX_SMOOTH_S
        mix += (wantMix - mix).coerceIn(-maxMove, maxMove)

        val duck = if (duckTarget.isFinite()) duckTarget.coerceIn(0f, 1f) else 1f
        val tau = if (duck < duckLevel) DUCK_ATTACK_S else DUCK_RELEASE_S
        duckLevel += (duck - duckLevel) * (1f - exp(-step / tau))
        if (step > 0f && kotlin.math.abs(duck - duckLevel) < 0.002f) duckLevel = duck

        val s = stinger
        if (s != null && !s.isPlaying) { s.release(); stinger = null }
        loopLevel = if (stinger != null) {
            (loopLevel - step / STINGER_MUTE_S).coerceAtLeast(0f)
        } else (loopLevel + step / STINGER_RETURN_S).coerceAtMost(1f)

        val vol = if (volume.isFinite()) volume.coerceIn(0f, 1f) else 0f
        val base = vol * duckLevel * loopLevel
        current?.let { apply(it, base) }
        outgoing?.let { apply(it, base) }
        stinger?.setVolume(vol)
    }

    private fun switchTo(target: Int) {
        val out = outgoing
        if (out != null && out.key == target && target != NONE) {
            // Powrót do poprzedniej pary w trakcie przenikania: zamiana ról bez otwierania plików.
            outgoing = current
            current = out
            return
        }
        out?.release()
        outgoing = null
        var prev = current
        // W trakcie przerywnika pętla jest wyciszona: zwalniamy ją przed otwarciem nowej (limit 4).
        if (prev != null && stinger != null) { prev.release(); prev = null; current = null }
        current = openGroup(target)
        if (prev != null) outgoing = prev
        else current?.fade = 1f // nic nie gra w tle: start bez przenikania (plik ma własne wejście)
    }

    private fun apply(g: Group, base: Float) {
        val gain = base * g.fade
        val calm = g.calm
        val battle = g.battle
        if (calm != null && battle != null) {
            calm.setVolume(gain * (1f - mix))
            battle.setVolume(gain * mix)
        } else {
            calm?.setVolume(gain)
            battle?.setVolume(gain)
        }
    }

    /** Pauza/zwolnienie: wszystkie odtwarzacze zwolnione, następny start od początku utworu. */
    fun releaseAll() {
        current?.release(); current = null
        outgoing?.release(); outgoing = null
        stinger?.release(); stinger = null
        requested = NONE
        loopLevel = 1f
        duckLevel = 1f
    }
}
