package com.centurywar.screens

import com.centurywar.engine.Quality
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.Bonuses
import com.centurywar.game.Defs
import com.centurywar.game.Mode
import com.centurywar.game.Role
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Audyt 72 (obszar B, czucie bitwy): sekwencja bitwy na telefonie 720p (d=2) i zewnętrznym
 * ekranie Folda (d=2,7) — start, zakup, tłok, ostrzał, awans epoki, zniszczenie bazy.
 * Tylko PNG do przeglądu (build/screenshots/audit72_*), bez wzorców regresji.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class Audit72BattleFeelTest : ScreenshotTestBase() {
    private val devices = mapOf(
        "phone720" to floatArrayOf(1600f, 720f, 2f),
        "fold" to floatArrayOf(904f, 2316f, 2.7f),
    )

    private fun start(tag: String, era: Int, seed: Long, recruits: Boolean = false): Pair<com.centurywar.engine.Game, BattleScreen> {
        val d = devices.getValue(tag)
        val g = game(d[0].toInt(), d[1].toInt(), d[2], if (tag == "phone720") Quality.LOW else Quality.HIGH)
        g.profile.hintsEnabled = false
        val bs = BattleScreen(g, BattleConfig(Mode.SKIRMISH, seed, 1f, startEra = era, maxEra = era + 1,
            playerBonuses = Bonuses(eliteUnlocked = true, rallyUnlocked = true), eraRecruits = recruits))
        g.setScreen(bs)
        return g to bs
    }

    private fun battle(bs: BattleScreen) = field(bs, "battle").get(bs) as Battle

    @Test
    fun battleSequence() {
        for (tag in devices.keys) {
            // 1. Zakup jednostki: przed i tuż po tapie (reakcja w 1-2 klatkach).
            run {
                val (g, bs) = start(tag, 0, 7201L)
                val b = battle(bs)
                b.player.gold = 400f
                shot(g, "audit72_${tag}_01_start", 30)
                val u = bs.unitBtn[0].r
                tap(g, u.centerX(), u.centerY())
                shot(g, "audit72_${tag}_02_buy_frame2", 2)
                shot(g, "audit72_${tag}_03_buy_1s", 60)
                // Tap przy braku złota.
                b.player.gold = 0f
                shot(g, "audit72_${tag}_04_nogold", 2)
                val u2 = bs.unitBtn[1].r
                tap(g, u2.centerX(), u2.centerY())
                shot(g, "audit72_${tag}_05_nogold_tap", 3)
                shot(g, "audit72_${tag}_05b_nogold_tap_10f", 10)
            }
            // 2. Tłok: po 12 jednostek z każdej strony (epoka z rekrutem D69).
            run {
                val era = 6
                val (g, bs) = start(tag, era, 7202L, recruits = true)
                val b = battle(bs)
                if (b.pendingVariant != null) bs.onClick(bs.variantBtn[1])
                b.ai = null
                val recruit = Defs.recruitUnit(era)
                for (k in 0 until 12) {
                    val role = Role.entries[k % 4]
                    b.spawn(b.player, if (k % 3 == 0 && recruit != null) recruit else Defs.unit(era, role))
                    b.spawn(b.enemy, Defs.unit(era, Role.entries[(k + 1) % 4]))
                }
                var n = 0; var m = 0
                for (u in b.units) if (u.alive) {
                    u.x = if (u.side == 0) 520f + (n++) * 22f else 820f + (m++) * 22f
                    u.prevX = Float.NaN
                }
                shot(g, "audit72_${tag}_06_crowd", 60)
                shot(g, "audit72_${tag}_07_crowd_3s", 180)
                // 3. Ostrzał: szybki (drugi tap przycisku).
                b.player.abilityCd[0] = 0f
                bs.onClick(bs.barrageBtn)
                shot(g, "audit72_${tag}_08_barrage_aim", 2)
                bs.onClick(bs.barrageBtn)
                shot(g, "audit72_${tag}_09_barrage_fire", 4)
                shot(g, "audit72_${tag}_10_barrage_1s", 50)
            }
            // 4. Awans epoki.
            run {
                val (g, bs) = start(tag, 2, 7203L)
                val b = battle(bs)
                b.player.xp = Defs.evolveXp[b.player.era] + 10f
                shot(g, "audit72_${tag}_11_evolve_ready", 30)
                bs.onClick(bs.evolveBtn)
                shot(g, "audit72_${tag}_12_evolve_frame2", 2)
                shot(g, "audit72_${tag}_12b_evolve_15f", 13)
                shot(g, "audit72_${tag}_13_evolve_1s", 60)
            }
            // 5. Zniszczenie bazy wroga: chwila zwycięstwa.
            run {
                val (g, bs) = start(tag, 3, 7204L)
                val b = battle(bs)
                b.ai = null
                for (k in 0 until 6) b.spawn(b.player, Defs.unit(3, Role.entries[k % 4]))
                var n = 0
                val front = b.enemy.baseFront
                for (u in b.units) if (u.alive && u.side == 0) {
                    u.x = front - 60f - (n++) * 40f; u.prevX = Float.NaN
                }
                field(bs, "manualCam").setFloat(bs, 0f)
                shot(g, "audit72_${tag}_14_prewin", 120)
                b.enemy.baseHp = 0.5f
                val fin = field(bs, "finished")
                var guard = 0
                while (!fin.getBoolean(bs) && guard++ < 600) g.frame(android.graphics.Canvas(
                    android.graphics.Bitmap.createBitmap(4, 4, android.graphics.Bitmap.Config.ARGB_8888)), 1f / 60f)
                shot(g, "audit72_${tag}_15_win_6f", 6)
                shot(g, "audit72_${tag}_15b_win_18f", 12)
                shot(g, "audit72_${tag}_15c_win_30f", 12)
                shot(g, "audit72_${tag}_16_win_48f", 18)
                shot(g, "audit72_${tag}_17_win_90f", 42)
            }
        }
    }
}
