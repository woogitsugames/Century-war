package com.centurywar.game

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Jednostka na polu bitwy. Obiekty pochodzą z puli, więc w trakcie gry nic nie jest alokowane. */
class Fighter {
    var active = false
    var side = 0
    var def: UnitDef = Defs.allUnits[0]
    var tier = 1
    var x = 0f
    var hp = 0f
    var maxHp = 0f
    var dmg = 0f
    /** Prędkość przypisana do fali przy spawnie (nie dotyczy bossa). */
    var waveSpeedMul = 1f
    var cd = 0f
    var anim = 0f
    var attackAnim = 0f
    var flash = 0f
    var moving = false
    /** < 0 znaczy, że żyje; >= 0 to czas od śmierci (animacja znikania). */
    var dying = -1f
    var serial = 0
    // Tylko dla renderera: stan z poprzedniego kroku (interpolacja między krokami
    // symulacji na ekranach 90/120 Hz) i płynne ruszanie/zatrzymywanie. Nie wpływa
    // na logikę bitwy, nie trafia do zapisu. NaN = brak poprzedniego kroku.
    var prevX = Float.NaN
    var prevAnim = 0f
    var prevAttack = 0f
    /** 0 = stoi, 1 = pełny krok marszu; dochodzi płynnie w ok. 0,15 s. */
    var stride = 0f
    var prevStride = 0f
    // Tylko dla renderera: ostatni cios i siła odchylenia. Nie wpływają na symulację ani zapis.
    var hitStep = -1
    var hitPow = 0f
    /** Główna i druga zdolność bossa (druga od fali 60). */
    var bossKind = EndlessBosses.NONE
    var bossSecondaryKind = EndlessBosses.NONE
    var bossCd = 0f
    var bossSummonCd = 0f
    var bossRageActive = false
    fun hasBossAbility(kind: Int) = kind != EndlessBosses.NONE &&
        (bossKind == kind || bossSecondaryKind == kind)
    var burnDps = 0f
    var burnLeft = 0f
    /** Spowolnienie od wieży w Endless, w sekundach symulacji. */
    var slowLeft = 0f
    val isBoss get() = bossKind != EndlessBosses.NONE
    val radius get() = 16f * def.size
    val alive get() = active && dying < 0f
}

class Projectile {
    var active = false
    var side = 0
    var type = Proj.STONE
    var x = 0f
    var h = 0f
    var vx = 0f
    var vh = 0f
    var g = 0f
    var tx = 0f
    var t = 0f
    var dur = 0f
    var delay = 0f
    var dmg = 0f
    var splash = 0f
    var siegeMul = 1f
    var era = 0
    var shooterRole: Role? = null
    var turretEra = -1
    var variant = -1
    var burnDps = 0f
    // Tylko dla renderera (interpolacja), NaN = pocisk z bieżącego kroku.
    var prevX = Float.NaN
    var prevH = 0f
}

class Side(val index: Int) {
    val dir = if (index == 0) 1f else -1f
    val baseFront = if (index == 0) Defs.BASE_FRONT else Defs.LANE - Defs.BASE_FRONT
    var era = 0
    var eraVariant = -1
    /** Zakupiony stopień jednostek aktualnej epoki, według ordinal Role. */
    val unitTier = IntArray(4) { 1 }
    var gold = 0f
    var xp = 0f
    var baseHp = 0f
    var baseMaxHp = 0f
    val queue = IntArray(Battle.QUEUE_CAP)
    /** Temat zapamiętany przy dodaniu jednostki do kolejki fal. */
    val queueWaveTheme = IntArray(Battle.QUEUE_CAP) { EndlessWaveTheme.MIXED.ordinal }
    var queueSize = 0
    var queueMax = 5
    var trainT = 0f
    val turretEra = IntArray(Defs.MAX_TURRET_SLOTS) { -1 }
    val turretVariant = IntArray(Defs.MAX_TURRET_SLOTS) { -1 }
    val turretCd = FloatArray(Defs.MAX_TURRET_SLOTS)
    val turretFire = FloatArray(Defs.MAX_TURRET_SLOTS)
    var slots = 2
    val abilityCd = FloatArray(2)
    var rallyT = 0f
    var hold = false
    var aliveCount = 0
    var front = 0f
    var baseFlash = 0f

    var incomeMul = 1f
    var hpMul = 1f
    var dmgMul = 1f
    var baseHpMul = 1f
    var turretDmgMul = 1f
    var trainMul = 1f
    var xpMul = 1f
    var abilityCdMul = 1f
    var siegeMul = 1f
    var killGoldMul = 1f
    var rallyArmorMul = 1f
    var supplyUntil = 0f
    var heavyBaseDmgMul = 1f
    var turretRangeMul = 1f
    var fogSight = false
    var speedMul = 1f
    var turretCostMul = 1f
    var turretRateMul = 1f
    var turretBurnRatio = 0f
    var rangedDmgMul = 1f
    var eliteDmgMul = 1f
    var lastStandMul = 1f
    var cloneChance = 0f
    var repairPerWave = 0f
    var freeElitesPerWave = 0
    var freeEliteCredits = 0
    var autoBarrageInterval = 0f
    var autoBarrageTimer = 0f
    /** Liczba użyć poszczególnych kart (bez alokacji w każdej fali). */
    val cardStacks = IntArray(EndlessCard.values().size)
    var cap = 12
    var eliteUnlocked = true
    var rallyUnlocked = true
    val doctrines = ArrayList<Doctrine>()
    /** Rangi weteranów: nadwyżka XP w ostatniej dostępnej epoce daje +6% obrażeń i HP za rangę. */
    var rank = 0
    /** Poziom zbrojowni (kupowany złotem w bitwie): +8% obrażeń i HP dla nowych jednostek. */
    var armory = 0

    var kills = 0
    var killValue = 0
    var spawned = 0
    val spawnedByRole = IntArray(Role.entries.size)
    var goldEarned = 0f

    // Liczniki misji i osiągnięć: wyłącznie statystyczne, nie zmieniają decyzji ani losowości
    // symulacji (zapisywane w BattleSnapshot). Elita liczona raz, w trainedByRole.
    val trainedByRole: IntArray get() = spawnedByRole
    var elitesTrained: Int
        get() = trainedByRole[Role.ELITE.ordinal]
        set(v) { trainedByRole[Role.ELITE.ordinal] = v }
    var elitesLost = 0
    var turretsBuilt = 0
    var barragesUsed = 0
    var ralliesUsed = 0
    var erasAdvanced = 0
    var baseDamageTaken = 0f

    fun unit(role: Role): UnitDef = Defs.unit(era, role, eraVariant)
    fun turretCost(era: Int = this.era, variant: Int = if (era == this.era) eraVariant else -1): Int =
        (Defs.turret(era, variant).cost * turretCostMul).toInt()
}

/** Kolejka zdarzeń wizualnych dla renderera. Symulacja nie zna Canvas ani Androida. */
class FxBuffer {
    companion object {
        const val CAP = 256
        const val HIT = 0
        const val DEATH = 1
        const val EXPLOSION = 2
        const val MUZZLE = 3
        const val BASE_HIT = 4
        const val EVOLVE = 5
        const val GOLD = 6
        const val BARRAGE = 7
    }

    val type = IntArray(CAP)
    val x = FloatArray(CAP)
    val h = FloatArray(CAP)
    val side = IntArray(CAP)
    val value = FloatArray(CAP)
    /** Dane wyłącznie do renderowania i dźwięku; nie należą do stanu bitwy ani powtórki. */
    val era = IntArray(CAP) { -1 }
    val body = IntArray(CAP) { -1 }
    val turret = BooleanArray(CAP)
    var count = 0

    fun add(
        t: Int, px: Float, ph: Float, s: Int, v: Float = 0f,
        eraId: Int = -1, bodyId: Int = -1, isTurret: Boolean = false,
    ) {
        if (count >= CAP) return
        type[count] = t; x[count] = px; h[count] = ph; side[count] = s; value[count] = v
        era[count] = eraId; body[count] = bodyId; turret[count] = isTurret
        count++
    }
}

/**
 * Cała symulacja bitwy: czysty Kotlin bez zależności od Androida, deterministyczna
 * dla danego seeda. Dzięki temu da się ją testować i balansować w testach JVM
 * (AI kontra AI), a w przyszłości weryfikować wyniki rankingu na serwerze.
 */
class Battle(val cfg: BattleConfig) {
    companion object {
        const val STEP = 1f / 60f
        /** Tempo narastania/wygaszania kroku marszu (czysto wizualne). */
        const val STRIDE_RATE = 7f
        // Strojenie balansu (docs/BALANCE.md, BalanceReport).
        /** HP baz poza Endless względem Defs.baseHp: bitwa trwa minuty, nie jedną. */
        const val BALANCE_BASE_HP_MUL = 3f
        /** Od tej sekundy rośnie presja oblężnicza i działa blokada baz. */
        const val SIEGE_START = 480f
        /** Ułamek HP bazy na sekundę przy pełnej kontroli frontu przez wroga (przed presją). */
        const val SIEGE_ATTRITION = 0.0032f
        const val SHORT_DAY_STEPS = 8 * 60 * 60
        const val MERCENARY_INTERVAL_STEPS = 60 * 60
        const val VETERAN_START_RANK = 2
        const val MAX_UNITS = 72
        const val MAX_PROJ = 160
        const val QUEUE_CAP = 32
        const val HOLD_DIST = 430f
        const val MIN_ABILITY_CD_MUL = 0.35f
        const val SUPPLY_INSTANT_GOLD = 15f
        const val BARRAGE_CD = 55f
        const val RALLY_CD = 42f
        const val RALLY_TIME = 8f
        const val RALLY_SPEED_BONUS = 1.4f
        const val ARMORY_BASE_COST = 140f
        const val ARMORY_COST_GROWTH = 1.45f
        const val ARMORY_BONUS = 1.08f
        const val ARMORY_MAX_LEVEL = 15
        const val ENDLESS_CHOICE_EVERY = 5
        /** Samouczek: po tylu sekundach symulacji AI zaczyna grać. */
        const val TUTORIAL_AI_DELAY = 25f
    }

    val rng = Rng(cfg.seed)
    val sides = arrayOf(Side(0), Side(1))
    val units = Array(MAX_UNITS) { Fighter() }
    val projs = Array(MAX_PROJ) { Projectile() }
    val fx = FxBuffer()
    /** Indeksy żywych jednostek w kolejności slotów, odtwarzane po wczytaniu zrzutu. */
    private val liveSlots = Array(2) { IntArray(MAX_UNITS) }
    private val liveCounts = IntArray(2)
    private var liveDirty = true
    /** Tryb referencyjny do testu zgodności bitowej z dotychczasowym skanowaniem. */
    var useIndexedScan = true
    var time = 0f
    /** Liczba zakończonych kroków; następna komenda dotyczy kroku stepCount. */
    var stepCount = 0
    var commandController: BattleController? = null
    /** Zabezpiecza odczyt AI przed podjęciem decyzji w trakcie deserializacji. */
    var restoringSnapshot = false
    var over = false
    var winner = -1
    var wave = 0
    var waveTimer = 12f
    var serialCounter = 0
    /** Chroni przed ponownym rozliczeniem wyniku tej samej bitwy. */
    var achievementsRecorded = false
    val endless = cfg.mode == Mode.ENDLESS
    /** W Endless pogoda jest pochodną seeda i epoki przeciwnika (co sześć fal). */
    val weather: Weather get() = if (!endless) cfg.weather else when (
        (((cfg.seed xor (cfg.seed ushr 32)).toInt() and 3) + enemy.era) and 3
    ) {
        0 -> Weather.CLEAR
        1 -> Weather.RAIN
        2 -> Weather.WIND
        else -> Weather.NIGHT
    }

    /** Kierunek +1 oznacza wiatr w prawo. Zmiana co 75 s bez użycia RNG. */
    fun windDirection(): Int {
        val phase = (stepCount / (Weather.WIND_SHIFT_SECONDS * 60)).toLong()
        return if (((cfg.seed xor (cfg.seed ushr 32) xor phase) and 1L) == 0L) 1 else -1
    }

    /** Mnożniki identyczne dla obu stron, zależą jedynie od kierunku wystrzału. */
    fun projectileRangeMul(side: Int, turret: Boolean = false): Float = when (weather) {
        Weather.WIND -> if (sides[side].dir == windDirection().toFloat())
            Weather.WIND_WITH else Weather.WIND_AGAINST
        else -> if (turret) weather.turretRange else weather.projectileRange
    }
    val fog = Modifier.FOG_OF_WAR in cfg.modifiers
    val rangedHorde = Modifier.RANGED_HORDE in cfg.modifiers

    /** Doktryny podczas awansowania epoki poza trybem Endless. */
    var pendingChoice: Array<Doctrine>? = null
    /** Epoka wymagająca wyboru; wybór jest zapisywany jako komenda VARIANT. */
    var pendingVariant: Int? = null
    /** Osobna oferta kart dla Endless; ekran obsługuje oba rodzaje wyboru. */
    var pendingCards: Array<EndlessCard>? = null
    private val cardOffer = Array(3) { EndlessCard.ECONOMY }
    var currentWaveTheme = EndlessWaveTheme.MIXED
    var nextWaveTheme = if (endless) EndlessCards.nextTheme(rng) else EndlessWaveTheme.MIXED
    val nextWaveKey get() = if ((wave + 1) % 10 == 0) "ew_boss" else nextWaveTheme.textKey
    val nextBossKind get() = EndlessBosses.kindAtWave(wave + 1)
    val nextBossSecondaryKind get() = EndlessBosses.secondaryAtWave(wave + 1)
    var bossesSpawned = 0
    var bossesDefeated = 0
    var nextBossToSpawn = 10
    var bossRewardsPending = 0
    /** Aktywne aury w bieżącym kroku; pomocnicze dane, odtwarzane po wczytaniu. */
    private val bossAuraXs = FloatArray(MAX_UNITS)
    private var bossAuraCount = 0

    var ai: Ai? = if (endless) null else Ai(this, 1)
    /** W Endless dyrektor szkoli fale; AI wroga służy tylko do wyboru wariantu. */
    private val endlessVariantAi: Ai? = if (endless) Ai(this, 1) else null
    /** Tylko testy/autoplay: AI sterujące graczem. */
    var playerAi: Ai? = null
        set(value) {
            field = value
            // Testy automatyczne bez kontrolera nie tworzą powtórki.
            if (value != null && !restoringSnapshot && commandController == null && pendingVariant != null) {
                chooseVariant(value.chooseVariant(player.era))
            }
        }

    init {
        val mods = cfg.modifiers
        for (s in sides) {
            s.era = cfg.startEra
            // Bitwy kampanii, potyczek i wyzwania dnia trwają kilka minut, nie jedną.
            if (!endless) s.baseHpMul *= BALANCE_BASE_HP_MUL
            if (Modifier.DOUBLE_INCOME in mods) s.incomeMul *= 2f
            if (Modifier.FRAGILE_BASES in mods) s.baseHpMul *= 0.5f
            if (Modifier.FAST_UNITS in mods) s.speedMul *= 1.5f
            if (Modifier.NO_TURRETS in mods) s.slots = 0
            if (Modifier.GLASS_CANNON in mods) { s.dmgMul *= 1.5f; s.hpMul *= 0.7f }
            if (Modifier.SLOW_XP in mods) s.xpMul *= 0.6f
            if (Modifier.VETERANS in mods) {
                s.rank = VETERAN_START_RANK
                val rankBonus = 1.06f * 1.06f
                s.dmgMul *= rankBonus; s.hpMul *= rankBonus; s.turretDmgMul *= rankBonus
            }
            if (Modifier.EXPENSIVE_TOWERS in mods) {
                s.turretCostMul *= 1.6f
                s.turretDmgMul *= 1.4f
            }
            if (cfg.dailyEvent == DailyEvent.FORTIFIED_BASES) s.baseHpMul *= 2f
            if (cfg.dailyEvent == DailyEvent.RAPID_ABILITIES)
                s.abilityCdMul = max(MIN_ABILITY_CD_MUL, s.abilityCdMul * 0.5f)
            if (cfg.dailyEvent == DailyEvent.FREE_BARRAGE) {
                s.autoBarrageInterval = 30f
                s.autoBarrageTimer = 30f
            }
            s.gold = 175f * Defs.costScale(cfg.startEra)
            if (Modifier.RICH_START in mods) s.gold += 400f * Defs.costScale(cfg.startEra)
            if (cfg.dailyEvent == DailyEvent.GOLD_RUSH) s.gold += 1000f * Defs.costScale(cfg.startEra)
        }
        val p = sides[0]
        val b = cfg.playerBonuses
        p.gold += b.startGold * Defs.costScale(cfg.startEra)
        p.incomeMul *= b.incomeMul
        p.hpMul *= b.unitHpMul
        p.dmgMul *= b.unitDmgMul
        p.baseHpMul *= b.baseHpMul
        p.turretDmgMul *= b.turretDmgMul
        p.trainMul *= b.trainMul
        p.xpMul *= b.xpMul
        p.cap = b.popCap
        p.abilityCdMul = max(MIN_ABILITY_CD_MUL, p.abilityCdMul * b.abilityCdMul)
        if (p.slots > 0) p.slots = b.turretSlots.coerceIn(0, Defs.MAX_TURRET_SLOTS)
        p.eliteUnlocked = b.eliteUnlocked
        p.rallyUnlocked = b.rallyUnlocked

        val e = sides[1]
        e.incomeMul *= cfg.aiPower
        e.xpMul *= cfg.aiPower
        if (Modifier.ARMORED_FOE in mods) e.hpMul *= 1.3f
        if (cfg.dailyEvent == DailyEvent.MIRROR_ARMY) {
            e.cap = p.cap
            e.slots = p.slots
        }
        // Przeciwnik ma taki sam limit armii i slotów, jak strona bez badań.
        if (cfg.tutorial) { e.gold *= 0.5f; p.xpMul *= 1.5f } // samouczek: łagodniejszy start, szybszy awans
        if (endless) {
            e.queueMax = QUEUE_CAP
            e.cap = 30
            e.slots = 0
            e.trainMul = 2.5f
        }
        for (s in sides) {
            s.baseMaxHp = Defs.baseHp(s.era) * s.baseHpMul
            s.baseHp = s.baseMaxHp
            s.front = s.baseFront
        }
        if (cfg.variantsEnabled && Defs.spec(cfg.startEra).variants.isNotEmpty()) {
            for (s in sides) offerVariant(s)
        }
        // Karta startowa nie zużywa RNG bitwy i jest częścią zapisanej konfiguracji.
        if (cfg.weekly >= 0L) cfg.weeklyCard?.let { applyEndlessCard(player, it) }
    }

    val player get() = sides[0]
    val enemy get() = sides[1]

    // ---------------------------------------------------------------- komendy gracza / AI

    /** Cena jednostki po zastosowaniu reguły specjalnej dnia. */
    fun unitCost(side: Side, role: Role): Int {
        val cost = side.unit(role).cost
        return if (cfg.dailyEvent == DailyEvent.MARKSMEN && role == Role.RANGED)
            cost * 7 / 10 else cost
    }

    fun canQueue(side: Side, role: Role): Boolean {
        if (cfg.dailyEvent == DailyEvent.MARKSMEN && role != Role.RANGED) return false
        if (role == Role.ELITE && !side.eliteUnlocked) return false
        return side.queueSize < min(side.queueMax, QUEUE_CAP) && side.gold >= unitCost(side, role)
    }

    fun queueUnit(side: Side, role: Role): Boolean {
        if (over || !canQueue(side, role)) return false
        val def = side.unit(role)
        side.gold -= unitCost(side, role)
        side.queue[side.queueSize] = def.id
        side.queueWaveTheme[side.queueSize] = EndlessWaveTheme.MIXED.ordinal
        side.queueSize++
        return true
    }

    /** -1, gdy kolejny stopień nie istnieje albo rola nie jest odblokowana. */
    fun unitUpgradeCost(side: Side, role: Role): Int {
        if (side.unitTier[role.ordinal] >= Defs.MAX_UNIT_TIER ||
            (role == Role.ELITE && !side.eliteUnlocked)) return -1
        return Defs.unitUpgradeCost(side.era, role, side.unitTier[role.ordinal] + 1)
    }

    fun canUpgradeUnit(side: Side, role: Role): Boolean {
        val cost = unitUpgradeCost(side, role)
        return !over && cost >= 0 && side.gold >= cost
    }

    /** Zakup działa wyłącznie na jednostki szkolone od tej chwili. */
    fun upgradeUnit(side: Side, role: Role): Boolean {
        if (!canUpgradeUnit(side, role)) return false
        side.gold -= unitUpgradeCost(side, role)
        side.unitTier[role.ordinal]++
        return true
    }

    /** Anuluje ostatnią jednostkę w kolejce i zwraca całe złoto. */
    fun cancelLast(side: Side): Boolean {
        if (side.queueSize == 0) return false
        val id = side.queue[--side.queueSize]
        val def = Defs.allUnits[id]
        side.gold += if (cfg.dailyEvent == DailyEvent.MARKSMEN && def.role == Role.RANGED)
            def.cost * 7 / 10 else def.cost
        if (side.queueSize == 0) side.trainT = 0f
        return true
    }

    fun canEvolve(side: Side): Boolean =
        !over && side.era < cfg.maxEra && side.xp >= Defs.evolveXp[side.era] && pendingVariant == null && pendingChoice == null && pendingCards == null

    fun evolve(side: Side): Boolean {
        if (!canEvolve(side)) return false
        side.xp -= Defs.evolveXp[side.era]
        val ratio = side.baseHp / side.baseMaxHp
        side.era++
        side.eraVariant = -1
        side.erasAdvanced++
        side.unitTier.fill(1) // Nowa epoka wymaga nowych inwestycji; weterani pozostają na polu.
        side.baseMaxHp = Defs.baseHp(side.era) * side.baseHpMul
        side.baseHp = min(side.baseMaxHp, side.baseMaxHp * ratio + side.baseMaxHp * 0.1f)
        fx.add(FxBuffer.EVOLVE, side.baseFront, 0f, side.index, eraId = side.era)
        if (cfg.variantsEnabled && Defs.spec(side.era).variants.isNotEmpty()) offerVariant(side)
        else if (!endless) offerDoctrines(side)
        return true
    }

    private fun offerVariant(side: Side) {
        val variants = Defs.spec(side.era).variants
        if (variants.isEmpty()) return
        if (side.index == 0 && (playerAi == null || commandController != null)) {
            pendingVariant = side.era
        } else {
            val brain = if (side.index == 0) playerAi else (ai ?: endlessVariantAi)
            applyVariant(side, brain?.chooseVariant(side.era) ?: 0)
        }
    }

    private fun applyVariant(side: Side, index: Int) {
        require(index in Defs.spec(side.era).variants.indices)
        side.eraVariant = index
        if (!endless && side.erasAdvanced > 0) offerDoctrines(side)
    }

    /** Decyzja gracza dopiero po przyjęciu komendy przez BattleController. */
    fun chooseVariant(index: Int): Boolean {
        val era = pendingVariant ?: return false
        if (era != player.era || index !in Defs.spec(era).variants.indices) return false
        pendingVariant = null
        applyVariant(player, index)
        return true
    }

    private fun offerDoctrines(side: Side) {
        val pool = Doctrine.values().filter {
            when (it) {
                Doctrine.ARSENAL -> side.slots in 1 until Defs.MAX_TURRET_SLOTS
                Doctrine.BANNER -> side.rallyUnlocked
                Doctrine.VIGILANCE -> fog || side.slots > 0
                Doctrine.TACTICS -> side.abilityCdMul > MIN_ABILITY_CD_MUL
                else -> true
            }
        }.toMutableList()
        val picks = Array(3) {
            val i = rng.nextInt(pool.size)
            pool.removeAt(i)
        }
        if (side.index == 0 && (playerAi == null || commandController != null)) {
            pendingChoice = picks
        } else {
            val brain = if (side.index == 0) playerAi else ai
            applyDoctrine(side, brain?.chooseDoctrine(picks) ?: picks[0])
        }
    }

    fun chooseDoctrine(index: Int) {
        val c = pendingChoice ?: return
        pendingChoice = null
        applyDoctrine(player, c[index.coerceIn(0, c.size - 1)])
    }


    /** Wybór 1 z 3, zgłoszony przez ekran. */
    fun chooseEndlessCard(index: Int): Boolean {
        val options = pendingCards ?: return false
        pendingCards = null
        applyEndlessCard(player, options[index.coerceIn(0, options.size - 1)])
        return true
    }

    fun applyEndlessCard(side: Side, card: EndlessCard) {
        if (!endless || over || side.index != 0 || !EndlessCards.canOffer(card, side, fog)) return
        side.cardStacks[card.ordinal]++
        val power = if (EndlessCards.persistent(card))
            EndlessCards.stackPower(side.cardStacks[card.ordinal]) else 1f
        if (card.doctrine != null) {
            applyDoctrine(side, card.doctrine, power)
        } else when (card) {
            EndlessCard.TOWER_SLOT -> side.slots = min(Defs.MAX_TURRET_SLOTS, side.slots + 1)
            EndlessCard.FREE_ELITE -> side.freeElitesPerWave = min(4, side.freeElitesPerWave + 1)
            EndlessCard.BURNING_TOWERS -> side.turretBurnRatio += 0.25f * power
            EndlessCard.AUTO_BARRAGE -> {
                side.autoBarrageInterval = if (side.autoBarrageInterval == 0f) 30f else max(15f, side.autoBarrageInterval - 5f)
                side.autoBarrageTimer = side.autoBarrageInterval
            }
            EndlessCard.BLOOD_GOLD -> {
                side.incomeMul *= 1f + 0.5f * power
                side.killGoldMul *= 1f + 0.5f * power
                val penalty = 1f - 0.2f * power
                side.baseHpMul *= penalty
                side.baseMaxHp *= penalty
                side.baseHp *= penalty
            }
            EndlessCard.CLONING -> side.cloneChance = min(0.6f, side.cloneChance + 0.1f)
            EndlessCard.FRONTLINE_PLATE -> side.hpMul *= 1f + 0.2f * power
            EndlessCard.DRILL_SERGEANT -> side.trainMul *= 1f + 0.2f * power
            EndlessCard.RAPID_TOWERS -> side.turretRateMul *= 1f + 0.25f * power
            EndlessCard.MEDIC -> side.baseHp = min(side.baseMaxHp, side.baseHp + side.baseMaxHp * 0.25f)
            EndlessCard.EXTRA_GOLD -> side.gold += 500f * Defs.costScale(side.era)
            EndlessCard.MOBILIZATION -> side.cap = min(MAX_UNITS, side.cap + 3)
            EndlessCard.FORTRESS -> {
                val bonus = 1f + 0.25f * power
                side.baseHpMul *= bonus
                side.baseMaxHp *= bonus
                side.baseHp *= bonus
            }
            EndlessCard.SHARPSHOOTERS -> side.rangedDmgMul *= 1f + 0.25f * power
            EndlessCard.BERSERK -> {
                side.dmgMul *= 1f + 0.3f * power
                side.hpMul *= 1f - 0.1f * power
            }
            EndlessCard.SCAVENGER -> side.killGoldMul *= 1f + 0.25f * power
            EndlessCard.MARCH -> side.speedMul *= 1f + 0.2f * power
            EndlessCard.RALLY_MASTER ->
                side.abilityCdMul = max(MIN_ABILITY_CD_MUL, side.abilityCdMul * (1f - 0.2f * power))
            EndlessCard.ELITE_SCHOOL -> side.eliteDmgMul *= 1f + 0.3f * power
            EndlessCard.LAST_STAND -> side.lastStandMul *= 1f + 0.35f * power
            EndlessCard.REPAIR_CREW -> side.repairPerWave = min(0.3f, side.repairPerWave + 0.06f)
            EndlessCard.WAR_DRUMS -> {
                side.speedMul *= 1f + 0.08f * power
                if (side.cardStacks[EndlessCard.MARCH.ordinal] > 0 ||
                    side.cardStacks[EndlessCard.SWIFT.ordinal] > 0)
                    side.speedMul *= 1f + 0.06f * power
            }
            EndlessCard.TOWER_NETWORK -> {
                side.turretDmgMul *= 1f + 0.08f * power
                val slots = side.cardStacks[EndlessCard.TOWER_SLOT.ordinal] +
                    side.cardStacks[EndlessCard.ARSENAL.ordinal]
                repeat(slots) { side.turretDmgMul *= 1f + 0.04f * power }
            }
            EndlessCard.MUSTER_BANNER -> {
                side.cap = min(MAX_UNITS, side.cap + 1)
                side.trainMul *= 1f + 0.05f * power
                if (side.cardStacks[EndlessCard.MOBILIZATION.ordinal] > 0)
                    side.trainMul *= 1f + 0.08f * power
            }
            EndlessCard.EMBER_FORGE -> side.turretDmgMul *= 1f + 0.1f * power
            EndlessCard.CITADEL_HEART -> {
                val defenses = side.cardStacks[EndlessCard.BULWARK.ordinal] +
                    side.cardStacks[EndlessCard.FORTRESS.ordinal] +
                    side.cardStacks[EndlessCard.REPAIR_CREW.ordinal]
                side.repairPerWave = min(0.3f, side.repairPerWave + 0.01f * (1 + defenses))
            }
            // Te karty działają przez istniejące stosy i mechanikę trafień.
            EndlessCard.SUPPLY_LINE, EndlessCard.IRON_CURTAIN, EndlessCard.LAST_BASTION,
            EndlessCard.FIELD_MEDICS, EndlessCard.FINAL_SPARK,
            EndlessCard.FIRST_GUARD, EndlessCard.BOSS_RECALL -> Unit
            else -> Unit
        }
        applyCard71Synergies(side, card, power)
    }

    /** Synergie każdej pary stosów są symetryczne, niezależnie od kolejności kart. */
    private fun applyCard71Synergies(side: Side, card: EndlessCard, power: Float) {
        if (card == EndlessCard.SUPPLY_LINE) {
            for (economic in EndlessCards.economicSynergyCards) {
                val n = min(side.cardStacks[economic.ordinal], EndlessCards.MAX_PERSISTENT_STACKS)
                for (stack in 1..n) {
                    val economicPower = if (EndlessCards.persistent(economic))
                        EndlessCards.stackPower(stack) else 1f
                    side.incomeMul *= 1f + 0.05f * power * economicPower
                }
            }
        } else if (card == EndlessCard.ECONOMY || card == EndlessCard.PLUNDER ||
            card == EndlessCard.EXTRA_GOLD || card == EndlessCard.BLOOD_GOLD ||
            card == EndlessCard.SCAVENGER) {
            if (card != EndlessCard.EXTRA_GOLD ||
                side.cardStacks[card.ordinal] <= EndlessCards.MAX_PERSISTENT_STACKS) {
                for (stack in 1..side.cardStacks[EndlessCard.SUPPLY_LINE.ordinal]) {
                    side.incomeMul *= 1f + 0.05f * power * EndlessCards.stackPower(stack)
                }
            }
        }
        if ((card == EndlessCard.MARCH && side.cardStacks[EndlessCard.MARCH.ordinal] == 1 &&
                side.cardStacks[EndlessCard.SWIFT.ordinal] == 0) ||
            (card == EndlessCard.SWIFT && side.cardStacks[EndlessCard.SWIFT.ordinal] == 1 &&
                side.cardStacks[EndlessCard.MARCH.ordinal] == 0)) {
            for (stack in 1..side.cardStacks[EndlessCard.WAR_DRUMS.ordinal]) {
                side.speedMul *= 1f + 0.06f * EndlessCards.stackPower(stack)
            }
        }
        if (card == EndlessCard.TOWER_SLOT || card == EndlessCard.ARSENAL) {
            for (stack in 1..side.cardStacks[EndlessCard.TOWER_NETWORK.ordinal]) {
                side.turretDmgMul *= 1f + 0.04f * EndlessCards.stackPower(stack)
            }
        }
        if (card == EndlessCard.MOBILIZATION && side.cardStacks[EndlessCard.MOBILIZATION.ordinal] == 1) {
            for (stack in 1..side.cardStacks[EndlessCard.MUSTER_BANNER.ordinal]) {
                side.trainMul *= 1f + 0.08f * EndlessCards.stackPower(stack)
            }
        }
        if (card == EndlessCard.BULWARK || card == EndlessCard.FORTRESS ||
            card == EndlessCard.REPAIR_CREW) {
            side.repairPerWave = min(0.3f, side.repairPerWave +
                0.01f * side.cardStacks[EndlessCard.CITADEL_HEART.ordinal])
        }
    }

    private fun offerEndlessCards() {
        EndlessCards.draw(rng, player, cardOffer, fog)
        val brain = playerAi
        // Z kontrolerem powtórki wybór AI idzie przez log komend (jak doktryny).
        if (brain != null && commandController == null) applyEndlessCard(player, cardOffer[brain.chooseCard(cardOffer)])
        else pendingCards = cardOffer
    }

    fun currentBoss(): Fighter? {
        for (u in units) if (u.alive && u.isBoss) return u
        return null
    }

    fun applyDoctrine(side: Side, d: Doctrine) = applyDoctrine(side, d, 1f)

    private fun applyDoctrine(side: Side, d: Doctrine, power: Float) {
        side.doctrines.add(d)
        when (d) {
            Doctrine.ECONOMY -> side.incomeMul *= 1f + 0.25f * power
            Doctrine.SIEGE -> {
                side.siegeMul *= 1f + 0.3f * power
                side.turretDmgMul *= 1f + 0.2f * power
            }
            Doctrine.BULWARK -> {
                val bonus = (if (endless) 0.2f else 0.05f) * power
                side.baseHpMul *= 1f + bonus
                val add = side.baseMaxHp * bonus
                side.baseMaxHp += add; side.baseHp += add
                side.hpMul *= 1f + (if (endless) 0.1f else 0.03f) * power
            }
            Doctrine.SWIFT -> {
                side.trainMul *= 1f + 0.25f * power
                side.speedMul *= 1f + 0.1f * power
            }
            Doctrine.VETERAN -> side.xpMul *= 1f + 0.3f * power
            Doctrine.FURY -> side.dmgMul *= 1f + (if (endless) 0.12f else 0.08f) * power
            Doctrine.ARSENAL -> {
                // Modyfikator zakazu wież ma pierwszeństwo przed doktryną.
                if (Modifier.NO_TURRETS !in cfg.modifiers && side.slots > 0) {
                    side.slots = min(Defs.MAX_TURRET_SLOTS, side.slots + 1)
                    side.turretCostMul *= 1f - 0.2f * power
                }
            }
            Doctrine.TACTICS ->
                side.abilityCdMul = max(MIN_ABILITY_CD_MUL, side.abilityCdMul * (1f - 0.25f * power))
            Doctrine.PLUNDER -> side.killGoldMul *= 1f + (if (endless) 0.35f else 0.08f) * power
            Doctrine.BANNER -> side.rallyArmorMul *= 1f - 0.15f * power
            Doctrine.SUPPLY -> {
                side.gold += SUPPLY_INSTANT_GOLD * Defs.costScale(side.era) * power
                side.supplyUntil = max(side.supplyUntil, time + 60f)
            }
            Doctrine.SIEGE_MASTERS -> side.heavyBaseDmgMul *= 1f + 0.18f * power
            Doctrine.VIGILANCE -> {
                side.turretRangeMul *= 1f + 0.18f * power
                if (fog) side.fogSight = true
            }
        }
    }

    fun armoryCost(side: Side): Int = (ARMORY_BASE_COST * Defs.costScale(side.era) * StrictMath.pow(ARMORY_COST_GROWTH.toDouble(), side.armory.toDouble()).toFloat()).toInt()

    fun buyArmory(side: Side): Boolean {
        if (over || side.armory >= ARMORY_MAX_LEVEL) return false
        val c = armoryCost(side)
        if (side.gold < c) return false
        side.gold -= c
        side.armory++
        side.dmgMul *= ARMORY_BONUS
        side.hpMul *= ARMORY_BONUS
        return true
    }

    fun freeSlot(side: Side): Int {
        for (i in 0 until side.slots) if (side.turretEra[i] < 0) return i
        return -1
    }

    fun buyTurret(side: Side, slot: Int): Boolean {
        if (over || slot !in 0 until side.slots || side.turretEra[slot] >= 0) return false
        val cost = side.turretCost()
        if (side.gold < cost) return false
        side.gold -= cost
        side.turretEra[slot] = side.era
        side.turretVariant[slot] = side.eraVariant
        side.turretCd[slot] = 0.5f
        side.turretsBuilt++
        return true
    }

    fun upgradeCost(side: Side, slot: Int): Int {
        if (slot !in 0 until side.slots || slot !in 0 until Defs.MAX_TURRET_SLOTS) return -1
        val te = side.turretEra[slot]
        if (te < 0 || te >= side.era) return -1
        return max(0, side.turretCost() - side.turretCost(te, side.turretVariant[slot]) / 2)
    }

    fun upgradeTurret(side: Side, slot: Int): Boolean {
        val cost = upgradeCost(side, slot)
        if (over || cost < 0 || side.gold < cost) return false
        side.gold -= cost
        side.turretEra[slot] = side.era
        side.turretVariant[slot] = side.eraVariant
        return true
    }

    fun sellTurret(side: Side, slot: Int): Boolean {
        if (slot !in 0 until side.slots || slot !in 0 until Defs.MAX_TURRET_SLOTS) return false
        val te = side.turretEra[slot]
        if (over || te < 0) return false
        side.gold += side.turretCost(te, side.turretVariant[slot]) / 2
        side.turretEra[slot] = -1
        side.turretVariant[slot] = -1
        return true
    }

    fun abilityReady(side: Side, i: Int): Boolean =
        !over && i in 0..1 && side.abilityCd[i] <= 0f && (i == 0 || side.rallyUnlocked)

    /** Bez argumentu celu zachowuje dawny, losowy rozrzut ostrzału. */
    fun castBarrage(side: Side): Boolean = castBarrage(side, Float.NaN)

    /** Ostrzał skupiony wokół wskazanego miejsca, ograniczony do zasięgu zdolności. */
    fun castBarrage(side: Side, targetX: Float): Boolean {
        if (!abilityReady(side, 0)) return false
        if (!fireBarrage(side, targetX)) return false
        side.abilityCd[0] = BARRAGE_CD * max(MIN_ABILITY_CD_MUL, side.abilityCdMul)
        side.barragesUsed++
        return true
    }

    /**
     * Dodatkowy ostrzał (karta) nie blokuje ostrzału ręcznego. NaN = klasyczny rozrzut.
     * false = pula pocisków pełna, nic nie wystrzelono (ręczny ostrzał nie zużywa wtedy odnowienia).
     */
    private fun fireBarrage(side: Side, targetX: Float = Float.NaN): Boolean {
        val shot = Defs.barrage(side.era, side.eraVariant)
        val count = shot.count
        var fired = 0
        val dmg = Defs.abilityDmg(side.era) * side.dmgMul
        val type = shot.projectile
        val aimed = targetX.isFinite()
        val aimDistance = (targetX - side.baseFront) * side.dir
        for (i in 0 until count) {
            val p = allocProj() ?: break
            val dist = if (aimed) {
                (aimDistance + (rng.nextFloat() - 0.5f) * 240f).coerceIn(220f, 1370f)
            } else {
                220f + rng.nextFloat() * 1150f
            }
            val tx = side.baseFront + side.dir * dist
            p.side = side.index
            p.type = type
            p.era = side.era
            p.variant = side.eraVariant
            p.x = tx - side.dir * 260f
            p.h = 950f
            p.tx = tx
            p.dur = 0.85f
            p.vx = (tx - p.x) / p.dur
            p.g = 0f
            p.vh = -p.h / p.dur
            p.t = 0f
            p.delay = i * 0.11f + rng.nextFloat() * 0.1f
            p.dmg = dmg
            p.splash = shot.splash
            p.siegeMul = 0.25f
            p.shooterRole = null
            p.turretEra = -1
            p.burnDps = 0f
            fired++
        }
        if (fired == 0) return false
        fx.add(FxBuffer.BARRAGE, side.baseFront, 0f, side.index, eraId = side.era)
        return true
    }

    fun castRally(side: Side): Boolean {
        if (!abilityReady(side, 1)) return false
        side.abilityCd[1] = RALLY_CD * max(MIN_ABILITY_CD_MUL, side.abilityCdMul)
        side.rallyT = RALLY_TIME
        side.ralliesUsed++
        return true
    }

    // ---------------------------------------------------------------- symulacja

    fun step() {
        if (over) return
        val controller = commandController
        // Doktryna lub karta z poprzedniego kroku (np. co piąta fala) musi trafić do logu.
        if (controller != null && playerAi != null) {
            if (pendingVariant != null) controller.choosePendingVariant()
            if (pendingChoice != null) controller.choosePendingDoctrine()
            if (pendingCards != null) controller.choosePendingCard()
        }
        if (pendingVariant != null || pendingChoice != null || pendingCards != null) return
        val dt = STEP
        // AI gracza wydaje komendy przed krokiem N, tak samo jak odtwarzacz.
        if (controller != null) playerAi?.update(dt)
        if (pendingVariant != null || pendingChoice != null || pendingCards != null) return
        stepCount++
        // Stan sprzed kroku dla interpolacji renderera (bez alokacji, bez wpływu na logikę).
        for (u in units) if (u.active) {
            u.prevX = u.x; u.prevAnim = u.anim; u.prevAttack = u.attackAnim; u.prevStride = u.stride
        }
        for (p in projs) if (p.active) { p.prevX = p.x; p.prevH = p.h }
        // Czas wynika z numeru kroku, nie z wielokrotnego dodawania Float.
        time = stepCount * STEP
        updateFronts()
        val control = ((sides[0].front + sides[1].front) * 0.5f / Defs.LANE).coerceIn(0f, 1f)
        for (s in sides) {
            if (s.baseFlash > 0f) s.baseFlash -= dt
            if (!(endless && s.index == 1)) {
                val territory = if (s.index == 0) control else 1f - control
                val inc = Defs.income(s.era) * s.incomeMul * (0.7f + 0.6f * territory) * dt
                s.gold += inc
                s.goldEarned += inc
                s.xp += 0.9f * Defs.costScale(s.era) * s.xpMul * dt
            }
            train(s, dt)
            turrets(s, dt)
            for (i in 0..1) if (s.abilityCd[i] > 0f) s.abilityCd[i] -= dt
            if (s.rallyT > 0f) s.rallyT -= dt
            if (cfg.dailyEvent == DailyEvent.FREE_BARRAGE) {
                s.autoBarrageTimer -= dt
                if (s.autoBarrageTimer <= 0f) {
                    s.autoBarrageTimer += s.autoBarrageInterval
                    fireBarrage(s)
                }
            }
        }
        for (s in sides) promote(s)
        if (endless) director(dt)
        if (!cfg.tutorial || time >= TUTORIAL_AI_DELAY) ai?.update(dt)
        if (controller == null) playerAi?.update(dt)
        if (Modifier.MERCENARIES in cfg.modifiers && stepCount % MERCENARY_INTERVAL_STEPS == 0)
            dispatchMercenaries()
        bossAuraCount = 0
        if (endless) for (u in units) if (u.alive && u.hasBossAbility(EndlessBosses.AURA)) {
            bossAuraXs[bossAuraCount++] = u.x
        }
        for (u in units) if (u.active) updateUnit(u, dt)
        for (p in projs) if (p.active) updateProj(p, dt)
        if (!endless && !over && time >= SIEGE_START && Modifier.SHORT_DAY !in cfg.modifiers)
            applySiegeAttrition(dt)
        if (!over) {
            if (sides[0].baseHp <= 0f) finish(1)
            else if (!endless && sides[1].baseHp <= 0f) finish(0)
            else if (!endless && Modifier.SHORT_DAY in cfg.modifiers && stepCount >= SHORT_DAY_STEPS) {
                // Porównujemy procent zdrowia baz; przy remisie wygrywa obrona.
                val playerHp = player.baseHp / player.baseMaxHp
                val enemyHp = enemy.baseHp / enemy.baseMaxHp
                finish(if (playerHp > enemyHp) 0 else 1)
            }
        }
        if (endless && !over && pendingCards == null && bossRewardsPending > 0) {
            bossRewardsPending--
            offerEndlessCards()
        }
        // Ten sam twardy koniec w grze, powtórkach i weryfikatorze serwera.
        if (!over && stepCount >= Replay.MAX_STEPS) {
            pendingVariant = null; pendingChoice = null; pendingCards = null
            finish(1)
        }
    }

    /** Darmowa jednostka tej samej losowej roli dla obu stron co minutę. */
    private fun dispatchMercenaries() {
        val role = Role.entries[rng.nextInt(Role.entries.size)]
        for (s in sides) if (s.aliveCount < s.cap) spawn(s, s.unit(role))
    }

    /** Koszt XP kolejnej rangi weteranów (gdy nie ma już epoki do awansu). */
    fun rankXp(s: Side): Float = Defs.evolveXp[max(0, cfg.maxEra - 1)] * 0.35f * (1f + s.rank * 0.25f)

    private fun promote(s: Side) {
        if (s.era < cfg.maxEra || s.rank >= 20) return
        if (endless && s.index == 1) return
        val need = rankXp(s)
        if (s.xp < need) return
        s.xp -= need
        s.rank++
        s.dmgMul *= 1.06f
        s.hpMul *= 1.06f
        s.turretDmgMul *= 1.06f
        fx.add(FxBuffer.EVOLVE, s.baseFront, 0f, s.index, 1f, eraId = s.era)
    }

    /**
     * Presja oblężnicza: po 8 minutach obrażenia zadawane bazom rosną o 20% na minutę.
     * Dzięki temu bitwa zawsze kiedyś się kończy (koniec z wiecznym patem przy wieżach).
     */
    fun siegePressure(): Float = if (endless || time < SIEGE_START) 1f else 1f + (time - SIEGE_START) / 60f * 0.2f

    /**
     * Blokada po [SIEGE_START]: obie bazy tracą HP proporcjonalnie do tego, jak daleko
     * przeciwnik trzyma front, więc nawet nieruchomy front w końcu rozstrzyga bitwę.
     */
    private fun applySiegeAttrition(dt: Float) {
        val control = ((player.front + enemy.front) * 0.5f / Defs.LANE).coerceIn(0f, 1f)
        val rate = SIEGE_ATTRITION * siegePressure() * dt
        player.baseHp -= player.baseMaxHp * rate * (1f - control)
        enemy.baseHp -= enemy.baseMaxHp * rate * control
    }

    fun surrender() { if (!over) { pendingVariant = null; pendingChoice = null; pendingCards = null; finish(1) } }

    private fun finish(w: Int) {
        over = true
        winner = w
        val loser = sides[1 - w]
        loser.baseHp = 0f
        fx.add(FxBuffer.EXPLOSION, loser.baseFront - loser.dir * 80f, 120f, loser.index, 3f, eraId = loser.era)
    }

    private fun updateFronts() {
        if (!useIndexedScan) {
            // Referencyjna wersja sprzed optymalizacji: dwukrotny skan wszystkich slotów.
            for (s in sides) {
                var front = s.baseFront
                var count = 0
                for (u in units) {
                    if (!u.alive || u.side != s.index) continue
                    count++
                    if ((u.x - front) * s.dir > 0f) front = u.x
                }
                s.front = front
                s.aliveCount = count
            }
            return
        }
        val left = sides[0]
        val right = sides[1]
        var leftFront = left.baseFront
        var rightFront = right.baseFront
        var leftCount = 0
        var rightCount = 0
        for (i in units.indices) {
            val u = units[i]
            if (!u.alive) continue
            if (u.side == 0) {
                liveSlots[0][leftCount++] = i
                if ((u.x - leftFront) * left.dir > 0f) leftFront = u.x
            } else {
                liveSlots[1][rightCount++] = i
                if ((u.x - rightFront) * right.dir > 0f) rightFront = u.x
            }
        }
        left.front = leftFront
        left.aliveCount = leftCount
        right.front = rightFront
        right.aliveCount = rightCount
        liveCounts[0] = leftCount
        liveCounts[1] = rightCount
        liveDirty = false
    }

    /** Po spawnie lub śmierci przywracamy porządek slotów; to nie jest stan zapisywany. */
    private fun ensureLiveSlots() {
        if (!liveDirty) return
        liveCounts[0] = 0
        liveCounts[1] = 0
        for (i in units.indices) {
            val u = units[i]
            if (!u.alive) continue
            val side = u.side
            liveSlots[side][liveCounts[side]++] = i
        }
        liveDirty = false
    }

    /** Najbardziej wysunięta żywa jednostka; remis zachowuje dotychczasową kolejność slotów. */
    fun frontUnit(sideIdx: Int): Fighter? {
        val s = sides[sideIdx]
        var best: Fighter? = null
        if (useIndexedScan) {
            ensureLiveSlots()
            val slots = liveSlots[sideIdx]
            for (i in 0 until liveCounts[sideIdx]) {
                val u = units[slots[i]]
                if (!u.alive) continue
                if (best == null || (u.x - best.x) * s.dir > 0f) best = u
            }
        } else {
            for (u in units) {
                if (!u.alive || u.side != sideIdx) continue
                if (best == null || (u.x - best.x) * s.dir > 0f) best = u
            }
        }
        return best
    }

    private fun train(s: Side, dt: Float) {
        if (s.queueSize == 0) return
        if (s.aliveCount >= s.cap) return
        val def = Defs.allUnits[s.queue[0]]
        val tier = if (def.era == s.era) s.unitTier[def.role.ordinal] else 1
        s.trainT += dt * s.trainMul * Defs.unitTrainRate(def, tier)
        if (s.trainT >= def.trainTime) {
            val theme = if (endless && s.index == 1)
                EndlessWaveTheme.entries[s.queueWaveTheme[0]] else EndlessWaveTheme.MIXED
            if (spawnFighter(s, def, waveTheme = theme) != null) {
                s.trainT = 0f
                for (i in 1 until s.queueSize) {
                    s.queue[i - 1] = s.queue[i]
                    s.queueWaveTheme[i - 1] = s.queueWaveTheme[i]
                }
                s.queueSize--
                if (endless && s.index == 0 && s.cloneChance > 0f &&
                    s.aliveCount < s.cap && rng.nextFloat() < s.cloneChance) spawn(s, def)
            }
        }
    }

    fun spawn(s: Side, def: UnitDef, extraHpMul: Float = 1f): Boolean =
        spawnFighter(s, def, extraHpMul) != null

    private fun spawnFighter(s: Side, def: UnitDef, extraHpMul: Float = 1f,
                             waveTheme: EndlessWaveTheme = EndlessWaveTheme.MIXED): Fighter? {
        var f: Fighter? = null
        for (u in units) if (!u.active) { f = u; break }
        if (f == null) return null
        f.active = true
        f.side = s.index
        f.def = def
        f.tier = if (def.era == s.era) s.unitTier[def.role.ordinal] else 1
        f.x = s.baseFront - s.dir * 30f
        f.prevX = Float.NaN
        f.stride = 0f; f.prevStride = 0f
        f.hitStep = -1; f.hitPow = 0f
        f.maxHp = def.hp * s.hpMul * extraHpMul * Defs.tierStatMul(f.tier) *
            (if (waveTheme == EndlessWaveTheme.SKIRMISHERS) 0.8f else 1f)
        f.waveSpeedMul = when (waveTheme) {
            EndlessWaveTheme.SIEGE_TRAIN -> 0.85f
            EndlessWaveTheme.TWIN_RAID -> 1.15f
            else -> 1f
        }
        f.hp = f.maxHp
        f.dmg = def.dmg * s.dmgMul * Defs.tierStatMul(f.tier) * when (def.role) {
            Role.RANGED -> s.rangedDmgMul
            Role.ELITE -> s.eliteDmgMul
            else -> 1f
        }
        f.bossKind = EndlessBosses.NONE
        f.bossSecondaryKind = EndlessBosses.NONE
        f.bossCd = 0f
        f.bossSummonCd = 0f
        f.bossRageActive = false
        f.burnDps = 0f
        f.burnLeft = 0f
        f.slowLeft = 0f
        f.cd = 0.3f
        f.anim = rng.nextFloat() * 6f
        f.attackAnim = 0f
        f.flash = 0f
        f.dying = -1f
        f.moving = false
        f.serial = ++serialCounter
        s.spawned++
        s.spawnedByRole[def.role.ordinal]++
        s.aliveCount++
        liveDirty = true
        return f
    }

    private fun turrets(s: Side, dt: Float) {
        val enemyIdx = 1 - s.index
        val target = frontUnit(enemyIdx) ?: run {
            for (i in 0 until s.slots) {
                if (s.turretCd[i] > 0f) s.turretCd[i] -= dt
                if (s.turretFire[i] > 0f) s.turretFire[i] -= dt
            }
            return
        }
        for (i in 0 until s.slots) {
            if (s.turretFire[i] > 0f) s.turretFire[i] -= dt
            val te = s.turretEra[i]
            if (te < 0) continue
            if (s.turretCd[i] > 0f) { s.turretCd[i] -= dt; continue }
            val td = Defs.turret(te, s.turretVariant[i])
            val dist = (target.x - s.baseFront) * s.dir
            if (dist > td.range * projectileRangeMul(s.index, turret = true) * s.turretRangeMul) continue
            s.turretCd[i] = td.cooldown / s.turretRateMul
            s.turretFire[i] = 0.15f
            val h = turretHeight(i)
            val turretDmg = td.dmg * s.turretDmgMul
            launch(s.index, s.baseFront - s.dir * 40f, h, target.x, td.projectile,
                turretDmg, td.splash, 0f, te, null, turretDmg * s.turretBurnRatio / 4f,
                s.turretVariant[i])
            // Tylko wizualizacja: ostrzał wieży otrzymuje własny bank audio.
            fx.add(FxBuffer.MUZZLE, s.baseFront - s.dir * 40f, h, s.index,
                td.projectile.toFloat(), te, isTurret = true)
        }
    }

    fun turretHeight(slot: Int) = 150f + slot * 55f

    private fun updateUnit(u: Fighter, dt: Float) {
        if (u.dying >= 0f) {
            u.dying += dt
            if (u.dying > 0.8f) u.active = false
            return
        }
        if (u.slowLeft > 0f) u.slowLeft = max(0f, u.slowLeft - dt)
        if (u.burnLeft > 0f) {
            val tick = min(dt, u.burnLeft)
            u.burnLeft -= tick
            val burn = u.burnDps * tick
            damageUnit(u, if (weather == Weather.RAIN) burn * Weather.RAIN.burnDamage else burn, 1 - u.side, direct = false)
            if (!u.alive) return
        }
        if (u.def.art == EraArt.BIOTECH && u.alive && u.hp < u.maxHp)
            u.hp = min(u.maxHp, u.hp + u.maxHp * 0.008f * dt)
        if (u.bossCd > 0f) u.bossCd -= dt
        if (endless && u.hasBossAbility(EndlessBosses.RECRUITER)) {
            u.bossSummonCd -= dt
            if (u.bossSummonCd <= 0f) {
                u.bossSummonCd += EndlessBosses.RECRUIT_INTERVAL
                val recruits = sides[u.side]
                for (i in 0 until 2) {
                    if (recruits.aliveCount >= recruits.cap) break
                    val fighter = spawnFighter(recruits, Defs.unit(recruits.era, Role.MELEE)) ?: break
                    fighter.x = (u.x - recruits.dir * (32f + i * 38f))
                        .coerceIn(player.baseFront + 20f, enemy.baseFront - 20f)
                    fighter.prevX = Float.NaN
                    fx.add(FxBuffer.EVOLVE, fighter.x, 15f * fighter.def.size, recruits.index, 0.7f)
                }
            }
        }
        val s = sides[u.side]
        val eIdx = 1 - u.side
        val e = sides[eIdx]
        if (u.flash > 0f) u.flash -= dt
        if (u.attackAnim > 0f) u.attackAnim -= dt
        var rally = if (s.rallyT > 0f) RALLY_SPEED_BONUS else 1f
        if (endless && u.side == 1) {
            for (i in 0 until bossAuraCount) if (abs(bossAuraXs[i] - u.x) < EndlessBosses.AURA_RANGE) {
                rally *= 1.25f
                break // Aury nie kumulują się.
            }
        }
        val attackRate = rally * (if (u.bossRageActive) 1.3f else 1f)
        if (u.cd > 0f) u.cd -= dt * attackRate

        val target = frontUnit(eIdx)
        val gap = if (target != null) (target.x - u.x) * s.dir - u.radius - target.radius
        else (e.baseFront - u.x) * s.dir - u.radius
        val def = u.def
        val bastionMul = if (endless && u.side == 0 && s.baseHp < s.baseMaxHp * 0.25f)
            1f + 0.3f * EndlessCards.totalStackPower(s.cardStacks[EndlessCard.LAST_BASTION.ordinal]) else 1f
        val attackDmg = (if (endless && u.side == 0 && s.baseHp < s.baseMaxHp * 0.35f)
            u.dmg * s.lastStandMul else u.dmg) * bastionMul *
            (if (u.bossRageActive) 1.5f else 1f)

        val attackRange = Defs.unitRange(def, u.tier)
        val effectiveRange = if (def.projectile == Proj.NONE) attackRange
            else attackRange * projectileRangeMul(u.side)
        if (gap <= effectiveRange) {
            u.moving = false
            if (endless && u.hasBossAbility(EndlessBosses.KNOCKBACK) &&
                u.bossCd <= 0f && target != null && target.alive) {
                target.x = max(e.baseFront + 16f, target.x - 90f)
                damageUnit(target, attackDmg * 0.2f, u.side)
                u.bossCd = 6f
            }
            if (u.cd <= 0f) {
                u.cd = def.cooldown
                u.attackAnim = 0.25f
                if (def.projectile == Proj.NONE) {
                    if (Defs.unitSplash(def, u.tier) > 0f) {
                        val cx = if (target != null) target.x else e.baseFront
                        splashDamage(u.side, cx, Defs.unitSplash(def, u.tier), attackDmg, def.siegeMul, def.role, -1)
                    } else if (target != null) {
                        damageUnit(target, attackDmg, u.side, def.role, -1)
                        fx.add(FxBuffer.HIT, target.x, 40f * target.def.size, eIdx,
                            eraId = target.def.era, bodyId = target.def.body)
                    } else {
                        damageBase(e, attackDmg * def.siegeMul, u.side, def.role)
                    }
                } else {
                    val tx = target?.x ?: (e.baseFront + e.dir * 30f)
                    val h = 38f * def.size
                    launch(u.side, u.x + s.dir * u.radius, h, tx, def.projectile, attackDmg, Defs.unitSplash(def, u.tier), def.siegeMul, def.era, def.role, variant = def.variant)
                    fx.add(FxBuffer.MUZZLE, u.x + s.dir * u.radius * 1.3f, h, u.side,
                        def.projectile.toFloat(), def.era, def.body)
                }
            }
            return
        }

        // Ruch: zatrzymaj się za sojusznikiem albo na linii obrony.
        var blocked = false
        if (useIndexedScan) {
            ensureLiveSlots()
            val slots = liveSlots[u.side]
            for (i in 0 until liveCounts[u.side]) {
                val a = units[slots[i]]
                if (a === u || !a.alive) continue
                val ahead = (a.x - u.x) * s.dir
                if (ahead > 0f && ahead < (u.radius + a.radius) * 0.9f + 6f) { blocked = true; break }
            }
        } else {
            for (a in units) {
                if (a === u || !a.alive || a.side != u.side) continue
                val ahead = (a.x - u.x) * s.dir
                if (ahead > 0f && ahead < (u.radius + a.radius) * 0.9f + 6f) { blocked = true; break }
            }
        }
        if (!blocked && s.hold) {
            val holdX = s.baseFront + s.dir * HOLD_DIST
            if ((u.x - holdX) * s.dir >= 0f) blocked = true
        }
        if (!blocked) {
            val slowMul = if (endless && u.slowLeft > 0f)
                1f - min(0.45f, 0.15f * e.cardStacks[EndlessCard.IRON_CURTAIN.ordinal]) else 1f
            val normalSpeed = Defs.unitSpeed(def, u.tier) * s.speedMul * u.waveSpeedMul * rally * slowMul
            val v = if (weather == Weather.RAIN) normalSpeed * Weather.RAIN.moveSpeed else normalSpeed
            u.x += s.dir * v * dt
            u.anim += v * dt * 0.09f
            u.moving = true
            u.stride = min(1f, u.stride + dt * STRIDE_RATE)
        } else {
            u.moving = false
            u.stride = max(0f, u.stride - dt * STRIDE_RATE)
        }
    }

    private fun allocProj(): Projectile? {
        for (p in projs) if (!p.active) { p.active = true; p.prevX = Float.NaN; return p }
        return null
    }

    private fun launch(side: Int, x: Float, h: Float, tx: Float, type: Int, dmg: Float, splash: Float, siegeMul: Float, era: Int, shooterRole: Role?, burnDps: Float = 0f, variant: Int = -1) {
        val p = allocProj() ?: return
        p.side = side; p.type = type; p.x = x; p.h = h; p.tx = tx; p.era = era
        p.shooterRole = shooterRole
        p.variant = variant
        p.turretEra = if (shooterRole == null) era else -1
        p.dmg = dmg; p.splash = splash; p.siegeMul = if (siegeMul <= 0f) 0.6f else siegeMul
        p.delay = 0f; p.t = 0f; p.burnDps = burnDps
        val dist = abs(tx - x)
        val straight = type == Proj.BULLET || type == Proj.BEAM || type == Proj.PLASMA || type == Proj.ROCKET || type == Proj.BOLT
        val speed = when (type) {
            Proj.BULLET -> 1500f; Proj.BEAM -> 2600f; Proj.PLASMA -> 900f; Proj.ROCKET -> 700f; Proj.BOLT -> 1000f
            else -> 620f
        }
        p.dur = (dist / speed).coerceIn(0.12f, 1.3f)
        p.vx = (tx - x) / p.dur
        val targetH = 30f
        if (straight) {
            p.g = 0f
            p.vh = (targetH - h) / p.dur
        } else {
            p.g = 900f
            p.vh = (targetH - h) / p.dur + 0.5f * p.g * p.dur
        }
    }

    private fun updateProj(p: Projectile, dt: Float) {
        if (p.delay > 0f) { p.delay -= dt; return }
        p.t += dt
        p.x += p.vx * dt
        p.vh -= p.g * dt
        p.h += p.vh * dt
        if (p.t < p.dur) return
        p.active = false
        val e = sides[1 - p.side]
        if (p.splash > 0f) {
            splashDamage(p.side, p.tx, p.splash, p.dmg, p.siegeMul, p.shooterRole, p.turretEra, p.burnDps, true)
            return
        }
        var best: Fighter? = null
        var bestD = 42f
        for (u in units) {
            if (!u.alive || u.side == p.side) continue
            val d = abs(u.x - p.tx) - u.radius
            if (d < bestD) { bestD = d; best = u }
        }
        if (best != null) {
            damageUnit(best, p.dmg, p.side, p.shooterRole, p.turretEra, projectile = true)
            if (p.variant >= 0 && Defs.sideArt(p.era, p.variant) == EraArt.GALVANIC) {
                chainDamage(p, best)
            }
            ignite(best, p.burnDps)
            fx.add(FxBuffer.HIT, best.x, 40f * best.def.size, best.side,
                eraId = best.def.era, bodyId = best.def.body)
        } else if ((p.tx - e.baseFront) * -e.dir >= -50f) {
            damageBase(e, p.dmg * p.siegeMul, p.side, p.shooterRole)
        }
    }

    /** Jeden dodatkowy cel, wybrany po odległości i kolejności puli; bez losowania i alokacji. */
    private fun chainDamage(p: Projectile, first: Fighter) {
        var next: Fighter? = null
        var distance = 145f
        for (u in units) {
            if (!u.alive || u.side == p.side || u === first) continue
            val d = abs(u.x - first.x)
            if (d < distance) { distance = d; next = u }
        }
        if (next != null) {
            damageUnit(next, p.dmg * 0.30f, p.side, p.shooterRole, p.turretEra, projectile = true)
            fx.add(FxBuffer.HIT, next.x, 40f * next.def.size, next.side)
        }
    }

    private fun splashDamage(attacker: Int, cx: Float, radius: Float, dmg: Float, siegeMul: Float, shooterRole: Role?, turretEra: Int, burnDps: Float = 0f, projectile: Boolean = false) {
        for (u in units) {
            if (!u.alive || u.side == attacker) continue
            if (abs(u.x - cx) <= radius + u.radius) {
                damageUnit(u, dmg, attacker, shooterRole, turretEra, projectile = projectile)
                ignite(u, burnDps)
            }
        }
        val e = sides[1 - attacker]
        if ((cx - e.baseFront) * -e.dir >= -radius) damageBase(e, dmg * siegeMul, attacker, shooterRole)
        fx.add(FxBuffer.EXPLOSION, cx, 20f, attacker, 0.6f + radius / 100f,
            eraId = sides[attacker].era)
    }

    private fun ignite(u: Fighter, dps: Float) {
        if (!u.alive || dps <= 0f) return
        u.burnDps = max(u.burnDps, dps)
        u.burnLeft = 4f
    }

    /** Bez roli strzelca i epoki wieży (podpalenie, odrzut bossa) mnożnik kontr wynosi 1. */
    private fun damageUnit(u: Fighter, d: Float, attacker: Int, shooterRole: Role? = null,
                           turretEra: Int = -1, direct: Boolean = true, projectile: Boolean = false) {
        if (!u.alive) return
        val multiplier = if (shooterRole != null) Defs.counterMultiplier(shooterRole, u.def.role)
            else Defs.turretCounterMultiplier(turretEra, u.def)
        val shield = projectile && u.hasBossAbility(EndlessBosses.SHIELD) &&
            u.hp > u.maxHp * EndlessBosses.SHIELD_HP
        val rallyArmor = if (sides[u.side].rallyT > 0f) sides[u.side].rallyArmorMul else 1f
        val emberStacks = if (endless && turretEra >= 0 && u.burnLeft > 0f)
            sides[attacker].cardStacks[EndlessCard.EMBER_FORGE.ordinal] else 0
        // Tarcza pochłania pierwsze trafienie przy pełnym zdrowiu (także po wyleczeniu).
        val firstGuard = if (endless && u.side == 0 && u.hp >= u.maxHp &&
            sides[0].cardStacks[EndlessCard.FIRST_GUARD.ordinal] > 0) 0.6f else 1f
        val dealt = d * multiplier * rallyArmor * firstGuard *
            (1f + EndlessCards.totalStackPower(emberStacks) * 0.10f) * (if (shield) 0.5f else 1f)
        u.hp -= dealt
        if (endless && turretEra >= 0 && attacker == 0 && u.hp > 0f &&
            sides[attacker].cardStacks[EndlessCard.IRON_CURTAIN.ordinal] > 0) {
            u.slowLeft = max(u.slowLeft, 2f)
        }
        if (direct) {
            u.hitStep = stepCount
            u.hitPow = (dealt / max(1f, u.maxHp) * 4f).coerceIn(0f, 1f)
        }
        if (u.hp > 0f && u.hp <= u.maxHp * 0.5f &&
            u.hasBossAbility(EndlessBosses.RAGE) && !u.bossRageActive) {
            u.bossRageActive = true
            fx.add(FxBuffer.EVOLVE, u.x, 0f, u.side, 2f)
        }
        u.flash = 0.12f
        if (u.hp <= 0f) {
            u.hp = 0f
            u.dying = 0f
            val k = sides[attacker]
            val v = sides[u.side]
            val cost = u.def.cost
            if (!(endless && attacker == 1)) {
                val supply = if (time < k.supplyUntil) 1.3f else 1f
                val g = cost * 1.1f * k.killGoldMul * supply
                k.gold += g
                k.goldEarned += g
                if (attacker == 0) fx.add(FxBuffer.GOLD, u.x, 90f * u.def.size, 0, g)
            }
            k.xp += cost * 1.0f * k.xpMul
            v.xp += cost * 0.25f * v.xpMul
            k.kills++
            k.killValue += cost
            if (endless && u.isBoss && attacker == 0) {
                bossesDefeated++
                bossRewardsPending++
                // Po pokonaniu bossa oba rozkazy gracza są znów dostępne.
                if (k.cardStacks[EndlessCard.BOSS_RECALL.ordinal] > 0) {
                    k.abilityCd[0] = 0f
                    k.abilityCd[1] = 0f
                }
            }
            v.aliveCount--
            liveDirty = true
            if (endless && u.side == 0 && attacker == 1) {
                val echo = EndlessCards.totalStackPower(
                    v.cardStacks[EndlessCard.FINAL_SPARK.ordinal])
                if (echo > 0f) {
                    fx.add(FxBuffer.EXPLOSION, u.x, 20f, 0, 0.75f)
                    // Iskra nie zadaje obrażeń bazie ani sojusznikom; bez dodatkowego RNG.
                    for (target in units) if (target.alive && target.side == 1 &&
                        abs(target.x - u.x) <= 95f + target.radius) {
                        damageUnit(target, u.maxHp * 0.18f * echo, 0, direct = false)
                    }
                }
            }
            if (u.def.role == Role.ELITE) v.elitesLost++
            fx.add(FxBuffer.DEATH, u.x, 0f, u.side, u.def.size,
                eraId = u.def.era, bodyId = u.def.body)
        }
    }

    private fun damageBase(s: Side, d: Float, attacker: Int, attackerRole: Role? = null) {
        if (endless && s.index == 1) return
        val k = sides[attacker]
        // Doktryna Oblężenie (siegeMul strony atakującej): +30% obrażeń wobec baz.
        val before = s.baseHp
        val heavyBonus = if (attackerRole == Role.HEAVY) k.heavyBaseDmgMul else 1f
        s.baseHp = (s.baseHp - d * k.siegeMul * heavyBonus * siegePressure()).coerceIn(0f, s.baseMaxHp)
        val damage = before - s.baseHp
        if (damage > 0f) s.baseDamageTaken += damage
        s.baseFlash = 0.15f
        k.xp += d * 0.12f * k.xpMul
        fx.add(FxBuffer.BASE_HIT, s.baseFront - s.dir * 30f, 80f, s.index, eraId = s.era)
    }

    // ---------------------------------------------------------------- tryb bez końca


    private fun director(dt: Float) {
        val p = player
        if (p.autoBarrageInterval > 0f) {
            p.autoBarrageTimer -= dt
            if (p.autoBarrageTimer <= 0f) {
                p.autoBarrageTimer += p.autoBarrageInterval
                fireBarrage(p)
            }
        }
        spawnFreeElites()
        if (wave >= nextBossToSpawn) spawnPendingBoss()
        waveTimer -= dt
        if (waveTimer > 0f) return
        wave++
        waveTimer = max(16f, 28f - wave * 0.25f)
        currentWaveTheme = nextWaveTheme
        nextWaveTheme = EndlessCards.nextTheme(rng)

        val e = enemy
        // Zmiana epoki od razu przełącza pogodę wyliczaną z seeda i enemy.era.
        val nextEra = min(cfg.maxEra, cfg.startEra + wave / 6)
        if (nextEra != e.era) {
            e.era = nextEra; e.eraVariant = -1; e.unitTier.fill(1)
            if (cfg.variantsEnabled) offerVariant(e)
        }
        val inEra = wave % 6
        // Dyrektor Endless przelicza te mnożniki po fali; zachowujemy rangę startową.
        val veteranBonus = if (Modifier.VETERANS in cfg.modifiers) 1.06f * 1.06f else 1f
        e.hpMul = (1f + inEra * 0.05f) * (1f + max(0, wave - 48) * 0.06f) * veteranBonus
        e.dmgMul = (1f + inEra * 0.03f) * (1f + max(0, wave - 32) * 0.055f) * veteranBonus
        var budget = 45f * Defs.costScale(e.era) * (1f + inEra * 0.18f + wave * 0.02f) *
            when (currentWaveTheme) {
                EndlessWaveTheme.SIEGE_TRAIN -> 1.15f
                EndlessWaveTheme.SKIRMISHERS -> 1.3f
                else -> 1f
            }
        var n = 0
        while (budget > 0f && n < 16 && e.queueSize < e.queueMax) {
            val def = e.unit(waveRole(rng.nextInt(100), n))
            e.queue[e.queueSize] = def.id
            e.queueWaveTheme[e.queueSize] = currentWaveTheme.ordinal
            e.queueSize++
            budget -= def.cost
            n++
        }
        if (p.repairPerWave > 0f) {
            p.baseHp = min(p.baseMaxHp, p.baseHp + p.baseMaxHp * p.repairPerWave)
        }
        // Medycy leczą żywych rannych dopiero na początku nowej fali.
        val medics = p.cardStacks[EndlessCard.FIELD_MEDICS.ordinal]
        if (medics > 0) {
            val healing = min(0.45f, 0.16f * EndlessCards.totalStackPower(medics))
            for (unit in units) if (unit.alive && unit.side == 0 && unit.hp < unit.maxHp) {
                unit.hp += (unit.maxHp - unit.hp) * healing
            }
        }
        if (p.freeElitesPerWave > 0) {
            p.freeEliteCredits += p.freeElitesPerWave
            spawnFreeElites()
        }
        if (wave % 10 == 0) spawnPendingBoss()
        if (wave % ENDLESS_CHOICE_EVERY == 0) offerEndlessCards()

        // Stopniowo narastająca presja po bardzo długim oblężeniu usuwa wieczne paty.
        if (wave >= 120) {
            damageBase(p, p.baseMaxHp * (wave - 119) * 0.008f, 1)
        }
    }

    internal fun waveRole(roll: Int, slot: Int): Role = when (currentWaveTheme) {
        EndlessWaveTheme.MIXED -> when {
            wave >= 4 && roll < 10 -> Role.ELITE
            roll < 30 -> Role.HEAVY
            roll < 60 -> Role.RANGED
            else -> Role.MELEE
        }
        EndlessWaveTheme.RANGED_SWARM -> if (roll < 75) Role.RANGED else Role.MELEE
        EndlessWaveTheme.ARMORED_COLUMN -> when {
            wave >= 4 && roll < 20 -> Role.ELITE
            roll < 85 -> Role.HEAVY
            else -> Role.MELEE
        }
        EndlessWaveTheme.FAST_RUSH -> if (roll < 85) Role.MELEE else Role.RANGED
        EndlessWaveTheme.ELITE_GUARD -> if (wave >= 4 && roll < 55) Role.ELITE else Role.HEAVY
        EndlessWaveTheme.ARTILLERY -> when {
            wave >= 4 && roll < 35 -> Role.ELITE
            roll < 85 -> Role.RANGED
            else -> Role.HEAVY
        }
        EndlessWaveTheme.SIEGE_TRAIN -> when {
            wave >= 4 && roll < 25 -> Role.ELITE
            roll < 80 -> Role.HEAVY
            else -> Role.RANGED
        }
        EndlessWaveTheme.SKIRMISHERS -> when {
            roll < 70 -> Role.MELEE
            roll < 95 -> Role.RANGED
            else -> Role.HEAVY
        }
        // Ekran ciężkich jednostek osłania strzelców w kolejce tej fali.
        EndlessWaveTheme.PIKE_SCREEN ->
            if (slot < 2) Role.HEAVY else if (roll < 80) Role.RANGED else Role.MELEE
        // Dwa oddziały szturmowe, strzelec, a od czwartej fali elita.
        EndlessWaveTheme.TWIN_RAID -> when (slot % 4) {
            0, 1 -> Role.MELEE
            2 -> Role.RANGED
            else -> if (wave >= 4) Role.ELITE else Role.RANGED
        }
    }

    private fun spawnFreeElites() {
        val p = player
        while (p.freeEliteCredits > 0 && p.aliveCount < p.cap) {
            if (!spawn(p, p.unit(Role.ELITE))) break
            p.freeEliteCredits--
        }
    }

    private fun spawnPendingBoss() {
        val bossWave = nextBossToSpawn
        if (wave < bossWave) return
        val e = enemy
        val f = spawnFighter(e, e.unit(Role.ELITE), 3f + bossWave / 10f * 0.45f)
            ?: return
        f.bossKind = EndlessBosses.kindAtWave(bossWave)
        f.bossSecondaryKind = EndlessBosses.secondaryAtWave(bossWave)
        f.dmg *= 1.4f + bossWave / 10f * 0.1f
        f.bossCd = 1.5f
        f.bossSummonCd = EndlessBosses.RECRUIT_INTERVAL
        bossesSpawned++
        nextBossToSpawn += 10
        fx.add(FxBuffer.EVOLVE, f.x, 0f, e.index, 2f, eraId = f.def.era, bodyId = f.def.body)
    }

    // ---------------------------------------------------------------- wynik

    fun stars(): Int {
        if (winner != 0) return 0
        val r = player.baseHp / player.baseMaxHp
        return when {
            r >= 0.75f -> 3
            r >= 0.4f -> 2
            else -> 1
        }
    }

    /** Wynik do rankingów. Liczony wyłącznie z przebiegu symulacji. */
    fun score(): Long = when (cfg.mode) {
        Mode.ENDLESS -> wave * 250L + player.kills * 12L + (time / 2f).toLong() + bossesDefeated * 1500L
        else -> {
            if (winner != 0) player.killValue.toLong() / 4
            else {
                val hp = (player.baseHp / player.baseMaxHp * 5000f).toLong()
                val speed = max(0L, 6000L - (time * 4f).toLong())
                10_000L + hp + speed + player.kills * 10L
            }
        }
    }
}
