package com.centurywar.screens

import android.graphics.RectF
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
import com.centurywar.game.BattleConfig
import com.centurywar.game.Defs
import com.centurywar.game.Mode
import com.centurywar.meta.Profile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BattleUpgradePanelTest {
    private data class Device(val w: Int, val h: Int, val density: Float, val inset: Int = 0)

    private fun screen(device: Device, scale: Int): Pair<Game, BattleScreen> {
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val file = File.createTempFile("upgrade177", ".json").apply { delete() }
        val profile = Profile(file).apply {
            tutorialDone = true
            hintsSeen = Profile.HINT_VALID_MASK
            hintsEnabled = false
            textScale = scale
            textScaleManual = true
        }
        val game = Game(host, profile, Quality.LOW)
        game.density = device.density
        game.resize(device.w, device.h, device.w, device.h)
        game.setInsets(device.inset, 0, device.inset, 0)
        game.applyAccessibility()
        val screen = BattleScreen(game, BattleConfig(Mode.SKIRMISH, 177L, 1f))
        game.setScreen(screen)
        screen.battle.player.slots = Defs.MAX_TURRET_SLOTS
        screen.battle.player.gold = 1_000_000f
        screen.layout()
        return game to screen
    }

    private fun hitRect(b: Button) = RectF(
        b.r.left - b.hitSlop + b.hitOffsetX, b.r.top - b.hitSlop + b.hitOffsetY,
        b.r.right + b.hitSlop + b.hitOffsetX, b.r.bottom + b.hitSlop + b.hitOffsetY,
    )

    private fun checkTargets(tag: String, game: Game, buttons: List<Button>) {
        val safe = RectF(game.insetL.toFloat(), game.insetT.toFloat(),
            (game.width - game.insetR).toFloat(), (game.height - game.insetB).toFloat())
        for ((i, button) in buttons.withIndex()) {
            assertTrue("$tag: przycisk ${button.id} niewidoczny", button.visible)
            val hit = hitRect(button)
            assertTrue("$tag: cel ${button.id} ma szerokość ${hit.width() / game.ui.dp} dp",
                hit.width() + 0.1f >= 48 * game.ui.dp)
            assertTrue("$tag: cel ${button.id} ma wysokość ${hit.height() / game.ui.dp} dp",
                hit.height() + 0.1f >= 48 * game.ui.dp)
            assertTrue("$tag: cel ${button.id} poza ekranem $hit / $safe", safe.contains(hit))
            for (other in buttons.drop(i + 1))
                assertFalse("$tag: cele ${button.id} i ${other.id} zachodzą na siebie",
                    RectF.intersects(hit, hitRect(other)))
        }
    }

    @Test
    fun upgradeAndTurretTargetsFitEightScreenConfigurations() {
        val devices = listOf(
            Device(1280, 720, 2f), // telefon 720p
            Device(1600, 720, 2f), // 20:9
            Device(904, 2316, 2.7f, 20), // Fold zewnętrzny
            Device(2184, 1968, 2.7f, 20), // Fold wewnętrzny
        )
        for (device in devices) for (scale in listOf(100, 130)) {
            val (game, s) = screen(device, scale)
            val tag = "${device.w}x${device.h}, tekst $scale%"
            s.showUpgrade = 0
            s.update(0f)
            checkTargets("$tag, jednostka", game, listOf(s.upgradeBuy, s.upgradeClose))
            s.showUpgrade = -1
            s.showTurrets = true
            // Pełne sloty pokazują wszystkie trzy przyciski każdego wiersza.
            for (i in 0 until Defs.MAX_TURRET_SLOTS) {
                s.battle.player.turretEra[i] = 0
                s.battle.player.turretTier[i] = 1
            }
            s.update(0f)
            val buttons = (0 until Defs.MAX_TURRET_SLOTS).flatMap {
                listOf(s.slotBuy[it], s.slotTier[it], s.slotSell[it])
            } + s.closeBtn
            checkTargets("$tag, wieże", game, buttons)
        }
    }

    @Test
    fun unitUpgradeRequiresGoldAndStopsAtMaximumTier() {
        val (_, s) = screen(Device(1600, 720, 2f), 100)
        val p = s.battle.player
        s.showUpgrade = 0
        p.gold = 0f
        s.update(0f)
        assertTrue(s.upgradeBuy.visible)
        assertFalse(s.upgradeBuy.enabled)
        assertTrue(s.upgradeClose.enabled)
        p.gold = 1_000_000f
        s.update(0f)
        assertTrue(s.upgradeBuy.enabled)
        p.unitTier[0] = Defs.MAX_UNIT_TIER
        s.update(0f)
        assertFalse(s.upgradeBuy.enabled)
        assertTrue(s.upgradeClose.enabled)
    }

    @Test
    fun turretButtonsReflectEmptyBuiltGoldAndMaxTier() {
        val (_, s) = screen(Device(1600, 720, 2f), 100)
        val p = s.battle.player
        s.showTurrets = true
        p.gold = 0f
        s.update(0f)
        assertTrue(s.slotBuy[0].visible)
        assertFalse(s.slotBuy[0].enabled)
        assertFalse(s.slotTier[0].visible)
        assertFalse(s.slotSell[0].visible)
        p.gold = 1_000_000f
        s.update(0f)
        assertTrue(s.slotBuy[0].enabled)
        p.turretEra[0] = 0
        p.turretTier[0] = 1
        p.gold = 0f
        s.update(0f)
        assertTrue(s.slotTier[0].visible)
        assertTrue(s.slotSell[0].visible)
        assertFalse(s.slotTier[0].enabled)
        p.gold = 1_000_000f
        s.update(0f)
        assertTrue(s.slotTier[0].enabled)
        p.turretTier[0] = 3
        s.update(0f)
        assertFalse(s.slotTier[0].enabled)
        assertTrue(s.slotSell[0].enabled)
    }
}
