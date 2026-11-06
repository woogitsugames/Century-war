package com.centurywar.screens

import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.game.BattleConfig
import com.centurywar.game.Mode
import com.centurywar.meta.Profile
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Pomiar tym samym Ui.measure/fit i geometrią co podpis zbrojowni. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BattleShortLabels194Test {
    private val savedLang = L.lang
    @After fun restoreLang() { L.lang = savedLang }

    @Test fun armoryMeasurementsForBothDevicesAndScales() {
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val devices = listOf(Triple(1600, 720, 1.8f), Triple(904, 2316, 2.7f))
        for ((w, h, density) in devices) for (scale in listOf(100, 130)) for (lang in L.CODES) {
            val file = File.createTempFile("hud194", ".json").apply { delete() }
            val profile = Profile(file).apply {
                this.lang = lang; textScale = scale; tutorialDone = true
                hintsEnabled = false; hintsSeen = Profile.HINT_VALID_MASK
            }
            val g = Game(host, profile, Quality.LOW)
            g.density = density
            g.resize(w, h, w, h)
            val s = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 194L, 1f))
            g.setScreen(s)
            val ui = g.ui
            val available = s.armoryBtn.r.width() - 8f * ui.dp
            val old = L["battle84_armory_short"]
            val full = L["armory"]
            val fitted = ui.fit(old, 10f, available)
            val width = ui.measure(old, fitted, true)
            println("194|$w|$h|$scale|$lang|$old|$full|${ui.measure(full, 10f, true) / ui.dp}|${ui.measure(full, ui.fit(full, 10f, available), true) / ui.dp}|${width / ui.dp}|${available / ui.dp}")
            assertTrue("Niepoprawna szerokość przycisku $w $h $lang", available > 0f)
            assertTrue("Nie mieści się obecny podpis $w $h $scale $lang", width <= available + 0.1f * ui.dp)
        }
    }
}
