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
 * Renderuje ekrany gry do PNG (Robolectric + natywna Skia), żeby obejrzeć UI
 * bez telefonu. Rozdzielczości: tani telefon 720p, flagowiec 20:9, rozłożony Fold.
 * Pliki trafiają do build/screenshots/.
 *
 * Część klasy dawnego ScreenshotTest (podział mechaniczny, noc 9); narzędzia:
 * [ScreenshotTestBase].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ScreenshotTestLayout : ScreenshotTestBase() {
    /** Podgląd wszystkich sześciu zdarzeń na 720p i rozłożonym Foldzie. */
    @Test
    fun specialDailyCards() {
        val dates = LinkedHashMap<DailyEvent, Long>()
        for (day in Daily.RELEASE_DAY + 3L..Daily.RELEASE_DAY + 720L step 4L) {
            val event = Daily.config(day, Bonuses()).dailyEvent ?: continue
            if (event !in dates) dates[event] = day
            if (dates.size == DailyEvent.entries.size) break
        }
        assertTrue(dates.size == DailyEvent.entries.size)
        for ((tag, dims) in mapOf(
            "phone720" to intArrayOf(1600, 720, 2),
            "foldOpen" to intArrayOf(2184, 1968, 3),
            "foldPortrait" to intArrayOf(1968, 2184, 3),
        )) {
            val g = game(dims[0], dims[1], dims[2] * 0.9f,
                if (tag == "phone720") Quality.LOW else Quality.HIGH)
            for ((event, day) in dates) {
                val screen = ModeScreen(g, Mode.DAILY, previewDailyDay = day)
                g.setScreen(screen)
                shot(g, "${tag}_daily_event_${event.name.lowercase()}")
                assertTrue((field(screen, "dailyEventTitle").get(screen) as String).isNotBlank())
            }
        }
    }

    /**
     * Zrzuty czterech palet i powiększonego tekstu w czterech rozdzielczościach.
     * Matryce transformacji barw wzorowane na symulacji Machado (pełne zaburzenie).
     */
    @Test
    fun accessibility() {
        val vision = arrayOf(
            "protan" to floatArrayOf(
                0.152286f, 1.052583f, -0.204868f,
                0.114503f, 0.786281f, 0.099216f,
                -0.003882f, -0.048116f, 1.051998f,
            ),
            "deutan" to floatArrayOf(
                0.367322f, 0.860646f, -0.227968f,
                0.280085f, 0.672501f, 0.047413f,
                -0.011820f, 0.042940f, 0.968881f,
            ),
            "tritan" to floatArrayOf(
                1.255528f, -0.076749f, -0.178779f,
                -0.078411f, 0.930809f, 0.147602f,
                0.004733f, 0.691367f, 0.303900f,
            ),
        )
        for ((tag, dims) in mapOf(
            "phone720" to intArrayOf(1600, 720, 2),
            "phone20x9" to intArrayOf(2400, 1080, 3),
            "foldOpen" to intArrayOf(2184, 1968, 3),
            "foldPortrait" to intArrayOf(1968, 2184, 3),
        )) {
            val g = game(dims[0], dims[1], dims[2] * 0.9f,
                if (tag == "phone720") Quality.LOW else Quality.HIGH)
            g.profile.textScale = 130
            g.profile.teamMarkers = true
            g.applyAccessibility()
            g.setScreen(MenuScreen(g)); shot(g, "${tag}_access_text130_menu", 8)
            g.setScreen(SettingsScreen(g)); shot(g, "${tag}_access_text130_settings")
            g.setScreen(ResearchScreen(g)); shot(g, "${tag}_access_text130_research")
            g.setScreen(MissionsScreen(g)); shot(g, "${tag}_access_text130_missions")
            g.setScreen(CodexScreen(g, 0)); shot(g, "${tag}_access_text130_codex")
            for (pal in 0 until Palette.COUNT) {
                g.profile.palette = pal
                g.applyAccessibility()
                val a = AccessibilityScreen(g)
                g.setScreen(a)
                shot(g, "${tag}_access_palette${pal}_options")
                field(a, "scroll").setFloat(a, field(a, "maxScroll").getFloat(a))
                a.layout()
                shot(g, "${tag}_access_palette${pal}_options_bottom")

                val bs = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 131L, 1f, startEra = 2, maxEra = 7))
                g.setScreen(bs)
                val battle = field(bs, "battle").get(bs) as Battle
                battle.spawn(battle.player, Defs.unit(2, Role.MELEE))
                battle.spawn(battle.enemy, Defs.unit(2, Role.MELEE))
                val center = Defs.LANE / 2f
                for (u in battle.units) if (u.active) {
                    u.x = center + if (u.side == 0) -65f else 65f
                    u.hp = u.maxHp * 0.64f
                    u.prevX = Float.NaN
                }
                val r = field(bs, "r").get(bs) as com.centurywar.game.BattleRenderer
                r.camX = center - r.worldVisible / 2f
                r.clampCam()
                val name = "${tag}_access_palette${pal}_battle"
                shot(g, name, 2)
                if (tag == "phone720") {
                    val raw = requireNotNull(BitmapFactory.decodeFile(File(out, "${name}.png").path)) {
                        "Nie udało się odczytać zrzutu: $name"
                    }
                    for ((visionName, matrix) in vision) {
                        val pixels = IntArray(raw.width * raw.height)
                        raw.getPixels(pixels, 0, raw.width, 0, 0, raw.width, raw.height)
                        for (i in pixels.indices) {
                            val color = pixels[i]
                            val red = Color.red(color).toFloat()
                            val green = Color.green(color).toFloat()
                            val blue = Color.blue(color).toFloat()
                            val nr = (matrix[0] * red + matrix[1] * green + matrix[2] * blue).toInt().coerceIn(0, 255)
                            val ng = (matrix[3] * red + matrix[4] * green + matrix[5] * blue).toInt().coerceIn(0, 255)
                            val nb = (matrix[6] * red + matrix[7] * green + matrix[8] * blue).toInt().coerceIn(0, 255)
                            pixels[i] = Color.argb(Color.alpha(color), nr, ng, nb)
                        }
                        val bmp = Bitmap.createBitmap(raw.width, raw.height, Bitmap.Config.ARGB_8888)
                        bmp.setPixels(pixels, 0, raw.width, 0, 0, raw.width, raw.height)
                        File(out, "${name}_vision_${visionName}.png").outputStream().use {
                            bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                        bmp.recycle()
                    }
                    raw.recycle()
                }
            }
        }
    }

    /** Układ modala i Kodeksu 720p / Fold, po polsku i po angielsku. */
    @Test
    fun variantScreens() {
        val priorLang = com.centurywar.engine.L.lang
        try {
            val steam = Defs.eraOf(com.centurywar.game.EraArt.STEAM)
            for ((tag, dims) in mapOf(
                "phone720" to intArrayOf(1600, 720, 2),
                "foldLandscape" to intArrayOf(2184, 1968, 3),
                "foldPortrait" to intArrayOf(1968, 2184, 3),
            )) {
                val g = game(dims[0], dims[1], dims[2] * 0.9f, Quality.HIGH)
                for (lang in arrayOf("pl", "en")) {
                    com.centurywar.engine.L.setCode(lang)
                    g.setScreen(BattleScreen(g, BattleConfig(Mode.SKIRMISH, 9900L, 1f,
                        startEra = steam, maxEra = steam)))
                    shot(g, "variant_modal_${tag}_${lang}")
                    val variantCodex = CodexScreen(g, steam)
                    g.setScreen(variantCodex)
                    variantCodex.showCategory(2)
                    shot(g, "variant_codex_${tag}_${lang}")
                }
            }
        } finally {
            com.centurywar.engine.L.lang = priorLang
        }
    }

    /** Widok pusty, statystyki, historia i szczegóły na czterech proporcjach ekranu. */
    @Test
    fun battleHistoryScreens() {
        for ((tag, dims) in mapOf(
            "phone720" to intArrayOf(1600, 720, 2),
            "phone20x9" to intArrayOf(2400, 1080, 3),
            "foldOpen" to intArrayOf(2184, 1968, 3),
            "foldPortrait" to intArrayOf(1968, 2184, 3),
            "foldInner" to intArrayOf(1802, 2000, 4),
            "foldOuter" to intArrayOf(904, 2316, 3),
        )) {
            val g = game(dims[0], dims[1], dims[2] * 0.9f,
                if (tag == "phone720") Quality.LOW else Quality.HIGH)
            val local = File.createTempFile("cw-history-shot", ".json").apply { delete() }
            val archive = BattleHistory(local)
            g.history = archive
            val empty = StatsScreen(g)
            g.setScreen(empty)
            shot(g, tag + "_stats_empty")
            empty.onClick(empty.buttons.first { it.id == 11 })
            shot(g, tag + "_history_empty")

            val now = System.currentTimeMillis()
            archive.add(BattleHistory.Record(
                date = now - 86_400_000L, mode = Mode.SKIRMISH.ordinal,
                result = BattleHistory.LOSS, score = 1490, seconds = 230,
                kills = 24, lost = 31, era = 4, advanced = 3,
                roles = intArrayOf(12, 10, 5, 4), doctrines = 3, turrets = 2,
                barrages = 3, rallies = 1, gold = 4500, baseDamage = 350,
                aiPower = 110, personality = 1, medals = 7, tipKey = "tip_eco",
            ))
            archive.add(BattleHistory.Record(
                date = now, mode = Mode.ENDLESS.ordinal, result = BattleHistory.WIN,
                score = 9350, seconds = 420, kills = 45, lost = 40,
                era = 5, advanced = 4, roles = intArrayOf(7, 15, 12, 6),
                doctrines = 7, cards = IntArray(30) { if (it < 2) it + 1 else 0 },
                turrets = 4, barrages = 5, rallies = 2, gold = 9000,
                baseDamage = 600, wave = 17, bosses = 2, medals = 70,
            ))
            g.profile.recordBattle(Mode.SKIRMISH, false, 230)
            g.profile.recordBattle(Mode.ENDLESS, true, 420)
            g.profile.submit(Profile.BOARD_ENDLESS, 9350, 20_000L)
            val stats = StatsScreen(g)
            g.setScreen(stats)
            shot(g, tag + "_stats")
            stats.onClick(stats.buttons.first { it.id == 11 })
            shot(g, tag + "_history")
            val panel = field(stats, "panel").get(stats) as android.graphics.RectF
            stats.onTap(panel.centerX(), panel.top + 40 * g.ui.dp)
            shot(g, tag + "_history_detail")
            archive.awaitWrites()
            archive.close()
        }
    }

    /** Zlecenie 105: historia 30 bitew, fale i czas dzienny na 720p i Foldzie. */
    @Test
    fun statsCharts105() {
        for ((tag, dims) in screenshotDevices(mapOf(
            "phone720" to intArrayOf(1600, 720, 2),
            "foldOpen" to intArrayOf(2184, 1968, 3),
        ))) {
            val g = game(dims[0], dims[1], dims[2] * 0.9f,
                if (tag == "phone720") Quality.LOW else Quality.HIGH)
            val archive = BattleHistory(File.createTempFile("cw-stats105", ".json").apply { delete() })
            try {
                val now = System.currentTimeMillis()
                // Dodaj od najstarszej do najnowszej: add() wstawia nowe bitwy na początek.
                for (i in 39 downTo 0) {
                    val mode = Mode.entries[i % 4]
                    val won = i % 3 == 0
                    val seconds = 120 + i * 15
                    archive.add(BattleHistory.Record(
                        date = now - (i / 3).toLong() * 86_400_000L,
                        mode = mode.ordinal,
                        result = if (won) BattleHistory.WIN else BattleHistory.LOSS,
                        seconds = seconds,
                        wave = if (mode == Mode.ENDLESS) 42 - i else 0,
                        score = 1000L + i * 150L,
                    ))
                    g.profile.recordBattle(mode, won, seconds)
                }
                g.history = archive
                g.historyLoaded = true
                val screen = StatsScreen(g)
                g.setScreen(screen)
                val metrics = field(screen, "metrics").get(screen) as BattleHistory.Metrics
                assertTrue(metrics.recentModes.size == 30)
                shot(g, tag + "_stats105_top")
                val maxScroll = field(screen, "maxScroll").getFloat(screen)
                val target = (if (tag == "phone720") 580f else 470f) * g.ui.dp
                field(screen, "scroll").setFloat(screen, minOf(target, maxScroll))
                shot(g, tag + "_stats105_charts")
                field(screen, "scroll").setFloat(screen, minOf(940f * g.ui.dp, maxScroll))
                shot(g, tag + "_stats105_daily")
            } finally {
                archive.awaitWrites()
                archive.close()
            }
        }
    }

    /** Wszystkie działy Kodeksu: góra i dół na 720p oraz Foldzie w pionie, także przy 130%. */
    @Test
    fun codexAllCategories() {
        val priorLang = L.lang
        try {
            L.setCode("pl")
            for ((tag, dims) in mapOf(
                "phone720" to intArrayOf(1600, 720, 2),
                "foldPortrait" to intArrayOf(1968, 2184, 3),
                "foldInner" to intArrayOf(1802, 2000, 4),
                "foldOuter" to intArrayOf(904, 2316, 3),
            )) {
                val g = game(dims[0], dims[1], dims[2] * 0.9f,
                    if (tag == "phone720") Quality.LOW else Quality.HIGH)
                g.profile.textScale = 130
                g.applyAccessibility()
                val codex = CodexScreen(g, Defs.eraOf(com.centurywar.game.EraArt.STEAM))
                g.setScreen(codex)
                for (category in 0 until 10) {
                    codex.showCategory(category)
                    shot(g, "${tag}_codex_section_${category}_top")
                    codex.onDrag(0f, -100000f)
                    shot(g, "${tag}_codex_section_${category}_bottom")
                }
            }
        } finally {
            L.lang = priorLang
        }
    }

    /** Cztery formaty, wszystkie języki, tekst 130%; bez generowania plików PNG. */
    @Test
    fun layoutCollisions() {
        val originalLang = L.lang
        try {
            for ((tag, dims) in screenshotDevices(mapOf(
                "phone720" to intArrayOf(1600, 720, 2),
                "phone1280" to intArrayOf(1280, 720, 2),
                "phone20x9" to intArrayOf(2400, 1080, 3),
                "foldOpen" to intArrayOf(2184, 1968, 3),
                "foldPortrait" to intArrayOf(1968, 2184, 3),
                "foldInner" to intArrayOf(1802, 2000, 4),
                "foldOuter" to intArrayOf(904, 2316, 3),
                "tablet4x3" to intArrayOf(2048, 1536, 2),
                "phone21x9" to intArrayOf(2520, 1080, 3),
            ))) {
                val density = if (tag == "tablet4x3" || tag == "phone21x9")
                    dims[2].toFloat() else dims[2] * 0.9f
                val g = game(dims[0], dims[1], density,
                    if (tag == "phone720") Quality.LOW else Quality.HIGH)
                if (tag == "phone20x9") g.setInsets(110, 0, 70, 40)
                if (tag == "phone21x9") g.setInsets(110, 0, 70, 65)
                val scoreFile = File.createTempFile("cw-layout-scores", ".json").apply { delete() }
                val scores = ScoreClient(scoreFile, "https://scores.example.invalid",
                    "testing-key-at-least-32-characters-long", g.profile)
                g.scores = scores
                g.online = object : OnlineLeaderboards {
                    override val available = true
                    override val state = OnlineState.SIGNED_OUT
                    override fun submit(board: String, score: Long) {}
                }
                try {
                    for (lang in L.CODES) {
                        g.profile.lang = lang
                        g.onLanguageChanged()
                        g.profile.textScale = 130
                        g.applyAccessibility()
                        visitAuditScreens(g) { name, screen, title ->
                            assertNoCollisions(g, screen, name, title)
                        }
                    }
                } finally {
                    scores.close()
                }
            }
        } finally {
            L.lang = originalLang
        }
    }

    /**
     * Pełna matryca zrzutów tylko na żądanie: LAYOUT_AUDIT=true ./gradlew
     * :app:testDebugUnitTest --tests '*ScreenshotTestLayout.layoutAudit*'.
     */
    @Test
    fun layoutAudit() {
        if (!"true".equals(System.getenv("LAYOUT_AUDIT"), ignoreCase = true)) return
        val originalLang = L.lang
        try {
            for ((tag, dims) in mapOf(
                "phone720" to intArrayOf(1600, 720, 2),
                "phone1280" to intArrayOf(1280, 720, 2),
                "phone20x9" to intArrayOf(2400, 1080, 3),
                "foldOpen" to intArrayOf(2184, 1968, 3),
                "foldPortrait" to intArrayOf(1968, 2184, 3),
                "foldInner" to intArrayOf(1802, 2000, 4),
                "foldOuter" to intArrayOf(904, 2316, 3),
            )) for (lang in arrayOf("de", "pt", "uk", "tr")) for (scale in intArrayOf(100, 130)) {
                val g = game(dims[0], dims[1], dims[2] * 0.9f,
                    if (tag == "phone720") Quality.LOW else Quality.HIGH)
                if (tag == "phone20x9") g.setInsets(110, 0, 70, 40)
                g.profile.lang = lang
                g.profile.textScale = scale
                g.profile.hintsEnabled = false
                g.onLanguageChanged()
                g.applyAccessibility()
                val scoreFile = File.createTempFile("cw-layout-audit", ".json").apply { delete() }
                val scores = ScoreClient(scoreFile, "https://scores.example.invalid",
                    "testing-key-at-least-32-characters-long", g.profile)
                g.scores = scores
                g.online = object : OnlineLeaderboards {
                    override val available = true
                    override val state = OnlineState.SIGNED_OUT
                    override fun submit(board: String, score: Long) {}
                }
                try {
                    // Usunięcie banera logowania z kadrów badanych ekranów.
                    val bmp = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
                    repeat(210) { g.frame(Canvas(bmp), 1f / 60f) }
                    bmp.recycle()
                    visitAuditScreens(g) { name, screen, title ->
                        assertNoCollisions(g, screen, name, title)
                        shot(g, "audit_${tag}_${lang}_${scale}_${name}", 1)
                    }
                } finally {
                    scores.close()
                }
            }
        } finally {
            L.lang = originalLang
        }
    }

    /** Zlecenie 89: cztery formaty, 130% tekstu, obie pozycje przewijania i kolizje. */
    @Test
    fun settingsCodexUx89() {
        val originalLang = L.lang
        try {
            for ((tag, dims) in screenshotDevices(mapOf(
                "phone720" to intArrayOf(1600, 720, 2),
                "phone20x9" to intArrayOf(2400, 1080, 3),
                "foldOpen" to intArrayOf(2184, 1968, 3),
                "foldPortrait" to intArrayOf(1968, 2184, 3),
                "foldInner" to intArrayOf(1802, 2000, 4),
                "foldOuter" to intArrayOf(904, 2316, 3),
            ))) {
                val g = game(dims[0], dims[1], dims[2] * 0.9f,
                    if (tag == "phone720") Quality.LOW else Quality.HIGH)
                g.profile.lang = "en"
                g.profile.textScale = 130
                g.onLanguageChanged()
                g.applyAccessibility()

                val settings = SettingsScreen(g)
                g.setScreen(settings)
                assertTrue("$tag: techniczne opcje powinny być zwinięte",
                    settings.buttons.first { it.id == 1 }.visible.not())
                val sound = settings.buttons.first { it.id == 8 }
                val music = settings.buttons.first { it.id == 12 }
                val effects = settings.buttons.first { it.id == 18 }
                val language = settings.buttons.first { it.id == 3 }
                assertTrue("$tag: kolejność najważniejszych ustawień",
                    sound.r.top < music.r.top && music.r.top < effects.r.top &&
                        effects.r.top < language.r.top)
                assertNoCollisions(g, settings, "settings89", "settings")
                shot(g, "${tag}_89_settings", 1)
                settings.onClick(settings.buttons.first { it.id == 20 })
                assertNoCollisions(g, settings, "settings89_advanced", "settings")
                shot(g, "${tag}_89_settings_advanced", 1)
                settings.onDrag(0f, -100000f)
                assertNoCollisions(g, settings, "settings_bottom", "settings")
                shot(g, "${tag}_89_settings_bottom", 1)

                val skirmish = SkirmishSetupScreen(g)
                g.setScreen(skirmish)
                assertTrue("$tag: ikona losowania", skirmish.buttons.first { it.id == -4 }.iconId ==
                    com.centurywar.engine.Icons.DICE)
                assertNoCollisions(g, skirmish, "skirmish89", null)
                shot(g, "${tag}_89_skirmish", 1)
                skirmish.onDrag(0f, -100000f)
                assertNoCollisions(g, skirmish, "skirmish89_bottom", null)
                shot(g, "${tag}_89_skirmish_bottom", 1)

                val codex = CodexScreen(g, 4)
                g.setScreen(codex)
                val categories = codex.buttons.filter { it.id in 200..209 && it.visible }
                assertTrue("$tag: widoczna listwa zakładek", categories.size >= 2)
                assertTrue("$tag: zakładki w jednym rzędzie",
                    categories.all { kotlin.math.abs(it.r.top - categories[0].r.top) < g.ui.dp })
                assertTrue("$tag: poziome przewijanie kategorii",
                    field(codex, "maxCategoryScroll").getFloat(codex) > 0f)
                assertNoCollisions(g, codex, "codex89", "codex")
                shot(g, "${tag}_89_codex", 1)

                val touch = TouchEvent().apply {
                    action = TouchEvent.DOWN
                    x = g.width / 2f
                    y = field(codex, "categoryRailTop").getFloat(codex) + 12 * g.ui.dp
                }
                codex.onTouch(touch)
                codex.onDrag(-100000f, 0f)
                touch.action = TouchEvent.UP
                codex.onTouch(touch)
                assertTrue("$tag: gest przewija listwę", field(codex, "categoryScroll").getFloat(codex) > 0f)
                assertNoCollisions(g, codex, "codex89_scrolled", "codex")
                shot(g, "${tag}_89_codex_tabs_end", 1)
                codex.showCategory(8)
                assertNoCollisions(g, codex, "codex89_cards", "codex")
                shot(g, "${tag}_89_codex_cards", 1)

                val about = AboutScreen(g)
                g.setScreen(about)
                for (button in about.buttons.filter { it.visible && it.id >= 0 }) {
                    assertTrue("$tag: link ${button.id} mniejszy od 48 dp",
                        button.r.height() + 0.01f >= 48 * g.ui.dp)
                }
                assertNoCollisions(g, about, "about89", "about")
                shot(g, "${tag}_89_about", 1)
            }
        } finally {
            L.lang = originalLang
        }
    }

    /** Menu, cztery zakładki Rekordów i tor logowania na czterech formatach. */
    @Test
    fun recordsUxFourFormats() {
        val oldLang = L.lang
        try {
            L.lang = L.PL
            for ((tag, dims) in screenshotDevices(mapOf(
                "phone720" to intArrayOf(1600, 720, 2),
                "phone20x9" to intArrayOf(2400, 1080, 3),
                "foldOpen" to intArrayOf(2184, 1968, 3),
                "foldPortrait" to intArrayOf(1968, 2184, 3),
                "foldInner" to intArrayOf(1802, 2000, 4),
                "foldOuter" to intArrayOf(904, 2316, 3),
            ))) {
                val g = game(dims[0], dims[1], dims[2] * 0.9f,
                    if (tag == "phone720") Quality.LOW else Quality.HIGH)
                g.profile.lang = "pl"
                L.setCode("pl")
                g.profile.hintsSeen = Profile.HINT_VALID_MASK
                (field(g, "noticeQueue").get(g) as MutableCollection<*>).clear()
                (field(g, "noticeIcons").get(g) as MutableCollection<*>).clear()
                field(g, "achievementSeconds").setFloat(g, 0f)
                val state = Missions(g.profile).refresh(System.currentTimeMillis())
                state.daily.firstOrNull()?.let { it.progress = it.target }
                // Trzy wyniki jednego dowódcy: w Rekordach mają być daty, nie trzy pseudonimy.
                val today = Missions.utcDay(System.currentTimeMillis())
                g.profile.boards[Profile.BOARD_ENDLESS] = mutableListOf(
                    com.centurywar.meta.ScoreEntry(g.profile.name, 195_000L, today),
                    com.centurywar.meta.ScoreEntry(g.profile.name, 163_000L, today - 1L),
                    com.centurywar.meta.ScoreEntry(g.profile.name, 121_000L, today - 2L),
                )
                g.setScreen(MenuScreen(g))
                shot(g, "${tag}_87_menu", 2)
                g.setScreen(RankingsScreen(g))
                shot(g, "${tag}_87_records_rankings", 2)
                g.setScreen(StatsScreen(g))
                shot(g, "${tag}_87_records_stats", 2)
                g.setScreen(ReplayListScreen(g))
                shot(g, "${tag}_87_records_replays", 2)
                g.setScreen(AchievementsScreen(g))
                shot(g, "${tag}_87_records_achievements", 2)
                g.setScreen(MissionsScreen(g))
                shot(g, "${tag}_87_streak", 2)
            }
        } finally {
            L.lang = oldLang
        }
    }
}
