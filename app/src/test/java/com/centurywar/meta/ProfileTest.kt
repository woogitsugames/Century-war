package com.centurywar.meta

import com.centurywar.engine.Palette
import com.centurywar.engine.Ui
import org.junit.Assert.assertEquals
import com.centurywar.game.Mode
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

/** Profil używa org.json z Androida, więc testy idą przez Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfileTest {

    private fun tmpFile(): File = File(Files.createTempDirectory("cw").toFile(), "profile.json")

    @Test
    fun globalRankingRequiresConsentAndKeepsIdAfterReset() {
        val file = tmpFile()
        val profile = Profile(file)
        assertFalse(profile.globalRanking)
        assertEquals("", profile.playerId)
        profile.save()
        Profile.awaitHintWrites()
        assertFalse(Profile(file).also { Profile.awaitHintWrites(); it.load() }.globalRanking)

        profile.enableGlobalRanking()
        val firstId = profile.playerId
        assertTrue(profile.globalRanking)
        assertTrue(java.util.UUID.fromString(firstId).version() == 4)
        profile.reset()
        assertEquals(firstId, profile.playerId)
        assertTrue(profile.globalRanking)

        profile.disableGlobalRanking()
        assertFalse(profile.globalRanking)
        assertEquals(firstId, profile.playerId)
        val fromFile = Profile(file).also { Profile.awaitHintWrites(); it.load() }
        assertFalse(fromFile.globalRanking)
        assertEquals(firstId, fromFile.playerId)
        fromFile.renewRankingId()
        assertTrue(fromFile.playerId != firstId)
    }

    @Test
    fun playGamesCloudCannotImportConsentOrRankingId() {
        val local = Profile(tmpFile())
        val remote = Profile(tmpFile())
        remote.enableGlobalRanking()
        remote.medals = 250
        assertFalse(remote.toCloudJson().contains("\"globalRanking\""))
        assertFalse(remote.toCloudJson().contains("\"playerId\""))

        local.fromJson(remote.toJson())
        assertFalse(local.globalRanking)
        assertEquals("", local.playerId)
        assertEquals(250, local.medals)

        local.enableGlobalRanking()
        val id = local.playerId
        val other = Profile(tmpFile())
        other.enableGlobalRanking()
        other.medals = 300
        local.fromJson(other.toJson())
        assertTrue(local.globalRanking)
        assertEquals(id, local.playerId)
        assertEquals(300, local.medals)
    }

    @Test
    fun malformedIdAndLegacyFileDisableGlobalRanking() {
        val file = tmpFile()
        file.writeText("""{"playerId":"fake","globalRanking":true}""")
        val malformed = Profile(file).also { Profile.awaitHintWrites(); it.load() }
        assertFalse(malformed.globalRanking)
        assertEquals("", malformed.playerId)
        file.writeText("""{"name":"Dawny profil"}""")
        val legacy = Profile(file).also { Profile.awaitHintWrites(); it.load() }
        assertFalse(legacy.globalRanking)
        assertEquals("", legacy.playerId)
    }

    @Test
    fun researchFormulaMatchesPreviousBonuses() {
        val profile = Profile(tmpFile())
        for (def in Profile.research) {
            profile.levels[def.key] = def.max
        }
        val p = profile.bonuses()
        assertEquals(200f, p.startGold, 0.001f)
        assertEquals(1.72f, p.incomeMul, 0.001f)
        assertEquals(1.6f, p.unitHpMul, 0.001f)
        assertEquals(1.6f, p.unitDmgMul, 0.001f)
        assertEquals(1.8f, p.baseHpMul, 0.001f)
        assertEquals(1.6f, p.turretDmgMul, 0.001f)
        assertEquals(1.5f, p.trainMul, 0.001f)
        assertEquals(1.5f, p.xpMul, 0.001f)
        assertEquals(18, p.popCap)
        assertEquals(0.6f, p.abilityCdMul, 0.001f)
        assertEquals(4, p.turretSlots)
        assertTrue(p.eliteUnlocked)
        assertTrue(p.rallyUnlocked)
    }

    @Test
    fun legacySkirmishUsesExistingDifficultyWhenSetupMissing() {
        val f = tmpFile()
        f.writeText("""{"skirmishDiff":3}""")
        val old = Profile(f).also { Profile.awaitHintWrites(); it.load() }
        assertEquals(3, old.skirmishDiff)
        assertEquals(160, old.skirmishSetup.powerPercent)
        assertTrue(old.skirmishSetup.isRanked())
        assertEquals(0, old.skirmishSetup.startEra)
        old.save()
        Profile.awaitHintWrites()
        val again = Profile(f).also { Profile.awaitHintWrites(); it.load() }
        assertEquals(old.skirmishSetup, again.skirmishSetup)
    }

    @Test
    fun personalBestSurvivesRename() {
        val p = Profile(tmpFile())
        p.name = "Alfa"
        assertTrue(p.submit(Profile.BOARD_ENDLESS, 1000, 1))
        p.name = "Beta"
        assertFalse("słabszy wynik po zmianie nazwy to nie rekord", p.submit(Profile.BOARD_ENDLESS, 500, 2))
        assertTrue(p.submit(Profile.BOARD_ENDLESS, 1500, 3))
    }

    @Test
    fun weeklyTiersAreClaimedOnceAndSurviveSave() {
        val f = tmpFile()
        val p = Profile(f)
        assertEquals(0, p.claimWeekly(301L, 9, 301L))
        assertEquals(25, p.claimWeekly(301L, 10, 301L))
        assertEquals(0, p.claimWeekly(301L, 19, 301L))
        assertEquals(60, p.claimWeekly(301L, 20, 301L))
        assertEquals(120, p.claimWeekly(301L, 30, 301L))
        assertEquals(0, p.claimWeekly(301L, 40, 301L))
        assertEquals(7, p.weeklyClaimed)
        p.save()
        Profile.awaitHintWrites()
        val loaded = Profile(f).also { Profile.awaitHintWrites(); it.load() }
        assertEquals(301L, loaded.weeklyWeek)
        assertEquals(7, loaded.weeklyClaimed)
        assertEquals(0, loaded.claimWeekly(301L, 35, 301L))
        // Opóźniona bitwa poprzedniego tygodnia nie daje progów ponownie.
        loaded.syncWeekly(302L)
        assertEquals(0, loaded.claimWeekly(301L, 35, 302L))
        assertEquals(25, loaded.claimWeekly(302L, 10, 302L))
        loaded.save()
        Profile.awaitHintWrites()
        val reload = Profile(f).also { Profile.awaitHintWrites(); it.load() }
        assertEquals(7, reload.weeklyClaimedFor(301L))
        assertEquals(1, reload.weeklyClaimedFor(302L))
    }

    @Test
    fun weeklyBoardsKeepOnlyLastEightWeeks() {
        val p = Profile(tmpFile())
        for (w in 200L..212L) p.submit(Profile.weeklyBoard(w), w * 10L, w * 7L)
        p.syncWeekly(212L)
        for (w in 200L..204L) assertTrue(p.top(Profile.weeklyBoard(w)).isEmpty())
        for (w in 205L..212L) assertEquals(1, p.top(Profile.weeklyBoard(w)).size)
        p.save()
        Profile.awaitHintWrites()
        val loaded = Profile(tmpFile())
        // Załadowanie starego JSON nie może wyzerować starego typu rankingu.
        loaded.fromJson(p.toJson())
        assertEquals(8, loaded.boards.keys.count { it.startsWith("weekly_") })
        assertEquals(0, loaded.claimWeekly(203L, 30, 212L))
    }

    @Test
    fun weeklyStateDefaultsAndMasksAreClamped() {
        val f = tmpFile()
        f.writeText("""{"weeklyWeek":-99,"weeklyClaimed":255,"weeklyClaims":{"3":255,"-9":7}}""")
        val old = Profile(f).also { Profile.awaitHintWrites(); it.load() }
        assertEquals(-1L, old.weeklyWeek)
        assertEquals(0, old.weeklyClaimed)
        assertEquals(7, old.weeklyClaimedFor(3L))
        assertEquals(0, old.weeklyClaimedFor(-9L))

        f.writeText("""{"weeklyWeek":999,"weeklyClaimed":255}""")
        val loaded = Profile(f).also { Profile.awaitHintWrites(); it.load() }
        assertEquals(999L, loaded.weeklyWeek)
        assertEquals(7, loaded.weeklyClaimed)
        loaded.syncWeekly(1000L)
        assertEquals(0, loaded.weeklyClaimed)
        assertEquals(7, loaded.weeklyClaimedFor(999L))

        f.writeText("""{"medals":7}""")
        val legacy = Profile(f).also { Profile.awaitHintWrites(); it.load() }
        assertEquals(-1L, legacy.weeklyWeek)
        assertEquals(0, legacy.weeklyClaimed)
        assertEquals(7, legacy.medals)
    }

    @Test
    fun concurrentSavesLeaveValidFile() {
        val f = tmpFile()
        val p = Profile(f).apply { medals = 777; name = "x".repeat(16) }
        repeat(30) { p.submit(Profile.BOARD_SKIRMISH, it.toLong() * 1000, 1) }
        val threads = List(4) { Thread { repeat(150) { p.save() } } }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        val q = Profile(f).also { Profile.awaitHintWrites(); it.load() }
        assertEquals(777, q.medals)
        assertEquals(20, q.top(Profile.BOARD_SKIRMISH).size)
    }

    @Test
    fun loadClampsOutOfRangeValues() {
        val f = tmpFile()
        f.writeText("""{"medals":-5,"skirmishDiff":9,"quality":7,"fpsCap":45,"stars":[5,-1,2]}""")
        val p = Profile(f).also { Profile.awaitHintWrites(); it.load() }
        assertEquals(0, p.medals)
        assertEquals(3, p.skirmishDiff)
        assertEquals(0, p.quality)
        assertEquals(0, p.fpsCap)
        assertEquals(3, p.stars[0])
        assertEquals(0, p.stars[1])
        assertEquals(2, p.stars[2])
    }


    @Test
    fun remindersRoundTripAndOldProfilesGetDefaults() {
        val f = tmpFile()
        f.writeText("""{"wins":2,"notifyHour":99,"notifications":false}""")
        val invalid = Profile(f).also { Profile.awaitHintWrites(); it.load() }
        assertEquals(23, invalid.notifyHour)
        assertFalse(invalid.notifications)
        invalid.notifyHour = 7
        invalid.save()
        Profile.awaitHintWrites()
        val loaded = Profile(f).also { Profile.awaitHintWrites(); it.load() }
        assertEquals(7, loaded.notifyHour)
        assertFalse(loaded.notifications)

        f.writeText("""{"wins":1}""")
        val old = Profile(f).also { Profile.awaitHintWrites(); it.load() }
        assertTrue(old.notifications)
        assertEquals(18, old.notifyHour)

        f.writeText("""{"notifyHour":-50}""")
        assertEquals(0, Profile(f).also { Profile.awaitHintWrites(); it.load() }.notifyHour)
    }

    @Test
    fun migrationKeepsLegacyStarsAndLeaderboard() {
        val f = tmpFile()
        val legacy = IntArray(32) { (it % 3) + 1 }
        val json = """{"v":1,"medals":117,"stars":[${legacy.joinToString(",")}],
            "boards":{"campaign":[{"n":"Komendant","s":1234,"d":17}]}}"""
        f.writeText(json)
        val p = Profile(f).also { Profile.awaitHintWrites(); it.load() }
        assertEquals(36, p.stars.size)
        assertEquals(legacy[7], p.stars[7])
        for (i in 8..11) assertEquals(0, p.stars[i])
        for (i in 8 until 32) assertEquals("Stary etap ${i + 1}", legacy[i], p.stars[i + 4])
        assertEquals(legacy.sum(), p.totalStars())
        assertEquals(1234L, p.top(Profile.BOARD_CAMPAIGN).single().score)
        p.save()
        Profile.awaitHintWrites()
        val reloaded = Profile(f).also { Profile.awaitHintWrites(); it.load() }
        assertEquals(p.stars.toList(), reloaded.stars.toList())
        assertEquals(legacy.sum(), reloaded.totalStars())
        assertEquals(117, reloaded.medals)
        assertEquals(1234L, reloaded.top(Profile.BOARD_CAMPAIGN).single().score)
    }

    @Test
    fun loadFallsBackToTempFileWhenMainIsMissing() {
        val f = tmpFile()
        Profile(f).apply { medals = 321; save() }
        assertTrue(f.renameTo(File(f.parentFile, f.name + ".tmp")))
        val p = Profile(f).also { Profile.awaitHintWrites(); it.load() }
        assertEquals(321, p.medals)
    }


    @Test
    fun replayIdIsOptionalBackwardCompatibleAndSurvivesJson() {
        val f = tmpFile()
        val board = Profile.BOARD_ENDLESS
        // Pełny profil v3 (ścisły odczyt) z wpisem rankingu sprzed pola "r".
        Profile(f).save()
        Profile.awaitHintWrites()
        val json = org.json.JSONObject(f.readText())
        json.put("boards", org.json.JSONObject("""{"endless":[{"n":"Stary","s":90,"d":12}]}"""))
        f.writeText(json.toString())
        val old = Profile(f).also { Profile.awaitHintWrites(); it.load() }
        assertEquals(null, old.top(board).single().replay)
        assertTrue(old.submit(board, 200L, 13L, "battle_001"))
        old.save()
        Profile.awaitHintWrites()
        val again = Profile(f).also { Profile.awaitHintWrites(); it.load() }
        assertEquals("battle_001", again.top(board).first().replay)
        assertEquals(null, again.top(board).last().replay)
        // ID jest wyłącznie nazwą pliku, nie może zawierać ścieżek.
        again.submit(board, 100L, 14L, "../invalid")
        assertEquals(null, again.top(board).first { it.score == 100L }.replay)
    }

    @Test
    fun standardPalettePreservesLegacyColors() {
        Palette.select(Palette.STANDARD)
        assertEquals(0xFF408CEB.toInt(), Ui.PLAYER)
        assertEquals(0xFFDC463C.toInt(), Ui.ENEMY)
        assertEquals(0xFF76E278.toInt(), Ui.GOOD)
        assertEquals(0xFFF05C50.toInt(), Ui.BAD)
        assertEquals(0xEE1A2030.toInt(), Ui.PANEL)
        assertEquals(0xFF1A1620.toInt(), Ui.OUTLINE)
        assertEquals(0xFF409646.toInt(), Ui.BTN_GREEN)
        Palette.select(Palette.DEUTAN_PROTAN)
        assertTrue(Ui.PLAYER != Ui.ENEMY)
        assertTrue(Ui.GOOD != Ui.BAD)
        Palette.select(Palette.TRITAN)
        assertTrue(Ui.PLAYER != Ui.ENEMY)
        Palette.select(Palette.HIGH_CONTRAST)
        assertEquals(0xFA080C16.toInt(), Ui.PANEL)
        Palette.select(Palette.STANDARD)
    }

    @Test
    fun accessibilityRoundTripAndLegacyDefaults() {
        val f = tmpFile()
        val p = Profile(f)
        assertEquals(0, p.palette)
        assertEquals(100, p.textScale)
        assertFalse(p.teamMarkers)
        assertFalse(p.reduceShake)
        assertFalse(p.reduceFlash)
        p.palette = 3
        p.textScale = 130
        p.teamMarkers = true
        p.reduceShake = true
        p.reduceFlash = true
        p.save()
        Profile.awaitHintWrites()
        val restored = Profile(f).also { Profile.awaitHintWrites(); it.load() }
        assertEquals(3, restored.palette)
        assertEquals(130, restored.textScale)
        assertTrue(restored.teamMarkers)
        assertTrue(restored.reduceShake)
        assertTrue(restored.reduceFlash)

        f.writeText("""{"v":1,"name":"stary profil"}""")
        val legacy = Profile(f).also { Profile.awaitHintWrites(); it.load() }
        assertEquals(0, legacy.palette)
        assertEquals(100, legacy.textScale)
        assertFalse(legacy.teamMarkers)
        assertFalse(legacy.reduceShake)
        assertFalse(legacy.reduceFlash)
    }

    @Test
    fun accessibilityClampsUntrustedSettings() {
        val f = tmpFile()
        f.writeText("""{"palette":99,"textScale":999,"teamMarkers":true,"reduceShake":true,"reduceFlash":true}""")
        val high = Profile(f).also { Profile.awaitHintWrites(); it.load() }
        assertEquals(3, high.palette)
        assertEquals(130, high.textScale)
        assertTrue(high.teamMarkers)
        assertTrue(high.reduceShake)
        assertTrue(high.reduceFlash)

        f.writeText("""{"palette":-8,"textScale":-40}""")
        val low = Profile(f).also { Profile.awaitHintWrites(); it.load() }
        assertEquals(0, low.palette)
        assertEquals(100, low.textScale)
        assertFalse(low.teamMarkers)
        low.textScale = 115
        assertEquals(115, low.textScale)
        low.textScale = 999
        assertEquals(130, low.textScale)
    }

    @Test
    fun sfxVolumeRoundTripLegacyAndInvalidValues() {
        val f = tmpFile()
        val p = Profile(f)
        assertEquals(0.8f, p.sfxVolume, 0.0001f)
        p.sfxVolume = 0.63f
        p.save()
        Profile.awaitHintWrites()
        assertEquals(0.63f, Profile(f).also { Profile.awaitHintWrites(); it.load() }.sfxVolume, 0.0001f)
        f.writeText("""{"sound":false}""")
        val mutedLegacy = Profile(f).also { Profile.awaitHintWrites(); it.load() }
        assertFalse(mutedLegacy.sound)
        assertEquals(0.8f, mutedLegacy.sfxVolume, 0.0001f)
        f.writeText("""{"sfxVolume":2}""")
        assertEquals(1f, Profile(f).also { Profile.awaitHintWrites(); it.load() }.sfxVolume, 0.0001f)
        f.writeText("""{"sfxVolume":-3}""")
        assertEquals(0f, Profile(f).also { Profile.awaitHintWrites(); it.load() }.sfxVolume, 0.0001f)
        f.writeText("""{"sfxVolume":"NaN"}""")
        assertEquals(0.8f, Profile(f).also { Profile.awaitHintWrites(); it.load() }.sfxVolume, 0.0001f)
        f.writeText("""{"v":1,"name":"Stary profil"}""")
        assertEquals(0.8f, Profile(f).also { Profile.awaitHintWrites(); it.load() }.sfxVolume, 0.0001f)
    }

    @Test
    fun musicVolumeDefaultsPersistsAndClamps() {
        val f = tmpFile()
        val p = Profile(f)
        assertEquals(0.5f, p.musicVolume, 0.0001f)
        p.musicVolume = 0.73f
        p.save()
        Profile.awaitHintWrites()
        assertEquals(0.73f, Profile(f).also { Profile.awaitHintWrites(); it.load() }.musicVolume, 0.0001f)
        f.writeText("""{"musicVolume":5}""")
        assertEquals(1f, Profile(f).also { Profile.awaitHintWrites(); it.load() }.musicVolume, 0.0001f)
        f.writeText("""{"musicVolume":-2}""")
        assertEquals(0f, Profile(f).also { Profile.awaitHintWrites(); it.load() }.musicVolume, 0.0001f)
        f.writeText("""{"musicVolume":"NaN"}""")
        assertEquals(0.5f, Profile(f).also { Profile.awaitHintWrites(); it.load() }.musicVolume, 0.0001f)
        f.writeText("""{"name":"Stary profil"}""")
        assertEquals(0.5f, Profile(f).also { Profile.awaitHintWrites(); it.load() }.musicVolume, 0.0001f)
    }

    @Test
    fun screenShakeDefaultsPersistsAndClamps() {
        val f = tmpFile()
        val p = Profile(f)
        assertEquals(2, p.screenShake)
        p.screenShake = 1
        p.save()
        Profile.awaitHintWrites()
        assertEquals(1, Profile(f).also { Profile.awaitHintWrites(); it.load() }.screenShake)

        // Zapis v3 sprzed dodania ustawienia nadal musi być poprawny.
        val old = org.json.JSONObject(p.toJson())
        old.remove("screenShake")
        f.writeText(old.toString())
        assertEquals(2, Profile(f).also { Profile.awaitHintWrites(); it.load() }.screenShake)

        old.put("screenShake", -5)
        f.writeText(old.toString())
        assertEquals(0, Profile(f).also { Profile.awaitHintWrites(); it.load() }.screenShake)
        old.put("screenShake", 100)
        f.writeText(old.toString())
        assertEquals(2, Profile(f).also { Profile.awaitHintWrites(); it.load() }.screenShake)
    }

    @Test
    fun battleModeCountersRoundTripAndTrackStreaks() {
        val p = Profile(tmpFile())
        p.recordBattle(Mode.ENDLESS, true, 200)
        p.recordBattle(Mode.ENDLESS, true, 100)
        p.recordBattle(Mode.CAMPAIGN, false, 50)
        p.recordBattle(Mode.DAILY, true, 45)
        assertEquals(2, p.bestWinStreak)
        assertEquals(1, p.winStreak)
        val copy = Profile(tmpFile())
        copy.fromJson(p.toJson())
        assertEquals(2, copy.modeBattles[Mode.ENDLESS.ordinal])
        assertEquals(2, copy.modeWins[Mode.ENDLESS.ordinal])
        assertEquals(300L, copy.modeSeconds[Mode.ENDLESS.ordinal])
        assertEquals(1, copy.modeBattles[Mode.CAMPAIGN.ordinal])
        assertEquals(0, copy.modeWins[Mode.CAMPAIGN.ordinal])
        assertEquals(2, copy.bestWinStreak)
        assertEquals(1, copy.winStreak)
    }

    @Test
    fun oldProfileDefaultsAndNewCountersAreClamped() {
        val f = tmpFile()
        f.writeText("""{"v":1,"wins":2}""")
        val old = Profile(f).also { Profile.awaitHintWrites(); it.load() }
        assertTrue(old.modeBattles.all { it == 0 })
        assertTrue(old.modeWins.all { it == 0 })
        assertTrue(old.modeSeconds.all { it == 0L })
        assertEquals(0, old.winStreak)
        assertEquals(0, old.bestWinStreak)

        val source = Profile(tmpFile()).toJson()
        val json = JSONObject(source)
        json.put("modeBattles", JSONArray().put(3).put(-20).put(99_999_999))
        json.put("modeWins", JSONArray().put(90).put(5).put(-4))
        json.put("modeSeconds", JSONArray().put(-1).put(9_999_999_999L))
        json.put("winStreak", -2)
        json.put("bestWinStreak", 50)
        val copy = Profile(tmpFile())
        copy.fromJson(json.toString())
        assertEquals(3, copy.modeBattles[0])
        assertEquals(3, copy.modeWins[0])
        assertEquals(0, copy.modeBattles[1])
        assertEquals(10_000_000, copy.modeBattles[2])
        assertEquals(0, copy.modeWins[2])
        assertEquals(0L, copy.modeSeconds[0])
        assertEquals(3_155_760_000L, copy.modeSeconds[1])
        assertEquals(50, copy.bestWinStreak)
        assertEquals(0, copy.winStreak)
    }

    @Test
    fun hintsPersistClampAndResetWithOldProfileDefaults() {
        val f = tmpFile()
        val p = Profile(f)
        assertTrue(p.hintsEnabled)
        assertEquals(0L, p.hintsSeen)
        assertTrue(p.markHintSeen(0))
        assertTrue(p.markHintSeen(Profile.HINT_COUNT - 1))
        assertFalse(p.markHintSeen(0))
        assertFalse(p.markHintSeen(Profile.HINT_COUNT))
        p.hintsEnabled = false
        p.save()
        Profile.awaitHintWrites()
        Profile.awaitHintWrites()
        val loaded = Profile(f).also { Profile.awaitHintWrites(); it.load() }
        assertFalse(loaded.hintsEnabled)
        assertEquals(1L or (1L shl (Profile.HINT_COUNT - 1)), loaded.hintsSeen)
        loaded.replayHints()
        Profile.awaitHintWrites()
        assertEquals(0L, Profile(f).also { Profile.awaitHintWrites(); it.load() }.hintsSeen)

        f.writeText("""{"hintsSeen":-9,"hintsEnabled":false}""")
        val negative = Profile(f).also { Profile.awaitHintWrites(); it.load() }
        assertEquals(0L, negative.hintsSeen)
        assertFalse(negative.hintsEnabled)
        f.writeText("""{"hintsSeen":9223372036854775807}""")
        assertEquals(Profile.HINT_VALID_MASK, Profile(f).also { Profile.awaitHintWrites(); it.load() }.hintsSeen)
        f.writeText("""{"tutorialDone":true}""")
        val old = Profile(f).also { Profile.awaitHintWrites(); it.load() }
        assertEquals(0L, old.hintsSeen)
        assertTrue(old.hintsEnabled)
        old.markHintSeen(2)
        Profile.awaitHintWrites()
        old.reset()
        assertEquals(0L, old.hintsSeen)
        assertTrue(old.hintsEnabled)
    }


    @Test
    fun rapidGameThreadSavesKeepLatestSnapshotAndWriteOffThread() {
        val file = tmpFile()
        val profile = Profile(file)
        val writerThread = java.util.concurrent.atomic.AtomicReference<String>()
        profile.onSaved = { writerThread.set(Thread.currentThread().name) }
        val gameThread = Thread({
            repeat(120) { i ->
                profile.medals = i + 1
                profile.save()
            }
        }, "game-loop")
        gameThread.start()
        gameThread.join()
        assertTrue(profile.awaitWrites(10_000))
        assertEquals("cw-profile-io", writerThread.get())
        assertEquals(120, Profile(file).also { it.load() }.medals)
        assertFalse(profile.awaitWrites(0))
    }
}
