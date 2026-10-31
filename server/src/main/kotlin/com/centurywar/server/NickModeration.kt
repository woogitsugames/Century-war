package com.centurywar.server

import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

/** Moderacja po stronie serwera; ani oryginalny nick, ani słownik nie trafiają do klienta. */
internal object NickModeration {
    private val spaces = Regex(" +")
    private val marks = Regex("\\p{M}+")

    private val forbidden: Set<String> by lazy {
        val stream = checkNotNull(javaClass.getResourceAsStream("/forbidden-nicks.txt")) {
            "Brak zasobu forbidden-nicks.txt"
        }
        stream.bufferedReader(Charsets.UTF_8).use { reader ->
            reader.lineSequence()
                .map { it.substringBefore('#').trim() }
                .filter { it.isNotEmpty() }
                .map { normalize(it).replace(" ", "") }
                .filter { it.isNotEmpty() }
                .toSet()
        }.also { check(it.isNotEmpty()) { "Pusty słownik moderacji" } }
    }

    /** NFKD + małe litery + zamiana cyfr, znaków i podobnych glifów. */
    internal fun normalize(value: String): String {
        val plain = marks.replace(
            Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFKD), ""
        )
        val result = StringBuilder(plain.length)
        for (ch in plain) {
            val mapped = when (ch) {
                '0' -> 'o'
                '1', '!', '|' -> 'i'
                '3' -> 'e'
                '4', '@' -> 'a'
                '5', '$' -> 's'
                '7' -> 't'
                '8' -> 'b'
                // Podobne litery cyrylicy i greki w nickach mieszanych.
                'а', 'α' -> 'a'
                'е', 'ε' -> 'e'
                'о', 'ο' -> 'o'
                'р', 'ρ' -> 'p'
                'с' -> 'c'
                'у' -> 'y'
                'υ' -> 'u'
                'і', 'ι' -> 'i'
                'к', 'κ' -> 'k'
                'х', 'χ' -> 'x'
                'т', 'τ' -> 't'
                'м' -> 'm'
                'н' -> 'h'
                'ѕ' -> 's'
                'ј' -> 'j'
                else -> ch
            }
            result.append(if (mapped.isLetterOrDigit()) mapped else ' ')
        }
        return spaces.replace(result.toString().trim(), " ")
    }

    /** Sprawdza wyrazy i ich rozdzielone spacjami/znakami warianty. */
    internal fun forbiddenNick(nick: String): Boolean {
        val normalized = normalize(nick)
        if (normalized.isEmpty()) return false
        val parts = normalized.split(' ')
        for (part in parts) {
            if (part in forbidden) return true
            val withoutTrailingDigits = part.dropLastWhile { it.isDigit() }
            if (withoutTrailingDigits.length != part.length && withoutTrailingDigits in forbidden) return true
        }
        for (start in parts.indices) {
            val combined = StringBuilder()
            for (end in start until minOf(parts.size, start + 24)) {
                combined.append(parts[end])
                if (combined.length > 24) break
                if (end > start && combined.toString() in forbidden) return true
            }
        }
        return false
    }

    /** Zastępuje również nick niezgodny z dozwolonym zestawem znaków. */
    internal fun publicNick(nick: String, playerId: String): String =
        if (validNick(nick) && !forbiddenNick(nick)) nick else fallbackNick(playerId)

    internal fun fallbackNick(playerId: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(playerId.toByteArray(Charsets.UTF_8))
        val digits = "0123456789ABCDEF"
        return buildString(12) {
            append("Dowódca-")
            for (i in 0..1) {
                val octet = hash[i].toInt() and 0xff
                append(digits[octet ushr 4])
                append(digits[octet and 15])
            }
        }
    }
}
