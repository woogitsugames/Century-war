package com.centurywar

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import android.graphics.Color
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import com.centurywar.engine.Game
import com.centurywar.engine.GameView
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
import com.centurywar.engine.Sound
import com.centurywar.engine.Music
import com.centurywar.game.SnapshotStore
import com.centurywar.game.RestoredBattle
import com.centurywar.game.Daily
import com.centurywar.meta.Profile
import com.centurywar.meta.ReplayStore
import com.centurywar.meta.BattleHistory
import com.centurywar.meta.ScoreClient
import com.centurywar.engine.L
import com.centurywar.notifications.DailyReminders
import com.centurywar.meta.PlayGamesLeaderboards
import java.io.File
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Jedyna Activity. Obsługuje zmiany konfiguracji sama (configChanges w manifeście),
 * więc składanie/rozkładanie Folda, multi-window i obrót nie restartują gry,
 * a jedynie zmieniają rozmiar powierzchni.
 */
class MainActivity : Activity(), Host {

    companion object {
        /** Od tej najmniejszej szerokości (dp) ekran jest „duży”: rozłożony Fold, tablet. */
        const val LARGE_SCREEN_DP = 600
    }

    private lateinit var game: Game
    private lateinit var view: GameView
    private var vibrator: Vibrator? = null
    private var playGames: PlayGamesLeaderboards? = null
    private val gameBackCallback by lazy { OnBackInvokedCallback { game.requestBack() } }
    private var gameBackRegistered = false
    private var resumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder()
                .detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build())
        }
        applyOrientation(resources.configuration)
        setContentView(TextView(this).apply {
            text = L["bh_loading"]
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.BLACK)
        })
        // Profil i zrzut są potrzebne przed pierwszą klatką, ale nigdy nie czytamy ich na UI.
        val app = applicationContext
        Thread({
            val root = app.filesDir
            val profile = Profile(File(root, "profile.json")).also { it.load() }
            profile.pruneDailyBoards(Daily.dayNumber())
            val snapshots = SnapshotStore(File(root, "battle.snap"))
            val restored = snapshots.loadBattle()
            runOnUiThread {
                if (!isFinishing && !isDestroyed) finishStartup(root, profile, snapshots, restored)
            }
        }, "startup-io").apply { isDaemon = true; priority = Thread.MIN_PRIORITY; start() }
    }

    private fun finishStartup(root: File, profile: Profile, snapshots: SnapshotStore, restored: RestoredBattle?) {
        game = Game(this, profile, Quality.detect(this))
        game.history = BattleHistory(File(root, "history.json")).also { history ->
            history.loadAsync { game.post { game.historyLoaded = true } }
        }
        game.density = resources.displayMetrics.density
        game.sound = Sound(this)
        // Nie wystawiamy zgody bez skonfigurowanego HTTPS i polityki prywatności.
        val privacyOk = runCatching {
            val policy = URI(BuildConfig.CW_PRIVACY_URL)
            policy.scheme == "https" && !policy.host.isNullOrEmpty()
        }.getOrDefault(false)
        if (privacyOk && ScoreClient.configured(BuildConfig.CW_SERVER_URL, BuildConfig.CW_HMAC_KEY)) {
            game.scores = ScoreClient(File(root, "score-queue.json"),
                BuildConfig.CW_SERVER_URL, BuildConfig.CW_HMAC_KEY, profile)
        }
        game.applySfxVolume()
        if ((application as? CenturyWarApp)?.playGamesReady == true) {
            try {
                playGames = PlayGamesLeaderboards(this, game)
                game.online = playGames!!
            } catch (_: RuntimeException) {
                // Niepowodzenie PGS nie blokuje uruchomienia gry.
            }
        }
        game.music = Music().also { it.setVolume(profile.musicVolume) }
        // Przerwana bitwa (Android zabił aplikację w tle): menu zaproponuje jej wznowienie.
        game.snapshots = snapshots
        game.replays = ReplayStore(File(root, "replays"))
        game.resumeOffer = restored
        view = GameView(this, game)
        if (!pendingRenderScale.isNaN()) view.setRenderScale(pendingRenderScale)
        if (pendingFrameRate >= 0) view.setFrameRate(pendingFrameRate)
        setContentView(view)

        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes.layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= 30)
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        view.setOnApplyWindowInsetsListener { _, insets ->
            val (l, t, r, b) = safeInsets(insets)
            game.post { game.setInsets(l, t, r, b) }
            insets
        }
        applyFrameRate(profile.fpsCap)
        hideSystemBars()
        if (resumed) resumeGame()
    }

    private fun safeInsets(insets: WindowInsets): IntArray {
        return if (Build.VERSION.SDK_INT >= 30) {
            val i = insets.getInsets(WindowInsets.Type.displayCutout() or WindowInsets.Type.systemGestures())
            val cut = insets.getInsets(WindowInsets.Type.displayCutout())
            // Gesty systemowe przy krawędziach: zostawiamy margines tylko z boków, gdzie są wycięcia.
            intArrayOf(maxOf(cut.left, 0), cut.top, maxOf(cut.right, 0), maxOf(cut.bottom, i.bottom / 3))
        } else if (Build.VERSION.SDK_INT >= 28) {
            val c = insets.displayCutout
            intArrayOf(c?.safeInsetLeft ?: 0, c?.safeInsetTop ?: 0, c?.safeInsetRight ?: 0, c?.safeInsetBottom ?: 0)
        } else intArrayOf(0, 0, 0, 0)
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (::game.isInitialized) resumeGame()
    }

    private fun resumeGame() {
        view.onResume()
        // Powrót z tła następnego dnia: seria logowań i nowe misje (na wątku gry).
        game.post { game.checkDailyLogin() }
        val app = applicationContext
        Thread({ DailyReminders.refresh(app) }, "reminder-io").apply {
            isDaemon = true; priority = Thread.MIN_PRIORITY; start()
        }
        playGames?.refreshSignIn()
        game.music?.resume()
    }

    private fun remainingSaveMs(deadline: Long): Long =
        ((deadline - System.nanoTime()).coerceAtLeast(0L) + 999_999L) / 1_000_000L

    override fun onPause() {
        resumed = false
        if (!::game.isInitialized) {
            super.onPause()
            return
        }
        game.music?.pause()
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(300)
        val done = CountDownLatch(1)
        val ran = AtomicBoolean(false)
        val task = Runnable {
            if (ran.compareAndSet(false, true)) {
                try { game.current?.onPause(); game.profile.save() } finally { done.countDown() }
            }
        }
        if (view.loopActive) {
            game.post(task)
            try { done.await(remainingSaveMs(deadline), TimeUnit.MILLISECONDS) }
            catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        }
        view.onPause()
        // Gdy pętla nie pracuje, możemy bezpiecznie wykonać migawkę w pamięci na UI.
        if (!view.loopActive) task.run()
        // Tylko oczekiwanie jest synchroniczne; fsync i odczyt plików pracują poza UI.
        game.profile.awaitWrites(remainingSaveMs(deadline))
        game.snapshots?.awaitWrites(remainingSaveMs(deadline))
        game.history?.awaitWrites(remainingSaveMs(deadline))
        game.replays?.awaitWrites(remainingSaveMs(deadline))
        super.onPause()
    }

    /** Na Androidzie 13+ brak callbacku w menu pozwala systemowi animować wyjście. */
    override fun onWantsBackChanged(wantsBack: Boolean) {
        if (Build.VERSION.SDK_INT < 33) return
        runOnUiThread {
            if (isFinishing || isDestroyed || wantsBack == gameBackRegistered) return@runOnUiThread
            if (wantsBack) {
                onBackInvokedDispatcher.registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, gameBackCallback)
            } else {
                onBackInvokedDispatcher.unregisterOnBackInvokedCallback(gameBackCallback)
            }
            gameBackRegistered = wantsBack
        }
    }

    override fun onDestroy() {
        if (!::game.isInitialized) {
            super.onDestroy()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && gameBackRegistered) {
            onBackInvokedDispatcher.unregisterOnBackInvokedCallback(gameBackCallback)
            gameBackRegistered = false
        }
        playGames?.close()
        game.scores?.close()
        game.music?.release()
        game.history?.close()
        game.sound?.release()
        super.onDestroy()
    }

    @Deprecated("Starsze Androidy (< 13)")
    @android.annotation.SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        game.requestBack()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == DailyReminders.PERMISSION_REQUEST_CODE) {
            DailyReminders.permissionResult(this, grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
            hideSystemBars()
        }
    }

    // ---------------------------------------------------------------- Host

    override fun onBattleWon() {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) DailyReminders.afterVictory(this)
        }
    }

    override fun onNotificationSettingsChanged() {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) DailyReminders.afterVictory(this)
        }
    }

    override fun requestNotifyHour(current: Int, onResult: (Int) -> Unit) {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            TimePickerDialog(this, { _, hour, _ ->
                game.post { onResult(hour) }
            }, current.coerceIn(0, 23), 0, true).apply {
                setOnDismissListener { hideSystemBars() }
            }.show()
        }
    }

    override fun requestGlobalRankingConsent(onAccepted: () -> Unit) {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            AlertDialog.Builder(this)
                .setTitle(L["global_consent_title"])
                .setMessage(L["global_consent_desc"])
                .setPositiveButton(L["global_allow"]) { _, _ -> game.post { onAccepted() } }
                .setNegativeButton(L["global_cancel"], null)
                .setNeutralButton(L["global_privacy"]) { _, _ ->
                    val uri = Uri.parse(BuildConfig.CW_PRIVACY_URL)
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                }
                .setOnDismissListener { hideSystemBars() }
                .show()
        }
    }

    override fun vibrate(ms: Long) {
        val v = vibrator ?: return
        try {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) { }
    }

    override fun requestText(title: String, current: String, onResult: (String) -> Unit) {
        runOnUiThread {
            // Wynik wywołania z wątku gry może przyjść, gdy Activity już się zamyka (BadTokenException).
            if (isFinishing || isDestroyed) return@runOnUiThread
            val edit = EditText(this).apply {
                setText(current)
                filters = arrayOf(InputFilter.LengthFilter(16))
                setSingleLine()
            }
            AlertDialog.Builder(this)
                .setTitle(title)
                .setView(edit)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val s = edit.text.toString().trim()
                    if (s.isNotEmpty()) game.post { onResult(s) }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .setOnDismissListener { hideSystemBars() }
                .show()
        }
    }

    // Game() woła applyRenderScale w konstruktorze, zanim powstanie GameView: wtedy tylko
    // zapamiętujemy wartość (dawniej: UninitializedPropertyAccessException i crash przy starcie).
    private var pendingRenderScale = Float.NaN
    private var pendingFrameRate = -1

    /**
     * Telefony: tylko poziom (manifest: sensorLandscape). Duże, prawie kwadratowe ekrany
     * (rozłożony Fold, tablet): dowolna orientacja, inaczej system potrafi uznać za „poziom”
     * inne ułożenie niż to, w którym gracz trzyma urządzenie, i obraz jest obrócony o 90°.
     */
    private fun applyOrientation(cfg: Configuration) {
        val want = if (cfg.smallestScreenWidthDp >= LARGE_SCREEN_DP) ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        if (requestedOrientation != want) requestedOrientation = want
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Złożenie/rozłożenie Folda nie restartuje Activity (configChanges), więc orientację ustawiamy tutaj.
        applyOrientation(newConfig)
        // supportsRtl=false: nie przestawiamy układu; tylko teksty dla języka Auto.
        // Bitwa i jej stan pozostają na tym samym wątku gry.
        if (::game.isInitialized) game.post {
            if (game.profile.lang.isEmpty()) game.onLanguageChanged()
        }
    }

    override fun applyRenderScale(scale: Float) {
        runOnUiThread { if (::view.isInitialized) view.setRenderScale(scale) else pendingRenderScale = scale }
    }

    override fun applyFrameRate(fps: Int) {
        runOnUiThread { if (::view.isInitialized) view.setFrameRate(fps) else pendingFrameRate = fps }
    }

    override fun exit() {
        runOnUiThread { finish() }
    }
}
