# R3: ocena „szybkiej bitwy” oraz Game Stats i Level Up (3.11.2026)

Krótka ocena dla koordynatora i właściciela, bez kodu. Baza: `integ/tura10` = 6119948.

## 1. Szybka bitwa 4–5 min

### Co już jest
- Potyczka ma ustawienia własne (`meta/SkirmishSetup.kt`): `startEra`, `maxEra`, `personality`,
  `powerPercent`, `modifierMask`. Ranking potyczki przyjmuje **tylko** bitwę od epoki 0 do
  ostatniej, bez modyfikatorów, z mocą z `PRESET_POWER` (`isRanked()`, serwer: `ReplayVerifier`
  dla `"skirmish"`). Bitwa z ustawieniami własnymi jest bez rankingu, ma mniejszą nagrodę
  (`SkirmishSetup.reward`) i jest zapisywana w historii.
- `Modifier.SHORT_DAY` kończy bitwę po **8 min** czasu symulacji (`Battle.SHORT_DAY_STEPS = 8*60*60`):
  wygrywa strona z większym procentem HP bazy (remis = obrona), bez presji oblężniczej.
- Długość bitwy wyznaczają głównie HP baz ×3 poza Endless (`BALANCE_BASE_HP_MUL`) i presja
  oblężnicza od 8. minuty (`SIEGE_START`, `SIEGE_ATTRITION`).

### Dwie drogi

| | A. Gotowy zestaw potyczki (bez zmian symulacji) | B. Nowy tryb/modyfikator „Szybka bitwa” |
|---|---|---|
| Co | Przycisk „Szybka bitwa” w potyczce ustawia np. `startEra = 3`, `maxEra = 5`, `SHORT_DAY`, moc 105% | Nowy `Modifier.QUICK` (dopisany na końcu): HP baz ×1 zamiast ×3, presja od 2. minuty, koniec po 5 min jak `SHORT_DAY` |
| Czas | Mediana nieznana. `SHORT_DAY` tnie dopiero po 8 min, więc **raczej 6–8 min, nie 4–5**. Wymaga pomiaru `BalanceReport` | Da się trafić w 4–5 min; trzeba stroić |
| Ranking | Brak, bo to ustawienia własne. Nagroda jak za potyczkę własną | Do decyzji: bez rankingu (najprościej) albo osobna tablica `quick` (lokalna, PGS i serwer: nowa tablica, nowe ID PGS, zmiana `ScoreClient`, `ReplayVerifier`) |
| PROTOCOL / złote powtórki | Bez zmian | PROTOCOL +1, złote powtórki, `BattleSnapshot.VERSION` bez zmian (modyfikator zapisany po ordinal; sprawdzić maskę), serwer akceptuje nowy modyfikator tylko poza rankingiem |
| Praca | S (UI + teksty + pomiar) | M (bez rankingu) / L (z rankingiem) |

### Ryzyka
- **Nagrody:** krótka bitwa z pełną nagrodą może stać się najszybszym źródłem medali i
  przyspieszyć kampanię. Nagroda powinna być liczona za minutę (jak `effort` w `settleRewards`)
  albo z limitem dziennym.
- **Balans epok:** start od epoki 3+ pomija naukę wcześniejszych epok, więc tryb ma sens dopiero
  po samouczku (np. odblokowany po etapie 4).
- **Rozstrzyganie po HP:** przy 5 min wynik częściej zależy od jednej wymiany ciosów. AI z
  `Ai.kt` gra na długą bitwę i może się źle zachowywać przy krótkim limicie (sprawdzić pomiarem
  lustrzanym `BalanceReport.mirrorReport`, 45–55%).
- **Misje i osiągnięcia** liczące bitwy/wygrane potyczki staną się łatwiejsze. Akceptowalne,
  ale do odnotowania.

### Rekomendacja
Najpierw **A** jako pomiar: 1–2 h pracy agenta plus raport z medianą czasu. Jeśli mediana
przekroczy 6 min, przejść do **B bez rankingu**. Osobna tablica rankingu dopiero wtedy, gdy
testerzy będą z trybu korzystać (raport testera, R2-04, pokaże użycie trybów).

## 2. Game Stats i program Level Up

### Co to daje
- **Game Stats** (PGS, API od wersji 22.0, w grze jest 22.1.0): skumulowane statystyki gracza
  na jego profilu Play Games (interfejs dla graczy od 09.2026). Napędzają funkcje Google Play:
  zadania (Quests), wyzwania społeczne, a w przyszłości ligi. Dla gry bez budżetu na marketing to
  **darmowa widoczność** w aplikacji Play Games. [Game Stats](https://developer.android.com/games/pgs/gamestats)
- **Level Up** (dobrowolny, zapisy od 1.09.2026): korzyści to głównie stawki opłat i promocja.
  Bez zakupów w grze stawki nic nie dają. Zostaje promocja i eligibility do Quests.
  [Wytyczne](https://developer.android.com/games/guidelines)

### Wymagania i koszt dla Century War

| Wymaganie | Stan | Koszt |
|---|---|---|
| PGS v2 przy starcie (LU-PA-GAA) | jest (gdy są ID) | — |
| ≥ 10 osiągnięć, ≥ 4 w pierwszej godzinie (LU-AC) | 74 osiągnięcia; pierwsza godzina do potwierdzenia testem (`FirstHourJourneyTest` już liczy) | S |
| Game Stats: ≥ 5 powtarzalnych + 1 postępu (LU-GS) | brak | M: zdarzenie `battle_completed` (tryb, wynik, zabici, fala, czas, gwiazdki) wysyłane po rozliczeniu bitwy + `progressUpdate` (`currentProgress` = najwyższy ukończony etap) przy starcie i po zmianie. Statystyki np. bitwy (COUNT), wygrane (COUNT z filtrem), zabici (SUM), fale Endless (SUM), najwyższa fala (MAX w jednym biegu), etapy z 3★ (COUNT z filtrem). Konfiguracja: ZIP z 3 CSV i ikonami w Play Console (właściciel), tłumaczenia nazw w CSV |
| Sidekick (LU-SK) | brak | S: przełącznik w konsoli dla AAB |
| Nagrody Play Games (LU-RE): 2 jednorazowe od 30.09.2026, 1 powtarzalna od 03.2027 | brak | M: np. sztandary z `BannerCatalog` jako nagroda (mechanizm odbioru z PGS) |
| Cloud save + konflikty (LU-CS) | jest | — |
| Duże ekrany, wcięcia (LU-LS) | jest (Fold) | — |
| Klawiatura, mysz, pad (LU-IC) i dystrybucja na PC (LU-PC) | **brak** | L: cała obsługa wejścia (`KeyEvent`, kursor, fokus przycisków); fokus pada mógłby korzystać z węzłów dostępności z D5 |
| Progi pamięci (LU-MM, od 02.2027) | spełnione wg szacunku | pomiar S |

**Wniosek:** Game Stats (M) i osiągnięcia w pierwszej godzinie (S) opłacają się nawet bez
pełnego Level Up, bo dają widoczność w Play Games. **Pełny Level Up** wymaga sterowania
klawiaturą, myszą i padem oraz wydania na PC (L). Nie przed premierą.

### Zmiany w Data safety i polityce
- Game Stats to **nowy przepływ do Google**: zdarzenia rozgrywki (tryb, wynik, liczby) powiązane
  z ID gracza PGS. W `docs/store/data_safety.md` w wierszu „Aktywność w aplikacji → Inne
  działania” trzeba dopisać statystyki gry (cel: funkcje aplikacji; zbierane po zalogowaniu do PGS).
  W polityce prywatności (PL/EN) dopisać zdanie o statystykach na profilu Play Games.
- Wymogi Google dla statystyk: bez danych osobowych, bez zależności od zakupów i reklam, dostępne
  dla wszystkich graczy. Projekt zdarzeń wyżej to spełnia (tylko liczby i tryb).
- ID statystyk z konsoli trafiają do `docs/store/play_games_ids.properties` jak rankingi, a
  `releaseChecklist` powinien je sprawdzać.

### Decyzje właściciela
1. Czy włączamy Game Stats przed premierą (wymaga konfiguracji ZIP w konsoli i aktualizacji Data safety).
2. Czy w ogóle celujemy w pełny Level Up (klawiatura, pad, PC). Rekomendacja: nie przed premierą.
3. Szybka bitwa: droga A (pomiar) teraz, B tylko po wynikach.
