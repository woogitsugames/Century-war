# Muzyka w grze

Gra ma dwa źródła muzyki:

- **nagrania** (Suno, opisy w [`SUNO_PROMPTS.md`](SUNO_PROMPTS.md)) w `app/src/main/assets/music/<nazwa>.ogg`,
- **syntezę proceduralną** (`engine/Music.kt`, `MusicSynth.kt`) — zapas dla każdego utworu, którego pliku jeszcze nie ma.

Wybór jest automatyczny i osobny dla menu i każdej epoki: jest plik → gra nagranie, brak → synteza. Dzięki temu muzykę można dostarczać partiami. Suwak „Muzyka” w ustawieniach (i w pauzie bitwy) działa dla obu źródeł.

## Nazwy plików

| Plik | Użycie |
|---|---|
| `menu.ogg` | menu i ekrany poza bitwą (pętla) |
| `victory.ogg`, `defeat.ogg` | jednorazowo na ekranie wyniku bitwy; pętla cichnie na ten czas, a krótki efekt wyniku nie gra |
| `era0_calm.ogg` … `era8_calm.ogg` | epoka w bitwie, spokój (pętla) |
| `era0_battle.ogg` … `era8_battle.ogg` | epoka w bitwie, starcie (pętla) |

W bitwie obie wersje epoki startują razem i przenikają się według napięcia (`BattleMusicIntensity`): udział bojowej = smoothstep(0,25..0,75), prowadzony przez `BattleMixFollower` z histerezą — wzrost szybko (pełna skala w 1,5 s), spadek dopiero po 7 s bez nowego szczytu (spadek celu o mniej niż 0,15 nie liczy się), potem powoli (4 s). Przenikanie ma krzywą równej mocy (sin/cos), więc w połowie nie ma dołka −3 dB. Zmiana epoki to przenikanie ~2 s do nowej pary, start z ciszy — wejście 0,6 s. Jeśli epoka ma tylko jedną wersję, gra tylko ona. Wyciszanie przy ważnych efektach (`duck`) działa jak w syntezie.

## Jak dodać pliki

1. **Właściciel** przysyła pliki z Suno (WAV najlepiej; MP3/M4A/FLAC też przejdą), nazwane dokładnie jak w tabeli `SUNO_PROMPTS.md`, np. `era3_battle.wav` (wielkość liter bez znaczenia). Pliki o innych nazwach (np. `era3_battle (1).wav`) skrypt pominie z ostrzeżeniem — trzeba je przemianować.
2. **Koordynator** rozpakowuje je do dowolnego katalogu poza repo i uruchamia:
   ```bash
   scripts/convert_music.sh ~/suno-partia-1
   ```
   Wymaga `ffmpeg` i `ffprobe` (najlepiej z `libvorbis`; bez niego skrypt użyje wbudowanego, gorszego kodera i to wypisze). Skrypt nie jest częścią builda.
   Przetwarzanie: przycięcie ciszy na początku i końcu, stereo 44,1 kHz, normalizacja do −16 LUFS (pomiar `loudnorm`, potem samo wzmocnienie, a `loudnorm` tylko gdy szczyt przekroczyłby −1,5 dBTP — długość i złączenie pętli zostają co do próbki), OGG Vorbis ~96 kb/s (`libvorbis -q:a 2.5`). Pętle są zszyte w pliku (sekcja „Pętle bez oddechu” niżej) i mają znacznik `ANDROID_LOOP=true`; `victory`/`defeat` nie są pętlami. Wynik jest deterministyczny. Dobór punktów pętli wymaga `python3` (sama biblioteka standardowa, `scripts/music_loop_points.py`); bez niego pętla obejmuje całe nagranie.
   Na końcu skrypt wypisuje tabelę (plik, długość, rozmiar, LUFS), punkty pętli (ile wstępu i wybrzmienia pominięto) i listę utworów, których jeszcze brakuje.
3. Odsłuchaj złączenie pętli kilku plików (np. w odtwarzaczu z zapętleniem), potem **commit** samych `.ogg` z `app/src/main/assets/music/` (źródeł WAV nie commitujemy). Uzupełnij `LICENSE_NOTES.md`.
4. Test skryptu (sygnały testowe ffmpeg): `scripts/test_convert_music.sh`.

Kolejną partię dodaje się tak samo; pliki o tej samej nazwie są nadpisywane.

## Rozmiar APK

Komplet 21 utworów (w tym dwa krótkie przerywniki) po zszyciu pętli to **19,0 MiB** (wcześniej 21,1 MiB: pętle są krótsze o nakładkę 3 s i pominięty cichy wstęp/wybrzmienie); debug APK 23,2 MB zamiast 25,5 MB. Pliki `.ogg` nie są kompresowane w APK (`noCompress`, wymagane przez `openFd`), ale Vorbis i tak się nie kompresuje. Jeśli rozmiar okaże się problemem, kandydatami do późniejszego pobierania są epoki 5–8 (gracz trafia tam po kilku godzinach gry) — gra i tak zagra wtedy syntezę.

## Odtwarzanie — szczegóły i ograniczenia

- `RecordedMusic` działa na własnym wątku sterującym (otwieranie plików, `MediaPlayer.prepare`, głośności co 40 ms); wątek gry tylko ustawia pola. Lista plików jest czytana raz przy starcie (`AssetManager.list("music")`).
- Pauza Activity, utrata fokusu audio i głośność 0 zwalniają wszystkie odtwarzacze; po powrocie utwór zaczyna się od początku. Najwyżej 4 odtwarzacze naraz (dwie pary w trakcie przenikania; przerywnik zwalnia wyciszaną parę).
- Pętle nie używają `setLooping`: `ChainedLoop` 12 s przed końcem okrążenia przygotowuje drugi `MediaPlayer` tego samego pliku i łączy je przez `setNextMediaPlayer`; po przejściu stary jest zwalniany (najwyżej 2 odtwarzacze na utwór i tylko pod koniec okrążenia). Błąd łańcucha przełącza ten utwór na `setLooping(true)`.
- Plik, którego nie da się otworzyć, jest oznaczany jako brakujący i do restartu gry gra synteza.

## Szlif odtwarzania (noc 2) — decyzje i pomiary

Właściciel jeszcze nie odsłuchał zmian; wszystkie wartości są z pomiarów, stałe łatwo stroić (`RecordedMixer`, `BattleMixFollower`, `music_loop_points.py`).

### 1. Pętle bez „oddechu”

Przyczyna: stary skrypt robił w każdej pętli wyjście 1,5 s i wejście 0,5 s, a nagrania Suno mają cichy wstęp i 1–3 s wybrzmienia — na złączeniu był dołek o 55–88 dB (praktycznie cisza). Do tego `setLooping` w NuPlayerze zapętla przez opróżnienie bufora i `seekTo(0)`, co na wielu urządzeniach dokłada kilkadziesiąt ms przerwy.

Rozwiązanie (dwie warstwy):

- **Zszycie w pliku** (`convert_music.sh` + `music_loop_points.py`): pętla to odcinek S..E oryginału, gdzie S/E omijają cichy wstęp i wybrzmienie (najniższy poziom 1 s w oknie nakładki + 1 s ≥ mediana − 4 dB, szukane do 12 s od brzegu), a E jest przesuwany do 1,5 s wstecz, żeby obwiednia uderzeń końca pokrywała się z początkiem. Ostatnie 3 s przed E są nakładane równą mocą (qsin) na pierwsze 3 s od S; plik zaczyna się w S+3 s, więc koniec pliku przechodzi w początek **ciągle co do próbki**. Normalizacja to samo wzmocnienie (bez przepróbkowania), więc złączenie przeżywa normalizację.
- **Odtwarzanie bez przerwy** (`ChainedLoop`): zamiast `setLooping` łańcuch dwóch `MediaPlayer` przez `setNextMediaPlayer`. MediaPlayerService przekazuje następnikowi ten sam AudioTrack (`switchToNextOutput`), więc nie ma opróżnienia bufora ani seeka; to ta sama ścieżka od Androida 4.1 do 15, niezależna od producenta kodeka. Następnik jest przygotowywany dopiero 12 s przed końcem okrążenia (nie trzymamy podwójnych dekoderów przez cały czas). Gdy system nie wystartuje następnika, robimy to sami w następnym kroku (≤ 40 ms); każdy błąd (`setNextMediaPlayer`, `prepare`) przełącza utwór na zwykłe `setLooping(true)` — wtedy złączenie jest zszyte w pliku, a ewentualna przerwa to tylko seek. Odrzuciłem nakładanie dwóch odtwarzaczy według zegara: pozycja `MediaPlayer` jest za mało dokładna (dziesiątki ms), więc nakładka dawałaby „echo” lub dziurę zależnie od urządzenia.

Pomiar (zdekodowane OGG, okna RMS 50 ms co 10 ms, mono): „złączenie” = najniższe okno w ostatnich 4 s + pierwszej 1 s pętli względem mediany utworu; „środek p10” = 10. percentyl tego samego wskaźnika dla 5-sekundowych odcinków w środku utworu (naturalna dynamika muzyki). Skok próbki na złączeniu / 99. percentyl różnic sąsiednich próbek ±1 s: wszędzie ≤ 0,71 (ciągłe).

| plik | stare złączenie | nowe złączenie | środek p10 | wstęp/wybrzmienie pominięte |
|---|---:|---:|---:|---|
| menu | −80,9 dB | −5,7 dB | −9,3 dB | 0,0 s / 3,8 s |
| era0_calm / battle | −76,6 / −66,9 | −14,4 / −10,0 | −9,6 / −12,1 | 6,5 / 4,7 s; 3,3 / 3,2 s |
| era1 | −64,7 / −88,5 | −13,5 / −6,7 | −13,8 / −9,2 | 8,4 / 6,3 s; 2,7 / 2,0 s |
| era2 | −71,9 / −56,8 | −6,3 / −5,6 | −9,8 / −10,4 | 3,9 / 5,1 s; 2,6 / 2,2 s |
| era3 | −74,1 / −67,9 | −12,3 / −12,0 | −13,6 / −11,1 | 3,6 / 5,1 s; 2,5 / 1,2 s |
| era4 | −59,5 / −72,4 | −9,6 / −10,0 | −8,0 / −8,8 | 4,0 / 5,9 s; 1,3 / 2,3 s |
| era5 | −60,2 / −60,3 | −15,9 / −9,6 | −11,1 / −11,1 | 9,9 / 9,2 s; 2,7 / 1,5 s |
| era6 | −67,4 / −56,7 | −5,7 / −5,6 | −9,8 / −8,7 | 6,3 / 5,2 s; 2,2 / 1,3 s |
| era7 | −74,1 / −75,1 | −5,1 / −5,3 | −7,4 / −9,3 | 6,5 / 8,0 s; 2,6 / 1,6 s |
| era8 | −62,3 / −66,9 | −6,8 / −5,9 | −5,2 / −8,7 | 3,3 / 6,9 s; 2,3 / 1,0 s |

(pary: calm / battle). 13 z 19 pętli mieści się w naturalnej dynamice utworu; najgorsze (era5_calm, era0_calm) mają w strefie złączenia okno ~5 dB cichsze niż typowy środek — to już frazowanie, nie cisza. Cena: pierwsze wejście pętli zaczyna się w środku frazy (mikser robi wejście 0,6 s), a cichy wstęp nagrania nie jest grany.

### 2. Spokojna ↔ bojowa bez „pompowania”

`BattleMixFollower` (opis wyżej). `BattleMusicHysteresisTest` gra 6 bitew AI kontra AI (seedy 11–16, łącznie 88,7 min, napięcie liczone jak w `BattleScreen`): stare wygładzanie 1,5 s w obie strony dawało **3,82 przejścia przez 0,5 na minutę** i 344 zawrócenia kierunku; nowe **0,25 przejścia/min** i 21 zawróceń (droga miksu 278 → 18). Test pilnuje ≤ 2,2 przejścia/min i co najmniej dwukrotnej poprawy.

### 3. Głośność muzyki względem efektów

Pomiar `ffmpeg ebur128` (stereo, liniowe wzmocnienia jak w grze: `SoundPool` i `MediaPlayer.setVolume` są liniowe):

- efekty bitwy: 4 min prawdziwej bitwy AI (seed 12) zmiksowane jak `BattleSfx` + bramka odstępów i budżet z `Sound`, suwak efektów 0,8 → **−35,0 LUFS** (krótkoterminowo: mediana −37, p90 −29); pojedyncze efekty przy typowym `vol`: trafienie −42, strzał −38, śmierć −36, wybuch −31, ostrzał −28 LUFS-M max;
- nagrania przed zmianą, suwak 0,5: **−22,1 LUFS**, czyli **13 dB nad efektami** (synteza dla porównania: menu −43, epoka spokój −38, starcie −36 LUFS);
- po zmianie (`LOOP_GAIN = 0,1`, −20 dB): suwak 0,5 → **−42,1 LUFS, 7 dB pod efektami**; suwak 1,0 → −36 LUFS; suwak 0 → cisza i brak fokusu. Przerywnik wyniku `STINGER_GAIN = 0,2` (+6 dB nad pętlą, ok. −36 LUFS) — zastępuje efekt zwycięstwa/porażki (−29 LUFS-M max).
- D63 (6.11): efekty wyrównane w rodzinach według głośności K-ważonej, poziomy rodzin bez zmian — miks bitwy nadal **−35,3 LUFS**, pętle −42,0…−42,2 LUFS (6,7–6,9 dB pod efektami); pilnuje `SoundBalance63Test`. Szczegóły: [`docs/reports/d63-dzwiek.md`](../reports/d63-dzwiek.md).

Synteza (zapas na brakujący/uszkodzony plik) zostaje bez zmian.

### 4. Powrót po przerywniku

Pętla cichnie w 0,4 s na start przerywnika, czeka 0,6 s po jego końcu i wraca w 3 s krzywą S (bez skoku). Zmiana ekranu w trakcie przerywnika (np. powrót do menu) otwiera nową pętlę wyciszoną; wchodzi tym samym powrotem po końcu przerywnika.

### 5. Fokus audio: GAIN, nie MAY_DUCK

- Głośność muzyki 0 (albo gra w tle) → **żadnego żądania fokusu**; fokus trzymany wcześniej jest oddawany od razu po ściszeniu suwaka. Spotify/podcast grają dalej, a efekty gry (SoundPool, bez fokusu) lecą na nie.
- Słyszalna muzyka → `AUDIOFOCUS_GAIN` (`USAGE_GAME`, `CONTENT_TYPE_MUSIC`): gracz zostawił muzykę gry włączoną, więc inna muzyka pauzuje — dwie muzyki naraz to kakofonia. `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` jest przeznaczony na krótkie komunikaty (nawigacja, powiadomienie); przy ciągłej muzyce Spotify grałby ściszony pod naszą pętlą przez całą sesję, a część aplikacji i tak pauzuje przy MAY_DUCK.
- Utrata fokusu (gracz włączył Spotify w trakcie gry) wycisza muzykę gry i **nie** odbieramy fokusu z powrotem przy ruchu suwaka ani zmianie epoki; ponowne żądanie dopiero po powrocie do gry z tła. `LOSS_TRANSIENT_CAN_DUCK` (np. nawigacja) obsługuje system.

Testy: `MusicAudioFocusTest` (Robolectric), `ChainedLoopTest`, `BattleMusicHysteresisTest`, `RecordedMusicTest`, `scripts/test_convert_music.sh` (m.in. RMS 50 ms na złączeniu stałego tonu w ±3 dB od środka).
