package com.centurywar.game

/** Strategie dowódcy. Różnią się wyłącznie decyzjami, bez bonusów do statystyk. */
enum class AiPersonality(
    val meleeWeight: Int,
    val rangedWeight: Int,
    val heavyWeight: Int,
    val eliteWeight: Int,
) {
    ASSAULT(48, 18, 24, 10),
    ENGINEER(20, 30, 38, 12),
    ECONOMIST(20, 18, 23, 39),
    SIEGER(17, 16, 20, 47),
    BALANCED(30, 28, 28, 14),
}
