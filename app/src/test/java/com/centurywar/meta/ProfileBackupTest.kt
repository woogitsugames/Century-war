package com.centurywar.meta

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files

/** Testy formatu przenośnego i zachowania kopii sprzed importu. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfileBackupTest {
    private fun profile(): Profile =
        Profile(File(Files.createTempDirectory("cw-backup").toFile(), "profile.json"))

    private fun decode(bytes: ByteArray): String =
        ProfileBackup.decode(ByteArrayInputStream(bytes))

    private fun rejected(bytes: ByteArray) {
        assertTrue("Niepoprawna kopia powinna być odrzucona",
            runCatching { decode(bytes) }.isFailure)
    }

    @Test fun roundTripRestoresProgressWithoutExportingIdentity() {
        val source = profile()
        source.name = "Testowy"
        source.medals = 1234
        source.stars[0] = 3
        val researchKey = Profile.research.first { it.max >= 2 }.key
        source.levels[researchKey] = 2
        source.enableGlobalRanking()
        val identity = source.playerId
        val secret = source.playerSecret
        val bytes = ProfileBackup.export(source)
        val fileText = String(bytes, Charsets.UTF_8)
        assertFalse(fileText.contains(identity))
        assertFalse(fileText.contains(secret))
        assertFalse(fileText.contains("playerSecret"))
        assertFalse(fileText.contains("globalRanking"))

        val restored = profile()
        restored.fromJson(decode(bytes))
        assertEquals("Testowy", restored.name)
        assertEquals(1234, restored.medals)
        assertEquals(3, restored.stars[0])
        assertEquals(2, restored.levels[researchKey])
        assertFalse(restored.globalRanking)
        assertEquals("", restored.playerId)
    }

    @Test fun importRetainsIdentityAndConsentOfTargetDevice() {
        val local = profile()
        local.enableGlobalRanking()
        val id = local.playerId
        val secret = local.playerSecret
        val remote = profile()
        remote.medals = 987
        local.fromJson(decode(ProfileBackup.export(remote)))
        assertEquals(987, local.medals)
        assertEquals(id, local.playerId)
        assertEquals(secret, local.playerSecret)
        assertTrue(local.globalRanking)
    }

    @Test fun corruptChecksumIsRejected() {
        val bytes = ProfileBackup.export(profile())
        val envelope = JSONObject(String(bytes, Charsets.UTF_8))
        val raw = JSONObject(envelope.getString("profile"))
        raw.put("medals", 555)
        envelope.put("profile", raw.toString())
        // Ten sam rozmiar; pomimo pozornie poprawnego JSON musi zawieść suma.
        envelope.put("length", raw.toString().toByteArray(Charsets.UTF_8).size)
        rejected(envelope.toString().toByteArray(Charsets.UTF_8))
    }

    @Test fun truncatedAndGarbageFilesAreRejected() {
        val full = ProfileBackup.export(profile())
        rejected(full.copyOfRange(0, full.size / 2))
        rejected(byteArrayOf(0, 1, 2, 3))
        rejected(byteArrayOf())
    }

    @Test fun futureAndMismatchedVersionsAreRejected() {
        val full = ProfileBackup.export(profile())
        val envelope = JSONObject(String(full, Charsets.UTF_8))
        envelope.put("formatVersion", 2)
        rejected(envelope.toString().toByteArray(Charsets.UTF_8))
        envelope.put("formatVersion", 1)
        envelope.put("profileVersion", Profile.CURRENT_VERSION + 1)
        rejected(envelope.toString().toByteArray(Charsets.UTF_8))
        envelope.put("profileVersion", 1)
        rejected(envelope.toString().toByteArray(Charsets.UTF_8))
    }

    @Test fun oversizedInputIsRejectedBeforeParsing() {
        rejected(ByteArray(ProfileBackup.MAX_FILE_BYTES + 1) { 'A'.code.toByte() })
    }

    @Test fun legacyProfileMigratesCampaignStars() {
        val old = profile()
        old.stars[8] = 3
        old.stars[9] = 2
        val legacy = JSONObject(old.toJson()).put("v", 2).toString()
        val bytes = ProfileBackup.encode(legacy)
        val restored = profile()
        restored.fromJson(decode(bytes))
        assertEquals(0, restored.stars[8])
        assertEquals(0, restored.stars[9])
        assertEquals(3, restored.stars[12])
        assertEquals(2, restored.stars[13])
    }

    @Test fun earliestVersionOneCanBeImportedAndMigrated() {
        val old = profile()
        old.medals = 41
        old.stars[8] = 1
        val versionOne = JSONObject(old.toJson()).put("v", 1).toString()
        val restored = profile()
        restored.fromJson(decode(ProfileBackup.encode(versionOne)))
        assertEquals(41, restored.medals)
        assertEquals(0, restored.stars[8])
        assertEquals(1, restored.stars[12])
    }

    @Test fun safetyCopyIsReadableAndSurvivesChangeOfCurrentProfile() {
        val folder = Files.createTempDirectory("cw-safety").toFile()
        val source = profile()
        source.medals = 700
        source.enableGlobalRanking()
        val safe = ProfileBackup.saveSafetyCopy(folder, source.toJson())
        assertTrue(safe.isFile)
        source.medals = 5
        val restored = profile()
        restored.fromJson(safe.inputStream().use { ProfileBackup.decode(it) })
        assertEquals(700, restored.medals)
        assertTrue(safe.name.endsWith(".cwprofile"))
    }

    @Test fun safetyCopiesKeepOnlyFiveLatestFiles() {
        val folder = Files.createTempDirectory("cw-safety-limit").toFile()
        val current = profile().toJson()
        repeat(7) { ProfileBackup.saveSafetyCopy(folder, current) }
        assertEquals(5, folder.listFiles()!!.count { it.name.endsWith(".cwprofile") })
    }
    @Test fun invalidProfileIsRejectedEvenWithCorrectChecksum() {
        val malformed = JSONObject(profile().toJson()).put("medals", "nie liczba")
        // Kodowanie sprawdza ogólny kształt; odczyt musi dodatkowo walidować typy.
        rejected(ProfileBackup.encode(malformed.toString()))
    }

    @Test fun safetyCopyFailsWhenDirectoryCannotBeCreated() {
        val fileInsteadOfDirectory = Files.createTempFile("cw-no-folder", ".tmp").toFile()
        assertTrue(runCatching {
            ProfileBackup.saveSafetyCopy(fileInsteadOfDirectory, profile().toJson())
        }.isFailure)
    }

    /** Import nie może zostać cofnięty przez równoległy zapis tego samego profilu (107a). */
    @Test fun trackedSaveSurvivesConcurrentLaterSave() {
        val source = profile()
        source.medals = 777
        val imported = source.toJson()
        val file = File(Files.createTempDirectory("cw-backup").toFile(), "profile.json")
        val target = Profile(file)
        target.fromJson(imported)
        val importSeq = target.saveTracked()
        // Równoległa zmiana i zapis (np. ekran ustawień) – plik różni się od migawki importu.
        target.musicVolume = 0.25f
        target.save()
        assertTrue(target.awaitWrites(5_000L))
        assertTrue(target.isPersisted(importSeq))
        val onDisk = JSONObject(file.readText(Charsets.UTF_8))
        assertEquals(777, onDisk.getInt("medals"))
    }

    @Test fun trackedSaveNotPersistedWhenWriteFails() {
        // Katalog nadrzędny nie istnieje: zapis nie może się udać.
        val missing = File(Files.createTempDirectory("cw-backup").toFile(), "brak/profile.json")
        val target = Profile(missing)
        val seq = target.saveTracked()
        assertTrue(target.awaitWrites(5_000L))
        assertFalse(target.isPersisted(seq))
    }
}
