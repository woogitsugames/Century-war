package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.game.BattleConfig
import com.centurywar.game.Mode
import com.centurywar.meta.Profile
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Zlecenie 136: nowe ekrany, pięć długich języków, telefon i zewnętrzny Fold. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class Localization136ScreenshotTest {
    private val output = File("build/screenshots").apply { mkdirs() }

    private val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
    }

    private fun game(width: Int, height: Int, density: Float, language: String): Game {
        val profileFile = File.createTempFile("cw-l10n136", ".json").apply { delete() }
        val profile = Profile(profileFile).apply {
            lang = language
            tutorialDone = true
            hintsEnabled = false
            hintsSeen = Profile.HINT_VALID_MASK
            medals = 600
        }
        return Game(host, profile, Quality.LOW).apply {
            this.density = density
            resize(width, height, width, height)
            onLanguageChanged()
            applyAccessibility()
        }
    }

    private fun shot(g: Game, device: String, language: String, screen: String) {
        g.finishScreenTransition()
        val bitmap = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            repeat(3) { g.frame(canvas, 0f) }
            val file = File(output, "l10n136_${device}_${language}_${screen}.png")
            file.outputStream().use {
                assertTrue("Nie można zapisać zrzutu: ${file.name}",
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            assertTrue("Pusty zrzut: ${file.name}", file.length() > 1000L)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun newScreensOnPortraitPhoneAndFoldCoverInLongLanguages() {
        val originalLanguage = L.lang
        try {
            val devices = linkedMapOf(
                "phone720x1280" to intArrayOf(720, 1280, 2),
                "fold904x2316" to intArrayOf(904, 2316, 3),
            )
            for ((device, size) in devices) {
                for (language in arrayOf("de", "uk", "pt", "fr", "tr")) {
                    val g = game(size[0], size[1], size[2] * 0.9f, language)

                    g.setScreen(BannerScreen(g))
                    shot(g, device, language, "banners_locked")
                    g.profile.stars.fill(3)
                    g.setScreen(BannerScreen(g))
                    shot(g, device, language, "banners_unlocked")

                    g.setScreen(CampaignScreen(g))
                    shot(g, device, language, "campaign_difficulty")

                    val codex = CodexScreen(g, 2)
                    g.setScreen(codex)
                    codex.showCategory(10)
                    shot(g, device, language, "codex_compare")

                    g.setScreen(ResearchScreen(g))
                    shot(g, device, language, "research_elite")

                    g.setScreen(WhatsNewScreen(g))
                    shot(g, device, language, "whats_new")

                    g.setScreen(PlayGamesScreen(g))
                    shot(g, device, language, "play_games")

                    g.setScreen(ModeScreen(g, Mode.DAILY))
                    shot(g, device, language, "daily_challenge")

                    val battle = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 136L, 1f))
                    battle.battle.player.gold = 10000f
                    g.setScreen(battle)
                    shot(g, device, language, "commander_turrets")
                }
            }
        } finally {
            L.lang = originalLanguage
        }
    }
}
