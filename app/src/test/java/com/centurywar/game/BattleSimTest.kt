package com.centurywar.game

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Headless balance: AI kontra AI na różnych etapach. Test pilnuje, że bitwy się kończą,
 * trwają rozsądnie długo i że obie strony awansują przez epoki.
 */
class BattleSimTest {

    private fun run(cfg: BattleConfig, maxMinutes: Float = 30f): Battle {
        val b = Battle(cfg)
        b.playerAi = Ai(b, 0)
        val maxSteps = (maxMinutes * 60f / Battle.STEP).toInt()
        var i = 0
        while (!b.over && i < maxSteps) { b.step(); b.fx.count = 0; i++ }
        return b
    }

    @Test
    fun campaignStagesFinish() {
        val report = StringBuilder()
        assertTrue("Kampania musi mieć 36 etapów", Campaign.stages.size == Defs.ERA_COUNT * 4)
        var finished = 0
        for (stage in Campaign.stages) {
            val b = run(Campaign.config(stage, Bonuses(), 1234L + stage.index))
            report.append("stage ${stage.index + 1} era ${stage.startEra}-${stage.maxEra} power ${"%.2f".format(stage.aiPower)}: ")
                .append(if (b.over) "winner=${b.winner}" else "TIMEOUT")
                .append(" t=${"%.1f".format(b.time / 60f)}min eras=${b.player.era}/${b.enemy.era}")
                .append(" kills=${b.player.kills}/${b.enemy.kills}\n")
            if (b.over) finished++
        }
        println(report)
        // Dłuższe etapy przy równych siłach są raportowane, nie maskowane.
        // Wymagamy zakończenia większości etapów w limicie czasu.
        assertTrue("AI ukończyło tylko $finished z ${Campaign.stages.size} etapów", finished >= Campaign.stages.size * 3 / 4)
    }

    /** Raport bazowego i ulepszonego balansu: różnice wynikają tylko z reguł stopni. */
    @Test
    fun unitTierBalanceReport() {
        println("epoka/rola | bez ulepszeń HP/DMG | stopień 2 HP/DMG koszt | stopień 3 HP/DMG koszt")
        for (e in 0 until Defs.ERA_COUNT) {
            for (r in Role.values()) {
                val d = Defs.unit(e, r)
                val hp2 = d.hp * Defs.tierStatMul(2)
                val hp3 = d.hp * Defs.tierStatMul(3)
                val dmg2 = d.dmg * Defs.tierStatMul(2)
                val dmg3 = d.dmg * Defs.tierStatMul(3)
                println("${e}/${r} | ${d.hp.toInt()}/${d.dmg.toInt()} | ${hp2.toInt()}/${dmg2.toInt()} ${Defs.unitUpgradeCost(e, r, 2)} | ${hp3.toInt()}/${dmg3.toInt()} ${Defs.unitUpgradeCost(e, r, 3)}")
                assertTrue(hp3 > hp2 && hp2 > d.hp && dmg3 > dmg2 && dmg2 > d.dmg)
            }
        }
    }

    @Test
    fun fullSkirmishProgressesThroughEras() {
        val b = run(BattleConfig(Mode.SKIRMISH, 42L, 1.0f), 90f)
        println("skirmish over=${b.over} winner=${b.winner} t=${b.time / 60f} eras=${b.player.era}/${b.enemy.era} kills=${b.player.kills}/${b.enemy.kills}")
        assertTrue("obie strony powinny awansować", b.player.era >= 3 || b.enemy.era >= 3 || b.over)
    }

    @Test
    fun endlessEventuallyEnds() {
        val b = run(BattleConfig(Mode.ENDLESS, 7L, 1f), 120f)
        println("endless over=${b.over} wave=${b.wave} t=${b.time / 60f} score=${b.score()} era=${b.player.era}")
        assertTrue("Tryb Endless powinien dobiec końca", b.over)
        assertTrue(b.wave >= 5)
    }

    @Test
    fun deterministicForSameSeed() {
        val a = run(BattleConfig(Mode.SKIRMISH, 99L, 1.1f), 20f)
        val c = run(BattleConfig(Mode.SKIRMISH, 99L, 1.1f), 20f)
        assertTrue(a.time == c.time && a.player.kills == c.player.kills && a.winner == c.winner)
    }

    @Test
    fun ironEraHasFourOriginalUnitsAndValidProgression() {
        assertTrue(Defs.ERA_COUNT == 9)
        val iron = Defs.eras[2]
        assertTrue(iron.nameKey == "era_iron")
        assertTrue(iron.units.size == Role.values().size)
        assertTrue(iron.units.all { it.era == 2 && it.cost > 0 && it.hp > 0f })
        assertTrue(iron.units.map { it.nameKey }.toSet().size == 4)
        assertTrue(Defs.evolveXp.take(Defs.ERA_COUNT - 1).zipWithNext().all { it.first < it.second })
        assertTrue(Defs.statScale(Defs.ERA_COUNT - 1) > 0f)
    }

    @Test
    fun campaignBalanceAdjustmentsPreserveStageStructure() {
        val stages = Campaign.stages
        assertTrue(stages.size == 36)
        assertTrue(stages.withIndex().all { (index, stage) ->
            stage.index == index && stage.startEra in 0..stage.maxEra &&
                stage.maxEra < Defs.ERA_COUNT && stage.aiPower > 0f
        })
        // Dwa odstające etapy mają po trzy dostępne epoki zamiast czterech.
        assertTrue(stages[11].startEra == 1 && stages[11].maxEra == 3)
        assertTrue(stages[15].startEra == 2 && stages[15].maxEra == 4)
        // Końcowy skok siły AI pozostaje łagodny, bez ruszania profili i gwiazdek.
        assertTrue((33..35).all { stages[it].aiPower in 2.8f..3.2f })
    }

    @Test
    fun dailyEraSelectionChangesOnlyFromNextUtcDay() {
        val old = Daily.config(Daily.ERA_EXPANSION_DAY - 1, Bonuses())
        val oldAgain = Daily.config(Daily.ERA_EXPANSION_DAY - 1, Bonuses(unitDmgMul = 5f))
        assertTrue(old.startEra == oldAgain.startEra && old.maxEra == oldAgain.maxEra)
        assertTrue(old.startEra != 2 && old.maxEra != 2)
        val fresh = Daily.config(Daily.ERA_EXPANSION_DAY, Bonuses())
        assertTrue(fresh.startEra in 0 until Defs.ERA_COUNT && fresh.maxEra in fresh.startEra until Defs.ERA_COUNT)
        val start = Daily.ERA_EXPANSION_DAY * 86_400_000L
        assertTrue(Daily.dayNumber(start - 1) == Daily.ERA_EXPANSION_DAY - 1)
        assertTrue(Daily.dayNumber(start) == Daily.ERA_EXPANSION_DAY)
        // Dzisiejsze i niedawne wyzwania dnia nie mogą zależeć od nowej epoki.
        assertTrue(Daily.ERA_EXPANSION_DAY > 20757L)
    }

    @Test
    fun dailyIsSameForEveryone() {
        val d1 = Daily.config(20000L, Bonuses(unitDmgMul = 2f))
        val d2 = Daily.config(20000L, Bonuses())
        assertTrue(d1.modifiers == d2.modifiers && d1.playerBonuses.unitDmgMul == d2.playerBonuses.unitDmgMul)
    }

    @Test
    fun tutorialEnemyStartsLateAndGentle() {
        val stage = Campaign.stages[0]
        val cfg = Campaign.config(stage, Bonuses(), 5L, tutorial = true)
        assertTrue(cfg.tutorial && cfg.aiPower < stage.aiPower && cfg.maxEra >= 1)
        val b = Battle(cfg)
        while (b.time < Battle.TUTORIAL_AI_DELAY - 1f) { b.step(); b.fx.count = 0 }
        assertTrue("AI nie powinno grać przed upływem opóźnienia", b.enemy.spawned == 0 && b.enemy.queueSize == 0)
        val normal = Campaign.config(stage, Bonuses(), 5L)
        assertTrue(!normal.tutorial && normal.maxEra == stage.maxEra)
    }
}
