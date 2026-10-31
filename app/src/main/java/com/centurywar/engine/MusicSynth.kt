package com.centurywar.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

/** Syntezator bez zależności od Androida. Wywołania generujące próbki należą do jednego wątku. */
class MusicSynth(private val seed: Int = 0x43A72F19) {
    companion object {
        const val SAMPLE_RATE = 22_050
        const val MENU = -1
        const val CROSSFADE_SAMPLES = SAMPLE_RATE * 2
        private const val TABLE_SIZE = 512
        private const val TABLE_MASK = TABLE_SIZE - 1

        private const val SINE = 0
        private const val TRIANGLE = 1
        private const val SAW = 2
        private const val PULSE = 3

        private val waves = Array(4) { type ->
            FloatArray(TABLE_SIZE) { i ->
                val x = i.toFloat() / TABLE_SIZE
                when (type) {
                    SINE -> sin(2.0 * PI * x).toFloat()
                    TRIANGLE -> 1f - 4f * abs(x - 0.5f)
                    SAW -> 2f * x - 1f
                    else -> if (x < 0.5f) 1f else -1f
                }
            }
        }
        private val noteStep = FloatArray(128) { midi ->
            (440.0 * 2.0.pow((midi - 69) / 12.0) / SAMPLE_RATE).toFloat()
        }
        private val pentatonic = intArrayOf(0, 2, 4, 7, 9)
        private val major = intArrayOf(0, 2, 4, 5, 7, 9, 11)
        private val minor = intArrayOf(0, 2, 3, 5, 7, 8, 10)
        private val dorian = intArrayOf(0, 2, 3, 5, 7, 9, 10)

        // Kolejno: plemiona, brąz, feudalizm, proch, para, stal, krzem, orbita, menu.
        private val motifs = arrayOf(
            Motif(86, 48, pentatonic, intArrayOf(0, -1, 2, -1, 3, 2, -1, 1, 4, -1, 2, -1, 1, 0, -1, -1), intArrayOf(0, 0, 3, 0), intArrayOf(0, 2, 3, 0), SINE, TRIANGLE, SINE, 1),
            Motif(94, 50, dorian, intArrayOf(0, 2, 4, -1, 3, -1, 2, 1, 0, -1, 3, 5, 4, 2, -1, -1), intArrayOf(0, 3, 4, 2), intArrayOf(0, 3, 4, 2), TRIANGLE, TRIANGLE, SINE, 1),
            Motif(104, 48, major, intArrayOf(0, 2, 4, 2, 5, -1, 4, 2, 3, 5, 7, -1, 6, 4, 2, -1), intArrayOf(0, 3, 4, 0), intArrayOf(0, 3, 4, 0), TRIANGLE, TRIANGLE, TRIANGLE, 0),
            Motif(110, 50, minor, intArrayOf(0, -1, 2, 3, 4, -1, 3, -1, 2, 4, 5, -1, 4, 2, 0, -1), intArrayOf(0, 4, 3, 4), intArrayOf(0, 4, 3, 4), SAW, TRIANGLE, SINE, 2),
            Motif(118, 45, minor, intArrayOf(0, 2, -1, 4, 3, -1, 2, 5, 4, 6, -1, 3, 2, -1, 1, 0), intArrayOf(0, 3, 4, 3), intArrayOf(0, 3, 4, 3), PULSE, SAW, TRIANGLE, 3),
            Motif(124, 43, minor, intArrayOf(0, 2, 4, -1, 5, 4, 2, -1, 3, 5, 7, 5, 4, 3, 2, -1), intArrayOf(0, 4, 5, 3), intArrayOf(0, 4, 5, 3), SAW, SAW, TRIANGLE, 2),
            Motif(132, 48, dorian, intArrayOf(0, 4, 2, 5, 7, 5, 4, 2, 3, 6, 4, 7, 5, 4, 2, -1), intArrayOf(0, 3, 5, 4), intArrayOf(0, 3, 5, 4), PULSE, PULSE, SAW, 4),
            Motif(70, 46, pentatonic, intArrayOf(0, -1, -1, 2, -1, -1, 4, -1, 3, -1, -1, 2, -1, 1, -1, -1), intArrayOf(0, 2, 3, 2), intArrayOf(0, 2, 3, 2), SINE, SINE, SINE, 5),
            Motif(74, 48, major, intArrayOf(0, -1, 2, -1, 4, -1, 2, -1, 3, -1, 5, -1, 4, -1, 2, -1), intArrayOf(0, 3, 4, 3), intArrayOf(0, 3, 4, 3), SINE, TRIANGLE, SINE, 5),
        )
    }

    private class Motif(
        val bpm: Int,
        val root: Int,
        val scale: IntArray,
        val melody: IntArray,
        val bass: IntArray,
        val chords: IntArray,
        val leadWave: Int,
        val bassWave: Int,
        val padWave: Int,
        val percussion: Int,
    ) {
        val stepSamples = (SAMPLE_RATE * 60f / (bpm * 4)).toInt()
    }

    private inner class Voice {
        var era = MENU
            private set
        private var motif = motifs[8]
        private var step = 0
        private var remaining = 0
        private var noiseState = 0
        private var leadPhase = 0f
        private var bassPhase = 0f
        private var pad1Phase = 0f
        private var pad2Phase = 0f
        private var pad3Phase = 0f
        private var kickPhase = 0f
        private var leadFreq = 0f
        private var bassFreq = 0f
        private var pad1Freq = 0f
        private var pad2Freq = 0f
        private var pad3Freq = 0f
        private var kickFreq = 0f
        private var leadEnv = 0f
        private var bassEnv = 0f
        private var padEnv = 0f
        private var kickEnv = 0f
        private var snareEnv = 0f
        private var hatEnv = 0f
        private var leadAttack = 0
        private var bassAttack = 0

        fun reset(newEra: Int) {
            era = newEra
            motif = motifs[if (newEra == MENU) 8 else newEra]
            step = 0; remaining = 0
            noiseState = seed xor (newEra + 2) * 0x45D9F3B
            leadPhase = 0f; bassPhase = 0f
            pad1Phase = 0f; pad2Phase = 0f; pad3Phase = 0f; kickPhase = 0f
            leadFreq = 0f; bassFreq = 0f
            pad1Freq = 0f; pad2Freq = 0f; pad3Freq = 0f; kickFreq = 0f
            leadEnv = 0f; bassEnv = 0f; padEnv = 0f
            kickEnv = 0f; snareEnv = 0f; hatEnv = 0f
            leadAttack = 0; bassAttack = 0
        }

        private fun frequency(degree: Int, octave: Int): Float {
            val scale = motif.scale
            val midi = (motif.root + scale[degree % scale.size] +
                12 * (degree / scale.size) + octave).coerceIn(0, 127)
            return noteStep[midi]
        }

        private fun trigger() {
            val s = step
            val degree = motif.melody[s]
            if (degree >= 0) {
                leadFreq = frequency(degree, 12)
                leadEnv = 0f
                leadAttack = 90
            }
            if (s % 4 == 0) {
                bassFreq = frequency(motif.bass[s / 4], -12)
                bassEnv = 0f
                bassAttack = 80
                val chord = motif.chords[s / 4]
                pad1Freq = frequency(chord, 0)
                pad2Freq = frequency(chord + 2, 0)
                pad3Freq = frequency(chord + 4, 0)
            }
            val drum = motif.percussion
            if (drum != 5) {
                if (s == 0 || s == 8 || (drum == 3 && (s == 3 || s == 11)) ||
                    (drum == 4 && s == 10)) {
                    kickPhase = 0f; kickFreq = 110f / SAMPLE_RATE; kickEnv = 1f
                }
                if (s == 4 || s == 12 || (drum == 2 && s == 14)) snareEnv = 1f
                if (s % 2 == 0 || (drum == 4 && s % 2 == 1)) hatEnv = 0.55f
            } else if (s == 0 || s == 8) {
                kickPhase = 0f; kickFreq = 65f / SAMPLE_RATE; kickEnv = 0.3f
            }
            step = (s + 1) and 15
        }

        private fun wave(type: Int, phase: Float): Float =
            waves[type][(phase * TABLE_SIZE).toInt() and TABLE_MASK]

        private fun noise(): Float {
            noiseState = noiseState * 1664525 + 1013904223
            return ((noiseState ushr 16) and 65535) / 32768f - 1f
        }

        fun sample(intensity: Float): Float {
            if (remaining == 0) { trigger(); remaining = motif.stepSamples }
            remaining--
            if (leadAttack > 0) { leadEnv += 1f / 90f; leadAttack-- }
            else leadEnv *= 0.9975f
            if (bassAttack > 0) { bassEnv += 1f / 80f; bassAttack-- }
            else bassEnv *= 0.996f
            padEnv += (1f - padEnv) * 0.0005f
            kickEnv *= 0.996f
            kickFreq *= 0.9998f
            snareEnv *= 0.987f
            hatEnv *= 0.978f
            // Wygaszone obwiednie zerujemy: liczby subnormalne bywają na ARM wielokrotnie wolniejsze.
            if (leadEnv < 1e-5f) leadEnv = 0f
            if (bassEnv < 1e-5f) bassEnv = 0f
            if (kickEnv < 1e-5f) kickEnv = 0f
            if (snareEnv < 1e-5f) snareEnv = 0f
            if (hatEnv < 1e-5f) hatEnv = 0f

            leadPhase += leadFreq
            bassPhase += bassFreq
            pad1Phase += pad1Freq; pad2Phase += pad2Freq; pad3Phase += pad3Freq
            kickPhase += kickFreq
            if (leadPhase >= 1f) leadPhase -= 1f
            if (bassPhase >= 1f) bassPhase -= 1f
            if (pad1Phase >= 1f) pad1Phase -= 1f
            if (pad2Phase >= 1f) pad2Phase -= 1f
            if (pad3Phase >= 1f) pad3Phase -= 1f
            if (kickPhase >= 1f) kickPhase -= 1f

            val lead = wave(motif.leadWave, leadPhase) * leadEnv
            val bass = wave(motif.bassWave, bassPhase) * bassEnv
            val pad = (wave(motif.padWave, pad1Phase) + wave(motif.padWave, pad2Phase) +
                wave(motif.padWave, pad3Phase)) * (padEnv / 3f)
            val percussion = if (kickEnv + snareEnv + hatEnv > 0.00001f) {
                val n = noise()
                wave(SINE, kickPhase) * kickEnv + n * (snareEnv * 0.55f + hatEnv * 0.22f)
            } else 0f
            return lead * (0.13f + 0.06f * intensity) +
                bass * (0.14f + 0.055f * intensity) +
                pad * 0.15f + percussion * (0.11f + 0.09f * intensity)
        }
    }

    private var foreground = Voice().apply { reset(MENU) }
    private var incoming = Voice().apply { reset(MENU) }
    private var targetEra = MENU
    private var fadeRemaining = 0
    private var intensity = 0f
    private var desiredIntensity = 0f
    private var volume = 0f
    private var duckLevel = 1f
    private var duckTarget = 1f
    private var duckHoldSamples = 0

    /** Obwiednia na wątku audio: łagodny atak i powrót po zadanym czasie. */
    fun duck(amount: Float, seconds: Float) {
        duckTarget = if (amount.isFinite()) amount.coerceIn(0.12f, 1f) else 1f
        duckHoldSamples = if (seconds.isFinite()) (seconds.coerceIn(0f, 12f) * SAMPLE_RATE).toInt() else 0
    }

    val duckGain: Float get() = duckLevel

    private fun nextDuckGain(): Float {
        if (duckHoldSamples > 0) {
            duckHoldSamples--
            duckLevel += (duckTarget - duckLevel) * 0.0017f
        } else {
            duckLevel += (1f - duckLevel) * 0.00024f
        }
        return duckLevel
    }

    /** W trakcie poprzedniego crossfade kolejna zmiana czeka na jego zakończenie. */
    fun setEra(era: Int) {
        require(era in MENU..7) { "Nieznana epoka: $era" }
        targetEra = era
        if (fadeRemaining == 0 && foreground.era != era) beginFade(era)
    }

    fun setIntensity(value: Float) {
        desiredIntensity = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
    }

    /** Natychmiast przechodzi do epoki docelowej, bez crossfade (start strumienia). */
    fun skipFade() {
        if (foreground.era != targetEra) foreground.reset(targetEra)
        fadeRemaining = 0
    }

    val currentEra: Int get() = foreground.era
    val isCrossfading: Boolean get() = fadeRemaining > 0

    private fun beginFade(era: Int) {
        incoming.reset(era)
        fadeRemaining = CROSSFADE_SAMPLES
    }

    private fun sample(): Float {
        intensity += (desiredIntensity - intensity) * 0.002f
        if (fadeRemaining == 0) return foreground.sample(intensity)
        val mix = 1f - fadeRemaining.toFloat() / CROSSFADE_SAMPLES
        val out = foreground.sample(intensity) * (1f - mix) + incoming.sample(intensity) * mix
        fadeRemaining--
        if (fadeRemaining == 0) {
            val old = foreground
            foreground = incoming
            incoming = old
            if (foreground.era != targetEra) beginFade(targetEra)
        }
        return out
    }

    /** Dla testów i narzędzi analitycznych. Nie alokuje buforów. */
    fun render(out: FloatArray, count: Int = out.size, targetVolume: Float = 1f) {
        require(count in 0..out.size)
        val gain = if (targetVolume.isFinite()) targetVolume.coerceIn(0f, 1f) else 0f
        for (i in 0 until count) {
            volume += (gain - volume) * 0.002f
            out[i] = sample() * volume * nextDuckGain()
        }
    }

    /** Próbki PCM16 do Android AudioTrack; bufor pozostaje własnością wywołującego. */
    fun renderPcm(out: ShortArray, count: Int = out.size, targetVolume: Float = 1f) {
        require(count in 0..out.size)
        val gain = if (targetVolume.isFinite()) targetVolume.coerceIn(0f, 1f) else 0f
        for (i in 0 until count) {
            volume += (gain - volume) * 0.002f
            out[i] = ((sample() * volume * nextDuckGain()).coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
    }
}
