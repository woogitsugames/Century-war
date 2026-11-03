package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.engine.Screen
import com.centurywar.engine.TouchEvent
import com.centurywar.game.Ai
import com.centurywar.game.Campaign
import com.centurywar.game.Mode
import com.centurywar.game.Research
import com.centurywar.game.SnapshotStore
import com.centurywar.meta.BattleHistory
import com.centurywar.meta.Profile
import com.centurywar.meta.ReplayStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale

/**
 * Pierwsza godzina na telefonie (pl, en) i przerwany samouczek. Ścieżka i narzędzia:
 * [FirstHourJourneyBase]; Fold zewnętrzny: [FirstHourJourneyFoldTest] (noc 9: podział
 * dawnej pętli po urządzeniach na dwie klasy, żeby każdy proces Gradle dostał połowę).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class FirstHourJourneyTest : FirstHourJourneyBase() {
    @Test
    fun firstHour() = firstHourOn("phone", "fh_audit.txt")

    /**
     * Aplikacja zabita w tle w połowie samouczka: po ponownym uruchomieniu menu pyta o wznowienie,
     * bitwa kończy się bez nakładki samouczka i rozlicza raz; kolejny start nie oferuje jej znowu.
     * Odmowa wznowienia przywraca samouczek od początku.
     */
    @Test
    fun tutorialInterruptedResumedSettlesOnce() {
        val old = L.lang
        tag = "przerwanie"
        reported.clear()
        try {
            val d = devices.getValue("phone")
            val dir = folder.newFolder("run1")
            val g1 = game(d, "pl", dir)
            tick(g1, 0.1f, 3)
            tap(g1, button(g1.current!!, 1), "graj")
            val bs = g1.current as BattleScreen
            val tut = requireNotNull(bs.tut)
            // Gracz przechodzi kilka kroków, potem ekran gaśnie (onPause).
            var guard = 0
            while (tut.step < TutorialOverlay.S_KILL && guard++ < 4000) {
                if (tut.showing && tut.step == TutorialOverlay.S_MELEE) tap(g1, bs.unitBtn[0], "piechota")
                else if (tut.showing && tut.step == TutorialOverlay.S_RANGED) tap(g1, bs.unitBtn[1], "strzelec")
                else if (tut.showing && tut.nextBtn.visible) tap(g1, tut.nextBtn, "dalej")
                else tick(g1, 0.2f)
            }
            assertTrue("samouczek nie doszedł do kroku zabicia", tut.step >= TutorialOverlay.S_KILL)
            val medals0 = g1.profile.medals
            bs.onPause()
            tick(g1)
            assertTrue("pauza nie zatrzymała bitwy", bs.paused)
            if (tut.skipBtn.visible || tut.nextBtn.visible) problem("przyciski samouczka pod pauzą")
            flush(g1)
            val killed = copyDir(dir, "killed")

            // Drugi start: menu z pytaniem o wznowienie.
            val dir2 = copyDir(killed, "run2")
            val g2 = game(d, "pl", dir2)
            val restored = g2.snapshots!!.loadBattle(g2.profile.lastSettledBattle)
            assertNotNull("brak zrzutu przerwanego samouczka", restored)
            assertTrue(restored!!.battle.cfg.tutorial)
            g2.resumeOffer = restored
            tick(g2, 0.1f, 3)
            val menu = g2.current as MenuScreen
            shot(g2, "menu_wznow")
            hintsOf(menu)?.let { if (it.activeId >= 0) problem("dymek menu nad pytaniem o wznowienie") }
            val yes = button(menu, 20)
            assertTrue("pytanie o wznowienie bez przycisku", yes.visible && yes.enabled)
            if (menu.accessibilityLabel(yes).isBlank()) problem("„Wznów” bez opisu TalkBack")
            tap(g2, yes, "wznów")
            val bs2 = g2.current as BattleScreen
            assertNull("wznowiony samouczek odtwarza nakładkę od zera", bs2.tut)
            assertTrue("wznowiona bitwa nie startuje w pauzie", bs2.paused)
            tick(g2, 0.1f, 2)
            shot(g2, "wznowiona_pauza")
            tap(g2, bs2.pauseMenu[0], "wznów z pauzy")
            assertFalse(bs2.paused)
            val o = playWithAi(g2, bs2, "wznowiony samouczek")
            flush(g2)
            val gained = bs2.totalMedalsGained
            assertTrue("wznowiona bitwa bez nagrody", gained > 0 || !o.won)
            assertEquals("rozliczenie wznowionego samouczka", medals0 + gained, g2.profile.medals)
            assertEquals(1, g2.profile.battles)
            if (o.won) assertTrue("wygrany wznowiony samouczek nie kończy samouczka", g2.profile.tutorialDone)
            // „Dalej” prowadzi na mapę; Graj nie uruchamia samouczka ponownie po wygranej.
            tap(g2, bs2.continueBtn, "dalej")
            tick(g2, 0.2f, 4)
            assertTrue(g2.current is CampaignScreen)

            // Trzeci start: nic do wznowienia, medale bez zmian (rozliczenie raz).
            val g3 = game(d, "pl", dir2)
            assertNull("rozliczona bitwa nadal do wznowienia", g3.snapshots!!.loadBattle(g3.profile.lastSettledBattle))
            assertEquals(g2.profile.medals, g3.profile.medals)
            assertEquals(1, g3.profile.battles)

            // Odmowa wznowienia: zrzut znika, Graj uruchamia samouczek od nowa.
            val dir4 = copyDir(killed, "run4")
            val g4 = game(d, "pl", dir4)
            g4.resumeOffer = g4.snapshots!!.loadBattle(g4.profile.lastSettledBattle)
            tick(g4, 0.1f, 3)
            tap(g4, button(g4.current!!, 21), "nie wznawiaj")
            assertNull(g4.resumeOffer)
            flush(g4)
            assertNull(g4.snapshots!!.loadBattle(g4.profile.lastSettledBattle))
            assertEquals(medals0, g4.profile.medals)
            tap(g4, button(g4.current!!, 1), "graj ponownie")
            val bs4 = g4.current as? BattleScreen
            assertTrue("po odmowie Graj nie wraca do samouczka", bs4?.tut != null)
        } finally {
            L.lang = old
        }
        assertTrue("Przerwanie samouczka — problemy:\n$problems", problems.isEmpty())
    }
}
