package com.centurywar.crash

import java.io.File

/** Wyłącznie prywatny katalog files/crash; bez dostępu do sieci i kopii chmurowej. */
class CrashStore(private val directory: File) {
    companion object {
        const val MAX_LOGS = 10
        const val MAX_CHARS = 32_000
        private val FILE_NAME = Regex("(fatal|caught)-[0-9]+\\.txt")
    }

    private fun logs(): List<File> = directory.listFiles()
        ?.filter { it.isFile && FILE_NAME.matches(it.name) } ?: emptyList()

    @Synchronized
    fun write(fatal: Boolean, report: String): File {
        check(directory.isDirectory || directory.mkdirs()) { "Brak katalogu dziennika" }
        val name = if (fatal) "fatal-" else "caught-"
        val file = File.createTempFile(name, ".txt", directory)
        try {
            file.writeText(report.take(MAX_CHARS), Charsets.UTF_8)
        } catch (e: Exception) {
            file.delete()
            throw e
        }
        rotate()
        return file
    }

    @Synchronized
    fun rotate() {
        val sorted = logs().sortedWith(compareBy<File> { it.lastModified() }.thenBy { it.name })
        for (i in 0 until (sorted.size - MAX_LOGS).coerceAtLeast(0)) sorted[i].delete()
    }

    @Synchronized
    fun newest(fatalOnly: Boolean = false): File? = logs()
        .filter { !fatalOnly || it.name.startsWith("fatal-") }
        .maxWithOrNull(compareBy<File> { it.lastModified() }.thenBy { it.name })

    /** Nazwa musi pochodzić z naszego katalogu; nie akceptujemy ścieżek ani URI. */
    @Synchronized
    fun read(name: String): String? {
        if (!FILE_NAME.matches(name)) return null
        val file = File(directory, name)
        if (!file.isFile) return null
        return file.readText(Charsets.UTF_8).take(MAX_CHARS)
    }
}
