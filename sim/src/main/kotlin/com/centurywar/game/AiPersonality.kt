package com.centurywar.game

/**
 * Strategie dowódcy. Parametry dotyczą wyłącznie decyzji, nie statystyk ani przychodu.
 * Wagi ról są losowane po uwzględnieniu kontr i stanu armii.
 */
enum class AiPersonality(
    val meleeWeight: Int,
    val rangedWeight: Int,
    val heavyWeight: Int,
    val eliteWeight: Int,
    val turretDelay: Float,
    /** Maksymalna część bieżącego złota przeznaczana na pojedynczy zakup wieży. */
    val turretBudget: Float,
    val evolveDelay: Float,
    /** Udział wymaganego XP, od którego AI ogranicza wydatki przed awansem. */
    val saveForEraBias: Float,
    val saveDuration: Float,
    val armoryBias: Float,
    val barrageDanger: Int,
    val barrageTargets: Int,
    val rallyMin: Int,
    val rallyOnMarch: Boolean,
    /** Stosunek siły własnej do wrogiej, poniżej którego należy się bronić. */
    val holdThreshold: Float,
    val pushArmySize: Int,
    val upgradeReserve: Float,
    val upgradeXpLimit: Float,
    /** Ile kosztu elity trzeba już mieć, żeby rozpocząć jej oszczędzanie. */
    val eliteCommitment: Float,
    val lateEliteBoost: Int,
) {
    // Waga, wieże, awans, zdolności, postawa, ulepszenia i inwestycja w elitę.
    ASSAULT(
        65, 17, 13, 5,
        140f, 0.12f, 16f, 0.97f, 2f, 0.8f,
        5, 5, 3, true, 0.25f, 1,
        4.5f, 0.35f, 1.2f, 10,
    ),
    ENGINEER(
        12, 26, 48, 14,
        8f, 0.78f, 9f, 0.90f, 5f, 2f,
        2, 3, 5, false, 1.20f, 6,
        0.75f, 0.82f, 1.0f, 18,
    ),
    ECONOMIST(
        23, 22, 17, 38,
        185f, 0.07f, 0f, 0.73f, 14f, 5f,
        4, 5, 6, false, 0.80f, 3,
        0.90f, 0.52f, 0.60f, 55,
    ),
    SIEGER(
        10, 20, 35, 35,
        90f, 0.22f, 8f, 0.88f, 8f, 1.8f,
        1, 2, 6, false, 0.80f, 7,
        1.70f, 0.70f, 0.85f, 75,
    ),
    BALANCED(
        30, 28, 28, 14,
        35f, 0.42f, 3f, 0.86f, 6f, 1.3f,
        4, 4, 5, false, 0.60f, 2,
        1.40f, 0.55f, 1.05f, 18,
    ),
}
