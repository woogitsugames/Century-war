package com.centurywar.game

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Atomowy zapis do pliku: niedokończony .tmp nie zastępuje poprawnego pliku.
 * Błędy są sygnalizowane raz na uruchomienie i nie przerywają działania gry.
 */
object SaveGuard {
    private val warningSent = AtomicBoolean(false)
    private val warningPending = AtomicBoolean(false)

    fun report(scope: String, error: Throwable? = null) {
        System.err.println("Century War: problem zapisu [$scope] (${error?.javaClass?.simpleName ?: "uszkodzony plik"})")
        if (warningSent.compareAndSet(false, true)) warningPending.set(true)
    }

    /** Powiadomienie odbiera wyłącznie menu; kolejne awarie nadal trafiają do logu. */
    fun takeWarning(): Boolean = warningPending.getAndSet(false)

    internal fun resetWarningForTests() {
        warningPending.set(false)
        warningSent.set(false)
    }

    /** Wyjątek obsługuje wywołujący; przy błędzie ani plik docelowy, ani .bak nie jest kasowany. */
    @Throws(IOException::class)
    fun atomicWrite(file: File, bytes: ByteArray) {
        val parent = file.parentFile ?: throw IOException("Brak katalogu zapisu")
        if (!parent.isDirectory && !parent.mkdirs()) throw IOException("Niedostępny katalog zapisu")
        val tmp = File(parent, file.name + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(bytes)
            out.flush()
            out.fd.sync()
        }
        if (!tmp.renameTo(file)) {
            Files.move(tmp.toPath(), file.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /** Druga, ostatnia w pełni zapisana kopia; awaria kopii nie unieważnia pliku głównego. */
    @Throws(IOException::class)
    fun writeBackup(file: File, bytes: ByteArray) {
        atomicWrite(File(file.parentFile, file.name + ".bak"), bytes)
    }
}
