package com.centurywar.screens

import com.centurywar.game.Bonuses
import com.centurywar.game.Defs
import com.centurywar.game.Role
import com.centurywar.game.UnitDef

/** Dane porównania z definicji bitwy, bez osobnego balansu w interfejsie. */
internal object CodexCompareModel {
    const val HP = 0
    const val DAMAGE = 1
    const val RANGE = 2
    const val SPEED = 3
    const val COST = 4
    const val DPS = 5
    const val STATS = 6

    val statKeys = arrayOf("cx_hp", "cx_dmg", "cx_range", "cx_speed", "cx_cost", "cx_dps")
    val roleKeys = arrayOf("cx_melee", "cx_ranged", "cx_heavy", "cx_elite")

    /** Bazowe parametry jednostki, z opcjonalnymi badaniami gracza. */
    fun value(unit: UnitDef, bonuses: Bonuses, field: Int): Float = when (field) {
        HP -> unit.hp * bonuses.unitHpMul
        DAMAGE -> unit.dmg * bonuses.unitDmgMul
        RANGE -> unit.range
        SPEED -> unit.speed
        COST -> unit.cost.toFloat()
        DPS -> unit.dmg * bonuses.unitDmgMul / unit.cooldown
        else -> throw IllegalArgumentException("Nieznana statystyka: $field")
    }

    /** +1: korzystniej, -1: mniej korzystnie, 0: remis; tańszy koszt jest zaletą. */
    fun advantage(ours: Float, theirs: Float, field: Int): Int {
        val sign = ours.compareTo(theirs)
        return if (field == COST) -sign else sign
    }

    /** Wiersz atakuje, kolumna się broni. Nie utrzymujemy osobnej tabeli liczb. */
    fun counter(attacker: Role, defender: Role): Float =
        Defs.counterMultiplier(attacker, defender)

    /** Siła ofensywna do wykresu: rzeczywisty bazowy DPS roli w epoce. */
    fun eraDps(era: Int, role: Role, bonuses: Bonuses): Float =
        value(Defs.unit(era, role), bonuses, DPS)
}
