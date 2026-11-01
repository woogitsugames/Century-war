package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.Defs
import com.centurywar.game.Mode
import com.centurywar.game.Role
import com.centurywar.meta.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale

/** Pełny update + render dla 72 wojowników; porównanie tylko w obrębie jednej JVM. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class BattleCrowdPerformance101Test {
    private val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
    }

    private class Fixture(
        val screen: BattleScreen, val battle: Battle, val bitmap: Bitmap
    ) {
        val canvas = Canvas(bitmap)
        fun frame() {
            screen.update(Battle.STEP)
            screen.render(canvas)
        }
    }

    private fun fixture(perSide: Int): Fixture {
        val file = File.createTempFile("cw-perf101-", ".json").apply { delete() }
        val profile = Profile(file).apply { tutorialDone = true; hintsEnabled = false }
        val game = Game(host, profile, Quality.LOW).apply {
            density = 1f
            bitmapCacheMemoryClassMb = 64
            resize(960, 540, 960, 540)
            finishScreenTransition()
        }
        val screen = BattleScreen(
            game, BattleConfig(Mode.SKIRMISH, 10101L, 1f, startEra = 0, maxEra = 0)
        )
        game.setScreen(screen)
        val field = BattleScreen::class.java.getDeclaredField("battle").apply { isAccessible = true }
        val battle = field.get(screen) as Battle
        battle.ai!!.thinkTimer = Float.MAX_VALUE
        battle.player.cap = 36
        battle.enemy.cap = 36
        battle.player.hold = true
        battle.enemy.hold = true
        val unit = Defs.unit(0, Role.MELEE)
        repeat(perSide) {
            assertTrue(battle.spawn(battle.player, unit))
            assertTrue(battle.spawn(battle.enemy, unit))
        }
        var positioned = 0
        for (fighter in battle.units) {
            if (!fighter.active) continue
            // Wypełniamy widoczny pas bez natychmiastowej śmierci podczas pomiaru.
            fighter.x = 200f + (positioned % 36) * 19f
            fighter.hp = 1_000_000f
            fighter.maxHp = fighter.hp
            fighter.dmg = 0f
            fighter.cd = 1_000f
            positioned++
        }
        assertEquals(perSide * 2, positioned)
        return Fixture(screen, battle, Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888))
    }

    private data class Sample(val medianMs: Double, val p95Ms: Double)

    private fun sample(fixture: Fixture): Sample {
        repeat(120) { fixture.frame() }
        val times = LongArray(240)
        for (i in times.indices) {
            val begin = System.nanoTime()
            fixture.frame()
            times[i] = System.nanoTime() - begin
        }
        times.sort()
        return Sample(times[120] / 1_000_000.0, times[227] / 1_000_000.0)
    }

    @Test fun seventyTwoUnitsStayWithinRelativeCpuBudgetOnLow() {
        val normal = fixture(18) // 36 jednostek
        val crowded = fixture(36) // dokładnie MAX_UNITS = 72
        val baseline = sample(normal)
        val heavy = sample(crowded)
        val ratio = heavy.medianMs / baseline.medianMs.coerceAtLeast(0.01)
        println(String.format(Locale.ROOT,
            "PERF101 LOW update+render: 36 jednostek median=%.3f p95=%.3f ms; " +
                "72 jednostki median=%.3f p95=%.3f ms; ratio=%.2fx",
            baseline.medianMs, baseline.p95Ms, heavy.medianMs, heavy.p95Ms, ratio))
        assertEquals(72, crowded.battle.units.count { it.active })
        // x2 jednostek: margines obejmuje interakcje O(n^2), GC oraz obciążone CI.
        assertTrue("Wzrost kosztu 72/36 = ${ratio}x, limit względny 8x", ratio < 8.0)
        normal.bitmap.recycle()
        crowded.bitmap.recycle()
    }
}
