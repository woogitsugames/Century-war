package com.centurywar.game

import org.junit.Assert.assertEquals
import org.junit.Test

/** R11-02: łagodniej przyspieszona presja oblężnicza w etapach 8–12 kampanii. */
class SecondHourSiegeTest {
    private fun expected(stage: Int): Pair<Float, Float> = when (stage) {
        in 2..7 -> 240f to 0.8f
        in 8..12 -> Campaign.SECOND_SIEGE_START to Campaign.SECOND_SIEGE_RAMP
        else -> Battle.SIEGE_START to Battle.SIEGE_RAMP
    }

    @Test
    fun everyStageAndDifficultyFollowsTable() {
        for (index in Campaign.stages.indices) {
            val (start, ramp) = expected(index + 1)
            for (difficulty in CampaignDifficulty.entries) {
                val b = Battle(Campaign.config(Campaign.stages[index], Bonuses(), 7L, difficulty = difficulty))
                assertEquals("etap ${index + 1} $difficulty", start, b.siegeStart)
                assertEquals("etap ${index + 1} $difficulty", ramp, b.siegeRamp)
            }
        }
    }

    @Test
    fun stagesEightToTwelvePressureCurve() {
        for (index in 7..11) {
            val b = Battle(Campaign.config(Campaign.stages[index], Bonuses(), 7L))
            b.time = Campaign.SECOND_SIEGE_START - 1f
            assertEquals(1f, b.siegePressure())
            b.time = Campaign.SECOND_SIEGE_START + 60f
            assertEquals(1f + Campaign.SECOND_SIEGE_RAMP, b.siegePressure(), 1e-4f)
        }
    }

    @Test
    fun tutorialAndOtherModesUnchanged() {
        val tutorial = Battle(Campaign.config(Campaign.stages[8], Bonuses(), 7L, tutorial = true))
        assertEquals(Battle.SIEGE_START, tutorial.siegeStart)
        assertEquals(Battle.SIEGE_RAMP, tutorial.siegeRamp)
        for (mode in listOf(Mode.SKIRMISH, Mode.ENDLESS, Mode.DAILY)) {
            for (index in 7..11) {
                val b = Battle(BattleConfig(mode, 11L, 1f, stageIndex = index))
                assertEquals("$mode $index", Battle.SIEGE_START, b.siegeStart)
                assertEquals("$mode $index", Battle.SIEGE_RAMP, b.siegeRamp)
            }
        }
    }
}
