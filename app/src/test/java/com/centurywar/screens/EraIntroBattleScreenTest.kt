package com.centurywar.screens

import android.graphics.Canvas
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
import com.centurywar.engine.TouchEvent
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.BattleController
import com.centurywar.game.Defs
import com.centurywar.game.Mode
import com.centurywar.game.Replay
import com.centurywar.game.Role
import com.centurywar.meta.Profile
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EraIntroBattleScreenTest {

    private fun game(profile: Profile): Game {
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        return Game(host, profile, Quality.LOW).apply {
            density = 2f
            resize(1600, 720, 1600, 720)
        }
    }

    private fun profile() = Profile(File.createTempFile("era-intro", ".json").apply { delete() })
    private fun field(obj: Any, name: String): Any? =
        obj.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(obj)

    @Test
    fun introAppearsOnceAndPersistsAcrossOldProfileFormats() {
        val p = profile()
        val g = game(p)
        val intro = EraIntroOverlay.showIfFirst(g, 3)
        assertNotNull(intro)
        assertNull(EraIntroOverlay.showIfFirst(g, 3))
        assertEquals(1L shl 3, p.eraIntrosSeen)
        val fromDisk = profile()
        fromDisk.fromJson(p.toJson())
        assertNull(EraIntroOverlay.showIfFirst(game(fromDisk), 3))
        assertNotNull(EraIntroOverlay.showIfFirst(game(fromDisk), 4))

        // Przed zleceniem 68 pole nie istniało; brak pola nie wyłącza zapowiedzi.
        val old = JSONObject(p.toJson()).apply {
            remove("eraIntrosSeen")
            remove("eraIntrosEnabled")
        }
        val migrated = profile()
        migrated.fromJson(old.toString())
        assertEquals(0L, migrated.eraIntrosSeen)
        assertTrue(migrated.eraIntrosEnabled)

        p.eraIntrosEnabled = false
        assertNull(EraIntroOverlay.showIfFirst(g, 5))
        assertEquals(1L shl 3, p.eraIntrosSeen)
        p.eraIntrosEnabled = true
        p.replayEraIntros()
        assertNotNull(EraIntroOverlay.showIfFirst(g, 3))
        assertNull(EraIntroOverlay.showIfFirst(g, -1))
        assertNull(EraIntroOverlay.showIfFirst(g, Defs.ERA_COUNT))
        val tampered = JSONObject(p.toJson()).put("eraIntrosSeen", Long.MAX_VALUE)
        migrated.fromJson(tampered.toString())
        assertEquals((1L shl Defs.ERA_COUNT) - 1L, migrated.eraIntrosSeen)
    }

    /** Te same polecenia trafiają do powtórki z planszą i bez niej. */
    private fun logAfterEvolve(withIntro: Boolean): Replay.Log {
        val p = profile().apply {
            tutorialDone = true
            hintsEnabled = false
            eraIntrosEnabled = withIntro
        }
        val g = game(p)
        val screen = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 6801L, 1f, startEra = 0, maxEra = 1))
        g.setScreen(screen)
        val battle = field(screen, "battle") as Battle
        val controller = field(screen, "commands") as BattleController
        val evolve = field(screen, "evolveBtn") as Button
        battle.player.xp = Defs.evolveXp[0].toFloat()
        screen.update(0f)
        assertTrue(evolve.enabled)
        screen.onClick(evolve)
        assertEquals(1, battle.player.era)
        val intro = field(screen, "eraIntro") as EraIntroOverlay?
        assertEquals(withIntro, intro?.active == true)

        // Bez kroku symulacji: wybór doktryny, potem trening jednostki.
        if (battle.pendingVariant != null) assertTrue(controller.variant(0))
        if (battle.pendingChoice != null) assertTrue(controller.doctrine(0))
        if (battle.pendingCards != null) assertTrue(controller.card(0))
        assertTrue(controller.queue(Role.MELEE))

        if (withIntro) {
            val before = battle.stepCount
            repeat(35) { screen.update(0.025f) }
            assertEquals(before, battle.stepCount)
            assertTrue(intro?.active == true)
            val tap = TouchEvent().apply { action = TouchEvent.UP }
            assertTrue(screen.onTouch(tap))
            assertFalse(intro?.active == true)
            assertEquals(before, battle.stepCount)
        }
        return controller.log()
    }

    @Test
    fun introDoesNotChangeCommandLogOrReplay() {
        val with = logAfterEvolve(true)
        val without = logAfterEvolve(false)
        assertEquals(without.steps, with.steps)
        assertEquals(without.commands, with.commands)
        assertEquals(Replay.encode(without), Replay.encode(with))
    }

    @Test
    fun unlockedCampaignEraSharesSeenFlag() {
        val p = profile().apply {
            hintsEnabled = false
            stars[0] = 3
            stars[1] = 3
            stars[2] = 3
            stars[3] = 3
        }
        val g = game(p)
        val map = CampaignScreen(g)
        g.setScreen(map)
        map.update(0.016f)
        assertEquals(1L shl 1, p.eraIntrosSeen)
        assertTrue((field(map, "eraIntro") as EraIntroOverlay).active)
        assertTrue(map.onTouch(TouchEvent().apply { action = TouchEvent.UP }))
        assertNull(EraIntroOverlay.showIfFirst(g, 1))
    }
}
