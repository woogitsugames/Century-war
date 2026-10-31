package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.StrictMode
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
import com.centurywar.engine.Screen
import com.centurywar.game.BattleConfig
import com.centurywar.game.GameIoCounter
import com.centurywar.game.Mode
import com.centurywar.game.SnapshotStore
import com.centurywar.meta.BattleHistory
import com.centurywar.meta.Profile
import com.centurywar.meta.ReplayStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicReference

/** Przejście przez podstawowe ekrany z polityką StrictMode w wątku gry. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class StabilityPathsTest {
    @Test
    fun gamePathsPerformNoDiskIoOnRenderingThread() {
        val dir = Files.createTempDirectory("cw-stability").toFile()
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val g = Game(host, Profile(dir.resolve("profile.json")), Quality.LOW)
        g.replays = ReplayStore(dir.resolve("replays"))
        g.snapshots = SnapshotStore(dir.resolve("battle.snap"))
        g.history = BattleHistory(dir.resolve("history.json"))
        g.historyLoaded = true

        GameIoCounter.resetForTest()
        val error = AtomicReference<Throwable?>()
        val worker = Thread({
            try {
                StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build())
                g.density = 1f
                g.resize(1600, 720, 1600, 720)
                val bitmap = Bitmap.createBitmap(1600, 720, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                val screens: List<Screen> = listOf(
                    MenuScreen(g),
                    BattleScreen(g, BattleConfig(Mode.SKIRMISH, 49L, 1f)),
                    StatsScreen(g),
                    SettingsScreen(g),
                    RankingsScreen(g),
                    GlobalRankingsScreen(g),
                    ReplayListScreen(g),
                    ReplayScreen(g, "missing"),
                )
                for (screen in screens) {
                    g.setScreen(screen)
                    repeat(2) { g.frame(canvas, 1f / 60f) }
                }
                g.current?.onPause()
                bitmap.recycle()
            } catch (t: Throwable) {
                error.set(t)
            }
        }, "game-loop")
        worker.start()
        worker.join(30_000)
        assertFalse("Wątek gry nie zakończył testu", worker.isAlive)
        assertNull("Wyjątek przy przejściu ekranów: " + error.get(), error.get())
        assertEquals("Dostęp do dysku z wątku gry", 0, GameIoCounter.diskViolations)
    }
}
