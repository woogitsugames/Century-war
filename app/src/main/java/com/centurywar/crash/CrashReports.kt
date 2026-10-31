package com.centurywar.crash

import android.content.Context
import android.os.Build
import com.centurywar.BuildConfig
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Dziennik lokalny. Żaden raport nie opuszcza urządzenia bez akcji gracza. */
object CrashReports {
    private const val PREFS = "crash_report_offer"
    private const val OFFERED = "last_offered"
    private val screenPattern = Regex("[A-Za-z0-9_]{1,48}Screen")
    private val events = CrashEventRing()
    private val caughtRecorded = AtomicBoolean(false)
    @Volatile private var store: CrashStore? = null
    @Volatile private var currentScreen = "Startup"

    @Synchronized
    fun install(context: Context) {
        if (store != null) return
        store = CrashStore(File(context.filesDir, "crash"))
        events.add(CrashEventRing.START)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Awaria jest wyjątkiem od reguły I/O poza pętlą: trzeba zapisać ją przed zamknięciem procesu.
            try { save(fatal = true, exception = error) } catch (_: Throwable) { }
            previous?.uncaughtException(thread, error)
        }
    }

    fun screenChanged(name: String) {
        // Dopuszczamy jedynie nazwy klas ekranów, nie tytuły ani teksty od gracza.
        val safe = if (screenPattern.matches(name)) name else "OtherScreen"
        currentScreen = safe
        events.add(CrashEventRing.SCREEN, screen = safe)
    }

    fun battleStarted(stage: Int) {
        events.add(CrashEventRing.BATTLE, value = stage.coerceIn(0, 999))
    }

    fun paused() { events.add(CrashEventRing.PAUSE) }
    fun resumed() { events.add(CrashEventRing.RESUME) }

    /** Złapany błąd ramki zapisujemy w osobnym wątku, najwyżej raz na proces. */
    fun frameError(error: Exception) {
        events.add(CrashEventRing.FRAME_ERROR)
        if (!caughtRecorded.compareAndSet(false, true)) return
        Thread({
            try { save(fatal = false, exception = error) } catch (_: Throwable) { }
        }, "crash-io").apply { isDaemon = true; priority = Thread.MIN_PRIORITY; start() }
    }

    private fun snapshot(error: Throwable?): String = CrashReportText.build(
        version = BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")",
        model = (Build.MANUFACTURER + " " + Build.MODEL).take(100),
        android = (Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")").take(100),
        screen = currentScreen,
        events = events.snapshot(),
        exception = error,
    )

    private fun save(fatal: Boolean, exception: Throwable) {
        store?.write(fatal, snapshot(exception))
    }

    /** Wołane na wątku I/O przy uruchamianiu, nie podczas renderowania. */
    fun pendingFatal(context: Context): String? {
        val recent = store?.newest(fatalOnly = true) ?: return null
        val offered = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(OFFERED, null)
        return recent.name.takeUnless { it == offered }
    }

    /** Jedno pytanie po awarii; odmowa nie wyzwoli pytania przy każdym następnym uruchomieniu. */
    fun markOffered(context: Context, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(OFFERED, name).apply()
    }

    /** Odczyt tylko na wątku I/O. Gdy nie ma awarii, wysyłamy bieżący stan diagnostyczny. */
    fun sharingText(preferredName: String? = null): String {
        val log = store
        val name = preferredName ?: log?.newest()?.name
        val saved = if (name == null) null else runCatching { log?.read(name) }.getOrNull()
        return saved ?: snapshot(null)
    }
}
