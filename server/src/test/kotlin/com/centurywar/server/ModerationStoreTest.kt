package com.centurywar.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import java.util.UUID

class ModerationStoreTest {
    private val now = 1_800_000_000_000L

    private fun input(player: String = UUID.randomUUID().toString(), nick: String = "Gracz") =
        ScoreSubmission(
            board = "endless", player = player, nick = nick, gameVersion = "0.6.0",
            seed = 42L, replay = "nieistotny", score = 100L, research = List(13) { 0 },
        )

    @Test fun blockHidesEveryScorePersistsAndUnblockRestores() {
        val db = Files.createTempFile("cw-block-", ".sqlite").toFile()
        val jdbc = "jdbc:sqlite:" + db.absolutePath
        val first = input(nick = "Pierwszy")
        val other = input(nick = "Drugi")
        try {
            SqliteStore(jdbc).use { store ->
                store.save(first, VerifiedReplay("endless", 200L, 900), now, "hash-a")
                store.save(first, VerifiedReplay("campaign_7", 99L, 900), now, "hash-b")
                store.save(other, VerifiedReplay("endless", 100L, 900), now, "hash-c")
                assertEquals(2, store.top("endless", "all", now).size)
                assertTrue(store.blockPlayer(first.player))
                assertFalse(store.blockPlayer(first.player))
                assertEquals(listOf(store.publicIdFor(other.player)), store.top("endless", "all", now).map { it.player })
                assertTrue(store.around("endless", "all", now, first.player).isEmpty())
                assertEquals(1L, store.around("endless", "all", now, other.player).single().rank)
                assertTrue(store.top("campaign_7", "all", now).isEmpty())
                assertThrows(BlockedPlayerException::class.java) {
                    store.save(first, VerifiedReplay("endless", 300L, 900), now, "hash-d")
                }
                assertEquals(3, store.recentScores().size)
                assertEquals(2, store.recentScores().count { it.blocked })
            }
            SqliteStore(jdbc).use { store ->
                assertTrue(store.isBlocked(first.player))
                assertEquals(1, store.top("endless", "all", now).size)
                assertTrue(store.unblockPlayer(first.player))
                assertFalse(store.unblockPlayer(first.player))
                assertEquals(2, store.top("endless", "all", now).size)
                assertEquals(1, store.top("campaign_7", "all", now).size)
                store.blockPlayer(first.player)
                store.deletePlayer(first.player)
                assertTrue(store.isBlocked(first.player)) // Usunięcie danych nie zdejmuje blokady.
                assertTrue(store.around("endless", "all", now, first.player).isEmpty())
            }
        } finally {
            db.delete()
            File(db.path + "-wal").delete()
            File(db.path + "-shm").delete()
        }
    }

    @Test fun oldOffensiveNicknameIsMaskedDuringReading() {
        val db = Files.createTempFile("cw-oldnick-", ".sqlite").toFile()
        val jdbc = "jdbc:sqlite:" + db.absolutePath
        val first = input()
        try {
            SqliteStore(jdbc).use { store ->
                store.save(first, VerifiedReplay("endless", 100L, 900), now, "hash")
                DriverManager.getConnection(jdbc).use { external ->
                    external.prepareStatement("UPDATE players SET nick = ? WHERE player_id = ?").use {
                        it.setString(1, "F.U.C.K")
                        it.setString(2, first.player)
                        it.executeUpdate()
                    }
                }
                assertEquals(NickModeration.fallbackNick(first.player), store.top("endless", "all", now).single().nick)
            }
        } finally {
            db.delete()
            File(db.path + "-wal").delete()
            File(db.path + "-shm").delete()
        }
    }

    @Test fun invalidNicknameIsReplacedAtPersistenceBoundary() {
        SqliteStore("jdbc:sqlite::memory:").use { store ->
            val player = input(nick = "k.u.r.w.@")
            store.save(player, VerifiedReplay("endless", 100L, 900), now, "hash")
            assertEquals(NickModeration.fallbackNick(player.player), store.top("endless", "all", now).single().nick)
            assertEquals(NickModeration.fallbackNick(player.player), store.recentScores().single().nick)
        }
    }
}
