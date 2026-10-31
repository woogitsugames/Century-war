package com.centurywar.game

import kotlin.math.pow

/** Definicje badań wspólne dla profilu i weryfikacji wyniku na serwerze. */
class ResearchDef(val key: String, val max: Int, val baseCost: Int, val growth: Float) {
    fun cost(level: Int): Int = (baseCost * growth.pow(level)).toInt()
}

object Research {
    /** Ustalona kolejność indeksów w protokole zgłoszenia rankingu. */
    val defs = listOf(
        ResearchDef("r_elite", 1, 120, 1f),
        ResearchDef("r_rally", 1, 90, 1f),
        ResearchDef("r_income", 12, 40, 1.38f),
        ResearchDef("r_unitDmg", 15, 45, 1.33f),
        ResearchDef("r_unitHp", 15, 45, 1.33f),
        ResearchDef("r_startGold", 10, 30, 1.35f),
        ResearchDef("r_baseHp", 10, 40, 1.36f),
        ResearchDef("r_turretDmg", 10, 40, 1.36f),
        ResearchDef("r_train", 10, 50, 1.38f),
        ResearchDef("r_xp", 10, 50, 1.4f),
        ResearchDef("r_cap", 6, 150, 1.6f),
        ResearchDef("r_cd", 8, 70, 1.42f),
        ResearchDef("r_slots", 2, 260, 2.5f),
    )

    fun valid(levels: List<Int>): Boolean =
        levels.size == defs.size && levels.indices.all { levels[it] in 0..defs[it].max }

    fun bonuses(levels: IntArray): Bonuses {
        require(levels.size == defs.size && levels.indices.all { levels[it] in 0..defs[it].max }) {
            "invalid_research"
        }
        return Bonuses(
            startGold = levels[5] * 20f,
            incomeMul = 1f + levels[2] * 0.06f,
            unitHpMul = 1f + levels[4] * 0.04f,
            unitDmgMul = 1f + levels[3] * 0.04f,
            baseHpMul = 1f + levels[6] * 0.08f,
            turretDmgMul = 1f + levels[7] * 0.06f,
            trainMul = 1f + levels[8] * 0.05f,
            xpMul = 1f + levels[9] * 0.05f,
            popCap = 12 + levels[10],
            abilityCdMul = 1f - levels[11] * 0.05f,
            turretSlots = 2 + levels[12],
            eliteUnlocked = levels[0] > 0,
            rallyUnlocked = levels[1] > 0,
        )
    }
}
