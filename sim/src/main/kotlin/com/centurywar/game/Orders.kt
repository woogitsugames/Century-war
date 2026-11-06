package com.centurywar.game

/**
 * Dzień 62: Rozkazy sztabu — stała, ręcznie ułożona kolekcja krótkich bitew z ograniczeniami
 * (docs/research/R14-nowy-tryb.md, propozycja B). Pierwsza wersja: 9 rozkazów, po jednym
 * na epokę. Nowe rozkazy dopisuje się WYŁĄCZNIE na końcu listy (indeks = zapis w profilu,
 * [BattleConfig.stageIndex] w zrzutach i powtórkach).
 *
 * Gwiazdki: cel główny = 1★, każdy z dwóch celów dodatkowych +1★ (liczone tylko po
 * wykonaniu celu głównego). Wszystkie rozkazy mają te same, zamrożone bonusy
 * ([Daily.normalizedBonuses]), więc badania nic nie zmieniają i ranking jest uczciwy.
 */
object Orders {
    /** Cel główny rozkazu. */
    enum class Goal {
        /** Zniszcz bazę wroga przed upływem limitu (limit = porażka). */
        DESTROY,
        /** Utrzymaj bazę do końca limitu (limit = zwycięstwo). */
        HOLD,
    }

    /** Rodzaj celu dodatkowego; [Objective.value] w jednostkach opisanych przy każdym. */
    enum class ObjectiveKind {
        /** Baza gracza ma na końcu co najmniej `value` % zdrowia. */
        BASE_HP,
        /** Bitwa wygrana w co najwyżej `value` sekund. */
        WITHIN,
        /** Co najmniej `value` pokonanych wrogów. */
        KILLS,
        /** Najwyżej `value` utraconych własnych jednostek. */
        LOSSES,
    }

    class Objective(val kind: ObjectiveKind, val value: Int = 0)

    class Order(
        val index: Int,
        /** Klucz tekstu: nazwa `o209_<key>`, odprawa `o209_<key>_d`. */
        val key: String,
        val era: Int,
        val maxEra: Int,
        val aiPower: Float,
        val goal: Goal,
        val limitSeconds: Int,
        val playerAllowed: Int,
        val enemyAllowed: Int = OrderRules.ALL,
        val modifiers: Set<Modifier> = emptySet(),
        val weather: Weather = Weather.CLEAR,
        val personality: AiPersonality? = null,
        val bonus: List<Objective>,
    ) {
        val nameKey: String get() = "o209_$key"
        val briefKey: String get() = "o209_${key}_d"
    }

    private const val NO_TURRETS = OrderRules.ALL and OrderRules.TURRETS.inv()

    /** Stała kolejność: nowe rozkazy tylko na końcu. Strojenie: docs/reports/d62-rozkazy.md. */
    val all: List<Order> = listOf(
        // Plemiona: obrona bez wież przed szturmowcem — piechota i proce trzymają linię.
        Order(0, "first_line", era = 0, maxEra = 0, aiPower = 4.5f, goal = Goal.HOLD,
            limitSeconds = 240, playerAllowed = NO_TURRETS, personality = AiPersonality.ASSAULT,
            bonus = listOf(Objective(ObjectiveKind.BASE_HP, 35), Objective(ObjectiveKind.KILLS, 55))),
        // Brąz: atak samą piechotą wręcz na obóz łuczników (wręcz bije dystans), kruche bazy.
        Order(1, "shield_wall", era = 1, maxEra = 1, aiPower = 0.7f, goal = Goal.DESTROY,
            limitSeconds = 360, modifiers = setOf(Modifier.FRAGILE_BASES),
            playerAllowed = OrderRules.MELEE or OrderRules.COMMANDER or OrderRules.BARRAGE or OrderRules.RALLY,
            enemyAllowed = OrderRules.RANGED or OrderRules.BARRAGE or OrderRules.RALLY,
            bonus = listOf(Objective(ObjectiveKind.WITHIN, 300), Objective(ObjectiveKind.BASE_HP, 20))),
        // Żelazo: strzelcy i ciężcy w deszczu (krótszy zasięg strzał) przeciw opancerzonym ciężkim.
        Order(2, "iron_rain", era = 2, maxEra = 2, aiPower = 0.7f, goal = Goal.DESTROY,
            limitSeconds = 360,
            playerAllowed = OrderRules.RANGED or OrderRules.HEAVY or OrderRules.TURRETS or
                OrderRules.BARRAGE or OrderRules.RALLY or OrderRules.COMMANDER,
            enemyAllowed = OrderRules.HEAVY or OrderRules.BARRAGE or OrderRules.RALLY,
            modifiers = setOf(Modifier.ARMORED_FOE, Modifier.FRAGILE_BASES), weather = Weather.RAIN,
            bonus = listOf(Objective(ObjectiveKind.BASE_HP, 25), Objective(ObjectiveKind.LOSSES, 55))),
        // Feudalizm: nocna obrona, obie strony bez wież.
        Order(3, "night_watch", era = 3, maxEra = 3, aiPower = 3f, goal = Goal.HOLD,
            limitSeconds = 240, playerAllowed = OrderRules.ALL,
            modifiers = setOf(Modifier.NO_TURRETS), weather = Weather.NIGHT,
            bonus = listOf(Objective(ObjectiveKind.BASE_HP, 40), Objective(ObjectiveKind.KILLS, 55))),
        // Proch: wyścig do Epoki pary (awans w trakcie bitwy), wróg bez wież, kruche bazy.
        Order(4, "powder_race", era = 4, maxEra = 5, aiPower = 0.85f, goal = Goal.DESTROY,
            limitSeconds = 360, playerAllowed = OrderRules.ALL, enemyAllowed = NO_TURRETS,
            modifiers = setOf(Modifier.FRAGILE_BASES),
            bonus = listOf(Objective(ObjectiveKind.WITHIN, 320), Objective(ObjectiveKind.BASE_HP, 25))),
        // Para: obrona samymi strzelcami, wieżami i ostrzałem.
        Order(5, "steam_battery", era = 5, maxEra = 5, aiPower = 3f, goal = Goal.HOLD,
            limitSeconds = 240,
            playerAllowed = OrderRules.RANGED or OrderRules.TURRETS or OrderRules.BARRAGE,
            bonus = listOf(Objective(ObjectiveKind.BASE_HP, 45), Objective(ObjectiveKind.LOSSES, 60))),
        // Stal: ciężka pięść we mgle — tylko ciężcy, elity i dowódca przeciw piechocie.
        Order(6, "steel_fist", era = 6, maxEra = 6, aiPower = 1.4f, goal = Goal.DESTROY,
            limitSeconds = 300,
            playerAllowed = OrderRules.HEAVY or OrderRules.ELITE or OrderRules.COMMANDER or
                OrderRules.TURRETS or OrderRules.BARRAGE or OrderRules.RALLY,
            enemyAllowed = OrderRules.MELEE or OrderRules.BARRAGE or OrderRules.RALLY,
            modifiers = setOf(Modifier.FOG_OF_WAR),
            bonus = listOf(Objective(ObjectiveKind.WITHIN, 255), Objective(ObjectiveKind.KILLS, 150))),
        // Krzem: błyskawica — szybkie, kruche armie, mało czasu.
        Order(7, "blitz", era = 7, maxEra = 7, aiPower = 0.2f, goal = Goal.DESTROY,
            limitSeconds = 240, playerAllowed = OrderRules.ALL, enemyAllowed = NO_TURRETS,
            modifiers = setOf(Modifier.FAST_UNITS, Modifier.GLASS_CANNON),
            bonus = listOf(Objective(ObjectiveKind.BASE_HP, 90), Objective(ObjectiveKind.LOSSES, 40))),
        // Era orbitalna: ostatni bastion na wietrze przeciw silniejszemu oblegającemu.
        Order(8, "last_bastion", era = 8, maxEra = 8, aiPower = 2f, goal = Goal.HOLD,
            limitSeconds = 300, playerAllowed = OrderRules.ALL, weather = Weather.WIND,
            personality = AiPersonality.SIEGER,
            bonus = listOf(Objective(ObjectiveKind.BASE_HP, 10), Objective(ObjectiveKind.KILLS, 75))),
    )

    const val COUNT = 9

    /**
     * Presja oblężnicza w Rozkazach: od 90. sekundy, +80%/min (jak etapy 2–7 kampanii, ale
     * wcześniej). Bez niej bitwa 3–6 min prawie nigdy nie dochodzi do bazy, a obrona jest
     * za łatwa; z nią rozstrzyga kontrola frontu (docs/reports/d62-rozkazy.md).
     */
    const val SIEGE_START = 90f
    const val SIEGE_RAMP = 0.8f
    const val MAX_STARS = 3

    /** Medale za każdą NOWĄ gwiazdkę (pierwsza, druga, trzecia); powtórki bez medali. */
    val STAR_MEDALS = intArrayOf(10, 15, 20)

    /** Punkty rankingu za rozkaz: gwiazdki × 1000 + zdrowie bazy (0–500). */
    const val POINTS_PER_STAR = 1000
    const val MAX_HP_POINTS = 500

    fun config(order: Order, seed: Long): BattleConfig = BattleConfig(
        mode = Mode.ORDERS, seed = seed, aiPower = order.aiPower,
        startEra = order.era, maxEra = order.maxEra, modifiers = order.modifiers,
        playerBonuses = Daily.normalizedBonuses(), stageIndex = order.index,
        aiPersonality = order.personality, weather = order.weather,
        playerAllowed = order.playerAllowed, enemyAllowed = order.enemyAllowed,
        timeLimitSteps = order.limitSeconds * 60, holdWins = order.goal == Goal.HOLD,
    )

    /** Rozkaz bitwy (null poza trybem Rozkazów albo przy nieznanym indeksie). */
    fun of(cfg: BattleConfig): Order? =
        if (cfg.mode == Mode.ORDERS) all.getOrNull(cfg.stageIndex) else null

    fun mainDone(battle: Battle): Boolean = battle.over && battle.winner == 0

    fun objectiveDone(objective: Objective, battle: Battle): Boolean {
        if (!mainDone(battle)) return false
        val p = battle.player
        val e = battle.enemy
        return when (objective.kind) {
            ObjectiveKind.BASE_HP -> p.baseHp * 100f >= p.baseMaxHp * objective.value
            ObjectiveKind.WITHIN -> battle.time <= objective.value.toFloat()
            ObjectiveKind.KILLS -> p.kills >= objective.value
            ObjectiveKind.LOSSES -> e.kills <= objective.value
        }
    }

    /** 0, gdy cel główny niewykonany; inaczej 1 + liczba wykonanych celów dodatkowych. */
    fun stars(order: Order, battle: Battle): Int {
        if (!mainDone(battle)) return 0
        return 1 + order.bonus.count { objectiveDone(it, battle) }
    }

    /** Punkty rankingu sztabu za tę bitwę (0 przy porażce). */
    fun points(order: Order, battle: Battle): Int {
        val stars = stars(order, battle)
        if (stars == 0) return 0
        val hp = (battle.player.baseHp / battle.player.baseMaxHp).coerceIn(0f, 1f)
        return stars * POINTS_PER_STAR + (hp * MAX_HP_POINTS).toInt()
    }

    /** Medale za przejście z [old] do [new] gwiazdek (tylko nowe gwiazdki). */
    fun medalsFor(old: Int, new: Int): Int {
        var sum = 0
        for (s in old.coerceIn(0, MAX_STARS) until new.coerceIn(0, MAX_STARS)) sum += STAR_MEDALS[s]
        return sum
    }

    /**
     * Rozkaz epoki odblokowuje się razem z epoką w kampanii: rozkaz 1 po samouczku (etap 1),
     * rozkaz epoki e ≥ 1 po zdobyciu etapu 4e (pierwszy etap tej epoki jest wtedy dostępny).
     * [campaignStars]: gwiazdki etapów kampanii z profilu.
     */
    fun unlocked(order: Order, campaignStars: IntArray): Boolean {
        val needed = if (order.era == 0) 0 else order.era * 4 - 1
        return campaignStars.getOrElse(needed) { 0 } > 0
    }

    /** Numer etapu kampanii (od 1), którego zdobycie odblokowuje rozkaz. */
    fun unlockStageNumber(order: Order): Int = if (order.era == 0) 1 else order.era * 4
}
