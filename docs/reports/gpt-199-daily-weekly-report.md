# 199 — trudność wyzwań dnia i tygodnia

**POMIAR 28 DNI I ZESTAWÓW TYGODNIA JESZCZE TRWA — ZLECENIE NIEUKOŃCZONE.**

Baza `origin/main`: 7d847576e27bc5411bfd93877c1ba6df18af05cd, 6.11.2026. Gałąź `gpt/199-daily-weekly-report`. Kod produkcyjny i balans niezmienione.

Uruchomiono `BalanceReport.mirrorReport` (40 bitew na epokę 0, 4, 8): mediana odpowiednio 13,87; 13,66; 13,24 min, wygrane strony 0: 15/40, 22/40, 19/40; bez timeoutów. To pomiar lustrzany, nie wynik 28 oficjalnych dni. Wynik: BUILD SUCCESSFUL, 1m 25s.

`:sim:test` z filtrami DailyTest, WeeklyTest, DailyEvent: BUILD SUCCESSFUL, 26s.

Przygotowano osobny test pomiarowy. Zmodyfikowane pliki: `app/src/test/java/com/centurywar/game/DailyWeeklyBalance199Test.kt` i ten raport. Test 28 dni i 11 zestawów wymaga uruchomienia; liczby oraz ograniczenia zostaną dopisane po wykonaniu.
