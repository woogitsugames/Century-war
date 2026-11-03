package com.centurywar.engine

import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.BattleSnapshot
import com.centurywar.game.Mode
import com.centurywar.game.RestoredBattle
import com.centurywar.meta.Profile
import com.centurywar.screens.AccessibilityScreen
import com.centurywar.screens.BattleScreen
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

/** R2-03: rozmiar tekstu, animacje i TalkBack z ustawień telefonu; tempo ×½. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SystemAccessibilityTest {
    private val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
    }

    private fun tmpFile(): File = File(Files.createTempDirectory("cw-r203").toFile(), "profile.json")

    private fun game(profile: Profile = Profile(tmpFile())): Game {
        profile.tutorialDone = true
        profile.hintsSeen = Profile.HINT_VALID_MASK
        profile.eraIntrosEnabled = false
        return Game(host, profile, Quality.LOW).apply {
            density = 2f
            resize(1600, 720, 1600, 720)
        }
    }

    private fun snapshot(font: Float = 1f, animOff: Boolean = false, talkBack: Boolean = false) =
        SystemA11y(font, animOff, talkBack)

    @Test
    fun readsFontScaleAnimationsAndTouchExploration() {
        val context = RuntimeEnvironment.getApplication()
        RuntimeEnvironment.setFontScale(1.3f)
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        shadowOf(manager).setTouchExplorationEnabled(true)
        val s = SystemAccessibility.read(context)
        assertEquals(1.3f, s.fontScale, 0.001f)
        assertTrue(s.animationsOff)
        assertTrue(s.touchExploration)

        val g = game()
        g.applySystemAccessibility(s)
        assertEquals("Świeży profil przejmuje skalę telefonu", 130, g.profile.textScale)
        assertEquals(1.3f, g.ui.textScale, 0.001f)
        assertTrue(g.profile.reduceShake)
        assertTrue(g.profile.reduceFlash)
        assertTrue(g.touchExplorationActive)
    }

    @Test
    fun fontScaleThresholds() {
        assertEquals(100, SystemAccessibility.textScaleFor(1.0f))
        assertEquals(100, SystemAccessibility.textScaleFor(1.11f))
        assertEquals(115, SystemAccessibility.textScaleFor(1.15f))
        assertEquals(130, SystemAccessibility.textScaleFor(1.3f))
        assertEquals(130, SystemAccessibility.textScaleFor(2f))
    }

    @Test
    fun manualChoiceWinsOverSystem() {
        val g = game()
        g.profile.textScale = 100
        g.profile.textScaleManual = true
        g.profile.motionManual = true
        g.applySystemAccessibility(snapshot(font = 1.3f, animOff = true))
        assertEquals(100, g.profile.textScale)
        assertFalse(g.profile.reduceShake)
        assertFalse(g.profile.reduceFlash)

        // Ręczna zmiana w Dostępności ustawia flagi i przetrwa kolejną zmianę czcionki systemu.
        val g2 = game()
        g2.applySystemAccessibility(snapshot(font = 1.3f))
        assertEquals(130, g2.profile.textScale)
        val screen = AccessibilityScreen(g2)
        g2.setScreen(screen)
        screen.onClick(screen.buttons.first { it.id == 31 })
        assertTrue(g2.profile.textScaleManual)
        screen.onClick(screen.buttons.first { it.id == 41 })
        assertTrue(g2.profile.motionManual)
        g2.applySystemAccessibility(snapshot(font = 1.0f, animOff = true))
        assertEquals(115, g2.profile.textScale)
        assertTrue(g2.profile.reduceShake)
        assertFalse("Ręczny wybór ruchu nie jest nadpisywany", g2.profile.reduceFlash)
    }

    @Test
    fun systemFontFollowsChangesUntilManual() {
        val g = game()
        g.applySystemAccessibility(snapshot(font = 1.15f))
        assertEquals(115, g.profile.textScale)
        g.applySystemAccessibility(snapshot(font = 1.0f))
        assertEquals(100, g.profile.textScale)
        assertFalse(g.profile.textScaleManual)
        g.applySystemAccessibility(snapshot(animOff = false))
        assertFalse("Animacje włączone: bez zmian", g.profile.reduceShake)
    }

    @Test
    fun oldProfileMigration() {
        val base = JSONObject(Profile(tmpFile()).toJson()).apply {
            remove("textScaleManual"); remove("motionManual")
            remove("slowSpeedOption"); remove("slowSpeedManual")
        }
        val file100 = tmpFile().apply { writeText(base.toString()) }
        val g100 = game(Profile(file100).also { it.load() })
        g100.applySystemAccessibility(snapshot(font = 1.3f))
        assertEquals("Stary profil ze 100% przejmuje skalę z telefonu", 130, g100.profile.textScale)

        val file115 = tmpFile().apply { writeText(JSONObject(base.toString()).put("textScale", 115).toString()) }
        val g115 = game(Profile(file115).also { it.load() })
        assertTrue(g115.profile.textScaleManual)
        g115.applySystemAccessibility(snapshot(font = 1.3f, animOff = true))
        assertEquals("Stary profil ze 115% zostaje przy wyborze gracza", 115, g115.profile.textScale)
        assertTrue("Ruch nie był ręcznie zmieniany", g115.profile.reduceShake)
    }

    @Test
    fun talkBackStartsBattleAtHalfSpeedAlsoWhenResumed() {
        val g = game()
        g.applySystemAccessibility(snapshot(talkBack = true))
        val cfg = BattleConfig(Mode.SKIRMISH, 181L, 1f)
        assertEquals(0, BattleScreen(g, cfg).speed)
        val battle = Battle(cfg)
        repeat(30) { battle.step() }
        assertEquals(0, BattleScreen(g, cfg, restored = RestoredBattle(battle, false, -120f)).speed)
        val s = BattleScreen(g, cfg)
        // Cykl zawiera ×½ także bez opcji.
        assertEquals(listOf(1, 3, 5, 0), generateSequence(s.nextSpeed(0, g.slowSpeedEnabled())) {
            s.nextSpeed(it, g.slowSpeedEnabled())
        }.take(4).toList())

        // Gracz ręcznie wyłączył opcję: start ×1, cykl bez ×½.
        val screen = AccessibilityScreen(g)
        g.setScreen(screen)
        assertTrue(screen.accessibilityChecked(screen.buttons.first { it.id == 44 }))
        screen.onClick(screen.buttons.first { it.id == 44 })
        assertTrue(g.profile.slowSpeedManual)
        assertFalse(g.profile.slowSpeedOption)
        assertFalse(g.slowSpeedEnabled())
        assertEquals(1, BattleScreen(g, cfg).speed)
        assertEquals(1, s.nextSpeed(5, g.slowSpeedEnabled()))

        // Bez TalkBack bitwa startuje w ×1.
        val plain = game()
        assertEquals(1, BattleScreen(plain, cfg).speed)
    }

    @Test
    fun slowOptionCycleAndHalfAccumulation() {
        val g = game()
        val s = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 182L, 1f))
        assertEquals(listOf(3, 5, 1), listOf(s.nextSpeed(1, false), s.nextSpeed(3, false), s.nextSpeed(5, false)))
        g.profile.slowSpeedOption = true
        assertTrue(g.slowSpeedEnabled())
        assertEquals(listOf(1, 3, 5, 0), listOf(0, 1, 3, 5).map { s.nextSpeed(it, true) })
        assertEquals(0.5f, BattleScreen.speedFactor(0), 0f)
        assertEquals("×½", s.speedLabels[0])

        g.setScreen(s)
        s.speed = 0
        val acc = BattleScreen::class.java.getDeclaredField("acc").apply { isAccessible = true }
        val dt = Battle.STEP / 4f
        val before = s.battle.time
        acc.setFloat(s, 0f)
        s.update(dt)
        assertEquals("Przy ×½ akumulator rośnie o połowę", dt * 0.5f, acc.getFloat(s), 1e-6f)
        assertEquals(before, s.battle.time, 0f)
        assertEquals("×½", s.speedButtonLabel())
    }

    @Test
    fun halfSpeedKeepsSimulationIdentical() {
        val cfg = BattleConfig(Mode.SKIRMISH, 183L, 1f)
        val slow = BattleScreen(game(), cfg).apply { speed = 0 }
        val normal = BattleScreen(game(), cfg).apply { speed = 1 }
        val dt = Battle.STEP * 2f
        // ×½: jeden krok na klatkę, ×1: dwa kroki; po tej samej liczbie kroków stan jest identyczny.
        repeat(400) { slow.update(dt) }
        repeat(200) { normal.update(dt) }
        assertEquals(normal.battle.time, slow.battle.time, 0f)
        assertArrayEquals(BattleSnapshot.encode(normal.battle), BattleSnapshot.encode(slow.battle))
    }
}
