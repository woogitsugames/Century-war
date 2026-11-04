package com.centurywar.crash

import android.annotation.TargetApi
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.centurywar.BuildConfig
import java.io.InputStream
import java.time.Instant

/**
 * R2-05: przyczyna poprzedniego zakończenia procesu z `ApplicationExitInfo` (API 30+).
 * ANR, błędy natywne i zabicie z braku pamięci są dziś widoczne tylko w Android vitals;
 * zapisujemy je lokalnie w [CrashStore]. Nic nie jest wysyłane automatycznie.
 * Wołane na wątku I/O przy starcie, przed [CrashReports.pendingFatal]: ANR i awaria natywna
 * są zapisywane jako `exit-fatal-…` i liczą się jak awaria, więc obejmuje je istniejące pytanie
 * o raport. Pozostałe przyczyny trafiają do `exit-…` z osobnym limitem (R6-02 M1, [CrashStore]).
 *
 * R6-02 M2: pierwsze uruchomienie bez znacznika [LAST_EXIT_TS] (instalacja albo aktualizacja do
 * wersji z tą funkcją) tylko zapamiętuje najnowszy wpis i nic nie zapisuje; wpisy sprzed
 * `lastUpdateTime` pakietu też pomijamy (np. po wyczyszczeniu danych), żeby stare ANR nie pytały
 * o raport.
 */
object ExitReasons {
    const val LAST_EXIT_TS = "last_exit_ts"
    /** Wpisy z systemu: najwyżej tyle ostatnich zakończeń procesu. */
    const val MAX_EXITS = 5
    /** Wycinek śladu ANR (wątek main i wątek gry). */
    const val TRACE_CHARS = 8_000
    /** Ślad ANR potrafi mieć megabajty; dalej nie czytamy. */
    private const val TRACE_SCAN_CHARS = 2_000_000
    private const val GAME_THREAD = "game-loop"

    /** Zwraca liczbę zapisanych raportów. */
    fun collect(context: Context, store: CrashStore, prefs: SharedPreferences): Int {
        if (Build.VERSION.SDK_INT < 30) return 0
        return collect30(context, store, prefs)
    }

    /** Które przyczyny zapisujemy (wersja testowalna bez obiektów systemu). */
    fun worthReporting(reason: Int, importance: Int): Boolean = when (reason) {
        ApplicationExitInfo.REASON_ANR,
        ApplicationExitInfo.REASON_CRASH_NATIVE,
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> true
        // R6-02 M1: zabicie z braku pamięci lub za zużycie zasobów w tle to normalna praca
        // systemu; liczy się tylko, gdy gracz widział grę.
        ApplicationExitInfo.REASON_LOW_MEMORY,
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE ->
            importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
        // Sygnał (np. zabicie przez system) liczy się tylko na pierwszym planie.
        ApplicationExitInfo.REASON_SIGNALED ->
            importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        // USER_REQUESTED, EXIT_SELF, CRASH (Java łapie UncaughtExceptionHandler) i pozostałe.
        else -> false
    }

    fun isFatal(reason: Int): Boolean =
        reason == ApplicationExitInfo.REASON_ANR || reason == ApplicationExitInfo.REASON_CRASH_NATIVE

    fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        else -> "OTHER"
    }

    @TargetApi(30)
    private fun collect30(context: Context, store: CrashStore, prefs: SharedPreferences): Int {
        val am = context.getSystemService(ActivityManager::class.java) ?: return 0
        val exits = try {
            am.getHistoricalProcessExitReasons(context.packageName, 0, MAX_EXITS)
        } catch (_: RuntimeException) {
            return 0
        }
        if (!prefs.contains(LAST_EXIT_TS)) {
            // Pierwszy start z tą funkcją: tylko znacznik, bez raportów o dawnych zdarzeniach.
            prefs.edit().putLong(LAST_EXIT_TS, exits.maxOfOrNull { it.timestamp } ?: 0L).apply()
            return 0
        }
        val last = prefs.getLong(LAST_EXIT_TS, 0L)
        val updated = lastUpdateTime(context)
        var newest = last
        var written = 0
        for (info in exits.sortedBy { it.timestamp }) {
            val ts = info.timestamp
            if (ts <= last) continue
            if (ts > newest) newest = ts
            if (ts < updated) continue
            if (!worthReporting(info.reason, info.importance)) continue
            try {
                store.write(isFatal(info.reason), report(info), exit = true, eventTime = ts)
                written++
            } catch (_: Exception) {
                // Brak miejsca: raport przepada, start gry trwa dalej.
            }
        }
        // Znacznik zapisujemy także dla pominiętych wpisów, żeby nie oglądać ich przy każdym starcie.
        if (newest != last) prefs.edit().putLong(LAST_EXIT_TS, newest).apply()
        return written
    }

    private fun lastUpdateTime(context: Context): Long = try {
        context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
    } catch (_: Exception) {
        0L
    }

    @TargetApi(30)
    private fun report(info: ApplicationExitInfo): String {
        val out = StringBuilder(2048)
        out.append("Century War - raport techniczny (zakończenie procesu)\n")
        out.append("Wersja: ").append(BuildConfig.VERSION_NAME).append(" (")
            .append(BuildConfig.VERSION_CODE).append(")\n")
        out.append("Urządzenie: ").append((Build.MANUFACTURER + " " + Build.MODEL).take(100)).append('\n')
        out.append("Android: ").append((Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")").take(100))
            .append("\n\n")
        out.append("Przyczyna: ").append(reasonName(info.reason)).append(" (").append(info.reason).append(")\n")
        out.append("Opis: ").append(info.description?.take(300) ?: "-").append('\n')
        out.append("Ważność procesu: ").append(info.importance).append('\n')
        out.append("PSS: ").append(info.pss).append(" kB, RSS: ").append(info.rss).append(" kB\n")
        out.append("Czas: ").append(Instant.ofEpochMilli(info.timestamp)).append('\n')
        if (info.reason == ApplicationExitInfo.REASON_ANR) {
            out.append("\nŚlad ANR (wątki main i ").append(GAME_THREAD).append("):\n")
            val trace = try { info.traceInputStream?.use { anrThreads(it) } } catch (_: Exception) { null }
            out.append(if (trace.isNullOrEmpty()) "Brak śladu.\n" else trace)
        }
        return out.toString().take(CrashStore.MAX_CHARS)
    }

    /**
     * Z pełnego zrzutu wątków wybiera bloki `"main"` i `"game-loop"` (od nagłówka do pustej
     * linii), najwyżej [TRACE_CHARS] znaków. Inne wątki (biblioteki) pomijamy.
     */
    fun anrThreads(input: InputStream): String {
        val out = StringBuilder(TRACE_CHARS)
        var scanned = 0
        var inside = false
        input.bufferedReader(Charsets.UTF_8).use { reader ->
            while (out.length < TRACE_CHARS && scanned < TRACE_SCAN_CHARS) {
                val line = reader.readLine() ?: break
                scanned += line.length + 1
                if (!inside) {
                    if (line.startsWith("\"main\"") || line.startsWith("\"$GAME_THREAD\"")) inside = true
                    else continue
                }
                if (line.isBlank()) {
                    inside = false
                    out.append('\n')
                    continue
                }
                out.append(line).append('\n')
            }
        }
        return if (out.length > TRACE_CHARS) out.substring(0, TRACE_CHARS) else out.toString()
    }
}
