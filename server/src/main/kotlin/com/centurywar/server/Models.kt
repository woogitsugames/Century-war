package com.centurywar.server

import com.centurywar.game.Campaign
import com.centurywar.game.Research
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ScoreSubmission(
    val board: String,
    val player: String,
    val nick: String,
    val gameVersion: String,
    val day: Long? = null,
    val seed: Long,
    val stage: Int? = null,
    /** Zapis komend z BattleController, zakodowany przez Replay.encode. */
    val replay: String,
    val score: Long,
    /** Poziomy w kolejności Research.defs; wymagane od wersji 0.2.0. */
    val research: List<Int> = emptyList(),
    /** 0..3 wyłącznie dla potyczki. */
    val difficulty: Int? = null,
    /** Tylko samouczek pierwszego etapu kampanii. */
    val tutorial: Boolean = false,
)

@Serializable
data class ScoreReceipt(
    val board: String,
    val verifiedScore: Long,
    val bestScore: Long,
    val accepted: Boolean,
)

@Serializable
data class RankingEntry(
    val rank: Long,
    val player: String,
    val nick: String,
    val score: Long,
)

@Serializable
data class RankingResponse(
    val board: String,
    val period: String,
    val entries: List<RankingEntry>,
)

@Serializable
data class ErrorResponse(val error: String)

internal fun validPlayer(player: String): Boolean {
    val id = runCatching { UUID.fromString(player) }.getOrNull() ?: return false
    return id.version() == 4 && id.variant() == 2 && id.toString() == player
}

internal fun validNick(nick: String): Boolean =
    nick.length in 1..24 && nick == nick.trim() && NICK_REGEX.matches(nick)

private val NICK_REGEX = Regex("^[\\p{L}\\p{N} ._-]{1,24}$")

internal fun validBoard(board: String): Boolean =
    board in setOf("daily", "endless", "skirmish") ||
        board.matches(Regex("^campaign_([0-9]|[12][0-9]|3[0-5])$"))

internal fun submittedBoard(board: String, stage: Int?): String = when (board) {
    "campaign" -> {
        require(stage != null && stage in Campaign.stages.indices) { "invalid_stage" }
        "campaign_" + stage
    }
    "daily", "endless", "skirmish" -> {
        require(stage == null) { "invalid_stage" }
        board
    }
    else -> throw IllegalArgumentException("invalid_board")
}

/** Błędy danych wejściowych -> HTTP 400, przed kosztownym odtwarzaniem bitwy. */
internal fun validateSubmission(input: ScoreSubmission) {
    submittedBoard(input.board, input.stage)
    require(Research.valid(input.research)) { "invalid_research" }
    require(if (input.board == "skirmish") input.difficulty != null && input.difficulty in 0..3
            else input.difficulty == null) { "invalid_difficulty" }
    require(!input.tutorial || (input.board == "campaign" && input.stage == 0)) { "invalid_tutorial" }
    require(input.board != "daily" || input.research.all { it == 0 }) { "invalid_research" }
}

internal fun todayUtc(nowMillis: Long): Long = Math.floorDiv(nowMillis, 86_400_000L)
