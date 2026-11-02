package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.Icons
import com.centurywar.engine.Quality
import com.centurywar.engine.TouchEvent
import com.centurywar.game.BattleConfig
import com.centurywar.game.Mode
import com.centurywar.game.Replay
import com.centurywar.meta.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Regresje A156-09 i A156-24: rzeczywisty dotyk oraz szerokość tekstu. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class FlashTurretTouch161Test {
    private val canvas = Canvas(Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888))

    private fun game(width: Int, height: Int, density: Float = 2f): Game {
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val f = File.createTempFile("flash161", ".json").apply { delete() }
        val profile = Profile(f).apply {
            tutorialDone = true
            hintsEnabled = false
            hintsSeen = Profile.HINT_VALID_MASK
            eraIntrosEnabled = false
        }
        return Game(host, profile, Quality.LOW).apply {
            this.density = density
            resize(width, height, width, height)
        }
    }

    private fun tap(g: Game, b: Button) {
        g.pushTouch(TouchEvent.DOWN, b.r.centerX(), b.r.centerY(), 0)
        g.pushTouch(TouchEvent.UP, b.r.centerX(), b.r.centerY(), 0)
        g.frame(canvas, 1f / 60f)
    }

    @Test
    fun compactTowerPanelDispatchesEachDistinctSlotByScreenHitTest() {
        val g = game(1200, 720)
        val screen = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 161L, 1f))
        screen.battle.player.slots = 4
        screen.battle.player.gold = 1_000_000f
        screen.battle.playerAi = null
        g.setScreen(screen)
        screen.showTurrets = true
        g.frame(canvas, 1f / 60f)

        assertTrue("niskie okno musi używać modalu", screen.turretOverlay)
        assertFalse("HUD pod modalem nie może przechwytywać tapów", screen.unitBtn[0].visible)
        assertFalse("pauza pod modalem nie może przechwytywać tapów", screen.pauseBtn.visible)

        val dp = g.ui.dp
        for (slot in 0 until 4) {
            val target = screen.slotBuy[slot]
            assertTrue("slot $slot: szerokość", target.r.width() >= 44f * dp)
            assertTrue("slot $slot: wysokość", target.r.height() >= 48f * dp - 0.1f)
            assertTrue("slot $slot niewidoczny", target.visible)
            val first = screen.buttons.firstOrNull {
                it.enabled && it.contains(target.r.centerX(), target.r.centerY())
            }
            assertTrue("slot $slot jest zasłonięty przez ${first?.id}", first === target)
            tap(g, target)
            val last = screen.commands.log().commands.last()
            assertEquals("slot $slot: niewłaściwa komenda", Replay.Kind.BUY_TURRET, last.kind)
            assertEquals("slot $slot: niewłaściwy indeks", slot, last.argument)
            assertTrue("slot $slot: wieża nie została kupiona", screen.battle.player.turretEra[slot] >= 0)
        }
        assertEquals(4, screen.commands.log().commands.size)
    }

    @Test
    fun standardGridRemainsOutsideModalOn720pAndFold() {
        for ((width, height, density) in listOf(
            Triple(1600, 720, 2f),
            Triple(2184, 1968, 2.7f),
        )) {
            val g = game(width, height, density)
            val screen = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 162L, 1f))
            screen.battle.player.slots = 4
            g.setScreen(screen)
            screen.showTurrets = true
            g.frame(canvas, 1f / 60f)
            assertFalse("$width x $height: niepotrzebny modal", screen.turretOverlay)
            assertTrue("$width x $height: HUD pozostaje widoczny", screen.unitBtn[0].visible)
        }
    }

    @Test
    fun lateEraUnitPriceAndCoinStayInsideFortyEightDpButton() {
        val g = game(720, 1280)
        val dp = g.ui.dp
        g.ui.textScale = 1.15f
        for (customIcon in listOf(true, false)) {
            val bmp = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888)
            val measured = object : Canvas(bmp) {
                var left = Float.POSITIVE_INFINITY
                var right = Float.NEGATIVE_INFINITY
                override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
                    if (text == "14748" && paint.style == Paint.Style.FILL) {
                        left = minOf(left, x)
                        right = maxOf(right, x + paint.measureText(text))
                    }
                    super.drawText(text, x, y, paint)
                }
            }
            val b = Button(161).apply {
                set(100 * dp, 100 * dp, 48 * dp, 48 * dp)
                sub = "14748"
                subIcon = Icons.COIN
                if (customIcon) icon = { _, _ -> } else iconId = Icons.PERSON
            }
            g.ui.button(measured, b, 13f)
            assertTrue("cena nie została narysowana", measured.left.isFinite())
            assertTrue("cena wychodzi z lewej: ${measured.left}", measured.left >= b.r.left + 4 * dp - 1f)
            assertTrue("cena wychodzi z prawej: ${measured.right}", measured.right <= b.r.right - 4 * dp + 1f)
            val sz = g.ui.subTextSize(b, 13f * 0.86f, b.r.width() - 8 * dp)
            val full = g.ui.measure(b.sub, sz, true) + (sz * dp * 1.25f) + 3 * dp
            assertTrue("ikona monety + cena wykracza poza 48 dp", full <= b.r.width() - 8 * dp + 1f)
            bmp.recycle()
        }
    }
}
