package com.centurywar.game

/**
 * Doktryny są wyborem 1 z 3 przy każdym awansie epoki (i co kilka fal w trybie bez końca).
 * Dzięki nim kolejne podejścia różnią się od siebie i nie ma jednej dominującej strategii.
 */
enum class Doctrine(val key: String) {
    ECONOMY("d_economy"),      // +25% przychodu
    SIEGE("d_siege"),          // +30% obrażeń wobec baz, +20% obrażeń wież
    BULWARK("d_bulwark"),      // +20% HP bazy (leczy), +10% HP jednostek
    SWIFT("d_swift"),          // +25% szybkości szkolenia, +10% szybkości ruchu
    VETERAN("d_veteran"),      // +30% XP
    FURY("d_fury"),            // +12% obrażeń jednostek
    ARSENAL("d_arsenal"),      // +1 slot wieży (do limitu) i -20% kosztu wież
    TACTICS("d_tactics"),      // -25% odnowienia zdolności
    PLUNDER("d_plunder"),      // +35% złota za zabójstwa
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
        val correction = when (i + 1) {
            11 -> 0.97f
            12 -> 0.96f
            16 -> 0.97f
            21 -> 1.08f
            22 -> 1.13f
            23 -> 0.92f
            29 -> 1.12f
            30 -> 1.02f
            31 -> 0.97f
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
    fun config(stage: Stage, bonuses: Bonuses, seed: Long, tutorial: Boolean = false) = BattleConfig(
        Mode.CAMPAIGN, seed,
        if (tutorial) stage.aiPower * 0.8f else stage.aiPower,
        stage.startEra,
        if (tutorial) maxOf(stage.maxEra, minOf(Defs.ERA_COUNT - 1, stage.startEra + 1)) else stage.maxEra,
        stage.modifiers, bonuses, stage.index, tutorial,
        weather = stage.weather,
    )
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

    fun config(week: Long): BattleConfig {
        require(week >= 0L) { "Nieprawidłowy numer tygodnia" }
        val seed = week * 104_729L + 211L
        val rng = Rng(seed)
        val start = rng.nextInt(6)
        val max = minOf(Defs.ERA_COUNT - 1, start + 2 + rng.nextInt(2))
        val pool = if (week >= weekNumber(Daily.RELEASE_DAY)) RELEASE_MODIFIERS else MODIFIERS
        val mods = linkedSetOf<Modifier>()
        while (mods.size < 2) mods.add(pool[rng.nextInt(pool.size)])
        val card = START_CARDS[rng.nextInt(START_CARDS.size)]
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

    /** Pierwszy dzień UTC pełnej zawartości wyzwania dnia (żelazo, warianty i pogoda). */
    // Przed pierwszym wydaniem ustaw na faktyczny dzień publikacji; potem NIGDY nie zmieniaj.
    // 21153 (2027-12-01 UTC) to wyłącznie data zastępcza do czasu ustalenia premiery.
    const val RELEASE_DAY = 21153L

    fun dayNumber(nowMillis: Long = System.currentTimeMillis()): Long = nowMillis / 86_400_000L

    fun config(day: Long, bonuses: Bonuses): BattleConfig =
        if (day < RELEASE_DAY) legacyConfig(day) else generatedConfig(day, historical = false)

    /** Zamrożone zasady sprzed premiery: stare nasiona i epoki pozostają bez zmian. */
    private fun legacyConfig(day: Long): BattleConfig = generatedConfig(day, historical = true)

    private fun generatedConfig(day: Long, historical: Boolean): BattleConfig {
        val rng = Rng(day * 7919L + 17L)
        val all = if (historical) LEGACY_MODIFIERS else Modifier.values()
        val mods = HashSet<Modifier>()
        val count = 2 + rng.nextInt(2)
        while (mods.size < count) mods.add(all[rng.nextInt(all.size)])
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
        return BattleConfig(Mode.DAILY, day, power,
            start, maxEra, mods, normalizedBonuses(), variantsEnabled = !historical, weather = weather)
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
