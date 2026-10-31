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
    val variant: Int = -1,
    val art: EraArt = Defs.spec(era).art,
) {
    /** Klucze lokalizowanych nazw trzech stopni. */
    val tierNameKeys = arrayOf(nameKey, nameKey + "_t2", nameKey + "_t3")
}

class TurretDef(
    val era: Int,
    val nameKey: String,
    val cost: Int,
    val dmg: Float,
    val range: Float,
    val cooldown: Float,
    val splash: Float,
    val projectile: Int,
    val variant: Int = -1,
    val art: EraArt = Defs.spec(era).art,
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

/** Identyfikatory stylów, niezależne od miejsca epoki w kampanii. */
enum class EraArt { TRIBAL, BRONZE, IRON, FEUDAL, POWDER, STEAM, STEEL, SILICON, ORBITAL, COMBUSTION, GALVANIC, SWARM, BIOTECH }

/** Rodzaj krajobrazu i obiektów tła. */
enum class Terrain { WOODS, DESERT, PAVED_HILLS, PINES, INDUSTRIAL, CITY, SPACE }

/** Komplet danych jednej epoki. Paleta: niebo góra/dół, trzy plany wzgórz, grunt. */
class EraSpec(
    val nameKey: String,
    val unitNames: Array<String>,
    val weapons: IntArray,
    val heavyBody: Int,
    val eliteBody: Int,
    val rangedProj: Int,
    val heavyProj: Int,
    val eliteProj: Int,
    val turretName: String,
    val turretProj: Int,
    val abilityKey: String,
    val barrageProj: Int,
    val evolveXp: Int,
    val art: EraArt,
    val terrain: Terrain,
    val palette: IntArray,
    val accent: Int,
    val fastTurret: Boolean,
    val variants: Array<EraVariant> = emptyArray(),
)

/** Odmienny skład, wieża i ostrzał w obrębie epoki. Kolejność pól statystyk odpowiada Role. */
class EraVariant(
    val key: String,
    val nameKey: String,
    val descKey: String,
    val art: EraArt,
    val unitNames: Array<String>,
    val heavyBody: Int,
    val eliteBody: Int,
    val heavyProj: Int,
    val eliteProj: Int,
    val heavyWeapon: Int,
    val eliteWeapon: Int,
    val costMul: FloatArray,
    val hpMul: FloatArray,
    val dmgMul: FloatArray,
    val speedMul: FloatArray,
    val turretName: String,
    val turretProj: Int,
    val turretDmgMul: Float,
    val turretCooldownMul: Float,
    val barrageProj: Int,
    val barrageCount: Int,
    val barrageSplash: Float,
    val terrain: Terrain? = null,
    val palette: IntArray? = null,
)

class BarrageDef(val projectile: Int, val count: Int, val splash: Float)

/** Dane epok bez równoległych tablic; każda kolejna jest jednym wpisem. */
object Defs {
    const val LANE = 2800f
    const val BASE_FRONT = 190f
    const val MAX_TURRET_SLOTS = 4
    const val MAX_UNIT_TIER = 3
    private val upgradeBase = intArrayOf(65, 95, 155, 255)

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

    // Osiem przejść przy końcowych statystykach zbliżonych do dawnych siedmiu.
    private const val STAT_GROWTH = 1.8740649f
    private const val COST_GROWTH = 1.7535147f
    private const val BASE_GROWTH = 1.7130706f
    private const val INCOME_GROWTH = 1.6724897f

    val specs: Array<EraSpec> = arrayOf(
        EraSpec("era_tribal", arrayOf("u_clubman", "u_slinger", "u_brute", "u_tusker"),
            intArrayOf(Weapon.CLUB, Weapon.SLING, Weapon.CLUB, Weapon.SPEAR), Body.HUMAN, Body.BEAST,
            Proj.STONE, Proj.NONE, Proj.NONE, "t_rock", Proj.STONE, "ab_rocks", Proj.BOULDER,
            950, EraArt.TRIBAL, Terrain.WOODS,
            intArrayOf(0xFF6EBEF0.toInt(), 0xFFC8E6FA.toInt(), 0xFF7FA0B8.toInt(), 0xFF5A8C50.toInt(), 0xFF3C6E3C.toInt(), 0xFF967846.toInt()), 0xFF96683C.toInt(), false),
        EraSpec("era_bronze", arrayOf("u_spearman", "u_javelineer", "u_chariot", "u_warbeast"),
            intArrayOf(Weapon.SPEAR, Weapon.SPEAR, Weapon.BOW, Weapon.SPEAR), Body.BEAST, Body.BEAST,
            Proj.JAVELIN, Proj.ARROW, Proj.NONE, "t_ballista", Proj.JAVELIN, "ab_spears", Proj.JAVELIN,
            2400, EraArt.BRONZE, Terrain.DESERT,
            intArrayOf(0xFF78B4E6.toInt(), 0xFFF0DCB4.toInt(), 0xFFC8AA7A.toInt(), 0xFFB48C5A.toInt(), 0xFF9C7448.toInt(), 0xFFC8AA6E.toInt()), 0xFFC48032.toInt(), false),
        EraSpec("era_iron", arrayOf("u_iron_guard", "u_iron_bow", "u_iron_rider", "u_iron_scorpion"),
            intArrayOf(Weapon.SPEAR, Weapon.BOW, Weapon.SWORD, Weapon.CROSSBOW), Body.BEAST, Body.SIEGE,
            Proj.ARROW, Proj.NONE, Proj.BOLT, "t_iron_ballista", Proj.BOLT, "ab_iron_pilum", Proj.JAVELIN,
            4100, EraArt.IRON, Terrain.PAVED_HILLS,
            intArrayOf(0xFF79A9DA.toInt(), 0xFFD9D5C0.toInt(), 0xFF97A0AC.toInt(), 0xFF718C62.toInt(), 0xFF667D50.toInt(), 0xFF857C61.toInt()), 0xFFA9744C.toInt(), false),
        EraSpec("era_feudal", arrayOf("u_footman", "u_crossbow", "u_knight", "u_trebuchet"),
            intArrayOf(Weapon.SWORD, Weapon.CROSSBOW, Weapon.PIKE, Weapon.SWORD), Body.BEAST, Body.SIEGE,
            Proj.BOLT, Proj.NONE, Proj.BOULDER, "t_arrow", Proj.ARROW, "ab_arrows", Proj.ARROW,
            6200, EraArt.FEUDAL, Terrain.PINES,
            intArrayOf(0xFF64AAE6.toInt(), 0xFFD2E6F5.toInt(), 0xFF7E9EB0.toInt(), 0xFF50825A.toInt(), 0xFF326440.toInt(), 0xFF788C46.toInt()), 0xFF547096.toInt(), false),
        EraSpec("era_powder", arrayOf("u_pikeman", "u_musketeer", "u_dragoon", "u_cannon"),
            intArrayOf(Weapon.PIKE, Weapon.MUSKET, Weapon.SWORD, Weapon.MUSKET), Body.BEAST, Body.CANNON,
            Proj.BULLET, Proj.NONE, Proj.BALL, "t_cannon", Proj.BALL, "ab_cannons", Proj.BALL,
            14500, EraArt.POWDER, Terrain.WOODS,
            intArrayOf(0xFF82AAD2.toInt(), 0xFFE6D2BE.toInt(), 0xFF8C9AA0.toInt(), 0xFF6E8250.toInt(), 0xFF506440.toInt(), 0xFF8C7850.toInt()), 0xFF963A32.toInt(), false),
        EraSpec("era_steam", arrayOf("u_rifleman", "u_sharpshooter", "u_walker", "u_ironclad"),
            intArrayOf(Weapon.RIFLE, Weapon.RIFLE, Weapon.MG, Weapon.MG), Body.MECH, Body.VEHICLE,
            Proj.BULLET, Proj.BULLET, Proj.SHELL, "t_gatling", Proj.BULLET, "ab_shells", Proj.SHELL,
            34500, EraArt.STEAM, Terrain.INDUSTRIAL,
            intArrayOf(0xFF969696.toInt(), 0xFFD2BEA0.toInt(), 0xFF7C7A78.toInt(), 0xFF646458.toInt(), 0xFF4E4E46.toInt(), 0xFF6E5F50.toInt()), 0xFF8C7040.toInt(), true,
            variants = arrayOf(
                EraVariant("combustion", "v_combustion", "v_combustion_d", EraArt.COMBUSTION,
                    arrayOf("u_comb_guard", "u_comb_gunner", "u_comb_carrier", "u_comb_colossus"),
                    Body.VEHICLE, Body.VEHICLE, Proj.SHELL, Proj.SHELL, Weapon.MG, Weapon.LAUNCHER,
                    floatArrayOf(1.03f, 1.02f, 1.10f, 1.12f),
                    floatArrayOf(1.02f, 1.02f, 1.25f, 1.30f),
                    floatArrayOf(1f, 1f, 1.04f, 1.08f),
                    floatArrayOf(0.98f, 0.98f, 0.78f, 0.74f),
                    "t_combustion", Proj.SHELL, 1.13f, 1.08f, Proj.SHELL, 12, 72f,
                    terrain = Terrain.INDUSTRIAL,
                    palette = intArrayOf(0xFF77706C.toInt(), 0xFFC3A38A.toInt(),
                        0xFF736960.toInt(), 0xFF5A534C.toInt(), 0xFF49463E.toInt(), 0xFF73624D.toInt())),
                EraVariant("galvanic", "v_galvanic", "v_galvanic_d", EraArt.GALVANIC,
                    arrayOf("u_galv_guard", "u_galv_gunner", "u_galv_coil", "u_galv_arc"),
                    Body.MECH, Body.HOVER, Proj.BEAM, Proj.PLASMA, Weapon.RAIL, Weapon.BLASTER,
                    floatArrayOf(1.02f, 1.02f, 1.10f, 1.12f),
                    floatArrayOf(0.98f, 0.98f, 0.82f, 0.80f),
                    floatArrayOf(1f, 1f, 1.08f, 1.06f),
                    floatArrayOf(1.02f, 1.02f, 1.08f, 1.12f),
                    "t_galvanic", Proj.BEAM, 0.90f, 0.94f, Proj.BEAM, 16, 45f,
                    terrain = Terrain.CITY,
                    palette = intArrayOf(0xFF477C8A.toInt(), 0xFFB4D9D1.toInt(),
                        0xFF578E94.toInt(), 0xFF3B646D.toInt(), 0xFF2B4F5B.toInt(), 0xFF4D6C69.toInt())),
            )),
        EraSpec("era_steel", arrayOf("u_infantry", "u_gunner", "u_halftrack", "u_heavytank"),
            intArrayOf(Weapon.RIFLE, Weapon.MG, Weapon.MG, Weapon.MG), Body.VEHICLE, Body.VEHICLE,
            Proj.BULLET, Proj.BULLET, Proj.SHELL, "t_flak", Proj.SHELL, "ab_bombers", Proj.SHELL,
            82000, EraArt.STEEL, Terrain.INDUSTRIAL,
            intArrayOf(0xFF78828C.toInt(), 0xFFC8BEAA.toInt(), 0xFF6E7478.toInt(), 0xFF5A644B.toInt(), 0xFF464E3A.toInt(), 0xFF696450.toInt()), 0xFF606E4A.toInt(), false),
        EraSpec("era_silicon", arrayOf("u_marine", "u_rocketeer", "u_apc", "u_mech"),
            intArrayOf(Weapon.RIFLE, Weapon.LAUNCHER, Weapon.MG, Weapon.LAUNCHER), Body.VEHICLE, Body.MECH,
            Proj.ROCKET, Proj.BULLET, Proj.ROCKET, "t_missile", Proj.ROCKET, "ab_missiles", Proj.ROCKET,
            194000, EraArt.SILICON, Terrain.CITY,
            intArrayOf(0xFF3C6EAA.toInt(), 0xFFAAC8E6.toInt(), 0xFF46506E.toInt(), 0xFF3C465A.toInt(), 0xFF2A3246.toInt(), 0xFF5A5A5F.toInt()), 0xFF288CA0.toInt(), false,
            variants = arrayOf(
                EraVariant("swarm", "v_swarm", "v_swarm_d", EraArt.SWARM,
                    arrayOf("u_swarm_scout", "u_swarm_spitter", "u_swarm_cloud", "u_swarm_carrier"),
                    Body.HOVER, Body.HOVER, Proj.PLASMA, Proj.ROCKET, Weapon.BLASTER, Weapon.LAUNCHER,
                    floatArrayOf(0.86f, 0.90f, 0.86f, 0.91f),
                    floatArrayOf(0.86f, 0.88f, 0.78f, 0.85f),
                    floatArrayOf(0.95f, 0.96f, 0.90f, 0.94f),
                    floatArrayOf(1.16f, 1.16f, 1.22f, 1.18f),
                    "t_swarm", Proj.PLASMA, 0.89f, 0.86f, Proj.PLASMA, 20, 42f,
                    terrain = Terrain.CITY,
                    palette = intArrayOf(0xFF3268A6.toInt(), 0xFFB8DDF1.toInt(),
                        0xFF547DB2.toInt(), 0xFF42718C.toInt(), 0xFF2E5169.toInt(), 0xFF526E76.toInt())),
                EraVariant("biotech", "v_biotech", "v_biotech_d", EraArt.BIOTECH,
                    arrayOf("u_bio_guard", "u_bio_spore", "u_bio_golem", "u_bio_leviathan"),
                    Body.BEAST, Body.MECH, Proj.PLASMA, Proj.BEAM, Weapon.BLASTER, Weapon.RAIL,
                    floatArrayOf(1.16f, 1.14f, 1.20f, 1.24f),
                    floatArrayOf(1.12f, 1.08f, 1.20f, 1.25f),
                    floatArrayOf(0.98f, 0.98f, 1.01f, 1.02f),
                    floatArrayOf(0.94f, 0.98f, 0.93f, 0.90f),
                    "t_biotech", Proj.PLASMA, 1.04f, 1.04f, Proj.PLASMA, 12, 72f,
                    terrain = Terrain.WOODS,
                    palette = intArrayOf(0xFF447D78.toInt(), 0xFFBAD5B3.toInt(),
                        0xFF638C78.toInt(), 0xFF426E5A.toInt(), 0xFF305746.toInt(), 0xFF517553.toInt())),
            )),
        EraSpec("era_orbital", arrayOf("u_plasma", "u_railgunner", "u_hovertank", "u_titan"),
            intArrayOf(Weapon.BLASTER, Weapon.RAIL, Weapon.BLASTER, Weapon.RAIL), Body.HOVER, Body.MECH,
            Proj.BEAM, Proj.PLASMA, Proj.PLASMA, "t_laser", Proj.BEAM, "ab_orbital", Proj.BEAM,
            Int.MAX_VALUE, EraArt.ORBITAL, Terrain.SPACE,
            intArrayOf(0xFF140F32.toInt(), 0xFF5A3278.toInt(), 0xFF3A2860.toInt(), 0xFF2C2050.toInt(), 0xFF22183E.toInt(), 0xFF463C5A.toInt()), 0xFF6E4CAA.toInt(), false),
    )
    val ERA_COUNT: Int = specs.size
    val eraKeys: Array<String> = Array(ERA_COUNT) { specs[it].nameKey }
    val evolveXp: IntArray = IntArray(ERA_COUNT) { specs[it].evolveXp }

    fun spec(era: Int): EraSpec = specs[era]

    /** Indeks gotowego stylu postaci i machin; nie jest numerem epoki. */
    fun artPreset(era: Int): Int = artPreset(specs[era].art)
    fun artPreset(art: EraArt): Int = when (art) {
        EraArt.TRIBAL -> 0
        EraArt.BRONZE -> 1
        EraArt.IRON, EraArt.FEUDAL -> 2
        EraArt.POWDER -> 3
        EraArt.STEAM, EraArt.COMBUSTION, EraArt.GALVANIC -> 4
        EraArt.STEEL -> 5
        EraArt.SILICON, EraArt.SWARM, EraArt.BIOTECH -> 6
        EraArt.ORBITAL -> 7
    }
    fun statScale(era: Int): Float = StrictMath.pow(STAT_GROWTH.toDouble(), era.toDouble()).toFloat()
    fun costScale(era: Int): Float = StrictMath.pow(COST_GROWTH.toDouble(), era.toDouble()).toFloat()

    val eras: Array<EraDef> = Array(ERA_COUNT) { e -> buildEra(e) }
    /** Warianty leżą za bazowymi jednostkami, bez zmiany dawnych identyfikatorów. */
    private val variantUnits: Array<Array<Array<UnitDef>>> = Array(ERA_COUNT) { era ->
        Array(specs[era].variants.size) { index ->
            val v = specs[era].variants[index]
            val offset = ERA_COUNT * Role.entries.size +
                (0 until era).sumOf { specs[it].variants.size } * Role.entries.size +
                index * Role.entries.size
            Array(Role.entries.size) { role ->
                val base = eras[era].units[role]
                UnitDef(offset + role, era, base.role, v.unitNames[role],
                    (base.cost * v.costMul[role]).toInt(), base.hp * v.hpMul[role],
                    base.dmg * v.dmgMul[role], base.range, base.cooldown,
                    base.speed * v.speedMul[role], base.trainTime, base.size, base.splash,
                    when (base.role) { Role.HEAVY -> v.heavyProj; Role.ELITE -> v.eliteProj; else -> base.projectile },
                    when (base.role) { Role.HEAVY -> v.heavyWeapon; Role.ELITE -> v.eliteWeapon; else -> base.weapon },
                    when (base.role) { Role.HEAVY -> v.heavyBody; Role.ELITE -> v.eliteBody; else -> base.body },
                    base.siegeMul, index, v.art)
            }
        }
    }
    val allUnits: Array<UnitDef> = buildList {
        for (e in eras) addAll(e.units)
        for (e in variantUnits) for (v in e) addAll(v)
    }.toTypedArray()

    private val variantTurrets: Array<Array<TurretDef>> = Array(ERA_COUNT) { era ->
        Array(specs[era].variants.size) { index ->
            val v = specs[era].variants[index]
            val base = eras[era].turret
            TurretDef(era, v.turretName, base.cost, base.dmg * v.turretDmgMul,
                base.range, base.cooldown * v.turretCooldownMul,
                base.splash, v.turretProj, index, v.art)
        }
    }
    private val baseBarrage = Array(ERA_COUNT) { BarrageDef(specs[it].barrageProj, 16, 60f) }
    private val variantBarrage = Array(ERA_COUNT) { era ->
        Array(specs[era].variants.size) { v ->
            val spec = specs[era].variants[v]
            BarrageDef(spec.barrageProj, spec.barrageCount, spec.barrageSplash)
        }
    }

    fun unit(era: Int, role: Role, variant: Int = -1): UnitDef =
        if (variant < 0) eras[era].units[role.ordinal] else variantUnits[era][variant][role.ordinal]
    fun turret(era: Int, variant: Int = -1): TurretDef =
        if (variant < 0) eras[era].turret else variantTurrets[era][variant]
    fun barrage(era: Int, variant: Int = -1): BarrageDef =
        if (variant < 0) baseBarrage[era] else variantBarrage[era][variant]
    fun eraOf(art: EraArt): Int = specs.indexOfFirst { it.art == art }
    fun palette(era: Int, variant: Int = -1): IntArray =
        if (variant < 0) specs[era].palette
        else specs[era].variants[variant].palette ?: specs[era].palette
    fun terrain(era: Int, variant: Int = -1): Terrain =
        if (variant < 0) specs[era].terrain
        else specs[era].variants[variant].terrain ?: specs[era].terrain
    fun sideArt(era: Int, variant: Int): EraArt =
        if (variant < 0) specs[era].art else specs[era].variants[variant].art

    /** Koszt zależy od epoki, roli i kupowanego stopnia. */
    fun unitUpgradeCost(era: Int, role: Role, nextTier: Int): Int {
        require(nextTier in 2..MAX_UNIT_TIER)
        val step = if (nextTier == 2) 1f else 2.1f
        return (upgradeBase[role.ordinal] * costScale(era) * step).toInt()
    }

    /** +20% bazowych HP i obrażeń za każdy kupiony stopień. */
    fun tierStatMul(tier: Int): Float = 1f + 0.2f * (tier - 1)

    /** Trzeci stopień ma cechę specjalną zależną od roli. */
    fun unitRange(def: UnitDef, tier: Int): Float =
        if (tier >= 3 && def.role == Role.RANGED) def.range * 1.1f else def.range

    fun unitSplash(def: UnitDef, tier: Int): Float =
        if (tier >= 3 && def.role == Role.ELITE) def.splash + 25f else def.splash

    fun unitSpeed(def: UnitDef, tier: Int): Float =
        if (tier >= 3 && def.role == Role.MELEE) def.speed * 1.1f else def.speed

    fun unitTrainRate(def: UnitDef, tier: Int): Float =
        if (tier >= 3 && def.role == Role.HEAVY) 1.15f else 1f

    private fun buildEra(e: Int): EraDef {
        val spec = specs[e]
        val s = statScale(e)
        val c = costScale(e)
        val sp = 1f + (e * 7f / (ERA_COUNT - 1)) * 0.035f
        val n = spec.unitNames
        val w = spec.weapons
        val units = arrayOf(
            UnitDef(e * 4, e, Role.MELEE, n[0], (15 * c).toInt(), 72 * s, 15 * s, 30f, 1f, 64 * sp, 1.2f, 1f, 0f, Proj.NONE, w[0], Body.HUMAN, 1f),
            UnitDef(e * 4 + 1, e, Role.RANGED, n[1], (26 * c).toInt(), 46 * s, 12.5f * s, 250f + e * 10.5f, 1.3f, 60 * sp, 1.6f, 0.95f, 0f, spec.rangedProj, w[1], Body.HUMAN, 1f),
            UnitDef(e * 4 + 2, e, Role.HEAVY, n[2], (62 * c).toInt(), 265 * s, 30 * s, if (spec.heavyProj == Proj.NONE) 36f else 160f, 1.4f, 50 * sp, 3f, 1.3f, 0f, spec.heavyProj, w[2], spec.heavyBody, 1f),
            UnitDef(e * 4 + 3, e, Role.ELITE, n[3], (165 * c).toInt(), 540 * s, 58 * s, if (spec.eliteProj == Proj.NONE) 44f else 340f, 2.1f, 40 * sp, 6f, 1.75f, if (spec.eliteProj == Proj.NONE) 40f else 85f, spec.eliteProj, w[3], spec.eliteBody, 1.6f),
        )
        val turret = TurretDef(e, spec.turretName, (110 * c).toInt(),
            20 * s * (if (spec.fastTurret) 0.4f else 1f), 540f + e * 13.125f,
            if (spec.fastTurret) 0.45f else 1.25f,
            if (spec.turretProj == Proj.SHELL || spec.turretProj == Proj.BALL || spec.turretProj == Proj.ROCKET) 60f else 0f,
            spec.turretProj)
        return EraDef(e, spec.nameKey, spec.evolveXp, units, turret, spec.abilityKey)
    }

    /** Bazowe HP bazy w danej epoce, przed bonusami (StrictMath: identycznie na każdym urządzeniu). */
    fun baseHp(era: Int): Float = 1600f * StrictMath.pow(BASE_GROWTH.toDouble(), era.toDouble()).toFloat()

    /** Pasywny przychód złota na sekundę w danej epoce. */
    fun income(era: Int): Float = 2.6f * StrictMath.pow(INCOME_GROWTH.toDouble(), era.toDouble()).toFloat()

    /** Obrażenia jednego pocisku zdolności specjalnej. */
    fun abilityDmg(era: Int): Float = 46f * statScale(era)
}
