package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.centurywar.R
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Palette
import com.centurywar.engine.Quality
import com.centurywar.engine.Screen
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.BattleController
import com.centurywar.game.BattleRenderer
import com.centurywar.game.Bonuses
import com.centurywar.game.Campaign
import com.centurywar.game.Defs
import com.centurywar.game.EndlessCard
import com.centurywar.game.FxBuffer
import com.centurywar.game.Mode
import com.centurywar.game.Role
import com.centurywar.game.RestoredBattle
import com.centurywar.game.Weather
import com.centurywar.meta.Achievements
import com.centurywar.meta.BattleHistory
import com.centurywar.meta.Missions
import com.centurywar.meta.Profile
import com.centurywar.meta.ReplayStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.DataInputStream
import java.io.File
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.util.Properties

/**
 * Autentyczne ekrany i sceny renderera do Google Play.
 * Test kosztowny: domyślnie pomijany; STORE_GRAPHICS=true włącza eksport.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class StoreGraphicsTest {
    private data class Device(val name: String, val width: Int, val height: Int, val density: Float)

    private val devices = listOf(
        Device("phone", 1920, 1080, 2f),
        Device("tablet7", 1920, 1080, 1.8f),
        Device("tablet10", 2560, 1440, 2f),
    )
    // Podglądy Folda nie są osobnym typem zasobu w Play Console.
    private val foldDevices = listOf(
        Device("foldLandscape", 2184, 1968, 3f),
        Device("foldPortrait", 1968, 2184, 3f),
    )
    private val names = arrayOf(
        "menu", "campaign", "endless-boss", "night-barrage", "era-variants",
        "pause-summary", "weekly-siege", "custom-skirmish",
    )
    private val roles = Role.values()

    @Test
    fun generate() {
        assumeTrue("Generator tylko przy STORE_GRAPHICS=true", System.getenv("STORE_GRAPHICS") == "true")
        val output = File("build/store").apply { mkdirs() }
        val previousLanguage = L.lang
        try {
            for (language in selectedLanguages()) {
                val strings = captions(language)
                val langDir = File(output, language).apply { mkdirs() }
                val manifest = StringBuilder("file\twidth\theight\talt\n")
                for (device in devices) {
                    val g = game(device, language)
                    val folder = File(langDir, device.name).apply { mkdirs() }
                    for (scene in 1..8) {
                        val name = scene.toString().padStart(2, '0') + "-" + names[scene - 1] + ".png"
                        val image = File(folder, name)
                        writePng(image, device.width, device.height, false) { canvas ->
                            when (scene) {
                                1 -> renderScreen(canvas, g, MenuScreen(g))
                                2 -> renderScreen(canvas, g, CampaignScreen(g))
                                3, 4 -> drawGameplay(canvas, g, scene)
                                5 -> drawVariant(canvas, g)
                                6 -> drawPauseSummary(canvas, g)
                                7 -> renderScreen(canvas, g, ModeScreen(g, Mode.DAILY, true))
                                8 -> renderScreen(canvas, g, SkirmishSetupScreen(g))
                            }
                            drawCaption(canvas, strings.getProperty("caption." + scene), scene,
                                device.width.toFloat(), device.height.toFloat())
                        }
                        checkPng(image, device.width, device.height, false)
                        manifest.append(device.name).append('/').append(name).append('\t')
                            .append(device.width).append('\t').append(device.height).append('\t')
                            .append(strings.getProperty("alt." + scene)).append('\n')
                    }
                }
                // Autentyczny HUD i nowy pasek na obu orientacjach rozłożonego Folda.
                // Podglądy są do QA, nie trafiają do manifestu Play Console.
                for (device in foldDevices) {
                    val g = game(device, language)
                    val preview = File(langDir, "preview_${device.name}_hud.png")
                    writePng(preview, device.width, device.height, false) { c ->
                        drawGameplay(c, g, 4)
                    }
                    checkPng(preview, device.width, device.height, false)
                }
                val feature = File(langDir, "feature_1024x500.png")
                writePng(feature, 1024, 500, false) { c ->
                    val g = game(Device("feature", 1024, 500, 1f), language)
                    drawBattle(c, g, 4, 1024, 500, true)
                    drawFeatureTitle(c)
                }
                checkPng(feature, 1024, 500, false)
                manifest.append("feature_1024x500.png\t1024\t500\t")
                    .append(strings.getProperty("alt.feature")).append('\n')

                val icon = File(langDir, "icon_512.png")
                writePng(icon, 512, 512, true) { c -> drawIcon(c) }
                checkPng(icon, 512, 512, true)
                assertTrue("Ikona Google Play musi miec <= 1024 KB", icon.length() <= 1024L * 1024L)
                manifest.append("icon_512.png\t512\t512\t")
                    .append(strings.getProperty("alt.icon")).append('\n')
                File(langDir, "manifest.tsv").writeText(manifest.toString(), Charsets.UTF_8)
                contactSheet(langDir, strings)
            }
        } finally {
            L.lang = previousLanguage
        }
    }

    /** Format wszystkich plików i komplet napisów, bez kosztownego renderowania. */
    @Test
    fun storeAssetRequirements() {
        assertEquals("Dziesięć języków gry", 10, L.CODES.size)
        assertEquals("Telefon i dwa tablety", setOf("phone", "tablet7", "tablet10"),
            devices.map { it.name }.toSet())
        for (device in devices) {
            assertTrue("Rozmiar: ${device.name}",
                device.width in 320..3840 && device.height in 320..3840)
            assertTrue("Proporcje 2:1: ${device.name}",
                maxOf(device.width, device.height) <= 2 * minOf(device.width, device.height))
            assertEquals("Zalecane 16:9: ${device.name}", device.width * 9, device.height * 16)
        }
        for (language in L.CODES) captions(language)
    }

    /** Sekcje publikowane musza miescic sie w limitach Play Console. */
    @Test
    fun listingLimits() {
        for (lang in L.CODES) {
            val content = File("../docs/store/listing_" + lang + ".md").readText(Charsets.UTF_8)
            fun section(name: String): String {
                val heading = "## " + name + "\n"
                check(heading in content) { "Brak sekcji: " + name }
                return content.substringAfter(heading).substringBefore("\n## ").trim()
            }
            val pl = lang == "pl"
            val title = section(if (pl) "Tytuł" else "Title")
            val short = section(if (pl) "Krótki opis" else "Short description")
            val full = section(if (pl) "Pełny opis" else "Full description")
            val keywords = if (lang == "pl" || lang == "en")
                section(if (pl) "ASO — pięć propozycji fraz" else
                    "ASO — five suggested search phrases").lines().filter { it.startsWith("- ") }
                else emptyList()
            assertTrue("Tytuł " + lang + ": " + title.length, title.length <= 30)
            assertTrue("Krótki opis " + lang + ": " + short.length, short.length <= 80)
            assertTrue("Pełny opis " + lang + ": " + full.length, full.length <= 4000)
            if (lang == "pl" || lang == "en")
                assertEquals("Pięć fraz ASO " + lang, 5, keywords.size)
            val features = mapOf(
                "pl" to ("wódz" to "stopnie wież"),
                "en" to ("commander" to "tower tiers"),
                "de" to ("kommandant" to "turmstufen"),
                "es" to ("comandante" to "niveles de torretas"),
                "fr" to ("commandant" to "niveaux des tourelles"),
                "it" to ("comandante" to "livelli delle torrette"),
                "pt" to ("comandante" to "níveis das torres"),
                "uk" to ("командир" to "рівні веж"),
                "nl" to ("commandant" to "torenniveaus"),
                "tr" to ("komutan" to "kule seviyeleri"),
            ).getValue(lang)
            assertTrue("Krótki opis bez dowódcy/stopni wież: " + lang,
                short.lowercase().contains(features.first) &&
                    short.lowercase().contains(features.second))
            assertTrue("Pełny opis bez dowódcy/stopni wież: " + lang,
                full.lowercase().contains(features.first) &&
                    full.lowercase().contains(features.second))
        }
    }


    // Zlecenie 153: pięć autentycznych ekranów nowej zawartości, bez nakładek marketingowych.
    // 20:9 pozostaje podglądem QA, ponieważ Play akceptuje najwyżej proporcje 2:1.
    private val spotlight153Names = arrayOf(
        "commander-aura", "turret-tiers", "campaign-map", "endless-cards", "battle-chart",
    )
    private val spotlight153Devices = listOf(
        Device("phone16x9", 1920, 1080, 2f),
        Device("phone20x9_qa", 2400, 1080, 2f),
        Device("tablet10", 2560, 1440, 2f),
        Device("fold", 2184, 1968, 2.7f),
    )

    @Test
    fun generate153() {
        assumeTrue("Eksport tylko przy STORE_GRAPHICS_153=true",
            System.getenv("STORE_GRAPHICS_153") == "true")
        val previousLanguage = L.lang
        try {
            val root = File("build/store153").apply { mkdirs() }
            for (language in listOf("pl", "en", "de")) {
                val strings = captions(language)
                val languageDir = File(root, language).apply { mkdirs() }
                val manifest = StringBuilder("file\twidth\theight\talt\tplay_upload\n")
                for (device in spotlight153Devices) {
                    val g = game(device, language)
                    val directory = File(languageDir, device.name).apply { mkdirs() }
                    for (scene in 1..5) {
                        val filename = scene.toString().padStart(2, '0') +
                            "-" + spotlight153Names[scene - 1] + ".png"
                        val png = File(directory, filename)
                        writePng(png, device.width, device.height, false) { canvas ->
                            when (scene) {
                                1 -> draw153Commander(canvas, g)
                                2 -> draw153TurretTiers(canvas, g)
                                3 -> renderScreen(canvas, g, CampaignScreen(g))
                                4 -> draw153EndlessCards(canvas, g)
                                else -> draw153BattleChart(canvas, g)
                            }
                        }
                        checkPng(png, device.width, device.height, false)
                        val isPlay = device.name != "phone20x9_qa"
                        manifest.append(device.name).append('/').append(filename)
                            .append('\t').append(device.width).append('\t')
                            .append(device.height).append('\t')
                            .append(strings.getProperty("alt153." + scene))
                            .append('\t').append(isPlay).append('\n')
                    }
                }
                // Jedna grafika promocyjna wspólna dla języków: oryginalny Wódz i emblemat.
                val feature = File(languageDir, "feature_1024x500.png")
                val featureGame = game(Device("feature153", 1024, 500, 1f), language)
                writePng(feature, 1024, 500, false) { c -> drawCommanderFeature(c, featureGame) }
                checkPng(feature, 1024, 500, false)
                manifest.append("feature_1024x500.png\t1024\t500\t")
                    .append(strings.getProperty("alt153.feature")).append("\ttrue\n")
                File(languageDir, "manifest.tsv").writeText(manifest.toString(), Charsets.UTF_8)
                contactSheet153(languageDir, strings)
            }
        } finally {
            L.lang = previousLanguage
        }
    }

    @Test
    fun spotlight153HudScenes() {
        val commander = commander153Battle()
        assertEquals(327f, commander.time, 0.05f)
        assertTrue(commander.player.baseHp < commander.player.baseMaxHp)
        assertTrue(commander.enemy.baseHp < commander.enemy.baseMaxHp)
        assertEquals(1120f, commander.player.gold, 0.01f)
        val feature = commander153Battle(forFeature = true)
        assertEquals(0f, feature.time, 0.01f)
        assertEquals(feature.player.baseMaxHp, feature.player.baseHp, 0.01f)
    }

    // D7: jeden premierowy zestaw 8 kadrów do Play Console (10 języków); 117 i 153 to archiwum.
    private enum class D7Anchor { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    /** [play]: zrzut trafia do Play Console; [sheetOnly]: tylko stykówka QA (pion Folda). */
    private class D7Device(val device: Device, val play: Boolean, val sheetOnly: Boolean = false)

    private val d7Names = arrayOf(
        "commander", "turret-tiers", "campaign", "endless-cards",
        "results", "missions", "accessibility", "weekly-siege",
    )
    private val d7Devices = listOf(
        D7Device(Device("phone", 1920, 1080, 2f), true),
        D7Device(Device("tablet7", 1920, 1080, 1.8f), true),
        D7Device(Device("tablet10", 2560, 1440, 2f), true),
        // Rozłożony Fold do Play dopiero po przeglądzie kadrów; 20:9 i pion zewnętrzny tylko do QA.
        D7Device(Device("fold", 2184, 1968, 2.7f), false),
        D7Device(Device("phone20x9_qa", 2400, 1080, 2f), false),
        D7Device(Device("foldOuter_qa", 904, 2316, 2.7f), false, sheetOnly = true),
    )

    /** Ok. 10 ukończonych etapów kampanii z gwiazdkami w kilku epokach (kadr 03). */
    private val d7Stars = intArrayOf(3, 3, 2, 3, 3, 2, 3, 1, 2, 3)

    /**
     * Kadr 05: czas zwycięstwa (7:45) w przedziale 7–10 min etapu 11. Presja oblężnicza w tym
     * etapie rusza od 5:00 (`Campaign.SECOND_SIEGE_START`, +50%/min), więc HUD nad wynikiem
     * pokazuje „Presja oblężnicza ×2.4” zamiast zegara — to zamierzony, prawdziwy stan gry.
     */
    private val D7_RESULT_SECONDS = 465

    /** Stały dzień misji (UTC), żeby kadr 06 był powtarzalny: 18.11.2026, 10:00. */
    private val d7NowMillis = 1_794_996_000_000L

    @Test
    fun generateD7() {
        assumeTrue("Eksport tylko przy STORE_GRAPHICS_D7=true",
            System.getenv("STORE_GRAPHICS_D7") == "true")
        val previousLanguage = L.lang
        val only = System.getenv("STORE_D7_DEVICES")?.takeIf { it.isNotBlank() }
            ?.split(',')?.map { it.trim() }?.toSet()
        try {
            val root = File("build/storeD7").apply { mkdirs() }
            for (language in selectedLanguages()) {
                val strings = captionsD7(language)
                val languageDir = File(root, language).apply { mkdirs() }
                val manifest = StringBuilder("file\twidth\theight\talt\tplay_upload\n")
                for (target in d7Devices) {
                    val device = target.device
                    if (only != null && device.name !in only) continue
                    if (target.sheetOnly) {
                        sheetD7Portrait(languageDir, device, language, strings)
                        continue
                    }
                    val directory = File(languageDir, device.name).apply { mkdirs() }
                    val g = gameD7(device, language)
                    for (scene in 1..8) {
                        val filename = d7File(scene)
                        val png = File(directory, filename)
                        writePng(png, device.width, device.height, false) { canvas ->
                            drawD7Scene(canvas, g, device, language, scene)
                            drawD7Caption(canvas, strings.getProperty("captionD7.$scene"), scene,
                                device.width.toFloat(), device.height.toFloat())
                        }
                        checkPng(png, device.width, device.height, false)
                        manifest.append(device.name).append('/').append(filename)
                            .append('\t').append(device.width).append('\t')
                            .append(device.height).append('\t')
                            .append(strings.getProperty("altD7.$scene"))
                            .append('\t').append(target.play).append('\n')
                    }
                }
                val feature = File(languageDir, "feature_1024x500.png")
                val featureGame = game(Device("featureD7", 1024, 500, 1f), language)
                writePng(feature, 1024, 500, false) { c -> drawCommanderFeature(c, featureGame) }
                checkPng(feature, 1024, 500, false)
                manifest.append("feature_1024x500.png\t1024\t500\t")
                    .append(strings.getProperty("altD7.feature")).append("\ttrue\n")
                // Częściowy eksport (STORE_D7_DEVICES) nie nadpisuje pełnego manifestu.
                if (only == null) {
                    File(languageDir, "manifest.tsv").writeText(manifest.toString(), Charsets.UTF_8)
                }
                if (only == null || "phone" in only) contactSheetD7(languageDir, strings)
            }
        } finally {
            L.lang = previousLanguage
            Palette.select(0)
        }
    }

    /** Szybki test bez eksportu: komplet podpisów D7 w 10 językach i czytelny rozmiar. */
    @Test
    fun d7CaptionsComplete() {
        assertEquals(10, L.CODES.size)
        assertEquals(8, d7Names.size)
        assertEquals(setOf("phone", "tablet7", "tablet10"),
            d7Devices.filter { it.play }.map { it.device.name }.toSet())
        for (target in d7Devices) if (target.play) {
            val d = target.device
            assertTrue("Proporcje Play: ${d.name}",
                maxOf(d.width, d.height) <= 2 * minOf(d.width, d.height))
        }
        val superlatives = listOf("!", "najlepsz", "best ", "beste", "mejor", "meilleur", "miglior",
            "melhor", "найкращ", "en iyi")
        for (language in L.CODES) {
            val strings = captionsD7(language)
            for (scene in 1..8) {
                val caption = strings.getProperty("captionD7.$scene")
                assertTrue("$language captionD7.$scene > 32 znaki: $caption", caption.length <= 32)
                for (word in superlatives) assertTrue("$language: superlatyw w '$caption'",
                    !caption.lowercase().contains(word))
                for (target in d7Devices) {
                    val d = target.device
                    val ratio = captionScaleD7(caption, d.width.toFloat(), d.height.toFloat())
                    assertTrue("$language/${d.name} captionD7.$scene zmniejszony do $ratio",
                        ratio >= 0.7f)
                    // Kadr misji: podpis nad „Wstecz” nie może sięgać wstęgi tytułu.
                    if (scene == 6 && d.width > d.height) {
                        val p = captionPaintD7(d.width.toFloat(), d.height.toFloat())
                        val box = p.measureText(caption) * ratio + 72f
                        assertTrue("$language/${d.name}: podpis misji za szeroki ($box)",
                            box <= d.width * 0.33f)
                    }
                }
            }
        }
    }

    private fun d7File(scene: Int) = scene.toString().padStart(2, '0') + "-" + d7Names[scene - 1] + ".png"

    private fun captionsD7(language: String): Properties {
        val props = captions(language)
        for (i in 1..8) {
            check(!props.getProperty("captionD7.$i").isNullOrBlank()) { "$language: brak captionD7.$i" }
            val alt = props.getProperty("altD7.$i")
            check(!alt.isNullOrBlank() && alt.length <= 140) { "$language: altD7.$i" }
        }
        check(props.getProperty("altD7.feature")?.let { it.length in 1..140 } == true) {
            "$language: altD7.feature"
        }
        return props
    }

    /** Profil premierowy: jak [game], ale ok. 10 etapów kampanii za sobą. */
    private fun gameD7(device: Device, language: String, tune: Profile.() -> Unit = {}): Game =
        game(device, language, d7Stars, tune)

    private fun drawD7Scene(c: Canvas, g: Game, device: Device, language: String, scene: Int) {
        when (scene) {
            1 -> draw153Commander(c, g)
            2 -> draw153TurretTiers(c, g)
            3 -> drawD7Campaign(c, g)
            4 -> drawD7EndlessCards(c, g)
            5 -> drawD7Results(c, gameD7(device, language))
            6 -> drawD7Missions(c, gameD7(device, language))
            7 -> drawD7Accessibility(c, device, language)
            else -> renderScreen(c, g, ModeScreen(g, Mode.DAILY, true))
        }
    }

    /**
     * Trzy karty z dnia 6 (różne rzadkości): Mur tarcz, Saperzy, Zakład. Wybór na stałe,
     * żeby kolejne karty dopisane do enumu nie zmieniały kadru. STORE_D7_CARDS=A,B,C wymusza inny.
     */
    private fun d7Cards(): Array<EndlessCard> {
        System.getenv("STORE_D7_CARDS")?.takeIf { it.isNotBlank() }?.let { list ->
            val forced = list.split(',').map { EndlessCard.valueOf(it.trim()) }
            require(forced.size == 3) { "STORE_D7_CARDS: trzy karty" }
            return forced.toTypedArray()
        }
        return arrayOf(EndlessCard.SHIELD_WALL, EndlessCard.SAPPERS, EndlessCard.WAGER)
    }

    /** Mapa po dojechaniu animacji przewijania do bieżącego etapu (bez niej kadr stoi na starcie). */
    private fun drawD7Campaign(c: Canvas, g: Game) {
        g.setScreen(CampaignScreen(g))
        g.finishScreenTransition()
        val tiny = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        try {
            repeat(150) { g.frame(Canvas(tiny), 1f / 60f) }
        } finally {
            tiny.recycle()
        }
        g.frame(c, 0f)
    }

    /** Wybór kart przy fali 10 z żywym bossem (ten sam stan co kadr 153). */
    private fun drawD7EndlessCards(c: Canvas, g: Game) {
        val battle = sceneBattle(3)
        check(battle.currentBoss() != null) { "Kadr 04 wymaga bossa" }
        stage153Hud(battle, 563, 0.74f, 0.61f, 1650f)
        battle.pendingCards = d7Cards()
        render153Battle(c, g, battle)
    }

    /**
     * Zwycięstwo etapu 11 kampanii: gwiazdki, „Za bitwę” i „Za osiągnięcia” osobno oraz
     * „Następny cel”. Zakładka Statystyki (na telefonie czytelniejsza niż wykres).
     * Liczby jak w typowej bitwie tego etapu: rzeczywista symulacja ok. 7:45 (baza gracza
     * trzymana przy 90% dla 3 gwiazdek, baza wroga pada dopiero w finale), kilkadziesiąt
     * zabitych i dokładnie dwa osiągnięcia z [Achievements] (25 bitew, 1000 zabitych) na
     * profilu gracza, który ma za sobą 24 bitwy (10 etapów z powtórkami).
     */
    private fun drawD7Results(c: Canvas, g: Game) {
        g.history = BattleHistory(File.createTempFile("d7h", ".json").apply { delete() })
        g.historyLoaded = true
        g.replays = ReplayStore(File.createTempFile("d7r", "").apply { delete(); mkdirs() })
        // Misje z dnia uruchomienia nie mogą zmienić celu: wszystkie już odebrane.
        for (item in g.profile.missionState.daily) item.claimed = true
        for (item in g.profile.missionState.weekly) item.claimed = true
        val stage = d7Stars.size
        val screen = BattleScreen(g, Campaign.config(Campaign.stages[stage], Bonuses(), 70005L))
        g.setScreen(screen)
        g.finishScreenTransition()
        val battle = screen.battle
        battle.player.gold = 4000f
        val finale = D7_RESULT_SECONDS / Battle.STEP
        var step = 0
        while (step < finale) {
            check(!battle.over) { "Etap rozstrzygnięty przed finałem kadru (${battle.time} s)" }
            if (step % 150 == 0) {
                screen.commands.queue(Role.MELEE)
                screen.commands.queue(Role.RANGED)
            }
            battle.player.baseHp = maxOf(battle.player.baseHp, battle.player.baseMaxHp * 0.9f)
            battle.enemy.baseHp = maxOf(battle.enemy.baseHp, battle.enemy.baseMaxHp * 0.25f)
            battle.step()
            screen.battleSummary.capture(battle)
            step++
        }
        check(!battle.over) { "Etap rozstrzygnięty przed finałem kadru" }
        // Profil po 10 etapach: 24 bitwy, 17 zwycięstw; zdobyte osiągnięcia oprócz dwóch,
        // które ta bitwa odblokowuje (25. bitwa i 1000. zabity).
        val profile = g.profile
        val pending = setOf("battles_25", "kills_1000")
        profile.unlockedAchievements.clear()
        for (a in Achievements.all) if (a.id !in pending) profile.unlockedAchievements.add(a.id)
        profile.battles = 24
        profile.wins = 17
        profile.totalKills = 1000L - battle.player.kills + 7L
        battle.enemy.baseHp = 0f
        val tiny = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        try {
            repeat(40) { g.frame(Canvas(tiny), 1f / 30f) }
        } finally {
            tiny.recycle()
        }
        check(battle.over && battle.winner == 0) { "Kadr 05 wymaga zwycięstwa" }
        check(battle.stars() == 3) { "Kadr 05: ${battle.stars()} gwiazdki" }
        check(battle.time in 420f..600f) { "Kadr 05: czas ${battle.time} s poza 7–10 min" }
        check(battle.player.kills in 30..99) { "Kadr 05: ${battle.player.kills} zabitych" }
        val expected = pending.sumOf { Achievements.byId(it)!!.medalReward }
        check(screen.reward > 0 && screen.achievementMedals == expected) {
            "Kadr 05: medale za bitwę ${screen.reward}, za osiągnięcia ${screen.achievementMedals} (oczekiwane $expected)"
        }
        screen.showResultChart = false
        g.frame(c, 0f)
        check(screen.resultRowsClipped == 0) { "Kadr 05: obcięte wiersze wyników" }
        check(screen.resultGoalDrawn) { "Kadr 05: brak „Następnego celu”" }
        println("D7 kadr 05: czas ${battle.time.toInt()} s, zabici ${battle.player.kills}, " +
            "za bitwę ${screen.reward}, za osiągnięcia ${screen.achievementMedals}")
    }

    /** Misje ze stałego dnia: seria 4 dni, jedna misja do odebrania, jedna odebrana. */
    private fun drawD7Missions(c: Canvas, g: Game) {
        val profile = g.profile
        val state = profile.missionState
        state.reset()
        val day = Missions.utcDay(d7NowMillis)
        // Seria: trzy dni wcześniej, dziś czwarty dzień.
        state.loginDay = day - 1
        state.lastSeenDay = day - 1
        state.loginClaimedDay = day - 1
        state.loginStreak = 3
        val missions = Missions(profile)
        missions.refresh(d7NowMillis)
        missions.claimLogin(d7NowMillis)
        check(state.loginStreak == 4 && state.daily.size == 3 && state.weekly.size == 3)
        state.daily[0].progress = state.daily[0].target
        state.daily[1].progress = state.daily[1].target
        state.daily[1].claimed = true
        state.daily[2].progress = state.daily[2].target / 2
        for ((i, item) in state.weekly.withIndex())
            item.progress = item.target * (i + 1) / 4
        check(Missions.pendingClaims(state) == 1)
        val screen = MissionsScreen(g)
        screen.clock = { d7NowMillis }
        renderScreen(c, g, screen)
    }

    /** Prawdziwe ustawienia Dostępności: wysoki kontrast, znaczniki drużyn i tekst 115%. */
    private fun drawD7Accessibility(c: Canvas, device: Device, language: String) {
        val g = gameD7(device, language) {
            palette = 3
            teamMarkers = true
            textScale = 115
            textScaleManual = true
        }
        try {
            check(g.ui.textScale > 1.1f)
            val battle = sceneBattle(7)
            stage153Hud(battle, 312, 0.81f, 0.47f, 960f)
            render153Battle(c, g, battle)
        } finally {
            Palette.select(0)
        }
    }

    /** Pozycje podpisów dobrane po przeglądzie kadrów: poza paskiem HUD i przyciskami. */
    private fun d7Anchor(scene: Int): D7Anchor = when (scene) {
        3 -> D7Anchor.BOTTOM_LEFT
        // Misje: lista wypełnia ekran, więc krótki podpis zasłania tylko przycisk „Wstecz”.
        6 -> D7Anchor.TOP_LEFT
        8 -> D7Anchor.TOP_RIGHT
        else -> D7Anchor.TOP_LEFT
    }

    private fun captionPaintD7(width: Float, height: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        textSize = minOf(52f, width * 0.032f, height * 0.077f)
    }

    /** Ułamek bazowego rozmiaru podpisu po dopasowaniu do szerokości (wymagane ≥ 0,7). */
    private fun captionScaleD7(caption: String, width: Float, height: Float): Float {
        val p = captionPaintD7(width, height)
        val maxWidth = width * 0.79f
        val measured = p.measureText(caption)
        return if (measured > maxWidth) maxWidth / measured else 1f
    }

    /** Ten sam wygląd podpisu co w zestawie 117; własne kotwice dla nowych ujęć. */
    private fun drawD7Caption(c: Canvas, caption: String, scene: Int, width: Float, height: Float) {
        val p = captionPaintD7(width, height)
        val scale = captionScaleD7(caption, width, height)
        check(scale >= 0.7f) { "Podpis za długi: $caption" }
        p.textSize *= scale
        val labelWidth = p.measureText(caption) + 72f
        val boxHeight = p.textSize * 1.65f
        val anchor = d7Anchor(scene)
        // Wyniki i misje: podpis w lewym górnym rogu, nad wstęgą tytułu panelu.
        val corner = scene == 5 || scene == 6
        val margin = width * (if (corner) 0.012f else 0.047f)
        val left = when (anchor) {
            D7Anchor.TOP_RIGHT, D7Anchor.BOTTOM_RIGHT -> width - labelWidth - margin
            else -> margin
        }
        val portrait = height > width
        // Bitwa: pod paskiem HUD; na prawie kwadratowym Foldzie pasek ma dwa rzędy.
        val underHud = when {
            portrait -> 0.16f
            height > width * 0.7f -> 0.215f
            else -> 0.125f
        }
        val top = when (anchor) {
            D7Anchor.TOP_LEFT, D7Anchor.TOP_RIGHT ->
                if (scene in 1..2 || scene == 4 || scene == 7) height * underHud
                else height * (if (corner) 0.006f else 0.015f)
            else -> height * (if (portrait) 0.9f else 0.84f)
        }
        val rect = RectF(left, top, left + labelWidth, top + boxHeight)
        val panel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(226, 23, 34, 52) }
        c.drawRoundRect(rect, 15f, 15f, panel)
        panel.style = Paint.Style.STROKE
        panel.strokeWidth = 3f
        panel.color = Color.rgb(225, 168, 67)
        c.drawRoundRect(rect, 15f, 15f, panel)
        panel.style = Paint.Style.FILL
        c.drawRect(rect.left + 17f, rect.top + 13f, rect.left + 23f, rect.bottom - 13f, panel)
        p.color = Color.rgb(252, 247, 233)
        c.drawText(caption, rect.left + 36f, rect.centerY() + p.textSize * 0.34f, p)
    }

    /** Osiem miniaturek telefonu z podpisami: przegląd zestawu, nie do Play. */
    private fun contactSheetD7(languageDir: File, strings: Properties) {
        val file = File(languageDir, "preview_contact.png")
        writePng(file, 1600, 960, false) { c ->
            c.drawColor(Color.rgb(18, 27, 43))
            val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(244, 202, 103)
                textSize = 37f
                typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            }
            c.drawText("CENTURY WAR D7 - " + languageDir.name.uppercase(), 24f, 52f, heading)
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(248, 245, 231)
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
            }
            val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(230, 170, 75)
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            for (i in d7Names.indices) {
                val x = 18f + i.rem(4) * 395f
                val y = 84f + (i / 4) * 415f
                val bitmap = BitmapFactory.decodeFile(File(languageDir, "phone/" + d7File(i + 1)).absolutePath)
                    ?: error("Brak miniatury: " + d7File(i + 1))
                val area = RectF(x, y, x + 376f, y + 211.5f)
                try { c.drawBitmap(bitmap, null, area, bmpPaint) } finally { bitmap.recycle() }
                c.drawRect(area, border)
                val label = (i + 1).toString().padStart(2, '0') + "  " +
                    strings.getProperty("captionD7." + (i + 1))
                text.textSize = 25f
                if (text.measureText(label) > 376f) text.textSize *= 376f / text.measureText(label)
                c.drawText(label, x, y + 249f, text)
            }
        }
        checkPng(file, 1600, 960, false)
    }

    /** Pion zewnętrznego Folda: tylko stykówka QA (8 kadrów obok siebie), bez plików do Play. */
    private fun sheetD7Portrait(languageDir: File, device: Device, language: String, strings: Properties) {
        val g = gameD7(device, language)
        val thumbW = 190f
        val thumbH = thumbW * device.height / device.width
        val sheet = File(languageDir, "preview_${device.name}.png")
        val sheetW = (16 + 8 * (thumbW + 10)).toInt()
        val sheetH = (thumbH + 40).toInt()
        val frame = Bitmap.createBitmap(device.width, device.height, Bitmap.Config.ARGB_8888)
        try {
            writePng(sheet, sheetW, sheetH, false) { c ->
                c.drawColor(Color.rgb(18, 27, 43))
                val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                for (scene in 1..8) {
                    frame.eraseColor(Color.rgb(27, 42, 65))
                    val canvas = Canvas(frame)
                    drawD7Scene(canvas, g, device, language, scene)
                    drawD7Caption(canvas, strings.getProperty("captionD7.$scene"), scene,
                        device.width.toFloat(), device.height.toFloat())
                    val x = 16f + (scene - 1) * (thumbW + 10f)
                    c.drawBitmap(frame, null, RectF(x, 20f, x + thumbW, 20f + thumbH), bmpPaint)
                }
            }
        } finally {
            frame.recycle()
        }
        checkPng(sheet, sheetW, sheetH, false)
    }

    /** Grafika promocyjna 1024×500 z 153: Wódz na polu bitwy i emblemat z logo. */
    private fun drawCommanderFeature(c: Canvas, featureGame: Game) {
        val battle = commander153Battle(forFeature = true)
        battle.fx.count = 0
        val renderer = BattleRenderer(featureGame, battle)
        renderer.layout(1024f, 500f, 12f, 10f)
        renderer.camX = Defs.LANE * 0.5f - renderer.worldVisible * 0.5f
        renderer.clampCam()
        renderer.lerp = 1f
        repeat(3) { renderer.render(c, 1f / 60f, 1024f, 500f) }
        drawFeatureTitle(c)
    }

    /** Stan przygotowany tylko dla zrzutów HUD; nie zmienia grafiki promocyjnej. */
    private fun stage153Hud(battle: Battle, seconds: Int, playerHp: Float, enemyHp: Float, gold: Float) {
        battle.stepCount = seconds * 60
        battle.time = battle.stepCount * Battle.STEP
        battle.player.baseHp = battle.player.baseMaxHp * playerHp
        battle.enemy.baseHp = battle.enemy.baseMaxHp * enemyHp
        battle.player.gold = gold
        battle.enemy.gold = 1250f
    }

    /** Ustalony seed, wyraźnie widoczny Wódz i aura przy wojskach gracza. */
    private fun commander153Battle(forFeature: Boolean = false): Battle {
        val battle = sceneBattle(2)
        battle.player.gold = 30000f
        check((battle.commandController ?: BattleController(battle)).summonCommander())
        val unit = battle.units.first { it.active && it.isCommander && it.side == 0 }
        unit.x = 1350f
        unit.prevX = unit.x
        unit.anim = 0.5f
        unit.prevAnim = unit.anim
        // Wybuch przy przywołaniu zasłaniał Wodza; pokazujemy trwałą aurę, nie jednorazowy efekt.
        battle.fx.count = 0
        if (!forFeature) stage153Hud(battle, 327, 0.72f, 0.43f, 1120f)
        return battle
    }

    private fun draw153Commander(c: Canvas, g: Game) =
        render153Battle(c, g, commander153Battle())

    /** Stopnie 1/2/3 po kupnie i awansie wież przez kontroler, nie fałszywe etykiety. */
    private fun draw153TurretTiers(c: Canvas, g: Game) {
        val battle = sceneBattle(2)
        val side = battle.player
        side.gold = 30000f
        side.slots = 3
        val orders = battle.commandController ?: BattleController(battle)
        check(orders.buyTurret(1))
        check(orders.upgradeTurretTier(0))
        check(orders.upgradeTurretTier(1))
        check(orders.upgradeTurretTier(1))
        check(side.turretTier[0] == 2 && side.turretTier[1] == 3)
        stage153Hud(battle, 284, 0.68f, 0.56f, 820f)
        render153Battle(c, g, battle, true)
    }

    /** Prawdziwe karty Endless, trzy rzadkości i właściwy panel wyboru. */
    private fun draw153EndlessCards(c: Canvas, g: Game) {
        val battle = sceneBattle(3)
        battle.pendingCards = arrayOf(
            EndlessCard.ECONOMY, EndlessCard.TOWER_NETWORK, EndlessCard.FREE_ELITE,
        )
        render153Battle(c, g, battle)
    }

    private fun render153Battle(c: Canvas, g: Game, battle: Battle, turrets: Boolean = false) {
        val screen = BattleScreen(g, battle.cfg,
            restored = RestoredBattle(battle, false, 800f))
        g.setScreen(screen)
        g.finishScreenTransition()
        check(screen.paused)
        screen.onBack()
        check(!screen.paused)
        if (turrets) screen.showTurrets = true
        g.frame(c, 0f)
        // R10-03: znak „baza atakowana” nie może trafić do zrzutów sklepu.
        check(!screen.baseAlertBtn.visible) { "Znak „baza atakowana” w kadrze" }
        if (turrets) {
            check(screen.slotTier[0].visible && screen.slotTier[1].visible)
            for (button in screen.slotTier) if (button.visible) {
                check(button.r.left >= 0f && button.r.right <= g.width.toFloat() &&
                    button.r.top >= 0f && button.r.bottom <= g.height.toFloat()) {
                    "Przycięty przycisk stopnia: " + button.r
                }
            }
        }
    }

    /** Rzeczywista symulacja przez 4:30; stan baz prowadzi do finału na wykresie. */
    private fun draw153BattleChart(c: Canvas, g: Game) {
        val config = BattleConfig(Mode.SKIRMISH, 153005L, 0.65f,
            startEra = 3, maxEra = 3, variantsEnabled = false)
        val screen = BattleScreen(g, config)
        g.setScreen(screen)
        g.finishScreenTransition()
        val battle = screen.battle
        battle.player.gold = 1500f
        val durationSteps = 270 * 60
        for (step in 0 until durationSteps) {
            check(!battle.over) { "Bitwa zakonczyla sie przed finałem wykresu" }
            if (step % 180 == 0) {
                screen.commands.queue(Role.MELEE)
                screen.commands.queue(Role.RANGED)
            }
            val progress = step.toFloat() / durationSteps
            val enemyCeiling = 0.99f - 0.83f * progress * progress
            // Utrzymujemy oba forty do finału; przewaga rośnie w ostatniej fazie.
            battle.player.baseHp = battle.player.baseHp.coerceIn(
                battle.player.baseMaxHp * 0.63f,
                battle.player.baseMaxHp * (0.99f - 0.20f * progress))
            battle.enemy.baseHp = battle.enemy.baseHp.coerceIn(
                battle.enemy.baseMaxHp * 0.14f,
                battle.enemy.baseMaxHp * enemyCeiling)
            battle.step()
            screen.battleSummary.capture(battle)
        }
        // Ostatni cios rozstrzyga zwykły krok symulacji, nie kapitulacja.
        battle.enemy.baseHp = 0f
        battle.step()
        screen.battleSummary.capture(battle)
        check(battle.over && battle.winner == 0) { "Zrzut wykresu wymaga zwycięstwa" }
        check(screen.battleSummary.count >= 200) { "Za mało próbek wykresu" }
        val tinyBitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        try {
            g.frame(Canvas(tinyBitmap), 1f / 60f)
        } finally {
            tinyBitmap.recycle()
        }
        check(screen.finished) { "Nie pokazano rzeczywistego wyniku bitwy" }
        screen.resultTime = 1.5f
        screen.showResultChart = true
        g.frame(c, 0f)
        check(screen.resultChartArea.left >= 0f &&
            screen.resultChartArea.right <= g.width.toFloat() &&
            screen.resultChartArea.top >= 0f &&
            screen.resultChartArea.bottom <= g.height.toFloat()) {
            "Wykres wykracza poza ekran"
        }
    }

    private fun contactSheet153(directory: File, strings: Properties) {
        val source = File(directory, "phone16x9")
        val preview = File(directory, "preview_contact.png")
        writePng(preview, 1600, 420, false) { canvas ->
            canvas.drawColor(Color.rgb(18, 27, 43))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(245, 225, 183)
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                textSize = 22f
            }
            val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            for (i in spotlight153Names.indices) {
                val name = (i + 1).toString().padStart(2, '0') +
                    "-" + spotlight153Names[i] + ".png"
                val image = BitmapFactory.decodeFile(File(source, name).absolutePath)
                    ?: error("Brak podglądu: " + name)
                val x = 10f + 320f * i
                try {
                    canvas.drawBitmap(image, null, RectF(x, 24f, x + 300f, 193f), imagePaint)
                } finally {
                    image.recycle()
                }
                val caption = strings.getProperty("caption153." + (i + 1))
                paint.textSize = 22f
                if (paint.measureText(caption) > 300f)
                    paint.textSize *= 300f / paint.measureText(caption)
                canvas.drawText(caption, x, 225f, paint)
            }
        }
        checkPng(preview, 1600, 420, false)
    }

    /** Wykluczamy kadr przejscia, korzystamy z prawdziwego ekranu. */
    private fun renderScreen(c: Canvas, g: Game, screen: Screen) {
        g.setScreen(screen)
        g.finishScreenTransition()
        g.frame(c, 0f)
    }

    /** Modal pojawia sie po autentycznym rozkazie awansu do epoki pary. */
    private fun drawVariant(c: Canvas, g: Game) {
        val cfg = BattleConfig(Mode.SKIRMISH, 5050L, 1f,
            startEra = 4, maxEra = Defs.ERA_COUNT - 1)
        val battle = Battle(cfg)
        val orders = BattleController(battle)
        check(Defs.spec(5).variants.size == 2)
        battle.player.xp = Defs.evolveXp[4].toFloat()
        check(orders.evolve()) { "Nie udalo sie awansowac epoki" }
        check(battle.pendingVariant == 5) { "Nie pokazano wyboru wariantu" }
        val screen = BattleScreen(g, cfg, restored = RestoredBattle(battle, false, -120f))
        g.setScreen(screen)
        screen.onBack() // Wznowienie po odtworzeniu zapisanej bitwy.
        check(!isPaused(screen)) { "Bitwa nadal w pauzie: kadr zasłoniłby wybór wariantu" }
        g.frame(c, 0f)
    }

    private fun isPaused(screen: BattleScreen): Boolean =
        BattleScreen::class.java.getDeclaredField("paused").apply { isAccessible = true }.getBoolean(screen)

    /** Prawdziwa walka z HUD i dolnym paskiem, kamera na fotografowanym starciu. */
    private fun drawGameplay(c: Canvas, g: Game, scene: Int) {
        val battle = sceneBattle(scene)
        val screen = BattleScreen(g, battle.cfg,
            restored = RestoredBattle(battle, false, 800f))
        g.setScreen(screen)
        g.finishScreenTransition()
        // onBack() zawsze zwraca true; liczy się, że wznowiona bitwa wyszła z pauzy.
        check(isPaused(screen)) { "Wznowiona bitwa powinna startować w pauzie" }
        screen.onBack()
        check(!isPaused(screen)) { "Bitwa nadal w pauzie: kadr pokazałby menu pauzy" }
        g.frame(c, 0f)
    }

    /** Kadr pauzy ma statystyki z deterministycznie rozegranych krokow. */
    private fun drawPauseSummary(c: Canvas, g: Game) {
        val cfg = BattleConfig(Mode.SKIRMISH, 6060L, 0.85f,
            startEra = 3, maxEra = Defs.ERA_COUNT - 1, weather = Weather.RAIN)
        val battle = Battle(cfg)
        val orders = BattleController(battle)
        check(orders.queue(Role.MELEE))
        orders.queue(Role.RANGED)
        repeat(420) {
            if (!battle.over && battle.pendingVariant == null && battle.pendingChoice == null)
                battle.step()
        }
        check(!battle.over) { "Bitwa zakonczyla sie przed pauza" }
        renderScreen(c, g, BattleScreen(g, cfg,
            restored = RestoredBattle(battle, false, -120f)))
    }

    /** Osiem miniaturek telefonu do raportu, nie do przeslania do Play. */
    private fun contactSheet(langDir: File, captions: Properties) {
        val file = File(langDir, "preview_contact.png")
        writePng(file, 1600, 960, false) { c ->
            c.drawColor(Color.rgb(18, 27, 43))
            val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(244, 202, 103)
                textSize = 37f
                typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            }
            c.drawText("CENTURY WAR - " + langDir.name.uppercase(), 24f, 52f, heading)
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(248, 245, 231)
                textSize = 25f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
            }
            val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(230, 170, 75)
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            for (i in names.indices) {
                val x = 18f + i.rem(4) * 395f
                val y = 84f + (i / 4) * 415f
                val filename = (i + 1).toString().padStart(2, '0') +
                    "-" + names[i] + ".png"
                val bitmap = BitmapFactory.decodeFile(
                    File(langDir, "phone/" + filename).absolutePath
                ) ?: error("Brak miniatury: " + filename)
                val area = RectF(x, y, x + 376f, y + 211.5f)
                try { c.drawBitmap(bitmap, null, area, bmpPaint) }
                finally { bitmap.recycle() }
                c.drawRect(area, border)
                val label = (i + 1).toString().padStart(2, '0') +
                    "  " + captions.getProperty("caption." + (i + 1))
                text.textSize = 25f
                if (text.measureText(label) > 376f)
                    text.textSize *= 376f / text.measureText(label)
                c.drawText(label, x, y + 249f, text)
            }
        }
        checkPng(file, 1600, 960, false)
    }

    private fun captions(language: String): Properties {
        val name = "/store/graphics_" + language + ".properties"
        val stream = javaClass.getResourceAsStream(name) ?: error("Brak napisow: " + name)
        val props = Properties()
        InputStreamReader(stream, Charsets.UTF_8).use { props.load(it) }
        for (i in 1..8) {
            check(!props.getProperty("caption." + i).isNullOrBlank())
            val alt = props.getProperty("alt." + i)
            check(!alt.isNullOrBlank() && alt.length <= 140)
        }
        check(props.getProperty("alt.feature")?.let { it.length in 1..140 } == true)
        check(props.getProperty("alt.icon")?.let { it.length in 1..140 } == true)
        return props
    }

    private fun selectedLanguages(): List<String> {
        val codes = System.getenv("STORE_LANGUAGES")
            ?.takeIf { it.isNotBlank() }
            ?.split(',')
            ?.map { it.trim().lowercase() }
            ?: L.CODES.toList()
        require(codes.isNotEmpty() && codes.distinct().size == codes.size &&
            codes.all { it in L.CODES }) { "Niepoprawne STORE_LANGUAGES: $codes" }
        return codes
    }

    private fun game(
        device: Device, language: String,
        progress: IntArray = intArrayOf(3, 3, 2, 3, 2, 1), tune: Profile.() -> Unit = {},
    ): Game {
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val tmp = File.createTempFile("store-profile-", ".json").apply { deleteOnExit() }
        val profile = Profile(tmp).apply {
            lang = language
            tutorialDone = true
            hintsEnabled = false
            screenShake = 0
            showFps = false
            quality = Quality.HIGH
            medals = 2400
            for (i in progress.indices) stars[i] = progress[i]
            levels["r_unitDmg"] = 4
            levels["r_unitHp"] = 3
            levels["r_income"] = 2
            levels["r_train"] = 2
            // Kadr kampanii ma pokazać mapę 36 etapów, nie zapowiedź odblokowanej epoki.
            for (era in 0 until Defs.ERA_COUNT) markEraIntroSeen(era)
            tune()
        }
        val g = Game(host, profile, Quality.HIGH)
        // R12-05: zrzuty sklepu pokazują wersję produkcyjną, bez dopisków „od premiery”.
        g.preReleaseNotesEnabled = false
        g.density = device.density
        g.resize(device.width, device.height, device.width, device.height)
        // Zniknie powiadomienie o premii logowania, zanim powstanie pierwszy kadr.
        val tiny = Canvas(Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888))
        repeat(220) { g.frame(tiny, 1f / 60f) }
        g.onLanguageChanged()
        return g
    }

    private fun writePng(
        file: File, width: Int, height: Int, withAlpha: Boolean, draw: (Canvas) -> Unit,
    ) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.rgb(27, 42, 65))
            draw(Canvas(bitmap))
            bitmap.setHasAlpha(withAlpha)
            file.outputStream().use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    "Nie mozna zapisac: " + file.path
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    /** Sprawdzamy tez RGB/RGBA w naglowku PNG, a nie tylko wymiary. */
    private fun checkPng(file: File, width: Int, height: Int, withAlpha: Boolean) {
        assertTrue("Pusty PNG: " + file.path, file.length() > 1024L)
        val bytes = ByteArray(26)
        DataInputStream(file.inputStream()).use { it.readFully(bytes) }
        assertEquals("Sygnatura PNG", 137, bytes[0].toInt() and 255)
        assertEquals("Szerokosc: " + file.name, width, ByteBuffer.wrap(bytes, 16, 4).int)
        assertEquals("Wysokosc: " + file.name, height, ByteBuffer.wrap(bytes, 20, 4).int)
        assertEquals("8 bitow na kanal", 8, bytes[24].toInt() and 255)
        assertEquals("PNG RGB lub RGBA", if (withAlpha) 6 else 2, bytes[25].toInt() and 255)
    }

    /** Jednostki są w ich własnej symulacji, w ustalonych pozycjach do fotografii. */
    private fun sceneBattle(scene: Int): Battle {
        val era = if (scene == 3) 4 else 6
        val weather = if (scene == 4) Weather.NIGHT else Weather.CLEAR
        val mode = if (scene == 3) Mode.ENDLESS else Mode.SKIRMISH
        val battle = Battle(BattleConfig(
            mode, 50000L + scene, 1f, startEra = era, maxEra = Defs.ERA_COUNT - 1,
            weather = weather, variantsEnabled = false,
        ))
        for (side in battle.sides) {
            side.turretEra[0] = era
            side.turretTier[0] = 1
            for (i in 0 until 7) {
                val role = roles[i % roles.size]
                check(battle.spawn(side, side.unit(role)))
                val unit = battle.units.first { it.active && it.serial == battle.serialCounter }
                unit.x = if (side.index == 0) 1120f + i * 65f else 1690f - i * 65f
                unit.prevX = unit.x
                unit.anim = i * 0.7f
                unit.prevAnim = unit.anim
                unit.stride = if (i % 2 == 0) 0.65f else 0.1f
                unit.prevStride = unit.stride
                unit.attackAnim = if (i % 3 == 0) 0.6f else 0.1f
                unit.prevAttack = unit.attackAnim
                unit.moving = i % 2 == 0
                unit.hp = unit.maxHp * (if (i % 3 == 0) 0.72f else 1f)
            }
        }

        when (scene) {
            3 -> {
                battle.wave = 10
                battle.waveTimer = 120f
                battle.step()
                val boss = battle.currentBoss() ?: error("Nie ma bossa Endless")
                boss.x = 1485f
                boss.prevX = boss.x
                boss.attackAnim = 0.65f
                battle.fx.add(FxBuffer.HIT, 1680f, 48f, 1)
            }
            4 -> {
                val orders = BattleController(battle)
                check(orders.barrage(1430f))
                repeat(27) { battle.step() }
                battle.fx.add(FxBuffer.MUZZLE, 1360f, 55f, 0, 0f)
                battle.fx.add(FxBuffer.HIT, 1430f, 60f, 1)
                battle.fx.add(FxBuffer.EXPLOSION, 1440f, 65f, 1, 45f)
            }
        }
        return battle
    }

    private fun drawBattle(c: Canvas, g: Game, scene: Int, width: Int, height: Int, feature: Boolean = false) {
        val battle = sceneBattle(scene)
        // Na grafice promocyjnej pokazujemy wojska, nie zaslaniajacy je rozblysk.
        if (feature) battle.fx.count = 0
        val renderer = BattleRenderer(g, battle)
        renderer.layout(width.toFloat(), height.toFloat(),
            if (feature) 12f else 92f * g.ui.dp,
            if (feature) 10f else 20f * g.ui.dp)
        renderer.camX = Defs.LANE * 0.5f - renderer.worldVisible * 0.5f
        renderer.clampCam()
        renderer.lerp = 1f
        repeat(3) { renderer.render(c, 1f / 60f, width.toFloat(), height.toFloat()) }
    }

    /** Czytelna nakladka reklamowa, poza kluczowym obszarem gry. */
    private fun drawCaption(c: Canvas, caption: String, scene: Int, width: Float, height: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            textSize = minOf(52f, width * 0.032f, height * 0.077f)
        }
        val maxWidth = width * 0.79f
        if (p.measureText(caption) > maxWidth) p.textSize *= maxWidth / p.measureText(caption)
        val labelWidth = p.measureText(caption) + 72f
        // Wyzwania i potyczka: nie zaslaniamy przyciskow w dolnym panelu.
        val rightAligned = scene == 7 || scene == 8
        val left = if (rightAligned) width - labelWidth - width * 0.047f
            else width * 0.047f
        val top = height * when (scene) {
            3, 4 -> 0.125f
            7, 8 -> 0.015f
            else -> 0.84f
        }
        val rect = RectF(left, top, left + labelWidth, top + p.textSize * 1.65f)
        val panel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(226, 23, 34, 52) }
        c.drawRoundRect(rect, 15f, 15f, panel)
        panel.style = Paint.Style.STROKE
        panel.strokeWidth = 3f
        panel.color = Color.rgb(225, 168, 67)
        c.drawRoundRect(rect, 15f, 15f, panel)
        panel.style = Paint.Style.FILL
        c.drawRect(rect.left + 17f, rect.top + 13f, rect.left + 23f, rect.bottom - 13f, panel)
        p.color = Color.rgb(252, 247, 233)
        c.drawText(caption, rect.left + 36f, rect.centerY() + p.textSize * 0.34f, p)
    }

    /** Obie warstwy tej samej ikony, których używa launcher Androida. */
    private fun drawIcon(c: Canvas) {
        val resources = RuntimeEnvironment.getApplication().resources
        val background = resources.getDrawable(R.drawable.ic_launcher_background, null)
        val foreground = resources.getDrawable(R.drawable.ic_launcher_foreground, null)
        background.setBounds(0, 0, 512, 512)
        foreground.setBounds(0, 0, 512, 512)
        background.draw(c)
        foreground.draw(c)
    }

    /** Logo uzywa wspolnego emblematu menu z zadania 57. */
    private fun drawFeatureTitle(c: Canvas) {
        val shade = Paint().apply {
            shader = LinearGradient(0f, 0f, 690f, 0f,
                intArrayOf(0xFF172339.toInt(), 0xE8172339.toInt(), 0x00172339),
                null, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, 690f, 500f, shade)
        MenuEmblem().draw(c, 192f, 118f, 161f)
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeWidth = 10f
            color = Color.rgb(24, 23, 35)
        }
        val fill = Paint(outline).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(0f, 240f, 0f, 398f,
                intArrayOf(Color.rgb(255, 248, 196),
                    Color.rgb(255, 206, 60), Color.rgb(232, 132, 28)),
                null, Shader.TileMode.CLAMP)
        }
        outline.textSize = 71f
        fill.textSize = 71f
        c.drawText("CENTURY", 46f, 302f, outline)
        c.drawText("CENTURY", 46f, 302f, fill)
        outline.textSize = 96f
        fill.textSize = 96f
        c.drawText("WAR", 46f, 399f, outline)
        c.drawText("WAR", 46f, 399f, fill)
        val trim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(240, 177, 65)
            strokeWidth = 5f
        }
        c.drawLine(46f, 420f, 372f, 420f, trim)
    }
}
