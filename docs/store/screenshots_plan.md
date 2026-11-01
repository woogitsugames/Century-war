# Century War — plan zrzutów Google Play (zlecenie 117)

Wykorzystujemy osiem aktualnych, autentycznych kadrów na typ urządzenia, w 10 wersjach językowych: `pl,en,de,es,fr,it,pt,uk,nl,tr`. Nie kopiujemy innych gier ani elementów spoza Century War. Hasła są osobne dla każdego języka i nie udają elementów gry.

## Osiem kadrów

| Nr | Plik | Co pokazuje |
| --- | --- | --- |
| 01 | `01-menu.png` | Menu, logo, emblemat i tryby |
| 02 | `02-campaign.png` | Mapa 36 etapów i gwiazdki |
| 03 | `03-endless-boss.png` | Boss fali 10 Endless, walczące oddziały i HUD |
| 04 | `04-night-barrage.png` | Nocny ostrzał, efekty i pasek akcji bitwy |
| 05 | `05-era-variants.png` | Dwa rzeczywiste warianty pary |
| 06 | `06-pause-summary.png` | Pauza, statystyki i ustawienia |
| 07 | `07-weekly-siege.png` | Oblężenie tygodnia i nagrody |
| 08 | `08-custom-skirmish.png` | Konfigurator potyczki |

Pliki napisów i tekstów alternatywnych: `app/src/test/resources/store/graphics_{pl,en,de,es,fr,it,pt,uk,nl,tr}.properties`. Opisy alt nie mogą przekraczać 140 znaków.

## Urządzenia, Fold i formaty

- Telefon: `1920×1080`; tablet 7″: `1920×1080`; tablet 10″: `2560×1440`, wszystkie poziomo 16:9. Nie rozciągamy gotowych screenshotów: każdy ekran jest odrębnie renderowany z jego DPI.
- Grafika promocyjna: `1024×500` RGB; ikona: `512×512` RGBA, maksymalnie 1024 KB.
- Dwa dodatkowe kadry `preview_foldLandscape_hud.png` i `preview_foldPortrait_hud.png` renderują autentyczną `BattleScreen` z dolnym paskiem na rozłożonym Foldzie w rozmiarach `2184×1968` i `1968×2184`; **wyłącznie do kontroli QA**.
- `preview_contact.png` to plansza do kontroli, a `manifest.tsv` zawiera wyłącznie 26 dopuszczonych grafik sklepu (8×3 + ikona + feature).
- Suma: 10 języków × (26 publikacyjnych + 3 QA) = **290 PNG**, do tego 10 manifestów.

## Generowanie i sprawdzanie

```bash
STORE_GRAPHICS=true ./gradlew :app:testDebugUnitTest --tests '*StoreGraphicsTest*' --no-configuration-cache --console=plain
python3 scripts/validate_store_graphics.py app/build/store
```

Workflow **Store graphics** automatycznie generuje grupy językowe na gałęzi zadania i udostępnia komplet w jednym artefakcie. Wymagania Google Play zweryfikowano **2026-11-01**:
https://support.google.com/googleplay/android-developer/answer/9866151

Wciąż konieczny jest przegląd wizualny każdej lokalizacji, porównanie z finalnym AAB i podgląd Play Console. Kadry z tygodniowym wyzwaniem wymagają powtórnego eksportu przed premierą. **Nie wysyłać automatycznie do sklepu.**
