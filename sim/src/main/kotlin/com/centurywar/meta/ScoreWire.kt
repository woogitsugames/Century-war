package com.centurywar.meta

import com.centurywar.game.BattleConfig
import com.centurywar.game.Mode
import com.centurywar.game.Replay
import com.centurywar.game.SimVersion
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Część protokołu klienta niezależna od Androida i transportu HTTPS.
 * Ten sam kod służy aplikacji oraz testom integracyjnym Ktor.
 */
object ScoreWire {
    data class SignedHeaders(
        val timestamp: String,
        val nonce: String,
        val signature: String,
        val installationSecret: String?,
    )

    /** Nie zmienia treści podpisu: pięć pól rozdzielonych LF i opcjonalny sekret instalacji. */
    fun sign(
        secret: ByteArray, timestamp: String, nonce: String,
        method: String, target: String, body: String, installationSecret: String? = null,
    ): String {
        val msg = listOf(timestamp, nonce, method, target, body).joinToString("\n") +
            (if (installationSecret != null) "\n" + installationSecret else "")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        val digest = mac.doFinal(msg.toByteArray(StandardCharsets.UTF_8))
        val hex = "0123456789abcdef"
        return buildString(64) {
            for (b in digest) {
                append(hex[(b.toInt() ushr 4) and 15])
                append(hex[b.toInt() and 15])
            }
        }
    }

    fun headers(
        secret: ByteArray, timestamp: String, nonce: String,
        method: String, target: String, body: String, installationSecret: String? = null,
    ): SignedHeaders = SignedHeaders(
        timestamp, nonce, sign(secret, timestamp, nonce, method, target, body, installationSecret),
        installationSecret,
    )

    /**
     * Te same nazwy pól, wartości i opcjonalne pola co w dotychczasowym POST /v1/score.
     * Badania są już skopiowane i wyzerowane dla daily/weekly przez ScoreClient.
     */
    fun submission(
        cfg: BattleConfig, log: Replay.Log, score: Long, nick: String,
        playerId: String, levels: IntArray, difficulty: Int,
    ): String {
        val weekly = cfg.mode == Mode.ENDLESS && cfg.weekly >= 0L
        val board = when (cfg.mode) {
            Mode.DAILY -> "daily"
            Mode.ENDLESS -> if (weekly) "weekly" else "endless"
            Mode.SKIRMISH -> "skirmish"
            Mode.CAMPAIGN -> "campaign"
        }
        return buildString(256) {
            append("{\"board\":")
            appendQuoted(board)
            append(",\"player\":")
            appendQuoted(playerId)
            append(",\"nick\":")
            appendQuoted(nick)
            append(",\"gameVersion\":")
            appendQuoted(SimVersion.PROTOCOL)
            append(",\"seed\":")
            append(cfg.seed)
            append(",\"replay\":")
            appendQuoted(Replay.encode(log))
            append(",\"score\":")
            append(score)
            append(",\"research\":[")
            levels.forEachIndexed { index, level ->
                if (index != 0) append(',')
                append(level)
            }
            append(']')
            if (cfg.mode == Mode.DAILY) {
                append(",\"day\":")
                append(cfg.seed)
            }
            if (weekly) {
                append(",\"day\":")
                append(cfg.weekly)
            }
            if (cfg.mode == Mode.CAMPAIGN) {
                append(",\"stage\":")
                append(cfg.stageIndex)
                if (cfg.tutorial) append(",\"tutorial\":true")
            }
            if (cfg.mode == Mode.SKIRMISH) {
                append(",\"difficulty\":")
                append(difficulty.coerceIn(0, 3))
            }
            append('}')
        }
    }

    private fun StringBuilder.appendQuoted(value: String) {
        append('"')
        for (char in value) {
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char < ' ') {
                    append("\\u")
                    append(char.code.toString(16).padStart(4, '0'))
                } else append(char)
            }
        }
        append('"')
    }
}
