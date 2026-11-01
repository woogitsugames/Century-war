package com.centurywar.screens

import com.centurywar.game.Bonuses
import com.centurywar.game.Defs
import com.centurywar.game.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodexCompareModelTest {
    @Test
    fun matrixAlwaysUsesSimulationMultipliers() {
        for (attacker in Role.entries) for (defender in Role.entries) {
            assertEquals(Defs.counterMultiplier(attacker, defender),
                CodexCompareModel.counter(attacker, defender), 0f)
        }
    }

    @Test
    fun advantagesAccountForCostAndTies() {
        assertEquals(1, CodexCompareModel.advantage(80f, 120f, CodexCompareModel.COST))
        assertEquals(-1, CodexCompareModel.advantage(120f, 80f, CodexCompareModel.COST))
        assertEquals(1, CodexCompareModel.advantage(120f, 80f, CodexCompareModel.HP))
        assertEquals(-1, CodexCompareModel.advantage(80f, 120f, CodexCompareModel.DPS))
        assertEquals(0, CodexCompareModel.advantage(80f, 80f, CodexCompareModel.DPS))
    }

    @Test
    fun comparedValuesMatchDefsAndResearch() {
        val noResearch = Bonuses()
        val withResearch = Bonuses(unitHpMul = 1.2f, unitDmgMul = 1.3f)
        for (era in Defs.eras.indices) for (role in Role.entries) {
            val unit = Defs.unit(era, role)
            assertEquals(unit.hp, CodexCompareModel.value(unit, noResearch, CodexCompareModel.HP), 0.01f)
            assertEquals(unit.dmg * 1.3f, CodexCompareModel.value(unit, withResearch, CodexCompareModel.DAMAGE), 0.01f)
            assertEquals(unit.hp * 1.2f, CodexCompareModel.value(unit, withResearch, CodexCompareModel.HP), 0.01f)
            assertEquals(unit.dmg * 1.3f / unit.cooldown,
                CodexCompareModel.eraDps(era, role, withResearch), 0.01f)
            assertEquals(unit.cost.toFloat(),
                CodexCompareModel.value(unit, withResearch, CodexCompareModel.COST), 0f)
        }
    }

    @Test
    fun growthChartCoversAllErasWithoutHardcodedStats() {
        for (role in Role.entries) {
            var previous = 0f
            for (era in 0 until Defs.ERA_COUNT) {
                val dps = CodexCompareModel.eraDps(era, role, Bonuses())
                assertTrue("Epoka $era, rola $role", dps > previous)
                previous = dps
            }
        }
    }
}
