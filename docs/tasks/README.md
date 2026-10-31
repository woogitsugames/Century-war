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
| 18 | [Rozgałęzienia epok (Diesel/Tesla, Cyber/Bio)](18-era-branches.md) | `gpt/18-era-branches` | po 17 |

**Konflikty:** zlecenia 05, 06, 07, 13, 16, 17 i 18 dotykają `game/Battle.kt` lub `game/Ai.kt` i będą scalane po kolei. Najlepsza kolejność: niezależne (01–03, 08–12, 14, 15) równolegle; potem 05 → 06 → 07 → 13 → 16 → 17 → 18; balans (04) na końcu albo po każdej większej zmianie.
