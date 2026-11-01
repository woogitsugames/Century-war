package com.centurywar.server

import com.centurywar.game.SimVersion
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.request.receiveChannel
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import io.ktor.server.request.uri
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

private val json = Json { ignoreUnknownKeys = false; encodeDefaults = true }
private const val MAX_REQUEST_BYTES = 1_048_576
private const val MAX_DELETE_BYTES = 1_024
private const val MAX_REPORT_BYTES = 512

private suspend fun ApplicationCall.fail(status: HttpStatusCode, code: String) {
    respondText(json.encodeToString(ErrorResponse(code)), ContentType.Application.Json, status)
}

private fun sha256(body: String): String = MessageDigest.getInstance("SHA-256")
    .digest(body.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it.toInt() and 0xff) }

fun Application.leaderboardModule(
    store: SqliteStore,
    secret: ByteArray,
    nowMillis: () -> Long = System::currentTimeMillis,
    supportedVersion: String = SimVersion.PROTOCOL,
    trustedProxies: Set<String> = setOf("127.0.0.1"),
    limits: RateLimits = RateLimits(),
    verificationGate: VerificationGate = VerificationGate(),
    verifyReplay: (ScoreSubmission, Long, String, VerificationBudget) -> VerifiedReplay = ReplayVerifier::verify,
    metrics: ServerMetrics = ServerMetrics(),
    metricsToken: String? = null,
) {
    val auth = RequestAuthenticator(secret, store, nowMillis)
    val limiter = RateLimiter(nowMillis)
    val addresses = ClientAddress(trustedProxies)
    monitor.subscribe(ApplicationStopped) { verificationGate.close() }
    fun clientIp(call: ApplicationCall): String =
        addresses.resolve(call.request.local.remoteHost, call.request.headers["X-Forwarded-For"])

    suspend fun reportHealth(call: ApplicationCall) {
        val healthy = withContext(Dispatchers.IO) {
            runCatching { store.healthy() }.getOrDefault(false)
        }
        call.respondText(
            if (healthy) "ok\n" else "unavailable\n",
            ContentType.Text.Plain,
            if (healthy) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
        )
    }

    routing {
        get("/health") { reportHealth(call) }
        // Stary adres pozostaje dostępny dla istniejących instalacji.
        get("/healthz") { reportHealth(call) }

        if (metricsToken != null) {
            get("/metrics") {
                val provided = call.request.headers[HttpHeaders.Authorization] ?: ""
                val expected = "Bearer " + metricsToken
                if (!MessageDigest.isEqual(
                        provided.toByteArray(Charsets.UTF_8),
                        expected.toByteArray(Charsets.UTF_8),
                    )
                ) {
                    call.respondText("unauthorized\n", ContentType.Text.Plain, HttpStatusCode.Unauthorized)
                    return@get
                }
                val count = withContext(Dispatchers.IO) {
                    runCatching { store.countScores() }.getOrNull()
                }
                if (count == null) {
                    call.respondText("unavailable\n", ContentType.Text.Plain, HttpStatusCode.ServiceUnavailable)
                    return@get
                }
                call.respondText(json.encodeToString(metrics.snapshot(count)), ContentType.Application.Json)
            }
        }

        post("/v1/player/delete") {
            if (!limiter.allow("delete-ip:" + clientIp(call), limits.deleteIp)) {
                call.fail(HttpStatusCode.TooManyRequests, "rate_limited")
                return@post
            }
            val length = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            if (length != null && length > MAX_DELETE_BYTES) {
                call.fail(HttpStatusCode.PayloadTooLarge, "body_too_large")
                return@post
            }
            val raw = call.receiveChannel().readRemaining(MAX_DELETE_BYTES + 1L).readByteArray()
            if (raw.size > MAX_DELETE_BYTES) {
                call.fail(HttpStatusCode.PayloadTooLarge, "body_too_large")
                return@post
            }
            val body = raw.toString(Charsets.UTF_8)
            val installationSecret = call.request.headers["X-CW-Player-Secret"]
            if (!validInstallSecret(installationSecret) || !auth.valid(
                    "POST", call.request.uri, body,
                    call.request.headers["X-CW-Timestamp"],
                    call.request.headers["X-CW-Nonce"],
                    call.request.headers["X-CW-Signature"],
                    true,
                    installationSecret,
                )
            ) {
                call.fail(HttpStatusCode.Unauthorized, "invalid_signature_or_nonce")
                return@post
            }
            val input = runCatching { json.decodeFromString<DeletePlayerRequest>(body) }.getOrNull()
            if (input == null || !validPlayer(input.player)) {
                call.fail(HttpStatusCode.BadRequest, "invalid_player")
                return@post
            }
            val deleted = withContext(Dispatchers.IO) {
                store.deleteOwnedPlayer(input.player, installSecretHash(installationSecret!!))
            }
            if (!deleted) {
                call.fail(HttpStatusCode.Forbidden, "not_player_owner")
                return@post
            }
            limiter.forget("post-player:" + input.player)
            call.respondText(
                json.encodeToString(DeletePlayerReceipt(true)),
                ContentType.Application.Json,
                HttpStatusCode.OK,
            )
        }

        post("/v1/score") {
            val remote = clientIp(call)
            if (!limiter.allow("post-ip:" + remote, limits.postIp)) {
                call.fail(HttpStatusCode.TooManyRequests, "rate_limited")
                return@post
            }
            val declaredLength = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            if (declaredLength != null && declaredLength > MAX_REQUEST_BYTES) {
                call.fail(HttpStatusCode.PayloadTooLarge, "body_too_large")
                return@post
            }
            // Czytamy najwyżej MAX+1 bajtów: body bez Content-Length (chunked) też nie zapcha pamięci.
            val raw = call.receiveChannel().readRemaining(MAX_REQUEST_BYTES + 1L).readByteArray()
            if (raw.size > MAX_REQUEST_BYTES) {
                call.fail(HttpStatusCode.PayloadTooLarge, "body_too_large")
                return@post
            }
            val body = raw.toString(Charsets.UTF_8)
            val installationSecret = call.request.headers["X-CW-Player-Secret"]
            if (!validInstallSecret(installationSecret) || !auth.valid(
                    "POST", call.request.uri, body,
                    call.request.headers["X-CW-Timestamp"],
                    call.request.headers["X-CW-Nonce"],
                    call.request.headers["X-CW-Signature"],
                    true,
                    installationSecret,
                )
            ) {
                call.fail(HttpStatusCode.Unauthorized, "invalid_signature_or_nonce")
                return@post
            }

            val input = runCatching { json.decodeFromString<ScoreSubmission>(body) }.getOrNull()
            if (input == null || !validPlayer(input.player)) {
                call.fail(HttpStatusCode.BadRequest, "invalid_submission")
                return@post
            }
            if (input.gameVersion != supportedVersion) {
                call.fail(HttpStatusCode.BadRequest, "unsupported_version")
                return@post
            }
            val metadataError = runCatching { validateSubmission(input) }.exceptionOrNull()
            if (metadataError != null) {
                call.fail(HttpStatusCode.BadRequest, metadataError.message ?: "invalid_submission")
                return@post
            }
            if (store.isBlocked(input.player)) {
                call.fail(HttpStatusCode.Forbidden, "blocked_player")
                return@post
            }
            val ownerHash = installSecretHash(installationSecret!!)
            if (!store.mayClaim(input.player, ownerHash)) {
                call.fail(HttpStatusCode.Forbidden, "not_player_owner")
                return@post
            }
            if (!limiter.allow("post-player:" + input.player, limits.postPlayer)) {
                call.fail(HttpStatusCode.TooManyRequests, "rate_limited")
                return@post
            }

            val receivedAt = nowMillis()
            if (!store.consumeScoreSubmissionQuota(ownerHash, remote, receivedAt,
                    limits.postInstallDaily, limits.postIpDaily)) {
                val untilMidnight = (todayUtc(receivedAt) + 1L) * 86_400_000L - receivedAt
                call.response.header("Retry-After", ((untilMidnight + 999L) / 1000L).toString())
                call.fail(HttpStatusCode.TooManyRequests, "daily_quota_exceeded")
                return@post
            }
            val verification = verificationGate.run { budget ->
                val started = System.nanoTime()
                try {
                    verifyReplay(input, receivedAt, supportedVersion, budget)
                } finally {
                    metrics.verificationDuration(System.nanoTime() - started)
                }
            }
            if (verification == null || verification.exceptionOrNull() is VerificationBudgetExceeded) {
                // Przeciążenie to błąd serwera, nie gracza: oddajemy jednostkę dziennego limitu.
                store.refundScoreSubmissionQuota(ownerHash, remote, receivedAt)
                metrics.unavailable()
                call.response.header("Retry-After", "30")
                call.fail(HttpStatusCode.ServiceUnavailable, "verification_busy")
                return@post
            }
            if (verification.isFailure) {
                metrics.invalidReplay()
                call.fail(HttpStatusCode.UnprocessableEntity, "invalid_replay")
                return@post
            }
            metrics.verified()
            val replay = verification.getOrThrow()
            val result = try {
                store.save(input, replay, receivedAt, sha256(body), ownerHash)
            } catch (_: BlockedPlayerException) {
                call.fail(HttpStatusCode.Forbidden, "blocked_player")
                return@post
            } catch (_: PlayerOwnershipException) {
                call.fail(HttpStatusCode.Forbidden, "not_player_owner")
                return@post
            }
            if (!result.inserted && replay.board == "daily") {
                call.fail(HttpStatusCode.Conflict, "daily_already_submitted")
                return@post
            }
            call.respondText(
                json.encodeToString(ScoreReceipt(replay.board, replay.score, result.bestScore, result.inserted)),
                ContentType.Application.Json,
                HttpStatusCode.OK,
            )
        }

        get("/v1/top") {
            if (!limiter.allow("read-ip:" + clientIp(call), limits.readIp)) {
                call.fail(HttpStatusCode.TooManyRequests, "rate_limited")
                return@get
            }
            if (!auth.valid(
                    "GET", call.request.uri, "",
                    call.request.headers["X-CW-Timestamp"],
                    call.request.headers["X-CW-Nonce"],
                    call.request.headers["X-CW-Signature"],
                    false,
                )
            ) {
                call.fail(HttpStatusCode.Unauthorized, "invalid_signature")
                return@get
            }
            val board = call.request.queryParameters["board"] ?: ""
            val period = call.request.queryParameters["period"] ?: "all"
            if (!validBoard(board) || period !in setOf("day", "week", "all")) {
                call.fail(HttpStatusCode.BadRequest, "invalid_query")
                return@get
            }
            call.respondText(
                json.encodeToString(RankingResponse(board, period, store.top(board, period, nowMillis()))),
                ContentType.Application.Json,
                HttpStatusCode.OK,
            )
        }

        post("/v1/report") {
            if (!limiter.allow("report-ip:" + clientIp(call), limits.reportIp)) {
                call.fail(HttpStatusCode.TooManyRequests, "rate_limited")
                return@post
            }
            val length = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            if (length != null && length > MAX_REPORT_BYTES) {
                call.fail(HttpStatusCode.PayloadTooLarge, "body_too_large")
                return@post
            }
            val raw = call.receiveChannel().readRemaining(MAX_REPORT_BYTES + 1L).readByteArray()
            if (raw.size > MAX_REPORT_BYTES) {
                call.fail(HttpStatusCode.PayloadTooLarge, "body_too_large")
                return@post
            }
            val input = runCatching {
                json.decodeFromString<NickReportRequest>(raw.toString(Charsets.UTF_8))
            }.getOrNull()
            if (input == null || !PublicPlayerIds.valid(input.publicId) ||
                input.reason !in setOf("offensive", "impersonation", "other")) {
                call.fail(HttpStatusCode.BadRequest, "invalid_report")
                return@post
            }
            if (!limiter.allow("report-target:" + input.publicId, 15)) {
                call.fail(HttpStatusCode.TooManyRequests, "rate_limited")
                return@post
            }
            if (!withContext(Dispatchers.IO) { store.reportNick(input.publicId, input.reason, nowMillis()) }) {
                call.fail(HttpStatusCode.NotFound, "unknown_player")
                return@post
            }
            call.respondText(
                json.encodeToString(NickReportReceipt(true)), ContentType.Application.Json,
                HttpStatusCode.Accepted,
            )
        }

        get("/v1/around") {
            if (!limiter.allow("read-ip:" + clientIp(call), limits.readIp)) {
                call.fail(HttpStatusCode.TooManyRequests, "rate_limited")
                return@get
            }
            val installationSecret = call.request.headers["X-CW-Player-Secret"]
            if (!validInstallSecret(installationSecret) || !auth.valid(
                    "GET", call.request.uri, "",
                    call.request.headers["X-CW-Timestamp"],
                    call.request.headers["X-CW-Nonce"],
                    call.request.headers["X-CW-Signature"],
                    false,
                    installationSecret,
                )
            ) {
                call.fail(HttpStatusCode.Unauthorized, "invalid_signature")
                return@get
            }
            val board = call.request.queryParameters["board"] ?: "endless"
            val period = call.request.queryParameters["period"] ?: "all"
            val player = call.request.queryParameters["player"] ?: ""
            if (!validBoard(board) || period !in setOf("day", "week", "all") || !validPlayer(player)) {
                call.fail(HttpStatusCode.BadRequest, "invalid_query")
                return@get
            }
            val ownerHash = installSecretHash(installationSecret!!)
            if (!store.isOwner(player, ownerHash)) {
                call.fail(HttpStatusCode.Forbidden, "not_player_owner")
                return@get
            }
            call.respondText(
                json.encodeToString(RankingResponse(
                    board, period, store.around(board, period, nowMillis(), player),
                    store.publicIdFor(player),
                )),
                ContentType.Application.Json,
                HttpStatusCode.OK,
            )
        }
    }
}
