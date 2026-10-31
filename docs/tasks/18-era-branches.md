# Zlecenie 18: Rozgałęzienia epok: wybór jednej z dwóch wersji przy awansie

**Gałąź robocza:** `gpt/18-era-branches` (utwórz z najnowszej `origin/claude/bold-sagan-vwz5gm`)

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
Przy awansie gracz wybiera jedną z dwóch wersji następnej epoki. To mocno podnosi regrywalność i różnorodność strategii.

## Wymaga
Zrobionego zlecenia 17 (refaktor `EraSpec`). Najlepiej też 16.

## Zakres
1. Model danych: epoka może mieć warianty (`EraSpec.variants`). Na start dwa rozgałęzienia: **Epoka pary → „Diesel” (ciężkie pancerne, wolne, wytrzymałe) albo „Tesla” (energia elektryczna, obrażenia obszarowe/łańcuchowe, kruche)** oraz **Epoka krzemu → „Cyber” (drony, roje, tanie jednostki) albo „Bio” (regeneracja, mutanty-mechy, drogie)**. Każdy wariant ma własne 4 jednostki, wieżę, ostrzał, bazę i tło (lub warianty istniejących z wyraźnie innym wyglądem).
2. Symulacja: `Side.eraVariant`, wybór przy `evolve` (gracz: modal wyboru jak doktryny, przed nimi albo zamiast nich przy tych awansach; AI: według osobowości/kontry). Deterministycznie.
3. Mechanika łańcuchowa (Tesla) i regeneracja (Bio) w `Battle.kt`, wydajnie (bez alokacji, O(n) na trafienie).
4. UI: ekran wyboru wariantu z podglądem jednostek (`drawUnitIcon`) i krótkim opisem stylu gry. Kodeks pokazuje oba warianty.
5. Wyzwanie dnia może narzucać wariant (modyfikator), a kampania ma etapy wymuszające wariant.
6. Balans: oba warianty z podobnym współczynnikiem wygranych w symulacjach (±5 pp), z raportem.
7. Zapis bitwy i profilu: wersje formatów, migracje, testy.
