package com.centurywar.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Warstwy ataku, rezonansu i odbić; cała losowość pochodzi z lokalnego stałego ziarna. */
internal object SfxLayers {
    private const val WOOD = 0
    private const val BRONZE = 1
    private const val STEEL = 2
    private const val POWDER = 3
    private const val ENERGY = 4
    private const val STRING = 5
    private const val VOICE = 6
    private const val MOTOR = 7
    private const val UI = 8
    private const val BOOM = 9
    // D63: wybuchy epok bez prochu (głuchy łomot kamienia/drewna) i epok krzemu (opadający „zap”).
    private const val BOOM_EARTH = 10
    private const val BOOM_ENERGY = 11
    private const val RATE = SoundSynth.SAMPLE_RATE
    private const val TAU = 2.0 * PI

    // Impuls i korpus mają niezależny zanik; strojenie pod głośniki 200–800 Hz.
    private data class Recipe(
        val type: Int, val hz: Float, val attack: Float, val body: Float,
        val texture: Float, val attackDecay: Float, val bodyDecay: Float,
        val sweep: Float = 0f, val notes: Int = 1, val interval: Float = 0.095f,
        val climb: Int = 3,
    )

    private fun recipe(id: Int): Recipe = when (id) {
        SoundSynth.HIT, SoundSynth.HIT_WOOD -> Recipe(WOOD, 260f, 0.95f, 0.7f, 0.5f, 45f, 21f)
        SoundSynth.HIT_BRONZE -> Recipe(BRONZE, 460f, 0.6f, 0.93f, 0.40f, 47f, 19f)
        SoundSynth.HIT_STEEL -> Recipe(STEEL, 580f, 0.8f, 0.9f, 0.48f, 49f, 17f)
        SoundSynth.HIT_ENERGY -> Recipe(ENERGY, 680f, 0.35f, 0.95f, 0.23f, 32f, 19f, -0.64f)
        SoundSynth.DEATH, SoundSynth.DEATH_HUMAN -> Recipe(VOICE, 290f, 0.32f, 0.9f, 0.2f, 27f, 9f, -0.42f)
        SoundSynth.DEATH_BEAST -> Recipe(VOICE, 210f, 0.52f, 0.94f, 0.38f, 26f, 9f, -0.48f)
        SoundSynth.DEATH_MACHINE -> Recipe(MOTOR, 520f, 0.78f, 0.79f, 0.37f, 28f, 12f, -0.64f)
        SoundSynth.ARROW, SoundSynth.SHOT_BOW -> Recipe(STRING, 480f, 0.33f, 0.82f, 0.35f, 30f, 16f, -0.2f)
        SoundSynth.SHOT_SLING -> Recipe(WOOD, 330f, 0.63f, 0.52f, 0.47f, 29f, 16f, 0.23f)
        SoundSynth.SHOT_CROSSBOW -> Recipe(STRING, 350f, 0.83f, 0.69f, 0.36f, 43f, 23f)
        SoundSynth.SHOT, SoundSynth.SHOT_MUSKET -> Recipe(POWDER, 330f, 1.25f, 0.73f, 0.83f, 31f, 16f, -0.18f)
        SoundSynth.SHOT_CANNON -> Recipe(POWDER, 240f, 1.22f, 1.03f, 0.87f, 24f, 12f, -0.26f)
        SoundSynth.SHOT_RIFLE -> Recipe(POWDER, 530f, 1.13f, 0.7f, 0.9f, 43f, 24f)
        SoundSynth.SHOT_GUN -> Recipe(POWDER, 350f, 1.31f, 0.78f, 1.0f, 37f, 19f)
        SoundSynth.LASER, SoundSynth.SHOT_LASER -> Recipe(ENERGY, 630f, 0.2f, 0.97f, 0.34f, 24f, 12f, -0.71f)
        SoundSynth.SHOT_PLASMA -> Recipe(ENERGY, 430f, 0.37f, 0.95f, 0.45f, 19f, 10f, 0.7f)
        SoundSynth.TURRET_PRIMITIVE -> Recipe(WOOD, 250f, 0.9f, 0.8f, 0.55f, 32f, 14f)
        SoundSynth.TURRET_MEDIEVAL -> Recipe(STRING, 380f, 0.83f, 0.75f, 0.38f, 39f, 15f)
        SoundSynth.TURRET_POWDER -> Recipe(POWDER, 300f, 1.22f, 0.9f, 0.89f, 23f, 12f)
        SoundSynth.TURRET_MODERN -> Recipe(MOTOR, 450f, 1.1f, 0.79f, 0.7f, 27f, 15f)
        SoundSynth.TURRET_ENERGY -> Recipe(ENERGY, 560f, 0.42f, 1.03f, 0.41f, 21f, 10f, -0.5f)
        SoundSynth.EXPLOSION, SoundSynth.BARRAGE_CANNON -> Recipe(BOOM, 280f, 1.2f, 0.88f, 1.04f, 9f, 6f, -0.55f)
        SoundSynth.BARRAGE_STONE -> Recipe(WOOD, 230f, 1.0f, 0.73f, 0.75f, 14f, 8f)
        SoundSynth.BARRAGE_FIRE -> Recipe(BOOM, 360f, 0.65f, 0.44f, 0.98f, 5f, 4f, 0.13f)
        SoundSynth.BARRAGE_ROCKET -> Recipe(MOTOR, 300f, 0.92f, 0.88f, 0.91f, 9f, 5f, 0.44f)
        SoundSynth.BARRAGE_ORBITAL -> Recipe(ENERGY, 350f, 1.2f, 0.96f, 0.87f, 8f, 5f, 0.83f)
        SoundSynth.EVOLVE, SoundSynth.EVOLVE_MEDIEVAL -> Recipe(UI, 330f, 0.19f, 0.96f, 0.12f, 40f, 11f, notes = 4, interval = 0.14f, climb = 3)
        SoundSynth.EVOLVE_PRIMITIVE -> Recipe(UI, 260f, 0.26f, 0.9f, 0.18f, 38f, 12f, notes = 4, interval = 0.14f, climb = 3)
        SoundSynth.EVOLVE_INDUSTRIAL -> Recipe(UI, 380f, 0.28f, 0.9f, 0.20f, 42f, 12f, notes = 4, interval = 0.14f, climb = 4)
        SoundSynth.EVOLVE_FUTURE -> Recipe(ENERGY, 460f, 0.28f, 0.99f, 0.3f, 17f, 6f, 0.39f)
        SoundSynth.VICTORY -> Recipe(UI, 390f, 0.12f, 0.94f, 0.11f, 42f, 8f, notes = 5, interval = 0.145f, climb = 4)
        SoundSynth.DEFEAT -> Recipe(UI, 310f, 0.09f, 0.82f, 0.2f, 31f, 8f, notes = 4, interval = 0.17f, climb = -3)
        SoundSynth.THUMP -> Recipe(WOOD, 230f, 1.05f, 0.8f, 0.57f, 37f, 21f)
        SoundSynth.COIN, SoundSynth.PURCHASE -> Recipe(UI, 590f, 0.13f, 0.88f, 0.1f, 50f, 22f, notes = 2, interval = 0.06f, climb = 4)
        SoundSynth.CLICK -> Recipe(UI, 510f, 0.25f, 0.75f, 0.13f, 80f, 45f, notes = 1)
        SoundSynth.ERROR -> Recipe(UI, 350f, 0.15f, 0.69f, 0.23f, 44f, 23f, notes = 2, interval = 0.061f, climb = -4)
        SoundSynth.MEDALS, SoundSynth.REWARD -> Recipe(UI, 550f, 0.18f, 0.88f, 0.1f, 47f, 12f, notes = 3, interval = 0.12f, climb = 4)
        SoundSynth.ACHIEVEMENT, SoundSynth.ERA_UNLOCK -> Recipe(UI, 460f, 0.16f, 0.96f, 0.1f, 39f, 11f, notes = 3, interval = 0.135f, climb = 5)
        SoundSynth.RANK_UP -> Recipe(UI, 620f, 0.14f, 0.9f, 0.1f, 48f, 13f, notes = 3, interval = 0.12f, climb = 3)
        SoundSynth.CARD_COMMON -> Recipe(UI, 350f, 0.16f, 0.77f, 0.16f, 50f, 16f, notes = 2, interval = 0.11f, climb = 2)
        SoundSynth.CARD_RARE -> Recipe(UI, 470f, 0.13f, 0.86f, 0.12f, 48f, 16f, notes = 2, interval = 0.11f, climb = 4)
        SoundSynth.CARD_EPIC -> Recipe(UI, 530f, 0.16f, 0.94f, 0.1f, 49f, 15f, notes = 3, interval = 0.08f, climb = 4)
        SoundSynth.CARD_LEGENDARY -> Recipe(UI, 590f, 0.19f, 0.97f, 0.14f, 51f, 16f, notes = 3, interval = 0.08f, climb = 5)
        SoundSynth.RESEARCH -> Recipe(UI, 490f, 0.12f, 0.83f, 0.13f, 48f, 19f, notes = 2, interval = 0.1f, climb = 5)
        SoundSynth.STAR -> Recipe(UI, 660f, 0.08f, 0.95f, 0.12f, 57f, 15f, notes = 2, interval = 0.09f, climb = 7)
        SoundSynth.BOSS -> Recipe(MOTOR, 240f, 1.1f, 0.9f, 0.58f, 14f, 6f, -0.58f)
        // R10-03: niski róg alarmowy (dwa dęcia; korpus każdej nuty gaśnie ok. 17/s), dwa tony w dół, jasny „dzyń”.
        SoundSynth.ALERT_BASE -> Recipe(UI, 233f, 0.06f, 1.0f, 0.08f, 40f, 3f, -0.06f, notes = 2, interval = 0.22f, climb = -1)
        SoundSynth.ALERT_ENEMY_ERA -> Recipe(UI, 520f, 0.12f, 0.95f, 0.1f, 40f, 5f, notes = 2, interval = 0.19f, climb = -5)
        SoundSynth.ALERT_READY -> Recipe(UI, 990f, 0.06f, 0.95f, 0.06f, 60f, 15f, notes = 1)
        // D63: trafienie pociskiem epok prochu i stali — niski, krótki „łup” z ziarnem prochu,
        // bez dzwonienia ostrza (HIT_STEEL zostaje dla epok żelaza i średniowiecza).
        SoundSynth.HIT_POWDER -> Recipe(POWDER, 190f, 1.1f, 0.78f, 0.62f, 58f, 30f, -0.3f)
        SoundSynth.EXPLOSION_PRIMITIVE -> Recipe(BOOM_EARTH, 150f, 1.15f, 0.9f, 0.8f, 13f, 8f, -0.3f)
        SoundSynth.EXPLOSION_ENERGY -> Recipe(BOOM_ENERGY, 330f, 1.0f, 0.95f, 0.9f, 10f, 6f, -0.68f)
        else -> Recipe(WOOD, 250f, 0.8f, 0.7f, 0.3f, 25f, 15f)
    }

    // Biquad transponowany II; stałe filtru są liczone tylko raz na próbkę dźwięku.
    private class Biquad(mode: Int, hz: Double, q: Double) {
        private val b0: Float
        private val b1: Float
        private val b2: Float
        private val a1: Float
        private val a2: Float
        private var z1 = 0f
        private var z2 = 0f

        init {
            val w = TAU * hz.coerceIn(40.0, RATE * 0.45) / RATE
            val c = cos(w)
            val alpha = sin(w) / (2.0 * q)
            val a0 = 1.0 + alpha
            val coefficients = when (mode) {
                0 -> doubleArrayOf((1.0 - c) / 2.0, 1.0 - c, (1.0 - c) / 2.0)
                1 -> doubleArrayOf(alpha, 0.0, -alpha)
                else -> doubleArrayOf((1.0 + c) / 2.0, -(1.0 + c), (1.0 + c) / 2.0)
            }
            b0 = (coefficients[0] / a0).toFloat()
            b1 = (coefficients[1] / a0).toFloat()
            b2 = (coefficients[2] / a0).toFloat()
            a1 = (-2.0 * c / a0).toFloat()
            a2 = ((1.0 - alpha) / a0).toFloat()
        }

        fun step(x: Float): Float {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y
        }
    }

    fun render(id: Int, variant: Int): FloatArray {
        val s = recipe(id)
        val n = (RATE * SoundSynth.duration(id)).toInt()
        val out = FloatArray(n)
        val pitchFactor = if (variant == 0) 0.986f else 1.014f
        var rng = 0x13579BDF xor ((id + 1) * 0x45D9F3B) xor (variant * 0x119DE1F3)
        fun noise(): Float {
            rng = rng xor (rng shl 13)
            rng = rng xor (rng ushr 17)
            rng = rng xor (rng shl 5)
            return ((rng ushr 8) and 0xffff) / 32768f - 1f
        }

        val boom = s.type == BOOM || s.type == BOOM_EARTH || s.type == BOOM_ENERGY
        val low = Biquad(0, when (s.type) { BOOM -> 640.0; BOOM_EARTH -> 380.0; else -> 820.0 }, 0.72)
        val band = Biquad(1, when (s.type) { STEEL -> 1450.0; BOOM_ENERGY -> 1100.0; else -> 530.0 }, 0.82)
        val high = Biquad(2, if (s.type == POWDER) 1900.0 else 2700.0, 0.75)
        val isString = s.type == WOOD || s.type == STRING || s.type == BOOM_EARTH
        val delayLength = if (isString) (RATE / (s.hz * pitchFactor)).toInt().coerceIn(32, 384) else 0
        val string = FloatArray(delayLength)
        for (j in string.indices) string[j] = noise() * 0.52f
        var stringIdx = 0
        val echo1 = FloatArray(137)
        val echo2 = FloatArray(283)
        val echo3 = FloatArray(401)
        var phase = 0.0
        var modPhase = 0.0
        var second = 0.0
        var envAttack = 1f
        var envBody = 1f
        val attackRate = exp(-s.attackDecay.toDouble() / RATE).toFloat()
        val bodyRate = exp(-s.bodyDecay.toDouble() / RATE).toFloat()
        var currentNote = 0
        var nextNoteAt = max(1, (s.interval * RATE).toInt())
        val noteSamples = nextNoteAt
        val semitone = 2.0.pow(s.climb / 12.0)
        var notePitch = 1.0
        var noteEnv = 1f
        val noteFall = exp(-17.0 / RATE).toFloat()
        // D63: kolejna nuta wchodzi rampą 4 ms przy ciągłej fazie (wcześniej skok obwiedni
        // i faza od zera dawały trzask na każdej granicy nut ewolucji, zwycięstwa, medali).
        val noteRampStep = 1f / (RATE * 0.004f)
        var noteRamp = 1f
        var noteFrom = 1f
        val resonance = if (variant == 0) 0.94f else 0.89f
        val fadeInSamples = RATE * (if (s.type == UI) 0.004f else 0.0018f)
        val fadeOutSamples = RATE * (SoundSynth.duration(id) * 0.12f).coerceIn(0.015f, 0.04f)

        for (i in 0 until n) {
            if (s.type == UI && currentNote + 1 < s.notes && i >= nextNoteAt) {
                currentNote++
                nextNoteAt += noteSamples
                notePitch *= semitone
                noteFrom = noteEnv
                noteRamp = 0f
                noteEnv = 1f
            }
            val noteGain = if (noteRamp >= 1f) noteEnv else {
                noteRamp = min(1f, noteRamp + noteRampStep)
                noteFrom + (noteEnv - noteFrom) * noteRamp
            }
            val p = i.toFloat() / n
            val sweep = (1f + s.sweep * p).coerceAtLeast(0.22f)
            val hz = s.hz * pitchFactor * sweep * notePitch
            phase += TAU * hz / RATE
            modPhase += TAU * hz * (if (s.type == BRONZE) 1.41 else 1.89) / RATE
            second += TAU * hz * 1.997 / RATE

            val white = noise()
            val mid = low.step(white) * 0.58f + band.step(white) * 0.67f
            val treble = high.step(white) * 0.19f
            val grit = mid + treble
            val fundamental = when (s.type) {
                BRONZE, STEEL -> sin(phase + (if (s.type == BRONZE) 1.15 else 2.0) * sin(modPhase)).toFloat()
                MOTOR -> (sin(phase) * 0.68 + sin(phase * 0.51) * 0.32).toFloat()
                ENERGY, BOOM_ENERGY -> (sin(phase) * 0.78 + sin(modPhase) * 0.22).toFloat()
                UI -> (sin(phase) * 0.86 + sin(second) * 0.14).toFloat()
                else -> sin(phase).toFloat()
            }
            var pluck = 0f
            if (isString) {
                pluck = string[stringIdx]
                val following = if (stringIdx + 1 == string.size) 0 else stringIdx + 1
                string[stringIdx] = (string[stringIdx] + string[following]) * 0.5f * resonance
                stringIdx = following
            }
            val texture = if (s.type == POWDER || boom) 1.32f else 1f
            val attack = grit * envAttack * s.attack * texture
            val body = (fundamental * 0.78f + pluck * 0.48f) * envBody * s.body * noteGain
            val material = if (isString) pluck * envBody * 0.35f else
                if (s.type == BRONZE || s.type == STEEL) sin(modPhase * 0.64).toFloat() * envBody * 0.17f else 0f
            val dry = attack + body + material + grit * envBody * s.texture * 0.3f
            // Trzy ciche opóźnienia dają wrażenie pomieszczenia, bez osobnej biblioteki DSP.
            val a = echo1[i % echo1.size]
            val b = echo2[i % echo2.size]
            val c = echo3[i % echo3.size]
            val wet = a * 0.13f + b * 0.09f + c * 0.06f
            echo1[i % echo1.size] = dry + a * 0.3f
            echo2[i % echo2.size] = dry + b * 0.26f
            echo3[i % echo3.size] = dry + c * 0.22f
            // D63: tony (UI) wchodzą w 4 ms, uderzenia w 1,8 ms; wyjście kosinusowe, dłuższe
            // dla dłuższych efektów (12 % długości, 15–40 ms) — bez urwanego ogona.
            val fadeIn = min(1f, i / fadeInSamples)
            val left = (n - 1 - i).toFloat()
            val fadeOut = if (left >= fadeOutSamples) 1f
                else (0.5f - 0.5f * cos(PI * left / fadeOutSamples).toFloat()).coerceAtLeast(0f)
            val raw = (dry + wet) * fadeIn * fadeOut
            // Tanie, miękkie nasycenie bez ostrych harmonicznych obcinania.
            val shaped = raw / (1f + 0.2f * abs(raw))
            out[i] = shaped
            envAttack *= attackRate
            envBody *= bodyRate
            noteEnv *= noteFall
        }
        normalize(out, loudnessTarget(id, s.type))
        return out
    }

    /**
     * D63: docelowa głośność K-ważona (LUFS liczone po samym zdarzeniu) według rodziny efektu.
     * Dawniej normalizacja RMS według typu barwy: przy tym samym RMS jasne efekty (stal, energia)
     * brzmiały do 3–4 LU głośniej niż ciemne (drewno, kamień), a rodziny rozjeżdżały się o 3 LU.
     * Poziomy rodzin = dawne średnie, więc miks bitwy i odstęp muzyki (−7 dB) zostają.
     */
    private fun loudnessTarget(id: Int, type: Int): Double = when (id) {
        SoundSynth.HIT, in SoundSynth.HIT_WOOD..SoundSynth.HIT_ENERGY, SoundSynth.HIT_POWDER -> -18.3
        SoundSynth.SHOT, SoundSynth.ARROW, SoundSynth.LASER,
        in SoundSynth.SHOT_SLING..SoundSynth.SHOT_PLASMA -> -19.0
        in SoundSynth.TURRET_PRIMITIVE..SoundSynth.TURRET_ENERGY -> -19.6
        SoundSynth.DEATH, in SoundSynth.DEATH_HUMAN..SoundSynth.DEATH_MACHINE -> -19.3
        SoundSynth.EXPLOSION, SoundSynth.EXPLOSION_PRIMITIVE, SoundSynth.EXPLOSION_ENERGY -> -20.7
        in SoundSynth.BARRAGE_STONE..SoundSynth.BARRAGE_ORBITAL -> -20.6
        SoundSynth.EVOLVE, in SoundSynth.EVOLVE_PRIMITIVE..SoundSynth.EVOLVE_FUTURE -> -21.4
        SoundSynth.THUMP -> -19.1
        SoundSynth.BOSS -> -20.3
        else -> if (type == UI) -20.3 else -19.5
    }

    private const val PEAK = 0.76f
    private const val KNEE = 0.56f

    // Filtr K (ITU-R BS.1770) dla 22 050 Hz: półka +4 dB powyżej ~1,7 kHz i górnoprzepustowy 38 Hz.
    private val kShelf: DoubleArray
    private val kHigh: DoubleArray
    init {
        var k = kotlin.math.tan(PI * 1681.974450955533 / RATE)
        var q = 0.7071752369554196
        val vh = 10.0.pow(3.999843853973347 / 20.0)
        val vb = vh.pow(0.4996667741545416)
        var a0 = 1.0 + k / q + k * k
        kShelf = doubleArrayOf((vh + vb * k / q + k * k) / a0, 2.0 * (k * k - vh) / a0,
            (vh - vb * k / q + k * k) / a0, 2.0 * (k * k - 1.0) / a0, (1.0 - k / q + k * k) / a0)
        k = kotlin.math.tan(PI * 38.13547087602444 / RATE)
        q = 0.5003270373238773
        a0 = 1.0 + k / q + k * k
        kHigh = doubleArrayOf(1.0, -2.0, 1.0, 2.0 * (k * k - 1.0) / a0, (1.0 - k / q + k * k) / a0)
    }

    /** Głośność K-ważona całego klipu (LUFS bez bramkowania). */
    internal fun loudness(data: FloatArray): Double {
        var z1 = 0.0; var z2 = 0.0; var w1 = 0.0; var w2 = 0.0
        var sum = 0.0
        val a = kShelf
        val h = kHigh
        for (v in data) {
            val x = v.toDouble()
            val y = a[0] * x + z1
            z1 = a[1] * x - a[3] * y + z2
            z2 = a[2] * x - a[4] * y
            val o = h[0] * y + w1
            w1 = h[1] * y - h[3] * o + w2
            w2 = h[2] * y - h[4] * o
            sum += o * o
        }
        val ms = sum / max(1, data.size)
        return if (ms <= 1e-12) -99.0 else -0.691 + 10.0 * kotlin.math.log10(ms)
    }

    /**
     * Wzmocnienie do celu głośności i miękki ogranicznik szczytów (kolano 0,56, sufit 0,76 —
     * wspólny z [SfxMix.SAMPLE_PEAK]). Ogranicznik działa tylko na krótkich szczytach ataku;
     * drugi krok dociąga głośność straconą na ograniczaniu (najwyżej +1,5 dB).
     */
    private fun normalize(out: FloatArray, target: Double) {
        for (pass in 0 until 2) {
            val now = loudness(out)
            var gain = 10.0.pow((target - now) / 20.0).toFloat()
            gain = if (pass == 0) gain.coerceIn(0.05f, 4f) else gain.coerceIn(1f, 1.19f)
            for (i in out.indices) out[i] = limit(out[i] * gain)
        }
    }

    private fun limit(x: Float): Float {
        val a = abs(x)
        if (a <= KNEE) return x
        val range = PEAK - KNEE
        val y = KNEE + range * kotlin.math.tanh((a - KNEE) / range)
        return if (x < 0f) -y else y
    }
}
