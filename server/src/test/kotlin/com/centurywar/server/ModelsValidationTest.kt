package com.centurywar.server

import com.centurywar.game.Campaign
import com.centurywar.game.Research
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModelsValidationTest {
    private val player = "a16036d5-800a-4898-aea3-11b6548de7b4"
    private val zeros = List(Research.defs.size) { 0 }

    private fun submission(board: String = "endless") = ScoreSubmission(
        board = board, player = player, nick = "Gracz", gameVersion = "0.8.13",
        seed = 1, replay = "", score = 1, research = zeros,
    )

    private fun error(expected: String, action: () -> Unit) {
        val e = assertThrows(IllegalArgumentException::class.java) { action() }
        assertEquals(expected, e.message)
    }

    @Test
    fun playerRequiresCanonicalLowercaseUuidV4AndRfcVariant() {
        val good = listOf(player, "00000000-0000-4000-8000-000000000000")
        val bad = listOf("", player.dropLast(1), player + "0", player.uppercase(),
            "a16036d5-800a-3898-aea3-11b6548de7b4", // inna wersja
            "a16036d5-800a-4898-6ea3-11b6548de7b4", // inny wariant
            "a16036d5-800a-4898-aea3-11b6548de7b\u0000", "ż" + player.drop(1),
            " a16036d5-800a-4898-aea3-11b6548de7b4", "1-1-4-8-1")
        good.forEach { assertTrue(validPlayer(it), it) }
        bad.forEach { assertFalse(validPlayer(it), it) }
    }

    @Test
    fun nickUsesOneToTwentyFourUtf16UnitsAndAllowedCharacters() {
        val good = listOf("A", "a".repeat(24), "Zażółć_17", "Жан-李 7", ".", "a b")
        val bad = listOf("", "a".repeat(25), " A", "A ", "A\tB", "A\nB", "A\u0000B",
            "A@B", "A/B", "🚀", "e\u0301", " ")
        good.forEach { assertTrue(validNick(it), it) }
        bad.forEach { assertFalse(validNick(it), it) }
        // Litera spoza BMP zajmuje dwie jednostki UTF-16.
        val letter = "\uD801\uDC00"
        assertTrue(validNick(letter.repeat(12)))
        assertFalse(validNick(letter.repeat(13)))
    }

    @Test
    fun boardNamesHaveExplicitIndexAndLengthBounds() {
        val good = listOf("daily", "endless", "skirmish", "campaign_0", "campaign_9", "campaign_10",
            "campaign_35", "weekly_0", "weekly_9999999", "weekly_0000001")
        val bad = listOf("", "campaign", "weekly", "campaign_-1", "campaign_36", "campaign_00",
            "weekly_", "weekly_-1", "weekly_10000000", "weekly_１２", "daily\n", "daily\u0000",
            "Dälly", "DAILY", "daily" + "x".repeat(100))
        good.forEach { assertTrue(validBoard(it), it) }
        bad.forEach { assertFalse(validBoard(it), it) }
    }

    @Test
    fun submittedBoardsCoverStageWeekAndNoStage() {
        assertEquals("campaign_0", submittedBoard("campaign", 0))
        assertEquals("campaign_${Campaign.stages.lastIndex}", submittedBoard("campaign", Campaign.stages.lastIndex))
        for (stage in listOf(null, -1, Campaign.stages.size, Int.MAX_VALUE))
            error("invalid_stage") { submittedBoard("campaign", stage) }
        for (name in listOf("daily", "endless", "skirmish")) {
            assertEquals(name, submittedBoard(name, null))
            error("invalid_stage") { submittedBoard(name, 0) }
        }
        assertEquals("weekly_0", submittedBoard("weekly", null, 0))
        assertEquals("weekly_9999999", submittedBoard("weekly", null, 9_999_999))
        for (week in listOf(null, -1L, 10_000_000L, Long.MAX_VALUE))
            error("invalid_week") { submittedBoard("weekly", null, week) }
        error("invalid_stage") { submittedBoard("weekly", 0, 1) }
        for (bad in listOf("", "campaign_0", "weekly_1", "daily\n", "wyzwanie", "x".repeat(100)))
            error("invalid_board") { submittedBoard(bad, null) }
        // Samo mapowanie ignoruje week dla pozostałych plansz; dalsza weryfikacja jest osobna.
        assertEquals("endless", submittedBoard("endless", null, 2))
    }

    @Test
    fun submissionChecksResearchDifficultyTutorialAndBoardCombination() {
        validateSubmission(submission())
        validateSubmission(submission("campaign").copy(stage = 0))
        validateSubmission(submission("campaign").copy(stage = Campaign.stages.lastIndex))
        validateSubmission(submission("weekly").copy(day = 0))
        validateSubmission(submission("weekly").copy(day = 9_999_999))
        validateSubmission(submission("daily").copy(day = 1))
        for (difficulty in 0..3) validateSubmission(submission("skirmish").copy(difficulty = difficulty))
        for (difficulty in listOf(null, -1, 4, Int.MAX_VALUE))
            error("invalid_difficulty") { validateSubmission(submission("skirmish").copy(difficulty = difficulty)) }
        error("invalid_difficulty") { validateSubmission(submission().copy(difficulty = 0)) }
        error("invalid_tutorial") { validateSubmission(submission().copy(tutorial = true)) }
        for (levels in listOf(emptyList(), zeros.dropLast(1), zeros + 0,
                zeros.toMutableList().apply { this[0] = -1 },
                zeros.toMutableList().apply { this[0] = Research.defs[0].max + 1 }))
            error("invalid_research") { validateSubmission(submission().copy(research = levels)) }
        validateSubmission(submission().copy(research = Research.defs.map { it.max }))
        for (board in listOf("daily", "weekly"))
            error("invalid_research") { validateSubmission(submission(board).copy(day = 1,
                research = zeros.toMutableList().apply { this[0] = 1 })) }
        error("invalid_stage") { validateSubmission(submission("campaign")) }
        error("invalid_week") { validateSubmission(submission("weekly")) }
        for (bad in listOf("", "x".repeat(100), "daily\n", "\u0000", "dáily"))
            error("invalid_board") { validateSubmission(submission(bad)) }
        // validateSubmission kontroluje metadane, a nie samodzielnie UUID, nick, wersję i replay.
        validateSubmission(submission().copy(player = "", nick = "", gameVersion = "", replay = "", score = 0))
    }

    @Test
    fun utcDaysUseFloorAcrossEpochAndMidnight() {
        val day = 86_400_000L
        val examples = listOf(
            -2 * day - 1 to -3L, -2 * day to -2L, -day - 1 to -2L,
            -day to -1L, -day + 1 to -1L, -1L to -1L, 0L to 0L,
            1L to 0L, day - 1 to 0L, day to 1L, day + 1 to 1L,
            2 * day - 1 to 1L, 2 * day to 2L, 2 * day + 1 to 2L,
        )
        for ((millis, expected) in examples) assertEquals(expected, todayUtc(millis), "ms=$millis")
    }
}
