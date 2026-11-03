package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.engine.Screen
import com.centurywar.engine.TouchEvent
import com.centurywar.game.Ai
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.Campaign
import com.centurywar.game.Defs
import com.centurywar.game.Doctrine
import com.centurywar.game.EraArt
import com.centurywar.game.Mode
import com.centurywar.meta.BattleHistory
import com.centurywar.meta.Profile
import com.centurywar.meta.ReplayStore
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Przegląd przed premierą na telefonie 720p oraz szlif 2; ścieżka i narzędzia: [PrereleasePolishBase]. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PrereleasePolishTest : PrereleasePolishBase() {
    @Test
    fun prereleaseTour() = prereleaseTourOn("phone720")

    /** R2-03: tekst 130% z ustawień telefonu (Dostępność i HUD bitwy). */
    @Test
    fun textScale130FromSystem() = textScale130On("phone720")

    /** Tura 10: ✕ na liście powtórek usuwa dopiero po drugim dotknięciu i mówi o tym na ekranie. */
    @Test
    fun replayDeleteNeedsSecondTap() {
        val old = L.lang
        try {
            val g = game(devices.getValue("phone720"), "pl")
            val cfg = BattleConfig(Mode.SKIRMISH, 299L, 0.65f, startEra = 0, maxEra = 1)
            val battle = Battle(cfg)
            val commands = com.centurywar.game.BattleController(battle)
            repeat(60 * 5) { battle.step() }
            commands.surrender()
            val bytes = com.centurywar.game.ReplayRecord.encode(cfg, commands.log(), com.centurywar.game.ReplayRecord.Meta(
                cfg.mode, cfg.stageIndex, battle.winner, battle.score(), battle.time, battle.wave, 1793461200000L, "Dowódca"))
            val store = g.replays!!
            val id = store.newId()
            store.saveAsync(id, bytes)
            val latch = java.util.concurrent.CountDownLatch(1)
            store.listAsync { latch.countDown() }
            assertTrue(latch.await(8, java.util.concurrent.TimeUnit.SECONDS))
            val list = ReplayListScreen(g)
            g.setScreen(list)
            waitReplays(g)
            val panel = field(list, "panel").get(list) as RectF
            val rowY = panel.top + 46 * g.ui.dp + 25 * g.ui.dp
            val x = panel.right - 14 * g.ui.dp - 25 * g.ui.dp
            list.onTap(x, rowY)
            assertTrue("pierwsze dotknięcie ✕ nie pyta", list.deleteArmedId == id &&
                list.statusText == L["replay150_delete_confirm"])
            shot(g, "replay", "usun_potwierdz")
            // Inny wiersz/kolumna rozbraja usuwanie.
            list.onTap(x - 60 * g.ui.dp, rowY)
            assertTrue("rozbrojenie po innym dotknięciu", list.deleteArmedId == null)
            g.setScreen(list); waitReplays(g)
            list.onTap(x, rowY); list.onTap(x, rowY)
            waitReplays(g)
            val entries = field(list, "entries").get(list) as List<*>
            assertTrue("drugie dotknięcie nie usunęło (${entries.size})", entries.isEmpty())
            g.history?.close()
        } finally { L.lang = old }
    }

    /** Szlif 2: Endless na drugim wewnętrznym Foldzie (d=3,6) i baner serii dni w menu 720p. */
    @Test
    fun polish2EndlessFoldAndMenuBanner() {
        val old = L.lang
        try {
            for (lang in arrayOf("pl", "en", "de")) {
                val g = game(Device(1802, 2000, 3.6f, intArrayOf(0, 80, 0, 0)), lang)
                counter = 0
                tick(g, 5, 0.1f)
                g.push(ModeScreen(g, Mode.ENDLESS)); tick(g, 6, 0.1f)
                shot(g, "foldInner36_$lang", "endless")
                g.history?.close()
            }
            for (lang in arrayOf("pl", "en", "de", "tr", "uk")) {
                val g = game(devices.getValue("phone720"), lang)
                counter = 0
                tick(g, 5, 0.1f)
                if (g.current !is MenuScreen) g.setScreen(MenuScreen(g))
                g.showNotice(L["ms_streak"] + " 1/7")
                tick(g, 4, 0.1f)
                val menu = g.current as MenuScreen
                shot(g, "banner720_$lang", "menu", saveFile = lang == "pl")
                val campaign = field(menu, "campaign").get(menu) as com.centurywar.engine.Button
                require10(!g.noticeBanner.isEmpty && campaign.r.top - g.noticeBanner.bottom >= 5.9f * g.ui.dp,
                    "banner720_$lang: baner ${"%.1f".format((campaign.r.top - g.noticeBanner.bottom) / g.ui.dp)} dp nad Kampanią")
                // Kontrolnie najdłuższe tłumaczenia nowych etykiet (tr/uk) w zakładce Rekordów.
                g.push(RankingsScreen(g)); tick(g, 6, 0.1f)
                shot(g, "banner720_$lang", "rankingi", saveFile = lang == "tr" || lang == "uk")
                g.pop()
                g.history?.close()
            }
        } finally { L.lang = old }
        assertTrue("Regresje szlifu 2:\n$regressions", regressions.isEmpty())
    }
}
