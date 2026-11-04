package com.centurywar.server

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Okresy przechowywania danych rankingu w dniach (R5, luka L1); `0` wyłącza dany krok.
 * Wartości domyślne są propozycją do zatwierdzenia przez właściciela (`docs/SERVER_DEPLOY.md` §11).
 */
data class RetentionPolicy(
    /** Wyniki tablic dnia (`daily`, liczone od dnia wyzwania) i tygodnia (`weekly_<n>`, od końca tygodnia). */
    val periodDays: Int = 90,
    /** Zgłoszenia nicków, liczone od `reported_at`. */
    val reportDays: Int = 180,
    /** Gracz bez nowego wyniku i bez aktualizacji profilu przez tyle dni jest usuwany w całości. */
    val inactiveDays: Int = 730,
    /** Dziennik usunięć i dziennik działań operatora. */
    val logDays: Int = 400,
) {
    init {
        for ((name, value) in listOf(
            "periodDays" to periodDays, "reportDays" to reportDays,
            "inactiveDays" to inactiveDays, "logDays" to logDays,
        )) require(value in 0..MAX_DAYS) { "$name must be in 0..$MAX_DAYS" }
    }

    companion object {
        const val MAX_DAYS = 36_500

        /** Zmienne `CW_RETENTION_*`; brak zmiennej = wartość domyślna, `0` = wyłączone. */
        fun fromEnv(env: (String) -> String? = System::getenv): RetentionPolicy {
            val defaults = RetentionPolicy()
            fun days(name: String, fallback: Int): Int {
                val text = env(name)?.trim()?.takeIf { it.isNotEmpty() } ?: return fallback
                val value = requireNotNull(text.toIntOrNull()) { "$name must be an integer" }
                require(value in 0..MAX_DAYS) { "$name must be in 0..$MAX_DAYS" }
                return value
            }
            return RetentionPolicy(
                periodDays = days("CW_RETENTION_PERIOD_DAYS", defaults.periodDays),
                reportDays = days("CW_RETENTION_REPORT_DAYS", defaults.reportDays),
                inactiveDays = days("CW_RETENTION_INACTIVE_DAYS", defaults.inactiveDays),
                logDays = days("CW_RETENTION_LOG_DAYS", defaults.logDays),
            )
        }
    }
}

/** Liczby wierszy usuniętych (albo do usunięcia przy [dryRun]) przez [SqliteStore.purgeExpired]. */
data class PurgeResult(
    val dailyScores: Int,
    val weeklyScores: Int,
    val nickReports: Int,
    val inactivePlayers: Int,
    val expiredBlocks: Int,
    val deletionLog: Int,
    val adminLog: Int,
    val quotas: Int,
    val nonces: Int,
    val dryRun: Boolean,
) {
    /** Wiersze z danymi graczy (bez technicznych limitów i `nonce`). */
    val personalRows: Int
        get() = dailyScores + weeklyScores + nickReports + inactivePlayers + expiredBlocks + deletionLog + adminLog

    fun summary(): String =
        "daily_scores=$dailyScores weekly_scores=$weeklyScores nick_reports=$nickReports " +
            "inactive_players=$inactivePlayers expired_blocks=$expiredBlocks " +
            "deletion_log=$deletionLog admin_log=$adminLog quotas=$quotas nonces=$nonces"
}

/**
 * Retencja przy starcie serwera i raz na dobę. Błąd jednego przebiegu nie zatrzymuje serwera
 * ani kolejnych przebiegów. Zamykane przy zatrzymaniu aplikacji.
 */
class RetentionScheduler(
    private val store: SqliteStore,
    private val policy: RetentionPolicy,
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = { println(it) },
) : AutoCloseable {
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "cw-retention").apply { isDaemon = true }
    }

    fun start(initialDelayMillis: Long = 0L, periodMillis: Long = TimeUnit.DAYS.toMillis(1)): RetentionScheduler {
        executor.scheduleWithFixedDelay(::runOnce, initialDelayMillis, periodMillis, TimeUnit.MILLISECONDS)
        return this
    }

    internal fun runOnce() {
        try {
            val now = clock()
            val result = store.purgeExpired(now, policy)
            if (result.personalRows > 0) store.logAdmin("purge", null, "auto " + result.summary(), now)
            // Tylko liczby: żadnych identyfikatorów w logach kontenera.
            log("retention: " + result.summary())
        } catch (e: Exception) {
            log("retention_failed: " + e.javaClass.simpleName + ": " + e.message)
        }
    }

    override fun close() {
        executor.shutdownNow()
        executor.awaitTermination(5, TimeUnit.SECONDS)
    }
}
