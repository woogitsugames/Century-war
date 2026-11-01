package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.Mode
import com.centurywar.meta.BattleHistory
import com.centurywar.meta.Profile
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Zlecenie 120: zrzuty pięciu długich języków bez zmiany układu ekranów. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class Localization120ScreenshotTest {
    private val output = File("build/screenshots").apply { mkdirs() }

    private fun game(w: Int, h: Int, density: Float): Game {
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val profileFile = File.createTempFile("cw-l10n120", ".json").apply { delete() }
        val profile = Profile(profileFile).apply {
            tutorialDone = true
            hintsEnabled = false
            hintsSeen = Profile.HINT_VALID_MASK
            medals = 600
        }
        val game = Game(host, profile, Quality.LOW)
        game.density = density
        game.resize(w, h, w, h)
        return game
    }

    private fun field(obj: Any, key: String) =
        obj.javaClass.getDeclaredField(key).apply { isAccessible = true }

    private fun clearNotices(g: Game) {
        (field(g, "noticeQueue").get(g) as MutableCollection<*>).clear()
        (field(g, "noticeIcons").get(g) as MutableCollection<*>).clear()
        field(g, "achievementSeconds").setFloat(g, 0f)
    }

    private fun shot(g: Game, tag: String, language: String, screen: String, frames: Int = 2) {
        g.finishScreenTransition()
        val bitmap = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            g.frame(canvas, 0f)
            clearNotices(g)
            repeat(frames) { g.frame(canvas, if (frames > 10) 1f / 60f else 0f) }
            val file = File(output, "l10n120_" + tag + "_" + language + "_" + screen + ".png")
            file.outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            assertTrue("Pusty zrzut: " + file.name, file.length() > 1000L)
        } finally {
            bitmap.recycle()
        }
    }

    private fun assertLeftHandedLabel(g: Game, screen: AccessibilityScreen, tag: String) {
        val toggle = screen.buttons.first { it.id == 43 }
        val panel = field(screen, "panel").get(screen) as RectF
        val available = toggle.r.left - (panel.left + 20f * g.ui.dp) - 10f * g.ui.dp
        val label = L["access_left_handed_hud"]
        val size = g.ui.fit(label, 15f, available)
        assertTrue(tag + ": etykieta dla leworęcznych poza wierszem",
            g.ui.measure(label, size) <= available + 1.5f * g.ui.dp)
    }

    private fun assertBackupLabelsFit(g: Game, screen: SettingsScreen, tag: String) {
        val panel = field(screen, "panel").get(screen) as RectF
        for ((id, key) in listOf(
            21 to "profile_backup_export",
            22 to "profile_backup_import",
            23 to "profile_backup_restore_prev",
        )) {
            val button = screen.buttons.first { it.id == id }
            assertTrue("$tag/$key: przycisk poza obszarem przewijania", button.visible)
            val dp = g.ui.dp
            // Równanie z SettingsScreen.render(): ikona (24 dp) i odstęp przed przyciskiem.
            val textX = panel.left + 12f * dp + 20f * dp +
                minOf(24f * dp, button.r.height() * 0.7f)
            val available = button.r.left - textX - 6f * dp
            val value = L[key]
            val size = g.ui.fit(value, 15f, available)
            assertTrue("$tag/$key: tekst wykracza poza etykietę: $value",
                g.ui.measure(value, size) <= available + 1.5f * dp)
        }
    }

    @Test
    fun reviewedScreensOnPortraitPhoneAndOuterFold() {
        val previousLanguage = L.lang
        try {
            val devices = linkedMapOf(
                "phone720x1280" to intArrayOf(720, 1280, 2),
                "fold904x2316" to intArrayOf(904, 2316, 3),
            )
            for ((tag, dims) in devices) for (language in arrayOf("de", "uk", "pt", "fr", "tr")) {
                val g = game(dims[0], dims[1], dims[2] * 0.9f)
                g.profile.lang = language
                g.onLanguageChanged()
                g.applyAccessibility()

                val access = AccessibilityScreen(g)
                g.setScreen(access)
                shot(g, tag, language, "accessibility")
                access.onDrag(0f, -100000f)
                shot(g, tag, language, "accessibility_bottom")
                assertTrue(tag + "/" + language + ": przełącznik poza widokiem",
                    access.buttons.first { it.id == 43 }.visible)
                assertLeftHandedLabel(g, access, tag + "/" + language)

                val settings = SettingsScreen(g)
                g.setScreen(settings)
                shot(g, tag, language, "settings")
                settings.onClick(settings.buttons.first { it.id == 20 })
                settings.onDrag(0f, -100000f)
                shot(g, tag, language, "settings_backup")
                assertBackupLabelsFit(g, settings, tag + "/" + language)

                g.setScreen(PlayGamesScreen(g))
                shot(g, tag, language, "play_games")

                g.setScreen(WhatsNewScreen(g))
                shot(g, tag, language, "whats_new")

                val archive = BattleHistory(
                    File.createTempFile("cw-l10n120-history", ".json").apply { delete() })
                try {
                    val now = System.currentTimeMillis()
                    for (i in 29 downTo 0) {
                        val mode = Mode.entries[i % 4]
                        val won = i % 3 == 0
                        val duration = 120 + i * 15
                        archive.add(BattleHistory.Record(
                            date = now - (i / 3).toLong() * 86_400_000L,
                            mode = mode.ordinal,
                            result = if (won) BattleHistory.WIN else BattleHistory.LOSS,
                            score = 1000L + i * 150L,
                            seconds = duration,
                            wave = if (mode == Mode.ENDLESS) 40 - i else 0,
                        ))
                        g.profile.recordBattle(mode, won, duration)
                    }
                    g.history = archive
                    g.historyLoaded = true
                    val stats = StatsScreen(g)
                    g.setScreen(stats)
                    shot(g, tag, language, "stats")
                    val maxScroll = field(stats, "maxScroll").getFloat(stats)
                    field(stats, "scroll").setFloat(stats,
                        minOf(720f * g.ui.dp, maxScroll))
                    shot(g, tag, language, "stats_charts")
                } finally {
                    archive.awaitWrites()
                    archive.close()
                    g.history = null
                }

                g.setScreen(ModeScreen(g, Mode.DAILY))
                shot(g, tag, language, "daily_challenge")

                val battleScreen = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 120120L, 1f))
                g.setScreen(battleScreen)
                shot(g, tag, language, "battle_hud")
                val battle = field(battleScreen, "battle").get(battleScreen) as Battle
                battle.surrender()
                shot(g, tag, language, "battle_result", 90)
            }
        } finally {
            L.lang = previousLanguage
        }
    }
}
