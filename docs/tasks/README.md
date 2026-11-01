# Zlecenia dla zewnętrznych wykonawców

Każdy plik to samodzielne zlecenie (prompt ogólny + prompt zlecenia). Każde zlecenie ma własną gałąź `gpt/NN-*`; scalanie robi koordynator.

| Nr | Zlecenie | Gałąź | Zależności |
|---|---|---|---|
| 01 | [Google Play Games Services v2: rankingi globalne, osiągnięcia, zapis w chmurze](01-play-games.md) | `gpt/01-play-games` | — |
| 02 | [System osiągnięć (logika + dane, bez UI)](02-achievements.md) | `gpt/02-achievements` | — |
| 03 | [Misje dzienne i tygodniowe + seria logowań](03-missions.md) | `gpt/03-missions` | — |
| 04 | [Narzędzie balansu i strojenie krzywej kampanii](04-balance.md) | `gpt/04-balance` | lepiej po 05/06 (albo powtórzyć) |
| 05 | [Mądrzejsze AI i osobowości dowódców](05-ai-generals.md) | `gpt/05-ai-generals` | — |
| 06 | [System kontr między typami jednostek](06-counters.md) | `gpt/06-counters` | — |
| 07 | [Zapis komend gracza i odtwarzanie bitwy (anty-cheat)](07-replay-log.md) | `gpt/07-replay-log` | — |
| 08 | [Backend rankingu globalnego z weryfikacją powtórek](08-server.md) | `gpt/08-server` | najlepiej po 07 |
| 09 | [Proceduralna muzyka zmieniająca się z epokami](09-music.md) | `gpt/09-music` | — |
| 10 | [GitHub Actions: build, testy, artefakty APK](10-ci.md) | `gpt/10-ci` | — |
| 11 | [Konfiguracja wydania: AAB, R8, Baseline Profile, wersjonowanie](11-release-config.md) | `gpt/11-release-config` | — |
| 12 | [Materiały do Google Play: opis, polityka prywatności, plan monetyzacji](12-store-listing.md) | `gpt/12-store-listing` | — |
| 13 | [Oblężenie bez końca 2.0: karty roguelite i bossowie](13-endless-cards.md) | `gpt/13-endless-cards` | — |
| 14 | [Testy odporności: zapis profilu, migracje, przypadki brzegowe](14-tests-robustness.md) | `gpt/14-tests-robustness` | — |
| 15 | [Przypomnienie o wyzwaniu dnia (powiadomienia)](15-notifications.md) | `gpt/15-notifications` | — |
| 16 | [Ulepszenia jednostek w obrębie epoki](16-unit-upgrades.md) | `gpt/16-unit-upgrades` | — |
| 17 | [Nowa epoka: Epoka żelaza + szablon kolejnych](17-new-era.md) | `gpt/17-new-era` | po 16 lub równolegle |
| 18 | ~~Rozgałęzienia epok (Diesel/Tesla, Cyber/Bio)~~ – odrzucone, zastąpione przez 28 | `gpt/18-era-branches` (nie scalać) | — |
| 19 | [Liczba mnoga w tekstach (formy liczebników w 10 językach)](19-plural-forms.md) | `gpt/19-plural-forms` | — (dotyka `L.kt`, scalać przed 21 i 23 albo rozwiązać konflikty w `L.kt`) |
| 20 | [Aktualizacja materiałów sklepu i prywatności po Play Games i powiadomieniach](20-store-privacy-update.md) | `gpt/20-store-privacy-update` | po scaleniu int-meta (01, 15); ponowić po 23 |
| 21 | [Celowanie ostrzału dotykiem dla gracza](21-aimed-barrage.md) | `gpt/21-aimed-barrage` | — |
| 22 | [Wyraźne różnice osobowości AI z miarami](22-ai-personalities.md) | `gpt/22-ai-personalities` | po 16–18 (dotyka `Ai.kt`); przed 04 |
| 23 | [Integracja klienta z serwerem rankingu globalnego](23-global-ranking-client.md) | `gpt/23-global-ranking-client` | po int-infra (08); najlepiej po 16–18 i 22 (zmiany `:sim` zmieniają powtórki) |
| 24 | [Poprawki z przeglądu: configChanges locale i predictive back](24-back-locale-fixes.md) | `gpt/24-back-locale-fixes` | — |
| 25 | [Animacja trafień i śmierci](25-hit-death-anim.md) | `gpt/25-hit-death-anim` | najlepiej po 27 (wspólne `drawUnit`, `drawUnitPose`, `Machines.kt`) |
| 26 | [Pociski, ostrzały, eksplozje i potrząsanie ekranem](26-projectile-fx.md) | `gpt/26-projectile-fx` | po 21 (`worldX` i wstrząs w `BattleRenderer`); po 25 (cząsteczki w `consumeFx`) |
| 27 | [Płynny ruch maszyn i zwierząt](27-machine-motion.md) | `gpt/27-machine-motion` | — (pierwsze z 25–27) |
| 28 | [Warianty epok (od nowa, zamiast 18)](28-era-variants.md) | `gpt/28-era-variants` | po 19, 21, 22 i 25–27; przed 23 i ponownym 04 |
| 29 | [Odtwarzacz powtórek bitew](29-replay-viewer.md) | `gpt/29-replay-viewer` | najlepiej po 23 (oba zmieniają `BattleScreen.onFinished`); wydziela `BattleSfx` używane przez 38 |
| 30 | [Historia bitew i ekran statystyk](30-battle-history.md) | `gpt/30-battle-history` | po 29 (przycisk „Powtórka” w szczegółach; bez 29 pominąć), po 19 (formy „%d dni temu”) |
| 31 | [Dostępność: palety barw, rozmiar tekstu, mniej potrząsania i błysków](31-accessibility.md) | `gpt/31-accessibility` | po 25–27 (renderer, `Pen`, efekty muszą czytać flagi); przed 32 i 38 (`SettingsScreen`) |
| 32 | [Podpowiedzi kontekstowe i powtórka samouczka](32-contextual-hints.md) | `gpt/32-contextual-hints` | po 21 (`TutorialOverlay`), po 31 (układ ustawień) |
| 33 | [Pogoda i pora dnia na polu bitwy](33-weather.md) | `gpt/33-weather` | po 26 i 28 (`BattleRenderer`, `Battle.kt`, `Modes.kt`); przed 35 i 36 (korzystają z `Weather`) |
| 34 | [Oblężenie bez końca: nowi bossowie i tematy fal](34-endless-bosses.md) | `gpt/34-endless-bosses` | po 25 i 27 (rysowanie jednostek), po 33 (kolejka `Battle.kt`/`BattleSnapshot`) |
| 35 | [Potyczka konfigurowalna](35-custom-skirmish.md) | `gpt/35-custom-skirmish` | po 22 (opisy osobowości), 23 (ranking online potyczki) i 33 (wybór pogody; bez 33 sekcja pominięta) |
| 36 | [Oblężenie tygodnia z progami nagród](36-weekly-siege.md) | `gpt/36-weekly-siege` | po 33 i 34 (pogoda, bossowie w konfiguracji tygodnia); część serwerowa po 08/23 |
| 37 | [Wydajność: budżet klatki, auto-jakość, testy kroków/s i alokacji](37-performance-budget.md) | `gpt/37-performance-budget` | na końcu (po 25–36): testy identyczności i wydajności mierzą gotową symulację i renderer |
| 38 | [Dźwięki epok i miks z muzyką](38-era-sound.md) | `gpt/38-era-sound` | po 26 (`FxBuffer`, `playSounds`), 29 (`BattleSfx`), 31/32 (`SettingsScreen`) |
| 39 | [Licencje, ekran „O grze” i porządek w dokumentach sklepu](39-licenses-about.md) | `gpt/39-licenses-about` | fala 1 |
| 40 | [Audyt układu ekranów: Fold w pionie, długie teksty, 130% tekstu](40-layout-audit.md) | `gpt/40-layout-audit` | fala 2 (po 39: nowy ekran „O grze”) |
| 41 | [Auto-jakość także dla telefonów ograniczonych przez GPU](41-gpu-quality.md) | `gpt/41-gpu-quality` | fala 1 |
| 42 | [Balans końca kampanii i odstających etapów (20 seedów)](42-campaign-endgame-balance.md) | `gpt/42-campaign-endgame-balance` | fala 1 |
| 43 | [Serwer rankingu: gotowość do wdrożenia i usuwanie danych gracza](43-server-deploy.md) | `gpt/43-server-deploy` | fala 2 (po 39: wiersz ustawień) |
| 44 | [Przegląd jakości tłumaczeń w 10 językach](44-translation-review.md) | `gpt/44-translation-review` | fala 3 (cały `L.kt`, na końcu) |
| 45 | [Odtwarzacz powtórek: płynność i poprawki z przeglądu](45-replay-polish.md) | `gpt/45-replay-polish` | fala 1 |
| 46 | [Oblężenie bez końca: poprawki bossów, tematów fal i wariantów wroga](46-endless-fixes.md) | `gpt/46-endless-fixes` | fala 1 |
| 47 | [Ekran bitwy bez alokacji: HUD, panele i wyniki](47-battle-hud-alloc.md) | `gpt/47-battle-hud-alloc` | fala 2 |
| 48 | [Dźwięk: priorytety wyciszania muzyki i brak gubienia pierwszych dźwięków](48-audio-polish.md) | `gpt/48-audio-polish` | fala 2 (po 52: `Sound.kt`) |
| 49 | [Stabilność: brak I/O na wątku UI, StrictMode i odporność na przerwanie](49-stability-io.md) | `gpt/49-stability-io` | fala 2 |
| 50 | [Grafiki do Google Play generowane z gry](50-store-graphics.md) | `gpt/50-store-graphics` | fala 3 (po 40) |
| 51 | [Dzień wydania i lista kontrolna przed publikacją](51-release-day.md) | `gpt/51-release-day` | fala 3 (po 42: `Modes.kt`) |
| 52 | [Lepsze dźwięki: nowa synteza efektów bitwy i interfejsu](52-better-sfx.md) | `gpt/52-better-sfx` | fala 1 |
| 53 | [Opisy przycisków bitwy po przytrzymaniu](53-hud-tooltips.md) | `gpt/53-hud-tooltips` | tura 4 (równolegle) |
| 54 | [Samouczek: zbrojownia, warianty, pogoda i celowanie ostrzału](54-tutorial-update.md) | `gpt/54-tutorial-update` | tura 4 (równolegle) |
| 55 | [Lepsza muzyka proceduralna epok](55-better-music.md) | `gpt/55-better-music` | tura 4 (równolegle) |
| 56 | [Tła epok na polu bitwy](56-era-backdrops.md) | `gpt/56-era-backdrops` | tura 4 (równolegle) |
| 57 | [Ożywione menu główne i ekran tytułowy](57-main-menu.md) | `gpt/57-main-menu` | tura 4 (równolegle) |
| 58 | [Ikona aplikacji i ekran startowy](58-app-icon-splash.md) | `gpt/58-app-icon-splash` | tura 4 (równolegle) |
| 59 | [Mapa kampanii](59-campaign-map.md) | `gpt/59-campaign-map` | tura 4 (równolegle) |
| 60 | [Nowe osiągnięcia za nowe tryby i mechaniki](60-new-achievements.md) | `gpt/60-new-achievements` | tura 4 (równolegle) |
| 61 | [Nowe typy misji dziennych i tygodniowych](61-new-missions.md) | `gpt/61-new-missions` | tura 4 (równolegle) |
| 62 | [Kodeks: pogoda, warianty epok, bossowie i karty](62-codex-update.md) | `gpt/62-codex-update` | tura 4 (równolegle) |
| 63 | [Haptyka: czytelne wibracje zdarzeń](63-haptics.md) | `gpt/63-haptics` | tura 4 (równolegle) |
| 64 | [Serwer rankingu: moderacja nicków i narzędzia administratora](64-server-moderation.md) | `gpt/64-server-moderation` | tura 4 (równolegle) |
| 65 | [Lokalny dziennik błędów i zgłaszanie problemu](65-error-log.md) | `gpt/65-error-log` | tura 4 (równolegle) |
| 66 | [Wersja release: R8, rozmiar, profil startowy](66-release-build-check.md) | `gpt/66-release-build-check` | tura 4 (równolegle) |
| 67 | [Pauza z szybkimi ustawieniami i podsumowaniem bitwy](67-pause-quick-settings.md) | `gpt/67-pause-quick-settings` | tura 4 (równolegle) |
| 68 | [Zapowiedzi epok: krótkie plansze fabularne](68-era-intros.md) | `gpt/68-era-intros` | tura 5 |
| 69 | [Cztery nowe doktryny](69-new-doctrines.md) | `gpt/69-new-doctrines` | tura 5 |
| 70 | [Cztery nowe modyfikatory bitew](70-new-modifiers.md) | `gpt/70-new-modifiers` | tura 5 |
| 71 | [Nowe karty oblężenia bez końca](71-endless-cards-2.md) | `gpt/71-endless-cards-2` | tura 5 |
| 72 | [Wyzwanie dnia: dni specjalne](72-daily-events.md) | `gpt/72-daily-events` | tura 5 |
| 73 | [Udostępnianie i import powtórek](73-replay-share.md) | `gpt/73-replay-share` | tura 5 |
| 74 | [Przegląd układu nowych ekranów (53–67) i menu](74-layout-audit-2.md) | `gpt/74-layout-audit-2` | tura 5 |
| 75 | [Odświeżenie karty sklepu po nowych funkcjach](75-store-refresh.md) | `gpt/75-store-refresh` | tura 5 |
| 76 | [Szybsze testy i CI](76-ci-speed.md) | `gpt/76-ci-speed` | tura 5 |
| 77 | [Wydajność nowej oprawy na słabych telefonach](77-lowend-perf-2.md) | `gpt/77-lowend-perf-2` | tura 5 |
| 78 | [Przegląd tłumaczeń z tury 4 (53–67)](78-translation-pass-2.md) | `gpt/78-translation-pass-2` | tura 5 (na końcu) |
| 79 | [Dopracowanie wyglądu maszyn wariantów epok](79-variant-art.md) | `gpt/79-variant-art` | tura 5 |
| 80 | [Serwer rankingu: własność gracza, publiczne ID i limity zapytań](80-server-identity.md) | `gpt/80-server-identity` | tura 6 (audyt) |
| 81 | [Weryfikacja wyników: północ, długie bitwy, samouczek, badania, wersja](81-server-verify-rules.md) | `gpt/81-server-verify-rules` | tura 6 (audyt) |
| 82 | [Balans symulacji: limity odnowienia, karty bez efektu, kumulacja](82-sim-balance-caps.md) | `gpt/82-sim-balance-caps` | tura 6 (audyt) |
| 83 | [Wydanie: podpis, dokumenty sklepu bez luk](83-release-hardening.md) | `gpt/83-release-hardening` | tura 6 (audyt) |
| 84 | [Ekran bitwy i wyniki: czytelność, cele dotyku, informacja po porażce](84-battle-ux.md) | `gpt/84-battle-ux` | tura 6 (audyt) |
| 85 | [Samouczek: pewne pomijanie i panel nie zasłania celu](85-tutorial-ux.md) | `gpt/85-tutorial-ux` | tura 6 (audyt) |
| 86 | [Podpowiedzi: mniejsze i obok celu](86-hint-layout.md) | `gpt/86-hint-layout` | tura 6 (audyt) |
| 87 | [Menu główne i rankingi: odznaki, porządek, bez tekstów technicznych](87-menu-rankings-ux.md) | `gpt/87-menu-rankings-ux` | tura 6 (audyt) |
| 88 | [Poprawki tłumaczeń z audytu](88-l10n-fixes-3.md) | `gpt/88-l10n-fixes-3` | tura 6 (audyt) |
| 89 | [Ustawienia, potyczka i kodeks: porządek i przewijanie](89-settings-codex-ux.md) | `gpt/89-settings-codex-ux` | tura 6 (audyt) |
| 90 | [Odczucia: odbiór nagród i drobne optymalizacje rysowania](90-feedback-perf.md) | `gpt/90-feedback-perf` | tura 6 (audyt) |
| 91 | [Siatka bezpieczeństwa: złote powtórki i testy symulacji bez Androida](91-golden-tests.md) | `gpt/91-golden-tests` | tura 6 (audyt) |

**Konflikty:** zlecenia 05, 06, 07, 13, 16, 17 i 18 dotykają `game/Battle.kt` lub `game/Ai.kt` i będą scalane po kolei. Najlepsza kolejność: niezależne (01–03, 08–12, 14, 15) równolegle; potem 05 → 06 → 07 → 13 → 16 → 17 → 18; balans (04) na końcu albo po każdej większej zmianie.

Zlecenia 19, 21, 23 dotykają `engine/L.kt`; 21, 23 dotykają `screens/BattleScreen.kt`; 22 dotyka `sim/.../Ai.kt` (kolejka z 05–18); 23 zmienia `:sim` i `:server` (każda późniejsza zmiana balansu wymaga podbicia `CW_GAME_VERSION`). Niezależne: 20, 24.

Zlecenia 25, 26, 27 są czysto wizualne (bez zmian `BattleSnapshot.VERSION` i `Replay.VERSION`; 25 dodaje w `Fighter` tylko pola tylko-wizualne `hitStep`/`hitPow`), ale wszystkie dotykają `game/BattleRenderer.kt` i `ArtGalleryTest.kt`, a 25 i 27 także `art/Machines.kt` i `art/Soldiers.kt`. Można je robić równolegle, scalać po kolei: 27 → 25 → 26. 26 dotyka też `engine/L.kt`, `meta/Profile.kt` i `screens/SettingsScreen.kt` (konflikty z 19, 21, 23 w `L.kt`) oraz miejsca wstrząsu ekranu, z którego korzysta celowanie z 21 (`worldX`), więc scalać po 21.

Zlecenie 28 zastępuje 18 i dotyka prawie wszystkiego w `:sim` (`Defs.kt`, `Battle.kt`, `Ai.kt`, `BattleController.kt`, `Replay.kt`, `BattleSnapshot.kt`, `Modes.kt`) oraz `BattleScreen.kt`, `CodexScreen.kt`, `L.kt`, renderera i `art/*`. Kolejność: 19 → 21 → 22 → (25–27) → 28 → 23 → 04. Podbija `Replay.VERSION` (3 → 4) i `BattleSnapshot.VERSION` (7 → 8); serwer rankingu wymaga potem podbicia `CW_GAME_VERSION`. Jeśli 23 zostanie scalone wcześniej, 28 musi dodatkowo przejść `:server:test`.

Zlecenia 29–38 prawie wszystkie dotykają `engine/L.kt` (poza 37) i `meta/Profile.kt` (29, 30, 31, 32, 35, 36, 38). `screens/BattleScreen.kt`: `onFinished` zmieniają 29, 30, 35, 36; HUD 33, 34, 37; `playSounds` 29 i 38. `game/BattleRenderer.kt`: 31, 33, 34, 37 (oraz 25–27). `sim/.../Battle.kt`: 33, 34, 36, 37 i addytywnie 38 (`FxBuffer`); `BattleSnapshot.kt` podbijają 33, 34, 36 (każde osobno, scalać po kolei i pilnować jednej rosnącej `VERSION`), 29 tylko udostępnia kodek konfiguracji. `sim/.../Modes.kt`: 33, 36, opcjonalnie 35 (oraz 28). `SettingsScreen.kt`: 31, 32, 38. `ModeScreen.kt`: 32, 33, 35, 36. `RankingsScreen.kt`: 29, 30, 36, 37. `CodexScreen.kt`: 33, 34. Zmiany przebiegu symulacji w 33, 34, 36 wymagają podbicia `CW_GAME_VERSION`, jeśli 23 jest już scalone. Najlepsza kolejność: 29 → 30 równolegle z 31 → 32 → 38 i z kolejką symulacji 33 → 34 → 36 → 35; 37 na końcu.

Kolejność wysyłki i polecenia startowe: [DISPATCH.md](DISPATCH.md).


Zlecenia 39–52 (tura 3) są pogrupowane w fale o rozłącznych plikach; opis w [DISPATCH.md](DISPATCH.md).
