package com.centurywar.build

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayGamesIdsGeneratorTest {
    private fun complete(): String = PlayGamesIdsGenerator.requiredKeys
        .mapIndexed { index, key ->
            key + "=" + if (key == "app_id") "123456789012" else
                "CgkI" + index.toString().padStart(12, '0') + "EAIQAg"
        }.joinToString("\n")

    @Test fun validIdsProduceCompleteXml() {
        val xml = PlayGamesIdsGenerator.render(complete(), release = true)
        assertTrue(xml.contains("<bool name=\"play_games_configured\">true</bool>"))
        assertEquals(PlayGamesIdsGenerator.requiredKeys.size,
            Regex("<string name=").findAll(xml).count())
        assertTrue(xml.contains("123456789012"))
        assertFalse(xml.contains("REPLACE_ME"))
    }

    @Test fun absentAndPartialDebugDisableAllIds() {
        for (source in listOf(null, "", "app_id=123456789012", complete().replace(
            "leaderboard_daily=CgkI", "leaderboard_daily=REPLACE_ME_CgkI"
        ))) {
            val xml = PlayGamesIdsGenerator.render(source, release = false)
            assertTrue(xml.contains("<bool name=\"play_games_configured\">false</bool>"))
            assertFalse(xml.contains("123456789012"))
            assertEquals(PlayGamesIdsGenerator.requiredKeys.size,
                Regex(">REPLACE_ME</string>").findAll(xml).count())
        }
    }

    @Test fun releaseRejectsEveryMissingValue() {
        for (source in listOf(null, "", "app_id=REPLACE_ME", complete()
            .replace("achievement_replay_record=CgkI", "achievement_replay_record=REPLACE_ME_CgkI"))) {
            var rejected = false
            try { PlayGamesIdsGenerator.render(source, release = true) }
            catch (e: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
        }
    }

    @Test fun invalidNonPlaceholderFailsAlsoInIncompleteDebug() {
        val incomplete = "app_id=123456789012\nleaderboard_endless=bad\nleaderboard_daily=REPLACE_ME"
        var rejected = false
        try { PlayGamesIdsGenerator.render(incomplete, release = false) }
        catch (e: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
    }

    @Test fun rejectsWrongIdsAndDuplicateId() {
        val valid = complete()
        val invalid = listOf(
            valid.replace("app_id=123456789012", "app_id=1234ABC"),
            valid.replace("leaderboard_daily=CgkI", "leaderboard_daily=https://"),
            valid.replace("achievement_win_1=CgkI", "achievement_win_1=<script>"),
            valid.replace("leaderboard_daily=CgkI", "leaderboard_daily=123456789012"),
            valid + "\nunknown_key=CgkI123456789012EAIQAg",
            valid.replace("leaderboard_daily=CgkI000000000004EAIQAg",
                "leaderboard_daily=CgkI000000000001EAIQAg"),
        )
        for (source in invalid) {
            var rejected = false
            try { PlayGamesIdsGenerator.render(source, release = true) }
            catch (e: IllegalArgumentException) { rejected = true }
            assertTrue(source, rejected)
        }
    }

    @Test fun propertiesSchemaCoversAllCatalogNames() {
        assertEquals(65, PlayGamesIdsGenerator.achievementNames.size)
        assertEquals(65, PlayGamesIdsGenerator.achievementNames.toSet().size)
        assertEquals(70, PlayGamesIdsGenerator.requiredKeys.size)
    }
}
