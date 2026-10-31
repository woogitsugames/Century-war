package com.centurywar.game

/** Teksty PL/EN kart i fal bez zależności od ekranu ani lokalizacji systemowej. */
object EndlessTexts {
    private val namePl = arrayOf(
        "Gospodarka",
        "Oblężenie",
        "Bastion",
        "Błyskawica",
        "Weterani",
        "Furia",
        "Arsenał",
        "Taktyka",
        "Łupieżcy",
        "Nowe stanowisko",
        "Posiłki elitarne",
        "Płonące wieże",
        "Automatyczny ostrzał",
        "Krew za złoto",
        "Klonowanie",
        "Pancerz piechoty",
        "Instruktor",
        "Szybkostrzelne wieże",
        "Medycy",
        "Skarbiec",
        "Mobilizacja",
        "Forteca",
        "Strzelcy wyborowi",
        "Szał bojowy",
        "Zbieracze",
        "Marsz bojowy",
        "Mistrz zrywu",
        "Akademia elit",
        "Ostatnia linia",
        "Ekipy naprawcze",
    )

    private val descPl = arrayOf(
        "+25% przychodu.",
        "+30% obrażeń wobec baz i +20% obrażeń wież.",
        "+20% maksymalnego HP bazy (z leczeniem) i +10% HP nowych jednostek.",
        "+25% tempa szkolenia i +10% szybkości ruchu.",
        "+30% zdobywanego doświadczenia.",
        "+12% obrażeń jednostek.",
        "+1 slot wieży (do limitu) i −20% kosztu wież.",
        "−25% czasu odnowienia zdolności.",
        "+35% złota za pokonanych wrogów.",
        "+1 slot wieży (maksymalnie 4).",
        "Jedna darmowa jednostka elitarna co falę; zaległe posiłki czekają na miejsce.",
        "Pociski wież podpalają wroga: 25% obrażeń trafienia w ciągu 4 s.",
        "Dodatkowy ostrzał co 30 s, niezależnie od ręcznej zdolności.",
        "+50% przychodu i złota za zabójstwa, ale −20% HP bazy.",
        "10% szans na darmową kopię każdej wyszkolonej jednostki.",
        "+20% HP nowo tworzonych jednostek.",
        "+20% szybkości szkolenia.",
        "+25% szybkostrzelności wież.",
        "Natychmiast przywróć 25% maksymalnego HP bazy.",
        "Otrzymaj złoto równe 500 × skala kosztu bieżącej epoki.",
        "+3 do limitu jednostek.",
        "+25% maksymalnego HP bazy, zachowując proporcję zdrowia.",
        "+25% obrażeń nowo wyszkolonych jednostek dystansowych.",
        "+30% obrażeń, ale −10% HP nowych jednostek.",
        "+25% złota za zabójstwa.",
        "+20% szybkości ruchu.",
        "−20% czasu odnowienia umiejętności.",
        "+30% obrażeń nowo wyszkolonych jednostek elitarnych.",
        "+35% obrażeń jednostek, gdy baza ma mniej niż 35% HP.",
        "Naprawiaj 6% maksymalnego HP bazy na początku każdej fali.",
    )

    private val nameEn = arrayOf(
        "Economy",
        "Siege",
        "Bulwark",
        "Swift",
        "Veteran",
        "Fury",
        "Arsenal",
        "Tactics",
        "Plunder",
        "Extra turret slot",
        "Elite reinforcements",
        "Burning towers",
        "Auto barrage",
        "Blood for gold",
        "Cloning",
        "Frontline armor",
        "Drill sergeant",
        "Rapid turrets",
        "Medics",
        "War chest",
        "Mobilization",
        "Fortress",
        "Sharpshooters",
        "Berserk",
        "Scavengers",
        "Forced march",
        "Rally master",
        "Elite academy",
        "Last stand",
        "Repair crews",
    )

    private val descEn = arrayOf(
        "+25% income.",
        "+30% base damage and +20% turret damage.",
        "+20% max base HP (with healing) and +10% HP for new units.",
        "+25% training speed and +10% movement speed.",
        "+30% experience gained.",
        "+12% unit damage.",
        "+1 turret slot (up to limit) and −20% turret cost.",
        "−25% ability cooldowns.",
        "+35% gold from kills.",
        "+1 turret slot (maximum 4).",
        "One free elite each wave; reinforcements wait for room.",
        "Turret shots burn enemies for 25% of the hit over 4 seconds.",
        "An extra barrage every 30 seconds, independent of the manual ability.",
        "+50% income and kill gold, but −20% base HP.",
        "10% chance to duplicate each trained unit for free.",
        "+20% HP for newly spawned units.",
        "+20% training speed.",
        "+25% turret fire rate.",
        "Immediately restore 25% of maximum base HP.",
        "Gain gold worth 500 × the current era's cost scale.",
        "+3 unit population capacity.",
        "+25% maximum base HP, preserving health percentage.",
        "+25% damage for newly trained ranged units.",
        "+30% damage, but −10% HP for new units.",
        "+25% kill gold.",
        "+20% movement speed.",
        "−20% ability cooldowns.",
        "+30% damage for newly trained elite units.",
        "+35% unit damage while the base is below 35% HP.",
        "Repair 6% of maximum base HP at the start of every wave.",
    )

    private val rarityPl = arrayOf("Zwykła", "Niepospolita", "Rzadka", "Legendarna")
    private val rarityEn = arrayOf("Common", "Uncommon", "Rare", "Legendary")
    private val wavesPl = arrayOf(
        "Zróżnicowana armia", "Rój strzelców", "Pancerna kolumna",
        "Szybki szturm", "Gwardia elitarna", "Nawała artylerii",
    )
    private val wavesEn = arrayOf(
        "Mixed army", "Ranged swarm", "Armored column",
        "Fast assault", "Elite guard", "Artillery barrage",
    )

    fun title(card: EndlessCard, polish: Boolean): String =
        if (polish) namePl[card.ordinal] else nameEn[card.ordinal]

    fun description(card: EndlessCard, polish: Boolean): String =
        if (polish) descPl[card.ordinal] else descEn[card.ordinal]

    fun rarity(rarity: EndlessRarity, polish: Boolean): String =
        if (polish) rarityPl[rarity.ordinal] else rarityEn[rarity.ordinal]

    fun wave(theme: EndlessWaveTheme, polish: Boolean): String =
        if (polish) wavesPl[theme.ordinal] else wavesEn[theme.ordinal]

    fun chooseCard(polish: Boolean): String = if (polish) "Wybierz kartę" else "Choose a card"
    fun bossIncoming(polish: Boolean): String = if (polish) "Nadciąga boss" else "Boss incoming"
}
