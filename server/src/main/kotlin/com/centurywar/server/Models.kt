package com.centurywar.server

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
    /** Zapis [com.centurywar.game.Replay.encode]: ten sam format, który nagrywa gra. */
    val replay: String,
    val score: Long,
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
        board.matches(Regex("^campaign_([0-9]|[12][0-9]|3[01])$"))

internal fun submittedBoard(board: String, stage: Int?): String = when (board) {
    "campaign" -> {
        require(stage != null && stage in 0..31) { "invalid_stage" }
        "campaign_" + stage
    }
    "daily", "endless", "skirmish" -> {
        require(stage == null) { "invalid_stage" }
        board
    }
    else -> throw IllegalArgumentException("invalid_board")
}

internal fun todayUtc(nowMillis: Long): Long = Math.floorDiv(nowMillis, 86_400_000L)
