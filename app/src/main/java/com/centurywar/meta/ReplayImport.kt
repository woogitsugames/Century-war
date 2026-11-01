package com.centurywar.meta

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Ogranicza odczyt niezaufanego strumienia przed dekodowaniem powtórki. */
object ReplayImport {
    const val MAX_BYTES = ReplayStore.MAX_RECORD_BYTES

    fun readLimited(input: InputStream): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return out.toByteArray().takeIf { it.size >= 24 }
            // Wadliwy dostawca strumienia nie może zapętlić wątku importu.
            if (count == 0) return null
            if (out.size() > MAX_BYTES - count) return null
            out.write(buffer, 0, count)
        }
    }
}
