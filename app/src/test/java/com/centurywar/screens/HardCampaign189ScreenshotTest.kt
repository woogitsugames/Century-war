package com.centurywar.screens

import android.graphics.RectF
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.L
import com.centurywar.game.Campaign
import com.centurywar.game.CampaignDifficulty
import com.centurywar.game.Weekly
import com.centurywar.meta.Missions
import com.centurywar.meta.NextGoalKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.hypot

/**
 * R10-02: korony Trudnego na mapie kampanii, w nagłówku epoki, w panelu etapu i na kaflu menu
 * oraz cel „korona na etapie” po komplecie gwiazdek. Profil: wszystkie gwiazdki i 5 koron
 * (pełna epoka 1 i etap 10). Zrzuty: build/screenshots/pre10_hard189_<urządzenie>_<język>_*.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class HardCampaign189ScreenshotTest : PrereleasePolishBase() {
    private val crowns = intArrayOf(0, 1, 2, 3, 9)
    private var visibleGoals = 0

    private fun prepared(g: Game) {
        val p = g.profile
        for (i in p.stars.indices) p.stars[i] = if (i % 3 == 0) 3 else 2
        for (i in crowns) p.recordHardVictory(i)
        p.medals = 0
        // Progi Oblężenia tygodnia odebrane: celem jest korona.
        val week = Missions.utcWeek(System.currentTimeMillis())
        p.claimWeekly(week, Weekly.threshold(Weekly.TIER_COUNT - 1), week)
        p.medals = 0
    }

    @Test
    fun crownsOnMapPanelMenuAndGoal() {
        val old = L.lang
        try {
            for (device in devices.keys) for (lang in listOf("pl", "en", "de")) {
                val g = game(devices.getValue(device), lang)
                prepared(g)
                val prefix = "hard189_${device}_$lang"
                counter = 0
                checkMenu(g, prefix)
                checkMap(g, prefix)
                checkPanel(g, prefix)
                checkGoal(g, prefix)
                g.history?.close()
            }
        } finally { L.lang = old }
        assertTrue("Regresje R10-02:\n$regressions", regressions.isEmpty())
    }

    private fun checkMenu(g: Game, prefix: String) {
        g.setScreen(MenuScreen(g))
        tick(g, 6, 0.1f)
        val menu = g.current as MenuScreen
        shot(g, prefix, "menu")
        val campaign = field(menu, "campaign").get(menu) as Button
        assertEquals("$prefix: licznik koron w menu", "5/36", campaign.sub2)
        assertTrue(menu.accessibilityLabel(campaign).contains(L["hard189_crowns"] + " 5/36"))
        val t = lastTexts.firstOrNull { it.s == "5/36" }
        require10(t != null, "$prefix: menu bez licznika koron")
        if (t != null) require10(t.r.left >= campaign.r.left && t.r.right <= campaign.r.right,
            "$prefix: licznik koron wychodzi poza kafel Kampanii")
    }

    private fun checkMap(g: Game, prefix: String) {
        val dp = g.ui.dp
        val cs = CampaignScreen(g)
        g.push(cs)
        // Baner serii dni znika po kilku sekundach (zasłaniał nagłówek epoki 1).
        tick(g, 90, 0.1f)
        require10(g.noticeBanner.isEmpty, "$prefix: baner nad mapą po 9 s")
        field(cs, "autoScroll").setBoolean(cs, false)
        field(cs, "scroll").setFloat(cs, 0f)
        tick(g, 2)
        shot(g, prefix, "mapa")
        val nodeR = field(cs, "nodeR").getFloat(cs)
        @Suppress("UNCHECKED_CAST")
        val tiles = field(cs, "tiles").get(cs) as List<Button>
        val box = RectF()
        var checked = 0
        for (i in crowns) {
            val t = tiles[i]
            if (!t.visible) continue
            checked++
            cs.crownCenter(i, box)
            val r = nodeR * if (i % 4 == 3) 1.12f else 1f
            val x = t.r.centerX()
            val y = t.r.top + r * 1.42f
            val kx = box.centerX(); val ky = box.centerY()
            val kr = box.width() / 2f
            // Pogoda (kółko 12 dp), błyskawica (17 dp) i gwiazdki pod węzłem.
            require10(hypot(kx - (x + r * 0.94f), ky - (y - r * 0.82f)) >= kr + 12 * dp,
                "$prefix: korona etapu ${i + 1} nachodzi na pogodę")
            require10(hypot(kx - (x - r * 0.94f), ky - (y + r * 0.68f)) >= kr + 8.5f * dp,
                "$prefix: korona etapu ${i + 1} nachodzi na błyskawicę")
            val size = r * 0.58f
            val step = size * 0.95f
            for (k in -1..1) {
                val d = hypot(kx - (x + k * step), ky - (y + r + 5 * dp))
                require10(d >= kr + size * 0.42f, "$prefix: korona etapu ${i + 1} nachodzi na gwiazdkę ${k + 2}" +
                    " (${"%.1f".format(d / dp)} dp, r=${"%.1f".format(r / dp)} dp)")
            }
        }
        require10(checked > 0, "$prefix: żadna korona nie jest widoczna na mapie")
        // Nagłówek epoki 1: korona i „4/4” nieucięte, ≥ 11 sp, nie na nazwie epoki.
        val label = lastTexts.firstOrNull { it.s == "4/4" }
        require10(label != null, "$prefix: brak licznika koron w nagłówku epoki")
        if (label != null) {
            val sp = label.sizePx / dp / g.ui.textScale
            require10(sp >= 11f, "$prefix: licznik koron ma ${"%.1f".format(sp)} sp")
            require10(label.r.right <= label.clip.right + 0.5f && label.r.left >= label.clip.left - 0.5f,
                "$prefix: licznik koron ucięty")
            val name = lastTexts.firstOrNull { it.s == L[com.centurywar.game.Defs.eraKeys[0]] }
            if (name != null) require10(!RectF.intersects(name.r, label.r), "$prefix: licznik koron na nazwie epoki")
            val stars = lastTexts.firstOrNull { it.s.endsWith("/12") && RectF.intersects(it.r, it.clip) }
            if (stars != null) require10(!RectF.intersects(stars.r, label.r), "$prefix: licznik koron na gwiazdkach")
        }
        g.pop(); tick(g, 2)
    }

    private fun checkPanel(g: Game, prefix: String) {
        val dp = g.ui.dp
        val cs = CampaignScreen(g)
        g.push(cs)
        tick(g, 6, 0.1f)
        val withMods = Campaign.stages.first { it.modifiers.isNotEmpty() && it.index !in crowns }
        for ((stage, hard) in listOf(Campaign.stages[1] to false, withMods to true, withMods to false)) {
            g.profile.campaignDifficulty = if (hard) CampaignDifficulty.HARD else CampaignDifficulty.NORMAL
            field(cs, "selected").set(cs, stage)
            cs.layout()
            tick(g, 2)
            val name = "etap${stage.index + 1}_" + if (hard) "trudny" else "normalny"
            shot(g, prefix, name)
            val won = g.profile.hardWon(stage.index)
            val expected = when { won -> L["hard189_won"]; hard -> L["hard189_todo"]; else -> "" }
            if (expected.isEmpty()) {
                require10(cs.hardInfoRect.isEmpty, "$prefix/$name: wiersz korony bez korony na Normalnym")
                continue
            }
            val t = lastTexts.lastOrNull { it.s == expected }
            require10(t != null, "$prefix/$name: brak wiersza „$expected”")
            if (t == null) continue
            val sp = t.sizePx / dp / g.ui.textScale
            require10(sp >= 11f - 0.05f, "$prefix/$name: wiersz korony ma ${"%.1f".format(sp)} sp")
            val info = field(cs, "info").get(cs) as RectF
            require10(t.r.left >= info.left && t.r.right <= info.right, "$prefix/$name: wiersz korony poza panelem")
            val diff = lastTexts.lastOrNull { it.s == L["campaign126_difficulty"] }
            if (diff != null) require10(t.r.bottom <= diff.r.top, "$prefix/$name: wiersz korony pod „Trudność”")
            for (m in stage.modifiers) {
                val chip = lastTexts.lastOrNull { it.s == L[m.key] } ?: continue
                require10(!RectF.intersects(chip.r, t.r), "$prefix/$name: wiersz korony na chipie ${chip.s}")
                if (diff != null) require10(chip.r.bottom <= diff.r.top + 1f, "$prefix/$name: chip ${chip.s} pod „Trudność”")
            }
        }
        g.profile.campaignDifficulty = CampaignDifficulty.NORMAL
        g.pop(); tick(g, 2)
    }

    private fun checkGoal(g: Game, prefix: String) {
        g.setScreen(MenuScreen(g))
        tick(g, 6, 0.1f)
        val menu = g.current as MenuScreen
        if (menu.portraitMenu) return // pionowe menu nie ma wiersza celu (R2-01)
        val goal = com.centurywar.meta.NextGoals.pick(g.profile, System.currentTimeMillis(), null, skipMissions = true)
        assertEquals("$prefix: cel", NextGoalKind.HARD_STAGE, goal.kind)
        assertEquals(4, goal.arg)
        if (menu.goalBtn.visible) {
            assertEquals("$prefix: cel menu", NextGoalKind.HARD_STAGE, menu.goalKind)
            assertEquals(4, menu.goalArg)
            visibleGoals++
        } else {
            // Ekran bez miejsca na wiersz celu (np. pasek serii dni): sama akcja celu.
            field(menu, "goalKind").set(menu, goal.kind)
            menu.goalArg = goal.arg
        }
        menu.onClick(menu.goalBtn)
        tick(g, 6, 0.1f)
        val cs = g.current as CampaignScreen
        assertEquals(Campaign.stages[4], field(cs, "selected").get(cs))
        assertTrue("$prefix: brak obwódki Trudnego", cs.hardSuggested)
        assertEquals(CampaignDifficulty.NORMAL, g.profile.campaignDifficulty)
        shot(g, prefix, "cel_okno")
        // Wstecz zamyka okno, drugie Wstecz wraca do menu (bez martwych końców).
        g.requestBack(); tick(g, 4, 0.1f)
        require10(g.current === cs && field(cs, "selected").get(cs) == null, "$prefix: Wstecz nie zamyka okna etapu")
        g.requestBack(); tick(g, 4, 0.1f)
        require10(g.current is MenuScreen, "$prefix: Wstecz z mapy nie wraca do menu")
    }
}
