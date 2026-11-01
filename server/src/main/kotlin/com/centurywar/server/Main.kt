package com.centurywar.server

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import com.centurywar.game.SimVersion
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val dbPath = System.getenv("CW_DATABASE_PATH") ?: "./data/leaderboards.db"
    val idKeyText = requireNotNull(System.getenv("CW_PUBLIC_ID_KEY")) {
        "CW_PUBLIC_ID_KEY is required (independent 64-character hex key)"
    }
    require(idKeyText.matches(Regex("[0-9a-fA-F]{64}"))) {
        "CW_PUBLIC_ID_KEY must contain 64 hex characters"
    }
    val ids = PublicPlayerIds(idKeyText.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
    if (args.firstOrNull() == "admin") {
        require(File(dbPath).isFile) { "Nie znaleziono bazy rankingu: $dbPath" }
        SqliteStore("jdbc:sqlite:" + dbPath, ids).use { store ->
            val code = runAdmin(args.drop(1), store)
            if (code != 0) exitProcess(code)
        }
        return
    }
    require(args.isEmpty()) { "Nieznane argumenty; użyj: server admin help" }
    val secret = requireNotNull(System.getenv("CW_HMAC_KEY")) { "CW_HMAC_KEY is required" }
        .toByteArray(Charsets.UTF_8)
    require(secret.size >= 32) { "CW_HMAC_KEY must be at least 32 UTF-8 bytes" }
    val port = (System.getenv("PORT") ?: "8080").toInt().also { require(it in 1..65535) }
    val gameVersion = SimVersion.PROTOCOL
    File(dbPath).absoluteFile.parentFile?.mkdirs()
    val store = SqliteStore("jdbc:sqlite:" + dbPath, ids)
    val trustedProxies = (System.getenv("CW_TRUSTED_PROXY_IPS") ?: "127.0.0.1")
        .split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    fun limit(name: String, fallback: Int): Int = (System.getenv(name)?.toInt() ?: fallback)
        .also { require(it in 1..100_000) { "$name must be in 1..100000" } }
    val rateLimits = RateLimits(
        postIp = limit("CW_RATE_POST_IP", 20),
        deleteIp = limit("CW_RATE_DELETE_IP", 20),
        readIp = limit("CW_RATE_READ_IP", 120),
        reportIp = limit("CW_RATE_REPORT_IP", 5),
        postPlayer = limit("CW_RATE_POST_PLAYER", 6),
        postInstallDaily = limit("CW_RATE_POST_INSTALL_DAY", 100),
        postIpDaily = limit("CW_RATE_POST_IP_DAY", 400),
    )
    Runtime.getRuntime().addShutdownHook(Thread { store.close() })
    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        leaderboardModule(store, secret, supportedVersion = gameVersion,
            trustedProxies = trustedProxies, limits = rateLimits)
    }.start(wait = true)
}
