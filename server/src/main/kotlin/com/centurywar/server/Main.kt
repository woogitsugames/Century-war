package com.centurywar.server

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.io.File

fun main() {
    val secret = requireNotNull(System.getenv("CW_HMAC_KEY")) { "CW_HMAC_KEY is required" }
        .toByteArray(Charsets.UTF_8)
    require(secret.size >= 32) { "CW_HMAC_KEY must be at least 32 UTF-8 bytes" }
    val dbPath = System.getenv("CW_DATABASE_PATH") ?: "./data/leaderboards.db"
    val port = (System.getenv("PORT") ?: "8080").toInt().also { require(it in 1..65535) }
    val gameVersion = System.getenv("CW_GAME_VERSION") ?: "0.1.0"
    File(dbPath).absoluteFile.parentFile?.mkdirs()
    val store = SqliteStore("jdbc:sqlite:" + dbPath)
    Runtime.getRuntime().addShutdownHook(Thread { store.close() })
    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        leaderboardModule(store, secret, supportedVersion = gameVersion)
    }.start(wait = true)
}
