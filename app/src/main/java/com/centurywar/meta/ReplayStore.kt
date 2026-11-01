package com.centurywar.meta

import com.centurywar.game.Replay
import com.centurywar.game.ReplayRecord
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Jeden wątek I/O: zapis atomowy, indeks nagłówków i retencja bez pracy dysku w pętli gry.
 * Zachowuje 20 ostatnich zwykłych zapisów i 20 przypiętych.
 */
class ReplayStore(private val directory: File) {
    data class Entry(
        val id: String, val meta: ReplayRecord.Meta, val playable: Boolean,
        val pinned: Boolean, val imported: Boolean = false, val orderMillis: Long = meta.dateMillis,
    )

    private val seq = AtomicLong()
    private val io = Executors.newSingleThreadExecutor { job ->
        Thread(job, "replay-io").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
    }
    private val pattern = Regex("[a-z0-9_-]{1,64}")
    private val pinsFile = File(directory, "pinned.txt")
    private var pins: MutableSet<String>? = null

    /** Wywołanie na wątku I/O; odbiorca przenosi zmianę profilu przez Game.post. */
    @Volatile var onRemoved: ((List<String>) -> Unit)? = null

    fun newId(): String = System.currentTimeMillis().toString(36) + "-" +
        seq.incrementAndGet().toString(36)

    fun saveAsync(id: String, bytes: ByteArray) {
        require(pattern.matches(id))
        io.execute {
            try {
                // Nie twórz plików niezgodnych z formatem.
                ReplayRecord.decode(bytes)
                writeAtomic(File(directory, "$id.cwr"), bytes)
                prune()
            } catch (_: Exception) { }
        }
    }

    /**
     * Import z obcego źródła. Dekodowanie, pełne odtworzenie i zapis odbywają się
     * kolejno na wątku I/O; callback nigdy nie działa na wątku gry.
     * Prefiks friend_ izoluje cudze powtórki od rankingu i osiągnięć.
     */
    fun importAsync(bytes: ByteArray, callback: (String?) -> Unit) {
        io.execute {
            val id = try {
                require(bytes.size in 24..ReplayImport.MAX_BYTES)
                val record = ReplayRecord.decode(bytes)
                require(record.playable) { "Niekompatybilna wersja powtórki" }
                val result = Replay.verify(record.cfg, requireNotNull(record.log))
                require(result.winner == record.meta.winner &&
                    result.score == record.meta.score && result.wave == record.meta.wave &&
                    result.time == record.meta.time) { "Wynik nie zgadza się z dziennikiem" }
                var newId: String
                do { newId = "friend_" + newId() }
                while (File(directory, "$newId.cwr").exists())
                if (!writeAtomic(File(directory, "$newId.cwr"), bytes)) null
                else {
                    prune()
                    newId.takeIf { File(directory, "$it.cwr").isFile }
                }
            } catch (_: Exception) { null }
            callback(id)
        }
    }

    fun pinAsync(id: String, pinned: Boolean = true) {
        if (!pattern.matches(id)) return
        io.execute {
            val set = pinned()
            if (pinned) {
                if (File(directory, "$id.cwr").isFile) set.add(id)
            } else set.remove(id)
            prune()
        }
    }

    fun deleteAsync(id: String) {
        if (!pattern.matches(id)) return
        io.execute {
            val deleted = File(directory, "$id.cwr").delete()
            pinned().remove(id)
            savePins()
            if (deleted) onRemoved?.invoke(listOf(id))
        }
    }

    /** Callback działa na wątku I/O; odbiorca przekazuje wynik do Game.post. */
    fun listAsync(callback: (List<Entry>) -> Unit) {
        io.execute {
            val entries = try { entries() } catch (_: Exception) { emptyList() }
            callback(entries)
        }
    }

    fun loadAsync(id: String, callback: (ByteArray?) -> Unit) {
        io.execute {
            val data = if (pattern.matches(id)) try {
                File(directory, "$id.cwr").takeIf { it.isFile && it.length() <= MAX_BYTES }?.readBytes()
            } catch (_: Exception) { null } else null
            callback(data)
        }
    }

    private fun pinned(): MutableSet<String> {
        pins?.let { return it }
        val ids = LinkedHashSet<String>()
        try {
            if (pinsFile.length() <= 4096L) pinsFile.forEachLine { if (pattern.matches(it)) ids.add(it) }
        } catch (_: Exception) { }
        pins = ids
        return ids
    }

    private fun entries(): List<Entry> {
        val set = pinned()
        val result = ArrayList<Entry>()
        for (file in directory.listFiles().orEmpty()) {
            if (!file.name.endsWith(".cwr") || !file.isFile || file.length() > MAX_BYTES) continue
            val id = file.name.removeSuffix(".cwr")
            if (!pattern.matches(id)) continue
            val record = try { ReplayRecord.decode(file.readBytes()) } catch (_: Exception) { continue }
            val imported = isImportedId(id)
            result.add(Entry(id, record.meta, record.playable, id in set,
                imported, if (imported) file.lastModified() else record.meta.dateMillis))
        }
        result.sortWith(compareByDescending<Entry> { it.orderMillis }.thenByDescending { it.id })
        return result
    }

    private fun prune() {
        val all = entries()
        val set = pinned()
        val keepPins = all.filter { it.id in set }.take(MAX_PINNED).mapTo(HashSet()) { it.id }
        set.retainAll(keepPins)
        val latest = all.filter { it.id !in keepPins }.take(MAX_RECENT).mapTo(HashSet()) { it.id }
        val removed = ArrayList<String>()
        for (entry in all) if (entry.id !in keepPins && entry.id !in latest) {
            if (File(directory, "${entry.id}.cwr").delete()) removed.add(entry.id)
        }
        savePins()
        if (removed.isNotEmpty()) onRemoved?.invoke(removed)
    }

    private fun savePins() {
        writeAtomic(pinsFile, pinned().sorted().joinToString("\n", postfix = "\n").toByteArray(Charsets.UTF_8))
    }

    private fun writeAtomic(file: File, bytes: ByteArray): Boolean = try {
        directory.mkdirs()
        val tmp = File(directory, file.name + ".tmp")
        FileOutputStream(tmp).use { it.write(bytes); it.fd.sync() }
        // Gdy rename się nie powiedzie, zachowaj poprzedni plik zamiast usuwać go.
        tmp.renameTo(file).also { if (!it) tmp.delete() }
    } catch (_: Exception) {
        File(directory, file.name + ".tmp").delete()
        false
    }

    /** Bariera dla onPause, bez przeprowadzania zapisu w wątku UI. */
    fun awaitWrites(timeoutMs: Long): Boolean {
        if (timeoutMs <= 0L) return false
        return try {
            io.submit { }.get(timeoutMs, TimeUnit.MILLISECONDS)
            true
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        } catch (_: Exception) { false }
    }

    companion object {
        const val MAX_RECENT = 20
        const val MAX_PINNED = 20
        const val MAX_SHARE_BYTES = 2 * 1024 * 1024
        private const val MAX_BYTES = 1 shl 20
        fun isImportedId(id: String): Boolean = id.startsWith("friend_")
    }
}
