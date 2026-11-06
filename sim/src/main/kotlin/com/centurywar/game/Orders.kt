package com.centurywar.game

/**
 * Dzień 62: Rozkazy sztabu — stała, ręcznie ułożona kolekcja krótkich bitew z ograniczeniami
 * (docs/research/R14-nowy-tryb.md, propozycja B). Pierwsza wersja: 9 rozkazów, po jednym
 * na epokę. Nowe rozkazy dopisuje się WYŁĄCZNIE na końcu listy (indeks = zapis w profilu,
 * [BattleConfig.stageIndex] w zrzutach i powtórkach).
 *
 * Dzień 66: druga partia (rozkazy 10–18, `o211_*`), trudniejsza, z nowymi rodzajami celów:
 * eskorta dowódcy ([Goal.ESCORT], [Goal.SPEARHEAD]), obrona samymi wieżami (maska),
 * obcięty dochód ([Order.incomePct]) i cele dodatkowe [ObjectiveKind.COMMANDER_ALIVE],
 * [ObjectiveKind.GOLD_LEFT], [ObjectiveKind.FIELDED_MAX].
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
        /** Dzień 66: utrzymaj bazę i dowódcę do końca limitu; śmierć dowódcy = porażka. */
        ESCORT,
        /** Dzień 66: zniszcz bazę wroga w limicie, nie tracąc dowódcy (śmierć = porażka). */
        SPEARHEAD;

        /** Upływ limitu oznacza zwycięstwo (obrona). */
        val holdWins: Boolean get() = this == HOLD || this == ESCORT
        /** Gracz zaczyna z dowódcą, którego musi utrzymać przy życiu ([OrderFlags.ESCORT]). */
        val escort: Boolean get() = this == ESCORT || this == SPEARHEAD
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
        /** Dzień 66: dowódca gracza żyje na końcu bitwy (`value` nieużywane). */
        COMMANDER_ALIVE,
        /** Dzień 66: w skarbcu zostaje na końcu co najmniej `value` złota. */
        GOLD_LEFT,
        /** Dzień 66: na polu stanęło najwyżej `value` własnych jednostek (z dowódcą i najemnikami). */
        FIELDED_MAX,
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
        /** Dzień 66: dochód gracza w % (mnożnik [Bonuses.incomeMul]); 100 = bez zmian. */
        val incomePct: Int = 100,
    ) {
        /** Partia (0 = rozkazy 1–9, 1 = rozkazy 10–18); prefiks tekstów `o209_`/`o211_`. */
        val batch: Int get() = index / BATCH
        private val prefix: String get() = if (batch == 0) "o209_" else "o211_"
        val nameKey: String get() = "$prefix$key"
        val briefKey: String get() = "$prefix${key}_d"
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

        // ---- Dzień 66: partia 2 (rozkazy 10–18), trudniejsza, nowe rodzaje celów.
        // Plemiona: eskorta — wódz stoi za linią obrony od pierwszej chwili i musi przeżyć.
        Order(9, "chief_guard", era = 0, maxEra = 0, aiPower = 4.7f, goal = Goal.ESCORT,
            limitSeconds = 210, playerAllowed = NO_TURRETS, personality = AiPersonality.ASSAULT,
            bonus = listOf(Objective(ObjectiveKind.BASE_HP, 57), Objective(ObjectiveKind.KILLS, 52))),
        // Brąz: obrona wyłącznie wieżami i ostrzałem.
        Order(10, "bronze_towers", era = 1, maxEra = 1, aiPower = 1.25f, goal = Goal.HOLD,
            limitSeconds = 210, playerAllowed = OrderRules.TURRETS or OrderRules.BARRAGE,
            bonus = listOf(Objective(ObjectiveKind.BASE_HP, 40), Objective(ObjectiveKind.KILLS, 36))),
        // Żelazo: chuda zima — połowa dochodu, przetrwaj i zachowaj zapas.
        Order(11, "lean_winter", era = 2, maxEra = 2, aiPower = 3.2f, goal = Goal.HOLD,
            limitSeconds = 240, playerAllowed = OrderRules.ALL, incomePct = 50,
            bonus = listOf(Objective(ObjectiveKind.GOLD_LEFT, 100), Objective(ObjectiveKind.FIELDED_MAX, 70))),
        // Feudalizm: grot — nocny atak kierowany z tyłów przez dowódcę, który musi przeżyć.
        Order(12, "night_herald", era = 3, maxEra = 3, aiPower = 0.85f, goal = Goal.SPEARHEAD,
            limitSeconds = 360, playerAllowed = OrderRules.ALL, weather = Weather.NIGHT,
            bonus = listOf(Objective(ObjectiveKind.WITHIN, 315), Objective(ObjectiveKind.LOSSES, 80))),
        // Proch: zniszcz w ulewie (wolniejszy marsz, krótszy zasięg), oszczędnie, dowódca przy życiu.
        Order(13, "wet_powder", era = 4, maxEra = 4, aiPower = 0.4f, goal = Goal.DESTROY,
            limitSeconds = 315, playerAllowed = OrderRules.ALL, weather = Weather.RAIN,
            modifiers = setOf(Modifier.FRAGILE_BASES),
            bonus = listOf(Objective(ObjectiveKind.FIELDED_MAX, 88), Objective(ObjectiveKind.COMMANDER_ALIVE))),
        // Para: obrona przy 75% dochodu przeciw silnemu wrogowi, z zapasem w skarbcu.
        Order(14, "counter_steam", era = 5, maxEra = 5, aiPower = 2.8f, goal = Goal.HOLD,
            limitSeconds = 270, playerAllowed = OrderRules.ALL, incomePct = 75,
            bonus = listOf(Objective(ObjectiveKind.GOLD_LEFT, 250), Objective(ObjectiveKind.KILLS, 70))),
        // Stal: eskorta we mgle — ciężcy i dowódca trzymają pozycję.
        Order(15, "fog_escort", era = 6, maxEra = 6, aiPower = 3.7f, goal = Goal.ESCORT,
            limitSeconds = 240, modifiers = setOf(Modifier.FOG_OF_WAR),
            playerAllowed = OrderRules.HEAVY or OrderRules.ELITE or OrderRules.COMMANDER or
                OrderRules.TURRETS or OrderRules.BARRAGE or OrderRules.RALLY,
            bonus = listOf(Objective(ObjectiveKind.BASE_HP, 42), Objective(ObjectiveKind.LOSSES, 31))),
        // Krzem: chudy budżet na wietrze — zniszcz bazę przy 70% dochodu, z rezerwą złota.
        Order(16, "thin_signal", era = 7, maxEra = 7, aiPower = 0.69f, goal = Goal.DESTROY,
            limitSeconds = 330, playerAllowed = OrderRules.ALL, incomePct = 70, weather = Weather.WIND,
            bonus = listOf(Objective(ObjectiveKind.GOLD_LEFT, 1000), Objective(ObjectiveKind.WITHIN, 285))),
        // Era orbitalna: ostatni grot — dowódca prowadzi szturm na oblegającego.
        Order(17, "final_spear", era = 8, maxEra = 8, aiPower = 0.8f, goal = Goal.SPEARHEAD,
            limitSeconds = 360, playerAllowed = OrderRules.ALL, personality = AiPersonality.SIEGER,
            weather = Weather.WIND,
            bonus = listOf(Objective(ObjectiveKind.BASE_HP, 30), Objective(ObjectiveKind.FIELDED_MAX, 82))),
    )

    const val COUNT = 18

    /** Liczba rozkazów w partii (po jednym na epokę). */
    const val BATCH = 9

    /** Dzień 66: gwiazdki w rozkazie tej samej epoki z partii 1, które odblokowują rozkaz partii 2. */
    const val BATCH2_UNLOCK_STARS = 2

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
        playerBonuses = bonuses(order), stageIndex = order.index,
        aiPersonality = order.personality, weather = order.weather,
        playerAllowed = order.playerAllowed, enemyAllowed = order.enemyAllowed,
        timeLimitSteps = order.limitSeconds * 60, holdWins = order.goal.holdWins,
        orderFlags = if (order.goal.escort) OrderFlags.ESCORT else 0,
    )

    /** Zamrożone bonusy rozkazów; dzień 66: obcięty dochód przez [Order.incomePct]. */
    private fun bonuses(order: Order): Bonuses {
        val base = Daily.normalizedBonuses()
        if (order.incomePct == 100) return base
        return Bonuses(popCap = base.popCap, turretSlots = base.turretSlots,
            eliteUnlocked = base.eliteUnlocked, rallyUnlocked = base.rallyUnlocked,
            incomeMul = base.incomeMul * order.incomePct / 100f)
    }

    /** Dzień 66: czy dowódca gracza żyje (przy eskorcie: od pierwszego kroku). */
    fun commanderAlive(battle: Battle): Boolean = battle.player.commanderCd == -1f

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
            ObjectiveKind.COMMANDER_ALIVE -> commanderAlive(battle)
            ObjectiveKind.GOLD_LEFT -> p.gold >= objective.value
            ObjectiveKind.FIELDED_MAX -> p.spawned <= objective.value
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
     * Dzień 66: rozkaz partii 2 wymaga [BATCH2_UNLOCK_STARS]★ w rozkazie tej samej epoki
     * z partii 1 ([prerequisite]), który z kolei wymaga epoki w kampanii.
     * [campaignStars]: gwiazdki etapów kampanii z profilu; [orderStars]: gwiazdki rozkazów.
     */
    fun unlocked(order: Order, campaignStars: IntArray, orderStars: IntArray): Boolean {
        prerequisite(order)?.let { pre ->
            return unlocked(pre, campaignStars, orderStars) &&
                orderStars.getOrElse(pre.index) { 0 } >= BATCH2_UNLOCK_STARS
        }
        val needed = if (order.era == 0) 0 else order.era * 4 - 1
        return campaignStars.getOrElse(needed) { 0 } > 0
    }

    /** Rozkaz partii 1 tej samej epoki, od którego zależy rozkaz partii 2 (null w partii 1). */
    fun prerequisite(order: Order): Order? =
        if (order.batch == 0) null else all.first { it.batch == 0 && it.era == order.era }

    /** Numer etapu kampanii (od 1), którego zdobycie odblokowuje rozkaz. */
    fun unlockStageNumber(order: Order): Int = if (order.era == 0) 1 else order.era * 4

    /** Najwyższy wynik punktowy jednego rozkazu (3★ i pełne zdrowie bazy). */
    const val MAX_POINTS = MAX_STARS * POINTS_PER_STAR + MAX_HP_POINTS
}
