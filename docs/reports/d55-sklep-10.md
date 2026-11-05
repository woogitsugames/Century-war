# D55 — pełny eksport grafik sklepu D7 w 10 językach

Baza: `origin/main` @ 7dd64d95. Gałąź: `claude/dzien-55-sklep-10`.

## Uruchomienie

Eksport przez Gradle (bez obejścia offline z D52), wszystkie języki (bez `STORE_LANGUAGES`):

```bash
STORE_GRAPHICS_D7=true flock /tmp/claude-0/gradle.lock ./gradlew :app:testDebugUnitTest \
  --tests '*StoreGraphicsTest.generateD7*' --tests '*StoreGraphicsTest.d7CaptionsComplete*' \
  --tests '*StoreGraphicsTest.listingLimits*' --tests '*StoreGraphicsTest.storeAssetRequirements*' \
  --rerun --no-configuration-cache --console=plain
python3 scripts/validate_store_d7.py app/build/storeD7
```

Wynik: `BUILD SUCCESSFUL in 3m 6s`; walidator: 10 × „24 zrzutów do Play + Fold + QA + grafika +
stykówki, OK”, razem 240 zrzutów do Play w 10 językach, OK.

## Porównanie z repo (próg różnicy piksela > 24)

W repo (`docs/store/assets/D7/`) są pełne PNG tylko `pl` i `en` (phone, tablet10) oraz manifesty
i stykówki wszystkich języków — tylko te pliki porównywano i ewentualnie kopiowano.

| Pliki | Wynik | Ocena |
|---|---|---|
| `manifest.tsv` (10 języków) | identyczne | bez zmian |
| pl/en 01, 02, 04, 05, 07 (phone, tablet10) | 0% | bez zmian (05 zatwierdzony w D52) |
| pl/en 03 / 06 / 08 | 0,06–0,18% / 0,8–1,0% / 1,3–1,9% | iskra i chmury, licznik tygodnia od zegara — szum, nie zatwierdzam |
| stykówki pl, en — kafelki | 05: 0%, zmiany tylko w 03/06/08 | szum — bez zmian w repo |
| stykówki de, es, fr, it, nl, tr, uk — kafelek 05 | 0,06–0,08% | **merytoryczna**: HUD „Presja oblężnicza ×2.4” zamiast zegara 7:45 |
| stykówka pt — kafelek 05 | 0,56% | **merytoryczna**: presja jak wyżej + tekst pt-PT „Já podes pagar! Desbloqueia em Pesquisa…” (było „Você pode pagar…”) |
| stykówka pt — kafelek 04 | 0,01% | **merytoryczna**: „Escolhe uma carta” (było „Escolha”, ujednolicenie pt-PT 93f141d3) |
| stykówki — kafelki 01, 02, 07 | 0% | bez zmian |

Liczby w okienku wyniku (wszystkie języki): czas 7:45, 62 zabitych, +131 za bitwę, +130 za
osiągnięcia, 3 gwiazdki, nowy rekord — jak w pl/en z D52.

## Zatwierdzone pliki

- `docs/store/assets/D7/{de,es,fr,it,nl,pt,tr,uk}/preview_contact.png` — odświeżone stykówki
  z nowym kadrem 05 (przy okazji niosą szum kafelków 03/06/08, jak w D52).
- Pełne PNG pl/en i stykówki pl/en: bez zmian (brak zmian merytorycznych, zostają spójne).
- `app/src/test/java/com/centurywar/screens/StoreGraphicsTest.kt`: komentarz przy
  `D7_RESULT_SECONDS` — w etapie 11 presja rusza od 5:00 (`Campaign.SECOND_SIEGE_START`,
  +50%/min), więc kadr 05 pokazuje „×2.4” zamiast zegara; kod bez zmian.

## Obejrzane kadry (Read)

`app/build/storeD7/{de,tr,pt}/phone/05-results.png` (pełne kadry; `ja` nie ma — projekt ma 10
języków bez japońskiego), stykówka de, wycinki stary/nowy kafelka 05 i 04 w stykówce pt.
Napisy mieszczą się, HUD pokazuje „Belagerungsdruck ×2.4”, „Kuşatma baskısı ×2.4”,
„Pressão do cerco ×2.4”.

## Uwagi

- Kadry 03/06/08 dalej różnią się między eksportami (animacja tła, zegar tygodnia w 08) — przed
  uploadem do Play i tak generuje się pełny zestaw lokalnie.
- Pełne PNG pozostałych 8 języków nie są w repo (zgodnie z `docs/store/STORE_GRAPHICS.md`).
