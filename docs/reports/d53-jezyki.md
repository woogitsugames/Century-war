# D53: ujednolicenie języków pt i fr w `L.kt`

Podstawa: `docs/reports/d42-tlumaczenia.md`, sekcja „Uwagi bez zmian (do decyzji)”.
Gałąź: `claude/dzien-53-jezyki` (od `integ/tura10`). Plik: `app/src/main/java/com/centurywar/engine/L.kt`.

Razem zmieniono **96 tekstów**: 71 pt i 25 fr. Klucze, liczba części (78) i placeholdery są bez zmian.

## Portugalski: pt-BR na pt-PT z formą „tu” (71)

Nowsze części (samouczek 54, osiągnięcia, kodeks 127/176) już używały pt-PT z „tu”: „Toca”, „Vence”,
„Tens ouro extra”. Stare teksty były w wariancie brazylijskim. Teraz wszystkie mają formę „tu”.

| Co | Przed | Po |
|---|---|---|
| „você”, „seu/sua” w 2. os. | `tut_done` „Você já sabe o básico…” | „Já sabes o básico…” |
| tryb rozkazujący 3. os. | `tut_melee_t` „Treine um porreteiro”, `cx_barrage_d` „Toque em Bombardeio e escolha…” | „Treina um porreteiro”, „Toca em Bombardeio e escolhe…” |
| słownictwo BR | `fps_max` „Tela”, `screen_shake` „Tremor da tela”, `tut_camera` „câmera”, „pular” | „Ecrã”, „Tremor do ecrã”, „câmara”, „saltar” |
| menu i ustawienia | `tut_skip` „Pular tutorial”, `tut85_skip_body` „…nas Configurações.”, `tut_next` „Próximo” | „Saltar tutorial”, „Podes repeti-lo nas Definições.”, „Seguinte” |
| gerundium | `pgs_checking` „Verificando…”, `pgs_syncing` „Sincronizando…”, `ach_orbital_d` „evoluindo” | „A verificar…”, „A sincronizar…”, „ao evoluir” |
| konta Play | `pgs_connected` „Conectado”, `pgs_error` „Erro de conexão”, `pgs_signed_out` „Desconectado” | „Ligado”, „Erro de ligação”, „Sessão terminada” |
| inne | `ec_repair_crew` „Equipes de reparo”, `tut_doctrine` „bônus”, `u_musketeer_fl` „fumaça”, `u_clubman_fl` „gênio” | „Equipas de reparação”, „bónus”, „fumo”, „génio” |

Zakres: cały stary samouczek (`tut_*`, `tut85_*`), podpowiedzi (`tip_*`, `elite141_*`, `tier145_hint`,
`comm125_tip`), konto Play (`pgs_*`), 9 opisów osiągnięć w formie „Vença/Alcance/Escolha”, pojedyncze
teksty w menu i kodeksie (`resume_desc`, `notification_body`, `choose_*`, `cx_turret_d`, `sk_moddesc_9` itd.).
Teksty fabularne jednostek (`u_gunner_fl`, `u_plasma_fl`) mówią o jednostce w 3. osobie, więc mają
„O seu / A sua”, a nie „teu”.

Zostawiono: nazwę umiejętności „Bombardeio” (to nazwa przycisku w wielu kluczach, nowsze teksty mają
„bombardeamento”) oraz „onda” obok „vaga” (oba słowa są poprawne w pt-PT). To osobne ujednolicenie
słownictwa, nie wariantu.

## Francuski: osiągnięcia na „vous” (25)

W całej grze dominuje „vous”. Poza osiągnięciami jest około 117 tekstów z „vous/votre/-ez”
(samouczek `tut_*`, podpowiedzi `tip_*`, menu, kodeks) i 18 z „tu”. Z tych 18 rzeczywiście w formie
„tu” jest tylko 7: `ec_war_chest_d`, 3× `week184_*_d`, `resume_desc` oraz `ms_research`, `ms_armory`,
`ms_unit_upgrades` („Achète”). Reszta to fałszywe trafienia („ta”, „t”). W opisach osiągnięć było
około 22 tekstów na „vous” i 25 na „tu”. Wszystkie 25 zmieniono na „vous”:

- `ach_wager_40_d` „Atteins la vague 40…” → „Atteignez la vague 40…”
- `ach_rain_victory_d` „Gagne une bataille sous la pluie.” → „Gagnez une bataille sous la pluie.”
- `ach_boss_*_d` (5) „Bats le boss…” → „Battez le boss…”
- `ach_replay_record_d` „Regarde 10 s de la rediffusion de ton record.” → „Regardez … de votre record.”
- `ach_card_*_d`, `ach_weekly_rules_*_d`, `ach_hard_perfect_d`, `ach_modifier_victories_10_d`,
  `ach_doctrine_victories_13_d` itd.

Do decyzji (poza zakresem zadania): 7 tekstów spoza osiągnięć wymienionych wyżej nadal ma „tu”.

## Sprawdzenie

Gradle nie działa, więc sprawdzałem skryptem Python na całym `L.kt`. Skrypt sprawdza: 1532 wpisy po
10 tekstów, brak powtórzonych kluczy, parzystą liczbę cudzysłowów w liniach, brak niezabezpieczonego
`$`, te same placeholdery `%s/%d/%1\$s` we wszystkich językach klucza oraz zgodność `putAll(partN())`
z `private fun partN` (78 = 78). Wynik jest identyczny jak przed zmianą. Zostały tylko znane fałszywe
alarmy: formy mnogie z `|` w `ms_*` i `medals_n`, szablon w `lost188_body` i komentarz z „klucz”.
Każdą podmianę skrypt dodatkowo sprawdzał pod kątem zgodności placeholderów.

Obejście `tools/offline-build/` (`CW_OFFLINE_DIR=/tmp/claude-0/d53`, zależności z d48): `build_app.sh`
kończy się „OK” (sim + app, main i test). `run_tests.sh` dla `LTest`, `LTestReview120` i `LTestReview136`
daje **OK (26 testów)**. Uwaga dla następnych użyć: katalog `stubs` trzeba skopiować z d48, a nie
podlinkować, bo `find $D/stubs` nie wchodzi w symlink („error: no source files”).
