package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
import com.centurywar.engine.TouchEvent
import com.centurywar.game.Ai
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.Bonuses
import com.centurywar.game.Campaign
import com.centurywar.game.Defs
import com.centurywar.game.Role
import com.centurywar.game.Mode
import com.centurywar.meta.OnlineLeaderboards
import com.centurywar.meta.OnlineState
import com.centurywar.meta.Profile
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

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
        val bmp = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        repeat(frames) { g.frame(c, 1f / 60f) }
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
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

    /** Szybki zestaw: wszystkie ekrany menu i HUD bitwy z otwartymi panelami (bez długiej symulacji). */
    @Test
    fun menus() {
        for ((tag, dims) in mapOf(
            "phone720" to intArrayOf(1600, 720, 2),
            "phone20x9" to intArrayOf(2400, 1080, 3),
            "foldOpen" to intArrayOf(2184, 1968, 3),
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
            g.setScreen(ModeScreen(g, Mode.SKIRMISH)); shot(g, "${tag}_mode_skirmish")
            g.setScreen(RankingsScreen(g)); shot(g, "${tag}_rankings")
            g.setScreen(SettingsScreen(g)); shot(g, "${tag}_settings")
            g.setScreen(CodexScreen(g, 0)); shot(g, "${tag}_codex_era0")
            g.profile.levels["r_unitDmg"] = 5; g.profile.levels["r_unitHp"] = 4; g.profile.levels["r_train"] = 3
            val cx = CodexScreen(g, 6)
            g.setScreen(cx)
            cx.onClick(cx.buttons.first { it.id == -2 })
            shot(g, "${tag}_codex_era6")
            field(cx, "scroll").set(cx, field(cx, "maxScroll").get(cx))
            shot(g, "${tag}_codex_era6_end")
            g.profile.levels.clear()

            val bs = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 7L, 1f, startEra = 2, maxEra = 7))
            g.setScreen(bs)
            val b = field(bs, "battle").get(bs) as com.centurywar.game.Battle
            b.playerAi = Ai(b, 0)
            b.player.gold = 900f
            repeat(60 * 20) { g.frame(Canvas(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)), 1f / 60f) }
            b.playerAi = null
            shot(g, "${tag}_battle_hud")
            field(bs, "showTurrets").set(bs, true)
            shot(g, "${tag}_battle_turrets")
            field(bs, "showTurrets").set(bs, false)
            b.pendingChoice = arrayOf(com.centurywar.game.Doctrine.ECONOMY, com.centurywar.game.Doctrine.FURY, com.centurywar.game.Doctrine.ARSENAL)
            shot(g, "${tag}_battle_doctrine")
            b.pendingChoice = null
            field(bs, "paused").set(bs, true)
            shot(g, "${tag}_battle_pause")
            field(bs, "paused").set(bs, false)
            b.surrender()
            shot(g, "${tag}_battle_results", 90)
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
            until(TutorialOverlay.S_CAMERA); shot(g, "${tag}_tut09_camera"); next()
            until(TutorialOverlay.S_DONE); shot(g, "${tag}_tut10_done"); next()
            repeat(5) { g.frame(c, 1f / 60f) }
            assertTrue("$tag: samouczek zakończony", tut.finished && g.profile.tutorialDone)
            println("$tag tutorial ok: t=${b.time} era=${b.player.era} turrets=${b.player.turretEra.count { it >= 0 }} kills=${b.player.kills}")
        }
    }
}
