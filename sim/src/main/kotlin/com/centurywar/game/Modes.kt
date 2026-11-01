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
    ARSENAL("d_arsenal"),      // +1 slot wieży (do limitu) i -20% kosztu wież
    TACTICS("d_tactics"),      // -25% odnowienia zdolności
    PLUNDER("d_plunder"),      // +8% złota za zabójstwa
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

enum class Mode { CAMPAIGN, SKIRMISH, ENDLESS, DAILY }

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
        val correction = when (i + 1) {
            1 -> 0.85f
            2 -> 0.753f
            3 -> 0.81f
            4 -> 0.80f
            5 -> 0.80f
            6 -> 0.85f
            11 -> 0.97f
            12 -> 0.828f
            16 -> 0.93f
            18 -> 0.92f
            19 -> 1.265f
            21 -> 1.08f
            22 -> 1.13f
            23 -> 0.96f
            24 -> 0.94f
            27 -> 0.846f
            29 -> 1.12f
            30 -> 1.02f
            31 -> 0.97f
            32 -> 0.97f
            34 -> 0.95f
            35 -> 0.88f
            36 -> 0.81f
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
 * Sześć zestawów łączących istniejące modyfikatory z pogodą dnia
 * i kartą startową tygodnia. Kolejność i skład są stałe dla rankingu.
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
    )

    const val COUNT = 6

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
     * Ten sam zestaw wraca dopiero po COUNT * RULE_SET_EVERY = 18 tygodniach.
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
        val card = rule?.weeklyCard ?: START_CARDS[rng.nextInt(START_CARDS.size)]
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
        val event = if (!historical && (day - RELEASE_DAY) % 4L == 3L)
            DailyEvent.entries[Rng(day * 131_071L + 907L).nextInt(DailyEvent.entries.size)]
        else null
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
