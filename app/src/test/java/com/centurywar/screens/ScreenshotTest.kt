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
import com.centurywar.game.Defs
import com.centurywar.game.Role
import com.centurywar.game.Mode
import com.centurywar.meta.MissionItem
import com.centurywar.meta.MissionType
import com.centurywar.meta.Missions
import com.centurywar.game.Weather
import com.centurywar.game.Daily
import com.centurywar.game.Weekly
import com.centurywar.meta.OnlineLeaderboards
import com.centurywar.meta.OnlineState
import com.centurywar.meta.Profile
import com.centurywar.meta.ReplayStore
import com.centurywar.meta.BattleHistory
import com.centurywar.meta.ScoreClient
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
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ScreenshotTest {

    private val out = File("build/screenshots").apply { mkdirs() }

    private fun game(w: Int, h: Int, density: Float, q: Int): Game {
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val f = File.createTempFile("profile", ".json").apply { delete() }
        val g = Game(host, Profile(f).apply { tutorialDone = true; medals = 340 }, q)
        g.density = density
        g.resize(w, h, w, h)
        return g
    }

    private fun shot(g: Game, name: String, frames: Int = 3) {
        // Zrzuty dokumentują ekran docelowy, nie pośrednią klatkę nasunięcia.
        g.finishScreenTransition()
        val bmp = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        repeat(frames) { g.frame(c, 1f / 60f) }
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
    }

    private fun tap(g: Game, x: Float, y: Float) {
        g.pushTouch(TouchEvent.DOWN, x, y, 0)
        g.pushTouch(TouchEvent.UP, x, y, 0)
    }

    private fun field(o: Any, name: String) = o.javaClass.getDeclaredField(name).apply { isAccessible = true }

    /** Testuje układ rankingów i ustawień przy aktywnym PGS, bez połączeń sieciowych. */
    @Test
    fun playGamesMenus() {
        for ((tag, dims) in mapOf(
            "phone720" to intArrayOf(1600, 720, 2),
            "phone20x9" to intArrayOf(2400, 1080, 3),
            "foldOpen" to intArrayOf(2184, 1968, 3),
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

    /** Czytelność salwy i znaczników na małym telefonie oraz rozłożonym Foldzie. */
    @Test
    fun barrageDuringBattle() {
        for ((tag, dims) in mapOf(
            "phone720" to intArrayOf(1600, 720, 2),
            "foldOpen" to intArrayOf(2184, 1968, 3),
        )) {
            val g = game(dims[0], dims[1], dims[2] * 0.9f,
                if (tag == "phone720") Quality.LOW else Quality.HIGH)
            g.profile.screenShake = 2
            val bs = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 77L, 1f, startEra = 6))
            g.setScreen(bs)
            val battle = field(bs, "battle").get(bs) as Battle
            assertTrue(battle.commandController!!.barrage(560f))
            assertTrue(battle.castBarrage(battle.sides[1], 560f))
            val bmp = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            repeat(42) { g.frame(canvas, Battle.STEP) }
            File(out, tag + "_battle_barrage.png").outputStream().use {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bmp.recycle()
        }
    }

    /** Szybki zestaw: wszystkie ekrany menu i HUD bitwy z otwartymi panelami (bez długiej symulacji). */
    @Test
    fun menus() {
        for ((tag, dims) in mapOf(
            "phone720" to intArrayOf(1600, 720, 2),
            "phone20x9" to intArrayOf(2400, 1080, 3),
            "foldOpen" to intArrayOf(2184, 1968, 3),
            // Rozłożony Fold trzymany pionowo: na dużych ekranach orientacja jest dowolna.
            "foldPortrait" to intArrayOf(1968, 2184, 3),
        )) {
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
            g.setScreen(AchievementsScreen(g)); shot(g, "${tag}_achievements")
            g.showAchievements(listOf(com.centurywar.meta.Achievements.all[1]))
            shot(g, "${tag}_achievements_toast", 20)
            repeat(200) { g.frame(Canvas(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)), 1f / 60f) }
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


    /** Odtwarzacz i lista w telefonach oraz obu orientacjach rozłożonego Folda. */
    @Test
    fun replays() {
        val cfg = BattleConfig(Mode.SKIRMISH, 299L, 0.65f, startEra = 2, maxEra = 4,
            playerBonuses = Bonuses(baseHpMul = 15f))
        val original = Battle(cfg)
        val commands = BattleController(original)
        original.playerAi = Ai(original, 0)
        repeat(60 * 32) {
            if (!original.over) {
                original.step()
                original.fx.count = 0
            }
        }
        if (!original.over) commands.surrender()
        val bytes = ReplayRecord.encode(cfg, commands.log(), ReplayRecord.Meta(
            cfg.mode, cfg.stageIndex, original.winner, original.score(),
            original.time, original.wave, 1793461200000L, "Dowódca",
        ))
        for ((tag, dims) in mapOf(
            "phone720" to intArrayOf(1600, 720, 2),
            "phone20x9" to intArrayOf(2400, 1080, 3),
            "foldOpen" to intArrayOf(2184, 1968, 3),
            "foldPortrait" to intArrayOf(1968, 2184, 3),
        )) {
            val g = game(dims[0], dims[1], dims[2] * 0.9f,
                if (tag == "phone720") Quality.LOW else Quality.HIGH)
            val store = ReplayStore(File.createTempFile("cw-replay", "").apply { delete(); mkdirs() })
            g.replays = store
            val id = store.newId()
            store.saveAsync(id, bytes)
            val ready = CountDownLatch(1)
            store.listAsync { ready.countDown() }
            assertTrue(ready.await(8, TimeUnit.SECONDS))

            g.setScreen(ReplayListScreen(g))
            // Lista trafia na wątek gry przez Game.post.
            shot(g, "${tag}_replay_list", 5)

            val screen = ReplayScreen(g, bytes)
            g.setScreen(screen)
            val player = field(screen, "player").get(screen) as ReplayPlayer
            player.advance(minOf(900, player.totalSteps))
            shot(g, "${tag}_replay_viewer", 5)
        }
    }

    @Test
    fun screens() {
        for ((tag, dims) in mapOf(
            "phone720" to intArrayOf(1600, 720, 2),
            "phone20x9" to intArrayOf(2400, 1080, 3),
            "foldOpen" to intArrayOf(2184, 1968, 3),
        )) {
            val g = game(dims[0], dims[1], dims[2] / 1.0f * 0.9f, if (tag == "phone720") Quality.LOW else Quality.HIGH)
            shot(g, "${tag}_menu")
            g.setScreen(CampaignScreen(g)); shot(g, "${tag}_campaign")
            g.setScreen(ResearchScreen(g)); shot(g, "${tag}_research")

            for (era in intArrayOf(0, 3, 5, 7)) {
                val bs = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 5L + era, 1f, startEra = era, maxEra = 7))
                g.setScreen(bs)
                val f = BattleScreen::class.java.getDeclaredField("battle").apply { isAccessible = true }
                val b = f.get(bs) as com.centurywar.game.Battle
                b.playerAi = Ai(b, 0)
                val bmp = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp)
                repeat(60 * 70) { g.frame(c, 1f / 60f) }
                File(out, "${tag}_battle_era$era.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    /**
     * Skryptowana bitwa samouczka (etap 1 kampanii, pierwszy raz): gracz wykonuje polecenia
     * z nakładki, a test robi zrzuty kolejnych kroków. phone20x9 ma wcięcia (wycięcie aparatu,
     * pasek gestów), żeby sprawdzić, że ramki i wycięcia je respektują.
     */
    @Test
    fun tutorial() {
        for ((tag, dims) in mapOf(
            "phone720" to intArrayOf(1600, 720, 2),
            "phone20x9" to intArrayOf(2400, 1080, 3),
            "foldOpen" to intArrayOf(2184, 1968, 3),
        )) {
            val g = game(dims[0], dims[1], dims[2] / 1.0f * 0.9f, if (tag == "phone720") Quality.LOW else Quality.HIGH)
            g.profile.tutorialDone = false
            if (tag == "phone20x9") g.setInsets(110, 0, 70, 40)
            val bs = BattleScreen(g, Campaign.config(Campaign.stages[0], Bonuses(), 11L, tutorial = true))
            g.setScreen(bs)
            val b = field(bs, "battle").get(bs) as Battle
            val tut = field(bs, "tut").get(bs) as TutorialOverlay
            val speed = field(bs, "speed")
            val bmp = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            var frame = 0
            fun until(step: Int) {
                var i = 0
                while (!(tut.step == step && tut.showing) && !tut.finished && !b.over && i < 60 * 900) {
                    // czekając na warunek kroku "gramy": szybko i z dosyłaniem piechoty
                    speed.set(bs, if (tut.showing) 1 else 5)
                    if (!tut.showing && tut.step >= TutorialOverlay.S_KILL && frame % 90 == 0) b.queueUnit(b.player, if (frame % 180 == 0) Role.MELEE else Role.RANGED)
                    g.frame(c, 1f / 60f); i++; frame++
                }
                speed.set(bs, 1)
                repeat(40) { g.frame(c, 1f / 60f) } // animacja pierścienia, odblokowanie "Dalej"
                assertTrue("$tag: krok $step (jest ${tut.step}, showing=${tut.showing}, over=${b.over})", tut.step == step && tut.showing)
            }
            fun tapHole() { tap(g, tut.hole.centerX(), tut.hole.centerY()); g.frame(c, 1f / 60f) }
            fun next() { tap(g, tut.nextBtn.r.centerX(), tut.nextBtn.r.centerY()); g.frame(c, 1f / 60f) }

            until(TutorialOverlay.S_MELEE); shot(g, "${tag}_tut01_melee"); tapHole()
            until(TutorialOverlay.S_INCOME); shot(g, "${tag}_tut02_income"); next()
            until(TutorialOverlay.S_RANGED); shot(g, "${tag}_tut03_ranged"); tapHole()
            until(TutorialOverlay.S_KILL); shot(g, "${tag}_tut04_kill"); next()
            b.player.xp = Defs.evolveXp[0] * 0.85f
            until(TutorialOverlay.S_EVOLVE); shot(g, "${tag}_tut05_evolve_wait")
            b.player.xp = Defs.evolveXp[0].toFloat()
            repeat(10) { g.frame(c, 1f / 60f) }
            shot(g, "${tag}_tut05_evolve"); tapHole()
            until(TutorialOverlay.S_DOCTRINE); shot(g, "${tag}_tut06_doctrine"); tapHole()
            until(TutorialOverlay.S_TURRET); shot(g, "${tag}_tut07_turret"); tapHole()
            repeat(5) { g.frame(c, 1f / 60f) }
            shot(g, "${tag}_tut07_turret_buy"); tapHole()
            until(TutorialOverlay.S_BARRAGE); shot(g, "${tag}_tut08_barrage"); tapHole()
            shot(g, "${tag}_tut08_barrage_aim"); tapHole()
            assertTrue("$tag: ostrzał samouczka", b.player.barragesUsed > 0)
            until(TutorialOverlay.S_CAMERA); shot(g, "${tag}_tut09_camera"); next()
            until(TutorialOverlay.S_DONE); shot(g, "${tag}_tut10_done"); next()
            repeat(5) { g.frame(c, 1f / 60f) }
            assertTrue("$tag: samouczek zakończony", tut.finished && g.profile.tutorialDone)
            println("$tag tutorial ok: t=${b.time} era=${b.player.era} turrets=${b.player.turretEra.count { it >= 0 }} kills=${b.player.kills}")
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
                    g.setScreen(CodexScreen(g, steam))
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

    /** Przegląd 5 nowych dymków bitwy i pierwszego dymka Badań: 720p, 20:9, Fold. */
    @Test
    fun contextualHints() {
        for ((tag, dims) in mapOf(
            "phone720" to intArrayOf(1600, 720, 2),
            "phone20x9" to intArrayOf(2400, 1080, 3),
            "foldOpen" to intArrayOf(2184, 1968, 3),
            "foldPortrait" to intArrayOf(1968, 2184, 3),
        )) {
            val g = game(dims[0], dims[1], dims[2] * 0.9f,
                if (tag == "phone720") Quality.LOW else Quality.HIGH)
            // Ukryj powitalny baner z nagrodą za logowanie przed kadrami dymków.
            val clear = Canvas(Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888))
            repeat(210) { g.frame(clear, 1f / 60f) }
            g.setScreen(ResearchScreen(g))
            shot(g, tag + "_hint_research", 1)

            val bs = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 560L, 1f))
            g.setScreen(bs)
            val b = field(bs, "battle").get(bs) as Battle
            b.player.gold = 50000f
            val hints = field(bs, "hints").get(bs) as HintOverlay
            val show = hints.javaClass.getDeclaredMethod("activate",
                Int::class.javaPrimitiveType, String::class.java).apply { isAccessible = true }
            fun capture(overlay: HintOverlay, id: Int, key: String, name: String) {
                g.profile.hintsSeen = 0L
                show.invoke(overlay, id, key)
                shot(g, tag + "_hint_" + name, 1)
                overlay.dismiss()
            }
            capture(hints, HintOverlay.UPGRADE, "hint_upgrade", "upgrade")
            b.player.aliveCount = b.player.cap
            capture(hints, HintOverlay.CAP, "hint_cap", "armory")

            val endlessScreen = BattleScreen(g, BattleConfig(Mode.ENDLESS, 561L, 1f))
            g.setScreen(endlessScreen)
            val eh = field(endlessScreen, "hints").get(endlessScreen) as HintOverlay
            val eb = field(endlessScreen, "battle").get(endlessScreen) as Battle
            eb.wave = 4
            eb.waveTimer = 5f
            capture(eh, HintOverlay.CARD, "hint_card", "endless_card")
            eb.wave = 10
            eb.units[0].active = true
            eb.units[0].dying = -1f
            eb.units[0].side = 1
            eb.units[0].bossKind = 1
            eb.units[0].hp = 100000f
            eb.units[0].maxHp = 100000f
            eb.units[0].x = 1800f
            capture(eh, HintOverlay.BOSS, "hint_boss_push", "boss")

            val siegeScreen = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 562L, 1f))
            g.setScreen(siegeScreen)
            val siege = field(siegeScreen, "battle").get(siegeScreen) as Battle
            siege.time = Battle.SIEGE_START + 7f
            val sh = field(siegeScreen, "hints").get(siegeScreen) as HintOverlay
            capture(sh, HintOverlay.SIEGE, "hint_siege", "siege")
        }
    }

    /** Zrzuty ustawień i rankingu serwera: tryb bez zgody, bez żadnych zapytań HTTP. */
    @Test
    fun globalRankingMenus() {
        for ((tag, dims) in mapOf(
            "phone720" to intArrayOf(1600, 720, 2),
            "foldOpen" to intArrayOf(2184, 1968, 3),
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

    /** Wszystkie warunki pogody na czterech formatach ekranu, także noc na LOW. */
    @Test
    fun weatherScreens() {
        for ((tag, dims) in mapOf(
            "phone720" to intArrayOf(1600, 720, 2),
            "phone20x9" to intArrayOf(2400, 1080, 3),
            "foldOpen" to intArrayOf(2184, 1968, 3),
            "foldPortrait" to intArrayOf(1968, 2184, 3),
        )) {
            val g = game(dims[0], dims[1], dims[2] * 0.9f,
                if (tag == "phone720") Quality.LOW else Quality.HIGH)
            if (tag == "phone20x9") g.setInsets(110, 0, 70, 40)
            for (weather in Weather.entries) {
                val era = when (weather) {
                    Weather.CLEAR -> 0
                    Weather.RAIN -> 2
                    Weather.WIND -> 5
                    Weather.NIGHT -> 8
                }
                val bs = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 5L + weather.ordinal, 1f,
                    startEra = era, maxEra = era, weather = weather))
                g.setScreen(bs)
                val battle = field(bs, "battle").get(bs) as Battle
                battle.player.gold = 200000f
                battle.enemy.gold = 200000f
                battle.spawn(battle.player, Defs.unit(era, Role.RANGED))
                battle.spawn(battle.enemy, Defs.unit(era, Role.HEAVY))
                battle.player.turretEra[0] = era
                battle.enemy.turretEra[0] = era
                if (weather == Weather.NIGHT) battle.player.baseFlash = 0.13f
                shot(g, "${tag}_weather_${weather.name.lowercase()}")
            }

            val cs = CampaignScreen(g)
            g.setScreen(cs)
            field(cs, "selected").set(cs, Campaign.stages[4])
            shot(g, "${tag}_weather_stage_info")
        }
    }

    /** Bossowie, podwójna zdolność, zapowiedzi i Kodeks na wszystkich formatach. */
    @Test
    fun endlessBossVariants() {
        for ((tag, dims) in mapOf(
            "phone720" to intArrayOf(1600, 720, 2),
            "phone20x9" to intArrayOf(2400, 1080, 3),
            "foldOpen" to intArrayOf(2184, 1968, 3),
            "foldPortrait" to intArrayOf(1968, 2184, 3),
        )) {
            val g = game(dims[0], dims[1], dims[2] * 0.9f,
                if (tag == "phone720") Quality.LOW else Quality.HIGH)
            for (w in intArrayOf(10, 20, 30, 40, 50, 60)) {
                val screen = BattleScreen(g, BattleConfig(Mode.ENDLESS, 802L + w, 1f))
                g.setScreen(screen)
                val b = field(screen, "battle").get(screen) as Battle
                b.wave = w - 1
                b.nextBossToSpawn = w
                b.waveTimer = 0f
                b.step()
                b.pendingCards?.let { b.chooseEndlessCard(0) }
                val boss = b.currentBoss()!!
                val renderer = field(screen, "r").get(screen) as BattleRenderer
                boss.x = b.player.baseFront + renderer.worldVisible * 0.54f
                renderer.camX = boss.x - renderer.worldVisible * 0.5f
                renderer.clampCam()
                shot(g, "${tag}_endless_boss_${w}")
            }
            val preview = BattleScreen(g, BattleConfig(Mode.ENDLESS, 901L, 1f))
            g.setScreen(preview)
            val battle = field(preview, "battle").get(preview) as Battle
            battle.wave = 9
            battle.nextBossToSpawn = 10
            shot(g, "${tag}_endless_next_boss_kind")
            battle.wave = 10
            battle.nextWaveTheme = EndlessWaveTheme.SIEGE_TRAIN
            shot(g, "${tag}_endless_siege_train_preview")
            battle.nextWaveTheme = EndlessWaveTheme.SKIRMISHERS
            field(preview, "nextThemeShown").setInt(preview, -1)
            shot(g, "${tag}_endless_skirmishers_preview")
            val codex = CodexScreen(g, 0)
            g.setScreen(codex)
            val end = field(codex, "maxScroll").getFloat(codex)
            field(codex, "scroll").setFloat(codex, end * 0.5f)
            shot(g, "${tag}_codex_endless_bosses")
            field(codex, "scroll").setFloat(codex, end)
            shot(g, "${tag}_codex_endless_waves")
        }
    }

    /** Nakładka metryk na tanim telefonie i rozłożonym Foldzie. */
    @Test
    fun performanceOverlay() {
        for ((tag, dims) in mapOf(
            "phone720" to intArrayOf(1600, 720, 2),
            "foldOpen" to intArrayOf(2184, 1968, 3),
        )) {
            val quality = if (tag == "phone720") Quality.LOW else Quality.HIGH
            val g = game(dims[0], dims[1], dims[2] * 0.9f, quality)
            g.profile.showFps = true
            g.profile.quality = quality
            g.setQuality(quality)
            g.setScreen(BattleScreen(g, BattleConfig(Mode.SKIRMISH, 27037L, 1f)))
            shot(g, "${tag}_performance_overlay", 30)
        }
    }

    /** Krótka poprawna powtórka do przeglądu odtwarzacza, bez dostępu do dysku. */
    private val auditReplay by lazy {
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
    private fun visitAuditScreens(g: Game, visit: (String, Screen, String?) -> Unit) {
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
        show("codex", CodexScreen(g, 0), "codex")
        show("codex_variants", CodexScreen(g, 4), "codex")
        show("play_games", PlayGamesScreen(g), "play_games")
    }

    /** Bezpieczny obszar i rozłączne hitboxy; wstęga też zajmuje miejsce. */
    private fun assertNoCollisions(g: Game, screen: Screen, name: String, title: String?) {
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

    /** Cztery formaty, wszystkie języki, tekst 130%; bez generowania plików PNG. */
    @Test
    fun layoutCollisions() {
        val originalLang = L.lang
        try {
            for ((tag, dims) in mapOf(
                "phone720" to intArrayOf(1600, 720, 2),
                "phone20x9" to intArrayOf(2400, 1080, 3),
                "foldOpen" to intArrayOf(2184, 1968, 3),
                "foldPortrait" to intArrayOf(1968, 2184, 3),
            )) {
                val g = game(dims[0], dims[1], dims[2] * 0.9f,
                    if (tag == "phone720") Quality.LOW else Quality.HIGH)
                if (tag == "phone20x9") g.setInsets(110, 0, 70, 40)
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
     * :app:testDebugUnitTest --tests '*ScreenshotTest.layoutAudit*'.
     */
    @Test
    fun layoutAudit() {
        if (!"true".equals(System.getenv("LAYOUT_AUDIT"), ignoreCase = true)) return
        val originalLang = L.lang
        try {
            for ((tag, dims) in mapOf(
                "phone720" to intArrayOf(1600, 720, 2),
                "phone20x9" to intArrayOf(2400, 1080, 3),
                "foldOpen" to intArrayOf(2184, 1968, 3),
                "foldPortrait" to intArrayOf(1968, 2184, 3),
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
}
