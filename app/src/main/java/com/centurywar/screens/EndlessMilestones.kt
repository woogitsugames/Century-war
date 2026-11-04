package com.centurywar.screens

import com.centurywar.game.Weekly

/**
 * R11-03: kamienie milowe w trakcie Endless i Oblężenia tygodnia (progi tygodnia, rekord fali).
 * Czysty stan interfejsu: nie wchodzi do zrzutu ani powtórki, nie zmienia nagród (medale i rekord
 * liczy wyłącznie BattleScreen.settleRewards) i nie alokuje.
 *
 * [bestWaveAtStart] = rekord fali sprzed bitwy (0 = brak rekordu, nic do bicia).
 * [claimedMaskAtStart] = progi tygodnia zaliczone przed bitwą (bit i = próg i); nie są ogłaszane.
 * [weekly] = Oblężenie tygodnia; zwykłe Endless nie ma progów.
 */
internal class EndlessMilestones(
    private val bestWaveAtStart: Int,
    private val claimedMaskAtStart: Int,
    private val weekly: Boolean,
) {
    private var ready = false
    private var lastWave = 0
    private var announced = 0
    private var recordDone = false

    /** Liczniki ogłoszonych zdarzeń (TalkBack porównuje je z własnymi, bez podwójnych komunikatów). */
    var tierEvents = 0
        private set
    var recordEvents = 0
        private set
    /** Fala, na której pobito rekord (0 = jeszcze nie). */
    var recordWave = 0
        private set
    /** Ostatnio ogłoszony próg (0..2) albo -1. */
    var lastTier = -1
        private set

    /** Fale do pobicia rekordu (następna fala po rekordzie = 1); > 0 od fali `rekord − 5`. */
    var wavesToRecord = 0
        private set

    /**
     * Zwraca maskę zdarzeń tej fali ([TIER_0]..[TIER_2], [RECORD]); 0, gdy fala się nie zmieniła.
     * Pierwsze wywołanie (start bitwy albo wznowienie ze zrzutu) traktuje bieżącą falę jako
     * już widzianą: progi i rekord sprzed niej nie są ogłaszane.
     */
    fun step(wave: Int): Int {
        if (!ready) {
            ready = true
            lastWave = wave
            if (weekly) for (i in 0 until Weekly.TIER_COUNT)
                if (wave >= Weekly.threshold(i)) announced = announced or (1 shl i)
            recordDone = bestWaveAtStart <= 0 || wave > bestWaveAtStart
            updateToRecord(wave)
            return 0
        }
        if (wave == lastWave) return 0
        lastWave = wave
        updateToRecord(wave)
        var mask = 0
        if (weekly) for (i in 0 until Weekly.TIER_COUNT) {
            val bit = 1 shl i
            if (wave >= Weekly.threshold(i) && announced and bit == 0) {
                announced = announced or bit
                if (claimedMaskAtStart and bit == 0) {
                    mask = mask or (TIER_0 shl i)
                    tierEvents++
                    lastTier = i
                }
            }
        }
        if (!recordDone && wave > bestWaveAtStart) {
            recordDone = true
            recordEvents++
            recordWave = wave
            mask = mask or RECORD
        }
        return mask
    }

    /** Rekord do pokazania w pauzie: dawny albo bieżąca fala, gdy już go pobito (0 = brak). */
    fun recordShown(wave: Int): Int = if (bestWaveAtStart > wave) bestWaveAtStart else wave

    private fun updateToRecord(wave: Int) {
        wavesToRecord = if (bestWaveAtStart > 0 && wave >= bestWaveAtStart - RECORD_LEAD && wave <= bestWaveAtStart)
            bestWaveAtStart + 1 - wave else 0
    }

    companion object {
        const val TIER_0 = 1
        const val TIER_1 = 2
        const val TIER_2 = 4
        const val RECORD = 8
        /** Dopisek „do rekordu” pojawia się tyle fal przed rekordem. */
        const val RECORD_LEAD = 5
    }
}
