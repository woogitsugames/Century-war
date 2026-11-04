package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.game.Ai
import com.centurywar.game.BattleConfig
import com.centurywar.game.BattleMeta
import com.centurywar.game.Bonuses
import com.centurywar.game.Daily
import com.centurywar.game.Mode
import com.centurywar.game.RestoredBattle
import com.centurywar.game.SnapshotStore
import com.centurywar.meta.LostBattle
import com.centurywar.meta.Profile
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.CRC32

/**
 * R10-01: bitwa przerwana aktualizacją gry. „Aktualizację balansu” modelujemy podmianą
 * 4 bajtów odcisku w zrzucie (z poprawionym CRC) na kopii katalogu danych; „start” to nowe
 * Profile/SnapshotStore/Game z tą samą procedurą co MainActivity.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LostBattle188Test {
    @get:Rule val folder = TemporaryFolder()

    private val canvas = Canvas(Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888))
    private val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
    }

    private inner class Run(val dir: File, w: Int = 1600, h: Int = 720, d: Float = 2f) {
        val profile = Profile(File(dir, "profile.json")).also { it.load() }
        val store = SnapshotStore(File(dir, "battle.snap"))
        val restored: RestoredBattle? = store.loadBattle(profile.lastSettledBattle)
        val lost: BattleMeta? = store.lostByUpdate
        val game = Game(host, profile, Quality.LOW).also { g ->
            profile.tutorialDone = true
            profile.hintsEnabled = false
            profile.eraIntrosEnabled = false
            g.snapshots = store
            g.density = d
            g.resize(w, h, w, h)
            g.resumeOffer = restored
            g.lostBattle = lost
        }

        init {
            assertFalse("jeden plik zrzutu: nigdy wznowienie i utrata naraz", restored != null && lost != null)
        }

        fun menu() {
            game.setScreen(MenuScreen(game))
            game.frame(canvas, 1f / 60f)
        }

        fun flush() {
            repeat(3) {
                Profile.awaitHintWrites()
                assertTrue(store.awaitWrites(5_000L))
            }
        }

        fun lostNotices(): List<String> = game.noticeTexts().filter { it.startsWith(L["lost188_title"]) }
    }

    private fun play(run: Run, cfg: BattleConfig, ranked: Boolean, seconds: Float = 90f): BattleScreen {
        val bs = BattleScreen(run.game, cfg, ranked)
        run.game.setScreen(bs)
        run.game.frame(canvas, 1f / 60f)
        val b = bs.battle
        b.playerAi = Ai(b, 0)
        while (!b.over && b.time < seconds) b.step()
        while (!b.over && (b.pendingChoice != null || b.pendingCards != null || b.pendingVariant != null)) b.step()
        assertFalse(b.over)
        bs.onPause()
        run.flush()
        assertTrue(File(run.dir, "battle.snap").exists())
        assertTrue("opis bitwy obok zrzutu", File(run.dir, "battle.meta").exists())
        return bs
    }

    /** Kopia katalogu z „nowym balansem”: inny odcisk we wszystkich zrzutach. */
    private fun updatedImage(from: File, name: String, corrupt: Boolean = false): File {
        val to = folder.newFolder(name)
        from.listFiles()?.filter { it.isFile }?.forEach { it.copyTo(File(to, it.name)) }
        for (f in to.listFiles()!!.filter { it.name.startsWith("battle.snap") }) {
            val b = f.readBytes()
            if (corrupt) { f.writeBytes(b.copyOf(b.size - 9)); continue }
            val bb = ByteBuffer.wrap(b)
            bb.putInt(16, bb.getInt(16) xor 0x00C0FFEE)
            bb.putInt(12, CRC32().apply { update(b, 16, b.size - 16) }.value.toInt())
            f.writeBytes(b)
        }
        return to
    }

    private fun diskProfile(dir: File) = JSONObject(File(dir, "profile.json").readText())

    @Test
    fun rankedDailyAttemptComesBackOnce() {
        val run = Run(folder.newFolder("daily"))
        val today = Daily.dayNumber()
        // Tak jak ModeScreen: próba dnia zużyta już przy starcie.
        run.profile.lastRankedDaily = today
        run.profile.save()
        play(run, Daily.config(today, Bonuses()), ranked = true)
        val medals = run.profile.medals
        val dir = updatedImage(run.dir, "daily-updated")

        val second = Run(dir)
        assertNull("po zmianie balansu nie ma wznowienia", second.restored)
        assertNotNull(second.lost)
        assertTrue(second.lost!!.rankedDaily)
        second.menu()
        assertEquals(listOf(L["lost188_title"] + ". " + L.f("lost188_body", L["daily"])), second.lostNotices())
        assertTrue(second.game.noticeTexts().contains(L["lost188_daily"]))
        assertEquals(-1L, second.profile.lastRankedDaily)
        assertEquals(medals, second.profile.medals)
        second.flush()
        assertEquals(-1L, diskProfile(dir).getLong("lastRankedDaily"))
        assertFalse(File(dir, "battle.snap").exists())
        assertFalse(File(dir, "battle.meta").exists())

        val third = Run(dir)
        assertNull(third.restored); assertNull(third.lost)
        third.menu()
        assertTrue(third.lostNotices().isEmpty())
        assertEquals(medals, third.profile.medals)
        assertEquals(-1L, third.profile.lastRankedDaily)
    }

    @Test
    fun endlessPaysConsolationOnce() {
        val run = Run(folder.newFolder("endless"))
        val bs = play(run, BattleConfig(Mode.ENDLESS, 12L, 1f), ranked = false, seconds = 150f)
        val expected = LostBattle.lossReward(bs.cfg, bs.battle, false)
        assertTrue(expected > 0)
        val medals = run.profile.medals
        val dir = updatedImage(run.dir, "endless-updated")

        val second = Run(dir)
        assertEquals(expected, second.lost!!.consolation)
        second.menu()
        assertEquals(1, second.lostNotices().size)
        assertTrue(second.game.noticeTexts().contains(L.f("lost188_medals", L.plural("medals_n", expected.toLong()))))
        assertEquals(medals + expected, second.profile.medals)
        second.flush()
        assertEquals(medals + expected, diskProfile(dir).getInt("medals"))
        assertFalse(File(dir, "battle.snap").exists())

        val third = Run(dir)
        assertNull(third.lost)
        third.menu()
        assertTrue(third.lostNotices().isEmpty())
        assertEquals(medals + expected, third.profile.medals)
    }

    @Test
    fun killedAfterProfileSaveBeforeDeleteDoesNotPayTwice() {
        val run = Run(folder.newFolder("kill"))
        play(run, BattleConfig(Mode.ENDLESS, 13L, 1f), ranked = false, seconds = 150f)
        val medals = run.profile.medals
        val dir = updatedImage(run.dir, "kill-updated")
        val second = Run(dir)
        val id = second.lost!!.battleId
        val crash = folder.newFolder("kill-image")
        val captured = AtomicBoolean(false)
        second.profile.onSaved = {
            val f = File(dir, "profile.json")
            if (!captured.get() && JSONObject(f.readText()).optLong("lastSettledBattle") == id) {
                dir.listFiles()?.filter { it.isFile }?.forEach { it.copyTo(File(crash, it.name), true) }
                captured.set(true)
            }
        }
        second.menu()
        second.flush()
        second.profile.onSaved = null
        assertTrue(captured.get())
        assertTrue("obraz awarii ma jeszcze zrzut", File(crash, "battle.snap").exists())
        val paid = diskProfile(crash).getInt("medals")
        assertTrue(paid > medals)

        val third = Run(crash)
        assertNull(third.lost); assertNull(third.restored)
        third.menu()
        assertTrue(third.lostNotices().isEmpty())
        assertEquals(paid, third.profile.medals)
        third.flush()
        assertFalse("rozliczony zrzut sprząta start", File(crash, "battle.snap").exists())
        assertFalse(File(crash, "battle.meta").exists())
    }

    @Test
    fun corruptSnapshotGivesNothing() {
        val run = Run(folder.newFolder("corrupt"))
        run.profile.lastRankedDaily = Daily.dayNumber()
        run.profile.save()
        play(run, Daily.config(Daily.dayNumber(), Bonuses()), ranked = true)
        val medals = run.profile.medals
        val dir = updatedImage(run.dir, "corrupt-image", corrupt = true)
        val second = Run(dir)
        assertNull(second.restored); assertNull(second.lost)
        second.menu()
        assertTrue(second.lostNotices().isEmpty())
        assertEquals(medals, second.profile.medals)
        assertEquals(Daily.dayNumber(), second.profile.lastRankedDaily)
    }

    @Test
    fun compatibleSnapshotStillOffersResume() {
        val run = Run(folder.newFolder("compatible"))
        val bs = play(run, BattleConfig(Mode.ENDLESS, 14L, 1f), ranked = false)
        val again = Run(run.dir)
        assertEquals(bs.battleId, again.restored?.battleId)
        assertNull(again.lost)
    }
}
