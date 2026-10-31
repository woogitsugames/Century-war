package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
import com.centurywar.game.*
import com.centurywar.meta.Profile
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HintOverlayTest {
    private fun game(): Game {
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val file = File.createTempFile("cw-hints", ".json").apply { delete() }
        return Game(host, Profile(file).apply { tutorialDone = true }, Quality.LOW).also {
            it.density = 2f
            it.resize(1600, 720, 1600, 720)
        }
    }

    private fun targets(): HintOverlay.Targets {
        val buttons = Array(9) { Button(it).apply { set(50f + it * 100f, 610f, 76f, 55f) } }
        return HintOverlay.Targets(
            Array(4) { buttons[it] }, buttons[4], buttons[5], buttons[6],
            buttons[7], buttons[8], RectF(20f, 20f, 160f, 60f),
            RectF(20f, 65f, 160f, 90f), RectF(350f, 30f, 950f, 55f),
            RectF(0f, 100f, 1600f, 590f),
        )
    }

    /** Każdy z 13 warunków musi wyświetlić wskazówkę dokładnie raz. */
    @Test
    fun eachBattleHintShowsExactlyOnce() {
        for (id in 0..HintOverlay.DAILY) {
            val g = game()
            val mode = when (id) {
                HintOverlay.CARD, HintOverlay.THEME, HintOverlay.BOSS -> Mode.ENDLESS
                HintOverlay.DAILY -> Mode.DAILY
                else -> Mode.SKIRMISH
            }
            val mods = when (id) {
                HintOverlay.MODIFIERS -> setOf(Modifier.DOUBLE_INCOME)
                HintOverlay.FOG -> setOf(Modifier.FOG_OF_WAR)
                else -> emptySet()
            }
            val b = Battle(BattleConfig(mode, id.toLong() + 7L, 1f, modifiers = mods))
            b.time = when (id) {
                HintOverlay.SIEGE -> Battle.SIEGE_START + 6f
                HintOverlay.DAILY -> 10f
                else -> 35f
            }
            val p = b.player
            when (id) {
                HintOverlay.UPGRADE -> p.gold = 99999f
                HintOverlay.CAP -> { p.aliveCount = p.cap; p.gold = 99999f }
                HintOverlay.RALLY, HintOverlay.HOLD -> {
                    p.rallyUnlocked = true
                    b.enemy.aliveCount = 4
                    repeat(3) { i ->
                        b.units[i].alive = true
                        b.units[i].side = 1
                        b.units[i].x = p.baseFront + 110f
                    }
                }
                HintOverlay.VETERAN -> { p.era = b.cfg.maxEra; p.rank = 1 }
                HintOverlay.CARD -> { b.wave = 4; b.waveTimer = 5f }
                HintOverlay.THEME -> {
                    b.wave = 1
                    b.waveTimer = 5f
                    b.nextWaveTheme = EndlessWaveTheme.RANGED_SWARM
                }
                HintOverlay.BOSS -> {
                    b.units[0].alive = true
                    b.units[0].side = 1
                    b.units[0].bossKind = 1
                }
                HintOverlay.OLD_TURRET -> { p.era = 1; p.turretEra[0] = 0 }
            }
            g.profile.hintsSeen = Profile.HINT_VALID_MASK and (1L shl id).inv()
            val hints = HintOverlay(g, b, targets(), id == HintOverlay.DAILY)
            hints.update(0.26f)
            assertEquals("Warunek dla wskazówki " + id, id, hints.activeId)
            assertTrue(g.profile.hintsSeen and (1L shl id) != 0L)
            hints.onAction()
            b.time += 25f
            hints.update(0.26f)
            assertEquals("Podpowiedź pokazana drugi raz: " + id, -1, hints.activeId)
        }
    }

    @Test
    fun minimumTwentySecondsBetweenHintsAndTimeout() {
        val g = game()
        val b = Battle(BattleConfig(Mode.SKIRMISH, 23L, 1f))
        b.time = 10f
        b.player.gold = 99999f
        g.profile.hintsSeen = Profile.HINT_VALID_MASK and
            ((1L shl HintOverlay.UPGRADE) or (1L shl HintOverlay.CAP)).inv()
        val h = HintOverlay(g, b, targets())
        h.update(0.3f)
        assertEquals(HintOverlay.UPGRADE, h.activeId)
        h.update(8.1f)
        assertEquals(-1, h.activeId)
        b.player.aliveCount = b.player.cap
        b.time = 29.9f
        h.update(0.3f)
        assertEquals(-1, h.activeId)
        b.time = 30f
        h.update(0.3f)
        assertEquals(HintOverlay.CAP, h.activeId)
    }

    @Test
    fun disabledTutorialChoiceAndPauseSuppressHints() {
        val g = game()
        val b = Battle(BattleConfig(Mode.SKIRMISH, 24L, 1f))
        b.player.gold = 99999f
        val h = HintOverlay(g, b, targets())
        h.update(0.3f, blocked = true)
        assertEquals(-1, h.activeId)
        b.pendingChoice = arrayOf(Doctrine.ECONOMY, Doctrine.FURY, Doctrine.ARSENAL)
        h.update(0.3f)
        assertEquals(-1, h.activeId)
        b.pendingChoice = null
        b.pendingCards = arrayOf(EndlessCard.ECONOMY, EndlessCard.ECONOMY, EndlessCard.ECONOMY)
        h.update(0.3f)
        assertEquals(-1, h.activeId)
        b.pendingCards = null
        g.profile.hintsEnabled = false
        h.update(0.3f)
        assertEquals(-1, h.activeId)
        g.profile.hintsEnabled = true
        h.update(0.3f)
        assertEquals(HintOverlay.UPGRADE, h.activeId)
        h.update(0.1f, blocked = true)
        assertEquals(-1, h.activeId)
    }

    @Test
    fun replayTutorialCreatesOverlayOnFirstCampaignStage() {
        val g = game()
        g.profile.replayTutorial()
        assertFalse(g.profile.tutorialDone)
        val bs = BattleScreen(g, Campaign.config(Campaign.stages[0], g.profile.bonuses(), 4L, true))
        val field = bs.javaClass.getDeclaredField("tut").apply { isAccessible = true }
        assertNotNull(field.get(bs))
        g.profile.tutorialDone = true
        val next = BattleScreen(g, Campaign.config(Campaign.stages[0], g.profile.bonuses(), 5L, false))
        assertNull(field.get(next))
    }

    @Test
    fun menuHintsAreOneTimeAndCanBeDisabled() {
        val g = game()
        val h = HintOverlay(g)
        for (id in HintOverlay.RESEARCH..HintOverlay.ENDLESS_MENU) {
            h.showScreen(id, RectF(40f, 40f, 320f, 95f))
            assertEquals(id, h.activeId)
            h.draw(Canvas(Bitmap.createBitmap(1600, 720, Bitmap.Config.ARGB_8888)))
            assertTrue(h.onClick(h.okBtn))
            h.showScreen(id, RectF(40f, 40f, 320f, 95f))
            assertEquals(-1, h.activeId)
        }
        g.profile.hintsEnabled = false
        g.profile.replayHints()
        h.showScreen(HintOverlay.RESEARCH, RectF(40f, 40f, 320f, 95f))
        assertEquals(-1, h.activeId)
    }
}
