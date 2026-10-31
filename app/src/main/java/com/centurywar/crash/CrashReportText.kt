package com.centurywar.crash

/** Buduje raport bez wiadomości wyjątków, argumentów metod i danych profilu. */
object CrashReportText {
    fun build(
        version: String,
        model: String,
        android: String,
        screen: String,
        events: String,
        exception: Throwable?,
    ): String {
        val out = StringBuilder(4096)
        out.append("Century War - raport techniczny\n")
        out.append("Wersja: ").append(version).append('\n')
        out.append("Urządzenie: ").append(model).append('\n')
        out.append("Android: ").append(android).append('\n')
        out.append("Ekran: ").append(screen).append("\n\n")
        out.append("Ostatnie zdarzenia (maks. 30):\n").append(events).append('\n')
        out.append("Stos wywołań (bez treści wyjątku):\n")
        if (exception == null) {
            out.append("Brak zapisanego wyjątku.\n")
        } else {
            var current: Throwable? = exception
            var depth = 0
            while (current != null && depth < 4) {
                out.append(current.javaClass.name).append('\n')
                val frames = current.stackTrace
                for (i in 0 until minOf(frames.size, 60)) {
                    val frame = frames[i]
                    out.append("  at ").append(frame.className).append('.')
                        .append(frame.methodName).append('(')
                        .append(frame.fileName ?: "Unknown").append(':')
                        .append(frame.lineNumber).append(")\n")
                }
                current = current.cause
                depth++
                if (current != null && depth < 4) out.append("Przyczyna:\n")
            }
        }
        return out.toString().take(CrashStore.MAX_CHARS)
    }
}
