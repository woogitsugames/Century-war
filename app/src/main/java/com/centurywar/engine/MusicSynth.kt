package com.centurywar.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

/** Muzyka proceduralna PCM mono. Cały stan syntezy należy wyłącznie do wątku muzyki. */
class MusicSynth(seed: Int = 0x43A72F19) {
    private var seed = seed
    companion object {
        const val SAMPLE_RATE = 22_050
        const val MENU = -1
        const val CROSSFADE_SAMPLES = SAMPLE_RATE * 2
        private const val TABLE_SIZE = 1024
        private const val TABLE_MASK = TABLE_SIZE - 1
        private const val SINE = 0
        private const val TRIANGLE = 1
        private const val SAW = 2
        private const val PULSE = 3
        private const val FLUTE = 0
        private const val PLUCK = 1
        private const val HORN = 2
        private const val BRASS = 3
        private const val SYNTH = 4
        private const val BELL = 5

        private val waves = Array(4) { type ->
            FloatArray(TABLE_SIZE) { i ->
                val x = i.toFloat() / TABLE_SIZE
                when (type) {
                    SINE -> sin(2.0 * PI * x).toFloat()
                    TRIANGLE -> 1f - 4f * abs(x - 0.5f)
                    SAW -> 2f * x - 1f
                    else -> if (x < 0.45f) 1f else -1f
                }
            }
        }
        private val noteStep = FloatArray(128) { midi ->
            (440.0 * 2.0.pow((midi - 69) / 12.0) / SAMPLE_RATE).toFloat()
        }
        private val pentatonic = intArrayOf(0, 2, 4, 7, 9)
        private val dorian = intArrayOf(0, 2, 3, 5, 7, 9, 10)
        private val minor = intArrayOf(0, 2, 3, 5, 7, 8, 10)
        private val major = intArrayOf(0, 2, 4, 5, 7, 9, 11)
        private val harmonic = intArrayOf(0, 2, 3, 5, 7, 8, 11)
        private val lydian = intArrayOf(0, 2, 4, 6, 7, 9, 11)

        // Wspólne DNA melodyczne: stopnie 0–2–4–3–2–0, zmieniane harmonią każdej epoki.
        private val themeA = intArrayOf(
            0, -1, 2, -1, 4, -1, 3, -1, 2, -1, 0, -1, 1, -1, 2, -1,
            0, -1, 2, -1, 4, 5, 4, -1, 3, -1, 2, -1, 1, -1, 0, -1,
        )
        private val themeB = intArrayOf(
            4, -1, 5, -1, 6, -1, 4, -1, 3, 2, 4, -1, 5, -1, 3, -1,
            2, -1, 4, -1, 5, 4, 3, -1, 2, -1, 1, -1, 2, 1, 0, -1,
        )
        private val themeBridge = intArrayOf(
            2, -1, -1, -1, 1, -1, -1, -1, 0, -1, 2, -1, 3, -1, -1, -1,
            4, -1, -1, -1, 3, -1, -1, -1, 2, -1, 1, -1, 0, -1, -1, -1,
        )
        private val chordsA = intArrayOf(0, 3, 4, 0)
        private val chordsB = intArrayOf(0, 5, 3, 4)
        private val chordsBridge = intArrayOf(5, 3, 4, 4)

        // Każda z dziewięciu epok ma własne tempo, skalę lub instrumentarium.
        // Menu ma osobny, dziesiąty styl.
        private val styles = arrayOf(
            Style(82, 52, pentatonic, FLUTE, TRIANGLE, SINE, 0, 0.18f),
            Style(94, 53, dorian, PLUCK, TRIANGLE, TRIANGLE, 1, 0.21f),
            Style(98, 50, dorian, HORN, TRIANGLE, SINE, 2, 0.20f),
            Style(102, 53, major, PLUCK, TRIANGLE, TRIANGLE, 2, 0.22f),
            Style(110, 50, harmonic, HORN, SAW, TRIANGLE, 3, 0.19f),
            Style(116, 48, minor, BRASS, SAW, SAW, 4, 0.18f),
            Style(124, 48, minor, BRASS, PULSE, TRIANGLE, 4, 0.16f),
            Style(132, 53, lydian, SYNTH, PULSE, SINE, 5, 0.23f),
            Style(90, 48, lydian, BELL, SINE, SINE, 5, 0.25f),
            Style(72, 55, pentatonic, FLUTE, SINE, SINE, 6, 0.28f),
        )
    }

    private class Style(
        val bpm: Int,
        val root: Int,
        val scale: IntArray,
        val lead: Int,
        val bass: Int,
        val pad: Int,
        val drums: Int,
        val reverb: Float,
    ) {
        val stepSamples = (SAMPLE_RATE * 60f / (bpm * 4)).toInt()
    }

    private inner class Voice {
        var era = MENU
            private set
        private var style = styles[9]
        private var step = 0
        private var samplesUntilStep = 0
        private var noiseState = 1
        private var phraseVariation = 0
        var seedAtReset = seed
            private set
        private val leadA = Note()
        private val leadB = Note()
        private val counter = Note()
        private var nextLead = false
        private var bassPhase = 0f
        private var bassFrequency = 0f
        private var bassLevel = 0f
        private var bassFilter = 0f
        private var tensionPhase = 0f
        private var tensionFrequency = 0f
        private var tensionLevel = 0f
        private var padPhase1 = 0f
        private var padPhase2 = 0f
        private var padPhase3 = 0f
        private var padFrequency1 = 0f
        private var padFrequency2 = 0f
        private var padFrequency3 = 0f
        private var padFilter = 0f
        private var padLevel = 0f
        private var kickPhase = 0f
        private var kickFrequency = 0f
        private var kickLevel = 0f
        private var snareLevel = 0f
        private var hatLevel = 0f

        private inner class Note {
            private var phase = 0f
            private var modPhase = 0f
            private var frequency = 0f
            private var envelope = 0f
            private var attack = 0
            private var filter = 0f
            private var timbre = FLUTE
            private val string = FloatArray(384)
            private var stringLength = 0
            private var stringPosition = 0

            fun clear() {
                phase = 0f; modPhase = 0f; frequency = 0f
                envelope = 0f; attack = 0; filter = 0f
                stringLength = 0; stringPosition = 0
            }

            fun start(step: Float, instrument: Int) {
                frequency = step
                timbre = instrument
                phase = 0f; modPhase = 0f; envelope = 0f; filter = 0f
                attack = if (instrument == PLUCK) 80 else 190
                if (instrument == PLUCK) {
                    stringLength = (1f / step).toInt().coerceIn(8, string.size)
                    stringPosition = 0
                    var previous = 0f
                    for (i in 0 until stringLength) {
                        val value = noise() * 0.55f + previous * 0.45f
                        string[i] = value
                        previous = value
                    }
                } else stringLength = 0
            }

            fun sample(): Float {
                if (envelope < 0.0001f && attack == 0) return 0f
                if (attack > 0) {
                    envelope += (1f - envelope) * 0.025f
                    attack--
                } else envelope *= if (timbre == FLUTE || timbre == HORN) 0.99965f else 0.99945f
                if (envelope < 0.0001f) { envelope = 0f; return 0f }
                phase += frequency
                modPhase += frequency * 2.01f
                if (phase >= 1f) phase -= 1f
                if (modPhase >= 1f) modPhase -= 1f
                val raw = when (timbre) {
                    FLUTE -> wave(SINE, phase) * 0.82f + wave(TRIANGLE, phase) * 0.18f
                    PLUCK -> {
                        val pos = stringPosition
                        val next = if (pos + 1 == stringLength) 0 else pos + 1
                        val value = string[pos]
                        string[pos] = (value + string[next]) * 0.497f
                        stringPosition = next
                        value
                    }
                    HORN -> wave(TRIANGLE, phase) * 0.7f + wave(PULSE, phase) * 0.3f
                    BRASS -> wave(SAW, phase) * 0.7f + wave(TRIANGLE, phase) * 0.3f
                    SYNTH -> wave(SINE, phase + wave(SINE, modPhase) * 0.28f) * 0.85f +
                        wave(PULSE, phase) * 0.15f
                    else -> wave(SINE, phase + wave(SINE, modPhase) * 0.52f) * 0.83f +
                        wave(SINE, modPhase) * 0.17f
                }
                val cutoff = when (timbre) {
                    BRASS, HORN -> 0.16f
                    PLUCK -> 0.32f
                    else -> 0.26f
                }
                filter += (raw - filter) * cutoff
                return filter * envelope
            }
        }

        fun reset(newEra: Int) {
            era = newEra
            style = styles[if (newEra == MENU) 9 else newEra]
            step = 0; samplesUntilStep = 0; phraseVariation = 0
            seedAtReset = seed
            noiseState = (seed xor ((newEra + 2) * 0x45D9F3B)).let { if (it == 0) 1 else it }
            leadA.clear(); leadB.clear(); counter.clear(); nextLead = false
            bassPhase = 0f; bassFrequency = 0f; bassLevel = 0f; bassFilter = 0f
            tensionPhase = 0f; tensionFrequency = 0f; tensionLevel = 0f
            padPhase1 = 0f; padPhase2 = 0f; padPhase3 = 0f
            padFrequency1 = 0f; padFrequency2 = 0f; padFrequency3 = 0f
            padFilter = 0f; padLevel = 0f
            kickPhase = 0f; kickFrequency = 0f; kickLevel = 0f
            snareLevel = 0f; hatLevel = 0f
        }

        private fun frequency(degree: Int, shift: Int): Float {
            val scale = style.scale
            val midi = (style.root + scale[degree % scale.size] +
                12 * (degree / scale.size) + shift).coerceIn(0, 127)
            return noteStep[midi]
        }

        private fun noise(): Float {
            noiseState = noiseState * 1664525 + 1013904223
            return ((noiseState ushr 16) and 65535) / 32768f - 1f
        }

        private fun wave(type: Int, phase: Float): Float =
            waves[type][(phase * TABLE_SIZE).toInt() and TABLE_MASK]

        private fun phraseHash(phrase: Int): Int {
            var x = seed xor ((era + 2) * 0x45D9F3B) xor (phrase * 0x7FEB352D)
            x = (x xor (x ushr 16)) * 0x7FEB352D
            x = (x xor (x ushr 15)) * 0x846CA68B.toInt()
            return x xor (x ushr 16)
        }

        private fun trigger() {
            val position = step % 32
            val phrase = step / 32
            val section = phrase % 3
            if (position == 0) phraseVariation = phraseHash(phrase)
            val melody = when (section) {
                0 -> themeA[position]
                1 -> themeB[position]
                else -> themeBridge[position]
            }
            val progression = when (section) {
                0 -> chordsA
                1 -> chordsB
                else -> chordsBridge
            }
            if (melody >= 0 && (position != 12 || (phraseVariation and 3) != 0)) {
                val note = if (nextLead) leadA else leadB
                val shift = if (position == 20 && (phraseVariation and 4) != 0) 1 else 0
                note.start(frequency(melody + shift, 12), style.lead)
                nextLead = !nextLead
            }
            if (position % 8 == 0) {
                val chord = progression[position / 8]
                bassFrequency = frequency(chord, -12)
                bassLevel = 1f
                tensionFrequency = frequency(chord, -24)
                tensionLevel = 1f
                padFrequency1 = frequency(chord, 0)
                padFrequency2 = frequency(chord + 2, 0)
                padFrequency3 = frequency(chord + 4, 0)
            }
            // Sześć cichych ozdobników koduje numer frazy (0..63).
            // Żadna fraza nie powtarza się dokładnie przez co najmniej 64 frazy.
            val marker = position / 4
            if (position % 4 == 3 && marker < 6 &&
                ((phrase and 63) and (1 shl marker)) != 0) {
                counter.start(frequency(progression[position / 8] + 2, 0),
                    if (style.lead == PLUCK) FLUTE else style.lead)
            } else if (section == 1 && position % 4 == 2) {
                counter.start(frequency(progression[position / 8] + 2 +
                    ((phraseVariation ushr 8) and 1), 0),
                    if (style.lead == PLUCK) FLUTE else style.lead)
            }
            val drums = style.drums
            if (drums != 6) {
                val kick = when (drums) {
                    0 -> position == 0 || position == 16
                    1, 2 -> position % 16 == 0
                    3 -> position % 8 == 0
                    4, 5 -> position % 8 == 0 || position % 16 == 6
                    else -> false
                }
                if (kick) {
                    kickPhase = 0f
                    kickFrequency = (if (drums < 3) 85f else 120f) / SAMPLE_RATE
                    kickLevel = 1f
                }
                if (position % 16 == 8 || (drums >= 3 && position % 16 == 12)) {
                    snareLevel = if (drums < 3) 0.48f else 0.85f
                }
                if (position % (if (drums >= 4) 2 else 4) == 0) hatLevel = 0.6f
            }
            // 3 sekcje x 64 warianty = 192 frazy; nie wracamy co 96 kroków.
            step = (step + 1) % (32 * 192)
        }

        fun sample(calm: Float, clash: Float, danger: Float): Float {
            if (samplesUntilStep == 0) {
                trigger()
                samplesUntilStep = style.stepSamples
            }
            samplesUntilStep--
            val lead = leadA.sample() + leadB.sample()
            val response = counter.sample()
            bassPhase += bassFrequency
            if (bassPhase >= 1f) bassPhase -= 1f
            bassLevel *= 0.9998f
            if (bassLevel < 0.0001f) bassLevel = 0f
            bassFilter += (wave(style.bass, bassPhase) - bassFilter) * 0.095f
            tensionPhase += tensionFrequency
            if (tensionPhase >= 1f) tensionPhase -= 1f
            tensionLevel *= 0.99965f

            padPhase1 += padFrequency1
            padPhase2 += padFrequency2
            padPhase3 += padFrequency3
            if (padPhase1 >= 1f) padPhase1 -= 1f
            if (padPhase2 >= 1f) padPhase2 -= 1f
            if (padPhase3 >= 1f) padPhase3 -= 1f
            val chord = (wave(style.pad, padPhase1) + wave(style.pad, padPhase2) +
                wave(style.pad, padPhase3)) / 3f
            padFilter += (chord - padFilter) * 0.065f
            padLevel += (1f - padLevel) * 0.00035f

            kickPhase += kickFrequency
            if (kickPhase >= 1f) kickPhase -= 1f
            kickFrequency *= 0.99965f
            kickLevel *= 0.9956f
            snareLevel *= 0.982f
            hatLevel *= 0.964f
            if (kickLevel < 0.0001f) kickLevel = 0f
            if (snareLevel < 0.0001f) snareLevel = 0f
            if (hatLevel < 0.0001f) hatLevel = 0f
            val percussion = if (kickLevel + snareLevel + hatLevel > 0f) {
                val n = noise()
                wave(SINE, kickPhase) * kickLevel * 0.7f +
                    n * (snareLevel * 0.45f + hatLevel * 0.16f)
            } else 0f
            val menuGain = if (era == MENU) 0.37f else 1f
            return (lead * (0.11f + 0.05f * calm + 0.055f * clash) +
                response * (0.026f + 0.062f * clash) +
                bassFilter * bassLevel * (0.064f + 0.038f * clash + 0.024f * danger) +
                padFilter * padLevel * (0.115f * calm + 0.060f * clash) +
                percussion * (0.018f * calm + 0.066f * clash + 0.072f * danger) +
                wave(TRIANGLE, tensionPhase) * tensionLevel * 0.048f * danger) * menuGain
        }

        val reverbAmount: Float get() = style.reverb
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
    private val echoA = FloatArray(1789)
    private val echoB = FloatArray(2693)
    private var echoPositionA = 0
    private var echoPositionB = 0

    /** Łagodny atak wyciszenia i powrót po zadanym czasie. Wywołuj z wątku audio. */
    fun duck(amount: Float, seconds: Float) {
        duckTarget = if (amount.isFinite()) amount.coerceIn(0.12f, 1f) else 1f
        duckHoldSamples = if (seconds.isFinite())
            (seconds.coerceIn(0f, 12f) * SAMPLE_RATE).toInt() else 0
    }

    val duckGain: Float get() = duckLevel

    private fun nextDuckGain(): Float {
        if (duckHoldSamples > 0) {
            duckHoldSamples--
            duckLevel += (duckTarget - duckLevel) * 0.0017f
        } else duckLevel += (1f - duckLevel) * 0.00024f
        return duckLevel
    }

    /** Ziarno dotyczy tylko muzyki; nie zmienia RNG bitwy ani powtórek. */
    fun setSeed(value: Int) {
        seed = value
    }

    /** W trakcie crossfade następna zmiana czeka na koniec poprzedniej. */
    fun setEra(era: Int) {
        require(era in MENU..8) { "Nieznana epoka: $era" }
        targetEra = era
        if (fadeRemaining == 0 && (foreground.era != era || foreground.seedAtReset != seed)) {
            beginFade(era)
        }
    }

    /** Dolna granica okresu fraz (sześciobitowe ozdobniki). */
    internal fun shortestFormRepeatSeconds(era: Int): Float {
        require(era in MENU..8)
        return 64 * 32f * styles[if (era == MENU) 9 else era].stepSamples / SAMPLE_RATE
    }

    fun setIntensity(value: Float) {
        desiredIntensity = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
    }

    /** Bez przejścia przy uruchomieniu nowego strumienia. */
    fun skipFade() {
        if (foreground.era != targetEra || foreground.seedAtReset != seed) foreground.reset(targetEra)
        fadeRemaining = 0
    }

    val currentEra: Int get() = foreground.era
    val isCrossfading: Boolean get() = fadeRemaining > 0

    private fun beginFade(era: Int) {
        incoming.reset(era)
        fadeRemaining = CROSSFADE_SAMPLES
    }

    private fun sample(): Float {
        // Szybsze wejście w walkę, wolniejszy powrót do spokoju.
        val smoothing = if (desiredIntensity > intensity) 0.00024f else 0.000075f
        intensity += (desiredIntensity - intensity) * smoothing
        val calm = (1f - intensity * 1.15f).coerceIn(0f, 1f)
        val clash = (intensity * 2.2f).coerceIn(0f, 1f)
        val danger = ((intensity - 0.55f) * 2.22f).coerceIn(0f, 1f)
        val dry: Float
        val reverb: Float
        if (fadeRemaining == 0) {
            dry = foreground.sample(calm, clash, danger)
            reverb = foreground.reverbAmount
        } else {
            val mix = 1f - fadeRemaining.toFloat() / CROSSFADE_SAMPLES
            dry = foreground.sample(calm, clash, danger) * (1f - mix) +
                incoming.sample(calm, clash, danger) * mix
            reverb = foreground.reverbAmount * (1f - mix) + incoming.reverbAmount * mix
            fadeRemaining--
            if (fadeRemaining == 0) {
                val old = foreground
                foreground = incoming
                incoming = old
                if (foreground.era != targetEra || foreground.seedAtReset != seed) beginFade(targetEra)
            }
        }
        // Dwa krótkie opóźnienia z tłumionym sprzężeniem; zero alokacji i stabilna energia.
        val a = echoA[echoPositionA]
        val b = echoB[echoPositionB]
        echoA[echoPositionA] = dry * 0.24f + a * 0.62f + b * 0.12f
        echoB[echoPositionB] = dry * 0.19f + b * 0.66f + a * 0.09f
        echoPositionA++
        echoPositionB++
        if (echoPositionA == echoA.size) echoPositionA = 0
        if (echoPositionB == echoB.size) echoPositionB = 0
        val mixed = dry + (a + b) * reverb
        // Łagodna kompresja zapobiega sumowaniu się szczytów warstw.
        return mixed / (1f + abs(mixed) * 0.7f)
    }

    /** Dla testów i analizy, bez alokacji na blok. */
    fun render(out: FloatArray, count: Int = out.size, targetVolume: Float = 1f) {
        require(count in 0..out.size)
        val gain = if (targetVolume.isFinite()) targetVolume.coerceIn(0f, 1f) else 0f
        for (i in 0 until count) {
            volume += (gain - volume) * 0.002f
            out[i] = sample() * volume * nextDuckGain()
        }
    }

    /** PCM16 do AudioTrack. Bufor należy do wywołującego. */
    fun renderPcm(out: ShortArray, count: Int = out.size, targetVolume: Float = 1f) {
        require(count in 0..out.size)
        val gain = if (targetVolume.isFinite()) targetVolume.coerceIn(0f, 1f) else 0f
        for (i in 0 until count) {
            volume += (gain - volume) * 0.002f
            out[i] = ((sample() * volume * nextDuckGain()).coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
    }
}
