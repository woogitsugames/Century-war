package com.centurywar.server

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.LocalDate

private val ADMIN_USAGE = """
    Użycie: server admin <polecenie> [argumenty]
      recent [limit]                         Ostatnie 20 (maks. 200) wpisów
      reports [--all] [limit]                Ostatnie 50 (maks. 200) nierozpatrzonych zgłoszeń
      resolve-report <id>                    Oznacz zgłoszenie jako rozpatrzone
      find-nick <tekst>                      Znajdź graczy po nicku (bez wielkości liter)
      delete-player <UUID> [--keep-block]    Usuń gracza w całości (prośba e-mailowa)
      delete-score <UUID> <board> <day>      Usuń jeden wynik z dnia UTC
      block <UUID>                           Ukryj gracza i zablokuj nowe wyniki
      unblock <UUID>                         Przywróć widoczność gracza
      purge [--dry-run]                      Usuń dane po okresie przechowywania (CW_RETENTION_*)
      replay-deletions --since <RRRR-MM-DD> [--from <plik.sqlite>]
                                             Powtórz usunięcia z dziennika po odtworzeniu kopii
      log [limit]                            Ostatnie 20 (maks. 200) działań operatora
      backup <plik.sqlite>                  Utwórz spójną kopię podczas pracy serwera
      restore <plik.sqlite|->               Przywróć kopię offline (- = stdin)
      export-csv                             Wypisz wszystkie wyniki jako CSV na stdout
      help                                   Pokaż pomoc
""".trimIndent()

/** CLI korzysta bezpośrednio z bazy, nie wystawia endpointów administracyjnych. */
internal fun runAdmin(
    args: List<String>,
    store: SqliteStore,
    out: Appendable = System.out,
    err: Appendable = System.err,
    retention: RetentionPolicy = RetentionPolicy(),
    now: () -> Long = System::currentTimeMillis,
): Int {
    fun invalid(message: String): Int {
        err.appendLine(message)
        err.appendLine(ADMIN_USAGE)
        return 2
    }

    return try {
        when (args.firstOrNull()) {
            "help", "--help", "-h" -> {
                if (args.size != 1) return invalid("Nieznane argumenty")
                out.appendLine(ADMIN_USAGE)
                0
            }
            "recent" -> {
                if (args.size !in 1..2) return invalid("Nieprawidłowa liczba argumentów")
                val limit = if (args.size == 1) 20 else args[1].toIntOrNull()
                if (limit == null || limit !in 1..200) return invalid("Limit musi być w zakresie 1..200")
                out.appendLine("player\tboard\tday\tscore\tverified_at\tblocked\tnick")
                for (row in store.recentScores(limit)) {
                    val nick = NickModeration.publicNick(row.nick, row.player)
                    out.appendLine(
                        row.player + "\t" + row.board + "\t" + row.day + "\t" +
                            row.score + "\t" + row.verifiedAt + "\t" + row.blocked + "\t" + nick
                    )
                }
                0
            }
            "reports" -> {
                val all = "--all" in args
                val rest = args.drop(1).filter { it != "--all" }
                if (rest.size > 1 || args.count { it == "--all" } > 1) return invalid("Nieprawidłowa liczba argumentów")
                val limit = if (rest.isEmpty()) 50 else rest[0].toIntOrNull()
                if (limit == null || limit !in 1..200) return invalid("Limit musi być w zakresie 1..200")
                out.appendLine("id\tplayer\tpublic_id\tnick\treason\treported_at\tresolved_at")
                for (row in store.recentReports(limit, unresolvedOnly = !all)) {
                    out.appendLine(
                        row.id.toString() + "\t" + row.player + "\t" + row.publicId + "\t" +
                            row.nick + "\t" + row.reason + "\t" + row.reportedAt + "\t" + (row.resolvedAt ?: "-")
                    )
                }
                0
            }
            "resolve-report" -> {
                val id = args.getOrNull(1)?.toLongOrNull()
                if (args.size != 2 || id == null || id <= 0L) return invalid("Podaj numer zgłoszenia (kolumna id)")
                when (store.resolveReport(id, now())) {
                    true -> {
                        out.appendLine("resolved")
                        0
                    }
                    false -> {
                        out.appendLine("unchanged")
                        0
                    }
                    null -> {
                        err.appendLine("not_found")
                        1
                    }
                }
            }
            "find-nick" -> {
                val query = args.drop(1).joinToString(" ").trim()
                if (query.isEmpty() || query.length > 64) return invalid("Podaj fragment nicku (1..64 znaków)")
                val matches = store.findPlayersByNick(query)
                if (NickModeration.publicNick(query, NIL_UUID) != query) {
                    // Nick odrzucony przez filtr nie jest zapisywany; w rankingu widać „Dowódca-XXXX”.
                    err.appendLine("hint: taki nick zastępuje filtr; poproś o nick widoczny w rankingu (Dowódca-XXXX)")
                }
                if (matches.isEmpty()) {
                    err.appendLine("not_found")
                    return 1
                }
                out.appendLine("player\tpublic_id\tnick\tscores\tlast_activity")
                for (m in matches) {
                    out.appendLine(
                        m.player + "\t" + (m.publicId ?: "-") + "\t" + m.nick + "\t" + m.scores + "\t" +
                            Instant.ofEpochSecond(m.lastActivity)
                    )
                }
                0
            }
            "delete-player" -> {
                val keepBlock = args.size == 3 && args[2] == "--keep-block"
                if ((args.size != 2 && !keepBlock) || !validPlayer(args[1])) {
                    return invalid("Podaj poprawny UUID v4 i opcjonalnie --keep-block")
                }
                val result = store.deletePlayerByOperator(args[1], keepBlock, now())
                store.logAdmin("delete-player", args[1],
                    (if (result.found) "deleted" else "not_found") + " keep_block=$keepBlock", now())
                if (result.found) {
                    out.appendLine("deleted")
                    if (result.blockRemoved) out.appendLine("block_removed")
                    0
                } else {
                    err.appendLine("not_found")
                    1
                }
            }
            "delete-score" -> {
                if (args.size != 4) return invalid("Podaj UUID, planszę i dzień UTC")
                val player = args[1]
                val board = args[2]
                val day = args[3].toLongOrNull()
                if (!validPlayer(player) || !validBoard(board) || day == null || day < 0L) {
                    return invalid("Nieprawidłowy UUID, plansza lub dzień UTC")
                }
                if (store.deleteScore(player, board, day)) {
                    store.logAdmin("delete-score", player, "board=$board day=$day", now())
                    out.appendLine("deleted")
                    0
                } else {
                    err.appendLine("not_found")
                    1
                }
            }
            "block", "unblock" -> {
                if (args.size != 2 || !validPlayer(args[1])) return invalid("Podaj poprawny UUID v4")
                val changed = if (args[0] == "block") store.blockPlayer(args[1])
                              else store.unblockPlayer(args[1])
                if (changed) store.logAdmin(args[0], args[1], "updated", now())
                out.appendLine(if (changed) "updated" else "unchanged")
                0
            }
            "purge" -> {
                val dryRun = args.size == 2 && args[1] == "--dry-run"
                if (args.size != 1 && !dryRun) return invalid("Użycie: purge [--dry-run]")
                val at = now()
                val result = store.purgeExpired(at, retention, dryRun)
                if (!dryRun) store.logAdmin("purge", null, "cli " + result.summary(), at)
                out.appendLine((if (dryRun) "dry-run " else "") + result.summary())
                0
            }
            "replay-deletions" -> {
                val options = parseOptions(args.drop(1), setOf("--since", "--from"))
                    ?: return invalid("Użycie: replay-deletions --since <RRRR-MM-DD> [--from <plik.sqlite>]")
                val since = options["--since"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: return invalid("Podaj datę --since w formacie RRRR-MM-DD")
                val sinceSeconds = since.toEpochDay() * 86_400L
                val from = options["--from"]
                val entries = if (from == null) store.deletionLogSince(sinceSeconds)
                              else DatabaseMaintenance.readDeletionLog(Path.of(from), sinceSeconds)
                val deleted = store.replayDeletions(entries)
                store.logAdmin("replay-deletions", null,
                    "since=$since source=${if (from == null) "current" else "file"} entries=${entries.size} deleted=$deleted",
                    now())
                out.appendLine("entries: ${entries.size}")
                out.appendLine("deleted_players: $deleted")
                0
            }
            "log" -> {
                if (args.size !in 1..2) return invalid("Nieprawidłowa liczba argumentów")
                val limit = if (args.size == 1) 20 else args[1].toIntOrNull()
                if (limit == null || limit !in 1..200) return invalid("Limit musi być w zakresie 1..200")
                out.appendLine("at\taction\ttarget_hash\tdetail")
                for (row in store.recentAdminLog(limit)) {
                    out.appendLine(
                        Instant.ofEpochSecond(row.at).toString() + "\t" + row.action + "\t" +
                            (row.targetHash ?: "-") + "\t" + row.detail
                    )
                }
                0
            }
            "backup" -> {
                if (args.size != 2) return invalid("Podaj ścieżkę nowej kopii .sqlite")
                val path = store.backupTo(Path.of(args[1]))
                out.appendLine("backup: $path")
                0
            }
            "restore" -> invalid("Przywracanie uruchom osobno: server admin restore <plik.sqlite>")
            "export-csv" -> {
                if (args.size != 1) return invalid("Eksport używa stdout: przekieruj do pliku")
                store.exportCsv(out)
                0
            }
            else -> invalid("Nieznane polecenie")
        }
    } catch (e: Exception) {
        err.appendLine("Błąd administracji: " + e.message)
        1
    }
}

/** UUID tylko do sprawdzenia, czy filtr zastąpiłby szukany nick (nie trafia do bazy). */
private const val NIL_UUID = "00000000-0000-4000-8000-000000000000"

/** Pary `--opcja wartość`; null przy nieznanej, powtórzonej albo niepełnej opcji. */
private fun parseOptions(args: List<String>, allowed: Set<String>): Map<String, String>? {
    if (args.size % 2 != 0) return null
    val result = HashMap<String, String>()
    for (i in args.indices step 2) {
        val key = args[i]
        if (key !in allowed || key in result) return null
        result[key] = args[i + 1]
    }
    return result
}

/** Uruchamiane przed otwarciem SQLite, by nie nadpisywać aktywnego uchwytu JDBC. */
internal fun runRestore(
    args: List<String>,
    database: Path,
    out: Appendable = System.out,
    err: Appendable = System.err,
    input: InputStream = System.`in`,
): Int {
    if (args.size != 2 || args[0] != "restore") {
        err.appendLine("Użycie: server admin restore <plik.sqlite|->")
        return 2
    }
    var temporary: Path? = null
    return try {
        val snapshot = if (args[1] == "-") {
            val db = database.toAbsolutePath().normalize()
            Files.createDirectories(db.parent)
            Files.createTempFile(db.parent, ".cw-stdin-", ".sqlite").also {
                temporary = it
                Files.copy(input, it, StandardCopyOption.REPLACE_EXISTING)
            }
        } else Path.of(args[1])
        val previous = DatabaseMaintenance.restore(snapshot, database)
        out.appendLine("restored: ${database.toAbsolutePath().normalize()}")
        if (previous != null) out.appendLine("previous-files: $previous")
        0
    } catch (e: Exception) {
        err.appendLine("Błąd przywracania: " + e.message)
        1
    } finally {
        temporary?.let { Files.deleteIfExists(it) }
    }
}
