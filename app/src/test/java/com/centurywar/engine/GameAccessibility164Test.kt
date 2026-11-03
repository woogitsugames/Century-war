package com.centurywar.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import com.centurywar.game.BattleConfig
import com.centurywar.game.Mode
import com.centurywar.meta.Profile
import com.centurywar.screens.AboutScreen
import com.centurywar.screens.AccessibilityScreen
import com.centurywar.screens.BattleScreen
import com.centurywar.screens.HintSettingsScreen
import com.centurywar.screens.MenuScreen
import com.centurywar.screens.SettingsScreen
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** A156-21: bez providera nie ma żadnego z tych węzłów ani kliknięć czytnika. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GameAccessibility164Test {
    private val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
        var playtestShares = 0
        override fun sharePlaytestReport() { playtestShares++ }
    }

    private class Fixture(val game: Game, val view: GameView, val bitmap: Bitmap) {
        private val canvas = Canvas(bitmap)
        val provider get() = view.accessibilityNodeProvider!!

        fun frame(dt: Float = 0f) {
            game.frame(canvas, dt)
            view.refreshAccessibilityNodes()
        }

        fun node(id: Int): AccessibilityNodeInfo? = provider.createAccessibilityNodeInfo(id)

        fun buttonId(buttonId: Int): Int {
            val screen = game.current!!
            val index = screen.buttons.indexOfFirst { it.id == buttonId }
            assertTrue("Brak przycisku $buttonId", index >= 0)
            return index + 1
        }

        fun press(id: Int) {
            assertTrue("Kliknięcie węzła $id", provider.performAction(id, AccessibilityNodeInfo.ACTION_CLICK, null))
            frame()
        }

        fun childCount(): Int = provider.createAccessibilityNodeInfo(View.NO_ID)!!.childCount

        fun finish() { bitmap.recycle() }
    }

    private fun setup(w: Int = 1600, h: Int = 720, density: Float = 2f): Fixture {
        val file = File.createTempFile("cw-access164-", ".json").apply { delete() }
        val profile = Profile(file).apply {
            lang = "pl"
            tutorialDone = true
        }
        val game = Game(host, profile, Quality.LOW).apply {
            this.density = density
            resize(w, h, w, h)
            setScreen(MenuScreen(this))
        }
        val view = GameView(RuntimeEnvironment.getApplication(), game)
        view.layout(0, 0, w, h)
        return Fixture(game, view, Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)).also { it.frame() }
    }

    @Test fun menuHasLabeledButtonsAndClickUsesGameTouch() {
        val f = setup()
        try {
            assertTrue(f.childCount() >= 7)
            val settings = f.node(f.buttonId(7))!!
            assertEquals("android.widget.Button", settings.className.toString())
            assertEquals(L["settings"], settings.contentDescription.toString())
            assertTrue(settings.isClickable)
            val box = Rect()
            settings.getBoundsInScreen(box)
            assertTrue(box.width() > 0 && box.height() > 0)
            assertTrue(box.left >= 0 && box.right <= 1600)

            f.press(f.buttonId(7))
            assertTrue(f.game.current is SettingsScreen)
            assertEquals(L["settings"], f.node(View.NO_ID)!!.contentDescription.toString())
        } finally { f.finish() }
    }

    @Test fun settingsDescribeRowAndStateAndRejectDisabled() {
        val f = setup()
        try {
            f.game.push(SettingsScreen(f.game))
            f.frame()
            val screen = f.game.current as SettingsScreen
            val soundId = f.buttonId(8)
            val sound = f.node(soundId)!!
            assertTrue(sound.contentDescription.toString().contains(L["sound"]))
            assertTrue(sound.isCheckable)
            assertEquals(f.game.profile.sound, sound.isChecked)
            val first = f.game.profile.sound
            f.press(soundId)
            assertEquals(!first, f.game.profile.sound)
            assertEquals(!first, f.node(soundId)!!.isChecked)

            val disabled = screen.buttons.firstOrNull { it.visible && !it.enabled }
            if (disabled != null) {
                val id = f.buttonId(disabled.id)
                assertFalse(f.node(id)!!.isEnabled)
                assertFalse(f.provider.performAction(id, AccessibilityNodeInfo.ACTION_CLICK, null))
            }
            assertEquals(screen, f.game.current)
        } finally { f.finish() }
    }

    @Test fun accessibilityStateAndScrollRefreshVisibleNodes() {
        val f = setup(w = 1200, h = 720)
        try {
            val screen = AccessibilityScreen(f.game)
            f.game.push(screen)
            f.frame()
            val paletteId = f.buttonId(11)
            assertTrue(f.node(paletteId)!!.isCheckable)
            f.press(paletteId)
            assertEquals(1, f.game.profile.palette)
            assertTrue(f.node(paletteId)!!.isChecked)

            val before = f.childCount()
            val revision = f.view.accessibilityRevision
            f.view.refreshAccessibilityNodes()
            assertEquals("Bez zmian nie wolno odbudowywać drzewa", revision, f.view.accessibilityRevision)
            screen.onDrag(0f, -1200f)
            f.view.refreshAccessibilityNodes()
            assertTrue(f.view.accessibilityRevision > revision)
            assertTrue(before > 0)
            assertNotNull(f.node(f.buttonId(42)))
            assertTrue(f.node(f.buttonId(42))!!.contentDescription.toString().contains(L["access_reduce_flash"]))
            // R2-03: tempo ×½ ma opis wiersza i stan zaznaczenia.
            val slowId = f.buttonId(44)
            val slow = f.node(slowId)!!
            assertTrue(slow.contentDescription.toString().contains(L["a11y181_slow"]))
            assertTrue(slow.isCheckable)
            assertFalse(slow.isChecked)
            f.press(slowId)
            assertTrue(f.game.profile.slowSpeedOption)
            assertTrue(f.game.profile.slowSpeedManual)
            assertTrue(f.node(slowId)!!.isChecked)
        } finally { f.finish() }
    }

    @Test fun hintsSwitchKeepsNameAndCheckedState() {
        val f = setup()
        try {
            f.game.push(HintSettingsScreen(f.game))
            f.frame()
            val id = f.buttonId(1)
            assertTrue(f.node(id)!!.contentDescription.toString().contains(L["hint_enabled"]))
            assertTrue(f.node(id)!!.isCheckable)
            val old = f.game.profile.hintsEnabled
            f.press(id)
            assertEquals(!old, f.game.profile.hintsEnabled)
            assertEquals(!old, f.node(id)!!.isChecked)
        } finally { f.finish() }
    }

    /** Dzień 5: poza pauzą HUD ma osobne węzły; pauza nadal ma tylko pięć kontrolek, wyniki mają opis. */
    @Test fun battleExposesOnlyPauseUntilPausedAndThenOnlyFiveControls() {
        val f = setup()
        try {
            val battle = BattleScreen(f.game, BattleConfig(Mode.SKIRMISH, 7L, 1f))
            f.game.setScreen(battle)
            f.frame()
            val pauseId = GameAccessibility.BTN_BASE + BattleScreen.PAUSE
            assertNotNull(f.node(pauseId))
            assertEquals("android.widget.Button", f.node(pauseId)!!.className.toString())
            assertTrue(f.childCount() > 5)
            f.press(pauseId)
            assertTrue(battle.paused)
            assertEquals(5, f.childCount())
            assertNull("Po otwarciu pauzy nie może pozostać węzeł HUD", f.node(pauseId))
            val resumeId = f.buttonId(BattleScreen.RESUME)
            assertTrue(f.node(resumeId)!!.contentDescription.toString().contains(L["resume"]))
            f.press(resumeId)
            assertFalse(battle.paused)
            assertNotNull(f.node(pauseId))

            // Koniec bitwy: opis wyniku i przyciski wyników.
            f.press(pauseId)
            f.press(f.buttonId(BattleScreen.QUIT))
            repeat(12) { f.frame(0.1f) }
            assertTrue(battle.finished)
            val result = f.node(GameAccessibility.INFO_RESULT)
            assertNotNull(result)
            assertTrue(result!!.contentDescription.toString().isNotBlank())
            val cont = f.node(GameAccessibility.BTN_BASE + BattleScreen.CONTINUE)
            assertNotNull(cont)
            assertTrue(cont!!.contentDescription.toString().contains(L["continue"]))
            assertNull(f.node(pauseId))
        } finally { f.finish() }
    }

    @Test fun accessFocusAndLanguageHaveWorkingLocalizedNodes() {
        val f = setup()
        try {
            val id = f.buttonId(7)
            assertTrue(f.provider.performAction(id, AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null))
            assertEquals(f.node(id)!!.contentDescription.toString(),
                f.provider.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)!!.contentDescription.toString())
            assertTrue(f.provider.performAction(id, AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS, null))
            assertNull(f.provider.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY))
            for (key in arrayOf("a164_loading", "a164_menu", "a164_battle", "a164_battle_pause", "a164_other", "a164_pause")) {
                val translations = L.raw(key)
                assertNotNull(key, translations)
                assertEquals(key, L.CODES.size, translations!!.size)
                for (s in translations) assertTrue(key, s.isNotBlank())
            }
        } finally { f.finish() }
    }

    @Test fun layoutUsesViewPixelsAfterBufferScaling() {
        val f = setup(w = 1600, h = 720)
        try {
            f.game.resize(800, 360, 1600, 720)
            f.game.current!!.layout()
            f.view.refreshAccessibilityNodes()
            val button = f.game.current!!.buttons.first { it.id == 7 }
            val box = Rect()
            f.node(f.buttonId(7))!!.getBoundsInScreen(box)
            assertTrue(kotlin.math.abs((button.r.centerX() * 2).toInt() - box.centerX()) <= 2)
            assertTrue(kotlin.math.abs((button.r.centerY() * 2).toInt() - box.centerY()) <= 2)
        } finally { f.finish() }
    }

    /** R2-04: „Raport dla testera” w O grze ma opis TalkBack, otwiera okno hosta; Wstecz działa. */
    @Test fun aboutTesterReportHasDescriptionAndBackWorks() {
        val f = setup(w = 1600, h = 1400)
        try {
            f.game.push(AboutScreen(f.game))
            f.frame()
            val id = f.buttonId(6)
            val node = f.node(id)!!
            val description = node.contentDescription.toString()
            assertTrue(description, description.contains(L["pt182_button"]))
            assertTrue(description, description.contains(L["pt182_a11y"]))
            assertTrue(node.isClickable)
            assertEquals(L["about"], f.node(View.NO_ID)!!.contentDescription.toString())
            val before = host.playtestShares
            f.press(id)
            assertEquals("Przycisk tylko prosi hosta o okno z opisem", before + 1, host.playtestShares)
            assertTrue(f.game.current is AboutScreen)
            f.game.requestBack()
            f.frame()
            assertTrue(f.game.current is MenuScreen)
        } finally { f.finish() }
    }
}
