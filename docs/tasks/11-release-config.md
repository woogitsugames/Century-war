# Zlecenie 11: Konfiguracja wydania: AAB, R8, Baseline Profile, wersjonowanie

**Gałąź robocza:** `gpt/11-release-config` (utwórz z `origin/claude/bold-sagan-vwz5gm`)

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
Przygotować projekt do publikacji w Google Play i przyspieszyć start gry.

## Zakres
1. `app/build.gradle.kts`: `bundleRelease` (AAB), podział ABI/density nie dotyczy (brak natywnego kodu), `versionCode` z liczby commitów albo z `gradle.properties`, `versionName` z tagu. R8 full mode, sprawdzenie, że release działa (żadnych reguł refleksji nie potrzeba; zweryfikuj).
2. **Baseline Profile:** ręcznie przygotowany `app/src/main/baseline-prof.txt` dla klas pętli gry (`engine/*`, `game/Battle*`, `screens/BattleScreen`) **albo** moduł generujący (jeśli wymaga AndroidX benchmark, umieść go w osobnym module `:baselineprofile`, który nie trafia do aplikacji). Dodaj `androidx.profileinstaller` **tylko** jeśli to konieczne do instalacji profilu; uzasadnij (wyjątek od zasady „bez AndroidX” dopuszczalny tylko dla profileinstaller).
3. `android:localeConfig` (pl, en) oraz `res/xml/locales_config.xml`; `android:dataExtractionRules` i `fullBackupContent` (backup profilu `profile.json`, bez cache).
4. Sprawdź manifest pod Play: `appCategory=game`, `isGame`, brak zbędnych uprawnień, wpis `<queries>` niepotrzebny itd.
5. Raport rozmiaru: APK/AAB release przed i po.

## Kryteria akceptacji
- `assembleRelease` i `bundleRelease` przechodzą; aplikacja release startuje (opisz, jak sprawdziłeś, np. `apkanalyzer`).
