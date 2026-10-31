# Prompt przekazania: Century War

Skopiuj całość poniżej do nowej sesji. Szczegóły stanu projektu: `docs/HANDOFF.md`.

---

Przejmujesz rolę koordynatora projektu **Century War**, oryginalnej strategii na Androida (Kotlin, SDK 36, minSdk 26, bez bibliotek, grafika rysowana kodem). Repo: `woogitsugames/century-war`. Główna gałąź robocza: **`claude/bold-sagan-vwz5gm`**. Na niej pracujesz i do niej scalasz.

Najpierw przeczytaj: `README.md`, `docs/GDD.md`, `docs/HANDOFF.md` (stan, ryzyka, komendy budowania i testów), `docs/tasks/README.md` (zlecenia dla wykonawcy GPT, gałęzie `gpt/NN-*`). Odpowiadaj po polsku, zwięźle.

## Zadania po kolei
1. Scal do `claude/bold-sagan-vwz5gm` gałęzie `claude/int-meta`, `claude/int-infra` i `claude/int-audio-tests`.
   - Wyszły ze starszego commita `6114244`, więc będą konflikty w `L.kt`, ekranach, `MainActivity`, `Profile` i `BattleScreen`.
   - W `int-infra` serwer (gpt/08), a w `int-audio-tests` muzyka (gpt/09) to WIP bez testów: uruchom testy aplikacji i `./gradlew :server:test`, potem napraw, co trzeba.
   - Dodatkowo scal `origin/gpt/14-tests-robustness`.
   - Po każdym scaleniu zbuduj i uruchom testy.
   - Teksty muszą mieć 10 języków (test `LTest`). Nowy stan bitwy musi trafić do `BattleSnapshot` (podbij `VERSION`).
2. Scal po kolei `origin/gpt/16-unit-upgrades` → `17-new-era` → `18-era-branches` i na końcu `04-balance`. Pilnuj:
   - migracji gwiazdek kampanii po wstawieniu epoki,
   - niezmienionego wyzwania dnia w bieżącym dniu UTC,
   - determinizmu i powtórek (`ReplayTest`),
   - stylu grafiki (`ArtGalleryTest`).
   Słabe 18 możesz odłożyć i to zgłosić.
3. Sprawdź `git ls-remote origin 'refs/heads/gpt/*'` i scal ewentualne nowe commity GPT.
4. Każdą gałąź przeglądaj krytycznie: determinizm, zero alokacji na klatkę, działanie od 720p po rozłożonego Folda, brak nawiązań do innych gier i brak emoji w UI.

## Komendy
```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties   # SDK: platforms;android-36, build-tools;36.0.0
./gradlew :app:assembleDebug --console=plain
./gradlew :app:testDebugUnitTest --tests '*BattleSimTest*' --tests '*BattleRulesTest*' --tests '*Snapshot*' --tests '*LTest*' --tests '*Replay*' --tests '*Counter*' --tests '*Endless*' --tests '*AiPersonality*' --console=plain
./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest.menus*' --console=plain --no-configuration-cache   # zrzuty -> app/build/screenshots
```

## Uwagi
- Emulatora nie ma, wygląd sprawdzaj zrzutami z `ScreenshotTest`.
- Przy błędzie 429 z Maven Central pobieraj artefakty z `https://maven-central.storage-download.googleapis.com/maven2` do `~/.m2/repository`.
- Commity po polsku, z liniami atrybucji sesji. Nie twórz PR bez prośby użytkownika.
