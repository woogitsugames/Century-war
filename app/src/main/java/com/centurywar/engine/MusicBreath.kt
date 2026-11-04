package com.centurywar.engine

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow

/**
 * „Oddech” muzyki bitwy (decyzja właściciela, R11-backlog 5): pętle bojowe trwają ok. 52 s,
 * więc w długiej bitwie co [INTERVAL_S] grania muzyka schodzi do tła o [DEPTH_DB] na ok.
 * 30 s ([FADE_S] wejścia + [HOLD_S] + [FADE_S] powrotu), potem płynnie wraca. Efekty bez zmian.
 *
 * Bez wątków i zegara: czas płynie tylko przez [update] (wątek muzyki; w testach wstrzykiwany
 * krok). Zegar liczy wyłącznie granie muzyki bitwy, więc pauza Activity i ustąpienie cudzej
 * muzyce (wtedy nikt nie woła [update]) go zatrzymują. Krzywa to kosinusowe S w decybelach:
 * bez skoków, najwyżej ok. 6 dB/s w połowie przejścia.
 *
 * - [update] z `active = false` (menu, brak muzyki bitwy): zegar od zera, ewentualny oddech
 *   wraca do pełnej głośności tym samym płynnym przejściem.
 * - `blocked` (przerywnik wyniku i powrót pętli po nim): oddech się nie zaczyna, trwający
 *   wraca do pełnej głośności, a zegar liczy od zera.
 * - silne starcie (`battleWeight` ≥ [STRONG]): termin oddechu przesuwa się o [POSTPONE_S];
 *   trwający oddech wraca, bo walka znów jest na pierwszym planie.
 */
internal class MusicBreath(
    private val intervalS: Float = INTERVAL_S,
    private val holdS: Float = HOLD_S,
    private val fadeS: Float = FADE_S,
    private val depthDb: Float = DEPTH_DB,
) {
    companion object {
        /** Tyle sekund pełnej muzyki bitwy przed oddechem (ok. 2,5 min). */
        const val INTERVAL_S = 150f
        /** Przejście w dół i w górę (≥ 2 s, bez trzasków). */
        const val FADE_S = 4f
        /** Czas na dnie; razem z przejściami ok. 30 s. */
        const val HOLD_S = 22f
        /** Głębokość: muzyka w tle, wyraźnie pod efektami. */
        const val DEPTH_DB = -15f
        /** Udział wersji bojowej, od którego starcie jest „silne”. */
        const val STRONG = 0.8f
        /** Przesunięcie terminu przy silnym starciu. */
        const val POSTPONE_S = 15f

        private const val IDLE = 0
        private const val DOWN = 1
        private const val HOLD = 2
        private const val UP = 3
    }

    private var phase = IDLE
    /** Sekundy grania muzyki bitwy od początku albo od końca poprzedniego oddechu. */
    private var clock = 0f
    /** Postęp zejścia 0 (pełna głośność) .. 1 (dno). */
    private var depth = 0f
    private var holdLeft = 0f

    /** Mnożnik głośności pętli 0..1 (1 = bez oddechu). */
    @get:Synchronized
    var gain = 1f
        private set

    /** Czy trwa oddech (zejście, dno albo powrót). */
    val breathing: Boolean @Synchronized get() = phase != IDLE

    /** Ile sekund grania zostało do najbliższego oddechu (0 w trakcie). */
    val untilNext: Float @Synchronized get() = if (phase == IDLE) (intervalS - clock).coerceAtLeast(0f) else 0f

    @Synchronized
    fun update(dt: Float, active: Boolean, battleWeight: Float, blocked: Boolean) {
        val step = if (dt.isFinite()) dt.coerceIn(0f, 0.5f) else 0f
        val strong = battleWeight.isFinite() && battleWeight >= STRONG
        if (!active || blocked) {
            clock = 0f
            if (phase == DOWN || phase == HOLD) phase = UP
        }
        when (phase) {
            IDLE -> if (active && !blocked) {
                clock += step
                if (clock >= intervalS) {
                    if (strong) clock = intervalS - POSTPONE_S
                    else { phase = DOWN; holdLeft = holdS }
                }
            }
            DOWN -> if (strong) phase = UP else {
                depth += step / fadeS
                if (depth >= 1f) { depth = 1f; phase = HOLD }
            }
            HOLD -> if (strong) phase = UP else {
                holdLeft -= step
                if (holdLeft <= 0f) phase = UP
            }
        }
        if (phase == UP) {
            depth -= step / fadeS
            if (depth <= 0f) { depth = 0f; phase = IDLE; clock = 0f }
        }
        val s = (1f - cos(depth * PI.toFloat())) * 0.5f
        gain = 10f.pow(depthDb * s / 20f)
    }

    /** Nowa bitwa: pełna głośność od razu (wołane tylko, gdy nic nie gra). */
    @Synchronized
    fun reset() {
        phase = IDLE; clock = 0f; depth = 0f; holdLeft = 0f; gain = 1f
    }
}
