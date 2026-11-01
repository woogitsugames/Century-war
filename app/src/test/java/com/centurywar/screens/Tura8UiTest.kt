package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.engine.Ui
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.BattleController
import com.centurywar.game.Campaign
import com.centurywar.game.Defs
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

/**
 * Tura 8: baner awansu (139), dymki podpowiedzi poza HUD i odstęp przycisków od lamówki paneli.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class Tura8UiTest {
    private val out = File("build/screenshots").apply { mkdirs() }

    /** Telefon 720p, 20:9 w poziomie, Fold zewnętrzny (pion i poziom), Fold wewnętrzny (pion i poziom). */
    private val devices = linkedMapOf(
        "phone720" to floatArrayOf(1600f, 720f, 1.8f),
        "phone20x9" to floatArrayOf(2400f, 1080f, 2.7f),
        "foldOuter" to floatArrayOf(904f, 2316f, 2.7f),
        "foldOuterLand" to floatArrayOf(2316f, 904f, 2.7f),
        "foldInner" to floatArrayOf(1802f, 2000f, 3.6f),
        "foldInnerLand" to floatArrayOf(2184f, 1968f, 2.7f),
    )

    private fun game(dims: FloatArray, leftHanded: Boolean = false, hints: Boolean = false): Game {
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val f = File.createTempFile("tura8", ".json").apply { delete() }
        val profile = Profile(f).apply {
            lang = "pl"; tutorialDone = true; hintsEnabled = hints; eraIntrosEnabled = false
            leftHandedHud = leftHanded
        }
        return Game(host, profile, Quality.LOW).apply {
            density = dims[2]
            resize(dims[0].toInt(), dims[1].toInt(), dims[0].toInt(), dims[1].toInt())
        }
    }

    private fun field(o: Any, name: String): Any? =
        o.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(o)

    private fun frame(g: Game) {
        val bmp = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
        try { g.frame(Canvas(bmp), 0f) } finally { bmp.recycle() }
    }

    private fun shot(g: Game, name: String) {
        val bmp = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
        try {
            g.frame(Canvas(bmp), 0f)
            File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bmp.recycle() }
    }

    private fun battle(g: Game, seed: Long = 13901L): BattleScreen {
        val screen = BattleScreen(g, BattleConfig(Mode.SKIRMISH, seed, 1f, startEra = 0, maxEra = 3))
        g.setScreen(screen)
        g.finishScreenTransition()
        return screen
    }

    /** Widoczne przyciski HUD (bez przycisków samego dymka i banera). */
    private fun hudButtons(screen: BattleScreen, except: Set<Button>): List<Button> =
        screen.buttons.filter { it.visible && it !in except && it.r.width() > 0f && it.r.height() > 0f }

    @Test
    fun evolveBannerAppearsOnceAndUsesEvolvePath() {
        for ((tag, dims) in devices) for (left in listOf(false, true)) {
            val g = game(dims, left)
            val screen = battle(g)
            val b = field(screen, "battle") as Battle
            val banner = screen.evolveBanner
            val where = "$tag/${if (left) "lewo" else "prawo"}"
            b.player.xp = Defs.evolveXp[0].toFloat()
            repeat(8) { screen.update(0.1f) }
            assertTrue("$where: baner nie pojawił się", banner.active && banner.goBtn.visible)
            assertEquals("$where: bitwa nie może stać", false, field(screen, "paused") as Boolean)
            val dp = g.ui.dp
            assertTrue("$where: przyciski < 48 dp",
                banner.goBtn.r.height() + 0.5f >= 48 * dp && banner.waitBtn.r.height() + 0.5f >= 48 * dp)
            val fieldRect = screen.fieldRect
            assertTrue("$where: baner poza polem bitwy", fieldRect.contains(banner.rect))
            for (other in hudButtons(screen, setOf(banner.goBtn, banner.waitBtn))) {
                assertFalse("$where: baner zasłania przycisk ${other.id}", RectF.intersects(banner.rect, other.r))
            }
            if (!left && (tag == "phone720" || tag == "foldOuter")) shot(g, "tura8_evolve_banner_$tag")

            // „Czekaj” zamyka bez awansu; baner nie wraca w tej epoce.
            screen.onClick(banner.waitBtn)
            assertFalse("$where: Czekaj nie zamknął", banner.active)
            assertEquals(0, b.player.era)
            repeat(20) { screen.update(0.1f) }
            assertFalse("$where: baner pojawił się drugi raz", banner.active)
            assertTrue("$where: Ewolucja powinna być dostępna", screen.evolveBtn.enabled)
        }
    }

    private fun logAfter(useBanner: Boolean): Replay.Log {
        val g = game(devices.getValue("phone720"))
        val screen = battle(g, 13902L)
        val b = field(screen, "battle") as Battle
        b.player.xp = Defs.evolveXp[0].toFloat()
        repeat(8) { screen.update(0.1f) }
        assertTrue(screen.evolveBanner.active)
        screen.onClick(if (useBanner) screen.evolveBanner.goBtn else screen.evolveBtn)
        assertEquals(1, b.player.era)
        assertFalse(screen.evolveBanner.active)
        return (field(screen, "commands") as BattleController).log()
    }

    @Test
    fun advanceButtonRecordsSameCommandAsEvolve() {
        val banner = logAfter(true)
        val direct = logAfter(false)
        assertEquals(direct.commands, banner.commands)
        assertEquals(Replay.encode(direct), Replay.encode(banner))
    }

    @Test
    fun bannerWaitsForModalAndFadesOut() {
        val g = game(devices.getValue("phone720"))
        val screen = battle(g)
        val b = field(screen, "battle") as Battle
        screen.onClick(screen.pauseBtn)
        b.player.xp = Defs.evolveXp[0].toFloat()
        repeat(10) { screen.update(0.1f) }
        assertFalse("Baner nie może startować pod pauzą", screen.evolveBanner.active)
        screen.onClick(screen.pauseMenu[0])
        repeat(8) { screen.update(0.1f) }
        assertTrue(screen.evolveBanner.active)
        repeat(100) { screen.update(0.1f) }
        assertFalse("Baner powinien zniknąć po ok. 8 s", screen.evolveBanner.active)
        assertEquals(0, b.player.era)
    }

    /** Dymek w bitwie nigdy nie przecina przycisku HUD, w każdym układzie paska. */
    @Test
    fun battleHintNeverCoversHudButtons() {
        val ids = intArrayOf(HintOverlay.UPGRADE, HintOverlay.CAP, HintOverlay.RALLY, HintOverlay.HOLD,
            HintOverlay.VETERAN, HintOverlay.SIEGE, HintOverlay.MODIFIERS, HintOverlay.FOG,
            HintOverlay.OLD_TURRET, HintOverlay.WEATHER)
        val keys = arrayOf("hint_upgrade", "hint_cap", "hint_rally", "hint_hold", "hint_veteran",
            "hint_siege", "hint_modifiers", "hint_fog", "hint_old_turret", "hint_weather54")
        val bmp = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        var drawn = 0
        for ((tag, dims) in devices) for (left in listOf(false, true)) {
            val g = game(dims, left, hints = true)
            val screen = battle(g)
            frame(g)
            val hints = field(screen, "hints") as HintOverlay
            val activate = HintOverlay::class.java.getDeclaredMethod("activate",
                Int::class.javaPrimitiveType, String::class.java).apply { isAccessible = true }
            for (i in ids.indices) {
                hints.dismiss()
                activate.invoke(hints, ids[i], keys[i])
                hints.draw(canvas)
                val panel = hints.panelRect
                val where = "$tag/${if (left) "lewo" else "prawo"}/${keys[i]}"
                assertTrue("$where: dymek się nie zmieścił", hints.activeId < 0 || !panel.isEmpty)
                if (panel.isEmpty) continue
                drawn++
                if (!left && ids[i] == HintOverlay.UPGRADE) shot(g, "tura8_hint_upgrade_$tag")
                assertTrue("$where: dymek poza polem bitwy", screen.fieldRect.contains(panel))
                for (b in hudButtons(screen, setOf(hints.okBtn))) {
                    assertFalse("$where: dymek zasłania przycisk ${b.id}", RectF.intersects(panel, b.r))
                }
            }
        }
        bmp.recycle()
        assertTrue("Za mało narysowanych dymków: $drawn", drawn >= devices.size * 2 * 6)
    }

    /** Dymek menu nie zasłania przycisku, na który wskazuje (Endless: „Graj”). */
    @Test
    fun menuHintDoesNotCoverTarget() {
        for ((tag, dims) in devices) {
            val g = game(dims, hints = true)
            val screen = ModeScreen(g, Mode.ENDLESS)
            g.setScreen(screen)
            g.finishScreenTransition()
            frame(g)
            frame(g)
            val hints = field(screen, "hints") as HintOverlay
            if (hints.activeId < 0) continue
            val start = field(screen, "start") as Button
            assertFalse("$tag: dymek zasłania Graj", RectF.intersects(hints.panelRect, start.r))
        }
    }

    private fun assertInsideTrim(g: Game, where: String, panel: RectF, buttons: List<Button>) {
        val dp = g.ui.dp
        val inner = g.ui.panelInner(panel, RectF())
        for (b in buttons) {
            if (!b.visible && b.r.isEmpty) continue
            val r = b.r
            assertTrue("$where: przycisk ${b.id} za blisko lewej lamówki (${(r.left - inner.left) / dp} dp)",
                r.left - inner.left >= 8 * dp - 0.5f)
            assertTrue("$where: przycisk ${b.id} za blisko prawej lamówki (${(inner.right - r.right) / dp} dp)",
                inner.right - r.right >= 8 * dp - 0.5f)
            assertTrue("$where: przycisk ${b.id} na dolnej lamówce (${(inner.bottom - r.bottom) / dp} dp)",
                inner.bottom - r.bottom >= Ui.PANEL_BUTTON_MARGIN * dp - 0.5f)
            assertTrue("$where: przycisk ${b.id} nad lamówką", r.top >= inner.top)
        }
    }

    /** Przyciski paneli mieszczą się w złotej lamówce z marginesem (pauza, ulepszenie, wyniki, kampania, tryby). */
    @Test
    fun panelButtonsStayInsideTrim() {
        for ((tag, dims) in devices) {
            val g = game(dims)
            val screen = battle(g)
            frame(g)
            @Suppress("UNCHECKED_CAST")
            assertInsideTrim(g, "$tag/pauza", field(screen, "pausePanel") as RectF,
                screen.pauseMenu)
            assertInsideTrim(g, "$tag/ulepszenie", field(screen, "upgradePanel") as RectF,
                listOf(screen.upgradeBuy))
            assertInsideTrim(g, "$tag/wyniki", field(screen, "resultPanel") as RectF,
                listOf(screen.restartEnd, screen.replayEnd, screen.continueBtn,
                    screen.resultStatsTab, screen.resultChartTab))

            val campaign = CampaignScreen(g)
            g.setScreen(campaign)
            g.finishScreenTransition()
            campaign.javaClass.getDeclaredField("selected").apply { isAccessible = true }
                .set(campaign, Campaign.stages[2])
            frame(g)
            val start = field(campaign, "start") as Button
            @Suppress("UNCHECKED_CAST")
            val diff = (field(campaign, "difficultyButtons") as Array<Button>).toList()
            assertInsideTrim(g, "$tag/kampania", field(campaign, "info") as RectF, diff + start)
            for (b in diff + start) assertTrue("$tag/kampania: przycisk ${b.id} < 48 dp",
                b.r.height() + 0.5f >= 48 * g.ui.dp)

            for (mode in listOf(Mode.ENDLESS, Mode.SKIRMISH)) {
                val ms = ModeScreen(g, mode)
                g.setScreen(ms)
                g.finishScreenTransition()
                frame(g)
                assertInsideTrim(g, "$tag/${mode.name}", field(ms, "panel") as RectF,
                    listOf(field(ms, "start") as Button))
            }
        }
        L.lang = L.PL
    }
}
