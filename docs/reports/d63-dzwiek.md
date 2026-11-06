# D63 — dźwięk: przegląd, wyrównanie głośności, kliknięcia, barwy epok

Decyzja właściciela 6.11 (muzyka i dźwięk). Gałąź `claude/dzien-63-dzwiek`.

## Jak mierzone

- **Efekty** — `SfxAudit63Test` (na żądanie: `D63_AUDIO_OUT=/katalog ./gradlew :app:testDebugUnitTest --tests 'com.centurywar.screens.SfxAudit63Test'`) renderuje wszystkie efekty `SoundSynth` (oba warianty) do WAV i liczy: głośność K-ważoną wg ITU-R BS.1770 (filtr K dla 22 050 Hz jak w libebur128), szczyt, poziom ogona przed wyciszeniem, wskaźnik trzasków (pik drugiej różnicy / jej 95. percentyl).
- **„W grze”** — głośność chwilowa (LUFS-M, 400 ms) przy typowym `vol` z wywołań i suwaku efektów 0,8 (`vol · 0,8 · 0,42`, jak `Sound.playLoaded`).
- **Miks bitwy** — 4 min prawdziwej bitwy AI (seed 12) przez tę samą ścieżkę co gra: `BattleSfx.playTo` → model `Sound` (`SfxMixModel`: `SfxRateGate`, budżet „quiet”, `SfxMix` z limiterem, jitter wysokości, warianty). Wynik przed zmianami **−35,3 LUFS** — zgodny z pomiarem z nocy 2 (−35,0, `docs/music/README.md`) i z `ffmpeg ebur128` na tym samym pliku (−35,3).
- **Muzyka** — `ffmpeg ebur128` na wszystkich 21 OGG (pełna skala i w grze: suwak 0,5 × `LOOP_GAIN` 0,1).
- **Podobieństwo epok** — kształt widma w pasmach tercjowych 100 Hz–8 kHz (dB względem średniej), z wysokością odtwarzania `eraPitch`; odległość < 2 dB = praktycznie ten sam dźwięk.

## Co znalazłem

1. **Głośność w rodzinach rozjechana o 3–3,8 LU.** Synteza normalizowała RMS według barwy, a ucho (filtr K) słyszy jasne barwy głośniej: `HIT_STEEL` −16,2 LUFS vs `HIT_WOOD` −19,4 (drewno — najczęstszy efekt epoki kamienia — był najcichszy), `SHOT_CROSSBOW` −21,3 vs `SHOT_BOW` −18,0, wieże pierwotne −21,5 vs prochowa −18,4, `BARRAGE_STONE` −22,6 vs `BARRAGE_FIRE` −18,8, `DEATH_MACHINE` −21,3 vs `DEATH_BEAST` −18,4.
2. **Za cichy sygnał błędu.** `ERROR` przy `vol` 0,34 grał −44,3 LUFS — 5 dB pod klikiem (−39,3), łatwo go przeoczyć.
3. **Przesterowań nie ma** — wszystkie szczyty ≤ −2,4 dBFS (sufit 0,76), miks bitwy −12,3 dBFS.
4. **Trzaski na granicach nut** efektów wielonutowych: faza oscylatora zerowana i obwiednia skacząca do 1 przy każdej nucie — `CARD_EPIC` 12,9, `VICTORY` 10,5, `COIN`/`PURCHASE` 9,6, `STAR` 8,9, `CARD_LEGENDARY` 7,5 (próg słyszalności ~6).
5. **Urwany koniec**: `COIN`, `PURCHASE`, `ERROR` (0,12 s) ucinały drugą nutę w pół brzmienia — ogon −8,5…−9 dB względem RMS tuż przed 12-ms wyciszeniem.
6. **Zbyt podobne epoki**: jeden `EXPLOSION` dla wszystkich 13 stylów (różniła tylko wysokość; odległość widma 0,2–1,0 dB), `HIT_STEEL` wspólny dla żelaza, średniowiecza, prochu, pary, spalania, galwanicznej i stali (0,4–2,8 dB).
7. **Muzyka** — wszystkie pliki −16,0…−16,2 LUFS, szczyty −2,2…−5,3 dBTP; w grze −42,0…−42,2 LUFS = **6,7–6,9 dB pod efektami**. Oddech działa (dno −57 LUFS, czyli −15 dB, płynne przejścia).

## Zmiany

- **Normalizacja według głośności K-ważonej, z celem na rodzinę** (`SfxLayers.loudnessTarget`): trafienia −18,3, strzały −19,0, śmierci −19,3, wieże −19,6, ostrzał −20,6, wybuchy −20,7, ewolucje −21,4, UI −20,3 LUFS. Cele to dawne średnie rodzin, więc hierarchia (trafienie < strzał < wybuch < ostrzał) i miks bitwy zostają: **−35,3 → −35,3 LUFS**. Miękki ogranicznik (kolano 0,56, sufit 0,76 = `SfxMix.SAMPLE_PEAK`) obcina tylko pojedyncze szczyty ataku tych efektów, które wcześniej nie mogły dojść do poziomu przez sufit.
- **Kliknięcia**: kolejna nuta z ciągłą fazą i rampą 4 ms; tony (UI) wchodzą w 4 ms (uderzenia nadal 1,8 ms — atak ma być ostry); wyjście kosinusowe 12 % długości (15–40 ms) zamiast liniowych 12 ms; `COIN`/`PURCHASE`/`ERROR` 0,17 s zamiast 0,12 s.
- **Barwy epok (synteza, bez plików audio)** — trzy nowe próbki dopisane na końcu (stare indeksy bez zmian, `VERSION` 5 kasuje stary cache WAV):
  - `HIT_POWDER` — trafienie pociskiem (niski „łup” z ziarnem prochu) dla prochu, pary, spalania, galwanicznej i stali; `HIT_STEEL` (dzwonienie ostrza) zostaje dla żelaza i średniowiecza;
  - `EXPLOSION_PRIMITIVE` — głuchy łomot kamienia/drewna (struna Karplusa-Stronga 150 Hz, ciemny filtr 380 Hz) dla epok przed prochem;
  - `EXPLOSION_ENERGY` — huk z opadającym „zapem” (FM jak broń energetyczna) dla krzemu, roju, biotechu i orbity; prochowy `EXPLOSION` zostaje dla epok prochu i stali.
  Wybuch nadal nigdy nie używa próbek ostrzału (`BARRAGE_*`). `prepareEra` zamawia wybuch epoki i `HIT_POWDER`; rodziny bramki odstępów (`SfxRateGate`) obejmują nowe próbki. Bank WAV mieści się w limicie 2 MiB.
- **`ERROR`**: `vol` 0,34 → 0,5 (−44,3 → −39,5 LUFS, na równi z klikiem i zakupem).
- **Bez zmian**: fokus audio (muzyka gry nie przerywa cudzej — jak dotąd), wyniki bitew (dźwięk nie dotyka symulacji), ustawienia (nie trzeba nowych — brak tekstów `part210`), muzyka i jej poziomy.

## Głośność przed/po

LUFS = głośność K-ważona samego zdarzenia (wariant 0); „w grze” = LUFS-M przy typowym `vol` i suwaku 0,8; ogon = 40 ms przed wyciszeniem względem RMS efektu (> −10 = urwany).

| id | efekt | LUFS przed | LUFS po | szczyt przed | szczyt po | w grze przed | w grze po | ogon przed | ogon po |
|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 0 | HIT | −18,8 | −18,3 | −2,4 | −3,1 | −42,3 | −41,8 | −20,1 | −20,1 |
| 1 | SHOT | −18,6 | −19,0 | −2,4 | −3,4 | −38,6 | −39,0 | −32,6 | −32,8 |
| 2 | ARROW | −18,0 | −19,0 | −3,2 | −4,2 | −38,1 | −39,0 | −35,4 | −35,5 |
| 3 | EXPLOSION | −20,7 | −20,7 | −2,4 | −3,2 | −30,6 | −30,6 | −33,9 | −34,9 |
| 4 | DEATH | −18,6 | −19,3 | −2,4 | −3,5 | −36,1 | −36,8 | −38,9 | −39,5 |
| 5 | LASER | −18,3 | −19,0 | −2,4 | −3,5 | −38,3 | −39,0 | −30,3 | −30,4 |
| 6 | EVOLVE | −20,8 | −21,4 | −2,4 | −3,8 | −29,9 | −30,5 | −76,3 | −76,2 |
| 7 | COIN | −20,1 | −20,3 | −6,1 | −5,1 | −47,9 | −46,6 | −8,6 | −24,2 |
| 8 | CLICK | −20,1 | −20,3 | −5,4 | −5,0 | −39,3 | −39,4 | −28,6 | −28,0 |
| 9 | VICTORY | −21,0 | −20,3 | −2,4 | −3,1 | −28,8 | −28,1 | −67,4 | −67,6 |
| 10 | DEFEAT | −20,3 | −20,3 | −2,7 | −3,3 | −28,9 | −28,9 | −49,1 | −49,2 |
| 11 | THUMP | −19,1 | −19,1 | −2,4 | −3,2 | −35,1 | −35,2 | −25,9 | −25,9 |
| 12 | HIT_WOOD | −19,4 | −18,3 | −2,4 | −3,0 | −42,9 | −41,8 | −19,2 | −19,1 |
| 13 | HIT_BRONZE | −17,9 | −18,3 | −4,7 | −5,1 | −41,4 | −41,8 | −16,3 | −16,3 |
| 14 | HIT_STEEL | −16,2 | −18,3 | −3,3 | −5,4 | −39,7 | −41,8 | −13,7 | −13,7 |
| 15 | HIT_ENERGY | −18,0 | −18,3 | −4,0 | −4,3 | −41,5 | −41,8 | −18,2 | −18,2 |
| 16 | DEATH_HUMAN | −18,6 | −19,3 | −2,4 | −3,5 | −36,1 | −36,8 | −39,3 | −40,0 |
| 17 | DEATH_BEAST | −18,4 | −19,3 | −3,3 | −4,3 | −35,8 | −36,8 | −34,1 | −34,7 |
| 18 | DEATH_MACHINE | −21,3 | −19,3 | −2,4 | −2,9 | −38,8 | −36,8 | −38,9 | −39,5 |
| 19 | SHOT_SLING | −19,4 | −19,0 | −2,4 | −3,1 | −39,5 | −39,0 | −32,2 | −32,3 |
| 20 | SHOT_BOW | −18,0 | −19,0 | −3,0 | −4,1 | −38,0 | −39,0 | −36,4 | −36,6 |
| 21 | SHOT_CROSSBOW | −21,3 | −19,1 | −2,4 | −2,9 | −41,3 | −39,1 | −45,1 | −45,0 |
| 22 | SHOT_MUSKET | −19,1 | −19,0 | −2,4 | −3,2 | −39,1 | −39,0 | −30,9 | −31,0 |
| 23 | SHOT_CANNON | −18,3 | −19,0 | −2,4 | −3,5 | −38,4 | −39,0 | −24,4 | −24,5 |
| 24 | SHOT_RIFLE | −19,2 | −19,0 | −2,4 | −3,2 | −39,2 | −39,1 | −42,3 | −42,3 |
| 25 | SHOT_GUN | −19,9 | −19,0 | −2,4 | −3,0 | −40,0 | −39,0 | −35,1 | −35,2 |
| 26 | SHOT_LASER | −18,0 | −19,0 | −2,6 | −3,8 | −38,1 | −39,0 | −30,8 | −31,0 |
| 27 | SHOT_PLASMA | −18,1 | −19,0 | −2,6 | −3,7 | −38,1 | −39,0 | −24,9 | −25,1 |
| 28 | TURRET_PRIMITIVE | −21,5 | −19,6 | −2,4 | −3,0 | −37,9 | −36,0 | −39,9 | −40,4 |
| 29 | TURRET_MEDIEVAL | −21,5 | −19,6 | −2,4 | −3,0 | −37,8 | −36,0 | −43,2 | −43,7 |
| 30 | TURRET_POWDER | −18,4 | −19,6 | −2,4 | −3,8 | −34,8 | −36,0 | −31,1 | −31,4 |
| 31 | TURRET_MODERN | −19,8 | −19,6 | −2,4 | −3,2 | −36,2 | −36,0 | −36,4 | −36,8 |
| 32 | TURRET_ENERGY | −18,9 | −19,6 | −2,4 | −3,5 | −35,3 | −36,0 | −31,9 | −32,2 |
| 33 | BARRAGE_STONE | −22,6 | −20,6 | −2,4 | −2,9 | −31,7 | −29,8 | −47,9 | −48,7 |
| 34 | BARRAGE_FIRE | −18,8 | −20,6 | −2,4 | −4,2 | −28,1 | −29,8 | −17,1 | −18,3 |
| 35 | BARRAGE_CANNON | −20,6 | −20,6 | −2,4 | −3,2 | −29,8 | −29,8 | −33,6 | −34,7 |
| 36 | BARRAGE_ROCKET | −22,1 | −20,6 | −2,4 | −3,0 | −31,3 | −29,8 | −29,5 | −30,7 |
| 37 | BARRAGE_ORBITAL | −20,9 | −20,6 | −2,4 | −3,2 | −30,0 | −29,8 | −27,5 | −28,6 |
| 38 | EVOLVE_PRIMITIVE | −22,7 | −21,4 | −2,4 | −3,0 | −31,8 | −30,6 | −80,0 | −79,5 |
| 39 | EVOLVE_MEDIEVAL | −20,3 | −21,4 | −2,4 | −3,7 | −29,4 | −30,5 | −77,1 | −77,1 |
| 40 | EVOLVE_INDUSTRIAL | −22,7 | −21,4 | −2,4 | −3,2 | −31,8 | −30,5 | −80,3 | −80,4 |
| 41 | EVOLVE_FUTURE | −21,6 | −21,4 | −2,4 | −3,2 | −30,7 | −30,5 | −47,0 | −48,3 |
| 42 | MEDALS | −20,1 | −20,3 | −2,9 | −3,4 | −31,1 | −31,3 | −55,6 | −55,8 |
| 43 | ACHIEVEMENT | −20,2 | −20,3 | −3,2 | −3,8 | −31,2 | −31,3 | −47,4 | −47,6 |
| 44 | CARD_COMMON | −20,3 | −20,3 | −4,2 | −4,5 | −37,7 | −37,7 | −37,4 | −37,3 |
| 45 | CARD_RARE | −20,2 | −20,3 | −5,1 | −5,0 | −37,6 | −37,7 | −42,5 | −42,4 |
| 46 | CARD_EPIC | −20,1 | −20,3 | −4,3 | −4,3 | −37,5 | −37,7 | −29,2 | −29,0 |
| 47 | CARD_LEGENDARY | −20,0 | −20,3 | −3,0 | −3,6 | −37,4 | −37,7 | −32,6 | −32,3 |
| 48 | RESEARCH | −20,2 | −20,3 | −5,1 | −4,9 | −37,2 | −37,3 | −46,9 | −46,4 |
| 49 | STAR | −20,0 | −20,3 | −5,3 | −5,3 | −37,8 | −38,1 | −40,7 | −40,8 |
| 50 | ERA_UNLOCK | −20,2 | −20,3 | −2,9 | −3,8 | −34,7 | −34,8 | −47,2 | −47,5 |
| 51 | BOSS | −20,3 | −20,3 | −2,4 | −3,2 | −30,9 | −30,9 | −30,5 | −31,6 |
| 52 | PURCHASE | −20,1 | −20,3 | −6,0 | −4,7 | −40,8 | −39,5 | −8,5 | −24,2 |
| 53 | ERROR | −20,3 | −20,3 | −7,4 | −5,9 | −44,3 | −39,5 | −9,0 | −23,7 |
| 54 | REWARD | −20,1 | −20,3 | −3,0 | −3,5 | −31,1 | −31,3 | −55,1 | −55,3 |
| 55 | RANK_UP | −20,1 | −20,3 | −3,0 | −3,6 | −33,1 | −33,3 | −59,8 | −59,9 |
| 56 | ALERT_BASE | −20,4 | −20,3 | −5,0 | −5,0 | −30,3 | −30,2 | −35,2 | −35,6 |
| 57 | ALERT_ENEMY_ERA | −20,2 | −20,3 | −5,0 | −4,9 | −31,0 | −31,2 | −26,7 | −27,2 |
| 58 | ALERT_READY | −19,5 | −20,3 | −6,4 | −6,9 | −38,9 | −39,7 | −32,9 | −32,6 |
| 59 | HIT_POWDER (nowy) | — | −18,3 | — | −3,2 | — | −41,8 | — | −22,5 |
| 60 | EXPLOSION_PRIMITIVE (nowy) | — | −20,7 | — | −2,9 | — | −30,7 | — | −50,5 |
| 61 | EXPLOSION_ENERGY (nowy) | — | −20,7 | — | −3,0 | — | −30,6 | — | −37,1 |

Rozrzut w rodzinach (wariant 0; `SoundBalance63Test` pilnuje ≤ 1 LU dla obu wariantów): trafienia 3,2 → 0,0 LU, strzały 3,3 → 0,1, wieże 3,1 → 0,0, ostrzał 3,8 → 0,0, śmierci 2,9 → 0,0, ewolucje 2,4 → 0,0. Ogony `HIT_STEEL`/`HIT_BRONZE` (−14/−16) to dzwonienie metalu wygaszane teraz kosinusem 16 ms — bez trzasku.

### Trzaski (wskaźnik od 45 ms, po fazie ataku; > 6 słychać)

| efekt | przed | po |
|---|---:|---:|
| CARD_EPIC | 12,9 (0,080 s — granica nut) | 3,1 |
| VICTORY | 10,5 (0,145 s) | 4,9 |
| COIN / PURCHASE | 9,6 (0,060 s) | 2,4 |
| STAR | 8,9 (0,090 s) | 2,3 |
| CARD_LEGENDARY | 7,5 (0,080 s) | 3,6 |

Ewolucje (5,8–6,9 przed i po, ok. 0,05 s) to koniec szumowego ataku, nie trzask — bez zmian.

### Epoki: najmniejsza odległość widma między **różnymi** próbkami

| zdarzenie | przed | po |
|---|---|---|
| trafienie (człowiek) | 4 próbki; `HIT_STEEL` w 7 stylach (0,4–2,8 dB między nimi) | 5 próbek; najbliższe różne 6,3 dB |
| wybuch | 1 próbka w 13 stylach (0,2–1,0 dB) | 3 próbki; najbliższe różne 3,7 dB |
| wieża | bez zmian: 5 próbek, najbliższe różne 3,5 dB | — |

Style w jednej grupie (np. TRIBAL/BRONZE dla `EXPLOSION_PRIMITIVE`) dalej dzielą próbkę i różnią się wysokością `eraPitch` — zamierzone.

## Muzyka i efekty

| pomiar | przed | po |
|---|---:|---:|
| miks efektów bitwy (seed 12, 4 min, suwak 0,8) | −35,3 LUFS | −35,3 LUFS |
| szczyt miksu | −12,3 dBFS | −13,2 dBFS |
| pętla muzyki w grze (suwak 0,5, 21 plików) | −42,0…−42,2 LUFS | bez zmian |
| odstęp muzyka → efekty | 6,7–6,9 dB | 6,7–6,9 dB |
| muzyka na dnie oddechu (155–175 s) | −57,0 LUFS (−15 dB) | bez zmian |

`SoundBalance63Test.battleMixKeepsMusicSevenDbBelow` pilnuje miksu (±1 LU) i odstępu 6–8 dB; `MusicBreathTest` (13 testów) — oddechu w mikserze, przy przerywniku, pauzie i ustąpieniu.

## Wydajność

Zmiany są tylko w syntezie na wątku `sfx-gen` (pomiar K-ważony to dwa biquady na próbkę, raz na wygenerowanie) i w mapowaniu `BattleSfx` (stałe `when`). `BattleSfx.playTo(SfxSink, …)` — `Game` jest swoim ujściem, bez lambd i alokacji. Wątek `sfx-gen` nadal czeka bez limitu czasu (d59). `LateEraPerformanceTest`, `LowEndScreensPerformanceTest`, `SfxMixTest.hotPathDoesNotAllocatePerSound` — zielone.

## Testy

`*Sound*`, `*Music*`, `*LowEnd*`, `*LateEra*`, `*Sfx*` (18 klas) — zielone. Nowe: `SoundBalance63Test` (rodziny ±1 LU, trzaski i ogony, różne barwy grup epok, miks bitwy i odstęp muzyki), `SfxAudit63Test` (przegląd na żądanie). Zmienione: `SoundSynthTest` (62 efekty, wersja 5), `ReplaySfxTest` (trzy wybuchy epok, prewarm `HIT_POWDER`).

## Próbki do odsłuchu

W `/tmp/claude-0/d63/` (poza repo):

- `porownanie_wszystkie_przed_po.wav` — każdy efekt: przed, 0,4 s ciszy, po; pojedynczo w `porownanie_przed_po/`;
- `przed/`, `po/` — `efekty/*.wav` (oba warianty), `wszystkie_efekty.wav`, `epoki_trafienie.wav`, `epoki_wybuch.wav`, `epoki_wieza.wav` (13 stylów po kolei, z wysokością epoki), `bitwa_seed12_efekty.wav`, `raport.txt`;
- `bitwa_z_muzyka_przed.wav`, `bitwa_z_muzyka_po.wav` — 4 min bitwy z pętlą `era1_battle` na poziomie gry i oddechem od 150 s;
- `muzyka_w_grze_z_oddechem.wav`, `muzyka.txt`, `trzaski.txt`, `tabela.md`.
