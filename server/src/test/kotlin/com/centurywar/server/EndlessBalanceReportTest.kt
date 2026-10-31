package com.centurywar.server

import com.centurywar.game.Ai
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.Mode
import org.junit.jupiter.api.Test

/** Pomiar identycznych seedów bazowego i nowego wariantu gry. */
class EndlessBalanceReportTest {
    @Test fun tenSeeds() {
        var sum = 0
        for (seed in 1001L..1010L) {
            val b = Battle(BattleConfig(Mode.ENDLESS, seed, 1f))
            b.playerAi = Ai(b, 0)
            var n = 0
            while (!b.over && n < 60_000) {
                b.step()
                b.fx.count = 0
                n++
            }
            println("ENDLESS_SEED=$seed WAVE=${b.wave} STEPS=$n")
            sum += b.wave
        }
        println("ENDLESS_MEAN=${sum / 10.0}")
    }
}
