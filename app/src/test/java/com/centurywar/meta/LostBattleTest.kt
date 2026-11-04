package com.centurywar.meta

import android.graphics.Bitmap
import android.graphics.Canvas
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.game.Ai
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.BattleMeta
import com.centurywar.game.Campaign
import com.centurywar.game.CampaignDifficulty
import com.centurywar.game.Daily
import com.centurywar.game.Mode
import com.centurywar.game.Weekly
import com.centurywar.screens.BattleScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.min

/** R10-01: medale za bitwę utraconą przy aktualizacji = nagroda za porażkę (kapitulację). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LostBattleTest {
    @get:Rule val folder = TemporaryFolder()

    private val canvas = Canvas(Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888))
    private val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
    }

    private fun game(): Game {
        val p = Profile(File(folder.newFolder(), "profile.json"))
        p.tutorialDone = true; p.hintsEnabled = false; p.eraIntrosEnabled = false
        return Game(host, p, Quality.LOW).also { it.density = 2f; it.resize(1600, 720, 1600, 720) }
    }

    /** Wzory gałęzi porażki sprzed wydzielenia [LostBattle.lossReward] (niezależna kopia). */
    private fun legacyLoss(cfg: BattleConfig, b: Battle, ranked: Boolean): Int {
        val effort = (b.time / 240f).coerceIn(0f, 1f)
        val kills = b.player.kills
        return when (cfg.mode) {
            Mode.CAMPAIGN -> (((5 + cfg.stageIndex * 2) * effort).toInt() + min(20, kills / 8)).let {
                (it * (Campaign.difficulty(cfg)?.medalMultiplier ?: 1f)).toInt()
            }
            Mode.SKIRMISH -> SkirmishSetup.reward(cfg, false, b.time, kills)
            Mode.ENDLESS -> if (cfg.weekly >= 0L) (b.wave * 3 + kills / 10) / 2 else b.wave * 3 + kills / 10
            Mode.DAILY -> if (ranked) 0 else (5 * effort).toInt()
        }
    }

    /** Bitwa grana przez AI przez [seconds] s, potem kapitulacja; zwraca ekran po rozliczeniu. */
    private fun surrenderAfter(cfg: BattleConfig, ranked: Boolean, seconds: Float): Pair<BattleScreen, Int> {
        val g = game()
        val bs = BattleScreen(g, cfg, ranked)
        g.setScreen(bs)
        g.frame(canvas, 1f / 60f)
        val b = bs.battle
        b.playerAi = Ai(b, 0)
        while (!b.over && b.time < seconds) b.step()
        while (!b.over && (b.pendingChoice != null || b.pendingCards != null || b.pendingVariant != null)) b.step()
        val expected = LostBattle.lossReward(cfg, b, ranked)
        assertEquals("niezależny wzór, ${cfg.mode}", legacyLoss(cfg, b, ranked), expected)
        bs.paused = false
        b.surrender()
        g.frame(canvas, 1f / 60f)
        assertTrue(bs.finished)
        return bs to expected
    }

    @Test
    fun campaignLossMatchesSettlementOnEveryDifficulty() {
        for (d in CampaignDifficulty.entries) {
            val cfg = Campaign.config(Campaign.stages[3], Profile(File(folder.newFolder(), "p.json")).bonuses(), 9L, difficulty = d)
            val (bs, expected) = surrenderAfter(cfg, false, 130f)
            assertEquals(d.name, expected, bs.reward)
            assertTrue("$d: kapitulacja po 2 min daje medale", expected > 0)
        }
    }

    @Test
    fun skirmishLossMatchesSettlement() {
        val cfg = BattleConfig(Mode.SKIRMISH, 31L, 1.05f)
        val (bs, expected) = surrenderAfter(cfg, false, 150f)
        assertEquals(expected, bs.reward)
    }

    @Test
    fun endlessLossMatchesSettlement() {
        val cfg = BattleConfig(Mode.ENDLESS, 12L, 1f)
        val (bs, expected) = surrenderAfter(cfg, false, 200f)
        assertEquals(expected, bs.reward)
        assertTrue(expected > 0)
    }

    @Test
    fun weeklySiegeLossHasNoTierBonus() {
        val week = Weekly.weekNumber(Daily.dayNumber())
        val cfg = Weekly.config(week)
        val (bs, expected) = surrenderAfter(cfg, false, 120f)
        // Premia progu wchodzi tylko w rozegranej bitwie; poniżej progu nagrody są równe.
        if (bs.battle.wave < Weekly.threshold(0)) assertEquals(expected, bs.reward)
        else assertTrue(bs.reward >= expected)
        assertEquals((bs.battle.wave * 3 + bs.battle.player.kills / 10) / 2, expected)
    }

    @Test
    fun dailyRankedGivesNoMedalsButUnrankedDoes() {
        val day = Daily.dayNumber()
        val cfg = Daily.config(day, com.centurywar.game.Bonuses())
        val (ranked, rankedExpected) = surrenderAfter(cfg, true, 120f)
        assertEquals("rankingowe wyzwanie dnia: zamiast medali zwrot próby", 0, rankedExpected)
        assertTrue(ranked.reward >= 10)
        val (free, freeExpected) = surrenderAfter(cfg, false, 120f)
        assertEquals(freeExpected, free.reward)
    }

    private fun meta(mode: Mode = Mode.ENDLESS, ranked: Boolean = false, day: Long = -1L, weekly: Long = -1L, consolation: Int = 75, id: Long = 5L) =
        BattleMeta(id, mode, 6, weekly, day, ranked, 23, 600, 120, consolation, 0L)

    @Test
    fun settleOnceRestoresOnlyTodaysRankedAttempt() {
        val today = 20_400L
        val p = Profile(File(folder.newFolder(), "profile.json"))
        p.medals = 10
        p.lastRankedDaily = today
        val r = LostBattle.settle(p, meta(Mode.DAILY, ranked = true, day = today, consolation = 0), today)!!
        assertTrue(r.dailyRestored)
        assertEquals(-1L, p.lastRankedDaily)
        assertEquals(10, p.medals)
        assertNull("drugi raz nic", LostBattle.settle(p, meta(Mode.DAILY, ranked = true, day = today, consolation = 0), today))
        // Wczorajsza próba nie wraca (dziś gracz ma już nową).
        p.lastRankedDaily = today
        val old = LostBattle.settle(p, meta(Mode.DAILY, ranked = true, day = today - 1, id = 6L, consolation = 0), today)!!
        assertEquals(false, old.dailyRestored)
        assertEquals(today, p.lastRankedDaily)
        val e = LostBattle.settle(p, meta(id = 7L), today)!!
        assertEquals(75, e.medals)
        assertEquals(85, p.medals)
        Profile.awaitHintWrites()
    }

    @Test
    fun noticesNameModeAndProgressInEveryLanguage() {
        val old = L.lang
        try {
            for (code in L.CODES) {
                L.setCode(code)
                val endless = LostBattle.notices(meta(), LostBattle.Settlement(75, false))
                assertEquals(2, endless.size)
                assertTrue(code + ": " + endless[0], endless[0].contains("23"))
                assertTrue(code + ": " + endless[1], endless[1].contains("75"))
                val camp = LostBattle.notices(meta(Mode.CAMPAIGN), LostBattle.Settlement(0, false))
                assertEquals(1, camp.size)
                assertTrue(code + ": " + camp[0], camp[0].contains("7"))
                val daily = LostBattle.notices(meta(Mode.DAILY, true), LostBattle.Settlement(0, true))
                assertEquals(L["lost188_daily"], daily[1])
                val weekly = LostBattle.notices(meta(weekly = 3L), LostBattle.Settlement(1, false))
                assertTrue(weekly[0].contains(L.f("lost188_weekly", 23)))
            }
            L.setCode("pl")
            assertEquals("Gra została zaktualizowana. Przerwanej bitwy (Oblężenie bez końca, fala 23) nie da się wznowić.",
                LostBattle.notices(meta(), LostBattle.Settlement(75, false))[0])
            assertEquals("Otrzymujesz 75 medali.", LostBattle.notices(meta(), LostBattle.Settlement(75, false))[1])
        } finally { L.lang = old }
    }
}
