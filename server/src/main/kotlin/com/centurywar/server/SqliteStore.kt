package com.centurywar.server

import java.sql.Connection
import java.sql.DriverManager
import java.security.MessageDigest
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
internal class PlayerOwnershipException : IllegalStateException("invalid_player_credential")

data class NickReportRow(
    val id: Long, val player: String, val publicId: String, val nick: String,
    val reason: String, val reportedAt: Long,
)

/** Jeden uchwyt JDBC, wszystkie operacje szeregowane; nadaje się dla jednej instancji. */
class SqliteStore(
    jdbcUrl: String,
    private val publicIds: PublicPlayerIds = PublicPlayerIds.temporary(),
) : AutoCloseable {
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
                    check(version in 0..2) { "unsupported_schema_version" }
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
                    // Stare instalacje nie miały poświadczenia ani publicznego ID.
                    val columns = mutableSetOf<String>()
                    s.executeQuery("PRAGMA table_info(players)").use { rs ->
                        while (rs.next()) columns.add(rs.getString("name"))
                    }
                    if ("secret_hash" !in columns) s.execute("ALTER TABLE players ADD COLUMN secret_hash TEXT")
                    if ("public_id" !in columns) s.execute("ALTER TABLE players ADD COLUMN public_id TEXT")
                    val missingPublicIds = mutableListOf<String>()
                    s.executeQuery("SELECT player_id FROM players WHERE public_id IS NULL").use { rs ->
                        while (rs.next()) missingPublicIds.add(rs.getString(1))
                    }
                    connection.prepareStatement("UPDATE players SET public_id = ? WHERE player_id = ?").use { update ->
                        for (player in missingPublicIds) {
                            update.setString(1, publicIds.of(player))
                            update.setString(2, player)
                            update.executeUpdate()
                        }
                    }
                    s.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_players_public_id ON players(public_id)")
                    s.execute("""
                        CREATE TABLE IF NOT EXISTS nick_reports (
                            report_id INTEGER PRIMARY KEY AUTOINCREMENT,
                            player_id TEXT NOT NULL,
                            public_id TEXT NOT NULL,
                            reported_nick TEXT NOT NULL,
                            reason TEXT NOT NULL,
                            reported_at INTEGER NOT NULL
                        )
                    """.trimIndent())
                    s.execute("CREATE INDEX IF NOT EXISTS idx_reports_time ON nick_reports(reported_at)")
                    if (version < 2) s.execute("UPDATE schema_version SET version = 2 WHERE id = 1")
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

    /** Stare wpisy z NULL nie są dostępne przez /around ani /delete do pierwszego wyniku. */
    fun isOwner(player: String, hash: String): Boolean = synchronized(lock) {
        hasCredentialUnsafe(player, hash, allowMissing = false, allowLegacy = false)
    }

    fun mayClaim(player: String, hash: String): Boolean = synchronized(lock) {
        hasCredentialUnsafe(player, hash, allowMissing = true, allowLegacy = true)
    }

    private fun hasCredentialUnsafe(
        player: String, hash: String, allowMissing: Boolean, allowLegacy: Boolean,
    ): Boolean = connection.prepareStatement("SELECT secret_hash FROM players WHERE player_id = ?").use { st ->
        st.setString(1, player)
        st.executeQuery().use { rs ->
            if (!rs.next()) return@use allowMissing
            val saved = rs.getString(1) ?: return@use allowLegacy
            MessageDigest.isEqual(saved.toByteArray(Charsets.US_ASCII), hash.toByteArray(Charsets.US_ASCII))
        }
    }

    fun publicIdFor(player: String): String? = synchronized(lock) {
        connection.prepareStatement("SELECT public_id FROM players WHERE player_id = ?").use {
            it.setString(1, player)
            it.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }
    }

    /** Usuwanie przez API wymaga klucza przypisanego do UUID. */
    fun deleteOwnedPlayer(player: String, hash: String): Boolean = synchronized(lock) {
        if (!hasCredentialUnsafe(player, hash, allowMissing = true, allowLegacy = false)) return@synchronized false
        deletePlayer(player)
        true
    }

    /** Usuwa rekordy i pseudonim gracza (blokada zostaje); brak gracza nie jest błędem. */
    fun deletePlayer(player: String) = synchronized(lock) {
        connection.autoCommit = false
        try {
            connection.prepareStatement("DELETE FROM nick_reports WHERE player_id = ?").use {
                it.setString(1, player)
                it.executeUpdate()
            }
            connection.prepareStatement("DELETE FROM scores WHERE player_id = ?").use {
                it.setString(1, player)
                it.executeUpdate()
            }
            connection.prepareStatement("DELETE FROM players WHERE player_id = ?").use {
                it.setString(1, player)
                it.executeUpdate()
            }
            // Blokady nie kasujemy: klucz HMAC jest w APK, więc usunięcie konta nie może
            // posłużyć zablokowanemu graczowi do zdjęcia blokady. Zdejmuje ją tylko moderator.
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
        installationSecretHash: String? = null,
    ): SavedScore = synchronized(lock) {
        val day = todayUtc(nowMillis)
        val nowSeconds = Math.floorDiv(nowMillis, 1000L)
        connection.autoCommit = false
        try {
            // Sprawdzamy ponownie po weryfikacji, aby uniknąć wyścigu z CLI.
            if (isBlockedUnsafe(input.player)) throw BlockedPlayerException()
            if (!hasCredentialUnsafe(input.player, installationSecretHash ?: "",
                    allowMissing = true, allowLegacy = true)) throw PlayerOwnershipException()
            val playerUpdated = connection.prepareStatement("""
                INSERT INTO players(player_id, nick, updated_at, secret_hash, public_id)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(player_id) DO UPDATE SET
                    nick=excluded.nick, updated_at=excluded.updated_at,
                    secret_hash=COALESCE(players.secret_hash, excluded.secret_hash),
                    public_id=COALESCE(players.public_id, excluded.public_id)
                WHERE players.secret_hash IS NULL OR players.secret_hash = excluded.secret_hash
            """.trimIndent()).use {
                it.setString(1, input.player)
                it.setString(2, NickModeration.publicNick(input.nick, input.player))
                it.setLong(3, nowSeconds)
                it.setString(4, installationSecretHash)
                it.setString(5, publicIds.of(input.player))
                it.executeUpdate()
            }
            if (playerUpdated != 1) throw PlayerOwnershipException()
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
                       best.player_id, players.nick, best.score, players.public_id
                FROM best JOIN players ON players.player_id=best.player_id
            )
            SELECT rank, player_id, nick, score, public_id FROM ranked
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
                        rs.getLong(1), rs.getString(5) ?: publicIds.of(playerId),
                        NickModeration.publicNick(rs.getString(3), playerId), rs.getLong(4)
                    ))
                }
                result
            }
        }
    }

    /** Zgłoszenie jest powiązane z realnym kontem, ale nie ujawnia jego UUID. */
    fun reportNick(publicId: String, reason: String, nowMillis: Long): Boolean = synchronized(lock) {
        val target = connection.prepareStatement(
            "SELECT player_id, nick FROM players WHERE public_id = ?"
        ).use { st ->
            st.setString(1, publicId)
            st.executeQuery().use { rs ->
                if (rs.next()) rs.getString(1) to rs.getString(2) else null
            }
        } ?: return@synchronized false
        connection.prepareStatement("""
            INSERT INTO nick_reports(player_id, public_id, reported_nick, reason, reported_at)
            VALUES (?, ?, ?, ?, ?)
        """.trimIndent()).use {
            it.setString(1, target.first)
            it.setString(2, publicId)
            it.setString(3, NickModeration.publicNick(target.second, target.first))
            it.setString(4, reason)
            it.setLong(5, Math.floorDiv(nowMillis, 1000L))
            it.executeUpdate()
        }
        true
    }

    fun recentReports(limit: Int = 50): List<NickReportRow> = synchronized(lock) {
        require(limit in 1..200) { "invalid_limit" }
        connection.prepareStatement("""
            SELECT report_id, player_id, public_id, reported_nick, reason, reported_at
            FROM nick_reports ORDER BY reported_at DESC, report_id DESC LIMIT ?
        """.trimIndent()).use { st ->
            st.setInt(1, limit)
            st.executeQuery().use { rs ->
                val rows = ArrayList<NickReportRow>()
                while (rs.next()) rows.add(NickReportRow(
                    rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                    rs.getString(5), rs.getLong(6)
                ))
                rows
            }
        }
    }

    /** Blokada obejmuje też wcześniejsze wyniki na wszystkich planszach. */
    fun blockPlayer(player: String): Boolean = synchronized(lock) {
        connection.prepareStatement(
            "INSERT OR IGNORE INTO blocked_players(player_id, blocked_at) VALUES (?, ?)"
        ).use {
            it.setString(1, player)
            it.setLong(2, System.currentTimeMillis() / 1000L)
            it.executeUpdate() > 0
        }
    }

    fun unblockPlayer(player: String): Boolean = synchronized(lock) {
        connection.prepareStatement("DELETE FROM blocked_players WHERE player_id = ?").use {
            it.setString(1, player)
            it.executeUpdate() > 0
        }
    }

    fun isBlocked(player: String): Boolean = synchronized(lock) { isBlockedUnsafe(player) }

    private fun isBlockedUnsafe(player: String): Boolean =
        connection.prepareStatement("SELECT 1 FROM blocked_players WHERE player_id = ?").use {
            it.setString(1, player)
            it.executeQuery().use { rs -> rs.next() }
        }

    /** Kasuje dokładnie jeden wynik (UUID, plansza, dzień UTC). */
    fun deleteScore(player: String, board: String, day: Long): Boolean = synchronized(lock) {
        connection.autoCommit = false
        try {
            val deleted = connection.prepareStatement(
                "DELETE FROM scores WHERE player_id = ? AND board = ? AND day = ?"
            ).use {
                it.setString(1, player)
                it.setString(2, board)
                it.setLong(3, day)
                it.executeUpdate() > 0
            }
            // Ostatni wpis usunięty: nick nie powinien pozostać osierocony.
            if (deleted) {
                connection.prepareStatement("""
                    DELETE FROM nick_reports WHERE player_id = ?
                    AND NOT EXISTS (SELECT 1 FROM scores WHERE scores.player_id=nick_reports.player_id)
                """.trimIndent()).use {
                    it.setString(1, player)
                    it.executeUpdate()
                }
                connection.prepareStatement("""
                    DELETE FROM players WHERE player_id = ?
                    AND NOT EXISTS (SELECT 1 FROM scores WHERE scores.player_id=players.player_id)
                """.trimIndent()).use {
                    it.setString(1, player)
                    it.executeUpdate()
                }
            }
            connection.commit()
            deleted
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }

    /** Ostatnie wyniki, w tym zablokowane, na potrzeby moderacji. */
    fun recentScores(limit: Int = 20): List<AdminScore> = synchronized(lock) {
        require(limit in 1..200) { "invalid_limit" }
        connection.prepareStatement("""
            SELECT s.player_id, p.nick, s.board, s.day, s.score, s.verified_at,
                   b.player_id IS NOT NULL AS blocked
            FROM scores s
            JOIN players p ON p.player_id=s.player_id
            LEFT JOIN blocked_players b ON b.player_id=s.player_id
            ORDER BY s.verified_at DESC, s.player_id ASC, s.board ASC, s.day DESC
            LIMIT ?
        """.trimIndent()).use {
            it.setInt(1, limit)
            it.executeQuery().use { rs ->
                val result = ArrayList<AdminScore>()
                while (rs.next()) {
                    result.add(AdminScore(
                        rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getLong(4), rs.getLong(5), rs.getLong(6), rs.getInt(7) != 0
                    ))
                }
                result
            }
        }
    }

    /** Eksport wiersz po wierszu, bez buforowania całej bazy. */
    fun exportCsv(out: Appendable): Int = synchronized(lock) {
        out.append("player,nick,board,day,score,seed,game_version,ticks,replay_sha256,verified_at,research_sum,blocked\n")
        var count = 0
        connection.createStatement().use { st ->
            st.executeQuery("""
                SELECT s.player_id, p.nick, s.board, s.day, s.score, s.seed,
                       s.game_version, s.ticks, s.replay_sha256, s.verified_at, s.research_sum,
                       b.player_id IS NOT NULL
                FROM scores s
                JOIN players p ON p.player_id=s.player_id
                LEFT JOIN blocked_players b ON b.player_id=s.player_id
                ORDER BY s.verified_at DESC, s.player_id ASC, s.board ASC, s.day DESC
            """.trimIndent()).use { rs ->
                while (rs.next()) {
                    for (column in 1..12) {
                        if (column > 1) out.append(',')
                        val value = if (column == 12) (rs.getInt(column) != 0).toString()
                                    else rs.getString(column)
                        // Apostrof zapobiega interpretowaniu wartości jako formuł w arkuszu.
                        val safe = if (value.firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r', '\n')) "'$value" else value
                        out.append('"').append(safe.replace("\"", "\"\"")).append('"')
                    }
                    out.append('\n')
                    count++
                }
            }
        }
        count
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
