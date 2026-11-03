package com.centurywar.engine

import android.animation.ValueAnimator
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/** R2-03: ustawienia dostępności telefonu odczytane na wątku UI. */
internal class SystemA11y(val fontScale: Float, val animationsOff: Boolean, val touchExploration: Boolean)

internal object SystemAccessibility {
    /** Wątek UI: skala czcionki, wyłączone animacje i TalkBack (eksploracja dotykiem). */
    fun read(context: Context): SystemA11y {
        val fontScale = context.resources.configuration.fontScale.takeIf { it.isFinite() && it > 0f } ?: 1f
        val durationOff = try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (_: Exception) {
            false
        }
        val animationsOff = !ValueAnimator.areAnimatorsEnabled() || durationOff
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        return SystemA11y(fontScale, animationsOff, manager?.isTouchExplorationEnabled == true)
    }

    /** Rozmiar tekstu gry (100/115/130) dla systemowej skali czcionki. */
    fun textScaleFor(fontScale: Float): Int = when {
        fontScale >= 1.27f -> 130
        fontScale >= 1.12f -> 115
        else -> 100
    }
}
