# Zlecenie 55 — muzyka proceduralna epok

## Kompozycja i technika

- Dziewięć wariantów barw (8 presetów grafiki + menu), sześć rodzin:
  pierwotne (flet i bębny ramowe), antyczne (struny szarpane),
  średniowieczne (struny i drewniane instrumenty), proch (róg i werbel),
  przemysł/stal (fale filtrowane, miarowa perkusja), przyszłość
  (syntezatory FM, dźwięk dzwonkowy, rytm elektroniczny).
- Trzy sekcje po 32 szesnastki: A, B z kontrapunktem i spokojniejsze
  przejście. Wspólna sekwencja stopni 0–2–4–3–2–0 jest aranżowana
  w skalach pentatonicznej, doryckiej, durowej, molowej, harmonicznej
  i lidyjskiej. Każdy preset ma własny BPM.
- Dwa głosy melodyczne z miękkim atakiem i wybrzmiewaniem, w tym
  model struny Karplus–Strong, modulacja FM, filtracja dolnoprzepustowa,
  bas, trzygłosowe tło, odpowiedź melodyczna w sekcji B i trzy
  typy perkusji. Dwa opóźnienia z ograniczonym sprzężeniem działają
  jako pogłos; kompresja soft-knee ogranicza sumujące się szczyty.
- Natężenie zmienia udział basu, bębnów i kontrapunktu z płynną
  obwiednią, bez przełączania instrumentów na granicach bloków.
  Menu jest spokojniejsze. Zachowano 2 s crossfade epok oraz ducking
  przy efektach. Próbki są wyliczane w osobnym wątku muzyki.
- AudioTrack: blok 2048 próbek i bufor sprzętowy co najmniej 0,5 s.
  Nie ma plików audio ani nowych zależności w instalowanej aplikacji.

## Testy i pomiary lokalne

**NIE URUCHOMIONO GRADLE / ANDROID SDK**: w dostępnym kontenerze
nie ma Android SDK ani bezpośredniego dostępu do internetu, więc
`:app:assembleDebug` i `:app:testDebugUnitTest` nie zostały wykonane.
Wymagają potwierdzenia przez CI lub na stanowisku z Android SDK.

- `kotlinc` skompilował kod `MusicSynth.kt`.
- Cztery testy `MusicArrangementTest` uruchomiono przez samodzielny
  program JVM (z minimalnymi atrapami asercji JUnit): **4/4 OK**.
- `MusicPreviewTest` został uruchomiony na JVM przy `MUSIC_PREVIEW=true`:
  **12/12 WAV**, każdy 60 s / 22 050 Hz / mono / PCM16,
  długość pliku 2 646 044 B. Pliki zweryfikowano czytnikiem WAV.
- 9 presetów (menu i 8 epok) po ok. 6,5 s: próbki skończone,
  brak przesterowań, maksymalny skok próbki w tych próbach 0,1501.
  Dotychczasowe testy JUnit `MusicSynthTest` muszą być sprawdzone przez CI.
- Pomiar JVM, jedna epoka przyszłości, rozgrzany JIT:
  **60,60 ms CPU / 59,91 s dźwięku = 0,101% jednego rdzenia**
  (Linux/JDK, bez AudioTrack). Nawet przy 30-krotnie wolniejszej
  syntezie daje to szacunkowo 3,0% rdzenia. **To oszacowanie,
  nie pomiar na tanim telefonie** — próg 6% zweryfikować urządzeniem.

## Odsłuch

```bash
MUSIC_PREVIEW=true ./gradlew :app:testDebugUnitTest \
  --tests '*MusicPreviewTest*' --rerun-tasks --console=plain
```

Eksport: `app/build/music/{primitive,ancient,medieval,powder,industrial,future}_{low,high}.wav`.
Bez zmiennej `MUSIC_PREVIEW=true` test jest pomijany i nie zapisuje plików.

## Pliki i ryzyka

- Zmienione: `engine/MusicSynth.kt`, `engine/Music.kt`.
- Nowe: `MusicArrangementTest.kt`, `MusicPreviewTest.kt`,
  `docs/MUSIC_55.md`.
- Nie zmieniano `SoundSynth.kt`, `Sound.kt`, `BattleSfx.kt`.
- Ryzyko: brak testu Android AudioTrack na fizycznym urządzeniu;
  porównanie poziomu z efektami i końcowy odsłuch właściciela
  wymagają sprawdzenia na telefonie.
