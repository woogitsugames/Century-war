package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.game.BattleMeta
import com.centurywar.game.Daily
import com.centurywar.game.Mode
import com.centurywar.game.SaveGuard
import com.centurywar.meta.LostBattle
import com.centurywar.meta.Profile
import com.centurywar.meta.WelcomeBack
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * R10-07: powitanie po przerwie w menu. Zegar gry jest wstrzykiwany w konstruktorze
 * ([Game.clock]) i stały, więc seria logowań, rozliczenie bitwy i powitanie liczą ten sam
 * dzień niezależnie od północy UTC i daty przebiegu. „Start gry” to nowe Profile/Game na
 * tym samym katalogu, jak w MainActivity.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class WelcomeBack193Test {
    @get:Rule val folder = TemporaryFolder()

    private val canvas = Canvas(Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888))
    private val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
    }
    private val day = 86_400_000L
    /** Stały „teraz”: 4.11.2026, 12:00 UTC (dzień 20761), z dala od północy UTC. */
    private val now = 1_793_793_600_000L
    private val today = Daily.dayNumber(now)
    private var savedLang = 0

    @Before fun setUp() {
        savedLang = L.lang
        // Ostrzeżenie o zapisie jest globalne na proces: inna klasa testów w tym samym
        // forku mogła je zgłosić, a menu pokazałoby wtedy baner save154_problem.
        SaveGuard.resetWarningForTests()
    }
    @After fun tearDown() {
        L.lang = savedLang
        SaveGuard.resetWarningForTests()
    }

    /** Profil, którego ostatnia wizyta w menu była [daysAgo] dni temu (zapisany na dysku). */
    private fun seed(dir: File, daysAgo: Long, tutorial: Boolean = true, medals: Int = 40) {
        val p = Profile(File(dir, "profile.json"))
        p.tutorialDone = tutorial
        p.hintsEnabled = false
        p.eraIntrosEnabled = false
        p.stars[0] = 3
        p.medals = medals
        p.claimWelcomeBack(today - daysAgo, WelcomeBack.MIN_GAP_DAYS, WelcomeBack.BONUS)
        assertEquals(today - daysAgo, p.lastPlayDay)
        p.save()
        assertTrue(p.awaitWrites(5_000L))
    }

    private inner class Run(val dir: File, w: Int = 1600, h: Int = 720, d: Float = 2f,
                            lang: String = "pl", lost: BattleMeta? = null) {
        val profile = Profile(File(dir, "profile.json")).also { it.load(); it.lang = lang }
        val game = Game(host, profile, Quality.LOW, clock = { now }).also { g ->
            g.density = d
            g.resize(w, h, w, h)
            g.lostBattle = lost
            // Seria logowań z konstruktora Game nie należy do powitania.
            g.clearNotices()
        }
        val medalsAtStart = profile.medals

        fun menu() {
            game.setScreen(MenuScreen(game))
            game.frame(canvas, 1f / 60f)
        }

        fun flush() = assertTrue(profile.awaitWrites(5_000L))
    }

    @Test
    fun welcomeOnceAfterThreeDaysWithBonusAndNews() {
        val dir = folder.newFolder()
        seed(dir, 3)
        val run = Run(dir)
        run.menu()
        val texts = run.game.noticeTexts()
        assertEquals(run.medalsAtStart + WelcomeBack.BONUS, run.profile.medals)
        assertEquals(today, run.profile.lastPlayDay)
        assertEquals(today, run.profile.welcomeBackDay)
        assertTrue(texts.toString(), texts.first().startsWith("Witaj z powrotem! Premia za powrót: 30 medali"))
        // Dzisiejsze wyzwanie dnia i następny cel.
        assertTrue(texts.toString(), texts.any { it.startsWith("Dziś w wyzwaniu dnia: ") })
        assertTrue(texts.toString(), texts.size >= 3)
        // Kolejne klatki i ponowne wejście do menu tego samego dnia: nic więcej.
        run.game.clearNotices()
        run.menu(); run.game.frame(canvas, 1f / 60f)
        assertTrue(run.game.noticeTexts().isEmpty())
        assertEquals(run.medalsAtStart + WelcomeBack.BONUS, run.profile.medals)
        run.flush()

        // Ponowny start (także po zabiciu procesu po zapisie): premia już na dysku, bez drugiej.
        val again = Run(dir)
        again.menu()
        assertTrue(again.game.noticeTexts().none { it.startsWith("Witaj") })
        assertEquals(run.profile.medals, again.profile.medals)
    }

    @Test
    fun noWelcomeAfterTwoDaysOrBeforeTutorial() {
        val dir = folder.newFolder()
        seed(dir, 2)
        val run = Run(dir)
        run.menu()
        assertTrue(run.game.noticeTexts().isEmpty())
        assertEquals(run.medalsAtStart, run.profile.medals)
        assertEquals(today, run.profile.lastPlayDay)

        val before = folder.newFolder()
        seed(before, 30, tutorial = false)
        val novice = Run(before)
        novice.menu()
        assertTrue(novice.game.noticeTexts().isEmpty())
        assertEquals(novice.medalsAtStart, novice.profile.medals)
        // Dzień wizyty zapisany: ukończenie samouczka jutro nie da premii za „przerwę”.
        assertEquals(today, novice.profile.lastPlayDay)
        assertEquals(-1L, novice.profile.welcomeBackDay)
    }

    /** Proces zabity przed zapisem migawki: na dysku nie ma ani premii, ani nowego dnia. */
    @Test
    fun killBeforePersistGrantsExactlyOnce() {
        val dir = folder.newFolder()
        seed(dir, 5)
        val file = File(dir, "profile.json")
        val before = file.readBytes()
        val run = Run(dir)
        val start = run.medalsAtStart
        run.menu()
        run.flush()
        assertEquals(start + WelcomeBack.BONUS, run.profile.medals)
        // Zapis „nie doszedł”: przywracamy plik sprzed powitania (i bez kopii zapasowych).
        file.writeBytes(before)
        File(dir, "profile.json.bak").delete()
        val retry = Run(dir)
        retry.menu()
        retry.flush()
        assertEquals(retry.medalsAtStart + WelcomeBack.BONUS, retry.profile.medals)
        // Po trwałym zapisie trzeci start już nic nie daje.
        val third = Run(dir)
        third.menu()
        assertEquals(retry.profile.medals, third.profile.medals)
        assertTrue(third.game.noticeTexts().isEmpty())
    }

    /** Zapis z chmury sprzed premii (stary dzień wizyty) nie odtwarza przerwy. */
    @Test
    fun cloudSnapshotFromBeforeTheBreakGivesNoSecondBonus() {
        val dir = folder.newFolder()
        seed(dir, 4)
        val staleCloud = Profile(File(dir, "profile.json")).also { it.load() }.toCloudJson()
        val run = Run(dir)
        run.menu()
        val afterBonus = run.profile.medals
        run.game.clearNotices()
        run.profile.fromJson(staleCloud)
        run.game.frame(canvas, 1f / 60f)
        assertEquals(today, run.profile.lastPlayDay)
        assertTrue(run.game.noticeTexts().isEmpty())
        assertTrue(afterBonus > run.profile.medals) // chmura podmieniła medale, ale bez nowej premii
    }

    /** Kolejność z R10-01: najpierw bitwa utracona przy aktualizacji, potem powitanie. */
    @Test
    fun lostBattleBannersComeFirst() {
        val dir = folder.newFolder()
        seed(dir, 6)
        val meta = BattleMeta(77L, Mode.ENDLESS, 0, -1L, -1L, false, 23, 900, 700, 75, now - 6 * day)
        val run = Run(dir, lost = meta)
        run.menu()
        val texts = run.game.noticeTexts()
        val lost = LostBattle.notices(meta, LostBattle.Settlement(75, false))
        assertEquals(texts.toString(), lost, texts.take(2))
        assertTrue(texts.toString(), texts[2].startsWith("Witaj z powrotem!"))
        assertEquals(run.medalsAtStart + 75 + WelcomeBack.BONUS, run.profile.medals)
    }

    /** Banery mieszczą się (≥ 11 sp) w 10 językach; zrzuty menu pl/en/de na telefonie i Foldzie. */
    @Test
    fun bannersFitAndScreenshots() {
        val bad = mutableListOf<String>()
        for ((w, h, d) in listOf(Triple(1600, 720, 2f), Triple(904, 2316, 2.7f))) {
            for (code in L.CODES) {
                val dir = folder.newFolder()
                seed(dir, 3, medals = 120)
                val run = Run(dir, w, h, d, lang = code)
                run.game.onLanguageChanged()
                run.menu()
                val texts = run.game.noticeTexts()
                assertTrue("$code: ${texts.size}", texts.size >= 3)
                for ((i, t) in texts.withIndex()) {
                    run.game.clearNotices()
                    run.game.showNotice(t)
                    run.game.frame(canvas, 1f / 60f)
                    assertFalse(run.game.noticeBanner.isEmpty)
                    if (run.game.noticeTextSize * run.game.ui.textScale < 11f)
                        bad += "$code ${w}x$h: '$t' ma ${run.game.noticeTextSize} sp"
                    if (code in setOf("pl", "en", "de")) shot(run.game, "welcome193_${code}_${w}x${h}_$i")
                }
            }
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    private fun shot(g: Game, name: String) {
        val out = File("build/screenshots").apply { mkdirs() }
        val bmp = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
        try {
            g.frame(Canvas(bmp), 0f)
            File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bmp.recycle() }
    }
}
