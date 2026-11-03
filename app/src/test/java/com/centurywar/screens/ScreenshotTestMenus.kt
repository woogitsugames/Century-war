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
class ScreenshotTestMenus : ScreenshotTestBase() {
    /** Testuje układ rankingów i ustawień przy aktywnym PGS, bez połączeń sieciowych. */
    @Test
    fun playGamesMenus() {
        for ((tag, dims) in mapOf(
            "phone720" to intArrayOf(1600, 720, 2),
            "phone20x9" to intArrayOf(2400, 1080, 3),
            "foldOpen" to intArrayOf(2184, 1968, 3),
            "foldInner" to intArrayOf(1802, 2000, 4),
            "foldOuter" to intArrayOf(904, 2316, 3),
        )) {
            val g = game(dims[0], dims[1], dims[2] * 0.9f,
                if (tag == "phone720") Quality.LOW else Quality.HIGH)
            g.online = object : OnlineLeaderboards {
                override val available = true
                override val state = OnlineState.SIGNED_OUT
                override fun submit(board: String, score: Long) {}
            }
            g.setScreen(RankingsScreen(g))
            shot(g, "${tag}_pgs_rankings")
            g.setScreen(SettingsScreen(g))
            shot(g, "${tag}_pgs_settings")
            g.setScreen(PlayGamesScreen(g))
            shot(g, "${tag}_pgs_signin")
        }
    }

    /** Misje 1/2/5 oraz nagrody 1/2/5 w dwóch językach, na ekranie 720p. */
    @Test
    fun pluralForms() {
        val previousLang = L.lang
        try {
            for (code in arrayOf("pl", "en")) {
                val g = game(1600, 720, 1.8f, Quality.LOW)
                g.profile.lang = code
                L.setCode(code)
                val state = Missions(g.profile).refresh(System.currentTimeMillis())
                state.daily.clear()
                state.weekly.clear()
                for (n in intArrayOf(1, 2, 5)) {
                    state.daily.add(MissionItem("plural_wins_" + n, MissionType.WINS, n, 5))
                    state.weekly.add(MissionItem("plural_stars_" + n, MissionType.STARS, n, 5))
                }
                g.setScreen(MissionsScreen(g))
                shot(g, "phone720_" + code + "_plural_missions")

                val bs = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 99L, 1f))
                g.setScreen(bs)
                val battle = field(bs, "battle").get(bs) as Battle
                battle.surrender()
                val tiny = Canvas(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))
                repeat(3) { g.frame(tiny, 1f / 60f) }
                for (n in intArrayOf(1, 2, 5)) {
                    field(bs, "reward").setInt(bs, n)
                    field(bs, "rewardText").set(bs, "+" + L.plural("medals_n", n.toLong()))
                    shot(g, "phone720_" + code + "_plural_results_" + n)
                }
            }
        } finally {
            L.lang = previousLang
        }
    }

    /** Mapa kampanii: cztery proporcje, cofanie po osi i szczegóły etapu. */
    @Test
    fun campaignJourneyMap() {
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
            for (i in 0 until 12) g.profile.stars[i] = (i % 3) + 1
            // Zapowiedź epoki (68) i baner serii logowania przechwytywały dotknięcia przycisków
            // trudności (126); test dotyczy mapy, więc wyłączamy obie nakładki.
            for (era in 0 until Defs.ERA_COUNT) g.profile.markEraIntroSeen(era)
            clear74Notices(g)
            val screen = CampaignScreen(g)
            g.setScreen(screen)
            val pictures = field(screen, "regionPictures").get(screen) as Array<*>
            assertTrue("Dziewięć buforowanych regionów", pictures.size == Defs.ERA_COUNT)
            assertTrue("Każde tło nagrane", pictures.all { it != null })
            // Przycisk "OK" wskazówki (HintOverlay.OK_ID) też ma nieujemne id, więc liczymy kafelki etapów.
            assertTrue("Wszystkie etapy obecne", screen.buttons.count { it.tag is Stage } == 36)
            assertTrue("Orientacja mapy", field(screen, "portraitMap").getBoolean(screen) == (g.height > g.width))
            shot(g, tag + "_campaign_journey", 90)

            // Przesunięcie palcem ma działać na właściwej osi.
            val before = field(screen, "scroll").getFloat(screen)
            if (g.height > g.width) screen.onDrag(0f, -150 * g.ui.dp)
            else screen.onDrag(-150 * g.ui.dp, 0f)
            val after = field(screen, "scroll").getFloat(screen)
            assertTrue("Przewijanie mapy", after > before)
            shot(g, tag + "_campaign_journey_drag", 4)

            field(screen, "selected").set(screen, Campaign.stages[12])
            shot(g, tag + "_campaign_journey_info")
            if (tag == "phone720" || tag == "foldOpen") {
                for (difficulty in listOf(CampaignDifficulty.EASY, CampaignDifficulty.HARD)) {
                    val button = screen.buttons.first { it.id == -10 - difficulty.ordinal }
                    tap(g, button.r.centerX(), button.r.centerY())
                    shot(g, tag + "_campaign_difficulty_" + difficulty.name.lowercase())
                    assertTrue("Zapis trudności po dotknięciu", g.profile.campaignDifficulty == difficulty)
                }
            }
            field(screen, "selected").set(screen, null)
            field(screen, "scroll").setFloat(screen, field(screen, "maxScroll").getFloat(screen))
            shot(g, tag + "_campaign_journey_final", 1)
        }
    }

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

    /** Szybki zestaw: wszystkie ekrany menu i HUD bitwy z otwartymi panelami (bez długiej symulacji). */
    @Test
    fun menus() {
        for ((tag, dims) in screenshotDevices(mapOf(
            "phone720" to intArrayOf(1600, 720, 2),
            "phone20x9" to intArrayOf(2400, 1080, 3),
            "foldOpen" to intArrayOf(2184, 1968, 3),
            // Rozłożony Fold trzymany pionowo: na dużych ekranach orientacja jest dowolna.
            "foldPortrait" to intArrayOf(1968, 2184, 3),
            "foldInner" to intArrayOf(1802, 2000, 4),
            "foldOuter" to intArrayOf(904, 2316, 3),
        ))) {
            val g = game(dims[0], dims[1], dims[2] / 1.0f * 0.9f, if (tag == "phone720") Quality.LOW else Quality.HIGH)
            g.profile.stars[0] = 3; g.profile.stars[1] = 2; g.profile.stars[2] = 1
            g.profile.submit(Profile.BOARD_ENDLESS, 12345, 1)
            g.profile.submit(Profile.BOARD_ENDLESS, 8000, 1)
            // Start gry przyznaje nagrodę za logowanie: baner powiadomienia, potem czyste menu.
            shot(g, "${tag}_menu_login", 20)
            shot(g, "${tag}_menu", 200)
            // Osiągnięcia: kilka odblokowanych i postęp innych.
            g.profile.battles = 30; g.profile.wins = 12; g.profile.totalKills = 640
            g.profile.unlockedAchievements.addAll(listOf("win_1", "win_10", "battles_25", "kills_100"))
            g.profile.achievementCounters["TURRETS_BUILT"] = 21L
            val achievementScreen = AchievementsScreen(g)
            g.setScreen(achievementScreen); shot(g, "${tag}_achievements")
            field(achievementScreen, "scroll").setFloat(achievementScreen,
                field(achievementScreen, "maxScroll").getFloat(achievementScreen))
            shot(g, "${tag}_achievements_new")
            g.showAchievements(listOf(com.centurywar.meta.Achievements.all[1]))
            shot(g, "${tag}_achievements_toast", 20)
            val idleBitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
            try {
                val idleCanvas = Canvas(idleBitmap)
                repeat(200) { g.frame(idleCanvas, 1f / 60f) }
            } finally {
                idleBitmap.recycle()
            }
            // Misje: pierwsza dzienna ukończona (do odebrania).
            val ms = com.centurywar.meta.Missions(g.profile)
            ms.refresh(System.currentTimeMillis())
            ms.state.daily[0].progress = ms.state.daily[0].target
            ms.state.weekly[0].progress = ms.state.weekly[0].target / 2
            g.setScreen(MissionsScreen(g)); shot(g, "${tag}_missions")
            val cs = CampaignScreen(g)
            g.setScreen(cs); shot(g, "${tag}_campaign")
            field(cs, "selected").set(cs, com.centurywar.game.Campaign.stages[2])
            shot(g, "${tag}_campaign_info")
            g.setScreen(ResearchScreen(g)); shot(g, "${tag}_research")
            g.setScreen(ModeScreen(g, Mode.ENDLESS)); shot(g, "${tag}_mode_endless")
            g.setScreen(ModeScreen(g, Mode.DAILY)); shot(g, "${tag}_mode_daily")
            val weeklyWeek = Weekly.weekNumber(Daily.dayNumber())
            g.profile.claimWeekly(weeklyWeek, 20, weeklyWeek)
            g.profile.submit(Profile.weeklyBoard(weeklyWeek), 17890, Daily.dayNumber())
            g.profile.submit(Profile.weeklyBoard(weeklyWeek - 1), 16200, Daily.dayNumber() - 7L)
            g.setScreen(ModeScreen(g, Mode.DAILY, true)); shot(g, "${tag}_mode_weekly")
            g.setScreen(ModeScreen(g, Mode.SKIRMISH)); shot(g, "${tag}_mode_skirmish")
            g.setScreen(SkirmishSetupScreen(g)); shot(g, "${tag}_skirmish_default")
            g.profile.skirmishSetup = SkirmishSetup(
                startEra = 2, maxEra = 6, personality = AiPersonality.ENGINEER.ordinal,
                powerPercent = 175,
                modifierMask = (1 shl Modifier.DOUBLE_INCOME.ordinal) or
                    (1 shl Modifier.SLOW_XP.ordinal) or (1 shl Modifier.FOG_OF_WAR.ordinal),
            )
            val customScreen = SkirmishSetupScreen(g)
            g.setScreen(customScreen); shot(g, "${tag}_skirmish_custom")
            field(customScreen, "scroll").setFloat(customScreen,
                field(customScreen, "maxScroll").getFloat(customScreen))
            customScreen.layout()
            shot(g, "${tag}_skirmish_modifiers")
            g.setScreen(RankingsScreen(g)); shot(g, "${tag}_rankings")
            val weeklyRanking = RankingsScreen(g)
            g.setScreen(weeklyRanking)
            field(weeklyRanking, "tab").setInt(weeklyRanking, 4)
            shot(g, "${tag}_rankings_weekly")
            field(weeklyRanking, "previousWeek").setBoolean(weeklyRanking, true)
            shot(g, "${tag}_rankings_previous_week")
            val weeklyResults = BattleScreen(g, Weekly.config(weeklyWeek))
            g.setScreen(weeklyResults)
            val weeklyBattle = field(weeklyResults, "battle").get(weeklyResults) as Battle
            weeklyBattle.wave = 20
            weeklyBattle.surrender()
            shot(g, "${tag}_weekly_results", 90)
            val settings = SettingsScreen(g)
            g.setScreen(settings)
            if (tag == "phone720" || tag == "foldPortrait") {
                val sound = field(settings, "sfxLevel").get(settings) as com.centurywar.engine.Button
                val music = field(settings, "music").get(settings) as com.centurywar.engine.Button
                check(sound.r.width() > 50 * g.ui.dp && music.r.width() > 50 * g.ui.dp)
                check(sound.r.left >= g.insetL && sound.r.right <= g.width - g.insetR)
                check(sound.r.top > 0f && sound.r.bottom <= g.height - g.insetB)
                shot(g, "${tag}_sfx_settings")
            }
            shot(g, "${tag}_settings")
            if (tag == "phone720" || tag == "foldPortrait") {
                field(settings, "scroll").setFloat(settings, field(settings, "maxScroll").getFloat(settings))
                settings.layout()
                shot(g, "${tag}_settings_bottom")
            }
            val about = AboutScreen(g)
            g.setScreen(about); shot(g, "${tag}_about")
            about.onDrag(0f, -100000f)
            shot(g, "${tag}_about_end")
            g.setScreen(CodexScreen(g, 0)); shot(g, "${tag}_codex_era0")
            g.profile.levels["r_unitDmg"] = 5; g.profile.levels["r_unitHp"] = 4; g.profile.levels["r_train"] = 3
            val cx = CodexScreen(g, 6)
            g.setScreen(cx)
            cx.onClick(cx.buttons.first { it.id == -2 })
            shot(g, "${tag}_codex_era6")
            field(cx, "scroll").set(cx, field(cx, "maxScroll").get(cx))
            shot(g, "${tag}_codex_era6_end")
            g.profile.levels.clear()

            val bs = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 7L, 1f,
                startEra = 2, maxEra = 7,
                modifiers = setOf(Modifier.FOG_OF_WAR),
                aiPersonality = AiPersonality.ENGINEER))
            g.setScreen(bs)
            val b = field(bs, "battle").get(bs) as com.centurywar.game.Battle
            b.playerAi = Ai(b, 0)
            b.player.gold = 900f
            repeat(60 * 20) { g.frame(Canvas(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)), 1f / 60f) }
            b.playerAi = null
            shot(g, "${tag}_battle_hud")
            b.player.abilityCd[0] = 0f
            bs.onClick(field(bs, "barrageBtn").get(bs) as com.centurywar.engine.Button)
            shot(g, "${tag}_battle_barrage_aim")
            bs.onBack()
            // Podgląd ulepszenia w trzech formatach ekranu (telefon i rozłożony Fold).
            field(bs, "showUpgrade").setInt(bs, 0)
            shot(g, "${tag}_battle_unit_upgrade")
            field(bs, "showUpgrade").setInt(bs, -1)
            field(bs, "showTurrets").set(bs, true)
            shot(g, "${tag}_battle_turrets")
            field(bs, "showTurrets").set(bs, false)
            b.pendingChoice = arrayOf(com.centurywar.game.Doctrine.ECONOMY, com.centurywar.game.Doctrine.FURY, com.centurywar.game.Doctrine.ARSENAL)
            shot(g, "${tag}_battle_doctrine")
            b.pendingChoice = null
            field(bs, "paused").set(bs, true)
            shot(g, "${tag}_battle_pause")
            shot(g, "${tag}_skirmish_pause_named")
            field(bs, "paused").set(bs, false)
            b.surrender()
            shot(g, "${tag}_battle_results", 90)
            shot(g, "${tag}_skirmish_results_unranked")

            // Endless 2.0: osobne kadry zapowiedzi, wyboru kart i aktywnego bossa.
            val es = BattleScreen(g, BattleConfig(Mode.ENDLESS, 2026L, 1f))
            g.setScreen(es)
            val endless = field(es, "battle").get(es) as Battle
            repeat(4) {
                endless.waveTimer = 0f
                endless.step()
            }
            shot(g, "${tag}_endless_preview")
            endless.waveTimer = 0f
            endless.step()
            assertTrue("Karty po piątej fali", endless.pendingCards != null)
            shot(g, "${tag}_endless_cards")
            endless.chooseEndlessCard(0)
            repeat(5) {
                endless.waveTimer = 0f
                endless.step()
                if (endless.wave != 10 && endless.pendingCards != null) endless.chooseEndlessCard(0)
            }
            assertTrue("Boss po dziesiątej fali", endless.currentBoss() != null)
            shot(g, "${tag}_endless_boss_with_cards")
            endless.chooseEndlessCard(0)
            shot(g, "${tag}_endless_boss")
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

    /** Zrzuty ustawień i rankingu serwera: tryb bez zgody, bez żadnych zapytań HTTP. */
    @Test
    fun globalRankingMenus() {
        for ((tag, dims) in mapOf(
            "phone720" to intArrayOf(1600, 720, 2),
            "foldOpen" to intArrayOf(2184, 1968, 3),
            "foldInner" to intArrayOf(1802, 2000, 4),
            "foldOuter" to intArrayOf(904, 2316, 3),
        )) {
            val g = game(dims[0], dims[1], dims[2] * 0.9f,
                if (tag == "phone720") Quality.LOW else Quality.HIGH)
            val queue = File.createTempFile("score", ".json").apply { delete() }
            val scores = ScoreClient(queue, "https://scores.example.invalid",
                "testing-key-at-least-32-characters-long", g.profile)
            g.scores = scores // Domyślnie wyłączony: nie wysyła żadnego żądania.
            g.setScreen(SettingsScreen(g))
            shot(g, "${tag}_global_settings")
            g.setScreen(RankingsScreen(g))
            shot(g, "${tag}_global_rankings_local")
            g.setScreen(GlobalRankingsScreen(g))
            shot(g, "${tag}_global_rankings")
            scores.close()
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
