package com.centurywar.game

/** Krótkie wskazówki do wyświetlenia przy wyborze jednostki (PL/EN). */
object CounterTexts {
    private val pl = arrayOf(
        "Silny przeciw: jednostkom dystansowym",
        "Silny przeciw: jednostkom ciężkim",
        "Silny przeciw: jednostkom wręcz",
        "Oblężnik: +60% obrażeń baz",
    )

    private val en = arrayOf(
        "Strong against: ranged units",
        "Strong against: heavy units",
        "Strong against: melee units",
        "Siege unit: +60% damage to bases",
    )

    fun roleHint(role: Role, polish: Boolean): String =
        if (polish) pl[role.ordinal] else en[role.ordinal]

    fun turretHint(polish: Boolean): String =
        if (polish) "Wieże od epoki pary: +15% obrażeń późnym ciężkim jednostkom"
        else "Steam-era and later turrets: +15% damage to late heavy units"
}
