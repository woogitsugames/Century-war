# Dzień 4 — zegar przy presji na Foldzie i przegląd pierwszej godziny po dniach 1–3

## A. Zegar bitwy na zewnętrznym ekranie Folda (zatwierdzone)

Gdy działa presja oblężnicza, a jej napis jest za mały pod minimapą (zewnętrzny Fold
904×2316), napis presji stoi w bocznym polu pod liczebnością armii. Wcześniej pod minimapą
było wtedy pusto — teraz wraca tam zegar bitwy, jak bez presji. Inne ekrany bez zmian
(pod minimapą dalej napis presji), układ dwurzędowego paska bez zmian.

- Kod: `BattleTopBar.kt` (zegar wydzielony do `drawClockUnderMini`).
- Testy w `Day2UxTest`: `foldOuterSiegeKeepsClockUnderMini` (10 języków, prawo- i leworęczny
  układ, 5:00 i 12:34: zegar co najmniej 10 sp, pod minimapą, na jej środku, bez nachodzenia
  na inne napisy paska) i `otherDevicesShowSiegeInsteadOfClock` (telefony i wewnętrzny Fold).
- Zrzuty: `app/build/screenshots/day2_day4_foldOuter_siege_clock_{pl,en,de}.png`.

## B. Przegląd pierwszej godziny

Sprawdzone (zrzuty i dzienniki testów): `FirstHourJourney*` (telefon 1600×720 i zewnętrzny
Fold; pl, en, de — samouczek, etapy 2–8, badania, potyczka, Endless, wyzwanie dnia,
„Co nowego”), `PrereleasePolish*` (wszystkie ekrany na 1600×720, 2400×1080, 904×2316,
2184×1968; pl, en, de), `NewPlayerJourneyTest`, `Day2UxTest` (wyniki z medalami
„Za bitwę”/„Za osiągnięcia”, przycisk „Co nowego” z kropką). Przeczytane teksty part175/176
we wszystkich językach.

Wszystkie ścieżki przechodzą bez zgłoszonych problemów. Wyniki: medale za bitwę i za
osiągnięcia czytelnie rozdzielone na każdym ekranie, nic nie nachodzi na przyciski. Przycisk
„Co nowego” z czerwoną kropką mieści się na wszystkich czterech ekranach. Krótsze etapy:
etap 2 trwa ok. 7–8 min, etap 8 ok. 13 min, presja pojawia się w trakcie i jest czytelna.
W nowych tekstach nie znalazłem błędów.

### Poprawione

| Problem | Gdzie | Poprawka |
|---|---|---|
| Na telefonie poziomo (720p, a po niemiecku też 20:9) najnowszy wpis historii zmian (osiągnięcia i misje dowódcy z dnia 3) jest pod dolną krawędzią panelu, a nic nie mówi, że można przewinąć. Gracz, który wszedł przez „Co nowego”, nie widzi akurat tej nowości. | `WhatsNewScreen` | Złota strzałka przewijania (ta sama co w Kodeksie i Ustawieniach) przy dolnej krawędzi listy, gdy niżej jest coś więcej |

### Do decyzji właściciela

1. **Kolejność wpisów w „Co nowego”.** Wpisy w wersji 0.1 idą od najstarszego do najnowszego,
   więc najświeższa nowość jest na końcu (na telefonie poziomo trzeba przewinąć). Można
   odwrócić kolejność (najnowsze na górze) — to drobna zmiana w `WhatsNewCatalog`.
2. **Nazwa ekranu.** Przycisk w menu nazywa się „Co nowego” (de „Neuigkeiten”), a ekran, który
   otwiera, ma tytuł „Historia zmian” (de „Änderungsverlauf”). Można dać tytuł „Co nowego”,
   gdy wchodzi się z menu, a „Historia zmian” zostawić dla wejścia z „O grze”.
3. **Znane z tury 10, bez zmian:** zakładki Kodeksu częściowo schowane za krawędzią listwy
   (przewijana listwa), drobny napis „Odstęp 2 / 3” (7 dp) w porównaniu jednostek w Kodeksie
   na zewnętrznym Foldzie, a po niemiecku nachodzi tam na „0.9 s / 0.7 s”; przyciski pauzy
   z napisem 8 dp na zewnętrznym Foldzie. Wszystkie opisane w dzienniku `pre10_audit_*`.
4. Po samouczku na mapie kampanii baner „Osiągnięcie odblokowane…” na chwilę zasłania nagłówek
   pierwszej epoki (przy dymku z podpowiedzią). Nic nie blokuje, znika sam.
