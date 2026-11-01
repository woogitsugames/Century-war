package com.centurywar.meta

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * Przenośna kopia profilu. Plik nie zawiera powtórek, historii bitew ani zapisu bitwy.
 * Format jest lokalny: żadna metoda nie korzysta z sieci ani uprawnień do pamięci.
 */
object ProfileBackup {
    const val MIME = "application/json"
    const val FILE_NAME = "century-war-profile.cwprofile"
    const val MAX_FILE_BYTES = 4 * 1024 * 1024
    const val MAX_PROFILE_BYTES = 512 * 1024
    private const val MAGIC = "century-war-profile"
    private const val FORMAT_VERSION = 1
    private val HEX = "0123456789abcdef".toCharArray()

    /** Eksport korzysta z Profile.toCloudJson(), aby nie ujawniać sekretu rankingu. */
    fun export(profile: Profile): ByteArray = encode(profile.toCloudJson())

    /** Używane również do wewnętrznej kopii sprzed importu (z lokalnymi ustawieniami). */
    fun encode(profileJson: String): ByteArray {
        val raw = profileJson.toByteArray(Charsets.UTF_8)
        require(raw.size in 1..MAX_PROFILE_BYTES) { "Profil jest zbyt duży" }
        require(Profile.isValidJson(profileJson)) { "Niepoprawny profil" }
        val version = JSONObject(profileJson).optInt("v", -1)
        require(version in 1..Profile.CURRENT_VERSION) { "Nieobsługiwana wersja profilu" }
        val envelope = JSONObject()
            .put("format", MAGIC)
            .put("formatVersion", FORMAT_VERSION)
            .put("profileVersion", version)
            .put("length", raw.size)
            .put("sha256", sha256(raw))
            .put("profile", profileJson)
            .toString()
            .toByteArray(Charsets.UTF_8)
        require(envelope.size <= MAX_FILE_BYTES) { "Kopia jest zbyt duża" }
        return envelope
    }

    /** Limit czytania jest stosowany przed parsowaniem JSON, także dla obcego dostawcy URI. */
    fun decode(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read == 0) {
                val one = input.read()
                if (one < 0) break
                require(output.size() < MAX_FILE_BYTES) { "Kopia jest zbyt duża" }
                output.write(one)
                continue
            }
            require(output.size() + read <= MAX_FILE_BYTES) { "Kopia jest zbyt duża" }
            output.write(buffer, 0, read)
        }
        val envelope = JSONObject(output.toString(Charsets.UTF_8.name()))
        require(envelope.opt("format") == MAGIC) { "Nieznany format kopii" }
        require(envelope.opt("formatVersion") is Number &&
            envelope.optInt("formatVersion") == FORMAT_VERSION) { "Nieobsługiwany format kopii" }
        val version = envelope.opt("profileVersion")
        require(version is Number && version.toInt() in 1..Profile.CURRENT_VERSION) {
            "Nieobsługiwana wersja profilu"
        }
        val payload = envelope.opt("profile")
        require(payload is String) { "Brak profilu" }
        val raw = payload.toByteArray(Charsets.UTF_8)
        require(raw.size in 1..MAX_PROFILE_BYTES) { "Profil jest zbyt duży" }
        val length = envelope.opt("length")
        require(length is Number && length.toInt() == raw.size) { "Niepoprawny rozmiar profilu" }
        val checksum = envelope.opt("sha256")
        require(checksum is String && checksum.length == 64 && checksum == sha256(raw)) {
            "Niezgodna suma kontrolna"
        }
        require(Profile.isValidJson(payload)) { "Uszkodzony profil" }
        require(JSONObject(payload).optInt("v", -1) == version.toInt()) { "Niezgodna wersja profilu" }
        // Sprawdź także semantykę i migrację bez dotykania aktywnego profilu.
        Profile(File("profile-backup-validation")).fromJson(payload)
        return payload
    }

    /** Trwała kopia stanu sprzed importu, tylko w prywatnym katalogu aplikacji. */
    fun saveSafetyCopy(directory: File, currentJson: String): File {
        val bytes = encode(currentJson)
        require(directory.isDirectory || directory.mkdirs()) { "Brak katalogu kopii" }
        val target = File(directory, "before-import-" + System.currentTimeMillis() +
            "-" + UUID.randomUUID() + ".cwprofile")
        val temporary = File(directory, target.name + ".tmp")
        try {
            FileOutputStream(temporary).use { out ->
                out.write(bytes)
                out.flush()
                out.fd.sync()
            }
            // Nie deklarujemy sukcesu, gdy pliku nie da się bezpiecznie przenieść.
            if (!temporary.renameTo(target)) {
                java.nio.file.Files.move(temporary.toPath(), target.toPath())
            }
        } finally {
            temporary.delete()
        }
        val previous = directory.listFiles()
            ?.filter { it.isFile && it.name.startsWith("before-import-") && it.name.endsWith(".cwprofile") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
        for (old in previous.drop(5)) old.delete()
        return target
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val result = CharArray(digest.size * 2)
        for (i in digest.indices) {
            val value = digest[i].toInt() and 0xff
            result[i * 2] = HEX[value ushr 4]
            result[i * 2 + 1] = HEX[value and 0x0f]
        }
        return String(result)
    }
}
