# Przekazanie pracy: Century War (stan na 31.10.2026)

## Prompt dla nowej sesji (skopiuj w całości)

> Przejmujesz rolę koordynatora projektu **Century War**, oryginalnej strategii na Androida (Kotlin, SDK 36, minSdk 26, bez bibliotek, grafika rysowana kodem na Canvas). Repo: `woogitsugames/century-war`. Główna gałąź robocza: **`claude/bold-sagan-vwz5gm`**: na niej pracujesz i do niej scalasz. Push na inne gałęzie tylko za zgodą użytkownika.
>
> Najpierw przeczytaj: `README.md`, `docs/GDD.md` (projekt gry, plan rozwoju), `docs/HANDOFF.md` (ten plik: stan i lista zadań), `docs/tasks/README.md` (zlecenia dla zewnętrznego wykonawcy GPT, gałęzie `gpt/NN-*`). Komunikuj się z użytkownikiem po polsku, zwięźle.
>
> Zadania w kolejności:
> 1. Scal do `claude/bold-sagan-vwz5gm` trzy gałęzie integracyjne (każda wyszła z commita `6114244`, a główna jest już dalej, na `bcc158c`, więc będą konflikty, głównie w `engine/L.kt`, `screens/*`, `MainActivity.kt`, `Profile.kt`, `BattleScreen.kt`):
>    - `claude/int-meta`: gpt/02 osiągnięcia, 03 misje, 15 powiadomienia, 01 Google Play Games (zrobione i przetestowane przez integratora; ostatni commit z testem zrzutów jest niezweryfikowany),
>    - `claude/int-infra`: gpt/10 CI, 11 wydanie, 12 sklep (przetestowane) + **08 serwer jako WIP** (konflikty rozwiązane, testy nieuruchomione: uruchom `./gradlew :server:test` i testy aplikacji),
>    - `claude/int-audio-tests`: **09 muzyka jako WIP** (testy nieuruchomione); **14 testy odporności jeszcze NIE scalone**: scal `origin/gpt/14-tests-robustness`.
>    Po każdym scaleniu: build + testy (komendy niżej). Teksty w `L.kt` muszą mieć 10 języków (`LTest`), nowy stan bitwy musi być w `BattleSnapshot` (podbij `VERSION`).
> 2. Scal po kolei `origin/gpt/16-unit-upgrades` → `17-new-era` → `18-era-branches` → `04-balance` (balans na końcu, przestrój pod wszystkie nowe mechaniki). Uważaj na: migrację gwiazdek kampanii po wstawieniu epoki (17), wyzwanie dnia niezmienione dla bieżącego dnia UTC, determinizm i powtórki (`ReplayTest`), styl grafiki nowych epok (`ArtGalleryTest`). Słabe 18 możesz odłożyć i to zgłosić.
> 3. Sprawdź, czy GPT nie dopchnął nowych commitów do `gpt/*` (`git ls-remote origin 'refs/heads/gpt/*'`), i scal je.
> 4. Recenzuj każdą gałąź krytycznie: poprawność, determinizm (`game/` bez zegara/random/kolejności HashMap), zero alokacji na klatkę, działanie od 720p po rozłożonego Folda, brak nawiązań do innych gier, brak emoji w UI.
>
> Komendy:
> ```bash
> echo "sdk.dir=$ANDROID_HOME" > local.properties   # SDK: platforms;android-36, build-tools;36.0.0
> ./gradlew :app:assembleDebug --console=plain
> ./gradlew :app:testDebugUnitTest --tests '*BattleSimTest*' --tests '*BattleRulesTest*' --tests '*Snapshot*' --tests '*LTest*' --tests '*Replay*' --tests '*Counter*' --tests '*Endless*' --tests '*AiPersonality*' --console=plain
> ./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest.menus*' --console=plain --no-configuration-cache   # zrzuty -> app/build/screenshots
> ```
> Robolectric pobiera ~200 MB; przy HTTP 429 z Maven Central pobierz artefakt z `https://maven-central.storage-download.googleapis.com/maven2/...` do `~/.m2/repository`. Emulatora nie ma (brak KVM); wygląd sprawdzaj zrzutami z testów.
>
> Commity po polsku, zakończone liniami atrybucji sesji. Nie twórz PR bez prośby użytkownika.

## Stan projektu

**W `claude/bold-sagan-vwz5gm` (`bcc158c`), zbudowane i przetestowane:**
- **Rozgrywka:** 8 epok, 32 jednostki, wieże, ostrzały, doktryny, zbrojownia, rangi weteranów, presja oblężnicza. Kampania: 32 etapy. Tryby: oblężenie bez końca 2.0 (karty, bossowie, fale tematyczne), wyzwanie dnia, potyczka. Badania (13 drzew), rankingi lokalne.
- **Mechaniki z GPT (scalone):** 5 osobowości AI (05), kontry ról (06), log komend + `Replay.verify` + `BattleController` (07).
- **Grafika i UI:** szczegółowa grafika (`game/art/`), nowe menu i HUD (`engine/Ui.kt`, `engine/Icons.kt`, `screens/Chrome.kt`), Kodeks, interaktywny samouczek, zapis i wznawianie bitwy (`BattleSnapshot`, v3).
- **Dźwięk i języki:** dźwięki syntezowane, 10 języków (`L.kt`), prędkość ×1/×3/×5.
- **Jakość:** poprawki z przeglądu kodu (wątki, Fold, zapis profilu, farmienie medali).

**Gałęzie robocze na GitHubie:**
| Gałąź | Zawartość | Status |
|---|---|---|
| `claude/int-meta` | gpt/02, 03, 15, 01 | gotowe do scalenia (ostatni commit: test zrzutów, niezweryfikowany) |
| `claude/int-infra` | gpt/10, 11, 12 + 08 | 10–12 przetestowane; 08 = WIP bez testów |
| `claude/int-audio-tests` | gpt/09 | WIP bez testów; gpt/14 nie scalone |
| `gpt/16`, `gpt/17`, `gpt/18`, `gpt/04` | prace GPT | nie scalone |

**Ryzyka i uwagi (od integratora bitwy):**
- Endless: AI-gracz ginie ok. 13. fali (trudniej niż wcześniej).
- Usunięcie ukrytego limitu armii i slotów wież wroga ułatwia późną kampanię; do przestrojenia w 04.
- Ostrzał celowany ma tylko AI; dla gracza warto dodać celowanie dotykiem.
- Osobowości ECONOMIST/SIEGER/BALANCED dają podobne wyniki: warto je bardziej zróżnicować.
- Zmiana strumieni RNG zmieniła wyniki dla tych samych seedów (stare wyniki dzienne nieporównywalne).
- Z przeglądu kodu, niezrobione: brak `locale|layoutDirection` w configChanges; predictive back w menu głównym.

**Na później:**
- Ranking globalny wymaga konfiguracji Play Games w Play Console (`docs/PLAY_GAMES_SETUP.md` z gpt/01).
- Własny upload key do wydania (`docs/CI.md` z gpt/10).
- Test na prawdziwych telefonach (tani, flagowiec, Fold).
