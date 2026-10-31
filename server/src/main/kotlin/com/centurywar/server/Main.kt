package com.centurywar.server

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val dbPath = System.getenv("CW_DATABASE_PATH") ?: "./data/leaderboards.db"
    if (args.firstOrNull() == "admin") {
        require(File(dbPath).isFile) { "Nie znaleziono bazy rankingu: $dbPath" }
        SqliteStore("jdbc:sqlite:" + dbPath).use { store ->
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
    val gameVersion = System.getenv("CW_GAME_VERSION") ?: "0.4.0"
    File(dbPath).absoluteFile.parentFile?.mkdirs()
    val store = SqliteStore("jdbc:sqlite:" + dbPath)
    Runtime.getRuntime().addShutdownHook(Thread { store.close() })
    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        leaderboardModule(store, secret, supportedVersion = gameVersion)
    }.start(wait = true)
}
