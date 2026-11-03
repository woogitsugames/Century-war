package com.centurywar.crash

import java.io.File

/**
 * Wyłącznie prywatny katalog files/crash; bez dostępu do sieci i kopii chmurowej.
 *
 * Nazwy plików (R6-02 M1):
 * - `fatal-…` i `caught-…`: awarie Javy (nieprzechwycona i złapany błąd klatki), najwyżej [MAX_LOGS];
 * - `exit-fatal-…`: zakończenie procesu z ANR albo błędem natywnym ([ExitReasons]). Liczy się jak
 *   awaria: obejmuje je pytanie po awarii ([CrashReports.pendingFatal]) i „Zgłoś problem”;
 * - `exit-…`: pozostałe zakończenia procesu (brak pamięci, nadmierne zużycie zasobów, sygnał).
 * Oba rodzaje `exit-` mają osobny limit [MAX_EXIT_LOGS], żeby seria zakończeń procesu nie wypierała
 * awarii Javy. „Zgłoś problem” ([newest] bez filtra) bierze zwykłe `exit-` tylko wtedy, gdy innych
 * raportów nie ma.
 */
class CrashStore(private val directory: File) {
    companion object {
        const val MAX_LOGS = 10
        const val MAX_EXIT_LOGS = 3
        const val MAX_CHARS = 32_000
        /** Stare nazwy (`fatal-`, `caught-`) i nowe (`exit-`, `exit-fatal-`). */
        private val FILE_NAME = Regex("(fatal|caught|exit|exit-fatal)-[0-9]+\\.txt")
        private const val EXIT = "exit-"
        private const val EXIT_FATAL = "exit-fatal-"
        private val ORDER = compareBy<File> { it.lastModified() }.thenBy { it.name }

        private fun isExit(f: File) = f.name.startsWith(EXIT)
        private fun isFatal(f: File) = f.name.startsWith("fatal-") || f.name.startsWith(EXIT_FATAL)
    }

    private fun logs(): List<File> = directory.listFiles()
        ?.filter { it.isFile && FILE_NAME.matches(it.name) } ?: emptyList()

    /**
     * [exit] oznacza raport zakończenia procesu; [eventTime] (> 0) ustawia czas modyfikacji pliku
     * na chwilę zdarzenia, żeby kolejność [newest] odpowiadała kolejności zdarzeń (R6-L7).
     */
    @Synchronized
    fun write(fatal: Boolean, report: String, exit: Boolean = false, eventTime: Long = 0L): File {
        check(directory.isDirectory || directory.mkdirs()) { "Brak katalogu dziennika" }
        val name = when {
            exit && fatal -> EXIT_FATAL
            exit -> EXIT
            fatal -> "fatal-"
            else -> "caught-"
        }
        val file = File.createTempFile(name, ".txt", directory)
        try {
            file.writeText(report.take(MAX_CHARS), Charsets.UTF_8)
        } catch (e: Exception) {
            file.delete()
            throw e
        }
        if (eventTime > 0L) file.setLastModified(eventTime)
        rotate()
        return file
    }

    @Synchronized
    fun rotate() {
        val (exits, crashes) = logs().partition(::isExit)
        trim(crashes, MAX_LOGS)
        trim(exits, MAX_EXIT_LOGS)
    }

    private fun trim(files: List<File>, limit: Int) {
        val sorted = files.sortedWith(ORDER)
        for (i in 0 until (sorted.size - limit).coerceAtLeast(0)) sorted[i].delete()
    }

    /**
     * [fatalOnly]: najnowsza awaria (`fatal-` albo `exit-fatal-`) do pytania po starcie.
     * Bez filtra („Zgłoś problem”): najnowsza awaria albo złapany błąd; zwykłe `exit-` tylko wtedy,
     * gdy innych raportów nie ma.
     */
    @Synchronized
    fun newest(fatalOnly: Boolean = false): File? {
        val all = logs()
        if (fatalOnly) return all.filter(::isFatal).maxWithOrNull(ORDER)
        return all.filter { !isExit(it) || isFatal(it) }.maxWithOrNull(ORDER) ?: all.maxWithOrNull(ORDER)
    }

    /** Nazwa musi pochodzić z naszego katalogu; nie akceptujemy ścieżek ani URI. */
    @Synchronized
    fun read(name: String): String? {
        if (!FILE_NAME.matches(name)) return null
        val file = File(directory, name)
        if (!file.isFile) return null
        return file.readText(Charsets.UTF_8).take(MAX_CHARS)
    }
}
