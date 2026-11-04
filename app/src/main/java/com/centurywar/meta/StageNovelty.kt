package com.centurywar.meta

import com.centurywar.engine.L
import com.centurywar.game.Campaign
import com.centurywar.game.Defs
import com.centurywar.game.Modifier
import com.centurywar.game.Role
import com.centurywar.game.Stage
import com.centurywar.game.Weather

/** R11-06: rodzaj nowości etapu; kolejność wpisów to kolejność w napisie. */
internal enum class NoveltyKind { COMMANDER, ENEMY_ERA, WEATHER, MODIFIER }

/** Jedna nowość: [era] dla ENEMY_ERA, [modifier] albo [weather] dla pozostałych (inaczej −1/null). */
internal class NoveltyItem(
    val kind: NoveltyKind,
    val era: Int = -1,
    val modifier: Modifier? = null,
    val weather: Weather? = null,
)

/**
 * R11-06: „Nowość na tym etapie”. Czysta logika liczona statycznie z [Campaign.stages]:
 * etap wprowadza coś nowego, gdy pierwszy raz w kampanii pojawia się na nim nowa epoka wroga
 * (wyższy [Stage.maxEra] niż na wszystkich wcześniejszych etapach), modyfikator, pogoda inna
 * niż bezchmurna albo dowódca (etap 8). Etap 1 (samouczek) jest punktem odniesienia i nic
 * nie wprowadza. Bez zmian w `:sim`: wynik nie wpływa na bitwę.
 */
internal object StageNovelty {
    /**
     * Pierwszy etap kampanii z dowódcą (indeks). Lustro warunku `cfg.stageIndex >= 7`
     * w `Battle.commanderAvailable`; test pilnuje zgodności z kampanią.
     */
    const val COMMANDER_STAGE = 7

    /** Nowości wszystkich etapów kampanii (lista pusta = etap bez nowości). */
    val items: List<List<NoveltyItem>> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        compute(Campaign.stages)
    }

    /** Nowości etapu [index]; poza zakresem lista pusta. */
    fun of(index: Int): List<NoveltyItem> = items.getOrNull(index) ?: emptyList()

    fun isNovel(index: Int): Boolean = of(index).isNotEmpty()

    /**
     * Iskra przy węźle na mapie: etap z nowością, którego gracz jeszcze nie wygrał
     * (0 gwiazdek, tak jak [Profile.unlockedStage]).
     */
    fun showSpark(index: Int, stars: Int): Boolean = stars <= 0 && isNovel(index)

    /** Wylicza nowości dla dowolnej listy etapów (w kolejności kampanii); do testów. */
    fun compute(stages: List<Stage>, commanderStage: Int = COMMANDER_STAGE): List<List<NoveltyItem>> {
        val result = ArrayList<List<NoveltyItem>>(stages.size)
        var seenEra = -1
        val seenModifiers = HashSet<Modifier>()
        val seenWeather = HashSet<Weather>()
        seenWeather.add(Weather.CLEAR)
        for ((position, s) in stages.withIndex()) {
            val list = ArrayList<NoveltyItem>(2)
            if (position == 0) {
                // Samouczek: punkt odniesienia, wszystko na nim jest „pierwsze”.
                seenEra = s.maxEra
                seenWeather.add(s.weather)
                seenModifiers.addAll(s.modifiers)
                result.add(emptyList())
                continue
            }
            if (s.index == commanderStage) list.add(NoveltyItem(NoveltyKind.COMMANDER))
            if (s.maxEra > seenEra) {
                list.add(NoveltyItem(NoveltyKind.ENEMY_ERA, era = s.maxEra))
                seenEra = s.maxEra
            }
            if (seenWeather.add(s.weather)) list.add(NoveltyItem(NoveltyKind.WEATHER, weather = s.weather))
            for (m in s.modifiers) {
                if (seenModifiers.add(m)) list.add(NoveltyItem(NoveltyKind.MODIFIER, modifier = m))
            }
            result.add(if (list.isEmpty()) emptyList() else list)
        }
        return result
    }

    /** Nazwa ciężkiej jednostki epoki (wróg ma ją zawsze, w przeciwieństwie do elity). */
    fun unitKey(era: Int): String {
        val units = Defs.eras[era.coerceIn(0, Defs.ERA_COUNT - 1)].units
        return (units.firstOrNull { it.role == Role.HEAVY } ?: units[0]).nameKey
    }

    /** Krótki opis jednej nowości w bieżącym języku. */
    fun describe(item: NoveltyItem): String = when (item.kind) {
        NoveltyKind.COMMANDER -> L["nov201_commander"]
        NoveltyKind.ENEMY_ERA -> L.f("nov201_era", L[Defs.eraKeys[item.era.coerceIn(0, Defs.ERA_COUNT - 1)]],
            L[unitKey(item.era)])
        NoveltyKind.WEATHER -> L[(item.weather ?: Weather.CLEAR).key]
        NoveltyKind.MODIFIER -> L[item.modifier?.key ?: "none"]
    }

    /** Napisy zapamiętane osobno dla każdego etapu, z językiem, w którym powstały. */
    private class Cached(val lang: Int, val text: String)

    private val cache = arrayOfNulls<Cached>(Campaign.stages.size)

    /**
     * Wiersz „Nowość: …” etapu [index] w bieżącym języku; "" = etap bez nowości.
     * Wynik jest zapamiętany do zmiany języka (panel woła to w każdej klatce).
     */
    fun line(index: Int): String {
        val list = of(index)
        if (list.isEmpty()) return ""
        val lang = L.lang
        val slot = cache.getOrNull(index)
        if (slot != null && slot.lang == lang) return slot.text
        val text = L.f("nov201_new", list.joinToString(", ") { describe(it) })
        if (index in cache.indices) cache[index] = Cached(lang, text)
        return text
    }
}
