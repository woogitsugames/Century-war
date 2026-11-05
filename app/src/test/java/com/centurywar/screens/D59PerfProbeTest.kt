package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
import com.centurywar.engine.Screen
import com.centurywar.game.Ai
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.Bonuses
import com.centurywar.game.Campaign
import com.centurywar.game.Mode
import com.centurywar.meta.Profile
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale

/** Sonda d59 (tymczasowa): pomiary z opcjonalnym zapisem JFR. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class D59PerfProbeTest {
    private val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
    }

    private val bean = Class.forName("java.lang.management.ManagementFactory")
        .getMethod("getThreadMXBean").invoke(null)!!
    private val api = Class.forName("com.sun.management.ThreadMXBean")
    private val getter = api.getMethod("getThreadAllocatedBytes", java.lang.Long.TYPE)
    init { api.getMethod("setThreadAllocatedMemoryEnabled", java.lang.Boolean.TYPE).invoke(bean, true) }
    private fun bytes(): Long = getter.invoke(bean, Thread.currentThread().id) as Long

    private fun game(level: Int, stars: Int = 36): Game {
        val file = File.createTempFile("cw-d59-", ".json").apply { delete() }
        val profile = Profile(file).apply {
            tutorialDone = true
            hintsEnabled = false
            hintsSeen = Profile.HINT_VALID_MASK
            for (i in 0 until minOf(stars, this.stars.size)) this.stars[i] = 3
        }
        return Game(host, profile, level).apply {
            density = 2f
            bitmapCacheMemoryClassMb = 64
            resize(1600, 720, 1600, 720)
            finishScreenTransition()
        }
    }

    private fun jfr(label: String, body: () -> Unit) {
        val dir = System.getenv("D59_JFR")
        if (dir == null) { body(); return }
        val cfgCls = Class.forName("jdk.jfr.Configuration")
        val config = cfgCls.getMethod("getConfiguration", String::class.java).invoke(null, "profile")
        val recCls = Class.forName("jdk.jfr.Recording")
        val rec = recCls.getConstructor(cfgCls).newInstance(config)
        recCls.getMethod("start").invoke(rec)
        try { body() } finally {
            recCls.getMethod("stop").invoke(rec)
            recCls.getMethod("dump", java.nio.file.Path::class.java)
                .invoke(rec, File(dir, "$label.jfr").toPath())
            recCls.getMethod("close").invoke(rec)
        }
    }

    private fun measure(label: String, warm: Int = 600, frames: Int = 1200, frame: () -> Unit) {
        repeat(warm) { frame() }
        val durations = LongArray(frames)
        var alloc = 0L
        jfr(label) {
            println("D59WIN start=" + System.currentTimeMillis())
            val before = bytes()
            for (i in 0 until frames) {
                val s = System.nanoTime()
                frame()
                durations[i] = System.nanoTime() - s
            }
            alloc = bytes() - before
            println("D59WIN end=" + System.currentTimeMillis())
        }
        val total = durations.sum()
        durations.sort()
        println(String.format(Locale.ROOT, "D59 %-34s avg=%7.3f ms p95=%7.3f ms alloc=%9d B/%d kl (%6.0f B/kl)",
            label, total / frames / 1e6, durations[frames * 95 / 100] / 1e6, alloc, frames,
            alloc.toDouble() / frames))
    }

    private fun battle(label: String, level: Int, cfg: BattleConfig, setup: (Battle) -> Unit = {}) {
        val g = game(level)
        val bmp = Bitmap.createBitmap(1600, 720, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        var s = BattleScreen(g, cfg)
        fun fresh() {
            s = BattleScreen(g, cfg)
            g.setScreen(s); g.finishScreenTransition()
            s.battle.playerAi = Ai(s.battle, 0)
            setup(s.battle)
        }
        fresh()
        var restarts = 0
        measure(label) {
            if (s.battle.over) { restarts++; fresh() }
            s.update(Battle.STEP); s.render(c)
        }
        println("D59 $label: jednostki=${s.battle.units.count { it.alive }} restarty=$restarts era=${s.battle.player.era}/${s.battle.enemy.era}")
        bmp.recycle()
    }

    @Test fun campaign34Low() = battle("kampania-34 LOW", Quality.LOW,
        Campaign.config(Campaign.stages[33], Bonuses(), 5934L))
    @Test fun campaign36Low() = battle("kampania-36 LOW", Quality.LOW,
        Campaign.config(Campaign.stages[35], Bonuses(), 5936L))
    @Test fun campaign36High() = battle("kampania-36 HIGH", Quality.HIGH,
        Campaign.config(Campaign.stages[35], Bonuses(), 5936L))
    @Test fun endless52Low() = battle("endless-52 LOW", Quality.LOW,
        BattleConfig(Mode.ENDLESS, 5952L, 1f, startEra = 8, maxEra = 8)) { it.wave = 52 }
    @Test fun endless52High() = battle("endless-52 HIGH", Quality.HIGH,
        BattleConfig(Mode.ENDLESS, 5952L, 1f, startEra = 8, maxEra = 8)) { it.wave = 52 }

    private fun screen(label: String, level: Int, stars: Int, make: (Game) -> Screen, frame: (Screen) -> Unit) {
        val g = game(level, stars)
        val bmp = Bitmap.createBitmap(1600, 720, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val s = make(g)
        g.setScreen(s); g.finishScreenTransition()
        measure(label, 300, 600) { frame(s); s.update(1f / 60f); s.render(c) }
        bmp.recycle()
    }

    @Test fun campaignMapSparksIdle() = screen("mapa-iskry bezczynna HIGH", Quality.HIGH, 20,
        { CampaignScreen(it) }) {}
    @Test fun campaignMapSparksScroll() = screen("mapa-iskry przewijana HIGH", Quality.HIGH, 20,
        { CampaignScreen(it) }) { var k = 0; it.onDrag(-6f, -6f) }
    @Test fun codexIdle() = screen("kodeks HIGH", Quality.HIGH, 36, { CodexScreen(it) }) {}
    @Test fun statsIdle() = screen("rekordy-statystyki HIGH", Quality.HIGH, 36, { StatsScreen(it) }) {}
    @Test fun rankingsIdle() = screen("rekordy-rankingi HIGH", Quality.HIGH, 36, { RankingsScreen(it) }) {}
    @Test fun achievementsIdle() = screen("rekordy-osiagniecia HIGH", Quality.HIGH, 36, { AchievementsScreen(it) }) {}
    @Test fun replaysIdle() = screen("rekordy-powtorki HIGH", Quality.HIGH, 36, { ReplayListScreen(it) }) {}
    @Test fun menuIdle() = screen("menu HIGH", Quality.HIGH, 36, { MenuScreen(it) }) {}
}
