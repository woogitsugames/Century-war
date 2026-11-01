package com.centurywar.server

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.net.InetAddress
import java.util.UUID
/** Sekret HMAC w APK można wydobyć; podpis jest jedynie barierą przeciw nadużyciom. */
class RequestAuthenticator(
    private val secret: ByteArray,
    private val store: SqliteStore,
    private val nowMillis: () -> Long,
) {
    init { require(secret.size >= 32) { "CW_HMAC_KEY must contain at least 32 UTF-8 bytes" } }

    fun valid(
        method: String,
        target: String,
        body: String,
        timestamp: String?,
        nonce: String?,
        signature: String?,
        consumeNonce: Boolean,
        installationSecret: String? = null,
    ): Boolean {
        if (installationSecret != null && !validInstallSecret(installationSecret)) return false
        val epoch = timestamp?.toLongOrNull() ?: return false
        val now = Math.floorDiv(nowMillis(), 1000L)
        if (epoch < now - 300L || epoch > now + 300L) return false
        if (nonce == null || nonce.length != 36 ||
            runCatching { UUID.fromString(nonce).toString() == nonce }.getOrDefault(false).not()
        ) return false
        if (signature == null || !signature.matches(Regex("[0-9a-f]{64}"))) return false
        val signed = listOf(timestamp, nonce, method, target, body).joinToString("\n") +
            (if (installationSecret != null) "\n" + installationSecret else "")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        val expected = mac.doFinal(signed.toByteArray(StandardCharsets.UTF_8))
        val actual = ByteArray(32) { i ->
            signature.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        if (!MessageDigest.isEqual(expected, actual)) return false
        return !consumeNonce || store.consumeNonce(nonce, now)
    }
}

/** Limity w pamięci jednej instancji. Limit na reverse proxy nadal jest konieczny. */
class RateLimiter(private val nowMillis: () -> Long, private val maxEntries: Int = 20_000) {
    private class Slot(var first: Long, var count: Int)
    private val slots = LinkedHashMap<String, Slot>(256, 0.75f, true)

    /** Wyzerowanie limitu identyfikatora po skutecznym usunięciu danych. */
    @Synchronized
    fun forget(key: String) {
        slots.remove(key)
    }

    @Synchronized
    fun allow(key: String, perMinute: Int): Boolean {
        val now = Math.floorDiv(nowMillis(), 1000L)
        var slot = slots[key]
        if (slot == null) {
            if (slots.size >= maxEntries) {
                val iter = slots.entries.iterator()
                if (iter.hasNext()) { iter.next(); iter.remove() }
            }
            slot = Slot(now, 0)
            slots[key] = slot
        }
        if (now < slot.first || now - slot.first >= 60L) {
            slot.first = now
            slot.count = 0
        }
        if (slot.count >= perMinute) return false
        slot.count++
        return true
    }
}


// Sekret instalacji jest w formacie hex, nie przechowujemy jego oryginalnych bajtów w SQLite.
private val INSTALL_SECRET_RE = Regex("[0-9a-f]{64}")
internal fun validInstallSecret(value: String?): Boolean =
    value != null && INSTALL_SECRET_RE.matches(value)

internal fun installSecretHash(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
        .joinToString("") { "%02x".format(it.toInt() and 255) }

/** Klucz mapowania ID MUSI pozostać wyłącznie na serwerze, niezależnie od klucza APK. */
class PublicPlayerIds(private val key: ByteArray) {
    init { require(key.size >= 32) { "CW_PUBLIC_ID_KEY must be at least 32 bytes" } }

    fun of(player: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(player.toByteArray(StandardCharsets.UTF_8))
            .take(12).joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    companion object {
        fun temporary(): PublicPlayerIds = PublicPlayerIds(ByteArray(32).also { SecureRandom().nextBytes(it) })
        fun valid(publicId: String): Boolean = publicId.matches(Regex("[0-9a-f]{24}"))
    }
}

/** X-Forwarded-For czytamy wyłącznie od jawnie zaufanego lokalnego proxy. */
class ClientAddress(private val trustedProxies: Set<String> = setOf("127.0.0.1")) {
    fun resolve(remote: String, forwarded: String?): String {
        if (remote !in trustedProxies || forwarded == null) return remote
        val candidate = forwarded.substringBefore(',').trim()
        if (candidate.length !in 3..45 || !candidate.matches(Regex("[0-9a-fA-F:.]+"))) return remote
        return try {
            val parsed = InetAddress.getByName(candidate)
            if (parsed.hostAddress.isNotEmpty()) parsed.hostAddress else remote
        } catch (_: Exception) { remote }
    }
}

data class RateLimits(
    val postIp: Int = 20,
    val deleteIp: Int = 20,
    val readIp: Int = 120,
    val reportIp: Int = 5,
    val postPlayer: Int = 6,
) {
    init { require(listOf(postIp, deleteIp, readIp, reportIp, postPlayer).all { it > 0 }) }
}
