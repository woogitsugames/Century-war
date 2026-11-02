package com.centurywar.screens

/** Liczby i zegar w prealokowanej tablicy, bez tworzenia String w klatce. */
internal class HudDigits152 {
    val chars = CharArray(16)
    var length = 0
        private set

    private fun appendUnsigned(value: Long) {
        var n = value
        val start = length
        do {
            chars[length++] = ('0'.code + (n % 10L).toInt()).toChar()
            n /= 10L
        } while (n > 0L)
        var lo = start
        var hi = length - 1
        while (lo < hi) {
            val tmp = chars[lo]
            chars[lo] = chars[hi]
            chars[hi] = tmp
            lo++
            hi--
        }
    }

    fun setNumber(value: Int) {
        length = 0
        val number = value.toLong()
        if (number < 0L) chars[length++] = '-'
        appendUnsigned(if (number < 0L) -number else number)
    }

    fun setClock(seconds: Int) {
        length = 0
        val s = seconds.coerceAtLeast(0)
        appendUnsigned(s.toLong() / 60L)
        chars[length++] = ':'
        chars[length++] = ('0'.code + s % 60 / 10).toChar()
        chars[length++] = ('0'.code + s % 10).toChar()
    }
}
