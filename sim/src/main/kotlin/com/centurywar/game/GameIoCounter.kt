package com.centurywar.game

import java.util.concurrent.atomic.AtomicInteger

/** Licznik dostępu do dysku przez pętlę gry, która nie ma Androidowego Loopera. */
object GameIoCounter {
    private val diskOnGame = AtomicInteger()

    val diskViolations: Int get() = diskOnGame.get()

    fun diskAccess() {
        if (Thread.currentThread().name == "game-loop") diskOnGame.incrementAndGet()
    }

    /** Reset wyłącznie na początku pojedynczego testu. */
    fun resetForTest() { diskOnGame.set(0) }
}
