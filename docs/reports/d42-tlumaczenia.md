# D42: przegląd tłumaczeń part185–part203 (`engine/L.kt`)

Podstawa: `origin/claude/dzien-34-kandydat` + `origin/integ/tura10`. Na tej gałęzi istnieją
`part185`, `186`, `188`, `189`, `190`, `193`, `194`, `196`, `198`, `200`, `201`, `203`
(części 187, 191, 192, 195, 197, 199 i 202 nie istnieją). Kolumny: pl, en, de, es, fr, it, pt, uk, nl, tr.
Wzór: `docs/research/R7-translations.md`.

Sprawdzono:
- **Placeholdery**: skrypt porównał `%s`/`%d`/`%1$s` (także `${'$'}`, `%%`) we wszystkich
  10 kolumnach każdego klucza. Wszystkie klucze się zgadzają, także tureckie `%%%d` w `alert190_base_a11y`.
- **Kopie EN**: jedyne trafienie to `ms198_to_record_short` (fr/it/nl: „record −%d”), ale to
  poprawne słowo w tych językach.
- **Terminy**: sprawdzone grepem w całym L.kt (tryby `daily`/`weekly`/`endless`, `wave`, `campaign`,
  epoka, dowódca, osiągnięcia, `Difícil`/`Складний`).
- **Forma zwracania się**: pl/de/es/it/uk/nl/tr i pt europejski używają „ty”, fr „vous” (R7).
- **Obietnice dla niewidomych**: brak. `pt186_desc` mówi tylko, że raport zawiera stan TalkBack,
  a `alert190`/`a196_*` to opisy elementów.
- **Składnia**: zbalansowane cudzysłowy, brak niezabezpieczonego `$`, 77 `putAll(part…)` = 77
  `private fun part…` (te same nazwy) i 10 kolumn w każdym kluczu. Gradle nie był uruchamiany.

## Poprawki

| Klucz | Język | Było | Jest | Dlaczego |
|---|---|---|---|---|
| `ach_first_evolve` | de | „Neues Zeitalter” | „Neue Epoche” | w grze era to „Epoche” (44×, `a178_era_up` „Neue Epoche: %s”, opis tego samego osiągnięcia) |
| `wn_185_achievements` | de | „neues Zeitalter” | „neue Epoche” | jw. |
| `t203_rules_note` | de | „volle Regeln der Herausforderungen” | „volle Challenge-Regeln” | tryb to „Tageschallenge”; krócej |
| `t203_ach_release` | es | „Disponible desde el lanzamiento” (31) | „Disponible en el lanzamiento” (28) | napis jest skracany wielokropkiem w wierszu osiągnięcia |
| `t200_title` | fr | „Ça te plaît ?” | „Ça vous plaît ?” | fr w menu i komunikatach na „vous” |
| `t200_note` | fr | „Ta réponse … si tu le partages” | „Votre réponse … si vous le partagez” | jw. |
| `a196_afford` | fr | „à ta portée” | „à votre portée” | jw.; `help180_research` „Recherche à votre portée” |
| `a196_medals` | fr | „Tu as %1$s” | „Vous avez %1$s” | jw. |
| `wb193_daily` | it | „Sfida del giorno di oggi” | „Oggi nella Sfida del giorno” | „del giorno di oggi” to powtórzenie |
| `a196_afford` | pt | „você pode pagar” | „podes pagar” | pt europejski z „tu” (`help180_research` „Podes pagar”) |
| `a196_medals` | pt | „Você tem %1$s” | „Tens %1$s” | jw. |
| `ach_wager_40_d` | pt | „Chega à vaga 40” | „Chega à onda 40” | fala to „onda” (`wave`, `ach_wave_*_d`, `lost188_endless`) |
| `t203_ach_release` | pt | „Disponível a partir do lançamento” (33) | „Disponível no lançamento” (24) | długość (wielokropek); spójne z `t203_rules_note_short` |
| `t203_rules_note` | pt | „a partir do lançamento” | „no lançamento” | jw. |
| `ms198_tier_a11y` | nl | „weekbelegering” | „het Wekelijks beleg” | nazwa trybu `weekly` |
| `t203_ach_release` | nl | „Beschikbaar vanaf de lancering” (30) | „Beschikbaar bij de lancering” (28) | długość |
| `t203_rules_note` | tr | „meydan okuma kuralları” | „mücadele kuralları” | wyzwanie to „Mücadele” (`daily`) |
| `a196_stage` | tr | „Aşama %1$d” | „Bölüm %1$d” | etap to zwykle „bölüm” (16× w grze, `lost188_campaign`, `hard189_goal`) |

W pl, en i uk nie było oczywistych błędów.

## Uwagi bez zmian (do decyzji)

- **fr, osiągnięcia**: opisy `ach_*_d` są w całej grze podzielone mniej więcej po równo
  (24 na „tu”, 22 na „vous”). `part185` używa „vous”, `ach_wager_40_d` (part194) „tu”.
  Nie ujednolicano, bo to decyzja dla całej listy. Do tego `part184` (poza zakresem) ma „Tu commences”.
- **pt**: stare teksty samouczka są w wariancie brazylijskim („você”, „Pular tutorial”), a nowe
  w europejskim. `t200_skip` „Ignorar” zostawiono.
- **tr**: oprócz „bölüm” zostały 3 miejsca z „aşama” (`bh_stage`, `cp143_locked`, `ach_late_no_turrets_d`),
  wszystkie poza zakresem.
- **nl**: `wn_184_content` (part184, poza zakresem) też ma „Weekbelegering”.
- **de**: `lost188_body` „geht nicht weiter” jest potoczne, ale zrozumiałe.
- **de/es/it/pt**: `ach_first_research_d` i `ach_first_turret_d` są 1,5–2 razy dłuższe od pl,
  ale opisy osiągnięć są zawijane.

## Długości do sprawdzenia na zrzutach

| Miejsce | Klucz | Ryzyko |
|---|---|---|
| Osiągnięcia: wiersz z postępem (`RowNote.ellipsize`) | `t203_ach_release` | es 28, nl 28, pt 24, uk „Доступно з прем'єри” 19 przy pl 20. Przy wąskim wierszu może się pojawić wielokropek |
| Wyzwania: notka nad kartami (`ModeScreen`, krótka wersja z wielokropkiem) | `t203_rules_note_short` | es „Reglas completas desde el lanzamiento” 37 znaków przy pl 24. Najbardziej narażony na „…” |
| Ankieta testera: przyciski odpowiedzi (2 kolumny ok. 200 dp, 15 sp) | `t200_issue_*` | najdłuższe: nl „Onduidelijke regels” i uk „Незрозумілі правила” (19), it „Regole poco chiare”, en „Nothing, it's good” (18) |
| Ankieta: notka (najwyżej 4 wiersze, 13 sp, nadmiar ginie) | `t200_note` | najdłuższe fr, uk i es (ok. 120–130 znaków). Sprawdzić przy dużej czcionce systemowej (`textScale`) i na wąskim ekranie |
| Ankieta: przycisk „Pomiń” | `t200_skip` | de „Überspringen” (12). Szerokość liczona z tekstu, więc ryzyko jest niskie |
| Mapa kampanii: wiersz gwiazdek z „Nowością” | `nov201_new` + nazwy | fr „Nouveauté : …”, uk „Новинка: …”. Przy dwóch nowościach naraz sprawdzić, czy wiersz się nie ucina |
