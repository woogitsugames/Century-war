package com.centurywar.game

/** Rzadkość kart; wagi dotyczą każdego egzemplarza z puli. */
enum class EndlessRarity(val weight: Int) {
    COMMON(40),
    UNCOMMON(20),
    RARE(9),
    LEGENDARY(3),
}

/** Doktryny i dodatkowe efekty dostępne wyłącznie w Oblężeniu bez końca. */
enum class EndlessCard(val rarity: EndlessRarity, val doctrine: Doctrine? = null) {
    ECONOMY(EndlessRarity.COMMON, Doctrine.ECONOMY),
    SIEGE(EndlessRarity.UNCOMMON, Doctrine.SIEGE),
    BULWARK(EndlessRarity.COMMON, Doctrine.BULWARK),
    SWIFT(EndlessRarity.COMMON, Doctrine.SWIFT),
    VETERAN(EndlessRarity.COMMON, Doctrine.VETERAN),
    FURY(EndlessRarity.COMMON, Doctrine.FURY),
    ARSENAL(EndlessRarity.UNCOMMON, Doctrine.ARSENAL),
    TACTICS(EndlessRarity.UNCOMMON, Doctrine.TACTICS),
    PLUNDER(EndlessRarity.COMMON, Doctrine.PLUNDER),

    TOWER_SLOT(EndlessRarity.UNCOMMON),
    FREE_ELITE(EndlessRarity.LEGENDARY),
    BURNING_TOWERS(EndlessRarity.RARE),
    AUTO_BARRAGE(EndlessRarity.LEGENDARY),
    BLOOD_GOLD(EndlessRarity.RARE),
    CLONING(EndlessRarity.RARE),
    FRONTLINE_PLATE(EndlessRarity.COMMON),
    DRILL_SERGEANT(EndlessRarity.COMMON),
    RAPID_TOWERS(EndlessRarity.UNCOMMON),
    MEDIC(EndlessRarity.COMMON),
    EXTRA_GOLD(EndlessRarity.COMMON),
    MOBILIZATION(EndlessRarity.UNCOMMON),
    FORTRESS(EndlessRarity.UNCOMMON),
    SHARPSHOOTERS(EndlessRarity.UNCOMMON),
    BERSERK(EndlessRarity.RARE),
    SCAVENGER(EndlessRarity.COMMON),
    MARCH(EndlessRarity.COMMON),
    RALLY_MASTER(EndlessRarity.UNCOMMON),
    ELITE_SCHOOL(EndlessRarity.RARE),
    LAST_STAND(EndlessRarity.RARE),
    REPAIR_CREW(EndlessRarity.UNCOMMON),
    BANNER(EndlessRarity.COMMON, Doctrine.BANNER),
    SUPPLY(EndlessRarity.COMMON, Doctrine.SUPPLY),
    SIEGE_MASTERS(EndlessRarity.UNCOMMON, Doctrine.SIEGE_MASTERS),
    VIGILANCE(EndlessRarity.UNCOMMON, Doctrine.VIGILANCE),

    // Zlecenie 71: nowe karty zawsze dopisujemy po istniejących (ordinal w zapisach).
    SUPPLY_LINE(EndlessRarity.COMMON),
    WAR_DRUMS(EndlessRarity.COMMON),
    IRON_CURTAIN(EndlessRarity.UNCOMMON),
    TOWER_NETWORK(EndlessRarity.UNCOMMON),
    MUSTER_BANNER(EndlessRarity.UNCOMMON),
    LAST_BASTION(EndlessRarity.RARE),
    EMBER_FORGE(EndlessRarity.RARE),
    CITADEL_HEART(EndlessRarity.LEGENDARY),
    ;
    val textKey: String = doctrine?.key ?: "ec_${name.lowercase()}"
}

/** Temat wpływa na rozkład ról w danej fali. */
enum class EndlessWaveTheme {
    MIXED,
    RANGED_SWARM,
    ARMORED_COLUMN,
    FAST_RUSH,
    ELITE_GUARD,
    ARTILLERY,
    SIEGE_TRAIN,
    SKIRMISHERS,
    ;
    val textKey: String = "ew_${name.lowercase()}"
}

/** Losowanie bez powtórzeń w obrębie oferty; brak alokacji podczas symulacji. */
object EndlessCards {
    const val MAX_PERSISTENT_STACKS = 8

    // Pierwsza karta ma pełną moc, kolejne 60%, 40% itd.; suma nigdy nie rośnie bez końca.
    private val stackPowers = floatArrayOf(1f, 0.6f, 0.4f, 0.3f, 0.2f, 0.15f, 0.1f, 0.05f)
    private val totalPowers = floatArrayOf(0f, 1f, 1.6f, 2f, 2.3f, 2.5f, 2.65f, 2.75f, 2.8f)
    private val cards = EndlessCard.values()
    private val themes = EndlessWaveTheme.values()

    fun stackPower(stacks: Int): Float =
        if (stacks in 1..MAX_PERSISTENT_STACKS) stackPowers[stacks - 1] else 0f

    fun totalStackPower(stacks: Int): Float =
        totalPowers[stacks.coerceIn(0, MAX_PERSISTENT_STACKS)]

    /** Jednorazowe nagrody oraz efekty ograniczone odrębnym limitem nie zużywają stosów mocy. */
    fun persistent(card: EndlessCard): Boolean = when (card) {
        EndlessCard.ARSENAL, EndlessCard.TOWER_SLOT, EndlessCard.FREE_ELITE,
        EndlessCard.AUTO_BARRAGE, EndlessCard.CLONING, EndlessCard.MEDIC,
        EndlessCard.EXTRA_GOLD, EndlessCard.MOBILIZATION, EndlessCard.REPAIR_CREW,
        EndlessCard.SUPPLY, EndlessCard.CITADEL_HEART -> false
        else -> true
    }

    fun draw(rng: Rng, side: Side, result: Array<EndlessCard>, fog: Boolean = false) {
        for (slot in result.indices) {
            var total = 0
            for (card in cards) if (available(card, side, result, slot, fog)) total += card.rarity.weight
            require(total > 0) { "Brak kart do wylosowania" }
            var roll = rng.nextInt(total)
            for (card in cards) {
                if (!available(card, side, result, slot, fog)) continue
                roll -= card.rarity.weight
                if (roll < 0) {
                    result[slot] = card
                    break
                }
            }
        }
    }

    fun nextTheme(rng: Rng): EndlessWaveTheme = themes[rng.nextInt(themes.size)]

    /** Kryteria ofert uwzględniają zarówno limity, jak i odblokowane mechaniki. */
    fun canOffer(card: EndlessCard, side: Side, fog: Boolean = false): Boolean {
        if (persistent(card) && side.cardStacks[card.ordinal] >= MAX_PERSISTENT_STACKS) return false
        if ((card == EndlessCard.ARSENAL || card == EndlessCard.TOWER_SLOT) &&
            (side.slots == 0 || side.slots >= Defs.MAX_TURRET_SLOTS)) return false
        if ((card == EndlessCard.BURNING_TOWERS || card == EndlessCard.RAPID_TOWERS ||
                card == EndlessCard.IRON_CURTAIN || card == EndlessCard.TOWER_NETWORK ||
                card == EndlessCard.EMBER_FORGE) && side.slots == 0) return false
        if (card == EndlessCard.BANNER && !side.rallyUnlocked) return false
        if (card == EndlessCard.VIGILANCE && !fog && side.slots == 0) return false
        if (card == EndlessCard.CLONING && side.cloneChance >= 0.6f) return false
        if (card == EndlessCard.FREE_ELITE && side.freeElitesPerWave >= 4) return false
        if ((card == EndlessCard.REPAIR_CREW || card == EndlessCard.CITADEL_HEART) &&
            side.repairPerWave >= 0.3f) return false
        if ((card == EndlessCard.MOBILIZATION || card == EndlessCard.MUSTER_BANNER) &&
            side.cap >= Battle.MAX_UNITS) return false
        if (card == EndlessCard.AUTO_BARRAGE && side.autoBarrageInterval in 0.001f..15f) return false
        if ((card == EndlessCard.TACTICS || card == EndlessCard.RALLY_MASTER) &&
            side.abilityCdMul <= Battle.MIN_ABILITY_CD_MUL) return false
        if (card == EndlessCard.MEDIC && side.baseHp >= side.baseMaxHp) return false
        if (card == EndlessCard.IRON_CURTAIN &&
            side.cardStacks[card.ordinal] >= 3) return false
        return true
    }

    private fun available(
        card: EndlessCard, side: Side, picks: Array<EndlessCard>, count: Int, fog: Boolean,
    ): Boolean {
        if (!canOffer(card, side, fog)) return false
        for (i in 0 until count) if (picks[i] == card) return false
        return true
    }
}

/** Typy i stała rotacja bossów; zero losowań i alokacji w kroku symulacji. */
object EndlessBosses {
    const val NONE = 0
    const val KNOCKBACK = 1
    const val AURA = 2
    const val SHIELD = 3
    const val RECRUITER = 4
    const val RAGE = 5
    const val MAX_KIND = RAGE

    const val AURA_RANGE = 380f
    const val SHIELD_HP = 0.5f
    const val RECRUIT_INTERVAL = 10f

    val nameKeys = arrayOf("", "eb_knockback", "eb_aura", "eb_shield", "eb_recruiter", "eb_rage")
    val descriptionKeys = arrayOf("", "eb_knockback_d", "eb_aura_d", "eb_shield_d", "eb_recruiter_d", "eb_rage_d")

    fun kindAtWave(wave: Int): Int =
        if (wave < 10 || wave % 10 != 0) NONE else (wave / 10 - 1) % MAX_KIND + 1

    fun secondaryAtWave(wave: Int): Int =
        if (wave < 60 || wave % 10 != 0) NONE else kindAtWave(wave) % MAX_KIND + 1
}
