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
    FORCE_DIESEL("m_diesel"),
    FORCE_TESLA("m_tesla"),
    FORCE_CYBER("m_cyber"),
    FORCE_BIO("m_bio"),
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
)

class Stage(
    val index: Int,
    val nameKey: String,
    val startEra: Int,
    val maxEra: Int,
    val aiPower: Float,
    val modifiers: Set<Modifier>,
)

object Campaign {
    /**
     * 32 etapy, po 4 na epokę. Każdy etap ma ograniczony zakres epok, więc bitwa trwa
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
        for (i in 0 until 32) {
            val era = i / 4
            val sub = i % 4
            val start = if (sub == 3) maxOf(0, era - 2) else era
            val max = minOf(Defs.ERA_COUNT - 1, era + if (sub >= 2) 1 else 0)
            val power = 0.72f + i * 0.028f + sub * 0.03f
            val basic = if (i < 2) emptySet() else modsCycle[(i * 3 + sub) % modsCycle.size]
            val forced = when (i) {
                16 -> Modifier.FORCE_DIESEL
                17 -> Modifier.FORCE_TESLA
                24 -> Modifier.FORCE_CYBER
                25 -> Modifier.FORCE_BIO
                else -> null
            }
            val mods = if (forced == null) basic else basic + forced
            add(Stage(i, "stage_${i + 1}", start, max, power, mods))
        }
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
    )
}

object Daily {
    /** Numer dnia (UTC) — ten sam dla wszystkich graczy na świecie. */
    fun dayNumber(nowMillis: Long = System.currentTimeMillis()): Long = nowMillis / 86_400_000L

    fun config(day: Long, bonuses: Bonuses): BattleConfig {
        val rng = Rng(day * 7919L + 17L)
        val all = Modifier.values().filter { it.ordinal <= Modifier.FOG_OF_WAR.ordinal }
        val mods = LinkedHashSet<Modifier>()
        val count = 2 + rng.nextInt(2)
        while (mods.size < count) mods.add(all[rng.nextInt(all.size)])
        val start = rng.nextInt(7)
        val maxEra = minOf(Defs.ERA_COUNT - 1, start + 3)
        if (rng.nextInt(3) == 0) {
            val branchEra = if (start <= 6 && maxEra >= 6 && rng.nextInt(2) == 0) 6
                else if (start <= 4 && maxEra >= 4) 4 else if (start <= 6 && maxEra >= 6) 6 else -1
            if (branchEra == 4) mods.add(if (rng.nextInt(2) == 0) Modifier.FORCE_DIESEL else Modifier.FORCE_TESLA)
            if (branchEra == 6) mods.add(if (rng.nextInt(2) == 0) Modifier.FORCE_CYBER else Modifier.FORCE_BIO)
        }
        // Modyfikator wariantu i seed są wspólne dla wszystkich w danym dniu UTC.
        return BattleConfig(Mode.DAILY, day, 1.1f + rng.nextFloat() * 0.25f, start, maxEra, mods, normalizedBonuses(bonuses))
    }

    private fun normalizedBonuses(b: Bonuses) = Bonuses(
        popCap = 14, turretSlots = 3, eliteUnlocked = true, rallyUnlocked = true,
    )
}

/** Mały, deterministyczny generator (xorshift). Ten sam seed daje tę samą bitwę na każdym telefonie. */
class Rng(seed: Long) {
    private var s: Long = if (seed == 0L) 0x9E3779B97F4A7C15uL.toLong() else seed xor 0x5DEECE66DL

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
