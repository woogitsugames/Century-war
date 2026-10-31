package com.centurywar

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputFilter
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.EditText
import android.window.OnBackInvokedDispatcher
import com.centurywar.engine.Game
import com.centurywar.engine.GameView
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
import com.centurywar.engine.Sound
import com.centurywar.meta.Profile
import java.io.File

/**
 * Jedyna Activity. Obsługuje zmiany konfiguracji sama (configChanges w manifeście),
 * więc składanie/rozkładanie Folda, multi-window i obrót nie restartują gry,
 * a jedynie zmieniają rozmiar powierzchni.
 */
class MainActivity : Activity(), Host {

    private lateinit var game: Game
    private lateinit var view: GameView
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val profile = Profile(File(filesDir, "profile.json")).also { it.load() }
        game = Game(this, profile, Quality.detect(this))
        game.density = resources.displayMetrics.density
        game.sound = Sound(this)
        view = GameView(this, game)
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
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
                game.requestBack()
            }
        }
        applyFrameRate(profile.fpsCap)
        hideSystemBars()
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
        view.onResume()
    }

    override fun onPause() {
        game.post { game.current?.onPause(); game.profile.save() }
        view.onPause()
        game.profile.save()
        super.onPause()
    }

    /** Tylko Android < 13; nowsze używają OnBackInvokedCallback zarejestrowanego w onCreate. */
    override fun onDestroy() {
        game.sound?.release()
        super.onDestroy()
    }

    @Deprecated("Starsze Androidy (< 13)")
    @android.annotation.SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        game.requestBack()
    }

    // ---------------------------------------------------------------- Host

    override fun vibrate(ms: Long) {
        val v = vibrator ?: return
        try {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) { }
    }

    override fun requestText(title: String, current: String, onResult: (String) -> Unit) {
        runOnUiThread {
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

    override fun applyRenderScale(scale: Float) {
        runOnUiThread { view.setRenderScale(scale) }
    }

    override fun applyFrameRate(fps: Int) {
        runOnUiThread { view.setFrameRate(fps) }
    }

    override fun exit() {
        runOnUiThread { finish() }
    }
}
