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
}

/** Temat wpływa na rozkład ról w danej fali. */
enum class EndlessWaveTheme {
    MIXED,
    RANGED_SWARM,
    ARMORED_COLUMN,
    FAST_RUSH,
    ELITE_GUARD,
    ARTILLERY,
}

/** Losowanie bez powtórzeń w obrębie oferty; brak alokacji podczas symulacji. */
object EndlessCards {
    private val cards = EndlessCard.values()
    private val themes = EndlessWaveTheme.values()

    fun draw(rng: Rng, side: Side, result: Array<EndlessCard>) {
        for (slot in result.indices) {
            var total = 0
            for (card in cards) if (available(card, side, result, slot)) total += card.rarity.weight
            require(total > 0) { "Brak kart do wylosowania" }
            var roll = rng.nextInt(total)
            for (card in cards) {
                if (!available(card, side, result, slot)) continue
                roll -= card.rarity.weight
                if (roll < 0) {
                    result[slot] = card
                    break
                }
            }
        }
    }

    fun nextTheme(rng: Rng): EndlessWaveTheme = themes[rng.nextInt(themes.size)]

    private fun available(card: EndlessCard, side: Side, picks: Array<EndlessCard>, count: Int): Boolean {
        if ((card == EndlessCard.ARSENAL || card == EndlessCard.TOWER_SLOT) &&
            (side.slots == 0 || side.slots >= Defs.MAX_TURRET_SLOTS)) return false
        if ((card == EndlessCard.BURNING_TOWERS || card == EndlessCard.RAPID_TOWERS) && side.slots == 0) return false
        for (i in 0 until count) if (picks[i] == card) return false
        return true
    }
}
