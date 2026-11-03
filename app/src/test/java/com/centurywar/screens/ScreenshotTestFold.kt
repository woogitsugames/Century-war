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
class ScreenshotTestFold : ScreenshotTestBase() {
    /**
     * Zlecenie 98: rzeczywisty obszar 1802x2000 i wąski ekran zewnętrzny.
     * Audytuje wszystkie stany menu przy zwykłym i powiększonym tekście.
     * PNG z polskiego interfejsu są publikowane jako artefakt CI.
     */
    @Test
    fun foldAudit98() {
        val originalLang = L.lang
        try {
            for ((tag, dims) in screenshotDevices(linkedMapOf(
                "foldInner" to intArrayOf(1802, 2000, 4),
                "foldOuter" to intArrayOf(904, 2316, 3),
            ))) {
                val g = game(dims[0], dims[1], dims[2] * 0.9f, Quality.HIGH)
                g.profile.hintsSeen = Profile.HINT_VALID_MASK
                g.profile.hintsEnabled = false
                g.profile.medals = 900
                g.profile.stars[0] = 3
                g.profile.stars[1] = 2
                // Zapisane powiadomienie o logowaniu nie przesłania badanych ekranów.
                (field(g, "noticeQueue").get(g) as MutableCollection<*>).clear()
                (field(g, "noticeIcons").get(g) as MutableCollection<*>).clear()
                field(g, "achievementSeconds").setFloat(g, 0f)
                // Zrzut kampanii ma pokazać mapę, nie nakładkę zapowiedzi epoki.
                for (era in 0 until Defs.ERA_COUNT) g.profile.markEraIntroSeen(era)

                for (lang in arrayOf("pl", "de")) for (scale in intArrayOf(100, 130)) {
                    g.profile.lang = lang
                    g.profile.textScale = scale
                    g.onLanguageChanged()
                    g.applyAccessibility()
                    visitAuditScreens(g) { name, screen, title ->
                        assertNoCollisions(g, screen, name, title)
                        val buttons = screen.buttons.filter { it.visible && it.r.width() > 0f && it.r.height() > 0f }
                        for (button in buttons) {
                            assertTrue("$tag/$lang/$scale/$name: przycisk ${button.id} za niski: ${button.r.height() / g.ui.dp} dp",
                                button.r.height() + 0.5f >= 48 * g.ui.dp)
                            assertTrue("$tag/$lang/$scale/$name: przycisk ${button.id} za wąski: ${button.r.width() / g.ui.dp} dp",
                                button.r.width() + 0.5f >= 48 * g.ui.dp)
                        }
                        if (lang == "pl" && scale == 100) shot(g, "audit98_${tag}_${name}", 1)
                    }
                }

                g.profile.lang = "pl"
                g.profile.textScale = 100
                g.onLanguageChanged()
                g.applyAccessibility()
                val battleScreen = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 98001L, 1f))
                g.setScreen(battleScreen)
                // Nie zmieniamy zatwierdzonego układu HUD na wąskich ekranach.
                shot(g, "audit98_${tag}_battle_hud", 2)
                battleScreen.onClick(field(battleScreen, "pauseBtn").get(battleScreen) as Button)
                shot(g, "audit98_${tag}_battle_pause", 2)
                field(battleScreen, "paused").setBoolean(battleScreen, false)
                val battle = field(battleScreen, "battle").get(battleScreen) as Battle
                battle.surrender()
                shot(g, "audit98_${tag}_battle_results", 90)
            }
        } finally {
            L.lang = originalLang
        }
    }

    /**
     * Zlecenie 123: 904x2316@2.7 i 720x1280@1.8, wszystkie epoki i trzy języki.
     * Osobno sprawdza obrysy sylwetek na przezroczystym obrazie.
     */
    @Test
    fun foldCoverOverlays123() {
        val oldLang = L.lang
        try {
            for ((device, dims) in screenshotDevices(linkedMapOf(
                "foldOuter" to intArrayOf(904, 2316, 3),
                "phone720" to intArrayOf(720, 1280, 2),
            ))) for (lang in arrayOf("pl", "de", "uk")) {
                val g = game(dims[0], dims[1], dims[2] * 0.9f,
                    if (device == "phone720") Quality.LOW else Quality.HIGH)
                g.profile.lang = lang
                g.profile.eraIntrosEnabled = false
                g.profile.hintsEnabled = true
                g.onLanguageChanged()
                clear74Notices(g)
                val dp = g.ui.dp
                val safe = RectF(g.insetL.toFloat(), g.insetT.toFloat(),
                    g.width - g.insetR.toFloat(), g.height - g.insetB.toFloat())
                val prefix = "cover123_${device}_${lang}"

                fun capture(name: String, draw: (Canvas) -> Unit) {
                    val bitmap = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
                    try {
                        draw(Canvas(bitmap))
                        val file = File(out, "${prefix}_${name}.png")
                        file.outputStream().use {
                            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                        }
                        assertTrue("Pusty zrzut: ${file.name}", file.length() > 2500L)
                    } finally {
                        bitmap.recycle()
                    }
                }

                val figuresBmp = if (lang == "pl")
                    Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888) else null
                val pixels = if (lang == "pl") IntArray(g.width * g.height) else null
                try {
                    for (era in 0 until Defs.ERA_COUNT) {
                        val intro = EraIntroOverlay.preview(g, era)
                        intro.layout()
                        val panel = field(intro, "panel").get(intro) as RectF
                        @Suppress("UNCHECKED_CAST")
                        val cards = field(intro, "cards").get(intro) as Array<RectF>
                        @Suppress("UNCHECKED_CAST")
                        val areas = field(intro, "portraitAreas").get(intro) as Array<RectF>
                        val where = "${prefix}/era${era}"
                        assertTrue("$where: panel poza ekranem", safe.contains(panel))
                        for (i in 0..3) {
                            val card = cards[i]
                            val area = areas[i]
                            assertTrue("$where: karta $i poza panelem", panel.contains(card))
                            assertTrue("$where: portret $i poza kartą", card.contains(area))
                            assertTrue("$where: brak marginesu portretu $i",
                                area.left - card.left >= 4f * dp &&
                                    card.right - area.right >= 4f * dp)
                            assertTrue("$where: portret koliduje z podpisem $i",
                                area.bottom <= card.bottom - 24f * dp)
                            val name = L[Defs.unit(era, Role.entries[i]).nameKey]
                            val width = card.width() - 8f * dp
                            assertTrue("$where: podpis nie mieści się: $name",
                                g.ui.measure(name, g.ui.fit(name, 11f, width, 5f), true) <= width + dp)
                        }
                        val lines = field(intro, "storyLines").get(intro) as Array<*>
                        val count = field(intro, "storyLineCount").getInt(intro)
                        assertTrue("$where: niepełny opis epoki", count in 1..3)
                        for (i in 0 until count) {
                            val line = lines[i] as String
                            assertTrue("$where: opis poza panelem: $line",
                                g.ui.measure(line, 14f) <= panel.width() - 36f * dp + dp)
                        }
                        capture("era$era") { intro.draw(it) }

                        if (figuresBmp != null && pixels != null) {
                            figuresBmp.eraseColor(Color.TRANSPARENT)
                            val picture = field(intro, "figures").get(intro) as android.graphics.Picture
                            Canvas(figuresBmp).drawPicture(picture)
                            figuresBmp.getPixels(pixels, 0, g.width, 0, 0, g.width, g.height)
                            var painted = 0
                            for (y in 0 until g.height) for (x in 0 until g.width) {
                                if (pixels[y * g.width + x] ushr 24 == 0) continue
                                painted++
                                var inside = false
                                for (area in areas) {
                                    if (x >= area.left - 1f && x <= area.right + 1f &&
                                        y >= area.top - 1f && y <= area.bottom + 1f) {
                                        inside = true
                                        break
                                    }
                                }
                                if (!inside) throw AssertionError(
                                    "$where: piksel sylwetki poza portretem ($x,$y)")
                            }
                            assertTrue("$where: sylwetki nie zostały narysowane", painted > 100)
                        }
                    }
                } finally {
                    figuresBmp?.recycle()
                }

                g.setScreen(MenuScreen(g))
                for ((id, name) in arrayOf(
                    HintOverlay.RESEARCH to "research",
                    HintOverlay.MISSIONS to "missions",
                    HintOverlay.DAILY_MENU to "daily",
                    HintOverlay.ENDLESS_MENU to "endless",
                )) {
                    g.profile.hintsSeen = 0L
                    val hint = HintOverlay(g)
                    hint.showScreen(id, RectF(g.width * 0.38f, g.height * 0.35f,
                        g.width * 0.62f, g.height * 0.4f))
                    assertTrue("$prefix: brak dymka $name", hint.activeId == id)
                    capture("hint_$name") { g.frame(it, 0f); hint.draw(it) }
                    val frame = field(hint, "panel").get(hint) as RectF
                    assertTrue("$prefix/$name: dymek poza ekranem",
                        frame.width() > 0f && safe.contains(frame))
                    assertTrue("$prefix/$name: OK poza dymkiem", frame.contains(hint.okBtn.r))
                    val n = field(hint, "lineCount").getInt(hint)
                    val textLines = field(hint, "lines").get(hint) as Array<*>
                    val font = field(hint, "hintFont").getFloat(hint)
                    val compact = field(hint, "compact").getBoolean(hint)
                    val width = frame.width() - (if (compact) 87f else 24f) * dp
                    for (i in 0 until n) {
                        val line = textLines[i] as String
                        assertTrue("$prefix/$name: tekst wykracza poza dymek",
                            g.ui.measure(line, font) <= width + dp)
                    }
                }

                // Samouczek i dialog potwierdzenia pominięcia.
                g.profile.tutorialDone = false
                g.profile.hintsEnabled = false
                val tutorial = BattleScreen(g, Campaign.config(
                    Campaign.stages[0], Bonuses(), 12301L, tutorial = true))
                g.setScreen(tutorial)
                shot(g, "${prefix}_tutorial", 2)
                val tut = field(tutorial, "tut").get(tutorial) as TutorialOverlay
                assertTrue("$prefix: samouczek nie wystartował", tut.showing)
                assertTrue("$prefix: panel samouczka poza ekranem",
                    tut.panel.width() > 0f && safe.contains(tut.panel))
                assertTrue("$prefix: panel samouczka zasłania cel",
                    !RectF.intersects(tut.panel, tut.hole))
                assertTrue("$prefix: Pomiń poza ekranem", safe.contains(tut.skipBtn.r))
                tut.onClick(tut.skipBtn)
                assertTrue("$prefix: brak potwierdzenia", tut.skipConfirmationVisible)
                shot(g, "${prefix}_tutorial_confirm", 2)
                assertTrue("$prefix: potwierdzenie poza ekranem", safe.contains(tut.panel))
                assertTrue("$prefix: przyciski potwierdzenia poza panelem",
                    tut.panel.contains(tut.nextBtn.r) && tut.panel.contains(tut.skipBtn.r))

                // Pauza oraz karty doktryn i Endless.
                g.profile.tutorialDone = true
                val bs = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 12302L, 1f))
                g.setScreen(bs)
                bs.onClick(field(bs, "pauseBtn").get(bs) as Button)
                shot(g, "${prefix}_pause", 2)
                assert74Pause(g, bs, prefix)

                fun assertChoiceCards(screen: BattleScreen, kind: String) {
                    @Suppress("UNCHECKED_CAST")
                    val buttons = field(screen, "doctrineBtn").get(screen) as Array<Button>
                    val counts = field(screen, "choiceDescCount").get(screen) as IntArray
                    @Suppress("UNCHECKED_CAST")
                    val lines = field(screen, "choiceDescLines").get(screen) as Array<Array<String?>>
                    val b = field(screen, "battle").get(screen) as Battle
                    for (i in 0..2) {
                        val card = buttons[i].r
                        assertTrue("$prefix/$kind: karta $i poza ekranem", safe.contains(card))
                        val width = card.width() - 22f * dp
                        for (j in 0 until counts[i]) {
                            val line = lines[i][j] ?: continue
                            assertTrue("$prefix/$kind: opis $i zbyt szeroki",
                                g.ui.measure(line, 12f) <= width + dp)
                        }
                        val mr = minOf(33f * dp, card.width() * 0.2f, card.height() * 0.18f)
                        val end = card.top + 10f * dp + mr * 2f +
                            (if (b.pendingCards != null) 56f else 45f) * dp +
                            (counts[i] - 1).coerceAtLeast(0) * 12f * dp * g.ui.textScale * 1.3f
                        assertTrue("$prefix/$kind: opis $i nachodzi na dół karty",
                            end < card.bottom - 6f * dp)
                    }
                }

                field(bs, "paused").setBoolean(bs, false)
                val battle = field(bs, "battle").get(bs) as Battle
                battle.pendingChoice = arrayOf(
                    com.centurywar.game.Doctrine.ECONOMY,
                    com.centurywar.game.Doctrine.FURY,
                    com.centurywar.game.Doctrine.ARSENAL,
                )
                shot(g, "${prefix}_doctrine", 2)
                assertChoiceCards(bs, "doctrine")

                val endlessScreen = BattleScreen(g, BattleConfig(Mode.ENDLESS, 12303L, 1f))
                g.setScreen(endlessScreen)
                val endless = field(endlessScreen, "battle").get(endlessScreen) as Battle
                repeat(5) { endless.waveTimer = 0f; endless.step() }
                assertTrue("$prefix: brak kart Endless", endless.pendingCards != null)
                shot(g, "${prefix}_endless_cards", 2)
                assertChoiceCards(endlessScreen, "endless")
            }
        } finally {
            L.lang = oldLang
        }
    }

    /**
     * Zlecenie 132: rzeczywiste 2048x1536 @2 i 2520x1080 @3.
     * Trzy języki, nowe ekrany, kopia/import, HUD, pauza i wykres wyników.
     * Nazwy PNG nie pokrywają się z zatwierdzonymi wzorcami zlecenia 100.
     */
    @Test
    fun tabletAudit132() {
        val oldLang = L.lang
        val requested = System.getenv("CW_SCREENSHOT_DEVICE")?.trim().orEmpty()
        try {
            for ((tag, dims) in linkedMapOf(
                "tablet4x3" to intArrayOf(2048, 1536, 2),
                "phone21x9" to intArrayOf(2520, 1080, 3),
            )) {
                // Równoległy CI może przypisać testowi tylko jeden format.
                if (requested.isNotEmpty() && requested != tag) continue
                for (lang in arrayOf("pl", "de", "uk")) {
                    val g = game(dims[0], dims[1], dims[2].toFloat(), Quality.HIGH)
                    val historyFile = File.createTempFile("cw-audit132", ".json").apply { delete() }
                    val history = BattleHistory(historyFile)
                    try {
                        if (tag == "phone21x9") g.setInsets(110, 0, 70, 65)
                        g.profile.lang = lang
                        g.profile.textScale = 100
                        g.profile.hintsEnabled = false
                        g.profile.hintsSeen = Profile.HINT_VALID_MASK
                        g.onLanguageChanged()
                        g.applyAccessibility()
                        g.profile.stars[0] = 3
                        g.profile.stars[1] = 2
                        for (era in 0 until Defs.ERA_COUNT) g.profile.markEraIntroSeen(era)
                        (field(g, "noticeQueue").get(g) as MutableCollection<*>).clear()
                        (field(g, "noticeIcons").get(g) as MutableCollection<*>).clear()
                        field(g, "achievementSeconds").setFloat(g, 0f)
                        g.online = object : OnlineLeaderboards {
                            override val available = true
                            override val state = OnlineState.SIGNED_OUT
                            override fun submit(board: String, score: Long) {}
                        }
                        val now = System.currentTimeMillis()
                        repeat(14) { i ->
                            history.add(BattleHistory.Record(
                                date = now - i * 86_400_000L,
                                mode = if (i % 3 == 0) Mode.ENDLESS.ordinal else Mode.SKIRMISH.ordinal,
                                result = if (i % 4 == 0) BattleHistory.LOSS else BattleHistory.WIN,
                                seconds = 130 + i * 12,
                                score = 1500L + i * 90L,
                                wave = 2 + i,
                            ))
                        }
                        g.history = history
                        g.historyLoaded = true

                        fun capture(name: String, screen: Screen, title: String? = null) {
                            g.setScreen(screen)
                            screen.update(0f)
                            assertNoCollisions(g, screen, "audit132_" + name, title)
                            shot(g, "audit132_" + tag + "_" + lang + "_" + name, 1)
                        }

                        capture("stats", StatsScreen(g), "records")
                        capture("whats_new", WhatsNewScreen(g), "wn_history")
                        capture("whats_new_auto", WhatsNewScreen(g, true), "wn_title")
                        capture("play_games", PlayGamesScreen(g), "play_games")

                        val settings = SettingsScreen(g)
                        g.setScreen(settings)
                        settings.onClick(settings.buttons.first { it.id == 20 })
                        val maxScroll = field(settings, "maxScroll").getFloat(settings)
                        // Przewiń dokładnie do pierwszej kontrolki kopii profilu.
                        // Stały offset na bardzo niskim ekranie potrafił ją uciąć.
                        val export = settings.buttons.first { it.id == 21 }
                        val contentTop = field(settings, "contentTop").getFloat(settings)
                        val currentScroll = field(settings, "scroll").getFloat(settings)
                        val scrollToExport = (currentScroll + export.r.top - contentTop -
                            12 * g.ui.dp).coerceIn(0f, maxScroll)
                        field(settings, "scroll").setFloat(settings, scrollToExport)
                        settings.layout()
                        settings.update(0f)
                        assertNoCollisions(g, settings, "audit132_backup", "settings")
                        for (id in intArrayOf(21, 22)) {
                            val button = settings.buttons.first { it.id == id }
                            assertTrue("132 " + tag + "/" + lang + ": kopia/import ukryty " + id,
                                button.visible)
                        }
                        shot(g, "audit132_" + tag + "_" + lang + "_backup_import", 1)

                        capture("accessibility", AccessibilityScreen(g), "accessibility")
                        val accessBottom = AccessibilityScreen(g)
                        g.setScreen(accessBottom)
                        field(accessBottom, "scroll").setFloat(accessBottom,
                            field(accessBottom, "maxScroll").getFloat(accessBottom))
                        accessBottom.layout()
                        accessBottom.update(0f)
                        assertNoCollisions(g, accessBottom, "audit132_access_bottom", "accessibility")
                        shot(g, "audit132_" + tag + "_" + lang + "_access_bottom", 1)

                        capture("hints", HintSettingsScreen(g), "hint_settings")
                        val campaign132 = CampaignScreen(g)
                        capture("campaign", campaign132, "campaign")
                        field(campaign132, "selected").set(campaign132, Campaign.stages[2])
                        capture("campaign_info", campaign132, "campaign")

                        val battleScreen = BattleScreen(g,
                            BattleConfig(Mode.SKIRMISH, 132132L, 1f, startEra = 2, maxEra = 7))
                        g.setScreen(battleScreen)
                        val safe = RectF(g.insetL, g.insetT,
                            g.width - g.insetR, g.height - g.insetB)
                        val pauseButton = field(battleScreen, "pauseBtn").get(battleScreen) as Button
                        assertTrue("132 " + tag + "/" + lang + ": pauza poza ekranem",
                            safe.contains(pauseButton.r))
                        shot(g, "audit132_" + tag + "_" + lang + "_battle_hud", 2)
                        battleScreen.onClick(pauseButton)
                        assert74Pause(g, battleScreen, "132 " + tag + "/" + lang)
                        shot(g, "audit132_" + tag + "_" + lang + "_battle_pause", 2)

                        val results = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 132133L, 1f))
                        g.setScreen(results)
                        val battle = field(results, "battle").get(results) as Battle
                        val samples = field(results, "battleSummary").get(results) as BattleSummarySamples
                        repeat(240) {
                            if (!battle.over) {
                                battle.step()
                                samples.capture(battle)
                            }
                        }
                        if (!battle.over) {
                            val commands = field(results, "commands").get(results) as BattleController
                            assertTrue("132: nie udalo sie zakonczyc bitwy", commands.surrender())
                        }
                        val tiny = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
                        try {
                            val tinyCanvas = Canvas(tiny)
                            repeat(70) { g.frame(tinyCanvas, 1f / 60f) }
                        } finally {
                            tiny.recycle()
                        }
                        val panel = field(results, "resultPanel").get(results) as RectF
                        val area = field(results, "resultChartArea").get(results) as RectF
                        val chartButton = field(results, "resultChartTab").get(results) as Button
                        val nextButton = field(results, "continueBtn").get(results) as Button
                        assertTrue("132 " + tag + "/" + lang + ": wyniki poza wcieciami",
                            safe.contains(panel))
                        assertTrue("132 " + tag + "/" + lang + ": wykres koliduje z przyciskami",
                            area.height() >= 95 * g.ui.dp &&
                                area.bottom < nextButton.r.top &&
                                area.top > chartButton.r.bottom)
                        assertTrue("132 " + tag + "/" + lang + ": brak zakladki wykresu",
                            chartButton.visible)
                        shot(g, "audit132_" + tag + "_" + lang + "_battle_results", 1)
                        assertTrue("132 " + tag + "/" + lang + ": obciete wiersze",
                            field(results, "resultRowsClipped").getInt(results) == 0)
                        results.onClick(chartButton)
                        shot(g, "audit132_" + tag + "_" + lang + "_battle_chart", 1)
                    } finally {
                        history.awaitWrites()
                        history.close()
                    }
                }
            }
        } finally {
            L.lang = oldLang
        }
    }
}
