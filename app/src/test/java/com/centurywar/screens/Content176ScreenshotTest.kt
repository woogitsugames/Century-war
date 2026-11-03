package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Screen
import com.centurywar.meta.AchievementMetric
import com.centurywar.meta.Achievements
import com.centurywar.meta.MissionItem
import com.centurywar.meta.MissionType
import com.centurywar.meta.Missions
import com.centurywar.meta.Profile
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Zrzuty nowości 176: osiągnięcia i misje dowódcy oraz stopni wież, Kodeks i „Co nowego”. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class Content176ScreenshotTest {
    private val out = File("build/screenshots").apply { mkdirs() }
    private val devices = linkedMapOf(
        "phone720" to floatArrayOf(1600f, 720f, 2f),
        "foldOuter" to floatArrayOf(904f, 2316f, 2.7f),
    )

    private fun game(w: Int, h: Int, density: Float, lang: String): Game {
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val f = File.createTempFile("profile176", ".json").apply { delete() }
        val profile = Profile(f).apply {
            this.lang = lang; tutorialDone = true; medals = 340
            for (i in 0..Achievements.LATE_STAGE_INDEX) stars[i] = 2
            unlockedAchievements.add("commander_first")
            achievementCounters[AchievementMetric.COMMANDER_SUMMONED.name] = 1L
            achievementCounters[AchievementMetric.COMMANDER_PULSES.name] = 18L
            achievementCounters[AchievementMetric.COMMANDER_WINS.name] = 4L
        }
        return Game(host, profile, 1).also { it.density = density; it.resize(w, h, w, h) }
    }

    private fun shot(g: Game, name: String) {
        g.finishScreenTransition()
        val tiny = Canvas(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))
        repeat(2) { g.frame(tiny, 1f / 60f) }
        val bmp = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
        g.frame(Canvas(bmp), 1f / 60f)
        File(out, "$name.png").outputStream().use { check(bmp.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bmp.recycle()
    }

    private fun scrollToEnd(screen: Screen) {
        val max = screen.javaClass.getDeclaredField("maxScroll")
            .apply { isAccessible = true }.getFloat(screen)
        screen.javaClass.getDeclaredField("scroll").apply { isAccessible = true }.setFloat(screen, max)
    }

    @Test fun newAchievementsMissionsCodexAndWhatsNew() {
        val old = L.lang
        try {
            val langs = System.getenv("CW_TEXT_LANGS")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?.toTypedArray() ?: arrayOf("pl", "de", "tr")
            for ((tag, d) in devices) for (lang in langs) {
                val g = game(d[0].toInt(), d[1].toInt(), d[2], lang)
                val now = System.currentTimeMillis()
                val state = Missions.refresh(g.profile, now)
                val day = Missions.utcDay(now)
                val week = Missions.utcWeek(now)
                state.daily[0] = MissionItem("d_${day}_COMMANDER_SUMMONS", MissionType.COMMANDER_SUMMONS, 1, 14, progress = 1)
                state.daily[1] = MissionItem("d_${day}_TURRET_TIERS", MissionType.TURRET_TIERS, 2, 12, progress = 1)
                state.daily[2] = MissionItem("d_${day}_TOP_TIER_WINS", MissionType.TOP_TIER_WINS, 1, 16)
                state.weekly[0] = MissionItem("w_${week}_COMMANDER_WINS", MissionType.COMMANDER_WINS, 4, 50, progress = 2)

                val missions = MissionsScreen(g)
                g.setScreen(missions)
                shot(g, "content176_${tag}_${lang}_missions")

                val ach = AchievementsScreen(g)
                g.setScreen(ach)
                g.finishScreenTransition()
                scrollToEnd(ach)
                shot(g, "content176_${tag}_${lang}_achievements")

                val codex = CodexScreen(g, 3)
                g.setScreen(codex)
                g.finishScreenTransition()
                codex.showCategory(3)
                scrollToEnd(codex)
                shot(g, "content176_${tag}_${lang}_codex_towers")
                codex.showCategory(4)
                scrollToEnd(codex)
                shot(g, "content176_${tag}_${lang}_codex_abilities")

                g.setScreen(WhatsNewScreen(g))
                shot(g, "content176_${tag}_${lang}_whats_new")
            }
            assertTrue(File(out, "content176_phone720_pl_missions.png").isFile)
        } finally {
            L.lang = old
        }
    }
}
