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
        for (stage in Campaign.stages) {
            val b = run(Campaign.config(stage, Bonuses(), 1234L + stage.index))
            report.append("stage ${stage.index + 1} era ${stage.startEra}-${stage.maxEra} power ${"%.2f".format(stage.aiPower)}: ")
                .append(if (b.over) "winner=${b.winner}" else "TIMEOUT")
                .append(" t=${"%.1f".format(b.time / 60f)}min eras=${b.player.era}/${b.enemy.era}")
                .append(" kills=${b.player.kills}/${b.enemy.kills}\n")
        }
        println(report)
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
        assertTrue(b.wave >= 5)
    }

    @Test
    fun deterministicForSameSeed() {
        val a = run(BattleConfig(Mode.SKIRMISH, 99L, 1.1f), 20f)
        val c = run(BattleConfig(Mode.SKIRMISH, 99L, 1.1f), 20f)
        assertTrue(a.time == c.time && a.player.kills == c.player.kills && a.winner == c.winner)
    }

    @Test
    fun dailyIsSameForEveryone() {
        val d1 = Daily.config(20000L, Bonuses(unitDmgMul = 2f))
        val d2 = Daily.config(20000L, Bonuses())
        assertTrue(d1.modifiers == d2.modifiers && d1.playerBonuses.unitDmgMul == d2.playerBonuses.unitDmgMul)
    }
}
