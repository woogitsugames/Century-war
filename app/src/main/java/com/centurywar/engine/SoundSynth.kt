package com.centurywar.engine

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Czysta, powtarzalna synteza PCM mono 22 050 Hz. Indeksy próbek są trwałe
 * (używa ich SoundPool), a pliki WAV powstają wyłącznie w wątku roboczym.
 */
object SoundSynth {
    const val SAMPLE_RATE = 22_050
    const val VERSION = 2
    const val MAX_CACHE_BYTES = 1_024 * 1_024

    // Zachowane identyfikatory starych efektów.
    const val HIT = 0
    const val SHOT = 1
    const val ARROW = 2
    const val EXPLOSION = 3
    const val DEATH = 4
    const val LASER = 5
    const val EVOLVE = 6
    const val COIN = 7
    const val CLICK = 8
    const val VICTORY = 9
    const val DEFEAT = 10
    const val THUMP = 11

    const val HIT_WOOD = 12
    const val HIT_BRONZE = 13
    const val HIT_STEEL = 14
    const val HIT_ENERGY = 15
    const val DEATH_HUMAN = 16
    const val DEATH_BEAST = 17
    const val DEATH_MACHINE = 18
    const val SHOT_SLING = 19
    const val SHOT_BOW = 20
    const val SHOT_CROSSBOW = 21
    const val SHOT_MUSKET = 22
    const val SHOT_CANNON = 23
    const val SHOT_RIFLE = 24
    const val SHOT_GUN = 25
    const val SHOT_LASER = 26
    const val SHOT_PLASMA = 27
    const val TURRET_PRIMITIVE = 28
    const val TURRET_MEDIEVAL = 29
    const val TURRET_POWDER = 30
    const val TURRET_MODERN = 31
    const val TURRET_ENERGY = 32
    const val BARRAGE_STONE = 33
    const val BARRAGE_FIRE = 34
    const val BARRAGE_CANNON = 35
    const val BARRAGE_ROCKET = 36
    const val BARRAGE_ORBITAL = 37
    const val EVOLVE_PRIMITIVE = 38
    const val EVOLVE_MEDIEVAL = 39
    const val EVOLVE_INDUSTRIAL = 40
    const val EVOLVE_FUTURE = 41
    const val MEDALS = 42
    const val ACHIEVEMENT = 43
    const val CARD_COMMON = 44
    const val CARD_RARE = 45
    const val CARD_EPIC = 46
    const val CARD_LEGENDARY = 47
    const val RESEARCH = 48
    const val STAR = 49
    const val ERA_UNLOCK = 50
    const val BOSS = 51
    const val COUNT = 52

    fun duration(id: Int): Float {
        require(id in 0 until COUNT)
        return when (id) {
            HIT, HIT_WOOD, HIT_BRONZE, HIT_STEEL, HIT_ENERGY -> 0.13f
            SHOT, ARROW, LASER, in SHOT_SLING..SHOT_PLASMA -> 0.22f
            EXPLOSION, in BARRAGE_STONE..BARRAGE_ORBITAL -> 0.64f
            DEATH, in DEATH_HUMAN..DEATH_MACHINE -> 0.3f
            EVOLVE, in EVOLVE_PRIMITIVE..EVOLVE_FUTURE -> 0.68f
            VICTORY -> 0.82f
            DEFEAT -> 0.68f
            THUMP -> 0.16f
            COIN, CLICK -> 0.12f
            in TURRET_PRIMITIVE..TURRET_ENERGY -> 0.28f
            MEDALS, ACHIEVEMENT, ERA_UNLOCK -> 0.46f
            BOSS -> 0.48f
            in CARD_COMMON..CARD_LEGENDARY, RESEARCH, STAR -> 0.28f
            else -> 0.16f
        }
    }

    fun wavBytes(id: Int): Int = 44 + (SAMPLE_RATE * duration(id)).toInt() * 2

    fun totalWavBytes(): Int {
        var total = 0
        for (id in 0 until COUNT) total += wavBytes(id)
        return total
    }

    private fun tone(freq: Float, t: Float): Float =
        sin(2.0 * PI * freq * t).toFloat()

    /** Każdy wariant ma inny szum/rezonans, ale jego ponowne wygenerowanie daje te same bajty. */
    fun render(id: Int): FloatArray {
        val seconds = duration(id)
        val n = (SAMPLE_RATE * seconds).toInt()
        val out = FloatArray(n)
        var rng = 0x13579BDF xor (id * 0x45D9F3B)
        var low = 0f
        for (i in 0 until n) {
            val t = i.toFloat() / SAMPLE_RATE
            val p = t / seconds
            rng = rng xor (rng shl 13)
            rng = rng xor (rng ushr 17)
            rng = rng xor (rng shl 5)
            val noise = ((rng ushr 8) and 65535) / 32768f - 1f
            val filter = when (id) {
                HIT, HIT_WOOD, SHOT_SLING, TURRET_PRIMITIVE, BARRAGE_STONE -> 0.09f
                HIT_BRONZE, SHOT_MUSKET, TURRET_POWDER, BARRAGE_CANNON -> 0.26f
                HIT_ENERGY, SHOT_LASER, SHOT_PLASMA, TURRET_ENERGY -> 0.52f
                else -> 0.17f
            }
            low += (noise - low) * filter
            val decay = exp(-t * 13f)
            val raw = when (id) {
                HIT, HIT_WOOD -> low * exp(-t * 40f) + tone(160f, t) * exp(-t * 55f) * 0.4f
                HIT_BRONZE -> (tone(540f, t) + tone(871f, t) * 0.65f) * exp(-t * 30f) * 0.6f + low * decay * 0.3f
                HIT_STEEL -> (tone(920f, t) + tone(1373f, t) * 0.8f) * exp(-t * 26f) * 0.6f + noise * decay * 0.25f
                HIT_ENERGY -> tone(1650f - 850f * p, t) * exp(-t * 18f) * 0.7f + tone(3300f - 500f * p, t) * decay * 0.2f
                DEATH, DEATH_HUMAN -> tone(330f - 190f * p, t) * exp(-t * 10f) * 0.8f + low * decay * 0.22f
                DEATH_BEAST -> tone(170f - 110f * p, t) * exp(-t * 8f) * 0.9f + low * exp(-t * 18f) * 0.4f
                DEATH_MACHINE -> tone(700f - 640f * p, t) * exp(-t * 7f) * 0.65f + noise * exp(-t * 9f) * 0.35f
                SHOT_SLING -> low * exp(-t * 16f) * 0.75f + tone(260f + 600f * p, t) * exp(-t * 17f) * 0.35f
                ARROW, SHOT_BOW -> low * sin(PI * p).toFloat() * 0.45f + tone(1050f - 430f * p, t) * exp(-t * 15f) * 0.26f
                SHOT_CROSSBOW -> noise * exp(-t * 38f) * 0.5f + tone(430f, t) * exp(-t * 25f) * 0.32f
                SHOT, SHOT_MUSKET -> noise * exp(-t * 26f) * 0.75f + tone(110f, t) * exp(-t * 16f) * 0.4f
                SHOT_CANNON -> low * exp(-t * 9f) * 1.1f + tone(70f - 25f * p, t) * exp(-t * 10f) * 0.7f
                SHOT_RIFLE -> noise * exp(-t * 40f) * 0.65f + tone(210f, t) * exp(-t * 27f) * 0.35f
                SHOT_GUN -> noise * exp(-t * 33f) * 0.9f + tone(83f, t) * exp(-t * 24f) * 0.5f
                LASER, SHOT_LASER -> tone(1850f - 1450f * p, t) * exp(-t * 9f) * 0.65f + tone(3000f, t) * exp(-t * 18f) * 0.2f
                SHOT_PLASMA -> tone(420f + 1100f * p, t) * exp(-t * 7f) * 0.6f + low * exp(-t * 11f) * 0.35f
                TURRET_PRIMITIVE -> low * exp(-t * 16f) * 0.7f + tone(110f, t) * exp(-t * 24f) * 0.5f
                TURRET_MEDIEVAL -> tone(420f, t) * exp(-t * 20f) * 0.65f + low * exp(-t * 16f) * 0.48f
                TURRET_POWDER -> noise * exp(-t * 20f) * 0.85f + tone(90f, t) * exp(-t * 12f) * 0.75f
                TURRET_MODERN -> noise * exp(-t * 23f) * 0.75f + tone(135f, t) * exp(-t * 12f) * 0.6f
                TURRET_ENERGY -> tone(1400f - 850f * p, t) * exp(-t * 9f) + tone(700f, t) * exp(-t * 20f) * 0.26f
                EXPLOSION, BARRAGE_CANNON -> low * exp(-t * 4.5f) * 1.3f + tone(65f - 20f * p, t) * exp(-t * 7f) * 0.7f
                BARRAGE_STONE -> low * exp(-t * 8f) * 0.85f + tone(90f, t) * exp(-t * 12f) * 0.55f
                BARRAGE_FIRE -> low * exp(-t * 4f) * 0.8f + tone(340f + 80f * p, t) * exp(-t * 5f) * 0.35f
                BARRAGE_ROCKET -> low * exp(-t * 5f) + tone(140f + 250f * p, t) * exp(-t * 4f) * 0.5f
                BARRAGE_ORBITAL -> tone(380f + 1900f * p, t) * exp(-t * 3f) * 0.6f +
                    tone(65f, t) * exp(-t * 5f) * 0.65f + low * exp(-t * 5f) * 0.4f
                EVOLVE, EVOLVE_PRIMITIVE, EVOLVE_MEDIEVAL, EVOLVE_INDUSTRIAL, EVOLVE_FUTURE,
                VICTORY, DEFEAT, MEDALS, ACHIEVEMENT, ERA_UNLOCK, RESEARCH, STAR,
                CARD_COMMON, CARD_RARE, CARD_EPIC, CARD_LEGENDARY, COIN, CLICK, BOSS -> {
                    val stepLength = when (id) {
                        VICTORY -> 0.14f
                        DEFEAT -> 0.17f
                        MEDALS, ACHIEVEMENT, ERA_UNLOCK -> 0.1f
                        EVOLVE, in EVOLVE_PRIMITIVE..EVOLVE_FUTURE -> 0.13f
                        else -> 0.075f
                    }
                    val note = min(5, (t / stepLength).toInt())
                    val local = t - note * stepLength
                    val base = when (id) {
                        EVOLVE_PRIMITIVE -> 196f; EVOLVE_MEDIEVAL, EVOLVE -> 262f
                        EVOLVE_INDUSTRIAL -> 330f; EVOLVE_FUTURE -> 440f
                        VICTORY, ACHIEVEMENT -> 392f; DEFEAT -> 330f
                        MEDALS, COIN -> 680f; CLICK -> 920f
                        CARD_COMMON -> 440f; CARD_RARE -> 540f
                        CARD_EPIC -> 660f; CARD_LEGENDARY -> 840f
                        RESEARCH -> 520f; STAR -> 784f; ERA_UNLOCK -> 440f
                        BOSS -> 105f; else -> 550f
                    }
                    val direction = if (id == DEFEAT || id == BOSS) -1f else 1f
                    val f = base * (1f + direction * note * (if (id == BOSS) 0.1f else 0.16f))
                    val hit = tone(f, local) * exp(-local * 11f) * 0.57f +
                        tone(f * (if (id == EVOLVE_FUTURE || id == CARD_LEGENDARY) 2.04f else 2f), local) *
                        exp(-local * 19f) * 0.16f
                    val tail = if (id == BOSS) low * exp(-t * 7f) * 0.3f else 0f
                    (hit + tail) * (if (id == CLICK) 0.22f else 0.78f)
                }
                THUMP -> tone(77f, t) * exp(-t * 24f) * 0.8f + low * exp(-t * 30f) * 0.3f
                else -> 0f
            }
            val attack = min(1f, t * 550f)
            val release = min(1f, (n - i).toFloat() / (SAMPLE_RATE * 0.012f))
            // Ograniczenie szczytu bez wzmacniania szumu w próbkach wybrzmiewających.
            out[i] = (raw * attack * release * 0.72f).coerceIn(-0.96f, 0.96f)
        }
        return out
    }
}
