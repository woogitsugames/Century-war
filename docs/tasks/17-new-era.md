# Zlecenie 17: Nowa epoka pomiędzy istniejącymi (szablon, pierwsza: Epoka żelaza)

**Gałąź robocza:** `gpt/17-new-era` (utwórz z najnowszej `origin/claude/bold-sagan-vwz5gm`)

**Faza 2:** uruchamiać dopiero po scaleniu bieżących prac (koordynator da znać).

---

# Prompt ogólny (wspólny dla wszystkich zleceń)

Pracujesz nad grą **Century War**, oryginalną strategią na Androida typu „wojna na jednym pasie przez epoki” (gatunek jak Age of War, ale własne nazwy, jednostki, grafika i mechaniki: **nie kopiuj niczego z Age of War**).

**Repozytorium:** `woogitsugames/century-war` (GitHub). **Gałąź bazowa:** `claude/bold-sagan-vwz5gm`.

## Zanim zaczniesz
1. Sklonuj repo i przejdź na gałąź bazową: `git fetch origin claude/bold-sagan-vwz5gm && git checkout -b <TWOJA_GAŁĄŹ> origin/claude/bold-sagan-vwz5gm`. Nazwa gałęzi jest podana w zleceniu.
2. Przeczytaj `README.md` i `docs/GDD.md` (projekt gry, zawartość, technologia).
3. Obejrzyj strukturę kodu w `app/src/main/java/com/centurywar/`:
   - `game/`: symulacja bitwy (czysty Kotlin, **deterministyczna**: `Battle.kt`, `Ai.kt`, `Defs.kt` z balansem, `Modes.kt` z kampanią/trybami/`Rng`), `BattleRenderer.kt` (rysowanie),
   - `engine/`: pętla gry (`GameView`), `Game`, `Screen`, `Ui`, `L` (teksty PL/EN), `Quality`, `Sound`,
   - `meta/`: `Profile` (zapis JSON, badania, rankingi lokalne), `OnlineLeaderboards`,
   - `screens/`: ekrany (menu, kampania, badania, rankingi, ustawienia, bitwa),
   - `MainActivity.kt`.

## Twarde zasady
- **Kotlin, Android SDK 36** (compileSdk/targetSdk 36, minSdk 26). Projekt celowo **nie używa AndroidX ani zewnętrznych bibliotek**. Dodaj zależność tylko wtedy, gdy zlecenie wprost na to pozwala.
- **Faza 2:** to zlecenie startuje po scaleniu przebudowy grafiki/menu, języków, kodeksu, samouczka i zapisu bitwy. Na starcie pobierz **najnowszą** gałąź bazową. Wolno edytować wszystkie pliki potrzebne do zlecenia, ale zmiany w `screens/*`, `engine/Ui.kt`, `engine/L.kt` i `game/BattleRenderer.kt` / `game/art/*` rób możliwie małe i w stylu istniejącego kodu. Teksty UI dodawaj do `engine/L.kt` we **wszystkich obsługiwanych językach** (sprawdź listę w pliku; brak tłumaczenia = angielski jako zapas, ale dąż do kompletu).
- **Symulacja musi zostać deterministyczna:** w `game/` nie używaj `System.currentTimeMillis()`, `Math.random()`, `kotlin.random` ani iteracji po `HashMap`/`HashSet`, gdzie liczy się kolejność. Losowość tylko przez `Battle.rng` (`Rng`). Test `deterministicForSameSeed` musi przechodzić.
- **Wydajność:** gra ma płynnie chodzić na tanich telefonach (2–3 GB RAM). W pętli gry i renderowania **zero alokacji** (pule obiektów, prealokowane tablice), żadnych operacji I/O na wątku gry w każdej klatce.
- Komentarze w kodzie po polsku, zwięzłe. Styl jak w istniejącym kodzie (kotlin official).
- Nie zmieniaj publicznych API używanych przez ekrany (`Battle`, `Profile`, `Game`) w sposób łamiący kompilację; dodawaj, nie usuwaj.
- **Bez identyfikatorów modeli AI** w commitach, komentarzach i plikach.

## Budowanie i testy (obowiązkowo przed pushem)
```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties   # jeśli trzeba
./gradlew :app:assembleDebug --console=plain
./gradlew :app:testDebugUnitTest --tests '*BattleSimTest*' --console=plain
```
- Test `ScreenshotTest` (Robolectric) jest wolny (~10 min) i potrzebuje pobrania ~200 MB; uruchamiaj go tylko, jeśli zlecenie dotyczy wyglądu. Przy błędach 429 z Maven Central ponawiaj z odstępem albo pobierz artefakt z `https://maven-central.storage-download.googleapis.com/maven2`.
- Jeśli dodajesz logikę, **dodaj testy JUnit** w `app/src/test/java/...`.

## Git
- Commituj małymi krokami, wiadomości po polsku (pierwsza linia ≤ 72 znaki).
- Wypchnij **wyłącznie na swoją gałąź** podaną w zleceniu (`git push -u origin <TWOJA_GAŁĄŹ>`). Nie pushuj na `claude/bold-sagan-vwz5gm` ani `main`, nie rób force-push.
- Opcjonalnie otwórz Pull Request z Twojej gałęzi **do** `claude/bold-sagan-vwz5gm`, z opisem zmian.

## Raport końcowy (w opisie PR albo w odpowiedzi)
- co zrobiono, lista zmienionych/nowych plików,
- wyniki budowania i testów (wklej końcówkę logu),
- czego nie zrobiono i dlaczego, ryzyka, ewentualne zmiany do naniesienia w zakazanych plikach (jako diff).

- Grafika jest rysowana kodem (styl: płaski cartoon z grubym ciemnym konturem, kolor drużyny dominujący: niebieski gracz / czerwony wróg). Do przeglądu wyglądu użyj testu `ArtGalleryTest` (rysuje wszystkie jednostki i bazy do PNG w `app/build/screenshots/`) i dołącz zrzuty do raportu/PR.

---

# Prompt zlecenia
## Cel
Rozbudować grę o nowe epoki wstawiane między istniejące. To zlecenie robi **pierwszą: Epokę żelaza (między Epoką brązu a Feudalizmem)** i przy okazji upraszcza dodawanie kolejnych (Renesans, Epoka atomowa, Era galaktyczna) w następnych aktualizacjach.

## Zakres
1. Refaktor pod wiele epok: `Defs.ERA_COUNT` wyliczane z danych, wszystkie tablice per epoka w jednym miejscu (np. lista obiektów `EraSpec` zamiast równoległych tablic), żadnych „magicznych” numerów epok w kodzie (szukaj `era == 4`, `e <= 3` itd. w `game/*`, `game/art/*`, `BattleRenderer`, ekranach) → zamień na cechy epoki (np. `spec.industrial`, `spec.futuristic`, styl tła, typ bazy).
2. Epoka żelaza: 4 jednostki (np. legionista z tarczą prostokątną, procarz/łucznik kompozytowy, jeździec w kolczudze, skorpion/balista polowa jako elita), wieża (balista na murze), ostrzał (np. „Salwa pilum”), baza (fort z palisadą i kamienną bramą), tło (np. wzgórza z drogą brukowaną). Nazwy oryginalne, bez nawiązań do innych gier, we wszystkich językach.
3. Balans: krzywe kosztów i statystyk po wstawieniu epoki (`STAT_GROWTH`, `COST_GROWTH`, `evolveXp`, `baseHp`, `income`) przeskaluj tak, żeby pełna potyczka przez wszystkie epoki trwała podobnie jak dziś (±15%). Kampania: +4 etapy dla nowej epoki (razem 36).
4. **Migracja zapisu:** `Profile.stars` zmienia indeksy etapów. Dodaj wersję profilu i migrację mapującą stare gwiazdki na nowe numery etapów (stare etapy 9–32 przesuwają się o 4). Test na przykładowym starym JSON. Rankingi „campaign” (suma gwiazdek) się nie psują.
5. Zapis bitwy (snapshot) i wyzwanie dnia: podbij wersje formatu. Wyzwanie dnia używa nowej listy epok od następnego dnia UTC (sprawdź, czy seed nie generuje innej konfiguracji dla dnia, który już trwa; jeśli tak, zastosuj zmianę od kolejnego dnia).
6. Kodeks (`screens/CodexScreen.kt`, jeśli istnieje) i kampania pokazują nową epokę automatycznie.
7. Dokumentacja: `docs/ADDING_ERA.md`, czyli lista kroków dodania kolejnej epoki (dane, grafika, teksty, balans, migracja), żeby następne epoki były szybkie.
8. Testy: `BattleSimTest` (wszystkie etapy kończą się), determinizm, migracja profilu, `ArtGalleryTest` z nową epoką (zrzuty do PR).
