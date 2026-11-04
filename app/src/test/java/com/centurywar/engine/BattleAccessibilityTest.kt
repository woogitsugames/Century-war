package com.centurywar.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import com.centurywar.R
import com.centurywar.engine.GameAccessibility.Companion.BTN_BASE
import com.centurywar.engine.GameAccessibility.Companion.INFO_BASES
import com.centurywar.engine.GameAccessibility.Companion.INFO_GOLD
import com.centurywar.engine.GameAccessibility.Companion.INFO_TUTORIAL
import com.centurywar.engine.GameAccessibility.Companion.INFO_XP
import com.centurywar.engine.GameAccessibility.Companion.STATUS
import com.centurywar.game.BattleConfig
import com.centurywar.game.Campaign
import com.centurywar.game.Defs
import com.centurywar.game.EndlessCard
import com.centurywar.game.Mode
import com.centurywar.game.Replay
import com.centurywar.game.Role
import com.centurywar.meta.Profile
import com.centurywar.screens.BarrageAiming
import com.centurywar.screens.BattleAccessibility
import com.centurywar.screens.BattleScreen
import com.centurywar.screens.MenuScreen
import java.io.File
import kotlin.math.abs
import kotlin.math.min
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

/** Dzień 5: TalkBack w bitwie (węzły HUD, akcje bez przeciągania, odświeżanie, komunikaty). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BattleAccessibilityTest {
    private val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
    }

    private class Fixture(val game: Game, val view: GameView, val bitmap: Bitmap, val screen: BattleScreen) {
        private val canvas = Canvas(bitmap)
        var now = 1_000_000L
        val provider get() = view.accessibilityNodeProvider!!
        val battle get() = screen.battle
        val revision get() = view.accessibilityRevision

        /** Klatka gry (dt w sekundach) i przesunięcie zegara przebudów o ten sam czas. */
        fun frame(dt: Float = 0f, ms: Long = (dt * 1000f).toLong()) {
            game.frame(canvas, dt)
            now += ms
            view.refreshAccessibilityNodes()
        }

        /** Krok gry bez rysowania (długie pętle; rysowanie Canvas w Robolectric jest wolne). */
        fun step(dt: Float = 1f / 60f, ms: Long = (dt * 1000f).toLong()) {
            game.current!!.advanceTouch(dt)
            game.current!!.update(dt)
            now += ms
            view.refreshAccessibilityNodes()
        }

        fun node(id: Int): AccessibilityNodeInfo? = provider.createAccessibilityNodeInfo(id)
        fun text(id: Int): String = node(id)!!.contentDescription.toString()

        fun action(id: Int, action: Int): Boolean {
            val ok = provider.performAction(id, action, null)
            frame()
            return ok
        }

        fun children(): List<Int> {
            val root = provider.createAccessibilityNodeInfo(View.NO_ID)!!
            // Robolectric nie zwraca identyfikatorów dzieci, więc sprawdzamy znane zakresy.
            val ids = ArrayList<Int>()
            for (id in BTN_BASE until BTN_BASE + 1000) if (node(id) != null) ids.add(id)
            for (id in 20_001..20_100) if (node(id) != null) ids.add(id)
            assertEquals(root.childCount, ids.size)
            return ids
        }

        fun finish() { bitmap.recycle() }
    }

    private fun setup(
        cfg: BattleConfig, w: Int = 1600, h: Int = 720, density: Float = 2f,
        tutorialSeen: Boolean = true, leftHanded: Boolean = false,
    ): Fixture {
        val file = File.createTempFile("cw-a11y-d5-", ".json").apply { delete() }
        val profile = Profile(file).apply {
            lang = "pl"
            tutorialDone = tutorialSeen
            leftHandedHud = leftHanded
        }
        L.setCode("pl")
        val game = Game(host, profile, Quality.LOW).apply {
            this.density = density
            resize(w, h, w, h)
            setScreen(MenuScreen(this))
        }
        val view = GameView(RuntimeEnvironment.getApplication(), game)
        view.layout(0, 0, w, h)
        val screen = BattleScreen(game, cfg)
        game.setScreen(screen)
        val f = Fixture(game, view, Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888), screen)
        view.setAccessibilityClock { f.now }
        f.frame()
        f.frame(1f / 60f)
        return f
    }

    private fun skirmish(seed: Long = 7L) = BattleConfig(Mode.SKIRMISH, seed, 1f)

    @Test fun hudHasStableNodesWithNamesCostsAndUpgradeAction() {
        val f = setup(skirmish())
        try {
            repeat(5) { f.frame(1f / 60f) }
            for (id in intArrayOf(10_000, 10_001, 10_002, 10_003, 10_012, 10_017, INFO_GOLD, INFO_XP, INFO_BASES, STATUS))
                assertNotNull("Brak węzła $id", f.node(id))
            val unit = f.node(10_000)!!
            assertEquals("android.widget.Button", unit.className.toString())
            val name = BattleAccessibility.unitName(f.screen, 0)
            val cost = f.battle.unitCost(f.battle.player, Role.MELEE)
            assertTrue(unit.contentDescription.toString(), unit.contentDescription.toString().contains(name))
            assertTrue(unit.contentDescription.toString(), unit.contentDescription.toString().contains(cost.toString()))
            assertTrue("Brak akcji Ulepsz", unit.actionList.any { it.id == R.id.cw_a11y_upgrade })
            val gold = f.node(INFO_GOLD)!!
            assertEquals("android.widget.TextView", gold.className.toString())
            assertFalse(gold.isClickable)
            assertTrue(gold.contentDescription.toString().startsWith("Złoto: "))
            val barrage = f.node(10_012)!!
            for (id in intArrayOf(R.id.cw_a11y_barrage_cluster, R.id.cw_a11y_barrage_front, R.id.cw_a11y_barrage_base))
                assertTrue("Brak akcji ostrzału $id", barrage.actionList.any { it.id == id })
            assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE, f.node(STATUS)!!.liveRegion)
            // Kolejność przechodzenia: informacje, jednostki, akcje, awans, pauza, komunikaty.
            val order = f.children()
            assertEquals(STATUS, order.last())
            // Id przycisków bitwy są unikalne i mieszczą się w zakresie węzłów przycisków.
            val ids = f.screen.buttons.map { it.id }
            assertEquals(ids.size, ids.toSet().size)
            assertTrue(ids.all { it in 0 until 10_000 })
        } finally { f.finish() }
    }

    @Test fun sameButtonKeepsIdWithCommanderAndFocusSurvivesRebuild() {
        val first = setup(Campaign.config(Campaign.stages[0], Profile(File.createTempFile("cw-b", ".json").apply { delete() }).bonuses(), 3L))
        val barrageDesc: String
        try {
            assertNotNull(first.node(10_012))
            assertNull("Etap 1 nie ma dowódcy", first.node(10_203))
            barrageDesc = first.text(10_012)
        } finally { first.finish() }
        val f = setup(Campaign.config(Campaign.stages[7], Profile(File.createTempFile("cw-b", ".json").apply { delete() }).bonuses(), 3L))
        try {
            assertNotNull("Etap 8 ma dowódcę", f.node(10_203))
            assertEquals(barrageDesc, f.text(10_012))
            assertTrue(f.provider.performAction(10_012, AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null))
            val revision = f.revision
            f.battle.player.gold = if (f.screen.commanderBtn.enabled) 0f else 5000f
            f.frame()
            assertTrue("Zmiana dostępności dowódcy przebudowuje drzewo", f.revision > revision)
            val focus = f.provider.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            assertNotNull("Fokus przeżył przebudowę", focus)
            assertEquals(barrageDesc, focus!!.contentDescription.toString())
        } finally { f.finish() }
    }

    @Test fun unitClickQueuesAndDisabledUnitRejects() {
        val f = setup(skirmish())
        try {
            f.battle.player.gold = 1000f
            f.frame()
            val before = f.battle.player.queueSize
            assertTrue(f.action(10_000, AccessibilityNodeInfo.ACTION_CLICK))
            assertEquals(before + 1, f.battle.player.queueSize)
            f.battle.player.gold = 0f
            f.frame()
            assertFalse(f.node(10_000)!!.isEnabled)
            assertTrue(f.node(10_000)!!.stateDescription.toString().contains(L["a178_no_gold"]))
            assertFalse(f.provider.performAction(10_000, AccessibilityNodeInfo.ACTION_CLICK, null))
            f.frame()
            assertEquals(before + 1, f.battle.player.queueSize)
        } finally { f.finish() }
    }

    @Test fun upgradeActionOpensSamePanelAsArrow() {
        val f = setup(skirmish())
        try {
            assertTrue(f.action(10_001, R.id.cw_a11y_upgrade))
            assertEquals(1, f.screen.showUpgrade)
            assertNotNull(f.node(GameAccessibility.INFO_UPGRADE))
            assertNotNull(f.node(BTN_BASE + BattleScreen.UNIT_UPGRADE_BUY))
            assertNull("Panel ulepszenia zasłania HUD", f.node(10_000))
            assertTrue(f.action(BTN_BASE + BattleScreen.UNIT_UPGRADE_CLOSE, AccessibilityNodeInfo.ACTION_CLICK))
            assertEquals(-1, f.screen.showUpgrade)
        } finally { f.finish() }
    }

    @Test fun barrageActionFiresWithoutAimingAndAvoidsOwnUnits() {
        val f = setup(skirmish(14201L))
        try {
            val b = f.battle
            val p = b.player
            p.gold = 5000f
            repeat(4) { f.screen.commands.queue(Role.MELEE) }
            var steps = 0
            while (b.units.count { it.alive && it.side == 0 } < 3 && steps++ < 4000) b.step()
            val own = b.units.filter { it.alive && it.side == 0 }
            val near = p.baseFront + 220f
            val positions = floatArrayOf(near + 80f, near + 260f, near + 430f)
            for (i in positions.indices) own[i].x = positions[i]
            for (k in own.indices) if (k >= positions.size) own[k].x = p.baseFront + 10f
            p.front = positions.last()
            p.abilityCd[0] = 0f
            f.frame()
            assertTrue(b.abilityReady(p, 0))
            assertTrue(f.action(10_012, R.id.cw_a11y_barrage_cluster))
            assertTrue("Ostrzał ma odnowienie", p.abilityCd[0] > 0f)
            assertFalse("Akcja nie włącza celowania palcem", f.screen.aimingBarrage)
            val cmd = f.screen.commands.log().commands.last { it.kind == Replay.Kind.BARRAGE_AT }
            for (u in b.units) if (u.alive && u.side == 0)
                assertTrue("Cel ${cmd.argument} za blisko własnej jednostki ${u.x}",
                    abs(u.x - cmd.argument) >= BarrageAiming.SPREAD - 1f)
            assertTrue(f.node(10_012)!!.stateDescription.toString().startsWith("odnowienie"))
            // ACTION_CLICK = pierwsza akcja, także bez celowania.
            p.abilityCd[0] = 0f
            f.frame()
            val count = f.screen.commands.log().commands.count { it.kind == Replay.Kind.BARRAGE_AT }
            assertTrue(f.action(10_012, AccessibilityNodeInfo.ACTION_CLICK))
            assertFalse(f.screen.aimingBarrage)
            assertEquals(count + 1, f.screen.commands.log().commands.count { it.kind == Replay.Kind.BARRAGE_AT })
        } finally { f.finish() }
    }

    @Test fun endlessCardChoiceHasDescriptionsPaneTitleAndClick() {
        val f = setup(BattleConfig(Mode.ENDLESS, 17L, 1f))
        try {
            val cards = arrayOf(EndlessCard.QUARTERMASTER, EndlessCard.MASTER_MASONS, EndlessCard.ECONOMY)
            f.battle.pendingCards = cards
            f.frame()
            val children = f.children()
            for (i in 0..2) {
                val id = BTN_BASE + BattleScreen.DOCTRINE0 + i
                assertTrue(id in children)
                assertTrue(f.text(id), f.text(id).contains(L[cards[i].textKey]))
                assertTrue(f.text(id), f.text(id).length > L[cards[i].textKey].length + 10)
            }
            assertNull("Wybór zasłania HUD", f.node(10_000))
            assertEquals(L["choose_card"], f.node(View.NO_ID)!!.paneTitle.toString())
            assertTrue(f.action(BTN_BASE + BattleScreen.DOCTRINE0 + 1, AccessibilityNodeInfo.ACTION_CLICK))
            assertNull(f.battle.pendingCards)
            assertTrue(f.screen.commands.log().commands.any { it.kind == Replay.Kind.CARD && it.argument == 1 })
            assertNotNull("Po wyborze wraca HUD", f.node(10_000))
        } finally { f.finish() }
    }

    @Test fun rebuildsAreThrottledAndIdleFramesDoNotRebuild() {
        val f = setup(skirmish())
        try {
            val start = f.revision
            val time0 = f.battle.time
            repeat(600) { f.step(1f / 60f, 16L) }
            val seconds = (f.battle.time - time0).coerceAtLeast(1f)
            val rebuilds = f.revision - start
            assertTrue("Przebudowy: $rebuilds w ${seconds}s", rebuilds <= 2 * seconds)
            assertTrue("Teksty się odświeżają", rebuilds > 0)
            repeat(70) { f.step(0f, 16L) }
            val idle = f.revision
            repeat(600) { f.step(0f, 16L) }
            assertEquals("Bez zmian w bitwie nie ma przebudów", idle, f.revision)
        } finally { f.finish() }
    }

    @Test fun limitedSpeedJitterDoesNotRebuildEveryFrame() {
        val f = setup(skirmish())
        try {
            f.screen.speed = 5
            repeat(70) { f.step(0f, 16L) }
            val start = f.revision
            val ms0 = f.now
            for (i in 0 until 600) {
                f.game.current!!.advanceTouch(0f)
                f.game.current!!.update(0f)
                // Tempo ograniczone budżetem CPU waha się między ×4 a ×5 co klatkę.
                f.screen.effectiveSpeed = if (i % 2 == 0) 4.6f else 5f
                f.now += 16L
                f.view.refreshAccessibilityNodes()
            }
            val seconds = (f.now - ms0) / 1000f
            val rebuilds = f.revision - start
            assertTrue("Przebudowy: $rebuilds w ${seconds}s", rebuilds <= 2 * seconds)
        } finally { f.finish() }
    }

    @Test fun noRefreshWithoutAccessibilityService() {
        val f = setup(skirmish())
        try {
            val revision = f.revision
            f.battle.player.gold = 0f
            f.game.frame(Canvas(f.bitmap), 0f)
            f.view.refreshAccessibilityIfEnabled()
            assertEquals(revision, f.revision)
        } finally { f.finish() }
    }

    private fun assertButtonsDoNotOverlap(f: Fixture, tag: String) {
        val s = f.screen
        val hud = (s.unitBtn.toList() + s.actionBtns.toList() + listOf(s.evolveBtn, s.pauseBtn))
            .filter { it.visible }.map { BTN_BASE + it.id }
        val dp = f.game.ui.dp
        val size = s.unitBtn[0].r.width()
        val minSide = min(48 * dp, size) - 1f
        val boxes = hud.map { id ->
            val node = f.node(id)
            assertNotNull("$tag: brak węzła $id", node)
            Rect().also { node!!.getBoundsInParent(it) }
        }
        for (i in boxes.indices) {
            val a = boxes[i]
            assertTrue("$tag: węzeł ${hud[i]} ma ${a.width()}×${a.height()} px, min ${minSide}",
                a.width() >= minSide && a.height() >= minSide)
            for (j in i + 1 until boxes.size)
                assertFalse("$tag: ${hud[i]} $a nachodzi na ${hud[j]} ${boxes[j]}", Rect.intersects(a, boxes[j]))
        }
    }

    @Test fun hudBoundsDoNotOverlapInAllLayouts() {
        val stage8 = { Campaign.config(Campaign.stages[7], Profile(File.createTempFile("cw-b", ".json").apply { delete() }).bonuses(), 5L) }
        for ((tag, f) in listOf(
            "phone720" to { setup(stage8(), 1600, 720, 2f) },
            "fold_outer" to { setup(stage8(), 904, 2316, 2.625f) },
            "left_handed" to { setup(stage8(), 1600, 720, 2f, leftHanded = true) },
        )) {
            val fx = f()
            try {
                if (tag == "fold_outer") assertTrue("Fold: trzy rzędy HUD", fx.screen.threeRowHud)
                assertButtonsDoNotOverlap(fx, tag)
            } finally { fx.finish() }
        }
    }

    @Test fun statusAnnouncesEraAndLimitsMessages() {
        val f = setup(skirmish())
        try {
            val p = f.battle.player
            p.xp = Defs.evolveXp[p.era].toFloat()
            assertTrue(f.screen.commands.evolve())
            f.frame()
            val eraName = L[Defs.eraKeys[p.era]]
            assertEquals(L.f("a178_era_up", eraName), f.text(STATUS))
            // Wybór (wariant lub doktryna) otworzył się w tej samej chwili: czeka 3 s.
            f.frame(0f, 1000L)
            assertEquals(L.f("a178_era_up", eraName), f.text(STATUS))
            f.frame(0f, 2100L)
            assertTrue(f.text(STATUS), f.text(STATUS).startsWith(L.f("a178_choice_open", "").trimEnd()))
            // Ważniejszy komunikat nie czeka.
            p.baseHp = p.baseMaxHp * 0.2f
            f.frame(0f, 100L)
            assertEquals(L["a178_base_low"], f.text(STATUS))
        } finally { f.finish() }
    }

    @Test fun tutorialExposesStepTextAndOnlyAllowedButtons() {
        val f = setup(Campaign.config(Campaign.stages[0], Profile(File.createTempFile("cw-b", ".json").apply { delete() }).bonuses(), 1L, tutorial = true),
            tutorialSeen = false)
        try {
            val tut = f.screen.tut
            assertNotNull(tut)
            var guard = 0
            while (f.node(INFO_TUTORIAL) == null && guard++ < 900) f.step(1f / 60f)
            assertNotNull("Brak kroku samouczka", f.node(INFO_TUTORIAL))
            assertTrue(f.text(INFO_TUTORIAL), f.text(INFO_TUTORIAL).contains(L["tut_melee_t"]))
            assertNotNull(f.node(10_000))
            assertNull("Krok „piechota” nie przepuszcza łuczników", f.node(10_001))
            val queued = f.battle.player.queueSize
            assertFalse(f.provider.performAction(10_001, AccessibilityNodeInfo.ACTION_CLICK, null))
            f.frame()
            assertEquals(queued, f.battle.player.queueSize)
            assertTrue(f.action(10_000, AccessibilityNodeInfo.ACTION_CLICK))
            assertEquals(queued + 1, f.battle.player.queueSize)
        } finally { f.finish() }
    }

    @Test fun texts178ExistInAllLanguages() {
        val keys = L.keys.filter { it.startsWith("a178_") }
        assertTrue(keys.size >= 30)
        for (key in keys) {
            val translations = L.raw(key)!!
            assertEquals(key, L.CODES.size, translations.size)
            for (s in translations) assertTrue(key, s.isNotBlank())
        }
    }
}
