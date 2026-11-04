package com.centurywar.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import com.centurywar.game.Defs
import com.centurywar.game.Mode
import com.centurywar.meta.Profile
import com.centurywar.screens.AchievementsScreen
import com.centurywar.screens.BannerScreen
import com.centurywar.screens.BattleScreen
import com.centurywar.screens.CampaignScreen
import com.centurywar.screens.MenuScreen
import com.centurywar.screens.MissionsScreen
import com.centurywar.screens.ModeScreen
import com.centurywar.screens.ResearchScreen
import com.centurywar.screens.SkirmishSetupScreen
import com.centurywar.screens.WhatsNewScreen
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * R11-01: ekrany drogi do bitwy i listy mają węzły czytnika zamiast jednego „Ekran gry”.
 * Ścieżka menu → kampania → etap → Start idzie wyłącznie przez akcje na węzłach.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GameAccessibilityScreensTest {
    private val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
    }

    private class Fixture(val game: Game, val view: GameView, val bitmap: Bitmap, val w: Int, val h: Int) {
        private val canvas = Canvas(bitmap)
        val provider get() = view.accessibilityNodeProvider!!
        val profile get() = game.profile

        fun frame(dt: Float = 0.05f) {
            game.frame(canvas, dt)
            view.refreshAccessibilityNodes()
        }

        fun settle(frames: Int = 40) = repeat(frames) { frame() }

        fun root(): AccessibilityNodeInfo = provider.createAccessibilityNodeInfo(View.NO_ID)!!

        /** Wszystkie węzły drzewa (przyciski: id 1..n, informacje: od INFO_SCREEN_BASE). */
        fun nodes(): List<Pair<Int, AccessibilityNodeInfo>> {
            val ids = (0..400) + (GameAccessibility.INFO_SCREEN_BASE until GameAccessibility.INFO_SCREEN_BASE + 120)
            val out = ids.mapNotNull { id -> provider.createAccessibilityNodeInfo(id)?.let { id to it } }
            assertEquals("Liczba węzłów = liczba dzieci korzenia", root().childCount, out.size)
            return out
        }

        fun texts(): List<String> = nodes().map { it.second.contentDescription.toString() }

        fun find(what: String, p: (String) -> Boolean): Int {
            val hit = nodes().firstOrNull { p(it.second.contentDescription.toString()) }
            assertNotNull("Brak węzła: $what; są: ${texts()}", hit)
            return hit!!.first
        }

        fun click(id: Int): Boolean {
            val ok = provider.performAction(id, AccessibilityNodeInfo.ACTION_CLICK, null)
            frame()
            return ok
        }

        fun scroll(action: Int): Boolean {
            val ok = provider.performAction(View.NO_ID, action, null)
            frame()
            return ok
        }

        fun finish() { bitmap.recycle() }
    }

    private fun setup(w: Int = 1600, h: Int = 720, density: Float = 2f, lang: String = "pl",
                      prepare: (Profile) -> Unit = {}): Fixture {
        val file = File.createTempFile("cw-access196-", ".json").apply { delete() }
        val profile = Profile(file).apply {
            this.lang = lang
            tutorialDone = true
            prepare(this)
        }
        val game = Game(host, profile, Quality.LOW).apply {
            this.density = density
            resize(w, h, w, h)
            setScreen(MenuScreen(this))
        }
        val view = GameView(RuntimeEnvironment.getApplication(), game)
        view.layout(0, 0, w, h)
        return Fixture(game, view, Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888), w, h).also { it.frame() }
    }

    private fun quiet(p: Profile) {
        p.hintsEnabled = false
        for (e in 0 until Defs.ERA_COUNT) p.markEraIntroSeen(e)
    }

    private val screens: List<Pair<String, (Game) -> Screen>> = listOf(
        "campaign" to { g -> CampaignScreen(g) },
        "endless" to { g -> ModeScreen(g, Mode.ENDLESS) },
        "daily" to { g -> ModeScreen(g, Mode.DAILY) },
        "weekly" to { g -> ModeScreen(g, Mode.DAILY, true) },
        "skirmish" to { g -> SkirmishSetupScreen(g) },
        "research" to { g -> ResearchScreen(g) },
        "missions" to { g -> MissionsScreen(g) },
        "achievements" to { g -> AchievementsScreen(g) },
        "whats_new" to { g -> WhatsNewScreen(g, fromMenu = true) },
        "banners" to { g -> BannerScreen(g) },
    )

    private data class Quad(val device: String, val size: FloatArray, val lang: String, val overlays: Boolean)

    @Test fun everyScreenHasNamedNodesInsideTheView() {
        val phone = floatArrayOf(1600f, 720f, 2f)
        val fold = floatArrayOf(904f, 2316f, 2.625f)
        // Telefon i zewnętrzny Fold, pl i en; okna (dymki, zapowiedź epoki) na telefonie i Foldzie po polsku.
        for ((device, size, lang, overlays) in listOf(
            Quad("phone", phone, "pl", false), Quad("phone", phone, "en", false),
            Quad("fold_outer", fold, "pl", false), Quad("fold_outer", fold, "en", false),
            Quad("phone", phone, "pl", true), Quad("fold_outer", fold, "pl", true),
        )) {
            run {
                run {
                    val f = setup(size[0].toInt(), size[1].toInt(), size[2], lang) { p ->
                        p.stars[0] = 3; p.stars[1] = 2
                        if (!overlays) quiet(p)
                    }
                    try {
                        for ((name, make) in screens) {
                            val tag = "$device/$lang/$name/${if (overlays) "okna" else "bez okien"}"
                            f.game.push(make(f.game))
                            f.settle(12)
                            val root = f.root()
                            assertNotEquals(tag, L["a164_other"], root.contentDescription.toString())
                            val nodes = f.nodes()
                            assertTrue("$tag: brak węzłów", nodes.isNotEmpty())
                            for ((id, n) in nodes) {
                                val text = n.contentDescription?.toString().orEmpty()
                                assertTrue("$tag: węzeł $id bez opisu", text.isNotBlank())
                                val box = Rect()
                                n.getBoundsInParent(box)
                                assertTrue("$tag: węzeł $id ($text) poza widokiem: $box",
                                    box.left >= 0 && box.top >= 0 && box.right <= f.w && box.bottom <= f.h &&
                                        box.width() > 0 && box.height() > 0)
                            }
                            if (!overlays) assertTrue("$tag: brak przycisku", nodes.any { it.second.isClickable })
                            f.game.pop()
                            f.frame()
                        }
                    } finally { f.finish() }
                }
            }
        }
    }

    /** Menu → Kampania → etap 2 → Start wyłącznie przez akcje czytnika. */
    @Test fun campaignPathToBattleOnlyThroughNodes() {
        for (size in listOf(intArrayOf(1600, 720), intArrayOf(904, 2316))) {
            val f = setup(size[0], size[1], if (size[0] < 1000) 2.625f else 2f) { p ->
                p.stars[0] = 3
                quiet(p)
            }
            try {
                f.click(f.find("Kampania") { it.startsWith(L["campaign"]) })
                assertTrue(f.game.current is CampaignScreen)
                f.settle()
                assertEquals(L["campaign"], f.root().contentDescription.toString())
                val era = L[Defs.eraKeys[0]]
                assertTrue("Nagłówek epoki: ${f.texts()}", f.texts().any { it.startsWith(L.f("a196_era", era, 3)) })
                val first = f.find("etap 1") { it.startsWith(L.f("a196_stage", 1, era) + ",") }
                assertTrue(f.provider.createAccessibilityNodeInfo(first)!!.contentDescription.toString()
                    .contains(L.f("a196_stars", 3)))
                val stageTwo = f.find("etap 2") { it.startsWith(L.f("a196_stage", 2, era) + ",") }
                assertTrue(f.provider.createAccessibilityNodeInfo(stageTwo)!!.contentDescription.toString()
                    .contains(L["a196_next"]))

                // Zablokowany etap: niedostępny, kliknięcie odrzucone.
                val locked = f.find("etap 3") { it.startsWith(L.f("a196_stage", 3, era) + ",") }
                val lockedNode = f.provider.createAccessibilityNodeInfo(locked)!!
                assertFalse(lockedNode.isEnabled)
                assertTrue(lockedNode.contentDescription.toString().contains(L["a196_locked"]))
                assertFalse(f.click(locked))

                assertTrue(f.click(stageTwo))
                val texts = f.texts()
                assertTrue("W panelu nie ma węzłów mapy: $texts", texts.none { it.startsWith(L.f("a196_stage", 1, era) + ",") })
                val info = f.provider.createAccessibilityNodeInfo(GameAccessibility.INFO_SCREEN_BASE + Defs.ERA_COUNT)
                assertNotNull("Brak opisu etapu: $texts", info)
                val panel = info!!.contentDescription.toString()
                val stage = com.centurywar.game.Campaign.stages[1]
                assertTrue(panel, panel.contains(L[stage.weather.key]))
                assertTrue(panel, panel.contains(L["modifiers"]))
                assertTrue(panel, panel.startsWith(L.f("stage_n", 2)))
                assertEquals("android.widget.TextView", info.className.toString())
                // Poziomy trudności są zaznaczane; dokładnie jeden jest zaznaczony.
                val levels = f.nodes().filter { it.second.isCheckable }
                assertEquals(3, levels.size)
                assertEquals(1, levels.count { it.second.isChecked })

                f.click(f.find("Start") { it == L["start"] })
                val battle = f.game.current
                assertTrue("Po Start: $battle", battle is BattleScreen)
                assertEquals(1, (battle as BattleScreen).cfg.stageIndex)
            } finally { f.finish() }
        }
    }

    @Test fun eraIntroAndHintHideTheMapUntilDismissed() {
        val f = setup { p -> p.stars[0] = 3 }
        try {
            f.game.push(CampaignScreen(f.game))
            f.settle(3)
            val introId = GameAccessibility.INFO_SCREEN_BASE + Defs.ERA_COUNT
            val intro = f.provider.createAccessibilityNodeInfo(introId)
            assertNotNull("Zapowiedź epoki: ${f.texts()}", intro)
            assertEquals(1, f.root().childCount)
            assertTrue(intro!!.isClickable)
            assertTrue(f.click(introId))
            f.settle()
            assertTrue(f.texts().any { it.startsWith(L.f("a196_stage", 1, L[Defs.eraKeys[0]]) + ",") })
        } finally { f.finish() }

        // Dymek podpowiedzi badań: w drzewie tylko jego tekst i OK.
        val r = setup()
        try {
            r.game.push(ResearchScreen(r.game))
            r.settle(3)
            assertEquals("Dymek: ${r.texts()}", 2, r.root().childCount)
            val ok = r.find("OK") { it.contains(L["hint_ok"]) }
            assertTrue(r.click(ok))
            assertTrue(r.root().childCount > 2)
        } finally { r.finish() }
    }

    @Test fun researchScrollsRevealsCardsAndBuys() {
        val f = setup { p -> quiet(p); p.medals = 100_000 }
        try {
            f.game.push(ResearchScreen(f.game))
            f.settle()
            assertEquals(L["research"], f.root().contentDescription.toString())
            assertTrue(f.texts().toString(), f.texts().any { it == L.f("a196_medals", L.plural("medals_n", f.profile.medals.toLong())) })
            val seen = f.texts().toMutableSet()
            val actions = f.root().actionList.map { it.id }
            assertTrue("Korzeń przewijany", f.root().isScrollable)
            assertTrue(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD in actions)
            assertFalse(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD in actions)
            var moves = 0
            while (f.scroll(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                moves++
                assertTrue(moves < 30)
            }
            assertTrue(moves > 0)
            assertTrue("Przewinięcie odsłania nowe karty", f.texts().any { it !in seen })
            assertFalse("Na końcu listy przewinięcie zwraca false",
                f.provider.performAction(View.NO_ID, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null))
            assertTrue(f.scroll(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD))

            // Zakup przez węzeł karty: poziom rośnie, opis się zmienia.
            val card = f.find("karta") { it.contains(L["a196_afford"]) }
            val before = f.provider.createAccessibilityNodeInfo(card)!!.contentDescription.toString()
            val medals = f.profile.medals
            assertTrue(f.click(card))
            assertTrue(f.profile.medals < medals)
            assertNotEquals(before, f.provider.createAccessibilityNodeInfo(card)!!.contentDescription.toString())
        } finally { f.finish() }
    }

    @Test fun missionClaimAddsMedalsOnce() {
        val f = setup { p -> quiet(p) }
        try {
            val screen = MissionsScreen(f.game)
            f.game.push(screen)
            f.frame()
            val item = f.profile.missionState.daily[0]
            item.progress = item.target
            screen.layout()
            f.frame()
            val expected = L.f("a196_claim", com.centurywar.meta.MissionTexts.label(item),
                L.plural("medals_n", item.reward.toLong()))
            val claim = f.find("Odbierz") { it == expected }
            val medals = f.profile.medals
            assertTrue(f.click(claim))
            assertEquals(medals + item.reward, f.profile.medals)
            val after = f.provider.createAccessibilityNodeInfo(claim)!!
            assertFalse(after.isEnabled)
            assertTrue(after.contentDescription.toString().contains(L["ms_claimed"]))
            assertFalse(f.click(claim))
            assertEquals(medals + item.reward, f.profile.medals)
            assertTrue(f.texts().any { it.contains(L.f("a196_progress", item.target, item.target)) })
        } finally { f.finish() }
    }

    @Test fun idleFramesDoNotRebuildAndScrollDoes() {
        val f = setup { p -> quiet(p); p.stars[0] = 3; p.stars[1] = 3 }
        try {
            for (make in listOf<(Game) -> Screen>({ CampaignScreen(it) }, { ModeScreen(it, Mode.SKIRMISH) },
                { MissionsScreen(it) }, { AchievementsScreen(it) }, { BannerScreen(it) })) {
                f.game.push(make(f.game))
                f.settle(60)
                val revision = f.view.accessibilityRevision
                repeat(30) { f.frame(0.05f) }
                assertEquals("${f.game.current}: bez zmian brak przebudów", revision, f.view.accessibilityRevision)
                if (f.root().isScrollable) {
                    val forward = AccessibilityNodeInfo.ACTION_SCROLL_FORWARD in f.root().actionList.map { it.id }
                    assertTrue(f.scroll(if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                        else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD))
                    assertTrue(f.view.accessibilityRevision > revision)
                }
                f.game.pop()
                f.frame()
            }
        } finally { f.finish() }
    }

    @Test fun modeScreensDescribeRulesAndCheckDifficulty() {
        val f = setup { p -> quiet(p) }
        try {
            f.game.push(ModeScreen(f.game, Mode.SKIRMISH))
            f.settle()
            assertTrue(f.texts().any { it.contains(L.f("ai_power", (com.centurywar.game.Skirmish.power(f.profile.skirmishDiff) * 100).toInt())) })
            val levels = f.nodes().filter { it.second.isCheckable }
            assertEquals(4, levels.size)
            assertEquals(1, levels.count { it.second.isChecked })
            f.game.pop()
            f.game.push(ModeScreen(f.game, Mode.DAILY))
            f.settle()
            val weekly = f.find("tydzień") { it == L["weekly_week_tab"] }
            assertTrue(f.click(weekly))
            assertEquals(L["weekly"], f.root().contentDescription.toString())
            assertTrue(f.provider.createAccessibilityNodeInfo(weekly)!!.isSelected)
            assertTrue(f.texts().any { it.contains(L["weekly_start_card"]) })
        } finally { f.finish() }
    }

    @Test fun texts196ExistInAllLanguages() {
        val keys = listOf("a196_stage", "a196_stars", "a196_locked", "a196_next", "a196_enemy_weaker",
            "a196_enemy_equal", "a196_enemy_stronger", "a196_afford", "a196_no_medals", "a196_level",
            "a196_maxed", "a196_cost", "a196_claim", "a196_done", "a196_era", "a196_crowns", "a196_difficulty",
            "a196_weather", "a196_medals", "a196_progress", "a196_reward", "a196_power_down", "a196_power_up",
            "a196_unlocked")
        for (key in keys) {
            val t = L.raw(key)
            assertNotNull(key, t)
            assertEquals(key, L.CODES.size, t!!.size)
            for (s in t) assertTrue(key, s.isNotBlank())
            val args = Regex("%(\\d)\\$").findAll(t[0]).map { it.value }.toSet()
            for (s in t) assertEquals("$key: $s", args, Regex("%(\\d)\\$").findAll(s).map { it.value }.toSet())
        }
        assertNull(L.raw("a196_missing"))
    }
}
