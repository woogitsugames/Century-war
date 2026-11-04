package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.RectF
import com.centurywar.engine.Game
import com.centurywar.engine.L
import com.centurywar.game.BattleConfig
import com.centurywar.game.Daily
import com.centurywar.game.Mode
import com.centurywar.game.Weekly
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * R11-03: wstęga kamienia milowego (próg tygodnia z bossem fali 10, rekord fali) i wiersz
 * w pauzie na telefonie 720p i zewnętrznym Foldzie (pl, en, de). Wstęga nie nachodzi na pasek
 * bossa, wiersz „Następna fala” ani przyciski, znika po ≤ 4 s; nagrody nie zmieniają się w bitwie.
 * Zrzuty: build/screenshots/r11_03_<urządzenie>_<język>_<scena>.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class EndlessMilestonesScreenTest : PrereleasePolishBase() {
    private val shown = listOf("phone720", "foldOuter")
    private val week = Weekly.weekNumber(Daily.RELEASE_DAY) + 3L

    private fun save(g: Game, name: String) {
        g.finishScreenTransition()
        val bmp = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
        try {
            val c = TextCanvas(bmp)
            g.frame(c, 0f)
            lastTexts = ArrayList(c.texts)
            File(out, "r11_03_$name.png").outputStream().use {
                check(bmp.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { bmp.recycle() }
    }

    private fun overlap(a: RectF, b: RectF) =
        !a.isEmpty && !b.isEmpty && a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

    private fun checkClear(s: BattleScreen, where: String) {
        val rib = s.milestoneRibbon.drawn
        assertTrue("$where: wstęga niewidoczna", !rib.isEmpty)
        assertTrue("$where: wstęga na pasku bossa", !overlap(rib, s.topBarView.bossDrawn))
        assertTrue("$where: wstęga na wierszu „Następna fala”", !overlap(rib, s.topBarView.nextThemeDrawn))
        assertTrue("$where: wstęga na pasku górnym", rib.top >= s.topBar.bottom)
        for (b in s.buttons) if (b.visible && b !== s.baseAlertBtn)
            assertTrue("$where: wstęga na przycisku ${b.id}", !overlap(rib, b.r))
        assertTrue("$where: wstęga poza ekranem", rib.left >= 0f && rib.right <= s.g.width)
    }

    /** Fala → [wave] krokami symulacji (boss i karty jak w grze); karty zamykane od razu. */
    private fun reachWave(g: Game, s: BattleScreen, wave: Int) {
        val b = s.battle
        var guard = 0
        while (b.wave < wave && guard++ < 50_000) {
            if (b.wave < wave - 1) b.wave = wave - 1
            b.pendingCards = null
            b.pendingVariant = null
            b.pendingChoice = null
            b.waveTimer = 0f
            b.step()
        }
        b.pendingCards = null
        b.pendingVariant = null
        b.pendingChoice = null
        assertEquals(wave, b.wave)
    }

    @Test fun ribbonTierRecordAndPause() {
        val old = L.lang
        try {
            for (device in shown) for (lang in listOf("pl", "en", "de")) {
                val where = "${device}_$lang"
                // Oblężenie tygodnia: fala 10 = Brąz i jednocześnie boss.
                run {
                    val g = game(devices.getValue(device), lang)
                    val medals = g.profile.medals
                    val s = BattleScreen(g, Weekly.config(week))
                    g.setScreen(s)
                    tick(g, 3)
                    s.battle.pendingCards = null
                    reachWave(g, s, 9)
                    tick(g, 2)
                    assertTrue(!s.milestoneRibbon.active)
                    reachWave(g, s, 10)
                    tick(g, 1)
                    assertEquals(L.f("ms198_tier0", L.plural("medals_n", 25L)), s.milestoneRibbon.current)
                    // Boss pojawia się w tej samej chwili: wstęga stoi pod jego paskiem.
                    var guard = 0
                    while (s.battle.currentBoss() == null && guard++ < 600) {
                        s.battle.pendingCards = null; s.battle.pendingVariant = null; s.battle.pendingChoice = null
                        s.battle.step()
                    }
                    tick(g, 15)
                    save(g, "${where}_tier")
                    checkClear(s, "$where/próg")
                    assertTrue("$where: brak paska bossa", !s.topBarView.bossDrawn.isEmpty)
                    assertTrue(lastTexts.any { it.s == s.milestoneRibbon.current })
                    // Nagrody liczy tylko koniec bitwy.
                    assertEquals(medals, g.profile.medals)
                    assertEquals(0, g.profile.weeklyClaimedFor(week))
                    tick(g, 120)
                    assertTrue("$where: wstęga po 4 s", !s.milestoneRibbon.active)
                    save(g, "${where}_tier_after")
                    assertTrue(s.milestoneRibbon.drawn.isEmpty)
                    // Pauza: progi tygodnia.
                    reachWave(g, s, 23)
                    s.paused = true
                    tick(g, 2)
                    save(g, "${where}_pause")
                    val line = L["weekly_bronze"] + " ✓ · " + L["weekly_silver"] + " ✓ · " +
                        L.f("ms198_pause_tier_next", L["weekly_gold"], 30)
                    assertEquals(line, s.pauseMilestoneText)
                    assertTrue("$where: brak wiersza progów w pauzie", lastTexts.any { it.s == line })
                }
                // Zwykłe Endless: dopisek „do rekordu” i wstęga rekordu.
                run {
                    val g = game(devices.getValue(device), lang)
                    g.profile.bestWave = 37
                    val s = BattleScreen(g, BattleConfig(Mode.ENDLESS, 2026L, 1f))
                    g.setScreen(s)
                    tick(g, 3)
                    s.battle.pendingCards = null
                    reachWave(g, s, 34)
                    tick(g, 2)
                    assertEquals(4, s.milestones!!.wavesToRecord)
                    save(g, "${where}_to_record")
                    val full = L.f("ms198_to_record", 4)
                    val short = L.f("ms198_to_record_short", 4)
                    assertTrue("$where: brak dopisku do rekordu",
                        lastTexts.any { it.s.endsWith(full) || it.s.endsWith(short) })
                    reachWave(g, s, 38)
                    tick(g, 15)
                    assertEquals(L.f("ms198_record", 38), s.milestoneRibbon.current)
                    save(g, "${where}_record")
                    checkClear(s, "$where/rekord")
                    assertEquals(37, g.profile.bestWave)
                    tick(g, 120)
                    reachWave(g, s, 41)
                    tick(g, 2)
                    assertTrue("$where: rekord drugi raz", !s.milestoneRibbon.active)
                    s.paused = true
                    tick(g, 2)
                    save(g, "${where}_pause_endless")
                    assertEquals(L.f("ms198_pause_record", 41), s.pauseMilestoneText)
                    assertTrue(lastTexts.any { it.s == s.pauseMilestoneText })
                }
            }
        } finally {
            L.lang = old
        }
    }
}
