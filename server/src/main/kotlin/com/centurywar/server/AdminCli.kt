package com.centurywar.server

private val ADMIN_USAGE = """
    Użycie: server admin <polecenie> [argumenty]
      recent [limit]                         Ostatnie 20 (maks. 200) wpisów
      delete-score <UUID> <board> <day>      Usuń jeden wynik z dnia UTC
      block <UUID>                           Ukryj gracza i zablokuj nowe wyniki
      unblock <UUID>                         Przywróć widoczność gracza
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
