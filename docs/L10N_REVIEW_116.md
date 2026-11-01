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


## Faktyczny przegląd gałęzi roboczych tury 92–115 (1.11.2026)

Przeczytano wszystkie 24 gałęzie zadaniowe i bezpośrednio przejrzano deklaracje z nowych części L.kt dostępnych na gałęziach: **92, 93, 104, 105, 106, 107, 108, 109, 110**. Łącznie **85 nowych kluczy i 850 tekstów**. Pozostałe gałęzie nie miały wtedy funkcji partNN() (albo nie dodawały tekstów). To nie jest audyt wersji po scaleniu — gałąź bazowa nadal nie zawiera żadnej z tych części.

### Kontrole strukturalne

| Warunek | Wynik |
|---|---|
| Dziesięć przekładów na klucz | 85/85 kompletne |
| Zgodność argumentów formatu %d, %s, %f względem EN | Bez wykrytych różnic |
| Nowe klucze zawierające składnię form mnogich | 0; brak nowych form do odmiany |
| Długości etykiet | Wymagają oceny na ekranie, nie na samym progu 18 znaków: część pól to opisy, a nie przyciski |

### Wykryte niespójności wymagające poprawienia w gałęziach źródłowych

1. **PILNE — ec_boss_recall_d (108), nazwy zdolności.** Opis karty odnawiającej **Ostrzał** i **Zryw** używa innych nazw niż kanoniczne cx_barrage / rally. Niezgodności: DE Sturm zamiast Ansturm; ES Impulso zamiast Ímpetu; FR Salve zamiast Barrage; IT Salva zamiast Sbarramento; PT Salva zamiast Bombardeio; NL Salvo i Aanval zamiast Spervuur i Stormloop; TR Atış i Hücum zamiast Baraj ateşi i Atılım. Można odmiennie odmieniać nazwy w zdaniu, ale nie zmieniać konkretnej zdolności na inną. Dodano do LTestReview116 test regresyjny.
2. **PILNE — bh105_best_wave (105): nieprzetłumaczone Endless w 9 lokalizacjach innych niż EN.** PL: „Rekord fal Endless w czasie”, DE: „Endless-Wellenrekord im Verlauf”, ES: „Récord de oleadas Endless”, FR: „Record de vagues Endless”, IT: „Record delle ondate Endless”, PT: „Recorde de ondas Endless”, UK: „Рекорд хвиль Endless із часом”, NL: „Endless-golfrecord door de tijd”, TR: „Endless dalga rekoru”. Używać nazw lokalizowanych z endless; krótką etykietę dopasować do rzeczywistej szerokości wykresu. Dodano regresję.
3. **PILNE — bs104_evolution (104), EN „Era advance”.** Termin „Advance” oznacza postawę armii; awans epoki to „Evolve”. Zalecane np. „Era evolution”/„Era change”. Dodano regresję.
4. **wn_010_iron (106), opisy epoki żelaza**: ES „una torre”, FR „une tour”, IT „una torre” zamiast odpowiednio torreta, tourelle, torretta — nazwy obronnych budowli muszą odpowiadać cx_turret. Dodano regresję.
5. **day109_rain (109), EN „Archers and Turrets”**: „Archers” zawęża „strzelców / ranged troops”, obejmujących wiele epok. Zalecane „Ranged Troops and Turrets”.
6. **ec_boss_recall (108), EN „Boss recall”, DE „Boss-Neustart”, FR „Relance du boss”** sugerują przywołanie/restart bossa, choć karta odnawia zdolności po jego pokonaniu. Zalecane odpowiednio „Boss recharge”, „Aufladen nach Bosskampf”, „Recharge après le boss”.
7. **day109_hired_d (109), DE „freie Söldner”** może znaczyć „wolni” zamiast „darmowi”. Trafniejsze „kostenlose Söldner”.
8. **privacy93_remove (93), EN „Deleting ... stores a request that retries offline”** oraz analogiczne PL „ponawia je offline” są niejasne: sugerują wykonywanie operacji serwerowej bez sieci. Jeśli implementacja kolejkuje żądanie do czasu połączenia, komunikat powinien dosłownie mówić o ponowieniu po odzyskaniu sieci.
9. **profile_backup_export / profile_backup_import (107), FR** nie mówią wprost o pliku, w odróżnieniu od innych lokalizacji (Enregistrer le profil / Charger le profil). Warto dodać dans un fichier / depuis un fichier, jeżeli pozwala rozmiar wiersza ustawień.

Długość privacy93_confirm przekracza 18 znaków w 7 językach. Jest to potencjalny problem tylko wtedy, gdy taki tekst rzeczywiście wyświetla się w przycisku o ograniczonej szerokości; należy sprawdzić układ, zamiast mechanicznie skracać komunikat.

**Nie wprowadzono poprawek do gałęzi 92–115** — zlecenie nakazuje zmieniać wyłącznie własną gałąź. Brak tych nowych funkcji w bazie uniemożliwia bezkonfliktową edycję docelowego L.kt na gałęzi 116. Konkretne regresje zapisano w testach, które po dołączeniu tłumaczeń wskażą niespójności.
