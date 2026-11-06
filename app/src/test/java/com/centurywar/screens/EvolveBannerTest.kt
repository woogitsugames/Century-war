package com.centurywar.screens

import android.graphics.RectF
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.game.BattleConfig
import com.centurywar.game.Defs
import com.centurywar.game.Mode
import com.centurywar.meta.Profile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EvolveBannerTest {
    private var savedLang = 0
    @Before fun rememberLanguage() { savedLang = L.lang; L.setCode("pl") }
    @After fun restoreLanguage() { L.lang = savedLang }

    private fun screen(w: Int = 1600, h: Int = 720, density: Float = 2f): Pair<Game, BattleScreen> {
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val file = File.createTempFile("banner179", ".json").apply { delete() }
        val g = Game(host, Profile(file).apply {
            tutorialDone = true; hintsEnabled = false; eraIntrosEnabled = false
        }, Quality.LOW)
        g.density = density
        g.resize(w, h, w, h)
        if (w == 904) g.setInsets(0, 88, 0, 0)
        val s = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 179L, 1f))
        g.setScreen(s)
        return g to s
    }

    private fun text(b: EvolveBanner, name: String) =
        b.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(b) as String

    @Test fun showOncePerEraAndDismiss() {
        val (_, s) = screen()
        val b = s.evolveBanner
        b.update(EvolveBanner.DELAY - .01f, 0, 8, true, false)
        assertFalse(b.active)
        b.update(.02f, 0, 8, true, false)
        assertTrue(b.active)
        assertEquals(0, b.shownForEra)
        assertTrue(b.goBtn.visible); assertTrue(b.waitBtn.visible)
        b.dismiss()
        assertFalse(b.active); assertFalse(b.goBtn.visible); assertFalse(b.waitBtn.visible)
        b.update(10f, 0, 8, true, false)
        assertFalse(b.active)
        b.update(EvolveBanner.DELAY, 1, 8, true, false)
        assertTrue(b.active)
        assertEquals(1, b.shownForEra)
    }

    @Test fun blockedAndUnavailableDoNotStartAndActiveTimerPauses() {
        val (_, s) = screen()
        val b = s.evolveBanner
        b.update(10f, 0, 8, true, true)
        assertFalse(b.active)
        b.update(10f, 0, 8, false, false)
        assertFalse(b.active)
        b.update(10f, 8, 8, true, false)
        assertFalse(b.active)
        b.update(EvolveBanner.DELAY, 0, 8, true, false)
        assertTrue(b.active)
        b.update(100f, 0, 8, true, true)
        assertTrue(b.active); assertFalse(b.goBtn.visible); assertFalse(b.waitBtn.visible)
        b.update(.01f, 0, 8, true, false)
        assertTrue(b.goBtn.visible)
        b.update(EvolveBanner.SHOW_TIME + EvolveBanner.FADE_TIME, 0, 8, true, false)
        assertFalse(b.active)
    }

    @Test fun languageRefreshUpdatesLabelsAndEraSubtitle() {
        val (_, s) = screen()
        val b = s.evolveBanner
        b.update(EvolveBanner.DELAY, 0, 8, true, false)
        L.setCode("en")
        b.refreshTexts()
        assertEquals(L["evo139_title"], text(b, "title"))
        assertEquals(L.f("evo139_next", L[Defs.eraKeys[1]]), text(b, "subtitle"))
        assertEquals(L["evo139_go"], b.goBtn.label)
        assertEquals(L["evo139_wait"], b.waitBtn.label)
    }

    @Test fun bannerAndButtonsFitFieldOnPhoneAndFold() {
        for ((w, h, density) in listOf(Triple(1600, 720, 2f), Triple(904, 2316, 2.7f))) {
            val (g, s) = screen(w, h, density)
            val b = s.evolveBanner
            b.update(EvolveBanner.DELAY, 0, 8, true, false)
            b.layout()
            val tag = "${w}x$h"
            assertFalse("$tag: pusty baner", b.rect.isEmpty)
            assertTrue("$tag: baner poza polem ${b.rect} / ${s.fieldRect}", s.fieldRect.contains(b.rect))
            for (button in listOf(b.goBtn, b.waitBtn)) {
                assertTrue("$tag: przycisk poza banerem", b.rect.contains(button.r))
                assertTrue("$tag: szerokość przycisku", button.r.width() + .1f >= 48 * g.ui.dp)
                assertTrue("$tag: wysokość przycisku", button.r.height() + .1f >= 48 * g.ui.dp)
            }
            assertFalse("$tag: przyciski zachodzą na siebie", RectF.intersects(b.goBtn.r, b.waitBtn.r))
        }
    }
}
