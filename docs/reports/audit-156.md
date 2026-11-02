# Zlecenie 156 — audyt przed premierą

**Data:** 2 listopada 2026 r.

**Repozytorium:** `woogitsugames/century-war`.

**Badany commit:** [`85e90b636a6c16b89f0be6fa89b03fd8fb079699`](https://github.com/woogitsugames/century-war/tree/85e90b636a6c16b89f0be6fa89b03fd8fb079699), pobrany z `origin/claude/bold-sagan-vwz5gm` przed utworzeniem `gpt/156-prerelease-audit`.

**Zakres zmiany:** wyłącznie ten raport. Nie zmieniono kodu, balansu, formatów zapisu, konfiguracji wydania ani pozostałej dokumentacji.

## 1. Podsumowanie dla właściciela

Budowanie pakietu debug zakończyło się powodzeniem, ale badanego stanu nie rekomenduję jeszcze do testów zamkniętych w Google Play ani do premiery. Dwie odtworzone usterki powodują odrzucenie prawidłowo zapisanej bitwy: ulepszenie wieży w drugim lub dalszym slocie oraz aktywna fala `TWIN_RAID` w trybie Endless. Serwer potrafi przyjąć do aktualnego rankingu powtórkę rozgrywaną według starszych zasad, a zgłoszenie wyniku rozpoczęte przed usunięciem danych może te dane ponownie utworzyć. Przegląd cyklu życia i zapisu wykazał dodatkowe zagrożenia dla postępu oraz uruchamianie pętli gry po pauzie. Opcja ograniczania błysków i obsługa małych przycisków wież wymagają poprawy przed przekazaniem gry szerszej grupie graczy. Automatyczne kontrole wydania mają rzeczywiste błędy testów i konfiguracji, więc sam udany build debug nie daje zielonego światła do publikacji. Brakuje również kompletu danych wydawcy, produkcyjnej konfiguracji usług i zatwierdzonej polityki zgodnej z obecną implementacją. Rdzeń symulacji, ograniczenia wejść serwera i zabezpieczenia plików są w wielu miejscach dobrze przygotowane, jednak wymagają opisanych poniżej uzupełnień oraz prób na fizycznych urządzeniach. Kontrolowane testy techniczne mogą trwać już teraz; wejście na zamkniętą ścieżkę sklepu powinno nastąpić po naprawie problemów utraty postępu, zamknięciu konfiguracji i uzyskaniu pełnego zielonego przebiegu testów.

## 2. Metoda i granice dowodu

Przeczytano `README.md`, `docs/GDD.md`, `docs/HANDOFF.md` i dokument stanu, do którego odsyła HANDOFF, a następnie kod trzech modułów, testy, manifesty, reguły backupu/R8 i workflow CI. Audyt obejmował przepływy komend, serializację, odtwarzanie, serwerową weryfikację wyników, zapis profilu i historii, import/eksport, cykl życia, audio, renderowanie oraz dokumentację sklepu. To audyt źródeł z uruchomionymi testami i reprodukcjami, nie test penetracyjny wdrożonej usługi ani certyfikacja wszystkich urządzeń.

Oznaczenia w tabeli:

- **R** — problem odtworzony uruchomieniem kodu; szczegóły i zakres reprodukcji w dalszych sekcjach.
- **K** — stwierdzona sprzeczność lub brak w kodzie/konfiguracji. Nie oznacza wykonanego testu na telefonie.
- **Ryzyko** — ścieżka awarii wynika z kolejności operacji, ale nie wykonano pełnego odtworzenia, np. zabicia procesu w odpowiedniej chwili.

Numery linii odnoszą się do **badanego SHA**, nie do późniejszych commitów. Dla czytelności `A/` oznacza `app/src/main/java/com/centurywar/`, `S/` — `sim/src/main/kotlin/com/centurywar/game/`, `B/` — `server/src/main/kotlin/com/centurywar/server/`, a `T/` — `buildSrc/src/`. Są to dokładne prefiksy ścieżek, nie nazwy nowych katalogów.

Podczas audytu baza przesunęła się do `a57056971b7a1778c35d556fa856422212fe3b5b`: doszły poprawki ekranów i tłumaczeń. Nie podmieniano źródeł w trakcie pomiarów. Wyniki poniżej dotyczą zamrożonego SHA; ocenę różnic i ograniczenia aktualności podano w końcowej części raportu.

## 3. Tabela znalezisk

Łącznie **27 ustaleń**: 3 blokujące premierę, 13 wysokiego, 7 średniego i 4 niskiego priorytetu.

| ID | Priorytet | Obszar | Plik:linia | Opis i dowód | Jak odtworzyć | Proponowana poprawka |
|---|---|---|---|---|---|---|
| A156-01 | **Blokuje premierę** | Zapis / powtórki | `S/BattleController.kt:250–262`; `S/BattleSnapshot.kt:273` | **R:** czytnik komend snapshotu pomija `TURRET_TIER` w komendach z argumentem slotu. Legalny argument 1–3 jest odrzucany; dotyczy wznowienia i cofania powtórek. | Przez kontroler kupić i ulepszyć wieżę w slocie 1, 2 lub 3; wykonać encode/decode snapshotu. Odczyt zwraca `null`; slot 0 działa. | Wspólna walidacja argumentów Replay/snapshotu; test wszystkich czterech slotów z kontrolerem i `ReplayPlayer.seek`. |
| A156-02 | **Blokuje premierę** | Endless / zapis | `S/Battle.kt:1181–1184`; `S/BattleSnapshot.kt:284–285` | **R:** gra tworzy jednostkę `TWIN_RAID` z `waveSpeedMul=1.15`, a czytnik dopuszcza tylko 1 i 0,85. | Endless, seed 2, difficulty 1; 750 zwykłych kroków. Pierwsza fala ma aktywną jednostkę z mnożnikiem 1,15; snapshot nie daje się odczytać. | Ujednolicić zbiór legalnych mnożników; roundtrip każdego tematu fali po faktycznym pojawieniu się jednostek. |
| A156-03 | **Blokuje premierę** | Przygotowanie publikacji | `docs/store/OWNER_INPUT.md:7–11`; `docs/store/data_safety.md:81–88`; `S/Modes.kt:404–407`; `app/build.gradle.kts:75–83` | **K:** dane właściciela i ID usług są zastępcze, dokumenty mają nierozstrzygnięte decyzje, `RELEASE_DAY=21153` oznacza 2027-12-01. Brak w tym środowisku klucza upload. To jawna niegotowość konfiguracji, nie dowód braku sekretów na koncie właściciela. | Sprawdzić wymienione pliki; zwykłe `:app:assembleRelease :app:bundleRelease` zatrzymuje bramka podpisu. | Właściciel uzupełnia dane i konfigurację, publikuje politykę; koordynator ustala dzień wydania i wersję. Zbudować właściwy podpisany AAB bez obejść smoke CI. |
| A156-04 | **Wysoki** | Integralność rankingu | `B/ReplayVerifier.kt:31–45`; `S/Replay.kt:65,111–112,193` | **R:** bieżące `gameVersion=0.8.2` nie wymusza bieżących zasad Replay. Format 5 wyłącza dowódców i stopnie wież, ale trafia do tego samego rankingu. | Prawdziwy verifier przyjmuje format 5, seed 42, wynik 273 po 10000 krokach. Te same komendy w formacie 7 odrzuca jako nielegalne. | Powiązać wersję zasad rankingu z dozwolonymi formatami; stare powtórki zachować lokalnie albo w osobnych tablicach. |
| A156-05 | **Wysoki** | Usuwanie danych / serwer | `B/Api.kt:195–220,235–237`; `B/SqliteStore.kt:196–216,357–365` | **R:** wynik weryfikowany podczas usuwania gracza może odtworzyć nick i wynik. Końcowy zapis dopuszcza nieistniejącego właściciela. | Zatrzymać poprawny wynik po weryfikacji, usunąć gracza, dokończyć zapis. W SQLite liczba wyników zmienia się z 0 na 1. Reprodukcja na store/verifier, bez HTTP. | Generacja operacji lub serializacja na gracza, unieważniana przy usunięciu i sprawdzana atomowo przy zapisie; test równoległych endpointów. |
| A156-06 | **Wysoki** | Profil / wątki | `A/meta/Profile.kt:648–655,1022–1025` | **R:** `load()` ignoruje niepowodzenie `awaitWrites(2000)`. Nowa instancja może odczytać stary plik i późniejszym zapisem nadpisać nowszy stan poprzedniej instancji. | Zablokować writer, ustawić w kolejce stan 500 medali, wczytać starą wartość 10 przez nowy obiekt i zapisać 15. | Po timeout ponowić odczyt na I/O; nie udostępniać starego stanu do modyfikacji. Rozważyć wspólną generację stanu dla ścieżki. |
| A156-07 | **Wysoki** | Cykl życia / wątki | `A/engine/GameView.kt:75–125`; `A/MainActivity.kt:142–156,390–391` | **R:** `surfaceChanged` uruchamia pętlę bez warunku wznowionej Activity. Dodatkowe **ryzyko:** timeout `join(1500)` i `finally` starego wątku mogą wyzerować `loopActive` nowego. | Sekwencja `surfaceCreated → onPause → surfaceChanged` bez `onResume`; obserwować start `game-loop`. Wariant z timeout wymaga osobnej próby urządzenia. | Wspólna bramka `activityResumed && hasSurface && validSize`; stan wątku aktualizować tylko dla jego aktualnej tożsamości; testy callbacków i opóźnionej klatki. |
| A156-08 | **Wysoki** | Dostępność / błyski | `A/screens/BattleScreen.kt:1439`; `A/game/BattleRenderer.kt:59–64,297–298,1349–1359` | **R:** ogranicznik „mniej błysków” odmierza czas symulacji, przyspieszany do ×5. Nocna aureola trafionej bazy omija ogranicznik. | Włączyć opcję, porównać ciągłe rozbłyski przy ×1 i ×5. Numeryczna reprodukcja algorytmu Float32 dopuszcza odpowiednio 29 i 120 zdarzeń w 10 s przy 60 FPS; to nie pomiar migania obrazu. | Osobny zegar rzeczywisty i wspólna bramka wszystkich rozbłysków; test ×1/×3/×5 oraz nocnych trafień baz. |
| A156-09 | **Wysoki** | Sterowanie / małe ekrany | `A/screens/BattleUpgradePanel.kt:67–126`; `A/screens/BattleBottomHud.kt:59–70`; `A/engine/Screen.kt:108–145` | **R:** przy 600×360 dp, czterech wieżach i dowódcy przyciski panelu mają 10,5 dp wysokości. Rozszerzone o 18,75 dp cele dotyku nakładają się; środek slotu 2 trafia też w obsługiwany wcześniej slot 0. | Widok 1200×720 px, density 2, cztery puste sloty i dostateczne złoto; dotknąć środka kupna trzeciej wieży. | Osobny modal, przewijanie lub paginacja przy małej wysokości; testować rzeczywiste hitboxy i komendy, nie tylko wizualne `RectF`. |
| A156-10 | **Wysoki** | Trwałość wyniku bitwy | `A/screens/BattleScreen.kt:1083,1175–1223`; `S/BattleSnapshot.kt:450–483`; `A/meta/Profile.kt:598–602` | **Ryzyko:** kasowanie snapshotu i zapis nagrody korzystają z niezależnych executorów; nie ma trwałej, idempotentnej transakcji rozliczenia. | Wymaga fault-injection: zabić proces po skasowaniu snapshotu, przed utrwaleniem profilu; sprawdzić też odwrotną kolejność i ponowne rozliczenie starej migawki. Nie wykonano kill-testu. | Trwały identyfikator rozliczenia w profilu/journalu; idempotentne naliczanie; skasowanie snapshotu po potwierdzeniu zapisu. |
| A156-11 | **Wysoki** | CI / testy konfiguracji | `T/test/kotlin/com/centurywar/build/PlayGamesIdsGeneratorTest.kt:76`; `T/test/kotlin/com/centurywar/build/ReleaseChecklistTest.kt:121–165` | **R:** 4 z 20 testów buildSrc nie przechodzą. Liczba wymaganych kluczy to 71, test oczekuje 70; „poprawny” fixture właściciela zawiera `example`, które walidator słusznie odrzuca. | `./gradlew -p buildSrc test --no-configuration-cache`. | Uaktualnić dane testowe i oczekiwania do kontraktu; zachować odrzucanie zastępczych danych w produkcji. |
| A156-12 | **Wysoki** | CI / graf zadań | `app/build.gradle.kts:246–249`; `.github/workflows/android.yml:164–168` | **R lokalnie i w CI:** deklarowane ograniczenie `:app:test` do debug nie działa; graf zawiera `releaseChecklist` i testy release. Kontrola CI przerywa szybkie testy przed wykonaniem. | `./gradlew :app:test --dry-run`; wyszukać zadania release. | Ustawić zależności po utworzeniu wariantów AGP albo używać jednoznacznego zadania agregującego debug; zachować test grafu. |
| A156-13 | **Wysoki** | Balans / bramka regresji | `app/src/test/java/com/centurywar/game/BalanceReport.kt:473` | **R lokalnie i w CI:** `variantReport` przekracza limit różnicy 10 punktów procentowych: STEAM A=165, B=235, bez remisów w 400 pojedynkach, różnica 17,50 pp. Dodatkowa reprodukcja SILICON: 178/222, różnica 11 pp. To odrębna awaria od testów konfiguracji. | Włączyć `BALANCE_RUN=true` i wykonać `balanceTest`; liczby z lokalnego przebiegu podano w sekcji wyników. | Zbadać różnice wariantów i świadomie ustalić balans; nie usuwać asercji tylko po to, by uzyskać zielony CI. |
| A156-14 | **Wysoki** | Pipeline wydania | `.github/workflows/android.yml:434–441`; `app/build.gradle.kts:37–49`; `T/main/kotlin/com/centurywar/build/ReleaseChecklist.kt:68–75` | **K:** job wydania przekazuje tylko dane podpisu. Nie przekazuje `CW_SERVER_URL`, `CW_HMAC_KEY`, `CW_PRIVACY_URL`, których wymaga checklista; samo istnienie sekretu GitHub nie ustawia zmiennej procesu. | Prześledzić `env` kroku wydania i źródła `scoreSetting`; czysty checkout nie ma lokalnego pliku z tymi wartościami. | Jawnie podłączyć wymagane secrets/vars do kroku builda oraz sprawdzić ich obecność bez wypisywania wartości. |
| A156-15 | **Wysoki** | Bramka wydania | `.github/workflows/android.yml:27–29,396–401` | **K:** build podpisanego wydania na tagu nie zależy od testów; pozostałe główne joby pomijają tagi. Tag wskazujący nieprzetestowany commit może przejść samą kompilację. | Prześledzić warunki `if` i brak `needs`/kontroli statusu badanego SHA w jobie release. Nie tworzono testowego tagu. | Uruchomić wymagane testy dla tego samego SHA przed pakowaniem albo zweryfikować komplet statusów przypiętego commita. |
| A156-16 | **Wysoki** | Prywatność / dokumenty | `docs/store/privacy_policy_pl.md:67,98`; odpowiedniki EN; `docs/store/data_safety.md:27,52,59,83`; `A/meta/PlayGamesLeaderboards.kt:120,180–187` | **K:** polityka twierdzi, że osiągnięcia PGS nie są połączone, choć adapter je wysyła po konfiguracji. Opisuje też backup tylko `profile.json`, mimo dołączenia `history.json`. Bezwarunkowa obietnica E2E pomija Androida 8/8.1. | Porównać dokumenty z adapterem, regułami backupu i minSdk 26; zakres platformy potwierdza dokumentacja Androida [Ź3]. | Zaktualizować oba języki i Data safety do rzeczywistego pakietu; rozstrzygnąć backup na API 26–27 i opisać faktyczne gwarancje. |
| A156-17 | **Średni** | Zimny start | `A/MainActivity.kt:80,119–156,432–435` | **R:** starsza ścieżka Wstecz odczytuje `lateinit game` przed końcem asynchronicznego startu. | API 26–32: Wstecz podczas ekranu rozruchowego; diagnostyka zatrzymuje kolejkę UI przed `finishStartup`. | Obsłużyć Wstecz przed inicjalizacją bez odczytu `game`; test starszych API z opóźnionym startup I/O. |
| A156-18 | **Średni** | Reset postępu | `A/screens/SettingsScreen.kt:369`; `A/screens/MenuScreen.kt:432–434`; `A/MainActivity.kt:128–129,186–195` | **R:** reset profilu i historii pozostawia lokalny `battle.snap`. Schowanie oferty wznowienia nie usuwa pliku. | Zapisać etap 36, schować ofertę Wstecz, zresetować postęp, ponownie uruchomić grę; stara bitwa pozostaje do wznowienia. | Unieważnić snapshot, oczekujące zapisy i `resumeOffer` w tej samej procedurze resetu. |
| A156-19 | **Średni** | PGS / ustawienia | `A/meta/PlayGamesLeaderboards.kt:389–402`; `A/MainActivity.kt:703–713` | **K:** przyjęcie profilu z PGS stosuje język, jakość i FPS, lecz pomija pełne odświeżenie layoutu, dostępności, głośności i przypomnień obecne przy imporcie lokalnym. | Profil z chmury wygrywa konflikt, ma inną paletę, tekst 130% i muzykę 0; wartości profilu i cache runtime mogą się różnić. Nie testowano na koncie PGS. | Jedna procedura zastosowania wczytanego profilu dla PGS i importu pliku; test wszystkich ustawień. |
| A156-20 | **Średni** | Fold / konfiguracja | `A/MainActivity.kt:164,903–911`; `A/engine/Game.kt:385–387` | **K:** `game.density` jest przypisywane tylko przy starcie, choć Activity samodzielnie obsługuje zmianę density. | Zmiana rozmiaru wyświetlania Androida lub przejście między ekranami o różnych dpi bez odtworzenia Activity; layout używa starej gęstości. Nie wykonano próby fizycznego Folda. | Aktualizować density wraz z rozmiarem i layoutem na wątku gry; test zmiany konfiguracji bez restartu Activity. |
| A156-21 | **Średni** | TalkBack / Switch Access | `A/engine/GameView.kt:25,175–193`; `A/engine/Ui.kt:13–48`; `A/engine/Screen.kt` | **K:** kontrolki są rysowane na jednym SurfaceView; brak wirtualnych węzłów, nazw i akcji dostępności. Same palety i duży tekst tego nie zapewniają. | Przeszukanie kodu nie znajduje `AccessibilityNodeProvider`, `AccessibilityNodeInfo`, akcji dostępności ani obsługi hover. Test fizycznego TalkBack pozostaje do wykonania. | Platformowy `AccessibilityNodeProvider`, początkowo menu/ustawienia/pauza, potem HUD; akcje kierować na wątek gry. |
| A156-22 | **Niski** | Muzyka / obsługa błędów | `A/engine/Music.kt:82–96,107–157` | **K:** po nieudanej inicjalizacji lub zapisie AudioTrack worker kończy się, lecz pole `worker` pozostaje niepuste. Dodatkowo `start()` następuje przed przypisaniem pola. | Wymusić błąd `getMinBufferSize`, niegotowy AudioTrack lub ujemny wynik `write`; ponowić dodatnią głośność/resume. Nie wykonywano takiego błędu urządzenia. | Opublikować worker przed startem, zerować w `finally` po sprawdzeniu tożsamości; kontrolowane ponawianie i test błędów AudioTrack. |
| A156-23 | **Niski** | Dokumentacja / oryginalność | `docs/GDD.md:5–7` | **K:** opis projektu nadal zawiera porównanie do innej gry, sprzeczne z zasadą braku takich odwołań w materiałach. | Odczytać wstęp GDD. Nazwy porównywanej gry celowo nie powielono tutaj. | Opisać założenia własnymi cechami Century War; osobne zlecenie dokumentacyjne. |
| A156-24 | **Niski** | Tekst / wąskie okno bitwy | `A/engine/Ui.kt:593–598,675–690` | **R:** cena jednostki z ikoną nie jest dopasowana do szerokości. W epoce 8 tekst ceny 14748 wraz z monetą ma 51,975 dp przy przycisku 48 dp i wystaje poza obrys. | Okno 720×1280 px, density 2, ostatnia epoka, tekst 130% (w bitwie 115%); pomiar rzeczywistego Paint i obejrzany PNG. To próba układu okna, nie pomiar urządzenia. | Przekazać maksymalną szerokość do `subLine` i dopasować cenę z monetą; zrzut/test ostatnich epok przy maksymalnym tekście. |
| A156-25 | **Niski** | Pokrycie zrzutów | `app/src/test/java/com/centurywar/screens/PrereleasePolishTest.kt:281–283`; `A/screens/AccessibilityScreen.kt:49–85` | **K + obejrzany PNG:** kadr nazwany „dostępność dół” nadal pokazuje górę. Test zmienia `scroll` refleksją bez przeliczenia layoutu; zwykłe przewijanie aplikacji wywołuje layout poprawnie. | Wygenerować `pre10_phone720_de_29_dostepnosc_dol.png` i porównać zawartość z nazwą; dolne przełączniki nie są potwierdzone tym kadrem. | Przewijać publicznym `onDrag` albo przeliczyć layout po zmianie pola; sprawdzić obecność dolnych kontrolek w zrzucie. |
| A156-26 | **Średni** | Wykonywanie testów / pamięć | `app/build.gradle.kts:202–212`; `app/src/test/java/com/centurywar/screens/PrereleasePolishTest.kt:378–388`; `gradle/test-categories.properties` | **R w środowisku 8 GiB:** `fastTest` i `screenshotTest` z `-PtestForks=1` zakończyły procesy kodem 137; licznik cgroup potwierdził OOM kill. „Fast” zawiera m.in. obszerną trasę zrzutów, a proces jest wymieniany dopiero co 40 klas. To nie pomiar pamięci gry na telefonie. | Uruchomić pełne kategorie w limicie 8 GiB, z jednym workerem/forkiem. Przerwania po 204 s i 310 s; brak kompletnego XML nie oznacza zera wykonanych testów. | Oddzielić ciężkie trasy graficzne od fast i dobrać częstsze izolowanie procesów oraz heap; wyniki ponowienia ze zmianą wyłącznie środowiska w sekcji 5. |
| A156-27 | **Średni** | CI / artefakty zrzutów | `.github/workflows/android.yml:236–257`; `app/build.gradle.kts:202–212` | **R w CI + K:** `testDebugUnitTest FROM-CACHE` przywraca raporty, lecz główny katalog PNG nie jest zadeklarowany jako output testu. Pobrany artefakt ma 36 plików i 6 PNG wyłącznie w `test-results/.../l10n88`; brak `app/build/screenshots`. | Otworzyć log joba 125460433805 i artefakt 13167317808 badanego SHA. Zielony wynik z cache nie dostarcza pełnego zestawu obrazów do przeglądu. | Zadeklarować główny katalog PNG jako output i zweryfikować odtworzenie z cache albo wyodrębnić jawne generowanie obrazów; kontrolować obecność oczekiwanych PNG przy publikacji artefaktu. |

## 4. Najważniejsze reprodukcje

### 4.1. Snapshoty: utrata wznowienia oraz cofanie powtórki

A156-01 odtworzono publicznymi metodami `BattleController`, bez zmiany pól gry: dla każdego slotu utworzono osobną potyczkę, seed 42, siła 1, `Bonuses(startGold=200f, turretSlots=4)`. Oba rozkazy `buyTurret(slot)` i `upgradeTurretTier(slot)` zwróciły `true`; po nich wykonano encode/decode snapshotu. Slot 0 odczytał się poprawnie, sloty 1–3 nie. Osobna powtórka z ulepszeniem slotu 1 przeszła `Replay.verify`, ale cofnięcie z kroku 2100 do 1900 nie odtworzyło klatki kluczowej.

```text
TURRET slot=0 tier=2 restore=true
TURRET slot=1 tier=2 restore=false
TURRET slot=2 tier=2 restore=false
TURRET slot=3 tier=2 restore=false
REPLAY_SEEK before=2100 seek1900=false broken=true
```

`Replay.kt:204–205` poprawnie dopuszcza argument slotu dla `TURRET_TIER`, natomiast `BattleController.kt:250–262` używa innej listy i wymaga dla tej komendy argumentu 0. `BattleSnapshot.decode` zamienia błąd walidacji na `null`. `SnapshotStore.loadBattle` (`BattleSnapshot.kt:523–546`) usuwa snapshot uznany za nieprawidłowy. Nie wykonano osobnego testu ubicia procesu Androida; odtworzono odrzucenie zapisu, a kasowanie pliku wynika z przeczytanej ścieżki ładowania.

Istniejący `TurretTierTest.kt:7–24` sprawdza stan wieży i snapshot bez logu kontrolera; test powtórki w liniach 67–79 używa slotu 0. Brakuje kombinacji **kontroler + ulepszenie slotu >0 + odczyt snapshotu**, dlatego obecne zielone testy nie wykluczają tej usterki.

A156-02 odtworzono niezależnie: `BattleConfig(Mode.ENDLESS, 2L, 1f)` i zwykłe `step()`. Przed pojawieniem się jednostki snapshot się odczytał. W kroku 750, fala 1, pojawiła się jedna aktywna jednostka `TWIN_RAID` z legalnym `waveSpeedMul=1.15f`; wtedy odczyt zwrócił `null`. Nie ustawiano ręcznie tematu fali ani mnożnika.

```text
TWIN_RAID seed=2 wave=1 step=750 active=1 speed=1.15 restore=false
```

`BattleSnapshotTest.kt:248–275` w `sim/src/test/kotlin/com/centurywar/game/` zapisuje falę w kolejce przed pojawieniem się jej jednostek; późniejszy postęp nie kończy się decode jednostki z mnożnikiem 1,15. `EndlessCards108Test.kt:140–156` porównuje zakodowane stany, lecz nie sprawdza ich odkodowania w tej sytuacji. Należy testować roundtrip po faktycznym pojawieniu się wszystkich specjalnych typów fali i porównać dalszy przebieg wznowionej bitwy.

### 4.2. Ranking przyjmuje starsze zasady przy aktualnej wersji gry

A156-04 sprawdzono na rzeczywistym `ReplayVerifier`. Potyczka miała seed 42, difficulty 1 i wszystkie badania zero. Przebieg utworzono konstruktorem `Battle(cfg, turretTiersEnabled=false, commanderEnabled=false)`, z kontrolerem i `playerAi=Ai(old, 0)`. Po maksymalnie 10000 kroków poddano trwającą bitwę przez kontroler. Powtórkę oznaczono formatem 5, a zgłoszenie miało `gameVersion=SimVersion.PROTOCOL`, czyli 0.8.2, poprawny testowy UUID, nick `Audit156` i rzeczywisty wynik. Wywołano verifier z czasem `1793577600000` ms. Następnie te same komendy przekazano jako aktualny format 7.

```text
SERVER_ACCEPTED protocol=0.8.2 format=5 score=273 steps=10000
SERVER_CURRENT_FORMAT rejected=illegal_replay; cause=Niedozwolona komenda nr 43 w kroku 8084
```

To błąd izolacji zasad rankingu, nie dowód możliwości wpisania dowolnej liczby punktów. Serwer sprawdza zgodność wyniku z replayem, lecz reguły zależą także od formatu podanego przez klienta. `Replay.kt:111–112` włącza stopnie wież od v6 i dowódców od v7; akceptowanie v5 przy bieżącym protokole zmienia przebieg symulacji. Lokalna kompatybilność oglądania starych powtórek może pozostać. Brakuje negatywnego testu serwera dla połączenia bieżącego `gameVersion` ze starszym formatem zmieniającym zasady.

Nie wysyłano zgłoszenia do produkcyjnego rankingu ani nie wykonywano żądania HTTP z HMAC. Potwierdzono przyjęcie przez rzeczywisty weryfikator wywoływany przez endpoint.

### 4.3. Zgłoszenie w toku przywraca usunięte dane

A156-05 odtworzono na `SqliteStore("jdbc:sqlite::memory:")` z rzeczywistym sqlite-jdbc i `ReplayVerifier`, używając dwóch wątków oraz latchy. Poprawny replay dotyczył Endless, seed 42, badań zero, 120 kroków i poddania przez kontroler, w bieżącym formacie v7. Najpierw zapisano wynik i właściciela. Drugi wątek przeszedł `mayClaim` i weryfikację, po czym czekał przed `save`. Pierwszy wątek usunął gracza przez `deleteOwnedPlayer` i potwierdził brak wyniku oraz właściciela. Zwolnienie latcha pozwoliło starszemu zgłoszeniu dokończyć `save`.

```text
DELETE deleted=true scores=0 owner=false
AFTER_IN_FLIGHT_SCORE inserted=true scores=1 owner=true nick=Audit156
```

Hash własności i UUID były danymi testowymi, nie poświadczeniami użytkownika. Końcowa kontrola `SqliteStore.kt:357–365` dopuszcza brak właściciela (`allowMissing=true`), więc nie odróżnia nowego świadomego zgłoszenia od zakończenia starego. Synchronizacja pojedynczych operacji nie obejmuje sekwencji „własność → weryfikacja → zapis”. `PlayerDeletionTest.kt:40–100` i `IdentityApiTest.kt:105–121` badają usuwanie sekwencyjnie, bez tego przeplotu.

Reprodukcja **nie była żądaniem HTTP** i nie potwierdza odpowiedzi 200 konkretnego wdrożenia. Bezpośrednio potwierdza odtworzenie danych w rzeczywistej bazie po skutecznym usunięciu. Docelowa regresja powinna obejmować również współbieżne endpointy oraz atomowo sprawdzany token/generację operacji.

Pierwotne reprodukcje z sekcji 4.1–4.3 uruchomiono ponownie na rzeczywistym `sim.jar` i klasach serwera z builda Gradle Kotlin 2.2.20. Wyniki były identyczne; ślad ładowania klas potwierdził użycie tych artefaktów. Z wcześniejszej kompilacji pomocniczej Kotlin 2.0.21 pozostały tylko klasy programów diagnostycznych, nie kopie klas produkcyjnych. Kod 0 tych programów oznaczał potwierdzenie usterek, a nie zaliczenie testów poprawnego działania.

### 4.4. Siedem diagnostyk aplikacji

Cztery diagnostyki cyklu życia i zapisu uruchomiono w Robolectric na **API 28**, z `android.app.Application` i `LooperMode.PAUSED`. Trzy diagnostyki UI korzystały z **API 35 i natywnego renderowania**. Źródła dołączono zewnętrznym init Gradle; nie zmieniano kodu gry ani testów w repo. Cały przebieg siedmiu przypadków zakończył się w 43,95 s. Wszystkie asercje potwierdzały **obecność usterek**, więc nie traktujemy ich jako regresji poprawnego zachowania.

- **Profil, A156-06:** zatrzymano writer publicznym callbackiem `onSaved` i latchem. Na dysku było 10 medali, w kolejce czekał stan 500. Nowa instancja po timeout 2000 ms odczytała stare 10 i zakolejkowała 15. Po odblokowaniu writera potwierdzono rzeczywisty końcowy zapis 15 zamiast 500. Kontrolowany callback modeluje opóźnienie I/O; nie mierzy częstości takich opóźnień na telefonie.
- **Pętla, A156-07:** publiczna sekwencja `surfaceCreated → onPause → surfaceChanged`, bez `onResume`, uruchomiła rzeczywisty `game-loop`. Pomocniczy shadow StrictMode obserwował wejście do wątku; nie zastępował bramki uruchamiania. Nie dowodzi to jeszcze zmiany stanu bitwy w tle; wariantu timeout `join` nie odtwarzano.
- **Wstecz, A156-17:** zatrzymano kolejkę UI przed `finishStartup` i wywołano publiczne `onBackPressed`. Uzyskano `UninitializedPropertyAccessException`. Wykonano próbę na API 28; zakres starszej ścieżki API 26–32 wynika z kodu, nie z oddzielnych prób każdego API.
- **Reset, A156-18:** zapisano etap 36, schowano ofertę publicznym `MenuScreen.onBack`, wykonano dwa kliknięcia resetu w ustawieniach. Postęp był zerowy, lecz `battle.snap` nadal odczytywał się jako etap 36. Test nie ubijał prawdziwego procesu Androida; późniejsza oferta wznowienia wynika również z prześledzonego startupu.
- **Wieże, A156-09:** w oknie 1200×720 px, density 2, publiczna obsługa dotknięcia środka trzeciego przycisku kupna zapisała `BUY_TURRET` z argumentem 0. Pomiar wykazał wysokość przycisku 10,5 dp i rozszerzenie celu o 18,75 dp. To potwierdzenie niewłaściwej komendy, nie tylko wizualna ocena PNG.
- **Cena, A156-24:** rzeczywisty `Paint` zmierzył cenę 14748 z monetą: 51,975 dp przy przycisku 48 dp. Okno miało 720×1280 px, density 2, epokę 8 i tekst profilu 130%, ograniczany w bitwie do 115%. Obejrzano również PNG potwierdzający wystawanie.
- **Błyski, A156-08:** rzeczywisty renderer z włączonym ograniczeniem, w 2 s przy 60 FPS i ciągłych impulsach, dopuścił 6 zdarzeń przy ×1 oraz 24 przy ×5. Oddzielna reprodukcja samego algorytmu Float32 w 10 s dała 29/86/120 dla ×1/×3/×5. Są to dopuszczenia przez bramkę, **nie pomiar luminancji obrazu ani ocena medyczna migania**. Nocny efekt trafienia bazy dodatkowo omija tę bramkę w kodzie.

Zachowane komunikaty czterech diagnostyk cyklu życia:

```text
AUDIT156 RESET: postęp=0, lecz można wznowić etap 36
AUDIT156 PROFILE: load po timeout odczytał 10; zapis 500 został nadpisany 15
AUDIT156 SURFACE: potwierdzono start game-loop po onPause bez onResume
AUDIT156 BACK: potwierdzono UninitializedPropertyAccessException przed finishStartup

# cały przebieg siedmiu diagnostyk
BUILD SUCCESSFUL in 43s
37 actionable tasks: 3 executed, 34 up-to-date
```

## 5. Budowanie, testy i wyniki CI

### 5.1. Środowisko i polecenia

Lokalnie: Linux x86_64, limit pamięci środowiska 8 GiB, **Temurin JDK 17.0.21**, Gradle 8.14.3, Kotlin 2.2.20, AGP 8.13.0, zainstalowane SDK platformy 36 i Build Tools 36. AGP dodatkowo pobrał swoją domyślną wersję Build Tools 35. `local.properties`, SDK, JDK i konfiguracja cache pozostawały poza zmianą w repo. Reprodukcje pomocnicze JVM uruchamiano również na systemowym JDK 17.0.20.

Pierwsze próby ujawniły braki środowiska: sam JRE zamiast pełnego JDK, zaufanie do certyfikatu proxy, metadane cache Gradle oraz brak bezpośredniego DNS w procesie Robolectric. Zainstalowano pełny JDK/SDK, skonfigurowano zaufany certyfikat środowiska i mirror Maven. Trzy obrazy `android-all-instrumented` dla API 28, 29 i 35 pobrano z mirrora Google do lokalnego repo Maven i sprawdzono SHA-512. Nie wyłączano walidacji TLS. **Błędów pobierania obrazów nie zaliczono do usterek gry; testy Androida ponowiono po ich usunięciu.**

Polecenia testów miały `--console=plain --no-configuration-cache --max-workers=1 -PtestForks=1`; kompilację testów ograniczono do procesu Gradle i heap 1536 MiB przez opcje uruchomienia, bez edycji builda. Standardowe przebiegi fast/screenshot przekroczyły limit 8 GiB i zostały zabite przez cgroup (kod 137, licznik `oom_kill` wzrósł z 0 do 2). Ponowienie przez zewnętrzny init ustawiło testowe `maxHeapSize=384m`, `maxParallelForks=1`, `forkEvery=1`, bez zmiany kodu, filtrów ani asercji. W tabeli wyniki zestawów częściowo się nakładają — nie należy ich sumować jako liczby różnych testów.

| Uruchomienie | Wynik rzeczywisty | Czas lokalny |
|---|---|---:|
| `:app:assembleDebug` | **PASS**; 43 wykonane zadania, gotowy APK | 2 min 39 s |
| Wymagany w zleceniu filtr `:app:testDebugUnitTest` | **74 testy, 0 błędów, 0 pominiętych** | 32,61 s |
| `fastTest`, konfiguracja repo z jednym forkiem | **NIEUKOŃCZONE — OOM kill**, kod 137; pełne wyniki aplikacji nie powstały. Sim/server w tym przebiegu odtworzono z wcześniej zweryfikowanego cache. | 204,18 s |
| `fastTest`, ponowienie z izolacją klas i heapem 384 MiB | **PASS**: app **422 przypadki repo (421 zaliczonych, 1 pominięty)**; sim 232 (1 pominięty); server 65 (1 pominięty); 0 błędów. XML app dodatkowo zawiera 7 osobno opisanych diagnostyk. | 502,79 s (8 min 22 s) |
| `screenshotTest`, konfiguracja repo z jednym forkiem | **NIEUKOŃCZONE — OOM kill**, kod 137; wygenerowane obrazy obejrzano, lecz nie zaliczono całego zestawu. | 309,98 s |
| `screenshotTest`, ponowienie z izolacją klas i heapem 384 MiB | **PASS**: 97 przypadków, 96 zaliczonych, 1 opcjonalny pominięty; 0 błędów | 588,29 s (9 min 48 s) |
| `balanceTest --continue`, `BALANCE_RUN=true BALANCE_RUNS=2 BALANCE_PROFILE=target` | **FAIL**: app 14 testów, 1 błąd; sim 11/11; server 3/3. Jedyny błąd: `BalanceReport.variantReport`. | 3 min 46 s |
| `:sim:test :server:test -PserverOnly=true --continue` | **PASS**: sim 243 przypadki, 242 zaliczone i 1 pominięty; server 68 przypadków, 67 zaliczonych i 1 pominięty; 0 błędów | 52,31 s |
| `-p buildSrc test` | **FAIL**: 20 testów, 4 błędy, 0 pominiętych | ponowienie 1,50 s |
| `:app:test --dry-run` | Sam dry-run kończy się kodem 0, lecz **kontrola grafu niezaliczona**: zawiera releaseChecklist i testReleaseUnitTest | 3,33 s |
| `:app:assembleRelease :app:bundleRelease` | **ZABLOKOWANE zgodnie z kontrolą podpisu**: brak upload JKS i `RELEASE_*`; nie powstał produkcyjny APK/AAB | 1,04 s |
| `python3 -m unittest discover -s tests -p 'test_*.py' -v` | **37/37 PASS**; obie kontrole inventory/shardów też PASS | 0,104 s same testy Python |
| Zewnętrzne diagnostyki `--tests '*Audit156*'` | **7 reprodukcji usterek potwierdzonych**, szczegóły w 4.4 | 43,95 s |

W końcowym XML fastTest aplikacja ma 429 przypadków: **422 z repozytorium oraz 7 diagnostyk audytu**, które pozostały w skompilowanym katalogu po wcześniejszym dołączeniu zewnętrznego source set. Wykaz klas XML potwierdził dokładnie 4 przypadki `Audit156LifecycleProbeTest` i 3 `Audit156UxDiagnosticsTest`. Oddzielono je od regresji gry; nie podnoszą liczby zaliczonych testów produktu. Pominięty przypadek app to opcjonalny eksport muzyki `MusicPreviewTest.exportNineErasAtThreeTensionLevels`.

Pominięte przypadki pełnych modułów: `AiPersonality127ReportTest.compareThreeStylesOnFiftySeeds` wymaga osobnego przełącznika raportu, a `ServerReplayBenchmarkTest.najdluzszaPowtorka` jest benchmarkiem opt-in. Nie przedstawiono ich jako zaliczonych pomiarów. W tym commicie znacząca część testów symulacji jest już w `sim/src/test/kotlin`; dlatego sam filtr aplikacji z treści zlecenia został uzupełniony pełnym `:sim:test`.

W ukończonym lokalnym zestawie zrzutów pominięto jedynie opcjonalne `StoreGraphicsTest.generate`. Pozostałe 96 przypadków wykonano; końcowy przebieg nie pochodził z cache wyników testowych.

Wymagany filtr uruchomiono dokładnie dla wzorców:

```bash
./gradlew :app:testDebugUnitTest \
  --tests '*BattleSimTest*' --tests '*BattleRulesTest*' \
  --tests '*Snapshot*' --tests '*LTest*' --tests '*Replay*' \
  --tests '*Counter*' --tests '*Endless*' --tests '*AiPersonality*' \
  --console=plain --no-configuration-cache --max-workers=1 -PtestForks=1
```

Końcówki rzeczywistych logów:

```text
# assembleDebug
BUILD SUCCESSFUL in 2m 39s
43 actionable tasks: 43 executed

# fastTest po izolacji procesów, bez zmian kodu i asercji
BUILD SUCCESSFUL in 8m 22s
45 actionable tasks: 3 executed, 42 up-to-date

# screenshotTest po izolacji procesów
BUILD SUCCESSFUL in 9m 48s
37 actionable tasks: 1 executed, 36 up-to-date

# pełne sim i server
BUILD SUCCESSFUL in 52s
13 actionable tasks: 2 executed, 11 up-to-date

# buildSrc
20 tests completed, 4 failed
BUILD FAILED in 1s

# graf :app:test — SKIPPED oznacza tutaj dry-run, nie usunięcie zależności
:app:releaseChecklist SKIPPED
:app:testReleaseUnitTest SKIPPED
BUILD SUCCESSFUL in 3s

# normalny release
Build file 'app/build.gradle.kts' line: 82
Brak prawidłowego podpisu release: wymagany upload JKS i cztery zmienne RELEASE_*.
BUILD FAILED in 906ms
```

Cztery błędy buildSrc to `PlayGamesIdsGeneratorTest.propertiesSchemaCoversAllCatalogNames` oraz `ReleaseChecklistTest.ownerInputDoesNotDuplicatePlayGamesId`, `validatesAllPublisherFieldsAndFormats`, `checksStoreDocsAndBlocksEveryPlaceholder`. Wymaganych kluczy PGS jest 71: projekt, cztery rankingi i 66 osiągnięć. Fixture `validOwner` używa adresów z `example`, odrzucanych przez produkcyjną kontrolę placeholderów. Dodatkowo `ReleaseChecklistTest:157–159` nadal oczekuje walidacji usuniętego pola właściciela `play_games_project_id`; tę nieaktualną asercję należy poprawić razem z fixture.

### 5.2. Rzeczywiście uruchomione raporty balansu

| Pomiar | Próba | Wynik |
|---|---|---|
| STEAM — oryginalny `variantReport` | 200 seedów × zamiana stron = 400 bitew | A **165 (41,25%)**, B **235 (58,75%)**, paty 0; różnica **17,50 pp**, próg ≤10 pp — **FAIL** |
| SILICON — dodatkowy program na zbudowanym `sim.jar` | Identyczna funkcja `variantDuel`, wzór seedów i 400 bitew | A **178 (44,50%)**, B **222 (55,50%)**, paty 0; różnica **11,00 pp**, próg ≤10 pp — **FAIL** |
| Endless, profil weterana, 100 seedów | Limity i profil oryginalnego testu serwera | Średnia fala **50,85**; mediana fali porażki **53**; przeżycie fali 20: **100/100**, 30: **96/100**, 40: **84/100**, 50: **80/100**, 60: **8/100**. Cele testu dla mediany 45–55 i fali 60 5–15% — **PASS**. |
| Dowódca w Endless, 100 seedów na wariant | Oryginalny raport `endlessCommander` | Średnia fala: bez dowódców **52,43**, tylko wrogi **50,96**, obie strony **50,85**. Przeżycie fali 60: **12/100**, **8/100**, **8/100**. |
| Lustrzane starcia | Epoki 0, 4, 8, po 20 bitew | Timeouty **0/20** w każdej; mediany **13,92 / 13,45 / 13,24 min**. |

Oryginalny test wariantów zatrzymał się na asercji STEAM i nie wykonał części SILICON ani późniejszych porównań kampanii w tej metodzie. Dlatego SILICON odtworzono osobnym programem, bez zmiany źródeł gry i testu; funkcja pojedynku została porównana z oryginałem. Program zachował oba kryteria i zakończył się błędem progu. **Nie jest to zaliczenie oryginalnego testu.**

```text
VARIANT STEAM: A=165 B=235 pats=0 diff=17.50pp
BalanceReport > variantReport FAILED
    java.lang.AssertionError at BalanceReport.kt:473
14 tests completed, 1 failed
BUILD FAILED in 3m 46s
45 actionable tasks: 3 executed, 42 up-to-date

# osobna reprodukcja SILICON, około 30,23 s
VARIANT SILICON: A=178 B=222 pats=0 diff=11.00pp
CRITERIA stalemates==0: true; diff<=10.0: false
RUN_EXIT: 1
```

Raport kampanii wykonano z `BALANCE_RUNS=2`, tak jak lekki przebieg CI. To próba techniczna dwóch seedów na etap, nie miarodajna estymacja odsetka wygranych graczy. Przykładowe rzeczywiste wyniki końcowych etapów dla profilu docelowego: etap 28 **2/2**, 29 **2/2**, 30 **2/2**, 31 **1/2**, 32 **0/2**, 33 **0/2**, 34 **1/2**, 35 **2/2**, 36 **1/2**; mediany odpowiednio **7,26; 4,05; 6,25; 6,92; 13,36; 11,15; 7,28; 11,19; 11,14 min**. Nie formułuję na tej podstawie nowego zarzutu trudności kampanii. Znalezisko A156-13 opiera się na osobnym, dużo większym porównaniu wariantów i istniejącym kryterium projektu.

### 5.3. CI badanego commita i jego koszt

Sprawdzono logi [przebiegu 41498736925](https://github.com/woogitsugames/Century-war/actions/runs/41498736925), którego SHA odpowiada badanemu `85e90b6`.

| Job | Wynik | Czas CI | Wniosek z logów |
|---|---|---:|---|
| Build, 125460434629 | FAIL | 67 s | APK zbudowany; później 4 błędy buildSrc. Dalszy krok kontroli licencji nie został wykonany. |
| Fast, 125460434203 | FAIL | 38 s | Kontrola grafu `:app:test` wykryła zadania release i przerwała przed szybkim zestawem testów. |
| Balance, 125460434324 | FAIL | 256 s | `BalanceReport.variantReport`, ten sam problem STEAM co lokalnie. |
| Zrzuty, 125460433805 | PASS z cache | 76 s | `:app:testDebugUnitTest FROM-CACHE`; XML zawiera 97 przypadków, 0 błędów i 1 pominięty. Nie jest to czas nowego wykonania testów. Brak głównego katalogu PNG w artefakcie — A156-27. |
| Lint, 125460434060 | PASS | 76 s | Log rzeczywiście kończy się sukcesem; obecne `continue-on-error` nadal osłabia przyszłą bramkę. |

Artefakt `cw-tests-screenshots-results` (ID 13167317808) pobrano i sprawdzono jako ZIP: **36 plików, 6 PNG w `app/build/test-results/testDebugUnitTest/l10n88`, zero PNG w `app/build/screenshots`**. Głównych obrazów nie odtworzono, ponieważ nie są zadeklarowanym outputem zadania testowego. Cache poprawnie może zaoszczędzić ponowne wykonanie niezmienionych testów; zarzut A156-27 dotyczy brakujących artefaktów do przeglądu, nie samego korzystania z cache. Opcjonalny `StoreGraphicsTest.generate` był pominięty w XML CI.

W workflow znajdują się powtórzone wykonania testów buildSrc (linie 51 i 61), kroki zależności/licencji (53–57 i 104–109) oraz nakładające się wybory testów. Ich usunięcie warto połączyć z naprawą bramek, zachowując pokrycie. Nie wyliczano oszczędności CI na podstawie samych nazw kroków; joby wykonują się równolegle, a cache zmienia ich koszt. Lokalny wynik 8 min 22 s dla fast i 9 min 48 s dla zrzutów dotyczy izolacji klas w tym środowisku, nie prognozy czasu GitHub Actions.

## 6. Determinizm, bezpieczeństwo i prywatność — sprawdzone zabezpieczenia

### Symulacja i komendy

`Battle.step` używa stałego kroku, deterministycznego `Rng` i jawnej konfiguracji. AI ma osobne strumienie zapisywane w snapshotach; obliczenia potęg w definicjach korzystają z `StrictMath` i cache. Nie znaleziono zegara systemowego ani niekontrolowanej losowości w przebiegu istniejącej bitwy. Domyślny zegar w `Daily.dayNumber` (`Modes.kt:412`) służy do wyboru dnia, a odtwarzanie korzysta z jawnej konfiguracji. `HashSet` użyty przy tworzeniu dziennego zestawu nie został uznany za błąd: sprawdzane jest membership w stałej kolejności, a zapis koduje maskę.

Operacje `Float` mają ustaloną kolejność, lecz nie wykonano porównania identycznych powtórek na JVM x86_64 i Android ART/ARM. Nie ma dowodu konkretnej rozbieżności liczb zmiennoprzecinkowych; pozostaje wymagany test urządzeń, a nie zgłoszenie hipotetycznego błędu. Przejrzane komendy UI przechodzą przez kontroler. Główna potwierdzona luka dotyczy niespójności walidacji tych samych komend przy odczycie snapshotu, nie pomijania kontrolera przez przyciski.

### Granice wejść i koszt weryfikacji serwera

W kodzie znajdują się ograniczenia: 1 MiB treści żądania także bez `Content-Length`, 600000 znaków Base64, 32768 komend i 432000 kroków. Weryfikacja ma pulę do 8 wykonawców, kolejkę 16 oczekujących i budżet 15 s, sprawdzany co 64 kroki. Serwer odtwarza konfigurację, legalność rozkazów, liczbę kroków i wynik; tryby z badaniami normalizuje dodatkowym przebiegiem. Odrzuca m.in. niedozwolone badania, tutorial, niepoprawny dzień/tydzień i nieobsługiwaną wersję gry. Źródła: `B/Api.kt`, `B/ReplayVerifier.kt`, `B/VerificationGate.kt`, `S/Replay.kt`.

HMAC jest porównywany w stałym czasie; okno czasu ma ograniczone porównania, nonce i kwoty są przechowywane w SQLite. Klucz zaszyty w APK jest **jawnie traktowany jako słaba bariera**, a nie bezpieczny sekret użytkownika (`Security.kt:11`, `ScoreClient.kt:38–40`, `server/README.md`). Własność gracza dodatkowo opiera się na losowym sekrecie instalacji, przechowywanym na serwerze jako SHA-256, a publiczny identyfikator wykorzystuje osobny sekret serwera. Nie zgłaszam samej obecności HMAC w aplikacji jako nieujawnionej luki. A156-04 i A156-05 pozostają problemami pomimo tych zabezpieczeń. Nie wykonano obciążenia produkcyjnego, testu sieci/proxy ani audytu konfiguracji uruchomionego hostingu; ograniczenia w kodzie nie są dowodem pełnej odporności na DoS.

### Pliki, manifest i dane

Profil, snapshot i historia mają walidację oraz atomowe podmiany. Snapshot i historia ograniczają wielkość pliku; limit dla obcego profilu istnieje w ścieżce importu `ProfileBackup`. Zwykły lokalny `Profile.load` używa `readText` bez limitu rozmiaru (`Profile.kt:1032,1045`), więc nie przypisuję mu tego zabezpieczenia. Profil obsługuje odzyskiwanie pliku tymczasowego oraz chroni stan zapisany przez nowszą wersję przed nadpisaniem. Import kopii profilu sprawdza wersję, rozmiar, sumę i semantykę, a przed zmianą tworzy kopię bezpieczeństwa. `toCloudJson` usuwa lokalne UUID, sekret instalacji i zgodę własnego rankingu (`Profile.kt:589–594`); to inny przepływ niż systemowy backup całego `profile.json`.

`ReplayRecord` sprawdza format, rozmiar i CRC32 — sumę wykrywającą uszkodzenie, nie podpis kryptograficzny. Import obcego nagrania przez `ReplayStore.importAsync` odtwarza Replay i porównuje wynik; zwykły zapis, listowanie i decode nie wykonują pełnej weryfikacji symulacji. Store ogranicza identyfikatory plików i retencję. `ReplayShareProvider` jest niewyeksportowany, udostępnia odczyt z przyznanym URI i kontroluje ścieżkę oraz dowiązania. Historia ma limit 1000000 bajtów i 100 wpisów oraz uwzględnia zdarzenia dodane podczas odczytu. Nie znaleziono dowodu trwałego wycieku Activity wynikającego tylko z braku `close()` pojedynczego executora; potrzebny byłby pomiar retencji obiektów.

Sprawdzony **scalony manifest debug**, a nie tylko plik źródłowy, zawiera cztery uprawnienia: `INTERNET`, `VIBRATE`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`. Nie zawiera `AD_ID`, dostępu do lokalizacji, kontaktów, mikrofonu ani uprawnień pamięci zewnętrznej. Eksportowana `MainActivity` obsługuje launcher i import plików; SDK PGS dodaje także eksportowaną `PlayGamesAppShortcutsActivity`. Własny receiver i provider nie są eksportowane. Nie stwierdzono luki w tej aktywności SDK; jej obecność uwzględniono zamiast błędnego twierdzenia, że jedynym eksportowanym komponentem całego pakietu jest MainActivity. Ruch cleartext jest zabroniony, zaufanie TLS ograniczone do certyfikatów systemowych (`res/xml/network_security_config.xml`). Manifest produkcyjnego AAB pozostaje do ponownej kontroli po konfiguracji release.

Reguły backupu obejmują tylko `profile.json` i `history.json`; wyłączają m.in. snapshot bitwy, powtórki, kolejki wyników i raporty awarii. `backup_rules.xml` wymaga flagi szyfrowania dostępnej od Androida 9, podczas gdy minSdk wynosi 26. Dokumentacja Androida określa E2E dla urządzeń od Androida 9 z blokadą ekranu [Ź3]. Dlatego nie można bezwarunkowo przypisywać tej gwarancji Androidowi 8/8.1; nie wykonywano rzeczywistego backupu na tych wersjach i nie stwierdzono wycieku danych.

Raporty awarii usuwają wiadomości wyjątków, ograniczają stosy i zdarzenia techniczne, przechowują maksymalnie 10 plików i są udostępniane ręcznie (`CrashReportText.kt`, `CrashReports.kt`). Własny ranking wymaga zgody i konfiguracji HTTPS. PGS może działać automatycznie po konfiguracji; deklaracja Data safety musi uwzględniać także zachowanie SDK [Ź4]. Nie zweryfikowano rzeczywistego konta PGS, backendu, retencji proxy ani formularza w Play Console.

## 7. Wydanie i Google Play

| Kontrola | Ustalenie |
|---|---|
| SDK | `compileSdk=36`, `targetSdk=36`, `minSdk=26` w kodzie; target/min potwierdzone w scalonym manifeście i APK debug. Aktualny wymóg nowych aplikacji i aktualizacji od 31.08.2026 to API 36 [Ź1]; ten warunek jest spełniony. |
| Wersja aplikacji | `versionCode=1`, `versionName=0.1.0-dev`; `release/last_version_code.txt=0`. Kod 1 jest większy od 0 i sam w sobie jest prawidłowy dla pierwszego wydania. Nazwa jest deweloperska; tag SemVer może ją zastąpić. Nie sprawdzono historii wersji konta Play Console. |
| Wersja zasad | `SimVersion.PROTOCOL=0.8.2`; klient i serwer korzystają z tego wspólnego źródła. Nie zmieniano protokołu. Niezależna kompatybilność Replay 5–7 wymaga ograniczenia po stronie rankingu — A156-04. |
| Dzień wydania | `Daily.RELEASE_DAY=21153`, czyli **2027-12-01**. To jawna data zastępcza, poza oknem checklisty względem dnia audytu. Nie zmieniono jej. |
| R8 | Minifikacja i shrinkResources są włączone. Reguły chronią komponenty Androida, enumy zapisane jako ordinal/nazwa oraz pola trwałych formatów; serializacja używa jawnych kluczy/bajtów. Nie wykryto konkretnego błędu reguł przez sam odczyt. |
| Smoke release | `release-check.yml` buduje na syntetycznych ID i kontrolowanym podpisie debug, bada mapping/DEX/baseline profile oraz uruchamia aplikację na emulatorze API 35. Te artefakty nie są wydaniem produkcyjnym; test uruchomienia nie zastępuje rozegrania bitwy, importu i wznowienia po R8. |
| APK debug | **3171602 bajty = 3,025 MiB**; SHA-256 `84440ec17ea4378c4f5e85c2a92d67a0ea846d9842f16b4aa80d53a10a04c7b6`. Rozmiar dotyczy zbudowanego lokalnego pliku APK, nie ZIP artefaktu CI. |
| APK/AAB produkcyjne | Nie uzyskano produkcyjnego artefaktu: brak klucza upload i kompletnej konfiguracji. Nie podaję pozornego rozmiaru AAB ani rozmiaru pobierania z Play. Obowiązkowy pomiar po zamknięciu A156-03/A156-14. |
| Strony 16 KiB | W zbudowanym APK nie było żadnego `.so`, również z zależności. Według Android Developers pakiet wyłącznie Java/Kotlin z takimi zależnościami nie wymaga przebudowy kodu natywnego [Ź2]. Próba na środowisku 16 KiB nadal jest wskazana; nie została wykonana. |
| Data safety | Formularz obejmuje także **testy zamknięte**, nie tylko produkcję; wyjątek dotyczy aplikacji aktywnych wyłącznie na ścieżce wewnętrznej [Ź5]. Szablony z nierozstrzygniętymi decyzjami nie są gotową deklaracją. |
| Polityka prywatności | Wymagany jest rzeczywisty, publicznie dostępny adres polityki i dane wydawcy; ostateczne treści trzeba uzgodnić z przepływami aplikacji i SDK [Ź6]. W repo pozostają zastępcze dane i rozbieżności A156-16. |

Dodatkowe ryzyko utrzymania: checklista dopuszcza `RELEASE_DAY` tylko od 30 dni wstecz do 60 dni naprzód (`ReleaseChecklist.kt:55–61`). Po pierwszym miesiącu od rzeczywistej premiery zwykła aktualizacja wymagałaby obejścia tej kontroli albo zmiany logiki walidacji. Należy odróżnić kontrolę daty pierwszej premiery od bieżącej daty publikacji aktualizacji; nie należy przesuwać początku wyzwań tylko po to, by odblokować build.

## 8. Wydajność, zrzuty, języki i utrzymanie

Podczas wykonanego przebiegu obejrzano poniższe **świeżo wygenerowane obrazy**, poza wcześniejszym przeglądem czterech wersjonowanych wzorców. Pochodziły z `app/build/screenshots`; nie dodawano ich do PR, zgodnie z zakresem „sam raport”. Wygląd pojedynczej sceny nie dowodzi poprawności hitboxów ani animacji.

| Świeży plik | Rozmiar | Ocena konkretnego stanu |
|---|---:|---|
| `pre10_phone720_de_37_bitwa_hud.png` | 1600×720 | Niemieckie etykiety, pauza, minimapa, awans i rekrutacja są czytelne i rozdzielone; bez widocznej kolizji w tej scenie. |
| `pre10_phone720_pl_41_bitwa_pauza.png` | 1600×720 | Statystyki, suwaki i przyciski wznowienia, restartu oraz poddania mieszczą się w panelu. |
| `pre10_phone720_de_29_dostepnosc_dol.png` | 1600×720 | Nazwa kadru jest myląca: wciąż pokazuje górę ekranu. Potwierdzono lukę testu A156-25; ten obraz nie dowodzi czytelności dolnych przełączników. |
| `cj_foldInner_L_pl_3_alive.png` | 2184×1968 | Leworęczny HUD jest odbity: pauza po lewej, ekonomia po prawej, akcje po lewej u dołu. Wódz i etykiety nie kolidują. |
| `cj_foldOuter_R_de_6_panel.png` | 904×2316 | Cztery sloty wież, zamknięcie i dolny HUD są rozdzielone. Niemieckie etykiety mieszczą się; sprzedaż ma drobny tekst. Ten wysoki układ nie obala A156-09 dla 600×360 dp. |
| `journey_phone720_07b_tut_wieza_panel.png` | 1600×720 | Instrukcja wież mieści dwa wiersze i nie zasłania wskazanego celu; przycisk pominięcia jest widoczny. |

Dodatkowe dwa obrazy diagnostyczne potwierdziły ściśnięcie przycisków wież i wystawanie ceny elity. Obejrzane wcześniej wzorce bitwy 720p/Fold, dostępności Fold i ustawień 720p nie zostały pomylone ze świeżymi wynikami. Nie deklaruję manualnego sprawdzenia wszystkich wygenerowanych PNG ani wszystkich dziesięciu języków.

Renderer używa prealokowanych `Paint`, `Path`, `RectF`, pul cząstek i cache rysunków tła/baz. Nie znaleziono oczywistego tworzenia nowych pędzli lub ścieżek dla każdej postaci w każdej klatce. Synteza dźwięku i zapis PCM działają w tle. Wymóg absolutnego zera alokacji nie jest jednak w pełni spełniony: `BattleTopBar.kt:131–132` tworzy tekst po zmianie całkowitego złota, co przy dochodzie późnych epok może następować co klatkę. Bez pomiaru nie przypisuję temu spadków FPS.

Obecne testy nie potwierdzają wydajności intensywnej bitwy na telefonie z 2–3 GB RAM. `BattleScreenAllocationTest.kt:54–84` bada pustą epokę 0 z wyłączonym AI i małym buforem. `BattleCrowdPerformance101Test.kt:56–80,101–113` używa 72 jednostek z zerowymi obrażeniami i `cd=1000`, a próg jest względny, 8× wobec 36 jednostek. Potrzebne są pomiary p95 czasu klatki, GC, PSS i temperatury podczas ostrzału, dowódców, rozbudowanych wież oraz wielokrotnego tła/wznowienia. Nie podaję niezmierzonych FPS ani czasu startu na telefonie. Istniejący `StartupTest.kt:17–20,35–41` nie czeka na zakończenie `startup-io` i może sprawdzić wyłącznie ekran zastępczy.

Statyczna kontrola tabeli językowej wykazała **1318 wpisów z 10 niepustymi kolumnami**, bez zduplikowanych kluczy i bez braków w literalnych odwołaniach. Nie jest to korekta językowa wszystkich tłumaczeń. Zmiany z równoległego zlecenia tłumaczeniowego nie były zawarte w badanym SHA. Palety, symbole drużyn i leworęczny HUD są zaimplementowane i mają osobne testy; brak semantyki TalkBack jest odrębnym problemem A156-21.

`BattleScreen.kt` ma 1681 linii, `BattleRenderer.kt` 1401, a `L.kt` 1938. Wydzielono części HUD, lecz współdzielą szeroki stan ekranu. Sama długość plików nie jest błędem premiery. Najbardziej opłacalne uproszczenia wynikają z realnych rozjazdów: jedna walidacja komend, jedna procedura zastosowania profilu i jawne przejścia cyklu życia. Nie znaleziono dostatecznego dowodu, by zalecić usuwanie konkretnego „martwego” fragmentu kodu; publiczne callbacki systemowe i ścieżki warunkowych usług nie mogą być tak kwalifikowane tylko dlatego, że zwykłe wyszukiwanie nie pokazuje wywołania.

## 9. Luki testów i kryteria ponownego audytu

W pierwszej kolejności potrzebne są regresje A156-01/02/04/05 i diagnostyk aplikacji zamienione na testy oczekiwanego, poprawnego zachowania. Następnie: zabicie procesu w punktach rozliczenia bitwy, timeouty zapisu, rzeczywiste przejścia pauza/surface/obrót/split-screen i zmiana density. ProfileTest należy nadal synchronizować przez `Profile.awaitHintWrites()` tam, gdzie odczytuje wynik asynchronicznego zapisu; samo dodanie oczekiwania w teście nie naprawi ignorowanego timeoutu w produkcyjnym `load()`.

Przed premierą trzeba sprawdzić poprawny podpisany pakiet na API 26–28 i 36, tanim telefonie oraz składanym urządzeniu. Zakres: start i szybkie Wstecz, powrót z tła, ubicie procesu, obie orientacje i okno dzielone, audio po przerwaniu, długie napisy i maksymalny rozmiar tekstu, leworęczność, palety i czytnik ekranu. Osobno: PGS na prawdziwym projekcie, konflikt zapisów, zmiana konta, osiągnięcia i ranking, kopia systemowa oraz odtwarzanie offline. Zmiana konta PGS może przenosić lokalny postęp zgodnie z polityką produktu; bez testu dwóch kont nie kwalifikowano tego automatycznie jako naruszenia prywatności.

Od strony usług wymagane są testy równoległego wyniku/usunięcia przez HTTP, długich lecz dopuszczalnych replayów, kwot i przeciążenia, a także sprawdzenie proxy, kluczy, retencji i procedury ponawiania usunięć po odtworzeniu backupu bazy. Są to brakujące dowody, nie twierdzenie, że wszystkie te mechanizmy są zepsute.

## 10. Aktualność i czego nie zrobiono

Porównanie z późniejszą gałęzią bazową `a570569` potwierdziło, że opisane główne usterki pozostają aktualne; pełne wyniki testów i numery linii dotyczą badanego `85e90b6`. W późniejszym diffie nie zmieniono `sim/`, `server/`, obsługi zapisu, cyklu życia ani panelu wież i renderera błysków. Zmiana MenuScreen dotyczyła odstępu pod banerem, nie resetu i wznowienia. Nowe wzorce PNG dotyczą kodeksu, rankingu i panelu Endless; nie unieważniają ocenianych ekranów bitwy, dostępności i ustawień. Jest to kontrola różnic, nie ponowny pełny audyt późniejszego commita.

Nie zmieniano kodu ani nie dodawano testów do repo. Diagnostyki i logi pomocnicze powstały poza nim; istotne kroki, liczby i wyniki są zapisane w tym raporcie. Nie konfigurowano kont właściciela, nie publikowano gry, nie zmieniano daty wydania i nie wysyłano żądań do produkcyjnego rankingu. Brak podpisanego artefaktu i prób urządzeń jest jawnym ograniczeniem audytu, nie pozytywnym wynikiem tych kontroli.

**Dostępność materiałów lokalnych:** po ukończeniu testów, podczas przerwanej publikacji gałęzi, środowisko robocze zostało wyczyszczone. Repozytorium odtworzono na tym samym badanym SHA, a raport z zachowanej treści, wyników narzędzi i notatek przeglądu. Powyższe testy rzeczywiście wykonano przed tą przerwą; po odtworzeniu nie uruchamiano ich ponownie. Cytowane wyniki i końcówki logów zachowano, lecz surowe lokalne XML, pełne logi, pomocnicze programy i wygenerowane PNG nie są załącznikami dostępnymi z odtworzonego katalogu. Logi i artefakty wskazanego przebiegu GitHub Actions stanowią odrębne źródło. To ograniczenie ponownego przeglądu lokalnych dowodów, nie zmiana wyników audytu.

## Źródła zewnętrzne

Stan sprawdzony 2 listopada 2026 r.; wymagania należy ponownie sprawdzić w dniu publikacji.

- **[Ź1]** [Google Play — Target API level requirements](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en).
- **[Ź2]** [Android Developers — Support 16 KB page sizes](https://developer.android.com/guide/practices/page-sizes).
- **[Ź3]** [Android Developers — Back up user data with Auto Backup](https://developer.android.com/identity/data/autobackup).
- **[Ź4]** [Android Developers — Play Games SDK data collection](https://developer.android.com/games/pgs/data-collection).
- **[Ź5]** [Google Play — Data safety, zakres i wymagania formularza](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en).
- **[Ź6]** [Google Play — User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en).

## 11. Proponowane zlecenia — paczki do równoległej pracy

| Paczka | Krótki tytuł i zakres | Ustalenia / zależności |
|---|---|---|
| A1 — równolegle z A2–A5 | **Spójność snapshotów nowych mechanik.** Walidacja wszystkich slotów wież, mnożników fal i cofania replaya; testy z kontrolerem i faktycznie aktywnymi jednostkami. | A156-01/02; jeden wykonawca dla wspólnego formatu i walidacji. |
| A2 | **Wersje zasad rankingu.** Jawne dozwolone formaty dla danego protokołu; negatywne testy starego replaya z aktualną wersją. | A156-04; zachować kompatybilność lokalnych nagrań. |
| A3 | **Usunięcie danych a operacje w toku.** Unieważnianie generacji i atomowy zapis; równoległe testy endpointów. | A156-05; uzgodnić minimalne zmiany `Api.kt` z A2. |
| A4 | **Cykl życia i spójny profil.** Bramka pętli gry, timeouty writerów, Wstecz podczas startu, reset snapshotu i aktualizacja density. | A156-06/07/17/18/20; jeden właściciel zmian `MainActivity`, `GameView`, `Profile`. |
| A5 | **Dostępność i panel wież.** Limiter według czasu rzeczywistego, nocne rozbłyski, poprawne cele dotyku na niskim ekranie. | A156-08/09/24/25; małe zmiany w BattleScreen, test komend i zrzutów. |
| B1 — po uzgodnieniu kontraktu zapisu A4 | **Idempotentne rozliczenie bitwy.** Trwały identyfikator wyniku, kolejność persist/delete i testy przerwania procesu. | A156-10; koordynacja `Profile`/`BattleScreen`, nie równoległe przebudowy tych samych metod. |
| B2 — równolegle z A | **Naprawa bramek CI i testów wydania.** Fixture buildSrc, graf debug, wymagane statusy przed tagiem, jawne zmienne konfiguracji. Usunąć duplikowane kroki dopiero po zachowaniu pokrycia. | A156-11/12/14/15/26/27. |
| B3 — równolegle z A | **Wyjaśnienie regresji wariantów.** Pełny raport nasion, poprawka przyczyny i ponowny test limitu 10 pp. | A156-13; zmiany balansu/protokołu koordynuje właściciel wspólnej symulacji. |
| B4 — praca właściciela i redakcji | **Konfiguracja i dokumenty sklepu.** Dane prawne, publiczna polityka PL/EN, aktualny opis osiągnięć i backupu, ID PGS, konfiguracja serwera, data i numer wydania, usunięcie porównań do innych gier. | A156-03/16/23; decyzje przed budową produkcyjnego AAB. |
| C1 — po A4 | **Jednolite przywracanie ustawień i odporne audio.** Wspólne zastosowanie profilu po imporcie/PGS; obsługa błędów AudioTrack. | A156-19/22; testy usług bez zmiany reguł rozgrywki. |
| C2 — osobna ścieżka UI | **Semantyka dostępności.** Wirtualne węzły platformowe, focus i akcje dla menu/pauzy, następnie HUD; próby TalkBack/Switch Access. | A156-21; bez dodawania biblioteki wbrew ograniczeniom projektu. |
| D — po scaleniu poprawek | **Kandydat do testów zamkniętych.** Zielony CI tego samego SHA, podpisany release, pomiar APK/AAB, próby urządzeń i usług, akceptacja Data safety. | Powtórzyć krytyczne reprodukcje, nie ograniczać się do kompilacji. Pomiary wydajności skoordynować z równoległym zleceniem 152. |

Przy poprawkach należy zachować dopisywanie enumów na końcu, deterministyczne RNG i przejście komend przez kontroler. Jeżeli zmieni się format snapshotu lub Replay, wykonawca musi odpowiednio zmienić wersję formatu; sama korekta walidatora nie oznacza automatycznie zmiany zapisywanych bajtów. Zmianę wersji zasad rankingu uzgadnia koordynator.
