package com.centurywar.meta

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Usunięcia działają także po zmianie tożsamości i wyłączeniu rankingu.
 * Sekret starej instalacji zapisujemy razem z żądaniem, a nie odczytujemy z nowego profilu.
 */
internal class PendingDeletions(private val file: File) {
    data class Entry(val player: String, val secret: String)
    private val credentials = LinkedHashMap<String, String>()

    fun load(): Boolean = try {
        val lines = if (file.exists()) file.readLines(Charsets.UTF_8) else emptyList()
        credentials.clear()
        for (line in lines) {
            val parts = line.split('\t', limit = 2)
            val player = parts.firstOrNull() ?: continue
            if (!validId(player)) continue
            val secret = parts.getOrNull(1)?.takeIf { validSecret(it) } ?: ""
            // Stare żądania (sam UUID) zachowujemy, ale bez klucza nie da się ich wykonać.
            credentials[player] = secret
        }
        true
    } catch (_: IOException) {
        false
    }

    fun contains(id: String): Boolean = id in credentials
    fun first(): Entry? = credentials.entries.firstOrNull { validSecret(it.value) }
        ?.let { Entry(it.key, it.value) }

    /** Potwierdzenie oznacza trwałe zapisanie żądania, nie wykonanie na serwerze. */
    fun add(id: String, secret: String): Boolean {
        if (!validId(id) || !validSecret(secret)) return false
        val previous = credentials[id]
        if (previous == secret) return true
        if (previous != null && previous.isNotEmpty()) return false
        credentials[id] = secret
        if (save()) return true
        if (previous == null) credentials.remove(id) else credentials[id] = previous
        return false
    }

    fun remove(id: String): Boolean {
        val previous = credentials.remove(id) ?: return true
        if (save()) return true
        credentials[id] = previous
        return false
    }

    private fun save(): Boolean = try {
        if (credentials.isEmpty()) {
            !file.exists() || file.delete()
        } else {
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            tmp.writeText(credentials.entries.joinToString("\n", postfix = "\n") {
                it.key + "\t" + it.value
            }, Charsets.UTF_8)
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

    private fun validSecret(value: String): Boolean = value.matches(Regex("[0-9a-f]{64}"))
}
