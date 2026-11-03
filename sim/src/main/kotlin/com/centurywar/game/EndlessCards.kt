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

    // Zlecenie 108: wyłącznie nowe pozycje, zachowana kolejność zapisanych ordinali.
    FIELD_MEDICS(EndlessRarity.UNCOMMON),
    FINAL_SPARK(EndlessRarity.RARE),
    FIRST_GUARD(EndlessRarity.RARE),
    BOSS_RECALL(EndlessRarity.LEGENDARY),

    // Zlecenie 170: dopisane na końcu.
    QUARTERMASTER(EndlessRarity.COMMON),
    MASTER_MASONS(EndlessRarity.UNCOMMON),

    // Zlecenie 184 (dzień 6): dopisane na końcu.
    SAPPERS(EndlessRarity.UNCOMMON),
    SHIELD_WALL(EndlessRarity.COMMON),
    WAR_CHEST(EndlessRarity.RARE),
    /** Jednorazowe ryzyko: twardsze fale wroga za wyższy wynik za fale. */
    WAGER(EndlessRarity.RARE),
    ;
    val textKey: String = doctrine?.key ?: "ec_${name.lowercase()}"
}

/** Temat wpływa na rozkład ról w danej fali. */
enum class EndlessWaveTheme(
    /** Mnożnik prędkości jednostek fali; jedyne źródło dla generatora i walidatora zrzutu. */
    val unitSpeedMul: Float = 1f,
) {
    MIXED,
    RANGED_SWARM,
    ARMORED_COLUMN,
    FAST_RUSH,
    ELITE_GUARD,
    ARTILLERY,
    SIEGE_TRAIN(0.85f),
    SKIRMISHERS,
    PIKE_SCREEN,
    TWIN_RAID(1.15f),
    ;
    val textKey: String = "ew_${name.lowercase()}"

    companion object {
        /** Czy mnożnik prędkości jednostki może pochodzić z któregoś tematu fali. */
        fun isLegalUnitSpeedMul(value: Float): Boolean = entries.any { it.unitSpeedMul == value }
    }
}

/** Losowanie bez powtórzeń w obrębie oferty; brak alokacji podczas symulacji. */
object EndlessCards {
    const val MAX_PERSISTENT_STACKS = 8

    // Zlecenie 184: stałe nowych kart w jednym miejscu (symulacja, testy, opisy).
    /** Saperzy: ciężkie jednostki gracza zadają bossom +25% za pełny stos. */
    const val SAPPERS_BOSS_BONUS = 0.25f
    /** Mur tarcz: -10% obrażeń od pocisków za pełny stos, najwyżej -25%. */
    const val SHIELD_WALL_REDUCTION = 0.10f
    const val SHIELD_WALL_MIN_MUL = 0.75f
    /** Skarbiec wojenny: odsetek złota na początku fali i limit (× skala kosztów epoki). */
    const val WAR_CHEST_INTEREST = 0.04f
    const val WAR_CHEST_CAP = 40f
    /**
     * Zakład: HP jednostek kolejnych fal wroga i punkty za falę (zamiast 250).
     * Strojenie D6: ×1,25 (312) dawało +11,0% średniego wyniku, więc ×1,15 (docs/BALANCE.md).
     */
    const val WAGER_ENEMY_HP_MUL = 1.10f
    const val WAGER_WAVE_SCORE = 287L

    // Pierwsza karta ma pełną moc, kolejne 60%, 40% itd.; suma nigdy nie rośnie bez końca.
    private val stackPowers = floatArrayOf(1f, 0.6f, 0.4f, 0.3f, 0.2f, 0.15f, 0.1f, 0.05f)
    private val totalPowers = floatArrayOf(0f, 1f, 1.6f, 2f, 2.3f, 2.5f, 2.65f, 2.75f, 2.8f)
    val economicSynergyCards = arrayOf(
        EndlessCard.ECONOMY, EndlessCard.PLUNDER, EndlessCard.EXTRA_GOLD,
        EndlessCard.BLOOD_GOLD, EndlessCard.SCAVENGER, EndlessCard.WAR_CHEST,
    )
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
        EndlessCard.SUPPLY, EndlessCard.CITADEL_HEART,
        EndlessCard.FIRST_GUARD, EndlessCard.BOSS_RECALL, EndlessCard.WAGER -> false
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
                card == EndlessCard.EMBER_FORGE || card == EndlessCard.MASTER_MASONS) && side.slots == 0) return false
        if (card == EndlessCard.BANNER && !side.rallyUnlocked) return false
        if (card == EndlessCard.VIGILANCE && side.slots == 0 && (!fog || side.fogSight)) return false
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
        if ((card == EndlessCard.FIRST_GUARD || card == EndlessCard.BOSS_RECALL ||
                card == EndlessCard.WAGER) &&
            side.cardStacks[card.ordinal] >= 1) return false
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
