package com.centurywar.meta

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Odtwarzanie profilu z pełnego zapisu, starych wersji i przerwanych operacji. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ProfileRobustnessTest {
    @get:Rule val folder = TemporaryFolder()

    private fun profileFile(): File = folder.newFile("profile.json").apply { delete() }

    @Test
    fun everyFieldSurvivesSaveAndLoad() {
        val file = profileFile()
        val source = Profile(file)
        source.name = "Łucja 👩🏽‍🚀 «Wódz» \"A\""
        source.medals = 1234567
        for ((i, def) in Profile.research.withIndex()) {
            source.levels[def.key] = i % (def.max + 1)
        }
        source.stars.indices.forEach { source.stars[it] = it % 4 }
        source.bestWave = 67
        source.lastRankedDaily = 20123L
        source.tutorialDone = true
        source.quality = 3
        source.fpsCap = 30
        source.vibration = false
        source.sound = false
        source.showFps = true
        source.lang = "pl"
        source.skirmishDiff = 3
        source.battles = 900
        source.wins = 450
        source.totalKills = 12345678901L
        source.playSeconds = 91234567890L
        source.boards[Profile.BOARD_ENDLESS] = arrayListOf(
            ScoreEntry("😊 \"Pola\"", 9000000001L, 20000L),
            ScoreEntry("Łódź 東京 🏆", 87L, 19999L),
        )
        source.boards[Profile.dailyBoard(20123L)] =
            arrayListOf(ScoreEntry("Élise 👩‍🚀", 33L, 20123L))
        source.save()

        assertEquals(Profile.CURRENT_VERSION, JSONObject(file.readText()).getInt("v"))
        val restored = Profile(file).apply { load() }
        assertEquals(source.name, restored.name)
        assertEquals(source.medals, restored.medals)
        assertEquals(source.levels, restored.levels)
        assertArrayEquals(source.stars, restored.stars)
        assertEquals(source.bestWave, restored.bestWave)
        assertEquals(source.lastRankedDaily, restored.lastRankedDaily)
        assertEquals(source.tutorialDone, restored.tutorialDone)
        assertEquals(source.quality, restored.quality)
        assertEquals(source.fpsCap, restored.fpsCap)
        assertEquals(source.vibration, restored.vibration)
        assertEquals(source.sound, restored.sound)
        assertEquals(source.showFps, restored.showFps)
        assertEquals(source.lang, restored.lang)
        assertEquals(source.skirmishDiff, restored.skirmishDiff)
        assertEquals(source.battles, restored.battles)
        assertEquals(source.wins, restored.wins)
        assertEquals(source.totalKills, restored.totalKills)
        assertEquals(source.playSeconds, restored.playSeconds)
        assertEquals(source.boards.keys, restored.boards.keys)
        for (board in source.boards.keys) {
            val expected = source.top(board).map { Triple(it.name, it.score, it.day) }
            val actual = restored.top(board).map { Triple(it.name, it.score, it.day) }
            assertEquals(board, expected, actual)
        }
    }

    @Test
    fun legacyJsonWithoutVersionAndOptionalFieldsIsMigrated() {
        val file = profileFile()
        file.writeText(
            JSONObject()
                .put("name", "Historyczny")
                .put("medals", 99)
                .put("levels", JSONObject().put("r_income", 4))
                .put("stars", JSONArray().put(3).put(2))
                .toString(),
        )
        val p = Profile(file).apply { load() }
        assertEquals("Historyczny", p.name)
        assertEquals(99, p.medals)
        assertEquals(4, p.level("r_income"))
        assertEquals(3, p.stars[0])
        assertEquals(2, p.stars[1])
        assertEquals(0, p.stars[2])
        assertTrue(p.vibration)
        assertTrue(p.sound)
        assertEquals(-1L, p.lastRankedDaily)

        val migrated = Profile.migrate(JSONObject().put("v", 1).put("medals", 99), 1)
        assertEquals(2, migrated.getInt("v"))
        assertEquals(99, migrated.getInt("medals"))
        p.save()
        assertEquals(2, JSONObject(file.readText()).getInt("v"))
        assertEquals(99, Profile(file).apply { load() }.medals)
    }

    @Test
    fun corruptFileIsPreservedAndProfileStartsClean() {
        val file = profileFile()
        val damaged = "{\"name\":\"Zapis\",\"medals\":123,"
        file.writeText(damaged)
        val p = Profile(file).apply {
            name = "Poprzedni"
            medals = 400
            levels["r_income"] = 9
            load()
        }
        val backup = File(file.parentFile, file.name + ".corrupt")
        assertEquals(damaged, backup.readText())
        assertEquals("Dowódca", p.name)
        assertEquals(0, p.medals)
        assertTrue(p.levels.isEmpty())

        file.writeText("{ drugi uszkodzony zapis")
        p.load()
        assertEquals(damaged, backup.readText())
        assertTrue(File(file.parentFile, file.name + ".corrupt.1").exists())
        p.medals = 15
        p.save()
        assertEquals(15, Profile(file).apply { load() }.medals)
        assertEquals(damaged, backup.readText())
    }

    @Test
    fun malformedBoardDoesNotPartiallyRestoreProfile() {
        val file = profileFile()
        file.writeText(
            JSONObject().put("name", "Przed błędem")
                .put("medals", 777)
                .put("boards", JSONObject().put("endless", JSONArray().put("nie obiekt")))
                .toString(),
        )
        val p = Profile(file).apply { load() }
        assertEquals("Dowódca", p.name)
        assertEquals(0, p.medals)
        assertTrue(p.boards.isEmpty())
        assertTrue(File(file.parentFile, file.name + ".corrupt").exists())
    }

    @Test
    fun validTmpWithoutMainFileRecoversAfterInterruption() {
        val file = profileFile()
        Profile(file).apply { name = "Ocalony"; medals = 34567; save() }
        val tmp = File(file.parentFile, file.name + ".tmp")
        assertTrue(file.renameTo(tmp))
        assertFalse(file.exists())

        val restored = Profile(file).apply { load() }
        assertEquals("Ocalony", restored.name)
        assertEquals(34567, restored.medals)
        assertTrue(file.exists())
        assertFalse(tmp.exists())
    }

    @Test
    fun newerTmpWinsButBrokenTmpCannotDestroyMainFile() {
        val file = profileFile()
        val tmp = File(file.parentFile, file.name + ".tmp")
        val p = Profile(file)
        p.medals = 200
        p.save()
        val earlier = file.lastModified()

        tmp.writeText(JSONObject(file.readText()).put("medals", 300).toString())
        assertTrue(tmp.setLastModified(earlier + 10_000L))
        assertEquals(300, Profile(file).apply { load() }.medals)
        assertEquals(300, Profile(file).apply { load() }.medals)

        val good = file.readText()
        tmp.writeText("{niepełny")
        assertTrue(tmp.setLastModified(file.lastModified() + 10_000L))
        assertEquals(300, Profile(file).apply { load() }.medals)
        assertEquals(good, file.readText())
        assertEquals("{niepełny", File(file.parentFile, file.name + ".corrupt").readText())
    }

    @Test
    fun hugeLeaderboardLoadsOnlyBestTwentyEntries() {
        val file = profileFile()
        val entries = JSONArray()
        for (i in 0 until 12_000) {
            entries.put(JSONObject().put("n", "Łódź #$i 🏆").put("s", i.toLong() * 17).put("d", 20_000L))
        }
        file.writeText(JSONObject().put("boards", JSONObject().put(Profile.BOARD_ENDLESS, entries)).toString())
        val p = Profile(file).apply { load() }
        assertEquals(20, p.top(Profile.BOARD_ENDLESS).size)
        assertEquals(11_999L * 17, p.top(Profile.BOARD_ENDLESS)[0].score)
        assertEquals(11_980L * 17, p.top(Profile.BOARD_ENDLESS)[19].score)
        p.save()
        val onDisk = JSONObject(file.readText()).getJSONObject("boards").getJSONArray(Profile.BOARD_ENDLESS)
        assertEquals(20, onDisk.length())
    }

    @Test
    fun futureProfileVersionIsNotOverwritten() {
        val file = profileFile()
        val future = JSONObject().put("v", Profile.CURRENT_VERSION + 1)
            .put("medals", 1234).toString()
        file.writeText(future)
        Profile(file).apply { load(); medals = 9000; save() }
        assertEquals(future, file.readText())
    }
}
