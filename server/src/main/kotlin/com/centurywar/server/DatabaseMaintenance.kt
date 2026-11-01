package com.centurywar.server

import java.io.Closeable
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Narzędzia offline/online do utrzymania jednej bazy SQLite. */
internal object DatabaseMaintenance {
    private val privatePermissions = setOf(
        PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
    )

    /** Osobne połączenie odczytujące WAL; zapisy na połączeniu serwera mogą trwać dalej. */
    fun backup(jdbcUrl: String, output: Path): Path {
        val target = output.toAbsolutePath().normalize()
        require(!Files.exists(target)) { "Plik kopii już istnieje: $target" }
        Files.createDirectories(target.parent)
        val staging = target.resolveSibling(".cw-backup-${UUID.randomUUID()}.partial")
        try {
            DriverManager.getConnection(jdbcUrl).use { db ->
                db.createStatement().use { statement ->
                    statement.execute("PRAGMA busy_timeout=5000")
                    // SQLite nie obsługuje parametru JDBC dla ścieżki w VACUUM INTO.
                    val literal = staging.toString().replace("'", "''")
                    statement.execute("VACUUM INTO '$literal'")
                }
            }
            validateSnapshot(staging)
            privateFile(staging)
            moveNew(staging, target)
            return target
        } finally {
            Files.deleteIfExists(staging)
        }
    }

    /** Przywracanie wyłącznie przy zatrzymanym serwerze. Stary stan trafia do katalogu ratunkowego. */
    fun restore(snapshot: Path, database: Path): Path? {
        val source = snapshot.toAbsolutePath().normalize()
        val target = database.toAbsolutePath().normalize()
        require(Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) && Files.size(source) > 0L) {
            "Nie znaleziono poprawnego pliku kopii: $source"
        }
        require(!Files.exists(target) || !Files.isSameFile(source, target)) {
            "Kopia nie może być bieżącą bazą danych"
        }
        Files.createDirectories(target.parent)
        acquireLock(target).use {
            validateSnapshot(source)
            val staging = Files.createTempFile(target.parent, ".cw-restore-", ".sqlite")
            var rescue: Path? = null
            val wal = target.resolveSibling(target.fileName.toString() + "-wal")
            val shm = target.resolveSibling(target.fileName.toString() + "-shm")
            try {
                Files.copy(source, staging, StandardCopyOption.REPLACE_EXISTING)
                privateFile(staging)
                validateSnapshot(staging)

                val oldFiles = listOf(target, wal, shm).filter { Files.exists(it) }
                if (oldFiles.isNotEmpty()) {
                    rescue = Files.createTempDirectory(target.parent, "cw-pre-restore-")
                    for (old in oldFiles) {
                        Files.copy(old, rescue.resolve(old.fileName), StandardCopyOption.COPY_ATTRIBUTES)
                    }
                }
                try {
                    // Pozostały WAL starej bazy nie może zostać odczytany z nowym plikiem.
                    Files.deleteIfExists(wal)
                    Files.deleteIfExists(shm)
                    replaceDatabase(staging, target)
                } catch (failure: Exception) {
                    // Przed wymianą DB odtwarzamy pliki pomocnicze, jeśli rename się nie udał.
                    if (Files.exists(staging) && rescue != null) {
                        for (sidecar in listOf(wal, shm)) {
                            val saved = rescue.resolve(sidecar.fileName)
                            if (Files.exists(saved)) {
                                Files.copy(saved, sidecar, StandardCopyOption.REPLACE_EXISTING)
                            }
                        }
                    }
                    throw failure
                }
                return rescue
            } finally {
                Files.deleteIfExists(staging)
            }
        }
    }

    /** Zatrzymuje jednoczesny start serwera i operację przywracania na tym samym wolumenie. */
    fun acquireLock(database: Path): DatabaseProcessLock {
        val target = database.toAbsolutePath().normalize()
        Files.createDirectories(target.parent)
        val lockPath = target.resolveSibling(target.fileName.toString() + ".server.lock")
        val channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        try {
            val lock = try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            }
            if (lock == null) error("Serwer korzysta z bazy; najpierw zatrzymaj leaderboard")
            return DatabaseProcessLock(channel, lock)
        } catch (failure: Exception) {
            channel.close()
            throw failure
        }
    }

    private fun validateSnapshot(file: Path) {
        // mode=ro chroni przed utworzeniem pustej bazy z literówki w ścieżce.
        val url = "jdbc:sqlite:file:${file.toUri().rawPath}?mode=ro"
        DriverManager.getConnection(url).use { db ->
            db.createStatement().use { statement ->
                statement.executeQuery("PRAGMA integrity_check").use { rs ->
                    check(rs.next() && rs.getString(1) == "ok" && !rs.next()) {
                        "Kopia nie przechodzi PRAGMA integrity_check: $file"
                    }
                }
                statement.executeQuery("""
                    SELECT COUNT(*) FROM sqlite_master
                    WHERE type = 'table' AND name IN ('players', 'scores', 'schema_version')
                """.trimIndent()).use { rs ->
                    check(rs.next() && rs.getInt(1) == 3) {
                        "Kopia nie zawiera schematu rankingu: $file"
                    }
                }
            }
        }
    }

    private fun privateFile(file: Path) {
        try {
            Files.setPosixFilePermissions(file, privatePermissions)
        } catch (_: UnsupportedOperationException) {
            // Windows nie obsługuje POSIX; pozostają uprawnienia utworzonego pliku.
        }
    }

    private fun moveNew(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target)
        }
    }

    private fun replaceDatabase(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

internal class DatabaseProcessLock(
    private val channel: FileChannel,
    private val fileLock: FileLock,
) : Closeable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            try {
                fileLock.release()
            } finally {
                channel.close()
            }
        }
    }
}
