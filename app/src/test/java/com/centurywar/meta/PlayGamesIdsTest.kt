package com.centurywar.meta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Properties

class PlayGamesIdsTest {
    private fun ids(count: Int) = List(count) {
        "CgkI" + (it + 1).toString().padStart(12, '0') + "EAIQAg"
    }

    @Test fun everyAchievementHasMapEntryAndTemplateKey() {
        val keys = Achievements.all.map { it.pgsKey }.toSet()
        assertEquals(54, keys.size)
        assertEquals(keys, PlayGamesIds.achievementResources.keys)
        val file = sequenceOf(
            File("../docs/store/play_games_ids.properties"),
            File("docs/store/play_games_ids.properties"),
        ).firstOrNull { it.isFile } ?: error("Nie znaleziono szablonu Play Games")
        val props = Properties().apply { file.inputStream().use { load(it) } }
        val expected = setOf("app_id", "leaderboard_endless", "leaderboard_campaign",
            "leaderboard_skirmish", "leaderboard_daily") +
            Achievements.all.map { "achievement_" + it.id }
        assertEquals(expected, props.stringPropertyNames())
    }

    @Test fun isConfiguredRequiresEveryValidAndUniqueId() {
        val boards = ids(4)
        val achievements = ids(54).mapIndexed { i, s ->
            if (i < 4) "CgkI" + (i + 100).toString().padStart(12, '0') + "EAIQAg" else s
        }
        assertTrue(PlayGamesIds.isConfiguredValues("123456789012", boards, achievements))
        assertFalse(PlayGamesIds.isConfiguredValues("REPLACE_ME", boards, achievements))
        assertFalse(PlayGamesIds.isConfiguredValues("invalid", boards, achievements))
        assertFalse(PlayGamesIds.isConfiguredValues("123456789012", boards.drop(1), achievements))
        assertFalse(PlayGamesIds.isConfiguredValues("123456789012", boards, achievements.drop(1)))
        assertFalse(PlayGamesIds.isConfiguredValues("123456789012",
            boards.toMutableList().apply { this[0] = "REPLACE_ME" }, achievements))
        assertFalse(PlayGamesIds.isConfiguredValues("123456789012",
            boards, achievements.toMutableList().apply { this[0] = "bad" }))
        assertFalse(PlayGamesIds.isConfiguredValues("123456789012",
            boards, achievements.toMutableList().apply { this[0] = boards[0] }))
    }
}
