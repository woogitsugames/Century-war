package com.centurywar.server

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class SecurityTest {
    private val secret = ByteArray(32) { it.toByte() }
    private val now = 1_800_000_000_000L
    private val nonce = "2f1c9b12-9420-44bf-81ac-11c10e6db407"

    private fun signature(timestamp: String, nonce: String = this.nonce): String {
        val message = listOf(timestamp, nonce, "GET", "/v1/test", "").joinToString("\n")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        return mac.doFinal(message.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private fun checkAuth(timestamp: String?, nonce: String? = this.nonce, sig: String? = null): Boolean =
        SqliteStore("jdbc:sqlite::memory:").use { store ->
            RequestAuthenticator(secret, store) { now }.valid(
                "GET", "/v1/test", "", timestamp, nonce,
                sig ?: timestamp?.let { signature(it) }, false)
        }

    @Test fun validSignature() = assertTrue(checkAuth("1800000000"))
    @Test fun wrongSignature() = assertFalse(checkAuth("1800000000", sig = "0".repeat(64)))
    @Test fun oldestAllowedTimestamp() = assertTrue(checkAuth("1799999700"))
    @Test fun expiredTimestamp() = assertFalse(checkAuth("1799999699"))
    @Test fun latestAllowedTimestamp() = assertTrue(checkAuth("1800000300"))
    @Test fun futureTimestamp() = assertFalse(checkAuth("1800000301"))
    @Test fun emptyTimestamp() = assertFalse(checkAuth(""))
    @Test fun tooLongTimestamp() = assertFalse(checkAuth("9".repeat(100)))
    @Test fun emptyNonce() = assertFalse(checkAuth("1800000000", nonce = ""))
    @Test fun emptySignature() = assertFalse(checkAuth("1800000000", sig = ""))
    @Test fun tooLongSignature() = assertFalse(checkAuth("1800000000", sig = "a".repeat(65)))

    @Test fun rateLimitAndResetAtMinuteBoundary() {
        var clock = 1_000_000L
        val limiter = RateLimiter({ clock })
        assertTrue(limiter.allow("a", 2)); assertTrue(limiter.allow("a", 2))
        assertFalse(limiter.allow("a", 2))
        clock += 59_000; assertFalse(limiter.allow("a", 2))
        clock += 1_000; assertTrue(limiter.allow("a", 2))
    }
    @Test fun forgettingAKeyResetsOnlyThatKey() {
        val limiter = RateLimiter({ now })
        assertTrue(limiter.allow("a", 1)); assertTrue(limiter.allow("b", 1))
        limiter.forget("a")
        assertTrue(limiter.allow("a", 1)); assertFalse(limiter.allow("b", 1))
    }
    @Test fun capacityEvictsLeastRecentlyUsed() {
        val limiter = RateLimiter({ now }, 2)
        assertTrue(limiter.allow("a", 1)); assertTrue(limiter.allow("b", 1))
        assertFalse(limiter.allow("a", 1)) // odświeża kolejność LRU
        assertTrue(limiter.allow("c", 1)); assertFalse(limiter.allow("a", 1))
        assertTrue(limiter.allow("b", 1))
    }
    @Test fun playerIdIsDeterministicForKeyAndPlayer() {
        assertEquals(PublicPlayerIds(secret).of("alice"), PublicPlayerIds(secret).of("alice"))
    }
    @Test fun playerIdsDifferByKey() {
        assertNotEquals(PublicPlayerIds(secret).of("alice"), PublicPlayerIds(ByteArray(32) { 7 }).of("alice"))
    }
    @Test fun publicIdFormat() {
        assertTrue(PublicPlayerIds.valid(PublicPlayerIds(secret).of("alice")))
        listOf("", "A".repeat(24), "g".repeat(24), "a".repeat(23), "a".repeat(25))
            .forEach { assertFalse(PublicPlayerIds.valid(it)) }
    }
    @Test fun installSecretFormatAndHash() {
        assertTrue(validInstallSecret("00".repeat(32)))
        assertFalse(validInstallSecret(null)); assertFalse(validInstallSecret("AA".repeat(32)))
        assertFalse(validInstallSecret("0".repeat(63)))
        assertEquals("66687aadf862bd776c8fc18b8e9f8e20089714856ee233b3902a591d0d5f2925", installSecretHash("00".repeat(32)))
    }
    @Test fun trustedProxyAcceptsOneForwardedAddress() =
        assertEquals("203.0.113.5", ClientAddress().resolve("127.0.0.1", "203.0.113.5"))
    @Test fun trustedProxyUsesFirstAddress() =
        assertEquals("203.0.113.5", ClientAddress().resolve("127.0.0.1", " 203.0.113.5, 192.0.2.1"))
    @Test fun untrustedProxyIgnoresForwardedAddress() =
        assertEquals("192.0.2.1", ClientAddress().resolve("192.0.2.1", "203.0.113.5"))
    @Test fun invalidForwardedAddressFallsBack() =
        assertEquals("127.0.0.1", ClientAddress().resolve("127.0.0.1", "invalid!"))
}
