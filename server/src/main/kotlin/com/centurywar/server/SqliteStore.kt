package com.centurywar.server

import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.TreeMap
import com.centurywar.game.Weekly

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
/** Gracza usunięto po rozpoczęciu zgłoszenia ([SqliteStore.beginClaim]); wynik nie może go odtworzyć. */
internal class PlayerDeletedException : IllegalStateException("player_deleted")

/**
 * Bilet zgłoszenia wyniku: zapamiętuje epokę usunięć z chwili sprawdzenia własności.
 * [SqliteStore.save] odrzuca zapis, jeśli gracza usunięto po wydaniu biletu.
 * Bilet zamyka [SqliteStore.endClaim] (także przez [close]) po zakończeniu żądania.
 */
class ScoreClaim internal constructor(
    val player: String,
    internal val ownerHash: String,
    internal val epoch: Long,
    internal val ownerExisted: Boolean,
    private val store: SqliteStore,
) : AutoCloseable {
    internal var closed = false
    override fun close() = store.endClaim(this)
}

data class NickReportRow(
    val id: Long, val player: String, val publicId: String, val nick: String,
    val reason: String, val reportedAt: Long, val resolvedAt: Long? = null,
)

/** Wpis dziennika usunięć: skrót HMAC identyfikatora gracza, nigdy surowy UUID. */
data class DeletionLogEntry(val playerIdHash: String, val deletedAt: Long, val source: String)

/** Gracz znaleziony po nicku (`admin find-nick`); [lastActivity] w sekundach UTC. */
data class PlayerMatch(
    val player: String, val publicId: String?, val nick: String,
    val scores: Int, val lastActivity: Long,
)

data class AdminLogRow(val at: Long, val action: String, val targetHash: String?, val detail: String)

/** Wynik usunięcia gracza przez operatora. */
data class OperatorDeletion(val found: Boolean, val blockRemoved: Boolean)

/** Jeden uchwyt JDBC, wszystkie operacje szeregowane; nadaje się dla jednej instancji. */
class SqliteStore(
    private val jdbcUrl: String,
    private val publicIds: PublicPlayerIds = PublicPlayerIds.temporary(),
) : AutoCloseable {
    private val lock = Any()
    private val connection: Connection = DriverManager.getConnection(jdbcUrl)

    // Epoki usunięć (A156-05). Usuwanie gracza jest dostępne tylko przez API tej instancji
    // (blokada procesu w DatabaseMaintenance.acquireLock), więc stan w pamięci wystarcza:
    // po restarcie nie ma zgłoszeń w toku. Wszystko chroni [lock], ten sam co transakcje.
    private var deletionEpoch = 0L
    private val deletedAtEpoch = HashMap<String, Long>()
    private val activeClaims = TreeMap<Long, Int>()

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
                    // Liczniki żądań z ważnym podpisem; przetrwają restart serwera.
                    s.execute("""
                        CREATE TABLE IF NOT EXISTS score_submit_quotas (
                            day INTEGER NOT NULL,
                            quota_kind TEXT NOT NULL,
                            quota_key TEXT NOT NULL,
                            requests INTEGER NOT NULL CHECK(requests > 0),
                            PRIMARY KEY(day, quota_kind, quota_key)
                        )
                    """.trimIndent())
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
                    check(version in 0..SCHEMA_VERSION) { "unsupported_schema_version" }
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
                    // Wersja 3 (R5): status zgłoszeń, dziennik usunięć i dziennik działań operatora.
                    val reportColumns = mutableSetOf<String>()
                    s.executeQuery("PRAGMA table_info(nick_reports)").use { rs ->
                        while (rs.next()) reportColumns.add(rs.getString("name"))
                    }
                    if ("resolved_at" !in reportColumns) s.execute("ALTER TABLE nick_reports ADD COLUMN resolved_at INTEGER")
                    s.execute("""
                        CREATE TABLE IF NOT EXISTS deletion_log (
                            player_id_hash TEXT NOT NULL,
                            deleted_at INTEGER NOT NULL,
                            source TEXT NOT NULL
                        )
                    """.trimIndent())
                    s.execute("CREATE INDEX IF NOT EXISTS idx_deletion_log_time ON deletion_log(deleted_at)")
                    s.execute("""
                        CREATE TABLE IF NOT EXISTS admin_log (
                            at INTEGER NOT NULL,
                            action TEXT NOT NULL,
                            target_hash TEXT,
                            detail TEXT NOT NULL
                        )
                    """.trimIndent())
                    s.execute("CREATE INDEX IF NOT EXISTS idx_admin_log_time ON admin_log(at)")
                    if (version < 3) s.execute("UPDATE schema_version SET version = 3 WHERE id = 1")
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

    /**
     * Sprawdza własność jak [mayClaim] i wydaje bilet zgłoszenia albo null.
     * Kolejność „własność → weryfikacja → zapis” jest chroniona epoką usunięć:
     * usunięcie gracza między [beginClaim] a [save] unieważnia bilet.
     */
    fun beginClaim(player: String, hash: String): ScoreClaim? = synchronized(lock) {
        if (!hasCredentialUnsafe(player, hash, allowMissing = true, allowLegacy = true)) return@synchronized null
        val claim = ScoreClaim(player, hash, deletionEpoch, playerExistsUnsafe(player), this)
        activeClaims.merge(claim.epoch, 1, Int::plus)
        claim
    }

    /** Zwalnia bilet; idempotentne. Epoki usunięć starsze niż każdy aktywny bilet są zapominane. */
    fun endClaim(claim: ScoreClaim) = synchronized(lock) {
        if (claim.closed) return@synchronized
        claim.closed = true
        val left = (activeClaims[claim.epoch] ?: 1) - 1
        if (left <= 0) activeClaims.remove(claim.epoch) else activeClaims[claim.epoch] = left
        val oldest = activeClaims.keys.firstOrNull()
        if (oldest == null) deletedAtEpoch.clear() else deletedAtEpoch.values.removeIf { it <= oldest }
    }

    /** Liczba śledzonych usunięć (diagnostyka testów: stan nie rośnie bez końca). */
    internal fun trackedDeletions(): Int = synchronized(lock) { deletedAtEpoch.size }

    private fun playerExistsUnsafe(player: String): Boolean =
        connection.prepareStatement("SELECT 1 FROM players WHERE player_id = ?").use { st ->
            st.setString(1, player)
            st.executeQuery().use { it.next() }
        }

    fun publicIdFor(player: String): String? = synchronized(lock) {
        connection.prepareStatement("SELECT public_id FROM players WHERE player_id = ?").use {
            it.setString(1, player)
            it.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }
    }

    /** Usuwanie przez API wymaga klucza przypisanego do UUID. */
    fun deleteOwnedPlayer(player: String, hash: String, nowMillis: Long = System.currentTimeMillis()): Boolean =
        synchronized(lock) {
            if (!hasCredentialUnsafe(player, hash, allowMissing = true, allowLegacy = false)) return@synchronized false
            deletePlayer(player, SOURCE_API, nowMillis)
            true
        }

    /**
     * Usuwa rekordy i pseudonim gracza (blokada zostaje); brak gracza nie jest błędem.
     * Gdy coś usunięto, zapisuje skrót identyfikatora w `deletion_log` (do powtórzenia po
     * odtworzeniu kopii, `admin replay-deletions`). Zwraca, czy gracz miał dane.
     */
    fun deletePlayer(
        player: String, source: String = SOURCE_API, nowMillis: Long = System.currentTimeMillis(),
    ): Boolean = synchronized(lock) {
        transaction {
            val existed = deletePlayerRowsUnsafe(player)
            // Blokady nie kasujemy: klucz HMAC jest w APK, więc usunięcie konta nie może
            // posłużyć zablokowanemu graczowi do zdjęcia blokady. Zdejmuje ją tylko moderator.
            if (existed) logDeletionUnsafe(player, source, Math.floorDiv(nowMillis, 1000L))
            existed
        }.also { markDeletedUnsafe(player) }
    }

    /**
     * Usunięcie przez operatora (prośba e-mailowa), bez sekretu instalacji: dostępne wyłącznie
     * z CLI na serwerze. Domyślnie zdejmuje też blokadę, aby nie zostawiać UUID w bazie.
     */
    fun deletePlayerByOperator(
        player: String, keepBlock: Boolean, nowMillis: Long = System.currentTimeMillis(),
    ): OperatorDeletion = synchronized(lock) {
        transaction {
            val existed = deletePlayerRowsUnsafe(player)
            val blockRemoved = !keepBlock && connection.prepareStatement(
                "DELETE FROM blocked_players WHERE player_id = ?"
            ).use {
                it.setString(1, player)
                it.executeUpdate() > 0
            }
            if (existed || blockRemoved) logDeletionUnsafe(player, SOURCE_OPERATOR, Math.floorDiv(nowMillis, 1000L))
            OperatorDeletion(existed || blockRemoved, blockRemoved)
        }.also { markDeletedUnsafe(player) }
    }

    /** Usuwa zgłoszenia, wyniki i wiersz gracza w bieżącej transakcji; zwraca, czy coś było. */
    private fun deletePlayerRowsUnsafe(player: String): Boolean {
        var changed = 0
        for (sql in listOf(
            "DELETE FROM nick_reports WHERE player_id = ?",
            "DELETE FROM scores WHERE player_id = ?",
            "DELETE FROM players WHERE player_id = ?",
        )) {
            changed += connection.prepareStatement(sql).use {
                it.setString(1, player)
                it.executeUpdate()
            }
        }
        return changed > 0
    }

    /** Nowa epoka unieważnia bilety wydane przed usunięciem (zgłoszenia w toku). Po commit. */
    private fun markDeletedUnsafe(player: String) {
        deletionEpoch++
        if (activeClaims.isNotEmpty()) deletedAtEpoch[player] = deletionEpoch
    }

    private fun logDeletionUnsafe(player: String, source: String, atSeconds: Long) {
        connection.prepareStatement(
            "INSERT INTO deletion_log(player_id_hash, deleted_at, source) VALUES (?, ?, ?)"
        ).use {
            it.setString(1, subjectHash(player))
            it.setLong(2, atSeconds)
            it.setString(3, source)
            it.executeUpdate()
        }
    }

    /**
     * Skrót HMAC (klucz `CW_PUBLIC_ID_KEY`) identyfikatora do dzienników. Osobna domena
     * sprawia, że skrót nie jest równy publicznemu ID widocznemu w rankingu.
     */
    fun subjectHash(player: String): String = publicIds.of("subject:$player")

    /** Transakcja na wspólnym połączeniu; wołać wewnątrz synchronized(lock). */
    private inline fun <T> transaction(block: () -> T): T {
        connection.autoCommit = false
        try {
            val result = block()
            connection.commit()
            return result
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

    /** Liczba wszystkich zapisanych wyników, także ukrytych przez moderację. */
    fun countScores(): Long = synchronized(lock) {
        connection.createStatement().use { s ->
            s.executeQuery("SELECT COUNT(*) FROM scores").use { rs ->
                check(rs.next())
                rs.getLong(1)
            }
        }
    }

    /** Spójna kopia online na osobnym połączeniu; nie trzyma blokady API. */
    fun backupTo(destination: Path): Path = DatabaseMaintenance.backup(jdbcUrl, destination)

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

    /**
     * Atomowy dzienny limit UTC dla sekretu instalacji i adresu IP.
     * Liczymy próby z poprawnym podpisem przed kosztowną weryfikacją, także błędne powtórki.
     */
    fun consumeScoreSubmissionQuota(
        installationHash: String, ip: String, nowMillis: Long,
        installationLimit: Int, ipLimit: Int,
    ): Boolean = synchronized(lock) {
        require(installationLimit > 0 && ipLimit > 0)
        val day = todayUtc(nowMillis)
        connection.autoCommit = false
        try {
            connection.prepareStatement("DELETE FROM score_submit_quotas WHERE day < ?").use {
                it.setLong(1, day)
                it.executeUpdate()
            }
            fun consume(kind: String, key: String, limit: Int): Boolean =
                connection.prepareStatement("""
                    INSERT INTO score_submit_quotas(day, quota_kind, quota_key, requests)
                    VALUES (?, ?, ?, 1)
                    ON CONFLICT(day, quota_kind, quota_key) DO UPDATE SET
                        requests = requests + 1
                    WHERE requests < ?
                """.trimIndent()).use {
                    it.setLong(1, day)
                    it.setString(2, kind)
                    it.setString(3, key)
                    it.setInt(4, limit)
                    it.executeUpdate() == 1
                }
            // Nie zapisujemy adresu IP: tylko dzienny HMAC kluczem prywatnym serwera.
            val ipKey = publicIds.of("quota-ip:$day:$ip")
            val allowed = consume("installation", installationHash, installationLimit) &&
                consume("ip", ipKey, ipLimit)
            if (allowed) connection.commit() else connection.rollback()
            allowed
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }

    /**
     * Zwraca jednostkę dziennego limitu pobraną przez [consumeScoreSubmissionQuota],
     * gdy serwer nie zweryfikował zgłoszenia z własnej winy (pełna kolejka, budżet).
     * Dzień liczymy od chwili przyjęcia; po północy UTC wiersz jest już nieaktualny.
     */
    fun refundScoreSubmissionQuota(installationHash: String, ip: String, receivedAtMillis: Long) =
        synchronized(lock) {
            val day = todayUtc(receivedAtMillis)
            val ipKey = publicIds.of("quota-ip:$day:$ip")
            connection.autoCommit = false
            try {
                for ((kind, key) in listOf("installation" to installationHash, "ip" to ipKey)) {
                    // Schemat wymaga requests > 0: ostatnią jednostkę zwracamy, usuwając wiersz.
                    for (sql in listOf(
                        "DELETE FROM score_submit_quotas " +
                            "WHERE day = ? AND quota_kind = ? AND quota_key = ? AND requests = 1",
                        "UPDATE score_submit_quotas SET requests = requests - 1 " +
                            "WHERE day = ? AND quota_kind = ? AND quota_key = ? AND requests > 1",
                    )) {
                        val changed = connection.prepareStatement(sql).use {
                            it.setLong(1, day)
                            it.setString(2, kind)
                            it.setString(3, key)
                            it.executeUpdate()
                        }
                        if (changed > 0) break
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

    fun save(
        input: ScoreSubmission,
        replay: VerifiedReplay,
        nowMillis: Long,
        replayHash: String,
        installationSecretHash: String? = null,
        claim: ScoreClaim? = null,
    ): SavedScore = synchronized(lock) {
        // Daily zachowuje datę wyzwania, także przy wysyłce po północy UTC.
        val day = if (replay.board == "daily") requireNotNull(input.day) else todayUtc(nowMillis)
        val nowSeconds = Math.floorDiv(nowMillis, 1000L)
        connection.autoCommit = false
        try {
            // Sprawdzamy ponownie po weryfikacji, aby uniknąć wyścigu z CLI.
            if (isBlockedUnsafe(input.player)) throw BlockedPlayerException()
            if (claim != null) {
                // Ta sama blokada i transakcja co deletePlayer: sprawdzenie i zapis są atomowe.
                check(!claim.closed && claim.player == input.player &&
                    claim.ownerHash == installationSecretHash) { "invalid_claim" }
                if ((deletedAtEpoch[input.player] ?: Long.MIN_VALUE) > claim.epoch) throw PlayerDeletedException()
                // Właściciel istniał przy sprawdzeniu własności, a teraz go nie ma: usunięty.
                if (claim.ownerExisted && !playerExistsUnsafe(input.player)) throw PlayerDeletedException()
            }
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
        // Dla daily filtr opiera się na dniu wyzwania, nie sekundzie odbioru.
        val boundary = if (board == "daily") Math.floorDiv(start, 86_400L) else start
        val timeColumn = if (board == "daily") "day" else "verified_at"
        val sql = """
            WITH best AS (
                SELECT player_id, MAX(score) AS score
                FROM scores
                WHERE board=? AND $timeColumn>=?
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
            it.setLong(2, boundary)
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

    /** Ostatnie zgłoszenia; [unresolvedOnly] pomija rozpatrzone (`admin resolve-report`). */
    fun recentReports(limit: Int = 50, unresolvedOnly: Boolean = false): List<NickReportRow> = synchronized(lock) {
        require(limit in 1..200) { "invalid_limit" }
        val filter = if (unresolvedOnly) "WHERE resolved_at IS NULL" else ""
        connection.prepareStatement("""
            SELECT report_id, player_id, public_id, reported_nick, reason, reported_at, resolved_at
            FROM nick_reports $filter ORDER BY reported_at DESC, report_id DESC LIMIT ?
        """.trimIndent()).use { st ->
            st.setInt(1, limit)
            st.executeQuery().use { rs ->
                val rows = ArrayList<NickReportRow>()
                while (rs.next()) {
                    val base = NickReportRow(
                        rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getLong(6),
                    )
                    val resolved = rs.getLong(7)
                    rows.add(if (rs.wasNull()) base else base.copy(resolvedAt = resolved))
                }
                rows
            }
        }
    }

    /** null: brak zgłoszenia; false: już rozpatrzone; true: oznaczone teraz. */
    fun resolveReport(id: Long, nowMillis: Long = System.currentTimeMillis()): Boolean? = synchronized(lock) {
        val updated = connection.prepareStatement(
            "UPDATE nick_reports SET resolved_at = ? WHERE report_id = ? AND resolved_at IS NULL"
        ).use {
            it.setLong(1, Math.floorDiv(nowMillis, 1000L))
            it.setLong(2, id)
            it.executeUpdate() > 0
        }
        if (updated) return@synchronized true
        val exists = connection.prepareStatement("SELECT 1 FROM nick_reports WHERE report_id = ?").use {
            it.setLong(1, id)
            it.executeQuery().use { rs -> rs.next() }
        }
        if (exists) false else null
    }

    /**
     * Gracze, których nick zawiera [query] bez względu na wielkość liter, także po
     * normalizacji [NickModeration.normalize] (diakrytyki, cyfry zamiast liter).
     * Porównuje nick zapisany i nick pokazywany w rankingu (po filtrze).
     */
    fun findPlayersByNick(query: String, limit: Int = 50): List<PlayerMatch> = synchronized(lock) {
        require(query.isNotBlank()) { "empty_query" }
        val lower = query.trim().lowercase(Locale.ROOT)
        val normalized = NickModeration.normalize(query).replace(" ", "")
        val result = ArrayList<PlayerMatch>()
        connection.createStatement().use { st ->
            st.executeQuery("""
                SELECT p.player_id, p.public_id, p.nick,
                       (SELECT COUNT(*) FROM scores s WHERE s.player_id = p.player_id),
                       MAX(p.updated_at, COALESCE((SELECT MAX(verified_at) FROM scores s
                                                   WHERE s.player_id = p.player_id), 0))
                FROM players p ORDER BY p.updated_at DESC, p.player_id ASC
            """.trimIndent()).use { rs ->
                while (rs.next() && result.size < limit) {
                    val player = rs.getString(1)
                    val stored = rs.getString(3)
                    val shown = NickModeration.publicNick(stored, player)
                    val match = listOf(stored, shown).any { nick ->
                        nick.lowercase(Locale.ROOT).contains(lower) ||
                            (normalized.isNotEmpty() &&
                                NickModeration.normalize(nick).replace(" ", "").contains(normalized))
                    }
                    if (match) result.add(PlayerMatch(player, rs.getString(2), shown, rs.getInt(4), rs.getLong(5)))
                }
            }
        }
        result
    }

    /** Wpisy dziennika usunięć od [sinceSeconds] (włącznie). */
    fun deletionLogSince(sinceSeconds: Long): List<DeletionLogEntry> = synchronized(lock) {
        readDeletionLog(connection, sinceSeconds)
    }

    /**
     * Powtarza usunięcia z dziennika (np. z nowszej bazy po odtworzeniu starszej kopii).
     * Porównuje skróty [subjectHash] wszystkich znanych UUID; brakujące wpisy dopisuje do
     * dziennika tej bazy. Blokady zostają jak w odtworzonej kopii. Zwraca liczbę usuniętych graczy.
     */
    fun replayDeletions(entries: List<DeletionLogEntry>): Int = synchronized(lock) {
        if (entries.isEmpty()) return@synchronized 0
        val wanted = entries.map { it.playerIdHash }.toSet()
        val known = LinkedHashSet<String>()
        connection.createStatement().use { st ->
            st.executeQuery(
                "SELECT player_id FROM players UNION SELECT player_id FROM scores " +
                    "UNION SELECT player_id FROM nick_reports"
            ).use { rs -> while (rs.next()) known.add(rs.getString(1)) }
        }
        val targets = known.filter { subjectHash(it) in wanted }
        transaction {
            for (player in targets) deletePlayerRowsUnsafe(player)
            connection.prepareStatement("""
                INSERT INTO deletion_log(player_id_hash, deleted_at, source)
                SELECT ?, ?, ? WHERE NOT EXISTS (
                    SELECT 1 FROM deletion_log WHERE player_id_hash = ? AND deleted_at = ? AND source = ?
                )
            """.trimIndent()).use {
                for (entry in entries) {
                    for (offset in listOf(0, 3)) {
                        it.setString(1 + offset, entry.playerIdHash)
                        it.setLong(2 + offset, entry.deletedAt)
                        it.setString(3 + offset, entry.source)
                    }
                    it.executeUpdate()
                }
            }
        }
        targets.forEach { markDeletedUnsafe(it) }
        targets.size
    }

    /**
     * Retencja (R5, luka L1) w jednej transakcji: tablice dnia i tygodnia starsze niż
     * [RetentionPolicy.periodDays], zgłoszenia, dzienniki, nieaktywni gracze w całości
     * (z wpisem w `deletion_log`), stare limity dzienne i zużyte `nonce`. Tablice stałe
     * (`endless`, `skirmish`, `campaign_*`) znikają tylko razem z nieaktywnym graczem.
     * [dryRun] wykonuje te same polecenia i wycofuje transakcję, więc liczby są dokładne.
     */
    fun purgeExpired(nowMillis: Long, policy: RetentionPolicy, dryRun: Boolean = false): PurgeResult =
        synchronized(lock) {
            val nowSeconds = Math.floorDiv(nowMillis, 1000L)
            val today = todayUtc(nowMillis)
            fun cutoffSeconds(days: Int) = nowSeconds - days * 86_400L
            fun update(sql: String, value: Any): Int = connection.prepareStatement(sql).use { st ->
                when (value) {
                    is Long -> st.setLong(1, value)
                    is String -> st.setString(1, value)
                    else -> error("unsupported_parameter")
                }
                st.executeUpdate()
            }
            val deletedPlayers = ArrayList<String>()
            connection.autoCommit = false
            try {
                var daily = 0
                var weekly = 0
                if (policy.periodDays > 0) {
                    val cutoffDay = today - policy.periodDays
                    daily = update("DELETE FROM scores WHERE board = 'daily' AND day < ?", cutoffDay)
                    // Tydzień jest przeterminowany, gdy jego ostatni dzień < cutoffDay, czyli gdy jego
                    // numer jest mniejszy niż numer tygodnia zawierającego cutoffDay (wzór z :sim).
                    val firstKeptWeek = Weekly.weekNumber(cutoffDay)
                    val boards = ArrayList<String>()
                    connection.createStatement().use { st ->
                        st.executeQuery("SELECT DISTINCT board FROM scores WHERE substr(board, 1, 7) = 'weekly_'")
                            .use { rs -> while (rs.next()) boards.add(rs.getString(1)) }
                    }
                    for (board in boards) {
                        val week = board.removePrefix("weekly_").toLongOrNull() ?: continue
                        if (week < firstKeptWeek) weekly += update("DELETE FROM scores WHERE board = ?", board)
                    }
                }
                val reports = if (policy.reportDays > 0) {
                    update("DELETE FROM nick_reports WHERE reported_at < ?", cutoffSeconds(policy.reportDays))
                } else 0
                var expiredBlocks = 0
                if (policy.inactiveDays > 0) {
                    val cutoff = cutoffSeconds(policy.inactiveDays)
                    connection.prepareStatement("""
                        SELECT p.player_id FROM players p
                        WHERE p.updated_at < ?
                          AND COALESCE((SELECT MAX(verified_at) FROM scores s WHERE s.player_id = p.player_id), 0) < ?
                        ORDER BY p.player_id
                    """.trimIndent()).use { st ->
                        st.setLong(1, cutoff)
                        st.setLong(2, cutoff)
                        st.executeQuery().use { rs -> while (rs.next()) deletedPlayers.add(rs.getString(1)) }
                    }
                    for (player in deletedPlayers) {
                        deletePlayerRowsUnsafe(player)
                        logDeletionUnsafe(player, SOURCE_RETENTION, nowSeconds)
                    }
                    // Blokada bez konta, starsza niż okres nieaktywności, wygasa: nie trzymamy UUID bez końca.
                    expiredBlocks = update("""
                        DELETE FROM blocked_players WHERE blocked_at < ?
                          AND NOT EXISTS (SELECT 1 FROM players p WHERE p.player_id = blocked_players.player_id)
                    """.trimIndent(), cutoff)
                }
                var deletionLog = 0
                var adminLog = 0
                if (policy.logDays > 0) {
                    deletionLog = update("DELETE FROM deletion_log WHERE deleted_at < ?", cutoffSeconds(policy.logDays))
                    adminLog = update("DELETE FROM admin_log WHERE at < ?", cutoffSeconds(policy.logDays))
                }
                // Skróty IP do dziennego limitu znikają następnego dnia także bez nowych zgłoszeń.
                val quotas = update("DELETE FROM score_submit_quotas WHERE day < ?", today)
                val nonces = update("DELETE FROM used_nonces WHERE expires_at <= ?", nowSeconds)
                val result = PurgeResult(
                    dailyScores = daily, weeklyScores = weekly, nickReports = reports,
                    inactivePlayers = deletedPlayers.size, expiredBlocks = expiredBlocks,
                    deletionLog = deletionLog, adminLog = adminLog, quotas = quotas, nonces = nonces,
                    dryRun = dryRun,
                )
                if (dryRun) connection.rollback() else connection.commit()
                if (!dryRun) deletedPlayers.forEach { markDeletedUnsafe(it) }
                result
            } catch (e: Exception) {
                connection.rollback()
                throw e
            } finally {
                connection.autoCommit = true
            }
        }

    /** Dziennik działań operatora; [player] zapisywany wyłącznie jako skrót [subjectHash]. */
    fun logAdmin(action: String, player: String?, detail: String, nowMillis: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            connection.prepareStatement(
                "INSERT INTO admin_log(at, action, target_hash, detail) VALUES (?, ?, ?, ?)"
            ).use {
                it.setLong(1, Math.floorDiv(nowMillis, 1000L))
                it.setString(2, action)
                it.setString(3, player?.let(::subjectHash))
                it.setString(4, detail)
                it.executeUpdate()
            }
        }
    }

    fun recentAdminLog(limit: Int = 20): List<AdminLogRow> = synchronized(lock) {
        require(limit in 1..200) { "invalid_limit" }
        connection.prepareStatement(
            "SELECT at, action, target_hash, detail FROM admin_log ORDER BY at DESC, rowid DESC LIMIT ?"
        ).use { st ->
            st.setInt(1, limit)
            st.executeQuery().use { rs ->
                val rows = ArrayList<AdminLogRow>()
                while (rs.next()) rows.add(AdminLogRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)))
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
        /** Wersja schematu tej wersji serwera; starszy serwer odmówi otwarcia nowszej bazy. */
        const val SCHEMA_VERSION = 3
        const val SOURCE_API = "api"
        const val SOURCE_OPERATOR = "operator"
        const val SOURCE_RETENTION = "retention"

        /** Odczyt `deletion_log` z dowolnego połączenia (bieżąca baza albo inna kopia). */
        internal fun readDeletionLog(connection: Connection, sinceSeconds: Long): List<DeletionLogEntry> =
            connection.prepareStatement(
                "SELECT player_id_hash, deleted_at, source FROM deletion_log WHERE deleted_at >= ? " +
                    "ORDER BY deleted_at, rowid"
            ).use { st ->
                st.setLong(1, sinceSeconds)
                st.executeQuery().use { rs ->
                    val rows = ArrayList<DeletionLogEntry>()
                    while (rs.next()) rows.add(DeletionLogEntry(rs.getString(1), rs.getLong(2), rs.getString(3)))
                    rows
                }
            }

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
