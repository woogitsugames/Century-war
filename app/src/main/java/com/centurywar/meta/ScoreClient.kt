package com.centurywar.meta

import com.centurywar.game.BattleConfig
import com.centurywar.game.Mode
import com.centurywar.game.Replay
import com.centurywar.game.Research
import com.centurywar.game.SimVersion
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

enum class ScoreSendStatus { DISABLED, QUEUED, SENDING, SENT, REJECTED, OFFLINE, INVALID_NICK, REPLAY_TOO_LONG }

data class GlobalEntry(val rank: Long, val player: String, val nick: String, val score: Long) {
    val rankLabel = rank.toString() + "."
    val scoreLabel = score.toString()
}
data class GlobalRankingView(
    val top: List<GlobalEntry> = emptyList(),
    val around: List<GlobalEntry> = emptyList(),
    val loading: Boolean = false,
    val error: Boolean = false,
    val updatedAt: Long = 0L,
    val selfPublicId: String = "",
)

/**
 * Cały HTTPS, JSON, kodowanie powtórki i obsługa pliku działają na jednym wątku.
 * Metody publiczne nie wykonują I/O. Klucz HMAC w APK nie jest sekretem.
 */
class ScoreClient(
    private val queueFile: File,
    baseUrl: String,
    secret: String,
    private val profile: Profile,
) {
    private val origin = baseUrl.trimEnd('/')
    private val key = secret.toByteArray(StandardCharsets.UTF_8)
    private val worker = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "score-http").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
    }
    private val queue = ScoreQueue()
    private val deletions = PendingDeletions(File(queueFile.path + ".deletions"))
    private var deletionsReady = false
    private var deleteAttempts = 0
    @Volatile private var deletingPlayerId: String? = null
    private val cache = ConcurrentHashMap<String, GlobalRankingView>()
    private val refreshing = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var enabled = profile.globalRanking
    @Volatile var status = if (enabled) ScoreSendStatus.QUEUED else ScoreSendStatus.DISABLED
        private set
    private var generation = 0L
    private var sending = false

    init {
        require(configured(origin, secret)) { "invalid_score_configuration" }
        worker.execute {
            deletionsReady = deletions.load()
            if (!deletionsReady) {
                status = ScoreSendStatus.OFFLINE
                return@execute
            }
            val scoresReady = if (enabled) {
                loadQueue()
            } else {
                queue.clear()
                persistQueue()
            }
            if (scoresReady && enabled) pump()
            if (scoresReady) pumpDeletions()
        }
    }

    /** Wywołanie po zmianie zgody; przy wyłączeniu nie ma już nowych żądań. */
    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) status = ScoreSendStatus.DISABLED
        worker.execute {
            generation++
            sending = false
            if (!value) {
                queue.clear()
                persistQueue()
            } else {
                pump()
            }
        }
    }

    /** Zmiana identyfikatora usuwa niewysłane zgłoszenia starego konta. */
    fun clearQueue() {
        worker.execute {
            generation++
            sending = false
            queue.clear()
            persistQueue()
            status = if (enabled) ScoreSendStatus.SENT else ScoreSendStatus.DISABLED
        }
    }

    /**
     * callback(true) oznacza zapisanie żądania na dysku, nie potwierdzenie usunięcia
     * przez serwer. Callback działa na wątku roboczym; zmiany UI przekaż przez Game.post.
     */
    fun deletePlayerData(playerId: String, callback: (Boolean) -> Unit) {
        if (!validPlayerId(playerId)) {
            callback(false)
            return
        }
        val installationSecret = profile.playerSecret
        if (!validInstallationSecret(installationSecret)) {
            callback(false)
            return
        }
        deletingPlayerId = playerId
        worker.execute {
            if (!deletionsReady) deletionsReady = deletions.load()
            if (!deletionsReady || !deletions.add(playerId, installationSecret)) {
                deletingPlayerId = null
                callback(false)
                return@execute
            }
            // Nie pozwalamy ponowić wysyłki zapisów ze starego identyfikatora.
            generation++
            sending = false
            queue.clear()
            if (!persistQueue()) {
                callback(false)
                return@execute
            }
            cache.clear()
            callback(true)
            pumpDeletions()
        }
    }

    fun submit(cfg: BattleConfig, log: Replay.Log, score: Long, rankedDaily: Boolean, nick: String,
               playerId: String, research: IntArray, difficulty: Int) {
        if (!enabled || !profile.globalRanking || playerId.isEmpty() || score <= 0L ||
            playerId == deletingPlayerId) return
        if (cfg.mode == Mode.DAILY && !rankedDaily) return
        // Samouczek nie jest wynikiem rankingowym, również w etapie pierwszym.
        if (cfg.tutorial) return
        if (log.steps > Replay.MAX_STEPS || log.steps < 0) {
            status = ScoreSendStatus.REPLAY_TOO_LONG
            return
        }
        if (!validNick(nick)) {
            status = ScoreSendStatus.INVALID_NICK
            return
        }
        // Snapshot liczb jest mały; zapis pełnej powtórki zlecamy wątkowi roboczemu.
        val weekly = cfg.mode == Mode.ENDLESS && cfg.weekly >= 0L
        // Daily i oblężenie tygodnia mają zamrożone reguły bez bonusów z badań.
        val levels = if (cfg.mode == Mode.DAILY || weekly) IntArray(Research.defs.size) else research.copyOf()
        status = ScoreSendStatus.QUEUED
        worker.execute {
            if (!enabled || !profile.globalRanking) return@execute
            try {
                val payload = ScoreWire.submission(cfg, log, score, nick, playerId, levels, difficulty)
                queue.add(ScoreQueue.Entry(System.currentTimeMillis(), payload), System.currentTimeMillis())
                persistQueue()
                pump()
            } catch (_: Exception) {
                status = ScoreSendStatus.REJECTED
            }
        }
    }

    fun view(board: String, period: String, player: String): GlobalRankingView? =
        cache[cacheKey(board, period, player)]

    /** Wywoływać przy wejściu na ekran lub zmianie filtra. Wynik zostaje w cache. */
    fun refresh(board: String, period: String, player: String) {
        if (!enabled || !profile.globalRanking || !validBoard(board) || period !in PERIODS) return
        val cacheKey = cacheKey(board, period, player)
        val previous = cache[cacheKey]
        if (previous != null && System.currentTimeMillis() - previous.updatedAt < CACHE_MS) return
        if (!refreshing.add(cacheKey)) return
        cache[cacheKey] = (previous ?: GlobalRankingView()).copy(loading = true, error = false)
        worker.execute {
            try {
                if (!enabled || !profile.globalRanking) return@execute
                val topUri = "/v1/top?board=" + board + "&period=" + period
                val top = parseEntries(get(topUri))
                // Stary gracz bez powiązanego sekretu może nadal czytać publiczny TOP.
                val aroundJson = if (validPlayerId(player)) runCatching {
                    JSONObject(get("/v1/around?board=" + board + "&period=" + period + "&player=" + player))
                }.getOrNull() else null
                val around = if (aroundJson != null) parseEntries(aroundJson) else emptyList()
                val self = aroundJson?.optString("self", "") ?: ""
                if (enabled) cache[cacheKey] = GlobalRankingView(top, around,
                    updatedAt = System.currentTimeMillis(), selfPublicId = self)
            } catch (_: Exception) {
                val old = cache[cacheKey] ?: GlobalRankingView()
                cache[cacheKey] = old.copy(loading = false, error = true, updatedAt = System.currentTimeMillis())
            } finally {
                refreshing.remove(cacheKey)
            }
        }
    }

    /** Publiczny identyfikator z rankingu, bez UUID ofiary. */
    fun reportNick(publicId: String, callback: (Boolean) -> Unit) {
        if (!enabled || !profile.globalRanking || !validPublicId(publicId)) {
            callback(false)
            return
        }
        worker.execute {
            val accepted = try {
                request("POST", "/v1/report",
                    JSONObject().put("publicId", publicId).put("reason", "offensive").toString()
                ).first == 202
            } catch (_: Exception) { false }
            callback(accepted)
        }
    }

    fun close() = worker.shutdown()

    /** Ponawianie działa również po wyłączeniu rankingu i po restarcie aplikacji. */
    private fun pumpDeletions() {
        if (!deletionsReady) return
        val deletion = deletions.first() ?: return
        val code = try {
            request("POST", "/v1/player/delete", JSONObject().put("player", deletion.player).toString(),
                deletion.secret).first
        } catch (_: Exception) {
            -1
        }
        if (code in 200..299 && deletions.remove(deletion.player)) {
            deleteAttempts = 0
            worker.execute { pumpDeletions() }
        } else {
            deleteAttempts++
            val delay = if (code in 400..499 && code != 408 && code != 429) {
                3_600_000L
            } else {
                (2_000L * (1L shl deleteAttempts.coerceIn(0, 11))).coerceAtMost(3_600_000L)
            }
            worker.schedule({ pumpDeletions() }, delay, TimeUnit.MILLISECONDS)
        }
    }

    private fun pump() {
        if (sending || !enabled || !profile.globalRanking) return
        sending = true
        sendNext(generation)
    }

    private fun sendNext(token: Long) {
        // Opóźniona próba starej generacji nie może wyzerować stanu nowej.
        if (token != generation) return
        if (!enabled || !profile.globalRanking) {
            sending = false
            return
        }
        val before = queue.entries.size
        queue.prune(System.currentTimeMillis())
        if (queue.entries.size != before) persistQueue()
        val item = queue.entries.firstOrNull()
        if (item == null) {
            sending = false
            return
        }
        status = ScoreSendStatus.SENDING
        val code = try {
            request("POST", "/v1/score", item.payload).first
        } catch (_: IOException) {
            -1
        } catch (_: Exception) {
            -1
        }
        if (token != generation) return
        if (!enabled || !profile.globalRanking) {
            sending = false
            return
        }
        val retryDelay = queue.recordResponse(code)
        persistQueue()
        if (retryDelay != null) {
            status = ScoreSendStatus.OFFLINE
            worker.schedule({ sendNext(token) }, retryDelay, TimeUnit.MILLISECONDS)
            return
        }
        status = if (code in 200..299) ScoreSendStatus.SENT else ScoreSendStatus.REJECTED
        worker.execute { sendNext(token) }
    }

    private fun get(target: String): String {
        val (status, response) = request("GET", target, "")
        if (status != 200) throw IOException("HTTP " + status)
        return response
    }

    private fun request(method: String, target: String, body: String,
                        deletionSecret: String? = null): Pair<Int, String> {
        val requiresIdentity = target == "/v1/score" || target == "/v1/player/delete" ||
            target.startsWith("/v1/around?")
        val installationSecret = if (target == "/v1/player/delete") deletionSecret
            else if (requiresIdentity) profile.playerSecret else null
        if (requiresIdentity) {
            if (!validInstallationSecret(installationSecret)) throw IOException("missing_player_secret")
            if (target != "/v1/player/delete" && !profile.awaitWrites(10_000L))
                throw IOException("profile_not_saved")
        }
        val timestamp = (System.currentTimeMillis() / 1000L).toString()
        val nonce = UUID.randomUUID().toString()
        val signed = ScoreWire.headers(key, timestamp, nonce, method, target, body, installationSecret)
        val conn = URL(origin + target).openConnection() as? HttpsURLConnection
            ?: throw IOException("https_required")
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.requestMethod = method
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("X-CW-Timestamp", signed.timestamp)
            conn.setRequestProperty("X-CW-Nonce", signed.nonce)
            if (signed.installationSecret != null)
                conn.setRequestProperty("X-CW-Player-Secret", signed.installationSecret)
            conn.setRequestProperty("X-CW-Signature", signed.signature)
            if (method == "POST") {
                val bytes = body.toByteArray(StandardCharsets.UTF_8)
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setFixedLengthStreamingMode(bytes.size)
                conn.outputStream.use { it.write(bytes) }
            }
            val code = conn.responseCode
            val result = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(StandardCharsets.UTF_8)?.use { reader ->
                    // Limit bufora odpowiedzi przed dekodowaniem JSON (wątek I/O).
                    val out = StringBuilder()
                    val buffer = CharArray(4096)
                    while (out.length < 1_048_576) {
                        val count = reader.read(buffer, 0, minOf(buffer.size, 1_048_576 - out.length))
                        if (count <= 0) break
                        out.append(buffer, 0, count)
                    }
                    out.toString()
                } ?: ""
            return code to result
        } finally {
            conn.disconnect()
        }
    }

    private fun parseEntries(response: String): List<GlobalEntry> = parseEntries(JSONObject(response))

    private fun parseEntries(response: JSONObject): List<GlobalEntry> {
        val array = response.getJSONArray("entries")
        return (0 until minOf(array.length(), 50)).map { i ->
            val entry = array.getJSONObject(i)
            GlobalEntry(entry.getLong("rank"), entry.getString("player"),
                entry.getString("nick"), entry.getLong("score"))
        }
    }

    private fun loadQueue(): Boolean {
        queue.clear()
        try {
            if (queueFile.exists()) {
                val array = JSONArray(queueFile.readText(StandardCharsets.UTF_8))
                for (i in 0 until minOf(array.length(), ScoreQueue.MAX_ENTRIES)) {
                    val entry = array.getJSONObject(i)
                    val payload = entry.getString("payload")
                    if (!deletions.contains(JSONObject(payload).optString("player"))) {
                        queue.add(
                            ScoreQueue.Entry(entry.getLong("created"), payload,
                                entry.optInt("attempts", 0).coerceIn(0, 100_000)),
                            System.currentTimeMillis(),
                        )
                    }
                }
            }
        } catch (_: Exception) {
            queue.clear()
        }
        return persistQueue()
    }

    private fun persistQueue(): Boolean {
        try {
            if (queue.entries.isEmpty()) {
                return !queueFile.exists() || queueFile.delete()
            }
            queueFile.parentFile?.mkdirs()
            val json = JSONArray()
            for (entry in queue.entries) {
                json.put(JSONObject().put("created", entry.createdAt)
                    .put("payload", entry.payload).put("attempts", entry.attempts))
            }
            val tmp = File(queueFile.parentFile, queueFile.name + ".tmp")
            tmp.writeText(json.toString(), StandardCharsets.UTF_8)
            if (!tmp.renameTo(queueFile)) {
                queueFile.delete()
                if (!tmp.renameTo(queueFile)) throw IOException("queue_rename_failed")
            }
            return true
        } catch (_: IOException) {
            status = ScoreSendStatus.OFFLINE
            return false
        }
    }

    companion object {
        const val GAME_VERSION = SimVersion.PROTOCOL
        private const val CACHE_MS = 120_000L
        private val PERIODS = setOf("day", "week", "all")
        private val NICK_REGEX = Regex("^[\\p{L}\\p{N} ._-]{1,24}$")
        private val INSTALL_SECRET_REGEX = Regex("[0-9a-f]{64}")
        private val PUBLIC_ID_REGEX = Regex("[0-9a-f]{24}")

        fun validInstallationSecret(secret: String?): Boolean =
            secret != null && INSTALL_SECRET_REGEX.matches(secret)
        fun validPublicId(value: String): Boolean = PUBLIC_ID_REGEX.matches(value)

        fun validNick(nick: String): Boolean =
            nick.length in 1..24 && nick == nick.trim() && NICK_REGEX.matches(nick)

        private fun validPlayerId(player: String): Boolean = runCatching {
            val uuid = UUID.fromString(player)
            uuid.version() == 4 && uuid.variant() == 2 && uuid.toString() == player
        }.getOrDefault(false)

        private fun validBoard(board: String): Boolean =
            board in setOf("daily", "endless", "skirmish") ||
                Regex("^weekly_[0-9]{1,7}$").matches(board) ||
                Regex("^campaign_([0-9]|[12][0-9]|3[0-5])$").matches(board)

        private fun cacheKey(board: String, period: String, player: String) =
            board + ":" + period + ":" + player

        fun configured(baseUrl: String, secret: String): Boolean = try {
            val uri = URI(baseUrl)
            uri.scheme == "https" && !uri.host.isNullOrBlank() &&
                uri.userInfo == null && uri.query == null && uri.fragment == null &&
                (uri.path.isNullOrEmpty() || uri.path == "/") &&
                secret.toByteArray(StandardCharsets.UTF_8).size >= 32
        } catch (_: Exception) {
            false
        }

        /** Wektor testowy jest wspólny z Security.kt na serwerze. */
        fun sign(secret: ByteArray, timestamp: String, nonce: String, method: String,
                 target: String, body: String, installationSecret: String? = null): String =
            ScoreWire.sign(secret, timestamp, nonce, method, target, body, installationSecret)
    }
}
