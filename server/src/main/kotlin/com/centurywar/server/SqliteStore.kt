package com.centurywar.server

import java.sql.Connection
import java.sql.DriverManager
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class SavedScore(val inserted: Boolean, val bestScore: Long)

data class AdminScore(
    val player: String,
    val nick: String,
    val board: String,
    val day: Long,
    val score: Long,
    val verifiedAt: Long,
    val blocked: Boolean,
)

internal class BlockedPlayerException : IllegalStateException("blocked_player")

/** Jeden uchwyt JDBC, wszystkie operacje szeregowane; nadaje się dla jednej instancji. */
class SqliteStore(jdbcUrl: String) : AutoCloseable {
    private val lock = Any()
    private val connection: Connection = DriverManager.getConnection(jdbcUrl)

    init {
        synchronized(lock) {
            // PRAGMA journal_mode nie może być zmieniane wewnątrz transakcji.
            connection.createStatement().use { s ->
                s.execute("PRAGMA busy_timeout=5000")
                s.execute("PRAGMA foreign_keys=ON")
                s.execute("PRAGMA journal_mode=WAL")
            }
            connection.autoCommit = false
            try {
                connection.createStatement().use { s ->
                    s.execute("""
                        CREATE TABLE IF NOT EXISTS players (
                            player_id TEXT PRIMARY KEY NOT NULL,
                            nick TEXT NOT NULL,
                            updated_at INTEGER NOT NULL
                        )
                    """.trimIndent())
                    s.execute("""
                        CREATE TABLE IF NOT EXISTS blocked_players (
                            player_id TEXT PRIMARY KEY NOT NULL,
                            blocked_at INTEGER NOT NULL
                        )
                    """.trimIndent())
                    s.execute("""
                        CREATE TABLE IF NOT EXISTS scores (
                            player_id TEXT NOT NULL REFERENCES players(player_id),
                            board TEXT NOT NULL,
                            day INTEGER NOT NULL,
                            score INTEGER NOT NULL CHECK (score >= 0),
                            seed INTEGER NOT NULL,
                            game_version TEXT NOT NULL,
                            ticks INTEGER NOT NULL,
                            replay_sha256 TEXT NOT NULL,
                            verified_at INTEGER NOT NULL,
                            research_sum INTEGER NOT NULL DEFAULT 0,
                            PRIMARY KEY(player_id, board, day)
                        )
                    """.trimIndent())
                    val hasResearch = s.executeQuery("PRAGMA table_info(scores)").use { rs ->
                        var found = false
                        while (rs.next()) if (rs.getString("name") == "research_sum") found = true
                        found
                    }
                    if (!hasResearch) s.execute("ALTER TABLE scores ADD COLUMN research_sum INTEGER NOT NULL DEFAULT 0")
                    s.execute("CREATE INDEX IF NOT EXISTS idx_scores_board_time ON scores(board, verified_at, score)")
                    s.execute("""
                        CREATE TABLE IF NOT EXISTS used_nonces (
                            nonce TEXT PRIMARY KEY NOT NULL,
                            expires_at INTEGER NOT NULL
                        )
                    """.trimIndent())
                    s.execute("CREATE INDEX IF NOT EXISTS idx_nonces_expire ON used_nonces(expires_at)")
                    s.execute("""
                        CREATE TABLE IF NOT EXISTS schema_version (
                            id INTEGER PRIMARY KEY CHECK (id = 1),
                            version INTEGER NOT NULL
                        )
                    """.trimIndent())
                    s.execute("INSERT OR IGNORE INTO schema_version(id, version) VALUES (1, 0)")
                    val version = s.executeQuery("SELECT version FROM schema_version WHERE id = 1").use { rs ->
                        check(rs.next()) { "missing_schema_version" }
                        rs.getInt(1)
                    }
                    check(version in 0..1) { "unsupported_schema_version" }
                    if (version == 0) {
                        // Dwa kroki zapobiegają kolizji klucza 8->12, 12->16 itd.
                        val oldBoards = (8..31).joinToString(",") { "'campaign_" + it + "'" }
                        val temporaryBoards = (8..31).joinToString(",") { "'__cw43_campaign_" + it + "'" }
                        s.execute("UPDATE scores SET board = '__cw43_' || board WHERE board IN (" + oldBoards + ")")
                        s.execute("""
                            UPDATE scores
                            SET board = 'campaign_' || (CAST(substr(board, 17) AS INTEGER) + 4)
                            WHERE board IN ($temporaryBoards)
                        """.trimIndent())
                        s.execute("UPDATE schema_version SET version = 1 WHERE id = 1")
                    }
                }
                connection.commit()
            } catch (e: Exception) {
                connection.rollback()
                throw e
            } finally {
                connection.autoCommit = true
            }
        }
    }

    /** Usuwa rekordy i pseudonim gracza; brak gracza nie jest błędem. */
    fun deletePlayer(player: String) = synchronized(lock) {
        connection.autoCommit = false
        try {
            connection.prepareStatement("DELETE FROM scores WHERE player_id = ?").use {
                it.setString(1, player)
                it.executeUpdate()
            }
            connection.prepareStatement("DELETE FROM players WHERE player_id = ?").use {
                it.setString(1, player)
                it.executeUpdate()
            }
            connection.prepareStatement("DELETE FROM blocked_players WHERE player_id = ?").use {
                it.setString(1, player)
                it.executeUpdate()
            }
            connection.commit()
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }

    fun healthy(): Boolean = synchronized(lock) {
        connection.createStatement().use { s ->
            s.executeQuery("SELECT 1").use { rs -> rs.next() && rs.getInt(1) == 1 }
        }
    }

    fun consumeNonce(nonce: String, nowSeconds: Long): Boolean = synchronized(lock) {
        connection.prepareStatement("DELETE FROM used_nonces WHERE expires_at <= ?").use {
            it.setLong(1, nowSeconds)
            it.executeUpdate()
        }
        connection.prepareStatement(
            "INSERT OR IGNORE INTO used_nonces(nonce, expires_at) VALUES (?, ?)"
        ).use {
            it.setString(1, nonce)
            it.setLong(2, nowSeconds + 600L)
            it.executeUpdate() == 1
        }
    }

    fun save(
        input: ScoreSubmission,
        replay: VerifiedReplay,
        nowMillis: Long,
        replayHash: String,
    ): SavedScore = synchronized(lock) {
        val day = todayUtc(nowMillis)
        val nowSeconds = Math.floorDiv(nowMillis, 1000L)
        connection.autoCommit = false
        try {
            // Sprawdzamy ponownie po weryfikacji, aby uniknąć wyścigu z CLI.
            if (isBlockedUnsafe(input.player)) throw BlockedPlayerException()
            connection.prepareStatement("""
                INSERT INTO players(player_id, nick, updated_at) VALUES (?, ?, ?)
                ON CONFLICT(player_id) DO UPDATE SET
                    nick=excluded.nick, updated_at=excluded.updated_at
            """.trimIndent()).use {
                it.setString(1, input.player)
                it.setString(2, NickModeration.publicNick(input.nick, input.player))
                it.setLong(3, nowSeconds)
                it.executeUpdate()
            }
            val policy = if (replay.board == "daily") {
                "INSERT OR IGNORE"
            } else {
                "INSERT"
            }
            val sql = """
                $policy INTO scores(
                    player_id, board, day, score, seed, game_version,
                    ticks, replay_sha256, verified_at, research_sum
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent() + if (replay.board == "daily") "" else """
                 ON CONFLICT(player_id, board, day) DO UPDATE SET
                    score=excluded.score, seed=excluded.seed,
                    game_version=excluded.game_version, ticks=excluded.ticks,
                    replay_sha256=excluded.replay_sha256,
                    verified_at=excluded.verified_at,
                    research_sum=excluded.research_sum
                 WHERE excluded.score > scores.score
            """
            val inserted = connection.prepareStatement(sql).use {
                it.setString(1, input.player)
                it.setString(2, replay.board)
                it.setLong(3, day)
                it.setLong(4, replay.score)
                it.setLong(5, input.seed)
                it.setString(6, input.gameVersion)
                it.setInt(7, replay.steps)
                it.setString(8, replayHash)
                it.setLong(9, nowSeconds)
                it.setInt(10, replay.researchSum)
                it.executeUpdate() > 0
            }
            val best = connection.prepareStatement("""
                SELECT COALESCE(MAX(score), 0) FROM scores
                WHERE player_id=? AND board=?
            """.trimIndent()).use {
                it.setString(1, input.player)
                it.setString(2, replay.board)
                it.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else 0L }
            }
            connection.commit()
            SavedScore(inserted, best)
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }

    fun top(board: String, period: String, nowMillis: Long, limit: Int = 50): List<RankingEntry> =
        ranked(board, period, nowMillis, null, limit)

    fun around(board: String, period: String, nowMillis: Long, player: String): List<RankingEntry> =
        ranked(board, period, nowMillis, player, 7)

    private fun ranked(
        board: String,
        period: String,
        nowMillis: Long,
        player: String?,
        limit: Int,
    ): List<RankingEntry> = synchronized(lock) {
        val start = firstSecond(period, nowMillis)
        val sql = """
            WITH best AS (
                SELECT player_id, MAX(score) AS score
                FROM scores
                WHERE board=? AND verified_at>=?
                  AND NOT EXISTS (SELECT 1 FROM blocked_players b WHERE b.player_id=scores.player_id)
                GROUP BY player_id
            ), ranked AS (
                SELECT ROW_NUMBER() OVER (ORDER BY best.score DESC, best.player_id ASC) AS rank,
                       best.player_id, players.nick, best.score
                FROM best JOIN players ON players.player_id=best.player_id
            )
            SELECT rank, player_id, nick, score FROM ranked
        """.trimIndent() + if (player == null) {
            " ORDER BY rank LIMIT ?"
        } else {
            " WHERE ABS(rank - (SELECT rank FROM ranked WHERE player_id=?)) <= 3 ORDER BY rank LIMIT ?"
        }
        connection.prepareStatement(sql).use {
            it.setString(1, board)
            it.setLong(2, start)
            if (player != null) {
                it.setString(3, player)
                it.setInt(4, limit)
            } else {
                it.setInt(3, limit)
            }
            it.executeQuery().use { rs ->
                val result = ArrayList<RankingEntry>()
                while (rs.next()) {
                    val playerId = rs.getString(2)
                    result.add(RankingEntry(
                        rs.getLong(1), playerId,
                        NickModeration.publicNick(rs.getString(3), playerId), rs.getLong(4)
                    ))
                }
                result
            }
        }
    }

    override fun close() = synchronized(lock) { connection.close() }

    companion object {
        private fun firstSecond(period: String, nowMillis: Long): Long {
            val day = LocalDate.ofEpochDay(todayUtc(nowMillis))
            val start = when (period) {
                "day" -> day
                "week" -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                else -> return 0L
            }
            return start.toEpochDay() * 86_400L
        }
    }
}
