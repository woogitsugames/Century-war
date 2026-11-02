# Tura 9 — przegląd UX: dowódca (125) i stopnie wież (140)

Ścieżka gracza, który pierwszy raz spotyka Wodza (etap 8 kampanii, indeks 7) i stopnie wież.
Test: `app/src/test/java/com/centurywar/screens/CommanderJourneyTest.kt`, zrzuty
`app/build/screenshots/cj_<ekran>_<R|L>_<język>_<stan>.png`, dziennik `cj_audit.txt`.

Ekrany: telefon 1600×720 d=2 (`phone`), pion 720×1280 d=2 (`portrait`), Fold zewnętrzny
904×2316 d=2,7 (`foldOuter`), Fold wewnętrzny 2184×1968 d=2,7 (`foldInner`); układ prawo-
i leworęczny (`R`/`L`); języki pl, en, de, tr, uk.

## Co działa dobrze

- Dymek dowódcy pojawia się dopiero, gdy stać gracza na Wodza (bez złota go nie ma,
  `cj_phone_R_pl_1_disabled.png`), wskazuje przycisk Wodza i stoi nad dolnym paskiem — nie
  zasłania żadnego przycisku HUD na żadnym ekranie i w żadnym języku (`*_2_hint.png`).
- Dymek stopni wież wskazuje przycisk „Wieże” i nie zasłania HUD (`*_5_tierhint.png`).
- Cele dotyku: Wódz 66 dp (telefon), 70 dp (Fold wewn.), 48 dp (pion, Fold zewn.);
  przyciski stopnia w panelu wież 64×48 dp na wszystkich ekranach, bez nakładania się.
- Kodeks (Zdolności) opisuje dowódcę w pełni (`comm125_codex`).

## Znalezione i naprawione

1. **„Ranga 0” przy przywołaniu Wodza** — przywołanie korzystało z efektu awansu
   weterana, więc nad paskiem górnym wyskakiwał napis „Ranga 0” (ten sam błąd przy
   rekrutach i bossach Endless). Renderer pokazuje teraz napis rangi tylko przy awansie
   weterana (wartość efektu 1); symulacja bez zmian. Zrzut: `cj_phone_R_pl_3_alive.png`.
2. **Żywy dowódca pokazywał cenę** — przycisk był wyszarzony, ale z kwotą, jakby brakowało
   złota. Teraz podpis „W boju” (`comm145_active`), bez monety.
3. **Odnowienie bez liczby** — po śmierci widać było tylko pasek postępu; teraz klepsydra
   i pozostałe sekundy (`cj_*_4_cooldown.png`), bez alokacji (cache `costStr`).
4. **Dymek w potyczce/Endless mówił „Od 8. etapu…”** — poza kampanią dymek używa nowego
   tekstu bez wzmianki o etapie (`comm145_hint`, `cj_*_4b_skirmish.png`).
5. **Przycisk stopnia wieży**: etykieta „2: 66” bez kontekstu i nieprzetłumaczone „MAX”,
   ta sama strzałka co przycisk awansu epoki obok. Teraz „St. 2: 66” / „Tier 2: 66” /
   „Sv. 2: 66”… (`tier145_btn`), „Maks.” (`tier145_max`) i ikona szewronów jak przy
   stopniach jednostek (`cj_*_6_panel.png`).
6. **Dymek stopni nie mówił, gdzie kliknąć** („Stać cię na rozbudowę wieży.”) — nowy tekst
   „…otwórz Wieże i dotknij przycisku z szewronami.” (`tier145_hint`).
7. **„Co nowego” nie wspominało dowódcy ani stopni wież** — dopisany punkt `wn_145_battle`
   do linii 0.1 (`whatsnew106_phone720_automatic.png`).

Nowe teksty: wyłącznie `part145()` w `L.kt`, wszystkie 10 języków.

## Do decyzji właściciela

- W układzie trzyrzędowym (pion, Fold zewnętrzny) przycisk Wodza nie ma podpisu
  (tylko korona i koszt) — zgodnie z zaakceptowanym układem 125; dodanie plakietki
  „Wódz” wymagałoby zmiany układu 48 dp.
- Dymki dwuwierszowe na telefonie mają sporo pustego miejsca pod tekstem (wysokość
  liczona dla przycisku OK pod tekstem) — ogólna cecha `HintOverlay`, nie ruszałem.
- Napis rangi weterana rysowany jest na wysokości świata 360 i na telefonie 720p wchodzi
  na krawędź paska górnego — dotyczy awansu weterana sprzed 125, nie poprawiałem.
- Panel wież z 4 slotami na telefonie 720p zachodzi na pasek górny (modalny, istniejący).
- Przycisk awansu epoki wieży w stanie „maksymalna epoka” pokazuje sam znaczek ✓ bez
  podpisu (istniejące zachowanie).
- Punkt „Co nowego” dopisano do linii 0.1 (wersja jeszcze niewydana, `0.1.0-dev`); jeśli
  0.1 trafiło już do graczy, przenieść go do nowej linii.
