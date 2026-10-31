package com.centurywar.engine

/**
 * Czysta, powtarzalna synteza PCM mono 22 050 Hz. Indeksy próbek są trwałe
 * (używa ich SoundPool), a pliki WAV powstają wyłącznie w wątku roboczym.
 */
object SoundSynth {
    const val SAMPLE_RATE = 22_050
    const val VERSION = 3
    const val VARIANTS = 2
    const val MAX_CACHE_BYTES = 2 * 1_024 * 1_024

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
    const val PURCHASE = 52
    const val ERROR = 53
    const val REWARD = 54
    const val RANK_UP = 55
    const val COUNT = 56

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
            COIN, CLICK, PURCHASE, ERROR -> 0.12f
            in TURRET_PRIMITIVE..TURRET_ENERGY -> 0.28f
            MEDALS, ACHIEVEMENT, ERA_UNLOCK, REWARD, RANK_UP -> 0.46f
            BOSS -> 0.48f
            in CARD_COMMON..CARD_LEGENDARY, RESEARCH, STAR -> 0.28f
            else -> 0.16f
        }
    }

    fun wavBytes(id: Int): Int = 44 + (SAMPLE_RATE * duration(id)).toInt() * 2

    fun totalWavBytes(): Int {
        var total = 0
        for (id in 0 until COUNT) total += wavBytes(id) * VARIANTS
        return total
    }

    /** Wariant zmienia barwę i wysokość niezależnie od losowości symulacji. */
    fun render(id: Int, variant: Int = 0): FloatArray {
        require(id in 0 until COUNT)
        require(variant in 0 until VARIANTS)
        return SfxLayers.render(id, variant)
    }
}
