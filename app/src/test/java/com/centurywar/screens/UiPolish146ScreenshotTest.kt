package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.game.BattleConfig
import com.centurywar.game.Defs
import com.centurywar.game.Mode
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
import kotlin.math.max

/** Zlecenie 146: geometria czterech wież, dymków i napisu weterana. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class UiPolish146ScreenshotTest {
    private data class Device(val tag: String, val width: Int, val height: Int,
                              val density: Float, val topInset: Int = 0)

    private val devices = listOf(
        Device("phone720", 1600, 720, 2f),
        Device("phone720small", 1280, 720, 1.5f),
        Device("phone720dense", 1280, 720, 2f),
        Device("phonePortrait", 720, 1280, 2f),
        Device("foldCover", 904, 2316, 2.7f, 88),
        Device("foldOpen", 2184, 1968, 2.7f, 70),
    )
    private val out = File("build/screenshots").apply { mkdirs() }
    private val tiny = Canvas(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))
    private val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
    }

    private fun game(device: Device, leftHanded: Boolean = false, hint: Int = -1): Game {
        val file = File.createTempFile("ui146", ".json").apply { delete() }
        val profile = Profile(file).apply {
            lang = "pl"
            tutorialDone = true
            leftHandedHud = leftHanded
            hintsSeen = if (hint < 0) Profile.HINT_VALID_MASK
                else Profile.HINT_VALID_MASK and (1L shl hint).inv()
        }
        return Game(host, profile, Quality.LOW).apply {
            density = device.density
            resize(device.width, device.height, device.width, device.height)
            setInsets(0, device.topInset, 0, 0)
        }
    }

    private fun shot(g: Game, name: String) {
        g.finishScreenTransition()
        val bmp = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
        try {
            g.frame(Canvas(bmp), 0f)
            File(out, "ui146_$name.png").outputStream().use {
                assertTrue(bmp.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            bmp.recycle()
        }
    }

    private fun assertInside(rect: RectF, bounds: RectF, tag: String) {
        assertTrue("$tag: lewa krawędź $rect nie mieści się w $bounds", rect.left >= bounds.left - 0.5f)
        assertTrue("$tag: prawa krawędź $rect nie mieści się w $bounds", rect.right <= bounds.right + 0.5f)
        assertTrue("$tag: góra $rect nie mieści się w $bounds", rect.top >= bounds.top - 0.5f)
        assertTrue("$tag: dół $rect nie mieści się w $bounds", rect.bottom <= bounds.bottom + 0.5f)
    }

    @Test
    fun fourTurretsFitBetweenTopAndHudAndKeep48DpTargets() {
        val lang = L.lang
        try {
            L.lang = L.PL
            for (d in devices) for (left in listOf(false, true)) {
                val g = game(d, left)
                val screen = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 146L, 1f))
                g.setScreen(screen)
                val p = screen.battle.player
                p.slots = Defs.MAX_TURRET_SLOTS
                p.gold = 100000f
                p.era = screen.cfg.maxEra
                for (i in 0 until p.slots) {
                    p.turretEra[i] = p.era
                    p.turretTier[i] = 1
                }
                screen.showTurrets = true
                screen.layout()
                repeat(2) { g.frame(tiny, 0f) }
                val tag = "${d.tag} ${if (left) "L" else "R"}"
                val dp = g.ui.dp
                val panel = screen.panel
                assertTrue("$tag: panel zachodzi na pasek górny",
                    panel.top >= screen.topBar.bottom - 0.5f)
                assertTrue("$tag: zamykanie zachodzi na górny pasek",
                    screen.closeBtn.r.top >= screen.topBar.bottom - 0.5f)
                assertTrue("$tag: panel zachodzi na HUD",
                    panel.bottom <= screen.fieldRect.bottom + 0.5f)
                val buttons = ArrayList<RectF>(12)
                for (i in 0 until p.slots) {
                    for (button in arrayOf(screen.slotBuy[i], screen.slotTier[i], screen.slotSell[i])) {
                        assertTrue("$tag: ukryty przycisk ${button.id}", button.visible)
                        assertInside(button.r, panel, "$tag: slot $i akcja ${button.id}")
                        assertTrue("$tag: zbyt wąski przycisk ${button.id}",
                            button.r.width() >= 47.5f * dp)
                        assertTrue("$tag: za niski cel dotyku ${button.id}",
                            button.r.height() + 2f * button.hitSlop >= 47.5f * dp)
                        buttons.add(RectF(button.r))
                    }
                }
                for (i in buttons.indices) for (j in i + 1 until buttons.size)
                    assertFalse("$tag: nakładające się przyciski $i / $j",
                        RectF.intersects(buttons[i], buttons[j]))
                assertEquals("$tag: brak podpisu maksymalnej epoki",
                    L["tier145_max"], screen.slotBuy[0].label)
                assertFalse("$tag: maksymalny awans wieży powinien być niedostępny",
                    screen.slotBuy[0].enabled)
                val label = screen.r.veteranLabelY(360f, 22f * dp)
                assertTrue("$tag: napis weterana nachodzi na pasek",
                    label - 22f * dp >= screen.topBar.bottom + 4f * dp)
                shot(g, "${d.tag}_${if (left) "L" else "R"}_four_turrets")
            }
        } finally { L.lang = lang }
    }

    @Test
    fun wrappedHintFitsItsTextAndDoesNotCoverHud() {
        for (d in devices) for (left in listOf(false, true)) {
            val g = game(d, left, HintOverlay.UPGRADE)
            val screen = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 1462L, 1f))
            g.setScreen(screen)
            g.finishScreenTransition()
            screen.battle.player.gold = 100000f
            var i = 0
            val hint = BattleScreen::class.java.getDeclaredField("hints")
                .apply { isAccessible = true }.get(screen) as HintOverlay
            while (hint.activeId < 0 && i++ < 30) g.frame(tiny, 1f / 30f)
            val tag = "${d.tag} ${if (left) "L" else "R"}"
            assertEquals("$tag: brak podpowiedzi ulepszenia", HintOverlay.UPGRADE, hint.activeId)
            shot(g, "${d.tag}_${if (left) "L" else "R"}_hint")
            assertTrue("$tag: pusty dymek", !hint.panelRect.isEmpty)
            assertInside(hint.panelRect, screen.fieldRect, "$tag dymek")
            for (button in screen.buttons) {
                if (button.visible && button.id != HintOverlay.OK_ID) {
                    assertFalse("$tag: dymek nakrywa HUD #${button.id}",
                        RectF.intersects(hint.panelRect, button.r))
                }
            }
            assertTrue("$tag: przycisk OK ma cel poniżej 48dp",
                hint.okBtn.r.height() + 2f * hint.okBtn.hitSlop >= 48f * g.ui.dp)
            assertInside(hint.okBtn.r, hint.panelRect, "$tag OK")
        }
    }

    @Test
    fun multilineHintDoesNotReserveEmptyButtonRow() {
        val lang = L.lang
        try {
            L.lang = L.PL
            val d = devices.first { it.tag == "foldCover" }
            val g = game(d, hint = HintOverlay.FIRST_BATTLE)
            val hint = HintOverlay(g)
            hint.showScreen(HintOverlay.FIRST_BATTLE,
                RectF(20f, 160f, 300f, 260f))
            val bmp = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
            try {
                hint.draw(Canvas(bmp))
                File(out, "ui146_foldCover_multiline_hint.png").outputStream().use {
                    assertTrue(bmp.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally { bmp.recycle() }
            val field = HintOverlay::class.java.getDeclaredField("lineCount")
                .apply { isAccessible = true }
            val lines = field.getInt(hint)
            assertTrue("Tekst powinien być łamany", lines >= 2)
            val dp = g.ui.dp
            assertTrue("Dymek ma zbędny pusty pas poniżej treści",
                hint.panelRect.height() <= max(58f, 35f + lines * 17f) * dp)
            assertTrue("Zbyt mały cel dotyku OK",
                hint.okBtn.r.height() + 2 * hint.okBtn.hitSlop >= 48f * dp)
            assertInside(hint.okBtn.r, hint.panelRect, "wielowierszowy OK")
        } finally { L.lang = lang }
    }
}
