# Century War

Strategia na Androida: wojna na jednym pasie przez 8 epok, od plemion do ery orbitalnej.
Krótkie bitwy, długa metaprogresja (kampania 32 etapów, badania, oblężenie bez końca,
wyzwanie dnia, rankingi). Projekt i zawartość: [docs/GDD.md](docs/GDD.md).

## Budowanie

Wymagania: JDK 17+, Android SDK z platformą 36 (`sdk.dir` w `local.properties` albo `ANDROID_HOME`).

```bash
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease      # podpisany kluczem debug (do testów)
./gradlew :app:testDebugUnitTest    # symulacje balansu AI vs AI + screenshoty (build/screenshots)
```

## Struktura

- `game/`: symulacja (czysty Kotlin, deterministyczna), dane balansu, AI, renderer bitwy
- `engine/`: pętla gry na SurfaceView, UI, jakość grafiki, teksty w 10 językach (engine/L.kt)
- `meta/`: profil gracza, badania, rankingi
- `screens/`: menu, kampania, badania, rankingi, ustawienia, bitwa

## Samouczek

Pierwsze uruchomienie etapu 1 kampanii prowadzi interaktywny samouczek
(`screens/TutorialOverlay.kt`): 9 kroków sterowanych stanem bitwy (szkolenie, złoto i teren,
strzelcy, zabójstwa i XP, Awans, doktryna, wieże, ostrzał, kamera), z podświetleniem elementu,
pauzą/spowolnieniem symulacji, limitem czasu w każdym kroku i przyciskiem „Pomiń samouczek”.
Bitwa samouczka jest łagodniejsza (`BattleConfig.tutorial`: AI ×0,8, startuje po 25 s, połowa
złota na start, gracz +50% XP, etap dopuszcza jeden awans epoki).
Ukończenie lub pominięcie ustawia `Profile.tutorialDone`. Powtórka: `profile.replayTutorial()`
(zeruje flagę i zapisuje profil), np. pod przyszły przycisk w Ustawieniach.
