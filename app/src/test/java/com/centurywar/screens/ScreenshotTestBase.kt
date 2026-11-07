package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.RectF
import com.centurywar.engine.Game
import com.centurywar.engine.Button
import com.centurywar.engine.Screen
import com.centurywar.engine.L
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
import com.centurywar.engine.Palette
import com.centurywar.engine.TouchEvent
import com.centurywar.game.Ai
import com.centurywar.game.AiPersonality
import com.centurywar.game.Modifier
import com.centurywar.meta.SkirmishSetup
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.BattleController
import com.centurywar.game.ReplayRecord
import com.centurywar.game.ReplayPlayer
import com.centurywar.game.BattleRenderer
import com.centurywar.game.EndlessWaveTheme
import com.centurywar.game.Bonuses
import com.centurywar.game.Campaign
import com.centurywar.game.Stage
import com.centurywar.game.CampaignDifficulty
import com.centurywar.game.Defs
import com.centurywar.game.Role
import com.centurywar.game.Mode
import com.centurywar.meta.MissionItem
import com.centurywar.meta.MissionType
import com.centurywar.meta.Missions
import com.centurywar.game.Weather
import com.centurywar.game.Daily
import com.centurywar.game.DailyEvent
import com.centurywar.game.Weekly
import com.centurywar.meta.OnlineLeaderboards
import com.centurywar.meta.OnlineState
import com.centurywar.meta.Profile
import com.centurywar.meta.ReplayStore
import com.centurywar.meta.BattleHistory
import com.centurywar.meta.ScoreClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Wspólne narzędzia klas ScreenshotTest* (zlecenie nocne 9: podział jednej klasy
 * na kilka, żeby dwa procesy Gradle dostawały podobną pracę). Klasa nie ma testów;
 * nazwy PNG, wzorce i asercje pozostają w podklasach bez zmian.
 */
abstract class ScreenshotTestBase {
    protected val out = File("build/screenshots").apply { mkdirs() }

    /** W CI każda ciężka metoda jest wykonywana raz na format, lokalnie na wszystkich. */
    protected fun screenshotDevices(devices: Map<String, IntArray>): Map<String, IntArray> {
        val target = System.getenv("CW_SCREENSHOT_DEVICE")?.trim().orEmpty()
        if (target.isEmpty()) return devices
        require(target in devices) { "Nieznany format zrzutu: $target" }
        return devices.filterKeys { it == target }
    }

    protected fun game(w: Int, h: Int, density: Float, q: Int): Game {
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val f = File.createTempFile("profile", ".json").apply { delete() }
        // Język profilu musi być znany przed Game, który nadaje domyślne imię.
        val profile = Profile(f).apply { lang = "pl"; tutorialDone = true; medals = 340 }
        val g = Game(host, profile, q)
        g.density = density
        g.resize(w, h, w, h)
        return g
    }

    protected fun shot(g: Game, name: String, frames: Int = 3) {
        // Liczba aktualizacji bez zmian; pełny raster powstaje tylko w ostatniej klatce.
        require(frames > 0)
        g.finishScreenTransition()
        val bmp = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
        try {
            if (frames > 1) {
                val tiny = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
                try {
                    val tickCanvas = Canvas(tiny)
                    repeat(frames - 1) { g.frame(tickCanvas, 1f / 60f) }
                } finally {
                    tiny.recycle()
                }
            }
            g.frame(Canvas(bmp), 1f / 60f)
            File(out, "$name.png").outputStream().use {
                check(bmp.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            bmp.recycle()
        }
    }

    protected fun tap(g: Game, x: Float, y: Float) {
        g.pushTouch(TouchEvent.DOWN, x, y, 0)
        g.pushTouch(TouchEvent.UP, x, y, 0)
    }

    protected fun field(o: Any, name: String) = o.javaClass.getDeclaredField(name).apply { isAccessible = true }

    /** Krótka poprawna powtórka do przeglądu odtwarzacza, bez dostępu do dysku. */
    protected val auditReplay by lazy {
        val cfg = BattleConfig(Mode.SKIRMISH, 299L, 0.65f,
            startEra = 2, maxEra = 4, playerBonuses = Bonuses(baseHpMul = 15f))
        val battle = Battle(cfg)
        val commands = BattleController(battle)
        repeat(240) { if (!battle.over) battle.step() }
        if (!battle.over) commands.surrender()
        ReplayRecord.encode(cfg, commands.log(), ReplayRecord.Meta(
            cfg.mode, cfg.stageIndex, battle.winner, battle.score(), battle.time,
            battle.wave, 1793461200000L, "Dowódca",
        ))
    }

    /**
     * Wspólna lista stanów menu: zarówno do kontroli geometrii, jak i opcjonalnych PNG.
     * Nie otwieramy bitwy (zlecenie 47).
     */
    protected fun visitAuditScreens(g: Game, visit: (String, Screen, String?) -> Unit) {
        fun show(name: String, screen: Screen, title: String? = null) {
            g.setScreen(screen)
            screen.update(0f)
            visit(name, screen, title)
        }
        show("menu", MenuScreen(g))
        val campaign = CampaignScreen(g)
        show("campaign", campaign, "campaign")
        field(campaign, "selected").set(campaign, Campaign.stages[2])
        show("campaign_info", campaign, "campaign")
        show("research", ResearchScreen(g), "research")
        show("achievements", AchievementsScreen(g), "achievements")
        show("missions", MissionsScreen(g), "ms_missions")
        show("mode_endless", ModeScreen(g, Mode.ENDLESS))
        show("mode_daily", ModeScreen(g, Mode.DAILY))
        show("mode_weekly", ModeScreen(g, Mode.DAILY, true))
        show("mode_skirmish", ModeScreen(g, Mode.SKIRMISH))
        val setup = SkirmishSetupScreen(g)
        show("skirmish_setup", setup)
        field(setup, "scroll").setFloat(setup, field(setup, "maxScroll").getFloat(setup))
        show("skirmish_modifiers", setup)
        show("rankings", RankingsScreen(g), "rankings")
        val weekly = RankingsScreen(g)
        field(weekly, "tab").setInt(weekly, 4)
        show("rankings_weekly", weekly, "rankings")
        show("global_rankings", GlobalRankingsScreen(g), "global_ranking")
        val stats = StatsScreen(g)
        show("stats", stats, "stats")
        stats.onClick(stats.buttons.first { it.id == 11 })
        show("stats_history", stats, "stats")
        show("replay_list", ReplayListScreen(g), "replay_list")
        show("replay_viewer", ReplayScreen(g, auditReplay))
        val settings = SettingsScreen(g)
        show("settings", settings, "settings")
        field(settings, "scroll").setFloat(settings, field(settings, "maxScroll").getFloat(settings))
        show("settings_bottom", settings, "settings")
        val access = AccessibilityScreen(g)
        show("accessibility", access, "accessibility")
        field(access, "scroll").setFloat(access, field(access, "maxScroll").getFloat(access))
        show("accessibility_bottom", access, "accessibility")
        show("hints", HintSettingsScreen(g), "hint_settings")
        val codex = CodexScreen(g, 0)
        show("codex", codex, "codex")
        for (category in 1 until 10) {
            codex.showCategory(category)
            show("codex_category_$category", codex, "codex")
        }
        val variantGuide = CodexScreen(g, Defs.eraOf(com.centurywar.game.EraArt.STEAM))
        variantGuide.showCategory(2)
        show("codex_variants", variantGuide, "codex")
        show("about", AboutScreen(g), "about")
        show("play_games", PlayGamesScreen(g), "play_games")
    }

    /** Bezpieczny obszar i rozłączne hitboxy; wstęga też zajmuje miejsce. */
    protected fun assertNoCollisions(g: Game, screen: Screen, name: String, title: String?) {
        val safe = RectF(g.insetL, g.insetT, g.width - g.insetR, g.height - g.insetB)
        val label = "$name ${g.width}x${g.height} ${g.profile.lang} ${g.profile.textScale}%"
        val buttons = screen.buttons.filter { it.visible && it.r.width() > 0f && it.r.height() > 0f }
        for (b in buttons) {
            assertTrue("$label: przycisk ${b.id} poza obszarem: ${b.r}",
                b.r.left >= safe.left && b.r.top >= safe.top &&
                    b.r.right <= safe.right && b.r.bottom <= safe.bottom)
        }
        for (i in buttons.indices) for (j in i + 1 until buttons.size) {
            val a = buttons[i]
            val b = buttons[j]
            assertTrue("$label: kolizja przycisków ${a.id} i ${b.id}",
                !RectF.intersects(a.r, b.r))
        }
        if (title != null) {
            val bounds = RectF()
            Chrome(screen).titleBounds(L[title], bounds)
            assertTrue("$label: wstęga poza obszarem: $bounds",
                bounds.left >= safe.left && bounds.top >= safe.top &&
                    bounds.right <= safe.right && bounds.bottom <= safe.bottom)
            for (b in buttons) assertTrue("$label: wstęga nachodzi na ${b.id}",
                !RectF.intersects(bounds, b.r))
        }
        if (screen is SettingsScreen) {
            // Suwaki są rysowane ręcznie i mają visible=false; sprawdzamy ich obszary osobno.
            val top = field(screen, "contentTop").getFloat(screen)
            val bottom = field(screen, "contentBottom").getFloat(screen)
            for (key in arrayOf("music", "sfxLevel")) {
                val b = field(screen, key).get(screen) as Button
                if (b.r.top >= top && b.r.bottom <= bottom) {
                    assertTrue("$label: suwak $key poza obszarem", safe.contains(b.r))
                    for (other in buttons) assertTrue("$label: suwak $key nachodzi na ${other.id}",
                        !RectF.intersects(b.r, other.r))
                }
            }
            if (name == "settings_bottom") {
                val reset = field(screen, "reset").get(screen) as Button
                assertTrue("$label: ostatni wiersz ustawień niewidoczny", reset.visible)
            }
        }
    }

    /** Geometria paneli modalnych, które celowo zasłaniają przyciski bitwy. */
    protected fun assert74Pause(g: Game, bs: BattleScreen, tag: String) {
        val safe = RectF(g.insetL, g.insetT, g.width - g.insetR, g.height - g.insetB)
        val panel = field(bs, "pausePanel").get(bs) as RectF
        assertTrue("$tag: panel pauzy poza ekranem: $panel", safe.contains(panel))
        val footer = field(bs, "pauseMenu").get(bs) as List<*>
        val controls = arrayOf(
            field(bs, "pauseVibrationBtn").get(bs) as Button,
            field(bs, "pauseSpeedBtn").get(bs) as Button,
        )
        val visible = footer.map { it as Button } + controls.toList()
        for (b in visible) {
            assertTrue("$tag: kontrolka pauzy ${b.id} poza panelem: ${b.r}",
                panel.contains(b.r))
        }
        for (i in visible.indices) for (j in i + 1 until visible.size) {
            assertTrue("$tag: nakładające się kontrolki pauzy ${visible[i].id}/${visible[j].id}",
                !RectF.intersects(visible[i].r, visible[j].r))
        }
        val sfx = field(bs, "pauseSfxRect").get(bs) as RectF
        val music = field(bs, "pauseMusicRect").get(bs) as RectF
        for (slider in arrayOf(sfx, music)) {
            assertTrue("$tag: suwak pauzy poza panelem", panel.contains(slider))
            for (b in visible) assertTrue("$tag: suwak nachodzi na kontrolkę ${b.id}",
                !RectF.intersects(slider, b.r))
        }
        assertTrue("$tag: suwaki pauzy na siebie nachodzą", !RectF.intersects(sfx, music))
    }

    /** Teksty i sylwetki wariantów mają rozłączne pola wewnątrz kart. */
    protected fun assert74Variants(g: Game, bs: BattleScreen, tag: String) {
        val dp = g.ui.dp
        val safe = RectF(g.insetL, g.insetT, g.width - g.insetR, g.height - g.insetB)
        @Suppress("UNCHECKED_CAST")
        val cards = field(bs, "variantBtn").get(bs) as Array<Button>
        val battle = field(bs, "battle").get(bs) as Battle
        val era = battle.pendingVariant ?: error("$tag: brak okna wariantu")
        val descriptions = field(bs, "variantDescCount").get(bs) as IntArray
        for (i in cards.indices) {
            val b = cards[i]
            assertTrue("$tag: karta wariantu $i niewidoczna", b.visible)
            assertTrue("$tag: karta wariantu $i poza ekranem: ${b.r}", safe.contains(b.r))
            val title = bs.variantCardName(era, i)
            val textW = b.r.width() - 16 * dp
            val titleSize = g.ui.fit(title, 19f, textW)
            assertTrue("$tag: tytuł wariantu nie mieści się w karcie",
                g.ui.measure(title, titleSize) <= textW + dp)
            val iconTop = bs.variantIconTop(i)
            // D70: dolna krawędź opisu w skali, w której karta była rysowana (HUD bitwy: do 115%).
            val lineEnd = b.r.top + bs.variantTextOffset(i)
            assertTrue("$tag: opis wariantu $i nachodzi na sylwetki: $lineEnd >= $iconTop",
                descriptions[i] == 0 || lineEnd + 3 * dp < iconTop)
            assertTrue("$tag: pola sylwetek za wąskie",
                b.r.width() / 4f > 12 * dp)
        }
        assertTrue("$tag: karty wariantów na siebie nachodzą",
            !RectF.intersects(cards[0].r, cards[1].r))
    }

    /** Powiadomienie w menu jest poniżej logo, ale nad pierwszym rzędem przycisków. */
    protected fun assert74Notice(g: Game, menu: MenuScreen, tag: String) {
        val banner = field(g, "achievementBanner").get(g) as RectF
        val first = field(menu, "campaign").get(menu) as Button
        val subtitleY = field(menu, "subY").getFloat(menu)
        assertTrue("$tag: baner zachodzi na podtytuł menu: $banner / $subtitleY",
            banner.top > subtitleY + 4 * g.ui.dp)
        assertTrue("$tag: baner zachodzi na przyciski menu: $banner / ${first.r}",
            banner.bottom <= first.r.top - 3 * g.ui.dp)
        val msg = field(g, "achievementMessage").get(g) as String
        val available = banner.width() - 61 * g.ui.dp
        assertTrue("$tag: tekst banera nie mieści się w panelu",
            g.ui.measure(msg, g.ui.fit(msg, 14f, available)) <= available + g.ui.dp)
    }

    protected fun clear74Notices(g: Game) {
        (field(g, "noticeQueue").get(g) as MutableCollection<*>).clear()
        (field(g, "noticeIcons").get(g) as MutableCollection<*>).clear()
        field(g, "achievementSeconds").setFloat(g, 0f)
    }
}
