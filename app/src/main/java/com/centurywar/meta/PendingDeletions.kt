package com.centurywar.meta

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Niezależna od zgody na ranking kolejka żądań usunięcia.
 * Nie wygasa, nie jest przycinana; dostęp tylko z wątku sieciowego ScoreClient.
 */
internal class PendingDeletions(private val file: File) {
    private val ids = LinkedHashSet<String>()

    fun load(): Boolean = try {
        val lines = if (file.exists()) file.readLines(Charsets.UTF_8) else emptyList()
        ids.clear()
        for (id in lines) {
            if (validId(id)) ids.add(id)
        }
        true
    } catch (_: IOException) {
        false
    }

    fun contains(id: String): Boolean = id in ids
    fun first(): String? = ids.firstOrNull()

    /** true oznacza, że żądanie zostało trwale zapisane na urządzeniu. */
    fun add(id: String): Boolean {
        if (!validId(id)) return false
        if (!ids.add(id)) return true
        if (save()) return true
        ids.remove(id)
        return false
    }

    fun remove(id: String): Boolean {
        if (!ids.remove(id)) return true
        if (save()) return true
        ids.add(id)
        return false
    }

    private fun save(): Boolean = try {
        if (ids.isEmpty()) {
            !file.exists() || file.delete()
        } else {
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            tmp.writeText(ids.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
            try {
                Files.move(tmp.toPath(), file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            true
        }
    } catch (_: IOException) {
        false
    }

    private fun validId(id: String): Boolean = runCatching {
        val uuid = UUID.fromString(id)
        uuid.version() == 4 && uuid.variant() == 2 && uuid.toString() == id
    }.getOrDefault(false)
}
