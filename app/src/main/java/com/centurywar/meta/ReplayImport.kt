package com.centurywar.meta

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Ogranicza odczyt niezaufanego strumienia przed dekodowaniem powtórki. */
object ReplayImport {
    const val MAX_BYTES = 2 * 1024 * 1024

    fun readLimited(input: InputStream): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return out.toByteArray()
            if (count == 0) continue
            if (out.size() > MAX_BYTES - count) return null
            out.write(buffer, 0, count)
        }
    }
}
