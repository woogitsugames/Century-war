package com.centurywar.game

/**
 * Doktryny są wyborem 1 z 3 przy każdym awansie epoki (i co kilka fal w trybie bez końca).
 * Dzięki nim kolejne podejścia różnią się od siebie i nie ma jednej dominującej strategii.
 */
enum class Doctrine(val key: String) {
    ECONOMY("d_economy"),      // +25% przychodu
    SIEGE("d_siege"),          // +30% obrażeń wobec baz, +20% obrażeń wież
    BULWARK("d_bulwark"),      // +5% HP bazy (leczy), +3% HP jednostek
    SWIFT("d_swift"),          // +25% szybkości szkolenia, +10% szybkości ruchu
    VETERAN("d_veteran"),      // +30% XP
    FURY("d_fury"),            // +8% obrażeń jednostek
    ARSENAL("d_arsenal"),      // +1 slot wieży (do limitu) i -35% kosztu wież
    TACTICS("d_tactics"),      // -25% odnowienia zdolności
    PLUNDER("d_plunder"),      // +6% złota za zabójstwa (karta Endless +35%)
    BANNER("d_banner"),       // -15% obrażeń podczas Zrywu
    SUPPLY("d_supply"),       // +15 złota w skali epoki, +30% za zabójstwa przez 60 s
    SIEGE_MASTERS("d_siege_masters"), // +18% ciężkich przeciw bazom
    VIGILANCE("d_vigilance"), // +18% zasięgu wież, zwiad przez mgłę
}

/** Modyfikatory bitwy: kampania i wyzwanie dnia zmieniają nimi zasady. */
enum class Modifier(val key: String) {
    DOUBLE_INCOME("m_income"),
    FRAGILE_BASES("m_fragile"),
    FAST_UNITS("m_fast"),
    NO_TURRETS("m_noturrets"),
    RANGED_HORDE("m_ranged"),
    GLASS_CANNON("m_glass"),
    RICH_START("m_rich"),
    SLOW_XP("m_slowxp"),
    ARMORED_FOE("m_armored"),
    FOG_OF_WAR("m_fog"),
    SHORT_DAY("m_shortday"),
    VETERANS("m_veterans"),
    EXPENSIVE_TOWERS("m_heavytowers"),
    MERCENARIES("m_mercenaries"),
}

/** Warunki atmosferyczne mają osobny model, aby nie zmieniać losowania modyfikatorów dnia. */
enum class Weather(
    val key: String,
    val projectileRange: Float,
    val turretRange: Float,
    val moveSpeed: Float,
    val burnDamage: Float,
) {
    CLEAR("w_clear", 1f, 1f, 1f, 1f),
    RAIN("w_rain", 0.9f, 0.9f, 0.92f, 0.5f),
    WIND("w_wind", 1f, 1f, 1f, 1f),
    NIGHT("w_night", 0.95f, 0.85f, 1f, 1f);

    companion object {
        const val WIND_WITH = 1.1f
        const val WIND_AGAINST = 0.9f
        const val WIND_SHIFT_SECONDS = 75
    }
}

/** Nowe tryby zawsze na końcu (ordinal w zrzutach i powtórkach); ORDERS: dzień 62. */
enum class Mode { CAMPAIGN, SKIRMISH, ENDLESS, DAILY, ORDERS }

/**
 * Dzień 62 (Rozkazy sztabu): maska działań dozwolonych jednej stronie bitwy
 * ([BattleConfig.playerAllowed], [BattleConfig.enemyAllowed]). [ALL] = bez ograniczeń,
 * więc wszystkie dotychczasowe tryby działają bez zmian.
 */
object OrderRules {
    const val MELEE = 1
    const val RANGED = 2
    const val HEAVY = 4
    const val ELITE = 8
    const val COMMANDER = 16
    const val TURRETS = 32
    const val BARRAGE = 64
    const val RALLY = 128
    const val ALL = 255

    fun roleBit(role: Role): Int = when (role) {
        Role.MELEE -> MELEE
        Role.RANGED -> RANGED
        Role.HEAVY -> HEAVY
        Role.ELITE -> ELITE
    }
}

/** Jawny mnożnik gospodarki i doświadczenia AI w kampanii. */
enum class CampaignDifficulty(val powerMultiplier: Float, val medalMultiplier: Float, val key: String) {
    EASY(0.8f, 0.75f, "campaign126_easy"),
    NORMAL(1f, 1f, "campaign126_normal"),
    HARD(1.2f, 1.3f, "campaign126_hard"),
}

/** Reguła specjalna wyzwania dnia; kolejność jest częścią formatu powtórek. */
enum class DailyEvent(val key: String, val descriptionKey: String) {
    MARKSMEN("day_marksmen", "day_marksmen_d"),
    RAPID_ABILITIES("day_rapid", "day_rapid_d"),
    FREE_BARRAGE("day_barrage", "day_barrage_d"),
    FORTIFIED_BASES("day_fortress", "day_fortress_d"),
    GOLD_RUSH("day_gold", "day_gold_d"),
    MIRROR_ARMY("day_mirror", "day_mirror_d"),
    // Zlecenie 170: nowe dni dopisane na końcu (ordinal w powtórkach i zapisach).
    SIEGE_WORKS("day_siege_works", "day_siege_works_d"),
    FORCED_MARCH("day_forced_march", "day_forced_march_d"),
    SPOILS_OF_WAR("day_spoils", "day_spoils_d"),
    // Zlecenie 184: losowane dopiero od Daily.EVENTS_V3_DAY.
    HEAVY_DAY("day184_heavy", "day184_heavy_d"),
    TOWER_DUEL("day184_towers", "day184_towers_d"),
    // Dzień 58 (paczka 206): losowane dopiero od Daily.EVENTS_V4_DAY.
    SCHOLARS_DAY("day206_lore", "day206_lore_d"),
    FURY_DAY("day206_fury", "day206_fury_d"),
}

/** Mnożniki z badań gracza (metaprogresja). Liczy je [com.centurywar.meta.Profile]. */
class Bonuses(
    val startGold: Float = 0f,
    val incomeMul: Float = 1f,
    val unitHpMul: Float = 1f,
    val unitDmgMul: Float = 1f,
    val baseHpMul: Float = 1f,
    val turretDmgMul: Float = 1f,
    val trainMul: Float = 1f,
    val xpMul: Float = 1f,
    val popCap: Int = 12,
    val abilityCdMul: Float = 1f,
    val turretSlots: Int = 2,
    val eliteUnlocked: Boolean = true,
    val rallyUnlocked: Boolean = true,
)

class BattleConfig(
    val mode: Mode,
    val seed: Long,
    /** Siła AI: mnoży jej przychód i XP. 1.0 = te same zasady co gracz. */
    val aiPower: Float,
    val startEra: Int = 0,
    val maxEra: Int = Defs.ERA_COUNT - 1,
    val modifiers: Set<Modifier> = emptySet(),
    val playerBonuses: Bonuses = Bonuses(),
    val stageIndex: Int = -1,
    /** Samouczek (pierwszy etap kampanii): AI rusza później i ma mniej złota na start. */
    val tutorial: Boolean = false,
    /** Brak wyboru: osobowość deterministyczna z seeda. */
    val aiPersonality: AiPersonality? = null,
    /** Historyczne wyzwania dnia pozostają bez wariantów. */
    val variantsEnabled: Boolean = true,
    val weather: Weather = Weather.CLEAR,
    /** Numer tygodnia UTC; -1 oznacza zwykłe Endless. */
    val weekly: Long = -1L,
    /** Karta przyznawana na starcie tygodniowego oblężenia. */
    val weeklyCard: EndlessCard? = null,
    /** Specjalna reguła dnia, niezależna od zwykłych modyfikatorów. */
    val dailyEvent: DailyEvent? = null,
    /** Dzień 62: dozwolone działania gracza ([OrderRules]); domyślnie wszystkie. */
    val playerAllowed: Int = OrderRules.ALL,
    /** Dzień 62: dozwolone działania przeciwnika ([OrderRules]); domyślnie wszystkie. */
    val enemyAllowed: Int = OrderRules.ALL,
    /** Dzień 62: twardy limit bitwy w krokach symulacji; 0 = bez limitu. */
    val timeLimitSteps: Int = 0,
    /** Po upływie [timeLimitSteps]: true = wygrywa gracz (obrona), false = wygrywa przeciwnik. */
    val holdWins: Boolean = false,
)

class Stage(
    val index: Int,
    val nameKey: String,
    val startEra: Int,
    val maxEra: Int,
    val aiPower: Float,
    val modifiers: Set<Modifier>,
    val weather: Weather = Weather.CLEAR,
)

object Campaign {
    /**
     * Cztery etapy na epokę. Każdy etap ma ograniczony zakres epok, więc bitwa trwa
     * kilka minut, a cała kampania wiele godzin. Do tego dochodzą gwiazdki do zdobycia.
     */
    val stages: List<Stage> = buildList {
        val modsCycle = arrayOf(
            emptySet(),
            setOf(Modifier.RANGED_HORDE),
            setOf(Modifier.FAST_UNITS),
            setOf(Modifier.ARMORED_FOE),
            setOf(Modifier.FRAGILE_BASES),
            setOf(Modifier.NO_TURRETS),
            setOf(Modifier.GLASS_CANNON),
            setOf(Modifier.SLOW_XP, Modifier.RICH_START),
            setOf(Modifier.FOG_OF_WAR),
            setOf(Modifier.DOUBLE_INCOME, Modifier.ARMORED_FOE),
        )
        for (i in 0 until Defs.ERA_COUNT * 4) {
            val era = i / 4
            val sub = i % 4
            // Etapy 12 i 16 zaczynają epokę później; skracamy skok o cztery epoki.
            val start = if (sub == 3) maxOf(0, era - if (i == 11 || i == 15) 1 else 2) else era
            val max = minOf(Defs.ERA_COUNT - 1, era + if (sub >= 2) 1 else 0)
            val power = (0.72f + i * (0.868f / (Defs.ERA_COUNT * 4 - 1)) + sub * 0.03f) * stageRamp(i)
            val mods = if (i < 2) emptySet() else modsCycle[(i * 3 + sub) % modsCycle.size]
            // Pogoda co trzy etapy, począwszy od piątego; tutorial zostaje bez zmian.
            val weather = if (i >= 4 && (i - 4) % 3 == 0) when ((i - 4) / 3 % 3) {
                0 -> Weather.RAIN
                1 -> Weather.WIND
                else -> Weather.NIGHT
            } else Weather.CLEAR
            add(Stage(i, "stage_${i + 1}", start, max, power, mods, weather))
        }
    }

    /**
     * Mnożnik siły AI w drugiej połowie kampanii. Zmierzony w BalanceReport (docs/BALANCE.md):
     * etapy 1-18 strojone pod gracza z ~10% badań, 19-27 pod ~40%, 28-36 pod ~80%.
     * Badania dają dużą przewagę, więc bez tego późna kampania wygrywa się w minutę.
     */
    private fun stageRamp(i: Int): Float {
        val n = Defs.ERA_COUNT * 4
        val midStart = n / 2          // etap 19
        val lateStart = n * 3 / 4     // etap 28
        val ramp = when {
            i < midStart -> 1f
            i < lateStart -> 1f + 0.35f * (i - midStart + 1) / (lateStart - midStart)
            else -> 1.35f + 0.85f * (i - lateStart + 1) / (n - lateStart)
        }
        // Korekty trudności pojedynczych etapów, bez wpływu na jednostki ani AI.
        // Tura 8: etapy 1-6 obniżone po wyłączeniu elit wroga bez badania gracza
        // (profil „nowy” nie ma elit), 12/18/32 po strojeniu osobowości 127.
        // Tura 10: etapy 16-18 i 34-36 lżejsze (gracz z dowódcą, docs/BALANCE.md).
        // Dzień 1: etap 7 lżejszy o 5% po skróceniu etapów 2-7 (mniej gwiazdek i medali).
        // R11-02: etap 12 lżejszy o 2% (0,828 → 0,811) po skróceniu etapów 8-12.
        // Etapy 13-18 (4.11): po skróceniu etap 15 lżejszy o 4% (1 → 0,96), etap 16
        // trudniejszy o 1% (0,86 → 0,87); wygrane wracają do pasma ±5 pp (docs/BALANCE.md).
        // Dzień 35: etap 19 lżejszy o 6% (1,265 × 0,94 = 1,1891; ten sam aiPower co mnożnik
        // 0,94 w pomiarze d33); klif 18 → 19 profilu medale-czas 1,63× → 1,49× (docs/BALANCE.md).
        // Dzień 40 (K1 z d37): etapy 28 i 30-33 lżejsze (×0,90 / 0,92 / 0,88 / 0,92 / 0,92).
        // Wartości to najbliższe Floaty dające aiPower bitowo równy (stary aiPower × mnożnik),
        // czyli dokładnie to, co mierzył d37: 1 → 0,9; 0,989 → 0,90988004; 0,97 → 0,8536;
        // 0,97 → 0,8924001 (0,8924f daje inny aiPower); 1 → 0,92 (docs/reports/d40-k1.md).
        val correction = when (i + 1) {
            1 -> 0.799f
            2 -> 0.708f
            3 -> 0.81f
            4 -> 0.80f
            5 -> 0.80f
            6 -> 0.8245f
            7 -> 0.95f
            11 -> 0.97f
            12 -> 0.811f
            15 -> 0.96f
            16 -> 0.87f
            17 -> 0.92f
            18 -> 0.85f
            19 -> 1.1891f
            21 -> 1.08f
            22 -> 1.13f
            23 -> 0.96f
            24 -> 0.94f
            25 -> 0.97f
            27 -> 0.846f
            28 -> 0.9f
            29 -> 1.053f
            30 -> 0.90988004f
            31 -> 0.8536f
            32 -> 0.8924001f
            33 -> 0.92f
            34 -> 0.727f
            35 -> 0.778f
            36 -> 0.6075f
            else -> 1f
        }
        return ramp * correction
    }

    /**
     * [tutorial]: łagodniejsza wersja etapu do samouczka (słabsze AI, a etap 1 dopuszcza
     * jeden awans epoki, żeby dało się pokazać Awans i wybór doktryny).
     */
    /** Poziom to wyłącznie mnożnik już zapisywanego aiPower; bez zmiany stageRamp. */
    fun config(
        stage: Stage, bonuses: Bonuses, seed: Long, tutorial: Boolean = false,
        difficulty: CampaignDifficulty = CampaignDifficulty.NORMAL,
    ) = BattleConfig(
        Mode.CAMPAIGN, seed,
        stage.aiPower * difficulty.powerMultiplier * (if (tutorial) 0.8f else 1f),
        stage.startEra,
        if (tutorial) maxOf(stage.maxEra, minOf(Defs.ERA_COUNT - 1, stage.startEra + 1)) else stage.maxEra,
        stage.modifiers, bonuses, stage.index, tutorial,
        weather = stage.weather,
    )

    /**
     * Dzień 1: etapy 2–7 (indeksy 1–6) kończą się wcześniej, bo presja oblężnicza startuje
     * po 4 minutach zamiast 8 i rośnie o 80% na minutę zamiast 20%. Mediana bitwy spada
     * z ok. 13,5 do ok. 7,7 min, a nowy gracz dochodzi do dowódcy (etap 8) w ok. godzinę.
     * HP baz nic tu nie daje: bitwy tych etapów rozstrzyga blokada (docs/BALANCE.md).
     * Etap 1 (samouczek), etapy ≥ 8 i pozostałe tryby bez zmian; nie zależy od poziomu.
     */
    const val EARLY_SIEGE_START = 240f
    const val EARLY_SIEGE_RAMP = 0.8f
    val EARLY_SIEGE_STAGES = 1..6

    /**
     * R11-02: etapy 8–12 (indeksy 7–11, pierwsze z dowódcą) też krócej, ale łagodniej niż
     * 2–7: bez tego bitwa po dowódcy nagle trwała dwa razy dłużej (13,4 zamiast 7,7 min).
     * Wartości z pomiaru w docs/BALANCE.md (sekcja „R11-02”); nie zależy od poziomu.
     */
    const val SECOND_SIEGE_START = 300f
    const val SECOND_SIEGE_RAMP = 0.5f
    val SECOND_SIEGE_STAGES = 7..11

    /**
     * Etapy 13–18 (indeksy 12–17, decyzja właściciela 4.11): po R11-02 profil „medale” grał
     * je ok. 14 min, a 8–12 już ok. 9,5 min. Presja startuje po 6 minutach i rośnie o 40%/min,
     * czyli łagodniej niż w 8–12 (bez skoku po etapie 12). Pomiar: docs/BALANCE.md,
     * sekcja „Etapy 13–18”; nie zależy od poziomu.
     */
    const val THIRD_SIEGE_START = 360f
    const val THIRD_SIEGE_RAMP = 0.4f
    val THIRD_SIEGE_STAGES = 12..17

    private fun campaignStage(cfg: BattleConfig, stages: IntRange) =
        cfg.mode == Mode.CAMPAIGN && !cfg.tutorial && cfg.stageIndex in stages

    fun siegeStart(cfg: BattleConfig): Float = when {
        campaignStage(cfg, EARLY_SIEGE_STAGES) -> EARLY_SIEGE_START
        campaignStage(cfg, SECOND_SIEGE_STAGES) -> SECOND_SIEGE_START
        campaignStage(cfg, THIRD_SIEGE_STAGES) -> THIRD_SIEGE_START
        // Dzień 62: Rozkazy sztabu są krótkie (3–6 min), presja startuje wcześnie.
        cfg.mode == Mode.ORDERS -> Orders.SIEGE_START
        else -> Battle.SIEGE_START
    }

    /**
     * R11-02: zwrot gwiazdek z przyspieszonej presji; etapy 8–12 wyżej niż dzień 2 (0,6).
     * Ten sam zwrot w etapach 13–18.
     */
    const val SECOND_SIEGE_STAR_FORGIVENESS = 1.0f

    fun siegeStarForgiveness(cfg: BattleConfig): Float =
        if (campaignStage(cfg, SECOND_SIEGE_STAGES) || campaignStage(cfg, THIRD_SIEGE_STAGES))
            SECOND_SIEGE_STAR_FORGIVENESS
        else Battle.SIEGE_STAR_FORGIVENESS

    fun siegeRamp(cfg: BattleConfig): Float = when {
        campaignStage(cfg, EARLY_SIEGE_STAGES) -> EARLY_SIEGE_RAMP
        campaignStage(cfg, SECOND_SIEGE_STAGES) -> SECOND_SIEGE_RAMP
        campaignStage(cfg, THIRD_SIEGE_STAGES) -> THIRD_SIEGE_RAMP
        cfg.mode == Mode.ORDERS -> Orders.SIEGE_RAMP
        else -> Battle.SIEGE_RAMP
    }

    /** Odczyt poziomu z konfiguracji przechowanej również w snapshotach i powtórkach. */
    fun difficulty(cfg: BattleConfig): CampaignDifficulty? {
        if (cfg.mode != Mode.CAMPAIGN || cfg.stageIndex !in stages.indices) return null
        val base = stages[cfg.stageIndex].aiPower * (if (cfg.tutorial) 0.8f else 1f)
        return CampaignDifficulty.entries.firstOrNull {
            kotlin.math.abs(cfg.aiPower - base * it.powerMultiplier) < 0.0001f
        }
    }
}


/**
 * Jedenaście zestawów łączących istniejące modyfikatory z pogodą dnia
 * i kartą startową tygodnia. Kolejność i skład są stałe dla rankingu.
 *
 * Zlecenie 184 dopisało trzy zestawy przed premierą, gdy nie było jeszcze historycznych
 * tygodni tematycznych. Po premierze zmiana [COUNT] przestawiłaby rotację [forWeek],
 * więc kolejne zestawy wymagają własnego progu tygodnia (wzór: [Weekly.firstRuleSetWeek]).
 * Kolejność modyfikatorów w nowych zestawach jest odwrotna niż w parach [Daily], więc
 * [Daily.ruleSet] ich nie zwraca i dni pozostają bez zmian.
 *
 * Dzień 58 (paczka 206) dopisał dwa kolejne zestawy, także przed premierą
 * ([Daily.RELEASE_DAY] i [Weekly.firstRuleSetWeek] jeszcze nie nadeszły), z tą samą
 * zasadą odwróconej kolejności modyfikatorów.
 */
object ChallengeRuleSets {
    class RuleSet(
        val key: String,
        val descriptionKey: String,
        val first: Modifier,
        val second: Modifier,
        val weeklyCard: EndlessCard,
    )

    private val SETS = arrayOf(
        RuleSet("day109_gold", "day109_gold_d", Modifier.DOUBLE_INCOME, Modifier.FRAGILE_BASES,
            EndlessCard.ECONOMY),
        RuleSet("day109_march", "day109_march_d", Modifier.FAST_UNITS, Modifier.FOG_OF_WAR,
            EndlessCard.MARCH),
        RuleSet("day109_rain", "day109_rain_d", Modifier.RANGED_HORDE, Modifier.EXPENSIVE_TOWERS,
            EndlessCard.SHARPSHOOTERS),
        RuleSet("day109_veterans", "day109_veterans_d", Modifier.RICH_START, Modifier.VETERANS,
            EndlessCard.VETERAN),
        RuleSet("day109_hired", "day109_hired_d", Modifier.SLOW_XP, Modifier.MERCENARIES,
            EndlessCard.EXTRA_GOLD),
        RuleSet("day109_iron", "day109_iron_d", Modifier.GLASS_CANNON, Modifier.ARMORED_FOE,
            EndlessCard.BULWARK),
        // Zlecenie 184: dopisane na końcu.
        RuleSet("week184_night", "week184_night_d", Modifier.FOG_OF_WAR, Modifier.ARMORED_FOE,
            EndlessCard.VIGILANCE),
        // Strojenie D6: z Grabieżą mediana fali 41,5 (inne zestawy 8,5–12,5), z Marszem 12,5.
        RuleSet("week184_mercs", "week184_mercs_d", Modifier.MERCENARIES, Modifier.DOUBLE_INCOME,
            EndlessCard.MARCH),
        RuleSet("week184_bastion", "week184_bastion_d", Modifier.EXPENSIVE_TOWERS,
            Modifier.FRAGILE_BASES, EndlessCard.FRONTLINE_PLATE),
        // Dzień 58 (paczka 206): dopisane na końcu, z nowymi kartami jako kartą tygodnia.
        RuleSet("week206_blitz", "week206_blitz_d", Modifier.GLASS_CANNON, Modifier.FAST_UNITS,
            EndlessCard.WAR_HORNS),
        RuleSet("week206_pikes", "week206_pikes_d", Modifier.ARMORED_FOE, Modifier.RICH_START,
            EndlessCard.PIKE_SQUARE),
    )

    const val COUNT = 11

    /** Wszystkie zestawy w stałej kolejności (Kodeks, testy). */
    val all: List<RuleSet> get() = SETS.asList()

    fun forModifiers(first: Modifier, second: Modifier): RuleSet? =
        SETS.firstOrNull { it.first == first && it.second == second }

    fun forWeek(index: Long): RuleSet = SETS[Math.floorMod(index, COUNT.toLong()).toInt()]
}

/**
 * Oblężenie tygodnia: poniedziałek 00:00 UTC, jeden zestaw reguł na cały tydzień.
 * Kolejność i zawartość obu pul są częścią zasad historycznych tygodni.
 */
object Weekly {
    private val MODIFIERS = arrayOf(
        Modifier.DOUBLE_INCOME, Modifier.FRAGILE_BASES, Modifier.FAST_UNITS,
        Modifier.RANGED_HORDE, Modifier.GLASS_CANNON, Modifier.RICH_START,
        Modifier.SLOW_XP, Modifier.ARMORED_FOE, Modifier.FOG_OF_WAR,
    )
    // Krótki dzień dotyczy bitew z dwiema bazami, nie fal Endless.
    private val RELEASE_MODIFIERS = MODIFIERS + arrayOf(
        Modifier.VETERANS, Modifier.EXPENSIVE_TOWERS, Modifier.MERCENARIES,
    )
    private val START_CARDS = arrayOf(
        EndlessCard.ECONOMY, EndlessCard.BULWARK, EndlessCard.FURY,
        EndlessCard.VETERAN, EndlessCard.FRONTLINE_PLATE, EndlessCard.DRILL_SERGEANT,
        EndlessCard.EXTRA_GOLD, EndlessCard.FORTRESS, EndlessCard.SHARPSHOOTERS,
        EndlessCard.MARCH,
    )
    private val WAVE_THRESHOLDS = intArrayOf(10, 20, 30)
    private val MEDAL_REWARDS = intArrayOf(25, 60, 120)

    fun weekNumber(day: Long): Long = Math.floorDiv(day + 3L, 7L)
    fun threshold(index: Int): Int = WAVE_THRESHOLDS[index]
    fun medalReward(index: Int): Int = MEDAL_REWARDS[index]
    const val TIER_COUNT = 3

    /** Co który tydzień (licząc od [firstRuleSetWeek]) przypada zestaw tematyczny. */
    const val RULE_SET_EVERY = 3L

    /**
     * Tydzień premiery i dwa kolejne (trzy tygodnie) zachowują dotychczasowe losowanie;
     * zestawy tematyczne zaczynają się w czwartym tygodniu.
     */
    fun firstRuleSetWeek(): Long = weekNumber(Daily.RELEASE_DAY) + 3L

    /**
     * Zestaw tematyczny co [RULE_SET_EVERY] tygodnie, pozostałe tygodnie losowane jak dotąd.
     * Ten sam zestaw wraca dopiero po COUNT * RULE_SET_EVERY = 33 tygodniach.
     */
    fun ruleSet(week: Long): ChallengeRuleSets.RuleSet? {
        val offset = week - firstRuleSetWeek()
        if (offset < 0L || offset % RULE_SET_EVERY != 0L) return null
        return ChallengeRuleSets.forWeek(offset / RULE_SET_EVERY)
    }

    /** Czy losowa para pokrywa się z modyfikatorami któregoś zestawu tematycznego. */
    private fun matchesRuleSet(mods: Set<Modifier>): Boolean {
        val iter = mods.iterator()
        val a = iter.next()
        val b = iter.next()
        return ChallengeRuleSets.forModifiers(a, b) != null ||
            ChallengeRuleSets.forModifiers(b, a) != null
    }

    /**
     * Karta tygodnia musi przejść [EndlessCards.canOffer] na starcie. Czujność bez slotów
     * wież i bez mgły (albo z odsłoniętą mgłą) byłaby odrzucona, więc wtedy Twierdza.
     */
    internal fun startCard(card: EndlessCard, mods: Set<Modifier>): EndlessCard =
        if (card == EndlessCard.VIGILANCE && Modifier.NO_TURRETS in mods &&
            Modifier.FOG_OF_WAR !in mods) EndlessCard.FORTRESS else card

    fun config(week: Long): BattleConfig {
        require(week >= 0L) { "Nieprawidłowy numer tygodnia" }
        val seed = week * 104_729L + 211L
        val rng = Rng(seed)
        val start = rng.nextInt(6)
        val max = minOf(Defs.ERA_COUNT - 1, start + 2 + rng.nextInt(2))
        val pool = if (week >= weekNumber(Daily.RELEASE_DAY)) RELEASE_MODIFIERS else MODIFIERS
        val rule = ruleSet(week)
        val mods = linkedSetOf<Modifier>()
        if (rule == null) {
            while (mods.size < 2) mods.add(pool[rng.nextInt(pool.size)])
            // Od zestawów tematycznych losowy tydzień nie powtarza ich pary modyfikatorów;
            // dawne tygodnie (przed firstRuleSetWeek) zostają bez zmian.
            while (week >= firstRuleSetWeek() && matchesRuleSet(mods)) {
                mods.clear()
                while (mods.size < 2) mods.add(pool[rng.nextInt(pool.size)])
            }
        } else {
            mods.add(rule.first); mods.add(rule.second)
        }
        val card = rule?.let { startCard(it.weeklyCard, mods) }
            ?: START_CARDS[rng.nextInt(START_CARDS.size)]
        return BattleConfig(
            mode = Mode.ENDLESS, seed = seed, aiPower = 1f,
            startEra = start, maxEra = max, modifiers = mods,
            playerBonuses = Daily.normalizedBonuses(), weekly = week, weeklyCard = card,
        )
    }
}

object Daily {
    // Kolejność historycznych modyfikatorów jest częścią algorytmu wyzwania dnia.
    private val LEGACY_MODIFIERS = arrayOf(
        Modifier.DOUBLE_INCOME, Modifier.FRAGILE_BASES, Modifier.FAST_UNITS,
        Modifier.NO_TURRETS, Modifier.RANGED_HORDE, Modifier.GLASS_CANNON,
        Modifier.RICH_START, Modifier.SLOW_XP, Modifier.ARMORED_FOE, Modifier.FOG_OF_WAR,
    )

    // Zamrożona kolejność 14 modyfikatorów: dopisanie enumów nie przelosuje starych dni.
    private val RELEASE_MODIFIERS = arrayOf(
        Modifier.DOUBLE_INCOME, Modifier.FRAGILE_BASES, Modifier.FAST_UNITS,
        Modifier.NO_TURRETS, Modifier.RANGED_HORDE, Modifier.GLASS_CANNON,
        Modifier.RICH_START, Modifier.SLOW_XP, Modifier.ARMORED_FOE,
        Modifier.FOG_OF_WAR, Modifier.SHORT_DAY, Modifier.VETERANS,
        Modifier.EXPENSIVE_TOWERS, Modifier.MERCENARIES,
    )
    // 90 unikalnych par bez sprzeczności „bez wież” + „drogie wieże”.
    private val RULE_PAIRS: List<Pair<Modifier, Modifier>> = buildList {
        for (i in RELEASE_MODIFIERS.indices) for (j in i + 1 until RELEASE_MODIFIERS.size) {
            val a = RELEASE_MODIFIERS[i]
            val b = RELEASE_MODIFIERS[j]
            if (a == Modifier.NO_TURRETS && b == Modifier.EXPENSIVE_TOWERS) continue
            add(a to b)
        }
    }

    private fun pairFor(day: Long): Pair<Modifier, Modifier> {
        val position = Math.floorMod(day - RULES_V2_DAY, RULE_PAIRS.size.toLong()).toInt()
        // 37 i 90 są względnie pierwsze: każde 90 kolejnych dni ma inne pary.
        return RULE_PAIRS[(position * 37 + 13) % RULE_PAIRS.size]
    }

    fun ruleSet(day: Long): ChallengeRuleSets.RuleSet? {
        if (day < RULES_V2_DAY) return null
        val pair = pairFor(day)
        return ChallengeRuleSets.forModifiers(pair.first, pair.second)
    }

    /** Pierwszy dzień UTC pełnej zawartości wyzwania dnia (żelazo, warianty i pogoda). */
    // Przed pierwszym wydaniem ustaw na faktyczny dzień publikacji; potem NIGDY nie zmieniaj.
    // 21153 (2027-12-01 UTC) to wyłącznie data zastępcza do czasu ustalenia premiery.
    const val RELEASE_DAY = 21153L

    /** Zestawy v2 od następnego cyklu; dni 0–3 po premierze mają przypięte powtórki. */
    const val RULES_V2_DAY = RELEASE_DAY + 4L

    /** Od tego dnia (dwa cykle po premierze) losujemy także dni dodane w zleceniu 170. */
    const val EVENTS_V2_DAY = RELEASE_DAY + 8L

    /** Od tego dnia losujemy także wydarzenia z zlecenia 184 (zamrożona jedenastka [EVENTS_V3]). */
    const val EVENTS_V3_DAY = RELEASE_DAY + 15L

    /**
     * Dzień 58 (paczka 206): od tego dnia (kolejny dzień wydarzenia po [EVENTS_V3_DAY])
     * losujemy pełną listę [DailyEvent] z Dniem nauki i Dniem furii.
     */
    const val EVENTS_V4_DAY = RELEASE_DAY + 19L

    // Zamrożona jedenastka z zlecenia 184: dni od EVENTS_V3_DAY do EVENTS_V4_DAY bez zmian.
    private val EVENTS_V3 = arrayOf(
        DailyEvent.MARKSMEN, DailyEvent.RAPID_ABILITIES, DailyEvent.FREE_BARRAGE,
        DailyEvent.FORTIFIED_BASES, DailyEvent.GOLD_RUSH, DailyEvent.MIRROR_ARMY,
        DailyEvent.SIEGE_WORKS, DailyEvent.FORCED_MARCH, DailyEvent.SPOILS_OF_WAR,
        DailyEvent.HEAVY_DAY, DailyEvent.TOWER_DUEL,
    )

    // Zamrożona dziewiątka z zlecenia 170: dni od EVENTS_V2_DAY do EVENTS_V3_DAY bez zmian.
    private val EVENTS_V2 = arrayOf(
        DailyEvent.MARKSMEN, DailyEvent.RAPID_ABILITIES, DailyEvent.FREE_BARRAGE,
        DailyEvent.FORTIFIED_BASES, DailyEvent.GOLD_RUSH, DailyEvent.MIRROR_ARMY,
        DailyEvent.SIEGE_WORKS, DailyEvent.FORCED_MARCH, DailyEvent.SPOILS_OF_WAR,
    )

    // Zamrożona pierwsza szóstka: dopisanie zdarzeń nie przelosuje wcześniejszych dni.
    private val EVENTS_V1 = arrayOf(
        DailyEvent.MARKSMEN, DailyEvent.RAPID_ABILITIES, DailyEvent.FREE_BARRAGE,
        DailyEvent.FORTIFIED_BASES, DailyEvent.GOLD_RUSH, DailyEvent.MIRROR_ARMY,
    )

    /**
     * Jedyna lista sprzeczności wydarzenie–modyfikator (dni od [EVENTS_V2_DAY]):
     * opis wydarzenia przeczy modyfikatorowi albo go powtarza.
     */
    val EVENT_CONFLICTS: Map<DailyEvent, Set<Modifier>> = mapOf(
        // „Wieże tańsze i szybsze” przy „Bez wież” albo „Ciężkich wieżach”.
        DailyEvent.SIEGE_WORKS to setOf(Modifier.NO_TURRETS, Modifier.EXPENSIVE_TOWERS),
        // Dwa razy „szybsze jednostki” (×1,25 i ×1,5 prędkości).
        DailyEvent.FORCED_MARCH to setOf(Modifier.FAST_UNITS),
        // „Bazy mają dwa razy więcej zdrowia” przy „Kruchych bazach” (×2 i ×0,5).
        DailyEvent.FORTIFIED_BASES to setOf(Modifier.FRAGILE_BASES),
        // „Tylko strzelcy” czyni „Hordę strzelców” pustą.
        DailyEvent.MARKSMEN to setOf(Modifier.RANGED_HORDE),
        // „Dodatkowy slot wieży” przy „Bez wież” albo „Ciężkich wieżach”.
        DailyEvent.TOWER_DUEL to setOf(Modifier.NO_TURRETS, Modifier.EXPENSIVE_TOWERS),
        // „Szybsze doświadczenie” przy „Wolnym doświadczeniu” (×1,5 i ×0,6).
        DailyEvent.SCHOLARS_DAY to setOf(Modifier.SLOW_XP),
        // Dwa razy „więcej obrażeń” (Szklane działo ×1,5 i furia ×1,2).
        DailyEvent.FURY_DAY to setOf(Modifier.GLASS_CANNON),
    )

    fun conflicts(event: DailyEvent, mods: Set<Modifier>): Boolean =
        EVENT_CONFLICTS[event]?.any { it in mods } == true

    fun dayNumber(nowMillis: Long = System.currentTimeMillis()): Long = nowMillis / 86_400_000L

    fun config(day: Long, bonuses: Bonuses): BattleConfig =
        if (day < RELEASE_DAY) legacyConfig(day) else generatedConfig(day, historical = false)

    /** Zamrożone zasady sprzed premiery: stare nasiona i epoki pozostają bez zmian. */
    private fun legacyConfig(day: Long): BattleConfig = generatedConfig(day, historical = true)

    private fun generatedConfig(day: Long, historical: Boolean): BattleConfig {
        val rng = Rng(day * 7919L + 17L)
        val all = if (historical) LEGACY_MODIFIERS else RELEASE_MODIFIERS
        val rolledMods = HashSet<Modifier>()
        val count = 2 + rng.nextInt(2)
        while (rolledMods.size < count) rolledMods.add(all[rng.nextInt(all.size)])
        val pair = if (historical || day < RULES_V2_DAY) null else pairFor(day)
        val mods: Set<Modifier> = if (pair == null) rolledMods
            else linkedSetOf(pair.first, pair.second)
        // Stare dni zachowują losowanie sprzed dodania żelaza. Numer epoki >=2
        // otrzymuje tylko nowy indeks, nie zmieniając historycznej pary epok.
        val rolledStart = if (historical) rng.nextInt(3) else rng.nextInt(6) // zamrożone: kolejna epoka nie może przelosować przyszłych dni
        val start = if (historical && rolledStart >= 2) rolledStart + 1 else rolledStart
        val rolledMax = if (historical) minOf(7, rolledStart + 3) else minOf(Defs.ERA_COUNT - 1, rolledStart + 3)
        val maxEra = if (historical && rolledMax >= 2) rolledMax + 1 else rolledMax
        val power = 1.1f + rng.nextFloat() * 0.25f
        // Drugi, niezależny strumień: nie przesuwa żadnego starego losowania.
        val weather = if (historical) Weather.CLEAR
            else Weather.entries[Rng(day * 65537L + 109L).nextInt(Weather.entries.size)]

        // Osobny strumień nie wpływa na losowania epok, AI, modyfikatorów i pogody.
        // Czwarty dzień cyklu to RELEASE_DAY + 3; przeszłe dni pozostają bez zmian.
        val event = if (!historical && (day - RELEASE_DAY) % 4L == 3L) {
            // Dni sprzed EVENTS_V2_DAY losują z zamrożonej szóstki (przypięte powtórki).
            val pool = when {
                day < EVENTS_V2_DAY -> EVENTS_V1
                day < EVENTS_V3_DAY -> EVENTS_V2
                day < EVENTS_V4_DAY -> EVENTS_V3
                else -> DailyEvent.entries.toTypedArray()
            }
            val eventRng = Rng(day * 131_071L + 907L)
            val first = pool[eventRng.nextInt(pool.size)]
            // Od EVENTS_V2_DAY wydarzenie nie może przeczyć modyfikatorom dnia: przy kolizji
            // drugie losowanie z tego samego strumienia, tylko spośród zgodnych wydarzeń.
            // Pierwsze losowanie jest takie jak wcześniej, więc dni bez kolizji się nie zmieniają.
            if (day < EVENTS_V2_DAY || !conflicts(first, mods)) first
            else pool.filter { !conflicts(it, mods) }.let { it[eventRng.nextInt(it.size)] }
        } else null
        val eventStart = if (event == DailyEvent.GOLD_RUSH) 2 else start
        val eventMax = maxOf(maxEra, eventStart)
        val eventPower = if (event == DailyEvent.MIRROR_ARMY) 1f else power
        val personality = if (event == DailyEvent.MIRROR_ARMY)
            AiPersonality.entries[Rng(day * 1_000_003L + 71L).nextInt(AiPersonality.entries.size)]
        else null
        return BattleConfig(Mode.DAILY, day, eventPower,
            eventStart, eventMax, mods, normalizedBonuses(), aiPersonality = personality,
            variantsEnabled = !historical, weather = weather, dailyEvent = event)
    }

    internal fun normalizedBonuses() = Bonuses(
        popCap = 14, turretSlots = 3, eliteUnlocked = true, rallyUnlocked = true,
    )
}

/** Mały, deterministyczny generator (xorshift). Ten sam seed daje tę samą bitwę na każdym telefonie. */
class Rng(seed: Long) {
    private var s: Long = if (seed == 0L) 0x9E3779B97F4A7C15uL.toLong() else seed xor 0x5DEECE66DL

    // Osobny strumień decyzji dla każdego AI; obie pule są częścią snapshotu.
    private val aiStates = LongArray(2) { i ->
        val value = seed xor if (i == 0) 0x6A09E667F3BCC909L else 0x3C6EF372FE94F82BL
        if (value == 0L) 0x2545F4914F6CDD1DL else value
    }

    fun aiState(side: Int): Long = aiStates[side]
    fun setAiState(side: Int, state: Long) { aiStates[side] = state }

    private fun nextAiLong(side: Int): Long {
        var x = aiStates[side]
        x = x xor (x shl 13)
        x = x xor (x ushr 7)
        x = x xor (x shl 17)
        aiStates[side] = x
        return x
    }

    fun nextAiInt(side: Int, bound: Int): Int = ((nextAiLong(side) ushr 1) % bound).toInt()
    fun nextAiFloat(side: Int): Float = ((nextAiLong(side) ushr 40).toFloat() / (1L shl 24).toFloat())

    /** Wewnętrzny stan generatora (zapis/wczytanie bitwy w [BattleSnapshot]). */
    var state: Long
        get() = s
        set(v) { s = v }

    fun nextLong(): Long {
        var x = s
        x = x xor (x shl 13)
        x = x xor (x ushr 7)
        x = x xor (x shl 17)
        s = x
        return x
    }

    fun nextInt(bound: Int): Int = ((nextLong() ushr 1) % bound).toInt()
    fun nextFloat(): Float = ((nextLong() ushr 40).toFloat() / (1L shl 24).toFloat())
}
