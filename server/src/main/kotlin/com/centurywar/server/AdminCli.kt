package com.centurywar.server

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

private val ADMIN_USAGE = """
    Użycie: server admin <polecenie> [argumenty]
      recent [limit]                         Ostatnie 20 (maks. 200) wpisów
      reports [limit]                        Ostatnie 50 (maks. 200) zgłoszeń
      delete-score <UUID> <board> <day>      Usuń jeden wynik z dnia UTC
      block <UUID>                           Ukryj gracza i zablokuj nowe wyniki
      unblock <UUID>                         Przywróć widoczność gracza
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
                if (args.size !in 1..2) return invalid("Nieprawidłowa liczba argumentów")
                val limit = if (args.size == 1) 50 else args[1].toIntOrNull()
                if (limit == null || limit !in 1..200) return invalid("Limit musi być w zakresie 1..200")
                out.appendLine("id\tplayer\tpublic_id\tnick\treason\treported_at")
                for (row in store.recentReports(limit)) {
                    out.appendLine(
                        row.id.toString() + "\t" + row.player + "\t" + row.publicId + "\t" +
                            row.nick + "\t" + row.reason + "\t" + row.reportedAt
                    )
                }
                0
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
                out.appendLine(if (changed) "updated" else "unchanged")
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
