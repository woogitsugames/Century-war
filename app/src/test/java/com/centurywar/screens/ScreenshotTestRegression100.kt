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
class ScreenshotTestRegression100 : ScreenshotTestBase() {
    /**
     * Trwały zestaw 15 ekranów w 720p i na rozłożonym Foldzie.
     * Oddzielne nazwy nie kolidują ze zrzutami innych testów.
     */
    @Test
    fun regression100() {
        val originalLanguage = L.lang
        try {
            L.lang = L.PL
            val devices = mapOf(
                "phone720" to intArrayOf(1600, 720, 2),
                "foldOpen" to intArrayOf(2184, 1968, 3),
            )
            val requested = System.getenv("CW_SCREENSHOT_DEVICE")?.trim().orEmpty()
            val selected = if (requested.isEmpty()) devices else devices.filterKeys { it == requested }
            require(selected.isNotEmpty()) { "Nieznane CW_SCREENSHOT_DEVICE=$requested" }
            for ((tag, dims) in selected) {
                val g = game(dims[0], dims[1], dims[2] * 0.9f,
                    if (tag == "phone720") Quality.LOW else Quality.HIGH)
                g.profile.lang = "pl"
                g.onLanguageChanged()
                g.profile.hintsEnabled = false
                g.profile.hintsSeen = Profile.HINT_VALID_MASK
                g.profile.stars[0] = 3
                g.profile.stars[1] = 2
                g.profile.medals = 340
                // Baner nagrody za logowanie nie jest częścią stabilnego wzorca.
                (field(g, "noticeQueue").get(g) as MutableCollection<*>).clear()
                (field(g, "noticeIcons").get(g) as MutableCollection<*>).clear()
                field(g, "achievementSeconds").setFloat(g, 0f)
                // Wspólny zegar tła menu jest statyczny: po innych testach w tej samej JVM
                // chmury i góry byłyby przesunięte względem wzorca.
                MenuBg::class.java.getDeclaredField("time").apply { isAccessible = true }.setFloat(null, 0f)

                fun capture(scene: String, frames: Int = 2) {
                    val name = "${tag}_reg100_$scene"
                    // dt=0 zamraża chmury i animacje niezależnie od System.nanoTime().
                    g.finishScreenTransition()
                    val bitmap = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
                    try {
                        val canvas = Canvas(bitmap)
                        repeat(frames) { g.frame(canvas, 0f) }
                        File(out, "$name.png").outputStream().use {
                            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                        }
                    } finally {
                        bitmap.recycle()
                    }
                    ScreenshotGolden.verify(name)
                }

                g.setScreen(MenuScreen(g)); capture("menu", 8)
                // Zapowiedzi epok jako widziane: inaczej oba zrzuty kampanii pokazują nakładkę.
                for (era in 0 until Defs.ERA_COUNT) g.profile.markEraIntroSeen(era)
                val campaign = CampaignScreen(g)
                g.setScreen(campaign); capture("campaign")
                field(campaign, "selected").set(campaign, Campaign.stages[2])
                capture("campaign_info")
                g.setScreen(ResearchScreen(g)); capture("research")
                g.setScreen(AchievementsScreen(g)); capture("achievements")
                g.setScreen(ModeScreen(g, Mode.ENDLESS)); capture("mode_endless")
                g.setScreen(ModeScreen(g, Mode.SKIRMISH)); capture("mode_skirmish")
                g.setScreen(SkirmishSetupScreen(g)); capture("skirmish_setup")
                g.setScreen(RankingsScreen(g)); capture("rankings")
                g.setScreen(SettingsScreen(g)); capture("settings")
                g.setScreen(AccessibilityScreen(g)); capture("accessibility")
                g.setScreen(AboutScreen(g)); capture("about")
                g.setScreen(CodexScreen(g, 0)); capture("codex")

                val battleScreen = BattleScreen(g, BattleConfig(
                    Mode.SKIRMISH, 100100L, 1f, startEra = 2, maxEra = 7,
                ))
                g.setScreen(battleScreen)
                val battle = field(battleScreen, "battle").get(battleScreen) as Battle
                battle.player.gold = 900f
                capture("battle_hud", 6)
                battleScreen.onClick(field(battleScreen, "pauseBtn").get(battleScreen) as Button)
                capture("battle_pause", 3)
            }
        } finally {
            L.lang = originalLanguage
        }
    }
}
