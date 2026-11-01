package com.centurywar.screens

import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.engine.TouchEvent
import com.centurywar.game.Mode
import com.centurywar.meta.Profile
import com.centurywar.meta.WhatsNewCatalog
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Ścieżka od czystego profilu do samouczka — rzeczywiste zdarzenia DOWN/UP ekranu. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FirstMinutesTest {
    private val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
    }

    private fun profile(): Profile {
        val file = File.createTempFile("cw-first-minutes", ".json")
        file.delete()
        return Profile(file)
    }

    private fun game(p: Profile): Game = Game(host, p, Quality.LOW).also {
        it.density = 2f
        it.resize(1600, 720, 1600, 720)
    }

    private fun tap(g: Game, id: Int) {
        val screen = requireNotNull(g.current)
        val button = screen.buttons.first { it.id == id }
        assertTrue("Przycisk $id jest ukryty", button.visible)
        assertTrue("Przycisk $id jest nieaktywny", button.enabled)
        val x = button.r.centerX()
        val y = button.r.centerY()
        assertTrue(button.r.width() > 0f && button.r.height() > 0f)
        screen.handle(TouchEvent().apply {
            action = TouchEvent.DOWN; this.x = x; this.y = y; pointer = 0
        })
        screen.handle(TouchEvent().apply {
            action = TouchEvent.UP; this.x = x; this.y = y; pointer = 0
        })
    }

    @Test fun freshInstallStartsTutorialAfterOneTouch() {
        val p = profile()
        val g = game(p)
        val screens = mutableListOf(requireNotNull(g.current).crashName)
        assertEquals(listOf("MenuScreen"), screens)
        assertFalse(p.hasExistingSave)
        assertFalse(WhatsNewCatalog.shouldShow(false, "", "0.1.0"))

        val menu = g.current as MenuScreen
        menu.update(0f)
        val campaign = menu.buttons.first { it.id == 1 }
        assertEquals(L["first_play"], campaign.label)
        assertEquals(L["first_goal"], campaign.sub)

        tap(g, 1)
        screens.add(requireNotNull(g.current).crashName)
        assertEquals(listOf("MenuScreen", "BattleScreen"), screens)
        val battle = g.current as BattleScreen
        assertEquals(Mode.CAMPAIGN, battle.cfg.mode)
        assertEquals(0, battle.cfg.stageIndex)
        assertTrue(battle.cfg.tutorial)
        assertNotNull(battle.tut)
        assertEquals(1L, p.eraIntrosSeen and 1L)
    }

    @Test fun experiencedPlayerStillOpensCampaignMap() {
        val p = profile().apply { battles = 3; wins = 2; tutorialDone = true }
        val g = game(p)
        val campaign = (g.current as MenuScreen).buttons.first { it.id == 1 }
        assertEquals(L["campaign"], campaign.label)

        tap(g, 1)
        assertTrue(g.current is CampaignScreen)
    }

    @Test fun oneTimeNextStepHintAppearsAfterFirstBattleEvenOnDefeat() {
        val p = profile().apply {
            battles = 1
            wins = 0
            tutorialDone = false
            eraIntrosEnabled = false
        }
        val g = game(p)
        val first = CampaignScreen(g)
        g.setScreen(first)
        first.update(1f)

        val bit = 1L shl HintOverlay.FIRST_BATTLE
        assertEquals(bit, p.hintsSeen and bit)
        val ok = first.buttons.first { it.id == HintOverlay.OK_ID }
        assertTrue(ok.visible)
        first.onClick(ok)
        assertFalse(ok.visible)

        Profile.awaitHintWrites()
        val second = CampaignScreen(g)
        g.setScreen(second)
        second.update(1f)
        assertFalse(second.buttons.first { it.id == HintOverlay.OK_ID }.visible)
        assertEquals(bit, p.hintsSeen and bit)
    }
}
