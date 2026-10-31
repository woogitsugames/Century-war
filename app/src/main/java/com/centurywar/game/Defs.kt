package com.centurywar.game


/** Role jednostki na pasie. Każda epoka ma po jednej jednostce z każdej roli. */
enum class Role { MELEE, RANGED, HEAVY, ELITE }

/** Wygląd broni: renderer rysuje go proceduralnie (gra nie ma żadnych plików graficznych). */
object Weapon {
    const val CLUB = 0
    const val SLING = 1
    const val SPEAR = 2
    const val SWORD = 3
    const val BOW = 4
    const val CROSSBOW = 5
    const val MUSKET = 6
    const val RIFLE = 7
    const val MG = 8
    const val LAUNCHER = 9
    const val BLASTER = 10
    const val RAIL = 11
    const val PIKE = 12
}

/** Jak renderer rysuje ciało jednostki. */
object Body {
    const val HUMAN = 0
    const val BEAST = 1      // czworonóg, opcjonalnie z jeźdźcem
    const val SIEGE = 2      // drewniana machina
    const val CANNON = 3     // działo na kołach
    const val VEHICLE = 4    // pojazd na gąsienicach / kołach
    const val MECH = 5       // dwunożny robot
    const val HOVER = 6      // pojazd unoszący się
}

class UnitDef(
    val id: Int,
    val era: Int,
    val role: Role,
    val nameKey: String,
    val cost: Int,
    val hp: Float,
    val dmg: Float,
    val range: Float,
    val cooldown: Float,
    val speed: Float,
    val trainTime: Float,
    val size: Float,
    val splash: Float,
    val projectile: Int,
    val weapon: Int,
    val body: Int,
    /** Mnożnik obrażeń zadawanych bazom i wieżom (oblężnicze jednostki mają > 1). */
    val siegeMul: Float,
)

class TurretDef(
    val era: Int,
    val nameKey: String,
    val cost: Int,
    val dmg: Float,
    val range: Float,
    val cooldown: Float,
    val splash: Float,
    val projectile: Int,
)

class EraDef(
    val index: Int,
    val nameKey: String,
    /** XP potrzebne, żeby z tej epoki przejść do następnej. */
    val evolveXp: Int,
    val units: Array<UnitDef>,
    val turret: TurretDef,
    val abilityKey: String,
)

/** Typy pocisków, które renderer potrafi narysować. */
object Proj {
    const val NONE = 0
    const val STONE = 1
    const val JAVELIN = 2
    const val ARROW = 3
    const val BOLT = 4
    const val BALL = 5
    const val BULLET = 6
    const val SHELL = 7
    const val ROCKET = 8
    const val PLASMA = 9
    const val BEAM = 10
    const val BOULDER = 11
}

/**
 * Wszystkie dane balansu w jednym miejscu. Statystyki liczymy wzorem
 * (szablon roli × współczynnik epoki), więc balans stroi się kilkoma liczbami,
 * a nie setką ręcznie wpisanych wartości.
 */
object Defs {
    const val ERA_COUNT = 8
    const val LANE = 2800f
    const val BASE_FRONT = 190f
    const val MAX_TURRET_SLOTS = 4

    /** Wiersz: rola atakująca; kolumna: rola obrońcy. Kolejność jak w [Role]. */
    private val roleMultipliers = arrayOf(
        floatArrayOf(1f, 1.35f, 1f, 1f), // wręcz kontruje dystans
        floatArrayOf(1f, 1f, 1.30f, 1f), // dystans kontruje ciężkich
        floatArrayOf(1.35f, 1f, 1f, 1f), // ciężcy kontrują wręcz
        floatArrayOf(1f, 1f, 1f, 1f), // elita specjalizuje się w oblężeniu
    )
    const val TURRET_VS_LATE_HEAVY = 1.15f

    fun counterMultiplier(attacker: Role, defender: Role): Float =
        roleMultipliers[attacker.ordinal][defender.ordinal]

    /** Premia późnych wież przeciw ciężkim jednostkom od epoki pary. */
    fun turretCounterMultiplier(turretEra: Int, defender: UnitDef): Float =
        if (turretEra >= 4 && defender.era >= 4 && defender.role == Role.HEAVY)
            TURRET_VS_LATE_HEAVY else 1f

    private const val STAT_GROWTH = 2.05f
    private const val COST_GROWTH = 1.9f

    val eraKeys = arrayOf(
        "era_tribal", "era_bronze", "era_feudal", "era_powder",
        "era_steam", "era_steel", "era_silicon", "era_orbital",
    )

    private val unitNames = arrayOf(
        arrayOf("u_clubman", "u_slinger", "u_brute", "u_tusker"),
        arrayOf("u_spearman", "u_javelineer", "u_chariot", "u_warbeast"),
        arrayOf("u_footman", "u_crossbow", "u_knight", "u_trebuchet"),
        arrayOf("u_pikeman", "u_musketeer", "u_dragoon", "u_cannon"),
        arrayOf("u_rifleman", "u_sharpshooter", "u_walker", "u_ironclad"),
        arrayOf("u_infantry", "u_gunner", "u_halftrack", "u_heavytank"),
        arrayOf("u_marine", "u_rocketeer", "u_apc", "u_mech"),
        arrayOf("u_plasma", "u_railgunner", "u_hovertank", "u_titan"),
    )

    private val weapons = arrayOf(
        intArrayOf(Weapon.CLUB, Weapon.SLING, Weapon.CLUB, Weapon.SPEAR),
        intArrayOf(Weapon.SPEAR, Weapon.SPEAR, Weapon.BOW, Weapon.SPEAR),
        intArrayOf(Weapon.SWORD, Weapon.CROSSBOW, Weapon.PIKE, Weapon.SWORD),
        intArrayOf(Weapon.PIKE, Weapon.MUSKET, Weapon.SWORD, Weapon.MUSKET),
        intArrayOf(Weapon.RIFLE, Weapon.RIFLE, Weapon.MG, Weapon.MG),
        intArrayOf(Weapon.RIFLE, Weapon.MG, Weapon.MG, Weapon.MG),
        intArrayOf(Weapon.RIFLE, Weapon.LAUNCHER, Weapon.MG, Weapon.LAUNCHER),
        intArrayOf(Weapon.BLASTER, Weapon.RAIL, Weapon.BLASTER, Weapon.RAIL),
    )

    private val heavyBody = intArrayOf(Body.HUMAN, Body.BEAST, Body.BEAST, Body.BEAST, Body.MECH, Body.VEHICLE, Body.VEHICLE, Body.HOVER)
    private val eliteBody = intArrayOf(Body.BEAST, Body.BEAST, Body.SIEGE, Body.CANNON, Body.VEHICLE, Body.VEHICLE, Body.MECH, Body.MECH)

    private val rangedProj = intArrayOf(Proj.STONE, Proj.JAVELIN, Proj.BOLT, Proj.BULLET, Proj.BULLET, Proj.BULLET, Proj.ROCKET, Proj.BEAM)
    private val eliteProj = intArrayOf(Proj.NONE, Proj.NONE, Proj.BOULDER, Proj.BALL, Proj.SHELL, Proj.SHELL, Proj.ROCKET, Proj.PLASMA)
    private val heavyProj = intArrayOf(Proj.NONE, Proj.ARROW, Proj.NONE, Proj.NONE, Proj.BULLET, Proj.BULLET, Proj.BULLET, Proj.PLASMA)

    private val turretNames = arrayOf(
        "t_rock", "t_ballista", "t_arrow", "t_cannon", "t_gatling", "t_flak", "t_missile", "t_laser",
    )
    private val turretProj = intArrayOf(Proj.STONE, Proj.JAVELIN, Proj.ARROW, Proj.BALL, Proj.BULLET, Proj.SHELL, Proj.ROCKET, Proj.BEAM)
    private val abilityKeys = arrayOf(
        "ab_rocks", "ab_spears", "ab_arrows", "ab_cannons", "ab_shells", "ab_bombers", "ab_missiles", "ab_orbital",
    )

    val evolveXp = intArrayOf(1000, 2600, 6400, 15500, 37000, 88000, 210000, Int.MAX_VALUE)

    fun statScale(era: Int): Float = StrictMath.pow(STAT_GROWTH.toDouble(), era.toDouble()).toFloat()
    fun costScale(era: Int): Float = StrictMath.pow(COST_GROWTH.toDouble(), era.toDouble()).toFloat()

    val eras: Array<EraDef> = Array(ERA_COUNT) { e -> buildEra(e) }

    /** Płaska tablica wszystkich jednostek; id = era * 4 + rola. */
    val allUnits: Array<UnitDef> = Array(ERA_COUNT * 4) { eras[it / 4].units[it % 4] }

    fun unit(era: Int, role: Role): UnitDef = eras[era].units[role.ordinal]

    private fun buildEra(e: Int): EraDef {
        val s = statScale(e)
        val c = costScale(e)
        val sp = 1f + e * 0.035f
        val n = unitNames[e]
        val w = weapons[e]
        val units = arrayOf(
            UnitDef(e * 4 + 0, e, Role.MELEE, n[0], (15 * c).toInt(), 72 * s, 15 * s, 30f, 1.0f, 64 * sp, 1.2f, 1.0f, 0f, Proj.NONE, w[0], Body.HUMAN, 1f),
            UnitDef(e * 4 + 1, e, Role.RANGED, n[1], (26 * c).toInt(), 46 * s, 12.5f * s, 250f + e * 12f, 1.3f, 60 * sp, 1.6f, 0.95f, 0f, rangedProj[e], w[1], Body.HUMAN, 1f),
            UnitDef(e * 4 + 2, e, Role.HEAVY, n[2], (62 * c).toInt(), 265 * s, 30 * s, if (heavyProj[e] == Proj.NONE) 36f else 160f, 1.4f, 50 * sp, 3.0f, 1.3f, 0f, heavyProj[e], w[2], heavyBody[e], 1f),
            UnitDef(e * 4 + 3, e, Role.ELITE, n[3], (165 * c).toInt(), 540 * s, 58 * s, if (eliteProj[e] == Proj.NONE) 44f else 340f, 2.1f, 40 * sp, 6.0f, 1.75f, if (eliteProj[e] == Proj.NONE) 40f else 85f, eliteProj[e], w[3], eliteBody[e], 1.6f),
        )
        val turret = TurretDef(
            e, turretNames[e], (110 * c).toInt(), 20 * s, 540f + e * 15f,
            if (e == 4) 0.45f else 1.25f,
            if (turretProj[e] == Proj.SHELL || turretProj[e] == Proj.BALL || turretProj[e] == Proj.ROCKET) 60f else 0f,
            turretProj[e],
        ).let { if (it.era == 4) TurretDef(it.era, it.nameKey, it.cost, it.dmg * 0.4f, it.range, it.cooldown, it.splash, it.projectile) else it }
        return EraDef(e, eraKeys[e], evolveXp[e], units, turret, abilityKeys[e])
    }

    /** Bazowe HP bazy w danej epoce, przed bonusami. */
    fun baseHp(era: Int): Float = 1600f * StrictMath.pow(1.85f.toDouble(), era.toDouble()).toFloat()

    /** Pasywny przychód złota na sekundę w danej epoce. */
    fun income(era: Int): Float = 2.6f * StrictMath.pow(1.8f.toDouble(), era.toDouble()).toFloat()

    /** Obrażenia jednego pocisku zdolności specjalnej. */
    fun abilityDmg(era: Int): Float = 46f * statScale(era)
}
