package com.centurywar.engine

/** Palety są przygotowane raz; render odczytuje wyłącznie pola Int. */
class PaletteColors(
    val player: Int, val enemy: Int, val good: Int, val bad: Int,
    val panel: Int, val outline: Int, val frame: Int,
    val buttonGood: Int, val buttonBad: Int,
)

object Palette {
    const val STANDARD = 0
    const val DEUTAN_PROTAN = 1
    const val TRITAN = 2
    const val HIGH_CONTRAST = 3
    const val COUNT = 4

    private val presets = arrayOf(
        PaletteColors(0xFF408CEB.toInt(), 0xFFDC463C.toInt(), 0xFF76E278.toInt(), 0xFFF05C50.toInt(),
            0xEE1A2030.toInt(), 0xFF1A1620.toInt(), 0xFF4E5870.toInt(), 0xFF187566.toInt(), 0xFFB0382E.toInt()),
        // Błękit / bursztyn, dobre stany cyjanowe, ostrzeżenia magentowe.
        PaletteColors(0xFF328CE8.toInt(), 0xFFF5A53D.toInt(), 0xFF54D3E3.toInt(), 0xFFE16AA8.toInt(),
            0xEE1A2030.toInt(), 0xFF1A1620.toInt(), 0xFF4E5870.toInt(), 0xFF216C8A.toInt(), 0xFFA5436B.toInt()),
        // Turkus / koral dla osób nierozróżniających błękitu i żółci.
        PaletteColors(0xFF13BDB4.toInt(), 0xFFE65079.toInt(), 0xFF47D9C4.toInt(), 0xFFFF8192.toInt(),
            0xEE1A2030.toInt(), 0xFF1A1620.toInt(), 0xFF4E5870.toInt(), 0xFF0D716B.toInt(), 0xFFAC365C.toInt()),
        PaletteColors(0xFF5AC8FF.toInt(), 0xFFFFBC48.toInt(), 0xFF9AFBFF.toInt(), 0xFFFF8AC3.toInt(),
            0xFA080C16.toInt(), 0xFF030307.toInt(), 0xFFB6C7E1.toInt(), 0xFF167089.toInt(), 0xFF9E376D.toInt()),
    )

    var selected = STANDARD
        private set
    var active: PaletteColors = presets[STANDARD]
        private set

    fun select(id: Int): Boolean {
        val next = id.coerceIn(0, COUNT - 1)
        if (selected == next) return false
        selected = next
        active = presets[next]
        return true
    }

    val highContrast: Boolean get() = selected == HIGH_CONTRAST
}
