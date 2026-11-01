# Zlecenie 116 — przegląd lokalizacji tury 92–115

## Stan wejściowy

- Gałąź bazowa: `claude/bold-sagan-vwz5gm`.
- W chwili przygotowania kontroli `engine/L.kt` nie zawierał jeszcze funkcji `part92()`–`part115()`; gałęzie `gpt/92` i `gpt/108` również nie były dostępne.
- Terminologia referencyjna: `docs/GLOSSARY.md` (audyt 88), test `audit88TerminologyAndFormatting` w `LTest.kt`.
- **Ocena tłumaczeń nowych kluczy: oczekuje na ich dołączenie. Nie wolno traktować pominiętych testów jako zweryfikowania treści.**

## Kontrole automatyczne

`LTestReview116.kt` odnajduje klucze według funkcji `part92()`–`part115()`, a nie według prefiksów kluczy. Sprawdza:
- dziesięć niepustych przekładów w kolejności `L.CODES`, brak duplikatów i zgodność placeholderów;
- trzy formy mnogie PL/UK, dwie w pozostałych językach oprócz TR (jedna), a dla prostych liczebników próby 1/2/5/21/22/25;
- kanoniczne nazwy pojęć przy identycznej etykiecie EN, rozróżnienie EN `Evolve`/`Advance`, użycie `turret` zamiast `tower`, `Steam Age`/`Silicon Age`, DE odstępy przed `%`, UK `ОД`/`ОЗ`, NL nazwy epok, TR `Çağ atla` i procent przed liczbą;
- limit 18 znaków dla kluczy rozpoznanych jako krótkie etykiety akcji.

`ScreenshotTestReview116.kt` mierzy szerokości nowych krótkich etykiet i wybiera **trzy najdłuższe wersje językowe**, zamiast zakładać języki na sztywno. Dla każdego języka przygotowuje przy rozdzielczości 1600 × 720 (720p) arkusz tłumaczeń oraz zrzuty Ustawień, Statystyk, Kodeksu, bitwy i wyników. Wynik rankingu długości zapisuje w `build/screenshots/l10n116/widths.tsv`.

## Uruchomienie

```bash
./gradlew :app:assembleDebug --console=plain --no-configuration-cache
./gradlew :app:testDebugUnitTest --tests '*LTest*' --console=plain --no-configuration-cache
./gradlew :app:testDebugUnitTest --tests '*ScreenshotTestReview116*' --console=plain --no-configuration-cache
```

Dedykowany workflow: `.github/workflows/l10n-116.yml`, artefakt `l10n116-720p`. Gdy źródło nie zawiera żadnego `part92()`–`part115()`, testy 116 zgłaszają **pominięcie przez JUnit Assume**, zamiast udawać pomyślne zbadanie nieistniejących tekstów.

## Pozostała praca po dołączeniu tury

1. Wykonać kontrolę treści każdej nowej pozycji w 10 językach z uwzględnieniem kontekstu ekranowego, naturalności, odmiany i spójności z glosariuszem. Sam test tekstowy nie rozstrzyga poprawności językowej.
2. Uruchomić build, `LTest`, zrzuty 720p i obejrzeć wszystkie obrazy — szczególnie etykiety i teksty przekraczające dostępną szerokość.
3. Poprawić rzeczywiste błędy w `L.kt` minimalnymi zmianami; w razie potrzeby dopisać regresje do testów i odświeżyć raport.
4. Nie scalać niniejszej gałęzi tylko na podstawie pominiętych testów.
