# D52 — ponowny eksport grafik sklepu D7 po zmianach etapów (R12 poz. 4)

Baza: `origin/integ/tura10` @ c7e5dc70 (PROTOCOL 0.8.10). Gałąź: `claude/dzien-52-sklep`.

## Jak test zapisuje grafiki

`StoreGraphicsTest.generateD7` (`app/src/test/java/com/centurywar/screens/StoreGraphicsTest.kt`)
działa tylko przy zmiennej środowiskowej `STORE_GRAPHICS_D7=true` (nie właściwość systemowa, nie
osobne zadanie Gradle). Pisze do `app/build/storeD7/<język>/` (ścieżka względna od `app/`);
do `docs/store/assets/D7/` kopiuje się ręcznie (patrz `docs/store/STORE_GRAPHICS.md`).
Języki: `STORE_LANGUAGES`, urządzenia: `STORE_D7_DEVICES`.

## Uruchomienie (obejście `tools/offline-build/`)

```bash
# /tmp/claude-0/d52: libs, android, apt podlinkowane z d48; stubs, monitor, mockgen skopiowane
CW_OFFLINE_DIR=/tmp/claude-0/d52 tools/offline-build/build_app.sh <repo>
STORE_GRAPHICS_D7=true STORE_LANGUAGES=pl,en CW_OFFLINE_DIR=/tmp/claude-0/d52 \
  tools/offline-build/run_tests.sh <repo> com.centurywar.screens.StoreGraphicsTest
python3 scripts/validate_store_d7.py app/build/storeD7 --languages pl,en
```

Wynik: `OK (7 tests)` w 79 s; walidator: 48 zrzutów do Play w 2 językach, OK. Nic nie pobierano.
Wyeksportowano tylko `pl` i `en` (w repo są pełne PNG tylko tych języków).

## Porównanie z obecnymi plikami (próg różnicy piksela > 24)

| Kadr (pl/en, phone i tablet10) | Różnica | Ocena |
|---|---|---|
| 01, 02, 04, 07 | 0% | bez zmian |
| 03 kampania | 0,06–0,34% | migotanie „iskry” przy etapie 11 i chmury — animacja, szum |
| 05 wyniki | 0,06–0,07%, pas pod paskiem HUD | **zmiana merytoryczna** (niżej) |
| 06 misje | 0,9–1,5% | przesunięte chmury w tle — szum |
| 08 oblężenie tygodnia | 1,5–2,3% | licznik „Koniec za …” liczony od prawdziwego zegara (4d 15:35 → 3d 13:07) i drobne przesunięcie rzędu progów medali; kadr niedeterministyczny, nie zatwierdzam |
| manifesty `manifest.tsv` | identyczne | bez zmian |

**Kadr 05:** liczby w okienku wyniku bez zmian (czas 7:45, 62 zabitych, +131 za bitwę, +130 za
osiągnięcia, 3 gwiazdki). Zmienił się HUD bitwy nad okienkiem: zamiast zegara `7:45` widać
`Presja oblężnicza ×2.4` (złoto wroga 12677 → 12674). Powód: po skróceniu etapów 8–12 (R11-02)
presja w etapie 11 zaczyna się od 5. minuty i rośnie o 50%/min (1 + 2,75 × 0,5 ≈ ×2,4). Kadr
pokazuje więc prawdziwy stan gry w 0.8.10.

Zatwierdzone pliki:
- `docs/store/assets/D7/pl/phone/05-results.png`
- `docs/store/assets/D7/pl/tablet10/05-results.png`
- `docs/store/assets/D7/en/phone/05-results.png`
- `docs/store/assets/D7/en/tablet10/05-results.png`
- `docs/store/assets/D7/pl/preview_contact.png`, `docs/store/assets/D7/en/preview_contact.png`
  (stykówki zawierają nowy kadr 05; przy okazji niosą szum z 03/06/08)

Obejrzane kadry (Read): 03, 05, 06, 08 `pl/phone` (stary nad nowym) i cały nowy 05.

## Zastrzeżenia i do decyzji

- Obejście linkuje zasoby z API 34 i kompiluje Kotlinem 2.0.21 (projekt: 2.2.20). Różnice
  renderu w kadrach 01/02/04/07 wyszły zerowe, więc wpływ na te grafiki jest pomijalny, ale to
  nie jest eksport Gradle.
- Komentarz przy `D7_RESULT_SECONDS` w teście („przed presją oblężniczą (8:00)”) jest nieaktualny
  — w etapie 11 presja rusza od 5:00. Jeśli w kadrze ma być zegar zamiast presji, trzeba by
  zejść poniżej 300 s, co łamie obecny warunek testu „czas 7–10 min”; zostawiłem bez zmian kodu.
- Pozostałe 8 języków (stykówki w repo) mają jeszcze stary kadr 05; pełny eksport 10 języków
  (`STORE_GRAPHICS_D7=true` bez `STORE_LANGUAGES`) przed uploadem do Play.
- Kadr 08 zależy od zegara (czas do końca tygodnia) — zawsze będzie różny między eksportami.
