package com.centurywar.meta

import android.graphics.Bitmap
import android.graphics.Canvas
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.BattleController
import com.centurywar.game.Mode
import com.centurywar.game.ReplayRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReplayCleanupTest {
    @get:Rule val temp = TemporaryFolder()

    private fun recording(date: Long): ByteArray {
        val cfg = BattleConfig(Mode.SKIRMISH, 9L, 1f)
        val battle = Battle(cfg)
        val controller = BattleController(battle)
        controller.surrender()
        return ReplayRecord.encode(cfg, controller.log(), ReplayRecord.Meta(
            cfg.mode, -1, battle.winner, battle.score(), battle.time, battle.wave, date, "Tester"))
    }

    @Test fun pruningPinnedFilesClearsOnlyBrokenLinksOnGameThread() {
        val file = temp.newFile("profile.json")
        val profile = Profile(file).apply { name = "Tester" }
        profile.submit(Profile.BOARD_ENDLESS, 1000, 1, "p0")
        profile.submit(Profile.BOARD_SKIRMISH, 900, 1, "p18")
        profile.submit("recent", 800, 1, "normal_24")
        profile.submit("without_file", 700, 1)

        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val game = Game(host, profile, Quality.LOW)
        val store = ReplayStore(temp.newFolder("replays"))
        var removedCount = 0
        store.onRemoved = { ids ->
            game.post {
                removedCount += ids.size
                if (profile.clearReplayReferences(ids)) profile.saveAsync()
            }
        }

        repeat(25) {
            store.saveAsync("p$it", recording(it.toLong()))
            store.pinAsync("p$it")
        }
        repeat(25) { store.saveAsync("normal_$it", recording((100 + it).toLong())) }

        var listed: List<ReplayStore.Entry> = emptyList()
        val done = CountDownLatch(1)
        store.listAsync { listed = it; done.countDown() }
        assertTrue("Kolejka I/O musi ukończyć retencję", done.await(30, TimeUnit.SECONDS))
        assertFalse(listed.any { it.id == "p0" })
        assertTrue(listed.any { it.id == "p18" && it.pinned })
        assertTrue(listed.any { it.id == "normal_24" })
        // Callback z I/O nie może zmienić profilu przed obsługą Game.post.
        assertEquals("p0", profile.top(Profile.BOARD_ENDLESS).first().replay)
        game.frame(Canvas(Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)), 0f)
        Profile.awaitHintWrites()

        assertTrue(removedCount > 0)
        assertNull(profile.top(Profile.BOARD_ENDLESS).first().replay)
        assertEquals("p18", profile.top(Profile.BOARD_SKIRMISH).first().replay)
        assertEquals("normal_24", profile.top("recent").first().replay)
        assertEquals(1000L, profile.top(Profile.BOARD_ENDLESS).first().score)

        val loaded = Profile(file).also { it.load() }
        assertNull(loaded.top(Profile.BOARD_ENDLESS).first().replay)
        assertEquals(1000L, loaded.top(Profile.BOARD_ENDLESS).first().score)
        assertEquals("p18", loaded.top(Profile.BOARD_SKIRMISH).first().replay)
    }
}
