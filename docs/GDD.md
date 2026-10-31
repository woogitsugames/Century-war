# Century War: dokument projektowy (GDD)

## 1. Kierunek gry

**Gatunek:** strategia „wojna na jednym pasie przez epoki” (lane battler / evolution war), jak w Age of War. Projekt jest jednak **własny**: nazwy, jednostki, grafika, mechaniki i balans są oryginalne.

**Główna różnica wobec Age of War:** AoW to jeden mecz na 15–30 minut bez żadnego postępu. Century War to **krótkie bitwy (5–15 min) plus metaprogresja na tygodnie**: kampania, badania, rekordy, wyzwanie dnia i rankingi.

**Pętla rozgrywki:**
1. Bitwa: szkolisz jednostki za złoto, zabijasz wrogów (złoto i XP), awansujesz epokę, burzysz bazę wroga.
2. Po bitwie dostajesz medale, także za porażkę.
3. Medale wydajesz na badania (trwałe ulepszenia).
4. Wracasz na trudniejszy etap, po więcej gwiazdek albo po lepszy wynik w rankingu.

**Styl graficzny:** płaski cartoon z grubym konturem, rysowany wektorowo kodem. Jest czytelny na małym ekranie, ostry na Foldzie, a APK waży ok. 100 KB (release). Docelowo można podmienić renderer jednostek na ręcznie rysowane sprite'y w tym samym stylu.

## 2. Zawartość

| Element | Ilość |
|---|---|
| Epoki | **9**: Plemiona, Epoka brązu, Epoka żelaza, Feudalizm, Epoka prochu, Epoka pary, Epoka stali, Epoka krzemu, Era orbitalna |
| Jednostki | **36** (4 na epokę: wręcz, dystansowa, ciężka, elitarna) |
| Wieże | **9** typów (1 na epokę), do 4 slotów, kupno, ulepszenie i sprzedaż |
| Ostrzały specjalne | **9** wariantów (1 na epokę) plus Zryw (+40% tempa armii) |
| Doktryny | **9**: wybór 1 z 3 przy każdym awansie epoki poza Endless |
| Karty Endless | **30**: 9 doktryn i 21 nowych efektów, cztery rzadkości |
| Modyfikatory bitew | **10**, np. Mgła wojny, Kruche bazy, Horda strzelców |
| Etapy kampanii | **36** (4 na epokę), po 3 gwiazdki = **108 gwiazdek** |
| Badania | **13** drzew, łącznie **110 poziomów** |
| Tryby | Kampania, Oblężenie bez końca, Wyzwanie dnia, Potyczka (4 poziomy trudności) |
| Rankingi | 4 tablice: fale oblężenia, wyzwanie dnia, gwiazdki kampanii, potyczka |

### Kampania (36 etapów)
- Każdy etap ma ograniczony zakres epok (np. etap 7: Brąz → Epoka żelaza), więc bitwa trwa kilka minut.
- Co 4. etap to „wielki” etap przez 2–3 epoki.
- Siła wroga to jawny mnożnik złota i XP AI, widoczny przed bitwą: od 72% na początku do ok. 370% w ostatnich etapach. Druga połowa kampanii zakłada rozwinięte badania (strojenie w `docs/BALANCE.md`).
- Gwiazdki zależą od HP bazy po zwycięstwie: ≥75% = ★★★, ≥40% = ★★.
- Od etapu 3 bitwy dostają modyfikatory, więc każdy etap gra się inaczej.

### Oblężenie bez końca
- Fale co ~16–28 s. Co 6 fal wróg wchodzi w nową epokę, a od fali 48 jednostki wroga stają się coraz silniejsze.
- Co 5 fal gracz wybiera jedną z trzech kart (30 rodzajów, cztery rzadkości); przy awansie epoki nie ma już osobnego wyboru doktryny.
- Fale tematyczne są zapowiadane przed pojawieniem się; wśród ośmiu tematów są tabor oblężniczy (+15% budżetu, wolniejszy) i harcownicy (+30% budżetu, -20% HP). Co 10 fal przychodzi boss z pięciu typów (odrzut, aura tempa, tarczownik, werbownik, szał) w stałej rotacji; od fali 60 zyskuje drugą zdolność.
- Pokonanie bossa zapewnia dodatkową kartę.
- Wynik = fale × 250 + zabici × 12 + czas / 2 + pokonani bossowie × 1500.
- Po bardzo długim oblężeniu narasta presja niszcząca bazę, żeby pojedynek nie trwał bez końca.

### Wyzwanie dnia
- Ten sam seed, epoki i modyfikatory dla każdego gracza na świecie (dzień UTC).
- Badania są wyłączone, wszyscy mają te same bonusy, więc ranking jest uczciwy.
- 1 podejście rankingowe dziennie, kolejne to trening.

## 3. Mechaniki bitwy

- **Ekonomia:** pasywny przychód rośnie z epoką. **Kontrola terytorium** daje od 70% do 130% przychodu zależnie od położenia linii frontu, więc opłaca się naciskać.
- **XP:** za zabójstwa, za obrażenia zadane bazie, za straty własne (mniej) oraz mały stały przyrost.
- **Awans epoki** zużywa XP. HP bazy rośnie proporcjonalnie i dostaje +10%.
- **Rangi weteranów:** w ostatniej dostępnej epoce nadwyżka XP daje rangi (+6% obrażeń i HP za rangę).
- **Zbrojownia:** złoto w bitwie zamieniane na +8% obrażeń i HP. Przewaga ekonomiczna nie przepada, gdy armia jest pełna.
- **Presja oblężnicza:** po 8 minutach obrażenia zadawane bazom rosną o 20% na minutę, a blokada stopniowo uszczupla HP baz zależnie od pozycji frontu, nawet bez trafień. Koniec z wiecznym patem. Nie dotyczy trybu bez końca.
- **Postawa:** Naprzód albo Trzymaj (jednostki czekają na linii obrony).
- **Kolejka:** 5 jednostek. Limit armii: 12, z badaniami do 18.
- **Uczciwe AI:** AI gra według tych samych zasad co gracz. Trudność to wyłącznie jawny mnożnik. Po porażce gra podpowiada, dlaczego przegrałeś.

## 4. Metaprogresja (badania za medale)

| Badanie | Poziomy | Efekt na poziom |
|---|---|---|
| Doktryna elit | 1 | odblokowuje jednostki elitarne |
| Okrzyk bojowy | 1 | odblokowuje Zryw |
| Handel | 12 | +6% przychodu |
| Kowalstwo | 15 | +4% obrażeń |
| Pancerze | 15 | +4% HP |
| Skarbiec | 10 | +40 złota na start |
| Fortyfikacje | 10 | +8% HP bazy |
| Balistyka | 10 | +6% obrażeń wież |
| Musztra | 10 | +5% szybkości szkolenia |
| Kroniki | 10 | +5% XP |
| Koszary | 6 | +1 limit armii |
| Sztab | 8 | −5% odnowienia zdolności |
| Inżynieria | 2 | +1 slot wieży |

Koszty rosną wykładniczo (×1,33–1,6 na poziom), więc pełne drzewko to wiele tygodni regularnej gry.

## 5. Technologia

- **Kotlin, Android SDK 36** (targetSdk/compileSdk 36, minSdk 26 = Android 8.0+). Bez bibliotek: brak AndroidX i silników, więc szybki start i mały APK.
- **Własna pętla gry** na `SurfaceView` z `lockHardwareCanvas()` (rysowanie przez GPU), tempo zgodne z ekranem (60/90/120 Hz) i opcjonalny limit 30/60 FPS.
- **Stały krok symulacji 60 Hz**, deterministyczny (seed). Ta sama bitwa wychodzi identycznie na każdym telefonie, co jest podstawą pod anty-cheat rankingu.
- **Symulacja w czystym Kotlinie** (bez Androida) jest testowana w JUnit: AI kontra AI przez wszystkie 36 etapów.
- **Optymalizacja na tanie telefony:** auto-wykrywanie klasy urządzenia (RAM, rdzenie). Na słabych telefonach bufor renderowany w 60–80% rozdzielczości (skalowanie sprzętowe), mniej cząsteczek, bez antyaliasingu i paralaksy. Dynamiczne obniżanie jakości przy spadkach FPS. Zero alokacji w pętli (pule obiektów).
- **Flagowce i Foldy:** pełna rozdzielczość (limit ~3,9 MPx bufora). `configChanges` obsługuje składanie i rozkładanie bez restartu gry. Układ HUD i kamera dopasowują się do proporcji 4:3…21:9. Obsługiwane wycięcia ekranu, `appCategory="game"` (Android 16 nie wymusza wtedy obrotu na dużych ekranach) oraz predictive back.
- **Zapis:** JSON w pamięci aplikacji, zapis atomowy.
- **Rankingi:** lokalne tablice już działają. Ranking globalny wymaga konfiguracji Google Play Games Services w Play Console (interfejs `OnlineLeaderboards` jest gotowy, opis w kodzie).

## 6. Plan dalszych prac

1. Ranking globalny: Google Play Games Services (Leaderboards + Achievements) i zapis w chmurze.
2. Dźwięk i muzyka (SFX dla każdej epoki, muzyka zmieniana z epoką).
3. Osiągnięcia oraz dzienne i tygodniowe misje.
4. Bohater/dowódca na polu bitwy (jednostka z poziomami między bitwami).
5. Asynchroniczne PvP: obrona gracza sterowana przez AI, ligi sezonowe.
6. Kosmetyka bez wpływu na balans (skórki epok, sztandary).
7. Grafika: ręcznie rysowane sprite'y w obecnym stylu, animacje śmierci i trafień.
8. Monetyzacja przyjazna graczom: reklamy tylko z nagrodą, jednorazowe „bez reklam”, zero pay-to-win.

## 7. Rozwój zawartości w czasie (aktualizacje)

1. **Ulepszenia jednostek w epoce:** każda jednostka ma 2–3 stopnie ulepszenia kupowane w bitwie (statystyki i detale wyglądu). Decyzja: awansować epokę czy wzmocnić obecną armię.
2. **Nowe epoki pomiędzy istniejącymi** (jedna na aktualizację): Renesans (między feudalizmem a prochem), Epoka atomowa (między stalą a krzemem), Era galaktyczna (po orbitalnej). Docelowo 12 epok i 48 etapów kampanii.
   - Technicznie: `Defs` jest sterowane danymi (`EraSpec` na epokę). Trzeba dopisać jednostki, wieżę, ostrzał, grafikę (`game/art/*`) i przestroić balans testem `BattleSimTest`/`BalanceReport`. Instrukcja: `docs/ADDING_ERA.md`.
   - Zapis gracza: `Profile.stars` dopasowuje się do liczby etapów. Przy wstawianiu epok w środek trzeba zmapować stare gwiazdki na nowe numery etapów (migracja profilu).
3. **Rozgałęzienia epok:** przy awansie wybór jednej z dwóch wersji następnej epoki (np. Para: Diesel / Tesla).
