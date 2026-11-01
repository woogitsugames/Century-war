package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
import com.centurywar.engine.TouchEvent
import com.centurywar.game.BattleConfig
import com.centurywar.game.BattleController
import com.centurywar.game.Mode
import com.centurywar.meta.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Kontrola lustrzanego HUD, bez zmiany kamery i komend symulacji. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LeftHandedHudTest {
    private val canvas = Canvas(Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888))

    private fun game(width: Int, height: Int, density: Float = 2f): Game {
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val file = File.createTempFile("hud110", ".json").apply { delete() }
        val game = Game(host, Profile(file).apply {
            tutorialDone = true
            hintsSeen = Profile.HINT_VALID_MASK
        }, Quality.LOW)
        game.density = density
        game.resize(width, height, width, height)
        return game
    }

    private fun button(s: BattleScreen, id: Int): Button = s.buttons.first { it.id == id }
    private fun rect(s: BattleScreen, id: Int): RectF = RectF(button(s, id).r)
    private fun field(s: BattleScreen, name: String): RectF =
        RectF(s.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(s) as RectF)
    private fun overlap(a: RectF, b: RectF) =
        a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

    private fun assertControlsDoNotOverlap(s: BattleScreen, g: Game) {
        val controls = (0..3).toList() + (10..15).toList()
        val pad = 6f * g.ui.dp
        val left = g.insetL + pad
        val right = g.width - g.insetR - pad
        val bottom = g.height - g.insetB
        for (i in controls.indices) {
            val a = rect(s, controls[i])
            assertTrue("Przycisk ${controls[i]} poza lewą krawędzią", a.left >= left - 0.1f)
            assertTrue("Przycisk ${controls[i]} poza prawą krawędzią", a.right <= right + 0.1f)
            assertTrue("Przycisk ${controls[i]} poza dołem", a.bottom <= bottom + 0.1f)
            for (j in i + 1 until controls.size)
                assertFalse("Kolizja ${controls[i]} i ${controls[j]}", overlap(a, rect(s, controls[j])))
        }
        val pause = rect(s, 17)
        for (other in listOf(rect(s, 16), field(s, "goldRect"), field(s, "xpRect"), field(s, "mini"))) {
            if (other.width() > 0f)
                assertFalse("Kolizja z pauzą", overlap(pause, other))
        }
        for (i in 0..3) {
            val arrow = button(s, 70 + i)
            val own = rect(s, i)
            if (arrow.r.top > own.bottom) {
                // Zlecenie 119: na wąskim ekranie ulepszenie ma własny rząd.
                assertFalse("Ulepszenie nie może nakrywać jednostki", overlap(own, arrow.r))
                assertTrue("Ulepszenie musi być nad akcjami", arrow.r.bottom < rect(s, 10).top)
            } else {
                // Dawny układ: mała strzałka mieści się w przycisku jednostki.
                assertTrue("Strzałka nie leży w jednostce",
                    own.contains(arrow.r.centerX(), arrow.r.centerY()))
            }
            for (j in 0..3) if (i != j)
                assertFalse("Cel strzałki trafia sąsiednią jednostkę",
                    arrow.contains(rect(s, j).centerX(), rect(s, j).centerY()))
        }
    }

    @Test
    fun defaultLayoutIsUnchangedAndMirrorKeepsFieldGeometry() {
        val g = game(1600, 720)
        val s = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 110L, 1f))
        g.setScreen(s)
        val pad = 6f * g.ui.dp
        val left = g.insetL + pad
        val right = g.width - g.insetR - pad
        assertEquals(left, button(s, 0).r.left, 0.01f)
        assertEquals(right - 48f * g.ui.dp, button(s, 17).r.left, 0.01f)
        val ids = (0..3).toList() + (10..17).toList() + (70..73).toList()
        val old = ids.associateWith { rect(s, it) }
        val fieldBefore = field(s, "fieldRect")
        assertControlsDoNotOverlap(s, g)

        g.profile.leftHandedHud = true
        s.layout()
        assertEquals(left, button(s, 17).r.left, 0.01f)
        for (id in ids) {
            val prev = old.getValue(id)
            val next = rect(s, id)
            assertEquals("Odbicie X dla $id", left + right - prev.right, next.left, 0.01f)
            assertEquals("Zachowana wysokość dla $id", prev.top, next.top, 0.01f)
        }
        assertEquals(fieldBefore, field(s, "fieldRect"))
        assertControlsDoNotOverlap(s, g)

        g.profile.leftHandedHud = false
        s.layout()
        for (id in ids) assertEquals(old.getValue(id), rect(s, id))
    }

    @Test
    fun narrowScreenUsesSeparateUpgradeRowAndSafeInsets() {
        val g = game(720, 1280)
        g.setInsets(24, 10, 36, 24)
        g.profile.leftHandedHud = true
        val s = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 111L, 1f))
        g.setScreen(s)
        assertTrue("Jednostki powinny być w górnym rzędzie", button(s, 0).r.bottom < button(s, 10).r.top)
        assertTrue("Akcje powinny znaleźć się po lewej", button(s, 15).r.left < button(s, 0).r.left)
        assertTrue("Dolny pasek zasłania pole bitwy", field(s, "fieldRect").bottom <= button(s, 0).r.top)
        assertControlsDoNotOverlap(s, g)
        g.profile.leftHandedHud = false
        s.layout()
        assertTrue("Praworęczne jednostki muszą być nad ulepszeniami",
            button(s, 0).r.bottom < button(s, 70).r.top)
        assertTrue("Praworęczne ulepszenia muszą być nad akcjami",
            button(s, 70).r.bottom < button(s, 10).r.top)
        assertControlsDoNotOverlap(s, g)
    }

    /** Zlecenie 119: wszystkie główne cele HUD mają co najmniej 48 dp, bez nakładania. */
    @Test
    fun narrowHud119HasIndependent48DpTargets() {
        val devices = listOf(
            Triple(904, 2316, 2.7f), // zewnętrzny ekran Folda
            Triple(720, 1280, 2f), // telefon w pionie, z wycięciami
            Triple(800, 2000, 2.5f), // szerokość 320 dp
        )
        for ((width, height, density) in devices) for (leftHanded in listOf(false, true)) {
            val g = game(width, height, density)
            if (width == 720) g.setInsets(24, 10, 36, 24)
            g.profile.leftHandedHud = leftHanded
            val s = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 119L, 1f))
            g.setScreen(s)
            val dp = g.ui.dp
            val tag = "${width}x$height ${if (leftHanded) "lewa" else "prawa"}"
            val ids = (0..3).toList() + (10..17).toList() + (70..73).toList()
            val controls = ids.map { button(s, it) }
            val safe = RectF(g.insetL.toFloat(), g.insetT.toFloat(),
                (g.width - g.insetR).toFloat(), (g.height - g.insetB).toFloat())
            for (i in controls.indices) {
                val b = controls[i]
                assertTrue("$tag: szerokość ${b.id} = ${b.r.width() / dp} dp",
                    b.r.width() + 0.1f >= 48 * dp)
                assertTrue("$tag: wysokość ${b.id} = ${b.r.height() / dp} dp",
                    b.r.height() + 0.1f >= 48 * dp)
                assertTrue("$tag: poza bezpiecznym obszarem ${b.id}", safe.contains(b.r))
                for (j in i + 1 until controls.size)
                    assertFalse("$tag: kolizja ${b.id} i ${controls[j].id}",
                        overlap(b.r, controls[j].r))
            }
            for (i in 0..3) {
                assertTrue("$tag: jednostka/ulepszenie $i",
                    button(s, i).r.bottom < button(s, 70 + i).r.top)
                assertTrue("$tag: ulepszenie/akcja $i",
                    button(s, 70 + i).r.bottom < button(s, 10).r.top)
            }
            val area = field(s, "fieldRect")
            assertTrue("$tag: pole bitwy ma ${area.height() / g.height * 100f}% wysokości",
                area.height() >= g.height * 0.55f)
            assertControlsDoNotOverlap(s, g)
        }
    }

    /** Zmiana dotyczy tylko ekranów wąskich, nie rozłożonego Folda i poziomych. */
    @Test
    fun widerHud119KeepsExistingRows() {
        for ((width, height, density) in listOf(
            Triple(1600, 720, 2f),
            Triple(2400, 1080, 2.7f),
            Triple(2184, 1968, 2.7f),
            Triple(1968, 2184, 2.7f),
            Triple(1802, 2000, 3.6f),
        )) {
            val g = game(width, height, density)
            val s = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 1191L, 1f))
            g.setScreen(s)
            assertEquals("$width x $height: praworęczny HUD nadal jednorzędowy",
                button(s, 0).r.top, button(s, 10).r.top, 0.01f)
            g.profile.leftHandedHud = true
            s.layout()
            val dp = g.ui.dp
            val availableDp = (g.width - g.insetL - g.insetR) / dp - 12f
            val hasTwoRows = availableDp < 560f
            if (hasTwoRows)
                assertTrue("Wewnętrzny Fold zachowuje dwa rzędy leworęczne",
                    button(s, 0).r.bottom < button(s, 10).r.top)
            else
                assertEquals("$width x $height: leworęczny HUD nadal jednorzędowy",
                    button(s, 0).r.top, button(s, 10).r.top, 0.01f)
        }
    }

    @Test
    fun twoRowActionsStayAtLeftOnMediumWidth() {
        val g = game(1000, 1280)
        g.profile.leftHandedHud = true
        val s = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 113L, 1f))
        g.setScreen(s)
        assertEquals(g.insetL + 6f * g.ui.dp, button(s, 15).r.left, 0.01f)
        assertTrue(button(s, 0).r.left > button(s, 10).r.left)
        assertControlsDoNotOverlap(s, g)
    }

    /**
     * Drugi rząd paska górnego: liczebność armii nie koliduje z awansem, pauzą, minimapą,
     * pogodą ani złotem/XP; awans zostaje w pierwszym rzędzie w obu trybach.
     */
    @Test
    fun armyCountsDoNotCollideInBothModesOnFoldInnerAndPhone() {
        for ((w, h, d) in listOf(Triple(1802, 2000, 3.6f), Triple(1600, 720, 2f), Triple(720, 1280, 2f))) {
            for (left in listOf(false, true)) {
                val g = game(w, h, d)
                g.profile.leftHandedHud = left
                val s = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 114L, 1f))
                g.setScreen(s)
                g.frame(canvas, 1f / 60f)
                val tag = "${w}x$h ${if (left) "leworęczny" else "domyślny"}"
                val army = field(s, "armyRect")
                val mini = field(s, "mini")
                val evolve = rect(s, 16)
                val pause = rect(s, 17)
                assertTrue("$tag: minimapa", mini.width() > 0f)
                assertTrue("$tag: liczebność armii w drugim rzędzie", army.width() > 0f)
                assertTrue("$tag: awans w pierwszym rzędzie", evolve.bottom <= mini.top)
                assertTrue("$tag: liczebność w ekranie",
                    army.left >= g.insetL && army.right <= g.width - g.insetR)
                assertTrue("$tag: liczebność pod paskiem", army.bottom <= field(s, "topBar").bottom)
                for ((name, other) in listOf("awans" to evolve, "pauza" to pause, "minimapa" to mini,
                    "pogoda" to field(s, "weatherRect"), "złoto" to field(s, "goldRect"),
                    "XP" to field(s, "xpRect"))) {
                    if (other.width() > 0f)
                        assertFalse("$tag: kolizja liczebności armii z: $name", overlap(army, other))
                }
                assertFalse("$tag: awans nachodzi na pauzę", overlap(evolve, pause))
                if (left) {
                    // Pogoda i armia zamieniają się stronami względem minimapy.
                    assertTrue("$tag: armia po prawej", army.left >= mini.right)
                    assertTrue("$tag: pogoda po lewej", field(s, "weatherRect").right <= mini.left)
                } else {
                    assertTrue("$tag: armia po lewej", army.right <= mini.left)
                }
            }
        }
    }

    /** Przycisk ulepszenia nie może przechwytywać szkolenia jednostki. */
    @Test
    fun narrowHud119KeepsUnitTapSeparateFromUpgradeTap() {
        for (leftHanded in listOf(false, true)) {
            val g = game(904, 2316, 2.7f)
            g.profile.leftHandedHud = leftHanded
            val s = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 1192L, 1f))
            g.setScreen(s)
            g.frame(canvas, 1f / 60f)
            val unit = button(s, 0)
            val upgrade = button(s, 70)
            assertFalse("Szkolenie i ulepszanie mają oddzielne cele", overlap(unit.r, upgrade.r))

            fun tap(b: Button) {
                g.pushTouch(TouchEvent.DOWN, b.r.centerX(), b.r.centerY(), 0)
                g.pushTouch(TouchEvent.UP, b.r.centerX(), b.r.centerY(), 0)
                g.frame(canvas, 1f / 60f)
            }
            tap(unit)
            val controller = s.javaClass.getDeclaredField("commands").apply {
                isAccessible = true
            }.get(s) as BattleController
            assertEquals("Szkolenie ma trafić do logu komend", 1, controller.log().commands.size)
            tap(upgrade)
            val selected = s.javaClass.getDeclaredField("showUpgrade").apply {
                isAccessible = true
            }.getInt(s)
            assertEquals("Ulepszanie ma otworzyć odpowiedni panel", 0, selected)
        }
    }

    @Test
    fun accessibilitySwitchUpdatesProfile() {
        val g = game(720, 1280)
        val s = AccessibilityScreen(g)
        g.setScreen(s)
        s.onDrag(0f, -3000f)
        val toggle = s.buttons.first { it.id == 43 }
        assertTrue("Przełącznik musi być dostępny po przewinięciu", toggle.visible)
        g.pushTouch(TouchEvent.DOWN, toggle.r.centerX(), toggle.r.centerY(), 0)
        g.pushTouch(TouchEvent.UP, toggle.r.centerX(), toggle.r.centerY(), 0)
        g.frame(canvas, 1f / 60f)
        assertTrue(g.profile.leftHandedHud)
    }

    @Test
    fun mirroredTargetsStillIssueCommandsAndPause() {
        val g = game(720, 1280)
        g.profile.leftHandedHud = true
        val s = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 112L, 1f))
        g.setScreen(s)
        g.frame(canvas, 1f / 60f)
        val unit = button(s, 0)
        fun tap(b: Button) {
            g.pushTouch(TouchEvent.DOWN, b.r.centerX(), b.r.centerY(), 0)
            g.pushTouch(TouchEvent.UP, b.r.centerX(), b.r.centerY(), 0)
            g.frame(canvas, 1f / 60f)
        }
        tap(unit)
        val controller = s.javaClass.getDeclaredField("commands").apply {
            isAccessible = true
        }.get(s) as BattleController
        assertEquals(1, controller.log().commands.size)
        tap(button(s, 17))
        assertTrue("Przycisk pauzy nie zadziałał", s.javaClass.getDeclaredField("paused").apply {
            isAccessible = true
        }.getBoolean(s))
    }
}
