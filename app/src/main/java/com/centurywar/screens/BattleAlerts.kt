package com.centurywar.screens

import com.centurywar.game.Battle

/**
 * R10-03: wykrywa ważne zdarzenia bitwy dla sygnałów dźwiękowych i znaku „baza atakowana”.
 * Czysty stan interfejsu: nie wchodzi do zrzutu ani powtórki, liczy w czasie symulacji
 * (tempo ×3/×5 nie mnoży sygnałów) i nie alokuje. Komunikaty TalkBack (GameAccessibility)
 * mają osobne liczniki.
 */
internal class BattleAlerts {
    private var ready = false
    private var halfArmed = true
    private var lowArmed = true
    private var enemyEra = 0
    private val lastCd = FloatArray(2)
    private val used = BooleanArray(2)
    private var lastReadyAt = NEVER
    private var siegeAnnounced = false
    private var lastHp = 0f
    private var lastAt = 0f
    /** Obrażenia bazy gracza w przesuwanym oknie ok. 1 s (zanik liniowy). */
    private var recentDamage = 0f

    /** Czas symulacji ostatniego trafienia bazy gracza (≥ 2,5% maks. HP w 1 s). */
    var baseHitAt = NEVER

    /** Zwraca maskę bitów zdarzeń tej klatki; bez alokacji. */
    fun step(b: Battle, nowSim: Float): Int {
        val p = b.player
        val e = b.enemy
        if (!ready) {
            // Start bitwy albo wznowienie ze zrzutu: bieżący stan nie jest zdarzeniem.
            ready = true
            val band = baseBand(p.baseHp, p.baseMaxHp)
            halfArmed = band == 0
            lowArmed = band < 2
            enemyEra = e.era
            lastCd[0] = p.abilityCd[0]
            lastCd[1] = p.abilityCd[1]
            used.fill(false)
            siegeAnnounced = b.endless || nowSim >= b.siegeStart
            lastHp = p.baseHp
            lastAt = nowSim
            return 0
        }
        if (b.over) return 0
        var mask = 0

        // Trafienia bazy: tylko spadek HP, naprawa i awans epoki się nie liczą.
        val dt = (nowSim - lastAt).coerceAtLeast(0f)
        lastAt = nowSim
        recentDamage = (recentDamage * (1f - dt).coerceAtLeast(0f))
        val drop = lastHp - p.baseHp
        if (drop > 0f) recentDamage += drop
        lastHp = p.baseHp
        if (p.baseMaxHp > 0f && drop > 0f && recentDamage >= p.baseMaxHp * HIT_SHARE) baseHitAt = nowSim

        // Progi bazy: raz w dół; ponownie dopiero po powrocie powyżej progu + 10%.
        if (p.baseMaxHp > 0f) {
            val ratio = p.baseHp / p.baseMaxHp
            if (halfArmed && ratio <= 0.5f) { halfArmed = false; mask = mask or BASE_HALF }
            else if (!halfArmed && ratio > 0.5f + REARM) halfArmed = true
            if (lowArmed && ratio <= 0.25f) { lowArmed = false; mask = mask or BASE_LOW }
            else if (!lowArmed && ratio > 0.25f + REARM) lowArmed = true
        }

        if (e.era > enemyEra) mask = mask or ENEMY_ERA
        enemyEra = e.era

        // Gotowość po odnowieniu: tylko po faktycznym użyciu (wzrost czasu odnowienia).
        var readyNow = false
        for (i in 0..1) {
            val cd = p.abilityCd[i]
            if (cd > lastCd[i] + 0.01f) used[i] = true
            else if (used[i] && b.abilityReady(p, i)) { used[i] = false; readyNow = true }
            lastCd[i] = cd
        }
        if (readyNow && nowSim - lastReadyAt >= READY_GAP) {
            lastReadyAt = nowSim
            mask = mask or ABILITY_READY
        }

        if (!siegeAnnounced && !b.endless && nowSim >= b.siegeStart) {
            siegeAnnounced = true
            mask = mask or SIEGE
        }
        return mask
    }

    companion object {
        const val BASE_HALF = 1
        const val BASE_LOW = 2
        const val ENEMY_ERA = 4
        const val ABILITY_READY = 8
        const val SIEGE = 16
        /** Znak „baza atakowana” gaśnie po tylu sekundach symulacji bez trafienia. */
        const val HIT_SHOW_SECONDS = 2.5f
        const val HIT_SHARE = 0.025f
        const val READY_GAP = 8f
        const val REARM = 0.1f
        const val NEVER = -1e9f

        /** Pasmo HP bazy jak w komunikatach TalkBack: 0, 1 = ≤ 50%, 2 = ≤ 25%. */
        fun baseBand(hp: Float, max: Float): Int = when {
            max <= 0f -> 0
            hp <= max * 0.25f -> 2
            hp <= max * 0.5f -> 1
            else -> 0
        }
    }
}
