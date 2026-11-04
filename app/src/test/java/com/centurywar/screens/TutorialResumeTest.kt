package com.centurywar.screens

import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.GameAccessibility.Companion.BTN_BASE
import com.centurywar.engine.GameAccessibility.Companion.INFO_TUTORIAL
import com.centurywar.engine.GameView
import com.centurywar.engine.L
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Backlog R10 poz. 6: proces zabity w różnych krokach samouczka. Po „Wznów” nakładka wraca
 * do przerwanego kroku (bez powtarzania wcześniejszych dymków), krok przepuszcza tylko swój
 * przycisk (także w TalkBack), a samouczek zalicza się dokładnie raz.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class TutorialResumeTest : FirstHourJourneyBase() {
    @Test fun killedDuringRangedStep() = killAndResume(TutorialOverlay.S_RANGED, "strzelec") { it.unitBtn[1] }

    @Test fun killedDuringTurretStep() = killAndResume(TutorialOverlay.S_TURRET, "wieza") { it.turretBtn }

    @Test fun killedDuringBarrageStep() = killAndResume(TutorialOverlay.S_BARRAGE, "ostrzal") { it.barrageBtn }

    /**
     * Armia w Trzymaj w chwili zabicia (gracz nacisnął postawę w kroku postawy, ale nie wrócił do
     * Naprzód): krok postawy wraca i prowadzi z powrotem do Naprzód. Na tym ziarnie krok postawy
     * nie doczekałby się wrogów, więc test ustawia Trzymaj zaraz po wejściu w krok.
     */
    @Test fun killedDuringStanceStepWithArmyHolding() =
        killAndResume(TutorialOverlay.S_STANCE, "postawa", holding = true) { it.stanceBtn }

    /** Krok jest zapisywany na dysk, nie idzie do chmury, a zapis z chmury go nie kasuje. */
    @Test fun stepPersistsLocallyAndIgnoresCloud() {
        val file = folder.newFile("p.json").apply { delete() }
        val p = Profile(file)
        assertEquals(0, p.tutorialResumeStep(42L))
        p.rememberTutorialStep(42L, TutorialOverlay.S_TURRET)
        Profile.awaitHintWrites()
        val loaded = Profile(file).apply { load() }
        assertEquals(TutorialOverlay.S_TURRET, loaded.tutorialResumeStep(42L))
        assertEquals("inna bitwa zaczyna od pierwszego kroku", 0, loaded.tutorialResumeStep(43L))
        assertEquals(0, loaded.tutorialResumeStep(0L))
        val cloud = JSONObject(loaded.toCloudJson())
        assertFalse(cloud.has("tutorialStep") || cloud.has("tutorialStepBattle"))
        loaded.fromJson(cloud.put("tutorialStep", 1).put("tutorialStepBattle", 7L).toString())
        assertEquals(TutorialOverlay.S_TURRET, loaded.tutorialResumeStep(42L))
        assertEquals(0, loaded.tutorialResumeStep(7L))
    }

    private fun killAndResume(step: Int, name: String, holding: Boolean = false, target: (BattleScreen) -> Button) {
        val old = L.lang
        tag = "wznowienie_$name"
        reported.clear()
        try {
            val d = devices.getValue("phone")
            val dir = folder.newFolder("run_$name")
            val g1 = game(d, "pl", dir)
            tick(g1, 0.1f, 3)
            tap(g1, button(g1.current!!, 1), "graj")
            val bs = g1.current as BattleScreen
            val tut = requireNotNull(bs.tut)
            // Bez dokupowania między krokami: inaczej krok strzelca zalicza się sam, zanim się pokaże.
            playTutorial(g1, bs, {
                tut.step > step || (tut.step == step && (holding || tut.showing))
            }, buyBetween = { tut.step >= TutorialOverlay.S_KILL })
            assertEquals("samouczek nie doszedł do kroku $step", step, tut.step)
            if (holding) bs.commands.stance(true) else assertTrue(tut.showing)
            assertEquals(holding, bs.battle.player.hold)
            val medals0 = g1.profile.medals
            bs.onPause()
            tick(g1)
            flush(g1)

            val g2 = game(d, "pl", copyDir(dir, "run2_$name"))
            assertEquals("krok samouczka nie trafił do profilu", step, g2.profile.tutorialStep)
            g2.resumeOffer = requireNotNull(g2.snapshots!!.loadBattle(g2.profile.lastSettledBattle))
            tick(g2, 0.1f, 3)
            tap(g2, button(g2.current!!, 20), "wznów")
            val bs2 = g2.current as BattleScreen
            val tut2 = requireNotNull(bs2.tut) { "wznowiony samouczek bez nakładki" }
            assertEquals(step, tut2.step)
            assertTrue(bs2.paused)
            assertEquals(holding, bs2.battle.player.hold)
            tap(g2, bs2.pauseMenu[0], "wznów z pauzy")
            var guard = 0
            while (!tut2.showing && guard++ < 600) tick(g2, 0.1f)
            assertTrue("przerwany krok $step nie wraca po wznowieniu", tut2.showing)
            assertEquals("po wznowieniu pokazano inny krok", step, tut2.step)
            shot(g2, "krok_$step")

            // Krok przepuszcza tylko swój przycisk HUD: dotyk i TalkBack.
            val goal = target(bs2)
            assertTrue(tut2.allows(goal))
            val blocked = listOf(bs2.unitBtn[0], bs2.unitBtn[1], bs2.turretBtn, bs2.armoryBtn, bs2.stanceBtn)
                .filter { it !== goal }
            for (b in blocked) assertFalse("krok $step przepuszcza przycisk #${b.id}", tut2.allows(b))
            checkTalkBack(g2, bs2, goal, blocked, step)

            playTutorial(g2, bs2)
            assertTrue("wznowiony samouczek nie zalicza się", tut2.finished && g2.profile.tutorialDone)
            assertFalse("krok postawy zostawił armię w Trzymaj", holding && bs2.battle.player.hold && !bs2.battle.over)
            val o = playWithAi(g2, bs2, "wznowiony samouczek")
            flush(g2)
            assertEquals("rozliczenie wznowionego samouczka", medals0 + bs2.totalMedalsGained, g2.profile.medals)
            assertEquals(1, g2.profile.battles)
            if (step == TutorialOverlay.S_TURRET && o.won)
                assertTrue("osiągnięcie pierwszej wieży po wznowieniu", "first_turret" in g2.profile.unlockedAchievements)
            tap(g2, bs2.continueBtn, "dalej")
            tick(g2, 0.2f, 4)
            flush(g2)

            // Samouczek zaliczony raz: kolejne wejście w etap 1 nie tworzy nakładki.
            val g3 = game(d, "pl", copyDir(folder.root.resolve("run2_$name"), "run3_$name"))
            assertTrue(g3.profile.tutorialDone)
            assertNull(g3.snapshots!!.loadBattle(g3.profile.lastSettledBattle))
        } finally {
            L.lang = old
        }
        assertTrue("Wznowienie samouczka — problemy:\n$problems", problems.isEmpty())
    }

    private fun checkTalkBack(g: Game, bs: BattleScreen, goal: Button, blocked: List<Button>, step: Int) {
        val view = GameView(RuntimeEnvironment.getApplication(), g)
        view.layout(0, 0, g.width, g.height)
        var now = 1_000_000L
        view.setAccessibilityClock { now }
        now += 10_000L
        view.refreshAccessibilityNodes()
        val provider = view.accessibilityNodeProvider!!
        val info = provider.createAccessibilityNodeInfo(INFO_TUTORIAL)
        assertNotNull("TalkBack nie czyta kroku $step", info)
        assertTrue(info!!.contentDescription.toString().isNotBlank())
        assertNotNull("TalkBack ukrywa cel kroku $step", provider.createAccessibilityNodeInfo(BTN_BASE + goal.id))
        for (b in blocked) assertNull("TalkBack pokazuje zablokowany przycisk #${b.id} w kroku $step",
            provider.createAccessibilityNodeInfo(BTN_BASE + b.id))
        assertEquals(step, bs.tut!!.step)
    }
}
