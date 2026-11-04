package com.centurywar.testloop

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import com.centurywar.BuildConfig
import com.centurywar.engine.Game
import com.centurywar.engine.GameView
import com.centurywar.engine.Host
import com.centurywar.engine.Music
import com.centurywar.engine.Quality
import com.centurywar.engine.Sound
import com.centurywar.game.SimVersion
import com.centurywar.game.SnapshotStore
import com.centurywar.meta.Profile
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * R11-04: pętla gry dla raportu przedpremierowego Google Play i Firebase Test Lab
 * (intencja `com.google.intent.action.TEST_LOOP`, numer scenariusza w `scenario`, plik wyników
 * pod adresem z intencji). Osobna Activity w osobnym procesie (`:testloop`), więc zwykły start
 * [com.centurywar.MainActivity] pozostaje bez zmian.
 *
 * Izolacja: profil, zrzut bitwy i wszystko inne tylko w `cacheDir/testloop/` (czyszczonym na
 * starcie). Bez Play Games, rankingu online, przypomnień, raportów awarii, powtórek i historii
 * gracza; [Game.testLoop] blokuje usługi sieciowe. Wynik trafia tylko pod URI z intencji
 * (sprawdzony w [TestLoopIntent]) i do logcat `CWTestLoop`.
 */
class TestLoopActivity : Activity(), Host {

    companion object {
        const val TAG = "CWTestLoop"
        const val DIR = "testloop"
        /** Zapas ponad limit scenariusza, gdyby pętla klatek stanęła (brak powierzchni). */
        private const val WATCHDOG_EXTRA_MS = 15_000L
        private val running = AtomicBoolean(false)

        /** Testy: krótsze fazy scenariuszy. */
        @Volatile internal var timingOverride: GameLoopTiming? = null
        /** Testy: ostatni wynik (JSON) zapisany przez dowolną pętlę w tym procesie. */
        @Volatile internal var lastResult: String? = null
            private set

        internal fun loopDir(context: Context): File = File(context.cacheDir, DIR)
    }

    internal lateinit var game: Game
        private set
    private lateinit var view: GameView
    private var music: Music? = null
    private var sound: Sound? = null
    private var owner = false
    private var resumed = false
    private var scenario = 1
    private var resultUri: Uri? = null
    private var runner: GameLoopRunner? = null
    private val reported = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())
    private val watchdog = Runnable { report(GameLoopReport(scenario).apply { reason = "watchdog" }, "timeout") }
    private var pendingRenderScale = Float.NaN
    private var pendingFrameRate = -1

    internal val gameReady: Boolean get() = ::game.isInitialized
    internal val runnerFinished: Boolean get() = runner?.finished == true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!TestLoopIntent.isTestLoop(intent)) { finish(); return }
        if (!running.compareAndSet(false, true)) {
            // Druga pętla w tym samym procesie: nie mieszamy pomiarów ani plików.
            Log.w(TAG, "Pętla już działa; ignoruję kolejną intencję")
            finish()
            return
        }
        owner = true
        scenario = TestLoopIntent.scenario(intent)
        val dir = loopDir(this)
        resultUri = TestLoopIntent.resultUri(intent, packageName, protectedDirs(), dir)
        val timing = timingOverride ?: GameLoopTiming()
        handler.postDelayed(watchdog, (timing.limitS * 1000f).toLong() + WATCHDOG_EXTRA_MS)
        if (!TestLoopIntent.validScenario(scenario)) {
            report(GameLoopReport(scenario).apply { reason = "unknown_scenario" }, "error")
            return
        }
        val app = applicationContext
        Thread({
            dir.deleteRecursively()
            dir.mkdirs()
            val profile = Profile(File(dir, "profile.json")).also { it.load() }
            // Pusty profil telefonu testowego: samouczek, dymki i zapowiedzi epok nie mogą
            // zablokować scenariusza; Co nowego i przypomnienia wyłączone.
            profile.tutorialDone = true
            profile.hintsEnabled = false
            profile.eraIntrosEnabled = false
            profile.notifications = false
            profile.whatsNewSeenVersion = BuildConfig.VERSION_NAME
            val quality = Quality.detect(app)
            val memoryClassMb = (app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
                .memoryClass.coerceAtLeast(1)
            runOnUiThread {
                if (!isFinishing && !isDestroyed) start(profile, quality, memoryClassMb, dir, timing)
            }
        }, "testloop-start").apply { isDaemon = true; start() }
    }

    /** Katalogi z danymi gracza: plik wyników nigdy tam nie trafi. */
    private fun protectedDirs(): List<File> = listOfNotNull(
        applicationInfo?.dataDir?.let { File(it) }, filesDir, cacheDir,
        runCatching { getExternalFilesDir(null)?.parentFile }.getOrNull(),
    )

    private fun start(profile: Profile, quality: Int, memoryClassMb: Int, dir: File, timing: GameLoopTiming) {
        game = Game(this, profile, quality)
        game.testLoop = true
        game.bitmapCacheMemoryClassMb = memoryClassMb
        game.density = resources.displayMetrics.density
        game.snapshots = SnapshotStore(File(dir, "battle.snap"))
        // Muzyka i efekty zostają: to też obciążenie do zmierzenia (fokus oddajemy przy końcu).
        music = runCatching {
            Music(assets, getSystemService(android.media.AudioManager::class.java))
                .also { it.setVolume(profile.musicVolume) }
        }.getOrNull()
        game.music = music
        view = GameView(this, game)
        view.onFirstFrame = { runOnUiThread { if (!isFinishing && !isDestroyed) startAudio() } }
        if (!pendingRenderScale.isNaN()) view.setRenderScale(pendingRenderScale)
        if (pendingFrameRate >= 0) view.setFrameRate(pendingFrameRate)
        setContentView(view)
        hideSystemBars()
        val r = GameLoopRunner(
            game, scenario, timing,
            pssKb = { runCatching { Debug.getPss() }.getOrDefault(0L) },
            startupMs = { SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime() },
            onDone = { report(it, it.result) },
        )
        runner = r
        game.post { r.start() }
        if (resumed) view.onResume()
    }

    private fun startAudio() {
        if (resumed) music?.resume()
        val app = applicationContext
        Thread({
            val s = try { Sound(app) } catch (_: RuntimeException) { return@Thread }
            runOnUiThread {
                if (isFinishing || isDestroyed || !::game.isInitialized) s.release()
                else { sound = s; game.sound = s; game.applySfxVolume() }
            }
        }, "testloop-audio").apply { isDaemon = true; start() }
    }

    /** Dowolny wątek; pierwszy wynik wygrywa (scenariusz, strażnik albo przerwanie). */
    private fun report(r: GameLoopReport, result: String) {
        if (!reported.compareAndSet(false, true)) return
        r.result = result
        val json = GameLoopResult.json(r, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE,
            SimVersion.PROTOCOL, Build.MANUFACTURER + " " + Build.MODEL, Build.VERSION.SDK_INT)
        val target = resultUri
        Thread({
            Log.i(TAG, json)
            if (target != null) writeResult(target, json)
            lastResult = json
            runOnUiThread { if (!isFinishing) finish() }
        }, "testloop-result").apply { isDaemon = true; start() }
    }

    private fun writeResult(uri: Uri, json: String) {
        try {
            val pfd = try { contentResolver.openFileDescriptor(uri, "wt") }
                catch (_: Exception) { contentResolver.openFileDescriptor(uri, "w") }
            if (pfd == null) { Log.w(TAG, "Brak pliku wyników"); return }
            ParcelFileDescriptor.AutoCloseOutputStream(pfd).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        } catch (e: Exception) {
            Log.w(TAG, "Nie udało się zapisać wyników: " + e.javaClass.simpleName)
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (::game.isInitialized) {
            view.onResume()
            music?.resume()
        }
    }

    override fun onPause() {
        resumed = false
        if (::game.isInitialized) {
            music?.pause()
            view.onPause()
        }
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(watchdog)
        // Zamknięte przed końcem (Wstecz, system): wynik „error”, żeby plik nie został pusty.
        if (owner && !reported.get()) report(GameLoopReport(scenario).apply { reason = "aborted" }, "error")
        if (::game.isInitialized) {
            music?.release()
            sound?.release()
        }
        if (owner) running.set(false)
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        }
    }

    // ---------------------------------------------------------------- Host (bez usług gracza)

    override fun vibrate(ms: Long) {}
    override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
    override fun applyRenderScale(scale: Float) {
        runOnUiThread { if (::view.isInitialized) view.setRenderScale(scale) else pendingRenderScale = scale }
    }
    override fun applyFrameRate(fps: Int) {
        runOnUiThread { if (::view.isInitialized) view.setFrameRate(fps) else pendingFrameRate = fps }
    }
    override fun exit() {
        runOnUiThread { if (!isFinishing) finish() }
    }
}
