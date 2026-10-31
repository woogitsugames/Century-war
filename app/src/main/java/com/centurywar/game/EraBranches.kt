package com.centurywar.game

/**
 * Odmiana epoki: osobny zestaw jednostek, wieża i ostrzał.
 * Indeks wariantu jest lokalny dla epoki (0 lub 1), a identyfikatory jednostek globalne.
 */
class EraVariant(
    val era: Int,
    val index: Int,
    val nameKey: String,
    val styleKey: String,
    val units: Array<UnitDef>,
    val turret: TurretDef,
    val abilityKey: String,
    val abilityProjectile: Int,
    val abilityCount: Int,
    val abilitySplash: Float,
    val abilityChain: Float,
    val abilityDmgMul: Float,
)

/** Dane i reguły rozgałęzień; brak wariantu ma wartość -1. */
object EraBranches {
    const val NONE = -1
    const val FIRST = 0
    const val SECOND = 1

    fun available(era: Int): Boolean = era == 4 || era == 6

    fun forced(era: Int, mods: Set<Modifier>): Int = when (era) {
        4 -> when {
            Modifier.FORCE_DIESEL in mods -> FIRST
            Modifier.FORCE_TESLA in mods -> SECOND
            else -> NONE
        }
        6 -> when {
            Modifier.FORCE_CYBER in mods -> FIRST
            Modifier.FORCE_BIO in mods -> SECOND
            else -> NONE
        }
        else -> NONE
    }

    /** Warianty wyliczane z podstawowych statystyk, z zachowaniem stałych ID. */
    fun build(era: Int, base: Array<UnitDef>, turret: TurretDef): Array<EraVariant> {
        if (!available(era)) return emptyArray()
        return Array(2) { v ->
            val isSteam = era == 4
            val names = if (isSteam) {
                if (v == FIRST) arrayOf("u_diesel_0", "u_diesel_1", "u_diesel_2", "u_diesel_3")
                else arrayOf("u_tesla_0", "u_tesla_1", "u_tesla_2", "u_tesla_3")
            } else {
                if (v == FIRST) arrayOf("u_cyber_0", "u_cyber_1", "u_cyber_2", "u_cyber_3")
                else arrayOf("u_bio_0", "u_bio_1", "u_bio_2", "u_bio_3")
            }
            val hp = when {
                isSteam && v == FIRST -> 1.52f
                isSteam -> 0.73f
                v == FIRST -> 0.78f
                else -> 1.22f
            }
            val dmg = when {
                isSteam && v == FIRST -> 0.94f
                isSteam -> 0.90f
                v == FIRST -> 0.81f
                else -> 1.12f
            }
            val cost = when {
                isSteam && v == FIRST -> 1.25f
                isSteam -> 0.94f
                v == FIRST -> 0.68f
                else -> 1.53f
            }
            val speed = when {
                isSteam && v == FIRST -> 0.70f
                isSteam -> 1.12f
                v == FIRST -> 1.23f
                else -> 0.88f
            }
            val bodies = when {
                isSteam && v == FIRST -> intArrayOf(Body.HUMAN, Body.CANNON, Body.VEHICLE, Body.VEHICLE)
                isSteam -> intArrayOf(Body.HUMAN, Body.HUMAN, Body.MECH, Body.HOVER)
                v == FIRST -> intArrayOf(Body.HOVER, Body.HOVER, Body.HOVER, Body.MECH)
                else -> intArrayOf(Body.HUMAN, Body.MECH, Body.MECH, Body.MECH)
            }
            val weapons = when {
                isSteam && v == FIRST -> intArrayOf(Weapon.RIFLE, Weapon.RIFLE, Weapon.MG, Weapon.MG)
                isSteam -> intArrayOf(Weapon.BLASTER, Weapon.BLASTER, Weapon.RAIL, Weapon.BLASTER)
                v == FIRST -> intArrayOf(Weapon.BLASTER, Weapon.RAIL, Weapon.LAUNCHER, Weapon.RAIL)
                else -> intArrayOf(Weapon.PIKE, Weapon.BLASTER, Weapon.LAUNCHER, Weapon.BLASTER)
            }
            val projectiles = when {
                isSteam && v == FIRST -> intArrayOf(Proj.NONE, Proj.SHELL, Proj.BULLET, Proj.SHELL)
                isSteam -> intArrayOf(Proj.NONE, Proj.BEAM, Proj.PLASMA, Proj.BEAM)
                v == FIRST -> intArrayOf(Proj.NONE, Proj.PLASMA, Proj.ROCKET, Proj.PLASMA)
                else -> intArrayOf(Proj.NONE, Proj.PLASMA, Proj.ROCKET, Proj.PLASMA)
            }
            val units = Array(4) { role ->
                val u = base[role]
                UnitDef(
                    id = Defs.ERA_COUNT * 4 + (if (isSteam) 0 else 8) + v * 4 + role,
                    era = era, role = u.role, nameKey = names[role],
                    cost = (u.cost * cost).toInt().coerceAtLeast(1),
                    hp = u.hp * hp, dmg = u.dmg * dmg,
                    range = if (role == 0) u.range else u.range,
                    cooldown = u.cooldown,
                    speed = u.speed * speed,
                    trainTime = u.trainTime * if (!isSteam && v == FIRST) 0.85f else if (!isSteam) 1.18f else 1f,
                    size = u.size, splash = if (isSteam && v == SECOND && role == 2) 54f else u.splash,
                    projectile = projectiles[role], weapon = weapons[role], body = bodies[role],
                    siegeMul = u.siegeMul,
                    variant = v,
                    chain = if (isSteam && v == SECOND && (role == 1 || role == 3)) 0.42f else 0f,
                    regen = if (!isSteam && v == SECOND) 0.016f + role * 0.002f else 0f,
                )
            }
            val turretDef = TurretDef(
                era, if (isSteam) if (v == FIRST) "t_diesel" else "t_tesla"
                else if (v == FIRST) "t_cyber" else "t_bio",
                (turret.cost * cost).toInt(),
                turret.dmg * dmg * if (isSteam && v == SECOND) 0.83f else 1f,
                turret.range,
                if (isSteam && v == FIRST) 1.12f else if (isSteam) 0.75f else turret.cooldown,
                if (isSteam && v == FIRST) 80f else if (!isSteam && v == FIRST) 48f else if (v == SECOND && !isSteam) 65f else 0f,
                if (isSteam) if (v == FIRST) Proj.SHELL else Proj.BEAM else if (v == FIRST) Proj.ROCKET else Proj.PLASMA,
                variant = v, chain = if (isSteam && v == SECOND) 0.38f else 0f,
            )
            val name = if (isSteam) if (v == FIRST) "branch_diesel" else "branch_tesla"
                else if (v == FIRST) "branch_cyber" else "branch_bio"
            EraVariant(era, v, name, name + "_d", units, turretDef,
                "ab_" + name.removePrefix("branch_"),
                if (isSteam) if (v == FIRST) Proj.SHELL else Proj.BEAM else if (v == FIRST) Proj.ROCKET else Proj.PLASMA,
                if (isSteam) if (v == FIRST) 12 else 18 else if (v == FIRST) 22 else 12,
                if (isSteam && v == SECOND) 50f else if (isSteam) 82f else if (v == FIRST) 48f else 74f,
                if (isSteam && v == SECOND) 0.33f else 0f,
                if (isSteam && v == SECOND) 0.78f else if (!isSteam && v == FIRST) 0.72f else 1f,
            )
        }
    }
}
