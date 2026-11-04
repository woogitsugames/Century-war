# R6-01: osiągnięcia pierwszej godziny (Play Games LU-AC-GAC)

Gałąź `claude/dzien-11-osiagniecia`. Zatwierdzone przez właściciela 3.11: cztery nowe osiągnięcia
zwykłe (bez paska postępu), razem 50 medali. Katalog: **78 osiągnięć, 83 ID Play Games**.

| id | metryka | próg | medale | kategoria | nazwa (pl / en) |
|---|---|---|---|---|---|
| `first_evolve` | EVOLUTIONS | 1 | 10 | TACTICS | Nowa epoka / New Era |
| `first_turret` | TURRETS_BUILT | 1 | 10 | TACTICS | Pierwsza wieża / First Turret |
| `first_research` | RESEARCH_LEVELS | 1 | 10 | RESEARCH | Pierwsze badanie / First Research |
| `stages_3` | STAGES | 3 | 20 | CAMPAIGN | Trzy etapy / Three Stages |

Teksty w `part185()` (10 języków) i wpis „Co nowego” `wn_185_achievements`. Arkusz
`docs/store/play_games_sheet.csv` i import `docs/store/pgs_import/` odświeżone przez
`tools/pgs_sheet.py` (`--check`: 78 osiągnięć, 83 ID, 1495 punktów).

## Odblokowania w teście pierwszej godziny (czas gracza, mm:ss)

Pula: `win_1`, `kills_100`, `first_evolve`, `first_turret`, `first_research`, `stages_3`.
Asercja: ≥ 4 z puli przed 60:00. Wynik: **6 z 6 w każdym przejściu**. Czasy różnią się między
uruchomieniami o kilka minut (przebieg AI); w wariancie ostrożnym druga próba etapu 2 też bywa
przegrana i wtedy gra proponuje badanie (R2-02).

| osiągnięcie | telefon pl | telefon en | Fold pl | Fold en | ostrożny (telefon pl) |
|---|---|---|---|---|---|
| win_1 | 11:16 | 10:13 | 08:49 | 08:43 | 10:51 |
| kills_100 | 11:16 | 10:13 | 08:49 | 08:43 | 10:51 |
| first_evolve | 11:16 | 10:13 | 08:49 | 08:43 | 10:51 |
| first_turret | 11:16 | 10:13 | 08:49 | 08:43 | 10:51 |
| first_research | 19:37 | 18:31 | 16:44 | 16:57 | 36:32 |
| stages_3 | 35:15 | 34:35 | 24:10 | 24:10 | 36:20 |

Wariant ostrożny (`FirstHourJourneyTest.firstHourCautious`): samouczek pominięty od razu,
pierwsza próba etapu 2 to kapitulacja po 75 s (liczy się do serii porażek), badania dopiero po 30. minucie. AI gracza i tak
awansuje epokę i buduje wieżę w etapie 1, więc `first_evolve` i `first_turret` przychodzą razem
z pierwszą wygraną. Bez `first_research` byłoby 5 przed 60:00.
Osie czasu: `app/build/screenshots/fh_<urządzenie>_<język>[_ostrozny]_os_czasu.txt`.

## Zmiany przy okazji (wykryte przez test)

- Pierwszy zakup w Badaniach odblokowuje `first_research`, a baner pojawiał się nad dymkiem
  podpowiedzi Badań. `ResearchScreen.blocksNotices` = dymek widoczny: baner czeka w kolejce.
- Po pierwszej bitwie wpadają naraz 4 osiągnięcia (4 × 2,9 s banerów). Gdy jest ich więcej
  niż dwa, pierwsze ma własny baner, a reszta jeden wspólny „Kolejne nowe osiągnięcia: 3 (+45)”
  (`ach185_more`), razem ok. 6 s. Test: `RewardFeedback90Test.manyAchievementsShareOneBanner`.

## Ekonomia

+50 medali jednorazowo; przy 196–356 medalach w pierwszej godzinie to ok. +10–15%
(`docs/BALANCE.md`). Bez strojenia.
