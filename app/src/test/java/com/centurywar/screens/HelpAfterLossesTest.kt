package com.centurywar.screens

import com.centurywar.engine.Game
import com.centurywar.engine.L
import com.centurywar.game.Campaign
import com.centurywar.game.CampaignDifficulty
import com.centurywar.game.Research
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
 * R2-02: po 2. porażce z rzędu na etapie wyniki proponują badanie (gdy stać), inaczej poziom
 * Łatwy; Dalej prowadzi do Badań albo do okna etapu z wyróżnionym Łatwym. Zrzuty pre10_help_*.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class HelpAfterLossesTest : PrereleasePolishBase() {
    private val stage = 10

    /** Prawdziwa bitwa etapu 11 zakończona porażką (baza gracza zburzona). */
    private fun lose(g: Game, surrenderEarly: Boolean = false, broke: Boolean = false): BattleScreen {
        // „Bez medali”: nagrody za porażkę i osiągnięcia nie mogą wystarczyć na badanie.
        if (broke) g.profile.medals = -100_000
        val bs = BattleScreen(g, Campaign.config(Campaign.stages[stage], g.profile.bonuses(), 7L + g.profile.battles,
            difficulty = g.profile.campaignDifficulty))
        g.setScreen(bs); tick(g, 3)
        if (surrenderEarly) {
            bs.onClick(bs.pauseBtn); tick(g)
            bs.onClick(bs.pauseMenu[2])
        } else bs.battle.player.baseHp = 0f
        var n = 0
        while (!bs.finished && n++ < 300) tick(g, 1, 0.1f)
        tick(g, 16, 0.25f)
        assertTrue(bs.finished && bs.battle.winner != 0)
        return bs
    }

    private fun prepared(g: Game) {
        for (i in 0 until stage) g.profile.stars[i] = 1
        g.profile.medals = 0
    }

    @Test
    fun secondLossOffersEasyThenStageWindowHighlightsEasy() {
        val old = L.lang
        try {
            for ((device, lang) in listOf("phone720" to "pl", "phone720" to "de", "foldOuter" to "pl", "foldOuter" to "en")) {
                val g = game(devices.getValue(device), lang)
                g.profile.hintsEnabled = true
                prepared(g)
                counter = 0
                val first = lose(g, broke = true)
                assertEquals(BattleScreen.HELP_NONE, first.helpOffer)
                assertFalse(first.tipText.startsWith(L["help180_easy"]))
                // Szybka kapitulacja nie nabija licznika.
                val quick = lose(g, surrenderEarly = true, broke = true)
                assertEquals(BattleScreen.HELP_NONE, quick.helpOffer)
                assertEquals(1, g.profile.lossStreakOn(stage))
                val second = lose(g, broke = true)
                assertEquals(2, g.profile.lossStreakOn(stage))
                assertEquals(BattleScreen.HELP_EASY, second.helpOffer)
                assertTrue(second.tipText, second.tipText.startsWith(L["help180_easy"]))
                shot(g, "help_${device}_$lang", "wyniki_latwy")
                checkHelpLine(g, second, "help_${device}_$lang")
                second.onClick(second.continueBtn); tick(g, 4)
                val cs = g.current as CampaignScreen
                assertTrue(cs.suggestEasy && cs.easySuggested)
                assertEquals(Campaign.stages[stage], field(cs, "selected").get(cs))
                // Gra nie zmienia poziomu sama.
                assertEquals(CampaignDifficulty.NORMAL, g.profile.campaignDifficulty)
                shot(g, "help_${device}_$lang", "okno_etapu_latwy")
                assertEquals(-1, field(field(cs, "hints").get(cs), "activeId").getInt(field(cs, "hints").get(cs)))
                // Trzecia porażka: dymek o poziomie trudności przy Łatwym.
                val third = lose(g, broke = true)
                third.onClick(third.continueBtn); tick(g, 4)
                val cs3 = g.current as CampaignScreen
                val hints = field(cs3, "hints").get(cs3) as HintOverlay
                assertEquals(HintOverlay.DIFFICULTY, hints.activeId)
                // Dymek leży nad oknem z założenia (zasłania wiersz modyfikatorów): nakładki tekstu
                // pod dymkiem nie są błędem układu; sprawdzamy, że nie zasłania przycisków poziomu.
                val before = regressions.length
                shot(g, "help_${device}_$lang", "okno_etapu_dymek")
                regressions.setLength(before)
                val panel = android.graphics.RectF(hints.panelRect)
                for (b in cs3.buttons) if (b.id in -12..-10 || b.id == -2)
                    require10(!android.graphics.RectF.intersects(panel, b.r), "$device/$lang: dymek zasłania przycisk #${b.id}")
                // Gracz wybiera Łatwy: wyróżnienie znika; wygrana zeruje serię.
                cs3.onClick(hints.okBtn)
                cs3.onClick(cs3.buttons.first { it.id == -10 })
                assertEquals(CampaignDifficulty.EASY, g.profile.campaignDifficulty)
                assertFalse(cs3.easySuggested)
                g.profile.recordCampaignOutcome(stage, won = true)
                assertEquals(0, g.profile.lossStreakOn(stage))
                g.history?.close()
            }
        } finally { L.lang = old }
        assertTrue("Regresje R2-02:\n$regressions", regressions.isEmpty())
    }

    @Test
    fun researchIsOfferedFirstWhenAffordable() {
        val old = L.lang
        try {
            val g = game(devices.getValue("phone720"), "pl")
            prepared(g)
            lose(g)
            val cheapest = Research.defs.filter { it.key != "r_elite" }.minOf { it.cost(0) }
            g.profile.medals = cheapest
            val bs = lose(g)
            assertEquals(BattleScreen.HELP_RESEARCH, bs.helpOffer)
            assertTrue(bs.tipText, bs.tipText.startsWith(L.f("help180_research", "").trimEnd()))
            shot(g, "help_phone720_pl", "wyniki_badanie")
            checkHelpLine(g, bs, "help_phone720_pl/badanie")
            // Dotknięcie propozycji = Dalej: Badania nad mapą, Wstecz wraca na mapę.
            bs.onClick(bs.resultGoalBtn); tick(g, 4)
            assertTrue(g.current is ResearchScreen)
            g.back(); tick(g, 2)
            assertTrue(g.current is CampaignScreen)
            // Na Łatwym propozycji nie ma.
            g.profile.campaignDifficulty = CampaignDifficulty.EASY
            val easy = lose(g)
            assertEquals(BattleScreen.HELP_NONE, easy.helpOffer)
            g.history?.close()
        } finally { L.lang = old }
    }

    private fun checkHelpLine(g: Game, bs: BattleScreen, where: String) {
        val dp = g.ui.dp
        val lines = (0 until bs.resultTipCount).map { bs.resultTipLines[it] ?: "" }
        require10(lines.isNotEmpty(), "$where: brak wierszy propozycji")
        for (line in lines) {
            val t = lastTexts.firstOrNull { it.s == line }
            require10(t != null, "$where: wiersz propozycji „$line” nie jest narysowany")
            if (t == null) continue
            require10(t.sizePx / dp >= 8.5f, "$where: drobny napis propozycji")
            require10(t.r.left >= bs.resultPanel.left && t.r.right <= bs.resultPanel.right + 0.5f,
                "$where: ucięty napis propozycji „$line”")
            require10(t.r.bottom <= bs.restartEnd.r.top, "$where: propozycja na przyciskach wyników")
        }
        require10(bs.resultGoalBtn.visible, "$where: propozycja bez celu dotyku")
        File(out, "pre10_help_audit.txt").appendText(audit.toString())
    }
}
