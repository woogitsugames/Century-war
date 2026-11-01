# Century War

Strategia na Androida: wojna na jednym pasie przez 9 epok, od plemion do ery orbitalnej.
Krótkie bitwy, długa metaprogresja (kampania 36 etapów, badania, oblężenie bez końca,
wyzwanie dnia, rankingi). Projekt i zawartość: [docs/GDD.md](docs/GDD.md).

Między epoką brązu a feudalizmem znajduje się **epoka żelaza**: czterech nowych wojowników,
fort z palisadą, balista murowa i salwa oszczepów. Instrukcja rozbudowy o kolejne epoki:
[docs/ADDING_ERA.md](docs/ADDING_ERA.md).

## Budowanie

Wymagania: JDK 17+, Android SDK z platformą 36 (`sdk.dir` w `local.properties` albo `ANDROID_HOME`).

```bash
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease      # wymaga klucza upload i pełnej checklisty; bez podpisu błąd
./gradlew :app:testDebugUnitTest    # symulacje balansu AI vs AI + screenshoty (build/screenshots)
./gradlew fastTest                 # szybkie testy app/sim/server, bez ciężkich raportów
./gradlew screenshotTest           # zrzuty i testy grafiki (Robolectric)
BALANCE_RUN=true ./gradlew balanceTest # długie testy balansu
```

Pełne zasady podziału testów, CI i pomiarów: [docs/TEST_SPEED.md](docs/TEST_SPEED.md).
## Regresja wizualna (zlecenie 100)

Zestaw 15 kluczowych stanów UI w dwóch formatach: telefon 1600×720 (720p) i rozłożony
Fold 2184×1968. Łącznie **30 wersjonowanych PNG** w
`app/src/test/resources/screens/`. Zrzuty bieżące: `app/build/screenshots/`.

```bash
# Sprawdzenie wzorców (odchylenia kończą się błędem testu):
./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest.regression100*' \
  --tests '*ScreenshotGoldenTest*' --console=plain --no-configuration-cache

# Świadoma zmiana wzorców, dopiero po zatwierdzeniu nowego wyglądu:
./gradlew :app:updateScreenshots -PscreenReason="Zatwierdzona zmiana układu HUD" \
  --console=plain --no-configuration-cache
git diff -- app/src/test/resources/screens/
git add app/src/test/resources/screens/
```

Zwykłe testy **nie tworzą** brakujących wzorców i **nigdy ich nie aktualizują**.
`updateScreenshots` wymaga uzasadnienia (minimum 8 znaków), odtwarza oba urządzenia,
kontroluje komplet 30 plików i dopisuje uzasadnienie do
`app/src/test/resources/screens/UPDATES.md`. Pierwszy zestaw powstaje na osobnej
gałęzi z zadania 100 w GitHub Actions i jest objęty tym samym przeglądem zmian.

Porównanie dopuszcza odchylenie do **20 jednostek na kanał RGBA** i najwyżej **0,5%**
zmienionych pikseli poza maską. Maska obejmuje środkowy pas animowanego pola bitwy
(`battle_hud`, 22–68% wysokości), **nie zasłania górnego paska ani dolnych kontrolek**.
Różne wymiary obrazu zawsze kończą się błędem. Przy regresji test zapisuje
`app/build/screenshots/diffs/<nazwa>.diff.png` (czerwony – zmiana, granatowy –
maska, ciemny – zgodność) oraz `<nazwa>.txt` z liczbami; w CI te pliki trafiają
do artefaktów. Zmiany wzorców wymagają obejrzenia PNG, nie tylko zielonych testów.

Wynik rasteryzacji zależy od środowiska: wzorce generować i porównywać na
ustalonej konfiguracji CI (Ubuntu 24.04, JDK 17, Robolectric SDK 35). Nie
nadpisywać wzorców na innym systemie bez świadomego przeglądu różnic.

## Struktura

- `game/`: symulacja (czysty Kotlin, deterministyczna), dane balansu, AI, renderer bitwy
- `engine/`: pętla gry na SurfaceView, UI, jakość grafiki, teksty w 10 językach (engine/L.kt)
- `meta/`: profil gracza, badania, rankingi
- `screens/`: menu, kampania, badania, rankingi, ustawienia, bitwa

## Samouczek

Pierwsze uruchomienie etapu 1 kampanii prowadzi interaktywny samouczek
(`screens/TutorialOverlay.kt`): 12 kroków sterowanych stanem bitwy (szkolenie, złoto i teren,
strzelcy, zabójstwa i XP, Awans, doktryna, wieże, Zbrojownia, postawa, Zryw,
celowany ostrzał i kamera), z podświetleniem elementu,
pauzą/spowolnieniem symulacji, limitem czasu w każdym kroku i przyciskiem „Pomiń samouczek”.
Bitwa samouczka jest łagodniejsza (`BattleConfig.tutorial`: AI ×0,8, startuje po 25 s, połowa
złota na start, gracz +50% XP, etap dopuszcza jeden awans epoki).
Ukończenie lub pominięcie ustawia `Profile.tutorialDone`. Powtórka: **Ustawienia → Podpowiedzi i samouczek → Powtórz samouczek**.
Po potwierdzeniu samouczek rozpocznie się przy kolejnym uruchomieniu etapu 1 kampanii.

Podpowiedzi kontekstowe: 15 jednorazowych dymków podczas bitew (w tym warianty epoki
przy wyborze i pogoda przy pierwszych zmiennych warunkach) i 4 przy pierwszych
wejściach do Badań, Misji, Endless i wyzwania dnia. Dymki nie zatrzymują symulacji,
po 8 s znikają automatycznie, a między wskazówkami bitwy jest 20 s przerwy.
W tym samym menu można je wyłączyć lub wyświetlić ponownie. Wybory kart,
pauza i samouczek wyłączają kontekstowe wskazówki.
