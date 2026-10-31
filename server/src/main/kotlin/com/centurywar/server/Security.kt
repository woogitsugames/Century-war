package com.centurywar.server

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

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
    ): Boolean {
        val epoch = timestamp?.toLongOrNull() ?: return false
        val now = Math.floorDiv(nowMillis(), 1000L)
        if (abs(now - epoch) > 300L) return false
        if (nonce == null || nonce.length != 36 ||
            runCatching { UUID.fromString(nonce).toString() == nonce }.getOrDefault(false).not()
        ) return false
        if (signature == null || !signature.matches(Regex("[0-9a-f]{64}"))) return false
        val signed = listOf(timestamp, nonce, method, target, body).joinToString("\n")
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
