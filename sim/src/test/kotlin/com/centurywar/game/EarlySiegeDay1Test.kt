package com.centurywar.game

import org.junit.Assert.assertEquals
import org.junit.Test

/** Dzień 1: wcześniejsza i szybsza presja oblężnicza tylko w etapach 2–7 kampanii. */
class EarlySiegeDay1Test {
    private fun campaign(index: Int, tutorial: Boolean = false) =
        Battle(Campaign.config(Campaign.stages[index], Bonuses(), 7L, tutorial = tutorial))

    @Test
    fun stagesTwoToSevenStartPressureAfterFourMinutes() {
        for (index in 1..6) {
            for (difficulty in CampaignDifficulty.entries) {
                val b = Battle(Campaign.config(Campaign.stages[index], Bonuses(), 7L, difficulty = difficulty))
                assertEquals("etap ${index + 1}", 240f, b.siegeStart)
                assertEquals("etap ${index + 1}", 0.8f, b.siegeRamp)
            }
            val b = campaign(index)
            b.time = 239f
            assertEquals(1f, b.siegePressure())
            b.time = 300f
            assertEquals(1.8f, b.siegePressure(), 1e-4f)
        }
    }

    @Test
    fun otherStagesAndModesKeepEightMinutes() {
        val others = listOf(campaign(0), campaign(0, tutorial = true), campaign(18), campaign(35)) +
            listOf(Mode.SKIRMISH, Mode.ENDLESS, Mode.DAILY).map { Battle(BattleConfig(it, 11L, 1f, stageIndex = 3)) } +
            Battle(Daily.config(Daily.RELEASE_DAY, Bonuses()))
        for (b in others) {
            assertEquals(Battle.SIEGE_START, b.siegeStart)
            assertEquals(Battle.SIEGE_RAMP, b.siegeRamp)
        }
        val skirmish = Battle(BattleConfig(Mode.SKIRMISH, 11L, 1f))
        skirmish.time = 300f
        assertEquals(1f, skirmish.siegePressure())
        skirmish.time = Battle.SIEGE_START + 60f
        assertEquals(1.2f, skirmish.siegePressure(), 1e-4f)
    }
}
