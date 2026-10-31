package com.centurywar.server

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveChannel
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import io.ktor.server.request.uri
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

private val json = Json { ignoreUnknownKeys = false; encodeDefaults = true }
private const val MAX_REQUEST_BYTES = 1_048_576

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
    supportedVersion: String = "0.1.0",
) {
    val auth = RequestAuthenticator(secret, store, nowMillis)
    val limiter = RateLimiter(nowMillis)
    val workers = Semaphore(2)

    routing {
        post("/v1/score") {
            val remote = call.request.local.remoteHost
            if (!limiter.allow("post-ip:" + remote, 20)) {
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
            if (!auth.valid(
                    "POST", call.request.uri, body,
                    call.request.headers["X-CW-Timestamp"],
                    call.request.headers["X-CW-Nonce"],
                    call.request.headers["X-CW-Signature"],
                    true,
                )
            ) {
                call.fail(HttpStatusCode.Unauthorized, "invalid_signature_or_nonce")
                return@post
            }

            val input = runCatching { json.decodeFromString<ScoreSubmission>(body) }.getOrNull()
            if (input == null || !validPlayer(input.player) || !validNick(input.nick)) {
                call.fail(HttpStatusCode.BadRequest, "invalid_submission")
                return@post
            }
            if (!limiter.allow("post-player:" + input.player, 6)) {
                call.fail(HttpStatusCode.TooManyRequests, "rate_limited")
                return@post
            }

            val receivedAt = nowMillis()
            val verification = withContext(Dispatchers.Default) {
                workers.withPermit {
                    runCatching { ReplayVerifier.verify(input, receivedAt, supportedVersion) }
                }
            }
            if (verification.isFailure) {
                call.fail(HttpStatusCode.UnprocessableEntity, "invalid_replay")
                return@post
            }
            val replay = verification.getOrThrow()
            val result = store.save(input, replay, receivedAt, sha256(body))
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
            if (!limiter.allow("read-ip:" + call.request.local.remoteHost, 120)) {
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

        get("/v1/around") {
            if (!limiter.allow("read-ip:" + call.request.local.remoteHost, 120)) {
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
            val board = call.request.queryParameters["board"] ?: "endless"
            val period = call.request.queryParameters["period"] ?: "all"
            val player = call.request.queryParameters["player"] ?: ""
            if (!validBoard(board) || period !in setOf("day", "week", "all") || !validPlayer(player)) {
                call.fail(HttpStatusCode.BadRequest, "invalid_query")
                return@get
            }
            call.respondText(
                json.encodeToString(RankingResponse(board, period, store.around(board, period, nowMillis(), player))),
                ContentType.Application.Json,
                HttpStatusCode.OK,
            )
        }
    }
}
