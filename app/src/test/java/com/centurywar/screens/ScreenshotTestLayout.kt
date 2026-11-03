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

    /**
     * Kontrola nowych stanów zleceń 53–67: 720p, 1280×720@2, 20:9,
     * Fold w obu orientacjach, DE/TR/UK i tekst 130%.
     * Pliki audit74_after_* służą do wizualnego porównania z bazowymi screenshotami.
     */
    @Test
    fun layout74VisualStates() {
        val originalLang = L.lang
        try {
            for ((tag, dims) in mapOf(
                "phone720" to intArrayOf(1600, 720, 2),
                "phone1280" to intArrayOf(1280, 720, 2),
                "phone20x9" to intArrayOf(2400, 1080, 3),
                "foldLandscape" to intArrayOf(2184, 1968, 3),
                "foldPortrait" to intArrayOf(1968, 2184, 3),
            )) for (lang in arrayOf("de", "tr", "uk")) {
                val g = game(dims[0], dims[1], dims[2] * 0.9f,
                    if (tag == "phone720" || tag == "phone1280") Quality.LOW else Quality.HIGH)
                if (tag == "phone20x9") g.setInsets(110, 0, 70, 40)
                g.profile.lang = lang
                g.profile.textScale = 130
                g.profile.hintsEnabled = false
                g.onLanguageChanged()
                g.applyAccessibility()
                val key = "$tag/$lang/130%"
                val prefix = "audit74_after_${tag}_${lang}_130"

                val menu = MenuScreen(g)
                g.setScreen(menu)
                clear74Notices(g)
                g.showNotice(L["ms_streak"] + " 7/7: " + L["ms_daily"])
                shot(g, "${prefix}_menu_notice", 1)
                assert74Notice(g, menu, key)

                clear74Notices(g)
                val pause = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 7401L, 1f,
                    startEra = 2, maxEra = 7, weather = Weather.NIGHT,
                    modifiers = setOf(Modifier.FOG_OF_WAR, Modifier.FAST_UNITS)))
                g.setScreen(pause)
                pause.onClick(field(pause, "pauseBtn").get(pause) as Button)
                shot(g, "${prefix}_pause", 1)
                assert74Pause(g, pause, key)

                val steam = Defs.eraOf(com.centurywar.game.EraArt.STEAM)
                val variants = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 7402L, 1f,
                    startEra = steam, maxEra = steam))
                g.setScreen(variants)
                (field(variants, "battle").get(variants) as Battle).pendingVariant = steam
                shot(g, "${prefix}_variants", 1)
                assert74Variants(g, variants, key)

                val tooltip = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 7403L, 1f,
                    startEra = 2, maxEra = 4))
                g.setScreen(tooltip)
                @Suppress("UNCHECKED_CAST")
                val units = field(tooltip, "unitBtn").get(tooltip) as Array<Button>
                assertTrue("$key: nie udało się otworzyć dymka", tooltip.onLongPress(units[0]))
                Screen::class.java.getDeclaredField("heldButton").apply {
                    isAccessible = true
                    set(tooltip, units[0])
                }
                shot(g, "${prefix}_tooltip", 1)

                val codex = CodexScreen(g, steam)
                g.setScreen(codex)
                codex.showCategory(2)
                shot(g, "${prefix}_codex_tabs", 1)
                assertNoCollisions(g, codex, "codex_variants74", "codex")

                g.setScreen(CampaignScreen(g))
                shot(g, "${prefix}_campaign", 1)
                g.setScreen(AchievementsScreen(g))
                shot(g, "${prefix}_achievements", 1)
                g.setScreen(MissionsScreen(g))
                shot(g, "${prefix}_missions", 1)
                g.setScreen(AboutScreen(g))
                shot(g, "${prefix}_about", 1)
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
}
