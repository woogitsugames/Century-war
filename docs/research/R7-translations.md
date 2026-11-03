# R7: przegląd tłumaczeń part177–part183 (`engine/L.kt`)

Podstawa: `origin/integ/tura10` (34499fd). Na tej gałęzi istnieją `part178`, `part181`, `part182`
(46 kluczy). `part177` i `part183` nie istnieją. `part179`/`part180` są tylko na
`claude/dzien-9-cele` (programista), więc ich nie ruszano. Kolejność kolumn w `t(...)`:
pl, en, de, es, fr, it, pt, uk, nl, tr (komentarz nad `table`).

Sprawdzono: placeholdery (liczba i kolejność `%s`/`%d` względem pl: wszystkie 46 kluczy zgodne,
także tureckie `%%%d`), terminologię ze starszymi częściami (`cx_barrage`, `rally`,
`comm125_name`, `ew_boss`, `wave`, `upgrade`, `privacy93_*`, `global_nick_error`), formę
zwracania się do gracza (pl/de/es/it/pt/uk/nl/tr: „ty”, fr: „vous”, jak w reszcie gry) oraz
długość tekstów na przyciskach (`pt182_button`, `a11y181_slow` mieszczą się w zakresie
istniejących etykiet; `ui.fit` i tak dopasowuje rozmiar).

## Poprawki (jedna na wiersz)

| Klucz | Język | Było | Jest | Dlaczego |
|---|---|---|---|---|
| `pt182_desc` | pl | „to Ty wybierasz” | „to ty wybierasz” | w grze „ty/twój” w środku zdania małą literą |
| `pt182_a11y` | pl | „tylko Ty” | „tylko ty” | jw. |
| `a178_barrage_base` | pl | „przed moją bazą” | „przed twoją bazą” | ta sama część mówi „Twoja baza”; jedna forma zwracania się |
| `a178_barrage_base` | en | „in front of my base” | „in front of your base” | jw. („Your base”) |
| `a178_barrage_base` | de | „vor meiner Basis” | „vor deiner Basis” | jw. („Deine Basis”) |
| `a178_barrage_base` | es | „frente a mi base” | „frente a tu base” | jw. („Tu base”) |
| `a178_barrage_base` | fr | „devant ma base” | „devant votre base” | jw. („Votre base”) |
| `a178_xp_ready` | fr | „prêt à évoluer” | „évolution disponible” | „expérience” jest rodzaju żeńskiego; to nie doświadczenie ewoluuje |
| `a178_training` | fr | „en formation” | „en entraînement” | w grze szkolenie jednostek to „entraînement” (`d_swift_d`, `ec_drill_sergeant_d`) |
| `pt182_desc` | it | „Senza nome” | „Senza nickname … né” | w grze pseudonim to „nickname” (`global_nick_error`); „né” przy wyliczeniu przeczącym |
| `a178_xp_ready` | it | „pronto a evolvere” | „evoluzione disponibile” | „esperienza” jest rodzaju żeńskiego |
| `a178_barrage_base` | it | „davanti alla mia base” | „davanti alla tua base” | spójność z „La tua base” |
| `pt182_desc` | pt | „Sem nome” | „Sem apelido” | w grze pseudonim to „apelido” (`global_nick_error`, `global_consent_desc`) |
| `a178_xp_ready` | pt | „pronto para evoluir” | „pronta para evoluir” | „experiência” jest rodzaju żeńskiego |
| `a178_barrage_base` | pt | „em frente à minha base” | „em frente à tua base” | spójność z „A tua base” |
| `pt182_desc` | uk | „акаунта Play Ігри” | „акаунта Play Ігор” | dopełniacz, jak w `privacy93_delete_hint` |
| `pt182_desc` | uk | „ви обираєте” | „ти обираєш” | reszta gry po ukraińsku zwraca się na „ти” („Твоя база”, „Поклич”) |
| `pt182_a11y` | uk | „надсилаєте лише ви” | „надсилаєш його лише ти” | jw. |
| `a178_barrage_base` | uk | „перед моєю базою” | „перед твоєю базою” | spójność z „Твоя база” |
| `a178_upgrade_action` | nl | „Verbeteren: %s” | „Upgraden: %s” | przycisk ulepszenia w grze to „Upgraden” (`upgrade`) |
| `a178_barrage_base` | nl | „voor mijn basis” | „voor jouw basis” | spójność z „Jouw basis” |
| `a178_upgrade_action` | tr | „Geliştir: %s” | „Yükselt: %s” | przycisk ulepszenia w grze to „Yükselt” (`upgrade`) |
| `a178_barrage_base` | tr | „üssümün önü” | „üssünün önü” | spójność z „Üssün” |

## Uwagi bez zmian

- `pt182_desc` (de: „Play-Games-Konto”, nl/tr: „Play Games”/„Play Oyunlar”): gra miesza nazwę
  angielską i lokalną (komunikat o niedostępności Play Games po angielsku, `privacy93_*` lokalnie); zostawiono.
- `a178_xp` pl/en („do: %s”, „to: %s”) brzmi sucho, ale jest czytelne dla TalkBack; bez zmian.
- `a11y181_slow` es/fr/pt ma 22–24 znaki (pl 14); wiersz używa `ui.fit`, więc się mieści.
- Nowy tekst M5 (`pt186_desc`, R6-02) powinien bazować na poprawionym `pt182_desc`.
