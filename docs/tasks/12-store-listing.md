# Zlecenie 12: Materiały do Google Play: opis, polityka prywatności, plan monetyzacji

**Gałąź robocza:** `gpt/12-store-listing` (utwórz z `origin/claude/bold-sagan-vwz5gm`)

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
- **NIE edytuj** tych plików (są równolegle przebudowywane przez innych wykonawców, edycja = konflikt): `app/src/main/java/com/centurywar/screens/*`, `engine/Ui.kt`, `engine/L.kt`, `engine/Quality.kt`, `game/BattleRenderer.kt`. Jeśli potrzebujesz tekstów UI, utwórz **własny plik z tabelą tekstów PL/EN** (np. `meta/AchievementTexts.kt`) w stylu `engine/L.kt`. Jeśli potrzebujesz zmiany w zakazanym pliku, opisz ją dokładnie w raporcie (diff do naniesienia później) zamiast ją robić.
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

---

# Prompt zlecenia
## Cel
Komplet tekstów potrzebnych do publikacji (bez kodu).

## Zakres (pliki w `docs/store/`)
1. `listing_pl.md` i `listing_en.md`: tytuł (≤ 30 znaków), krótki opis (≤ 80), pełny opis (≤ 4000) z listą funkcji (8 epok, 32 jednostki, kampania 32 etapy, tryb bez końca, wyzwanie dnia, rankingi, działa offline, optymalizacja na słabsze telefony i Foldy), słowa kluczowe ASO (bez nazw cudzych gier!).
2. `privacy_policy_pl.md` / `_en.md`: gra nie zbiera danych osobowych; zapis lokalny; opcjonalnie Google Play Games i własny serwer rankingów (anonimowe UUID i nick); brak reklam w v1. Gotowe do wystawienia jako strona (GitHub Pages).
3. `data_safety.md`: odpowiedzi do formularza „Bezpieczeństwo danych” w Play Console.
4. `content_rating.md`: odpowiedzi do kwestionariusza IARC (przemoc kreskówkowa, brak krwi).
5. `monetization_plan.md`: model przyjazny graczom (reklamy wyłącznie z nagrodą: podwojenie medali po bitwie, jednorazowy zakup „bez reklam + 2× medale z wyzwań”, kosmetyka), zero pay-to-win w rankingach; techniczne wymagania integracji (AdMob z mediacją, Play Billing 7+) i zgodność z polityką rodzin (Families) do rozważenia.
6. `screenshots_plan.md`: 8 zrzutów ekranu z podpisami PL/EN i proponowany film promocyjny (scenariusz 30 s).
