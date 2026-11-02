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

W bitwie obie wersje epoki startują razem i przenikają się według napięcia (`BattleMusicIntensity`): udział bojowej = smoothstep(0,25..0,75), wygładzony w czasie ~1,5 s. Zmiana epoki to przenikanie ~2 s do nowej pary. Jeśli epoka ma tylko jedną wersję, gra tylko ona. Wyciszanie przy ważnych efektach (`duck`) działa jak w syntezie.

## Jak dodać pliki

1. **Właściciel** przysyła pliki z Suno (WAV najlepiej; MP3/M4A/FLAC też przejdą), nazwane dokładnie jak w tabeli `SUNO_PROMPTS.md`, np. `era3_battle.wav` (wielkość liter bez znaczenia). Pliki o innych nazwach (np. `era3_battle (1).wav`) skrypt pominie z ostrzeżeniem — trzeba je przemianować.
2. **Koordynator** rozpakowuje je do dowolnego katalogu poza repo i uruchamia:
   ```bash
   scripts/convert_music.sh ~/suno-partia-1
   ```
   Wymaga `ffmpeg` i `ffprobe` (najlepiej z `libvorbis`; bez niego skrypt użyje wbudowanego, gorszego kodera i to wypisze). Skrypt nie jest częścią builda.
   Przetwarzanie: przycięcie ciszy na początku i końcu, stereo 44,1 kHz, normalizacja do −16 LUFS (dwa przejścia `loudnorm`, więc wersja spokojna i bojowa epoki mają tę samą głośność), OGG Vorbis ~96 kb/s (`libvorbis -q:a 2.5`). Pętle dostają wejście 0,5 s, wyjście 1,5 s i znacznik `ANDROID_LOOP=true`; `victory`/`defeat` nie są pętlami. Wynik jest deterministyczny.
   Na końcu skrypt wypisuje tabelę (plik, długość, rozmiar, LUFS) i listę utworów, których jeszcze brakuje.
3. Odsłuchaj złączenie pętli kilku plików (np. w odtwarzaczu z zapętleniem), potem **commit** samych `.ogg` z `app/src/main/assets/music/` (źródeł WAV nie commitujemy). Uzupełnij `LICENSE_NOTES.md`.
4. Test skryptu (sygnały testowe ffmpeg): `scripts/test_convert_music.sh`.

Kolejną partię dodaje się tak samo; pliki o tej samej nazwie są nadpisywane.

## Rozmiar APK

Utwory 2–3 min przy ~96 kb/s to ok. 1,5–2 MB każdy; komplet 21 utworów (w tym dwa krótkie przerywniki) to **ok. 30–35 MB** więcej w APK/AAB. Pliki `.ogg` nie są kompresowane w APK (`noCompress`, wymagane przez `openFd`), ale Vorbis i tak się nie kompresuje. Jeśli rozmiar okaże się problemem, kandydatami do późniejszego pobierania są epoki 5–8 (gracz trafia tam po kilku godzinach gry) — gra i tak zagra wtedy syntezę.

## Odtwarzanie — szczegóły i ograniczenia

- `RecordedMusic` działa na własnym wątku sterującym (otwieranie plików, `MediaPlayer.prepare`, głośności co 40 ms); wątek gry tylko ustawia pola. Lista plików jest czytana raz przy starcie (`AssetManager.list("music")`).
- Pauza Activity, utrata fokusu audio i głośność 0 zwalniają wszystkie odtwarzacze; po powrocie utwór zaczyna się od początku. Najwyżej 4 odtwarzacze naraz (dwie pary w trakcie przenikania; przerywnik zwalnia wyciszaną parę).
- Pętle grają przez `MediaPlayer` z `setLooping(true)` i znacznikiem `ANDROID_LOOP`; na części urządzeń złączenie może mieć bardzo krótką przerwę, dlatego skrypt robi łagodne wyjście/wejście zamiast twardego cięcia (słychać to jako krótki oddech co pętlę).
- Plik, którego nie da się otworzyć, jest oznaczany jako brakujący i do restartu gry gra synteza.
