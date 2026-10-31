package com.centurywar.game.art

import kotlin.math.max
import kotlin.math.sin

/** Przeliczenia animacji wizualnej: anim = droga w świecie * 0,09. */
internal object Motion {
    const val PHASE_PER_WORLD = 0.09f
    const val PI = 3.1415927f
    const val TAU = 6.2831855f

    /** Odległość w lokalnych jednostkach grafiki, po uwzględnieniu UnitDef.size. */
    fun distance(walk: Float, size: Float): Float = walk / (PHASE_PER_WORLD * size)

    /** Obrót bez poślizgu: odległość / promień; modulo ogranicza błędy trygonometrii. */
    fun wheelAngle(walk: Float, size: Float, radius: Float): Float =
        distance(walk, size) / radius % TAU

    /** Pozycja ogniwa na zamkniętej bieżni. */
    fun trackOffset(walk: Float, size: Float, step: Float): Float {
        val offset = distance(walk, size) % step
        return if (offset < 0f) offset + step else offset
    }

    /** Krótki impuls i łagodniejszy powrót przez 0,25 sekundy po strzale. */
    fun recoil(atk: Float): Float {
        val t = 1f - atk.coerceIn(0f, 1f)
        if (t <= 0f) return 0f
        if (t < 0.14f) return t / 0.14f
        val rem = (1f - t) / 0.86f
        return rem * rem * (1.25f - 0.25f * rem)
    }

    /** Pochylenie kadłuba ze słabym odbiciem w przeciwnym kierunku pod koniec powrotu. */
    fun hullTilt(atk: Float): Float {
        val t = 1f - atk.coerceIn(0f, 1f)
        val overshoot = if (t > 0.58f && t < 1f)
            -sin((t - 0.58f) / 0.42f * PI) * (1f - t) * 1.3f else 0f
        return recoil(atk) * 2.2f + overshoot
    }

    /** Podparta stopa przesuwa się w lokalnym układzie dokładnie o -drogę / size. */
    fun mechFootX(phase: Float, size: Float): Float {
        var a = phase % TAU
        if (a < 0f) a += TAU
        val halfStep = PI / (2f * PHASE_PER_WORLD * size)
        return if (a < PI) halfStep - a / (PHASE_PER_WORLD * size)
        else -halfStep + (a - PI) / (PHASE_PER_WORLD * size)
    }

    /** W fazie podparcia stopa nie podnosi się; powrót nogi odbywa się w powietrzu. */
    fun mechFootLift(phase: Float): Float = max(0f, -sin(phase))
}
