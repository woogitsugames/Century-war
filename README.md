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
