# Balans kampanii Century War — przed i po strojeniu

## Metodyka

- Każdy pełny przebieg: **32 etapy × 3 profile badań × 20 seedów = 1920 bitew**; obie strony sterowane przez `Ai`. Profile: **nowy** (0%), **średni** (40% maksymalnych poziomów), **weteran** (80%).
- Poziomy badań rozdzielone proporcjonalnie do maksymalnych poziomów z `Profile.research`; dwie umiejętności jednopoziomowe odblokowywane na początku profilu rozwiniętego. Mnożniki z badań pochodzą z `Profile.bonuses()`.
- Identyczne ziarna przed i po: `246813579 + stageIndex*10007 + trial*997`. Symulacja ze stałym krokiem `Battle.STEP`, bez losowości spoza `Battle.rng`.
- **Win rate:** zwycięstwa gracza-AI / 20. **Mediana:** czas ukończenia walki w minutach (również porażki). **Timeout:** bitwa niedokończona po 30 minutach. **Średnia epoka:** średnia końcowych epok obu stron (indeksowana od zera).
- Tabela przed/po porównuje wyłącznie **profil docelowy danego etapu**: 1–6 nowy; 7–20 średni; 21–32 weteran. Wszystkie 96 kombinacji etap/profil oraz średnie epoki są w plikach [balance-before.csv](balance-before.csv) i [balance-after.csv](balance-after.csv). Każdy profil i etap ma 20 prób.

## Kryteria i wyniki

| Etapy | Profil | Próg zwycięstw | Wygrane po — min.–maks. | Mediana czasu po — min.–maks. | Timeouty po |
|---|---|---:|---:|---:|---:|
| 1–6 | nowy | ≥70% | 100–100% | 4.52–10.28 min | 0/120 |
| 7–20 | średni | ≥55% | 70–100% | 4.58–10.60 min | 0/280 |
| 21–32 | weteran | ≥50% | 70–100% | 4.02–9.55 min | 0/240 |

**Wynik:** wszystkie 32/32 docelowe kombinacje spełniły progi zwycięstw, a mediany ich czasu mieszczą się w 4–12 minutach. **Timeouty: 0/1920 po** wobec **68/1920 przed**; w docelowych 640 bitwach po nie było żadnego timeoutu. Wynik AI sterującego graczem stanowi dolne oszacowanie umiejętności człowieka.

## Wyniki przed/po dla 32 etapów

| Etap | Profil docelowy | Zwycięstwa przed → po | Mediana [min] przed → po | Timeout przed → po | aiPower przed → po |
|---:|---|---:|---:|---:|---:|
| 1 | nowy | 5% → 100% | 30.04 → 9.43 | 95% → 0% | 0.72 → 0.20 |
| 2 | nowy | 10% → 100% | 30.04 → 10.28 | 90% → 0% | 0.78 → 0.22 |
| 3 | nowy | 55% → 100% | 25.15 → 5.34 | 45% → 0% | 0.84 → 0.35 |
| 4 | nowy | 30% → 100% | 30.04 → 4.52 | 70% → 0% | 0.89 → 0.35 |
| 5 | nowy | 80% → 100% | 16.58 → 7.47 | 20% → 0% | 0.83 → 0.35 |
| 6 | nowy | 80% → 100% | 19.04 → 7.50 | 20% → 0% | 0.89 → 0.50 |
| 7 | sredni | 80% → 85% | 6.44 → 7.25 | 0% → 0% | 0.95 → 1.20 |
| 8 | sredni | 80% → 70% | 7.24 → 8.63 | 0% → 0% | 1.01 → 1.32 |
| 9 | sredni | 100% → 95% | 1.47 → 4.98 | 0% → 0% | 0.94 → 2.30 |
| 10 | sredni | 100% → 100% | 1.67 → 4.58 | 0% → 0% | 1.00 → 1.80 |
| 11 | sredni | 100% → 95% | 1.45 → 5.60 | 0% → 0% | 1.06 → 1.80 |
| 12 | sredni | 95% → 85% | 7.39 → 10.60 | 0% → 0% | 1.12 → 1.80 |
| 13 | sredni | 100% → 85% | 1.25 → 7.54 | 0% → 0% | 1.06 → 1.80 |
| 14 | sredni | 100% → 80% | 1.65 → 8.98 | 0% → 0% | 1.11 → 2.40 |
| 15 | sredni | 100% → 95% | 1.58 → 5.14 | 0% → 0% | 1.17 → 2.60 |
| 16 | sredni | 95% → 75% | 8.95 → 9.29 | 0% → 0% | 1.23 → 1.80 |
| 17 | sredni | 100% → 100% | 1.63 → 7.60 | 0% → 0% | 1.17 → 2.00 |
| 18 | sredni | 100% → 75% | 1.20 → 8.27 | 0% → 0% | 1.23 → 2.60 |
| 19 | sredni | 100% → 100% | 1.22 → 8.78 | 0% → 0% | 1.28 → 1.80 |
| 20 | sredni | 100% → 100% | 1.88 → 7.16 | 0% → 0% | 1.34 → 1.80 |
| 21 | weteran | 100% → 75% | 1.17 → 9.55 | 0% → 0% | 1.28 → 5.80 |
| 22 | weteran | 100% → 70% | 1.06 → 8.11 | 0% → 0% | 1.34 → 6.90 |
| 23 | weteran | 100% → 80% | 1.18 → 4.14 | 0% → 0% | 1.40 → 4.20 |
| 24 | weteran | 100% → 100% | 1.03 → 6.76 | 0% → 0% | 1.45 → 4.40 |
| 25 | weteran | 100% → 75% | 0.90 → 7.19 | 0% → 0% | 1.39 → 4.50 |
| 26 | weteran | 100% → 70% | 1.01 → 9.52 | 0% → 0% | 1.45 → 4.20 |
| 27 | weteran | 100% → 80% | 1.12 → 5.93 | 0% → 0% | 1.51 → 5.00 |
| 28 | weteran | 100% → 90% | 1.12 → 6.01 | 0% → 0% | 1.57 → 5.80 |
| 29 | weteran | 100% → 70% | 0.97 → 8.99 | 0% → 0% | 1.50 → 6.50 |
| 30 | weteran | 100% → 95% | 1.08 → 4.02 | 0% → 0% | 1.56 → 4.70 |
| 31 | weteran | 100% → 85% | 0.88 → 6.65 | 0% → 0% | 1.62 → 5.20 |
| 32 | weteran | 100% → 90% | 1.05 → 9.17 | 0% → 0% | 1.68 → 4.50 |

## Paty w bitwach lustrzanych

Lustrzany pomiar dodatkowy: 20 walk na epokę, `Mode.SKIRMISH`, bez modyfikatorów, `Bonuses()` obu stron, ten sam model `Ai` i seedy `1000003 + era*10007 + trial*997`. Pomiar „przed” wykonano przy **już dostrojonej krzywej HP baz**, ale jeszcze przed zmianą mechaniki blokady; izoluje on wpływ presji oblężniczej, nie całego strojenia.

| Epoka | Timeouty bez blokady | Timeouty z blokadą | Mediana po |
|---:|---:|---:|---:|
| 0 | 15/20 | **0/20** | 13,82 min |
| 3 | 0/20 | **0/20** | 9,55 min |
| 7 | 0/20 | **0/20** | 10,09 min |

## Wprowadzone zmiany

1. **`game/Modes.kt`:** 32 indywidualne wartości `Campaign.stages.aiPower`. Dla pierwszych sześciu etapów dodano `NO_TURRETS` i `FRAGILE_BASES`, co zapobiega patowi przy braku odblokowanych elit. W etapie 29 usunięto `FRAGILE_BASES`. Zakresy epok i pozostałe modyfikatory zachowano.
2. **`game/Defs.kt`:** HP bazy zmieniono z `1600 × 1.85^era` na `6400 × 1.85^era × (1 + 2.5 × (era/7)^3)`. Wyniki dla ośmiu epok są preobliczane w `FloatArray`, bez potęgowania w pętli gry.
3. **`game/Battle.kt`:** po 8 minutach `siegePressure()` zwiększa również automatyczną, deterministyczną presję blokady na obie bazy, zależnie od pozycji frontu; działa mimo zerowej liczby trafień baz i **nie dotyczy ENDLESS**. Nie dodano alokacji ani źródeł losowości w ticku.
4. **`app/src/test/java/com/centurywar/game/BalanceReport.kt`:** JUnit generuje `build/balance/report.csv`, `report.md` i `mirror.md`; obsługuje podział na etapy, liczbę prób, profile i odtworzenie bazowych parametrów etapów. Test lustrzany posiada asercję zerowych timeoutów.
5. **`.github/workflows/balance.yml`:** 8 równoległych przedziałów po 4 etapy, Android SDK 36, `assembleDebug`, `BattleSimTest`, testy lustrzane i raporty CSV jako artefakty.

## Kompilacja i testy

GitHub Actions: [pełny przebieg pomiaru po strojeniu](https://github.com/woogitsugames/Century-war/actions/runs/41203246617). Osiem grup ukończono pomyślnie; w grupie 1–4 odnotowano kolejno:

```text
:app:assembleDebug                  BUILD SUCCESSFUL in 1m 16s
:app:testDebugUnitTest BattleSimTest BUILD SUCCESSFUL in 14s
:app:testDebugUnitTest mirrorReport  BUILD SUCCESSFUL in 6s
:app:testDebugUnitTest campaignReport BUILD SUCCESSFUL in 5s
```

W każdym wierszu wynik z 20 seedów. Zapis wyników bazowych pochodzi z osobnego uruchomienia na oryginalnej wersji silnika i etapów; nie należy porównywać go z opcją `BALANCE_BASELINE=true` uruchomioną *po* zmianie `Defs.kt` i `Battle.kt`, ponieważ ta opcja przywraca tylko stare parametry etapów.

## Ryzyka

- `aiPower` w późnych etapach sięga 6,90× gospodarki i XP AI; jest to znacznie więcej niż historyczne 0,72–1,68×. Wpływa na widoczną dla gracza liczbę i tempo natarć, choć AI nie otrzymuje ukrytego bonusu do obrażeń.
- Zmiana `Defs.baseHp` wpływa także na **potyczkę, wyzwanie dnia i tryb bez końca**. Brak presji blokady w `ENDLESS` nie oznacza, że jego czas rozgrywki jest niezmieniony. Wskazane są testy innych trybów na rzeczywistych telefonach 2–3 GB RAM.
- 20 seedów daje rozdzielczość win rate co 5 punktów procentowych i nie gwarantuje wyników graczy ludzkich. Krzywa odsetków zwycięstw nie jest ściśle monotoniczna, gdyż epoki i modyfikatory zmieniają się między etapami.
- Nie edytowano ekranów ani zastrzeżonych plików renderera lub UI.

## Odtworzenie

```bash
./gradlew :app:assembleDebug --console=plain
./gradlew :app:testDebugUnitTest --tests '*BattleSimTest*' --console=plain
./gradlew :app:testDebugUnitTest --tests '*BalanceReport.campaignReport' --console=plain
./gradlew :app:testDebugUnitTest --tests '*BalanceReport.mirrorReport' --console=plain
```

Raport powstaje w `app/build/balance/` (katalog roboczy testu Gradle). Pełen przebieg testu uruchamianego bez filtrów obejmuje 1920 walk. Dla etapu lub profilu ustaw `BALANCE_STAGE_FROM`, `BALANCE_STAGE_TO`, `BALANCE_PROFILE` i `BALANCE_RUNS`.
