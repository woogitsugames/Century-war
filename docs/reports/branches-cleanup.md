# Porządek gałęzi na GitHubie (2.11.2026)

Wszystkich gałęzi: **129**. Sprawdzono, czy ich zawartość jest w `main` (commity scalone albo pliki obecne; brakujące pliki to wyłącznie jednorazowe workflowy GPT, pliki `__pycache__` i kod przeniesiony podczas przebudowy do modułu `:sim`).

Uwaga: historia `main` zaczyna się 1.11.2026 (przebudowa repozytorium po turze 5). Gałęzie GPT 01–91 mają starą, niepołączoną historię — ich praca została scalona przed przebudową i jest w `main`, ale Git nie pokazuje ich jako „scalonych”.

## Zostawić

| Gałąź | Dlaczego |
|---|---|
| `main` | główna wersja gry |
| `claude/bold-sagan-vwz5gm` | baza zleceń GPT (= main) |
| `integ/tura10` | bieżąca gałąź robocza koordynatora |
| `music-src` | oryginały muzyki (FLAC) — jedyna kopia plików od właściciela |
| `claude/upbeat-galileo-jyhjtg` | gałąź tej sesji koordynatora |
| `claude/audit-a1` | w toku: naprawa zapisu bitwy (agent) |
| `claude/audit-a2a3` | scalona do integ/tura10, jeszcze nie na main — usunąć po wypchnięciu na main |
| `gpt/16-unit-upgrades` | w toku: zlecenie GPT fali 7 |
| `gpt/160-lifecycle-profile` | w toku: zlecenie GPT fali 7 |
| `gpt/161-flash-turret-touch` | w toku: zlecenie GPT fali 7 |
| `gpt/162-ci-release-gates` | w toku: zlecenie GPT fali 7 |
| `gpt/163-variant-balance` | w toku: zlecenie GPT fali 7 |
| `gpt/164-accessibility-nodes` | w toku: zlecenie GPT fali 7 |
| `gpt/165-privacy-docs` | w toku: zlecenie GPT fali 7 |

## Można usunąć — w pełni scalone (Git potwierdza)

13 gałęzi:

- `claude/peaceful-ride-0azzze`
- `claude/tura10-doktryny`
- `claude/tura10-muzyka`
- `claude/tura10-strojenie`
- `claude/tura10-szlif`
- `claude/tura10-szlif2`
- `claude/tura9-balans-dowodca`
- `claude/tura9-ux-dowodca`
- `claude/wip-tura8-balans`
- `integ/tura6`
- `integ/tura7`
- `integ/tura8`
- `integ/tura9`

## Można usunąć — praca dawno w `main` lub zastąpiona

| Gałąź | Uwagi |
|---|---|
| `gpt/115-battle-screen-split` | odrzucona (podział BattleScreen zrobiony ponownie w 118) |
| `gpt/116-l10n-review-4` | świadomie niescalona (wnioski naniesione ręcznie w turze 6) |
| `gpt/118-battle-verify` | scalona; 2 ostatnie commity to pomocnicze testy CI GPT |
| `gpt/127-ai-personalities` | scalona; ostatni commit (złote powtórki) zastąpiony regeneracją koordynatora |
| `gpt/132-tablet-layouts` | scalona; ostatni commit tylko usuwa pomocniczy workflow |
| `claude/wip-tura8-ui` | kopia awaryjna pracy agenta z tury 8 — praca scalona w integ/tura8 |
| `claude/wip-tura9-fix125` | kopia awaryjna — dokończona i scalona w turze 9 |
| `claude/wip-muzyka` | kopia awaryjna odtwarzacza muzyki — gotowa wersja scalona (tura10-muzyka) |
| `claude/int-audio-tests` | stara integracja sprzed przebudowy historii (31.10) — treść w main |
| `claude/int-infra` | jw. |
| `claude/int-meta` | jw. |
| `claude/youthful-ptolemy-fykzyu` | stara sesja koordynatora (31.10) — treść w main |

### Stare zlecenia GPT 01–91 (90 gałęzi, scalone przed przebudową historii)

- `gpt/01-play-games`
- `gpt/02-achievements`
- `gpt/03-missions`
- `gpt/04-balance`
- `gpt/05-ai-generals`
- `gpt/06-counters`
- `gpt/07-replay-log`
- `gpt/08-server`
- `gpt/09-music`
- `gpt/10-ci`
- `gpt/11-release-config`
- `gpt/12-store-listing`
- `gpt/13-endless-cards`
- `gpt/14-tests-robustness`
- `gpt/15-notifications`
- `gpt/17-new-era`
- `gpt/18-era-branches`
- `gpt/19-plural-forms`
- `gpt/20-store-privacy-update`
- `gpt/21-aimed-barrage`
- `gpt/22-ai-personalities`
- `gpt/23-global-ranking-client`
- `gpt/24-back-locale-fixes`
- `gpt/25-hit-death-anim`
- `gpt/26-projectile-fx`
- `gpt/27-machine-motion`
- `gpt/28-era-variants`
- `gpt/29-replay-viewer`
- `gpt/30-battle-history`
- `gpt/31-accessibility`
- `gpt/32-contextual-hints`
- `gpt/33-weather`
- `gpt/34-endless-bosses`
- `gpt/35-custom-skirmish`
- `gpt/36-weekly-siege`
- `gpt/37-performance-budget`
- `gpt/38-era-sound`
- `gpt/39-licenses-about`
- `gpt/40-layout-audit`
- `gpt/41-gpu-quality`
- `gpt/42-campaign-endgame-balance`
- `gpt/43-server-deploy`
- `gpt/44-translation-review`
- `gpt/45-replay-polish`
- `gpt/46-endless-fixes`
- `gpt/47-battle-hud-alloc`
- `gpt/48-audio-polish`
- `gpt/49-stability-io`
- `gpt/50-store-graphics`
- `gpt/51-release-day`
- `gpt/52-better-sfx`
- `gpt/53-hud-tooltips`
- `gpt/54-tutorial-update`
- `gpt/55-better-music`
- `gpt/56-era-backdrops`
- `gpt/57-main-menu`
- `gpt/58-app-icon-splash`
- `gpt/59-campaign-map`
- `gpt/60-new-achievements`
- `gpt/61-new-missions`
- `gpt/62-codex-update`
- `gpt/63-haptics`
- `gpt/64-server-moderation`
- `gpt/65-error-log`
- `gpt/66-release-build-check`
- `gpt/67-pause-quick-settings`
- `gpt/68-era-intros`
- `gpt/69-new-doctrines`
- `gpt/70-new-modifiers`
- `gpt/71-endless-cards-2`
- `gpt/72-daily-events`
- `gpt/73-replay-share`
- `gpt/74-layout-audit-2`
- `gpt/75-store-refresh`
- `gpt/76-ci-speed`
- `gpt/77-lowend-perf-2`
- `gpt/78-translation-pass-2`
- `gpt/79-variant-art`
- `gpt/80-server-identity`
- `gpt/81-server-verify-rules`
- `gpt/82-sim-balance-caps`
- `gpt/83-release-hardening`
- `gpt/84-battle-ux`
- `gpt/85-tutorial-ux`
- `gpt/86-hint-layout`
- `gpt/87-menu-rankings-ux`
- `gpt/88-l10n-fixes-3`
- `gpt/89-settings-codex-ux`
- `gpt/90-feedback-perf`
- `gpt/91-golden-tests`

**Razem do usunięcia: 115 gałęzi.** Zostaje 14.

## Jak usunąć

Najprościej: napisz koordynatorowi „usuń gałęzie z listy” — usunie je sam. Ręcznie na GitHubie: zakładka *Branches* → ikona kosza przy gałęzi.

Bezpieczeństwo: usunięta gałąź da się zwykle przywrócić z GitHuba przez kilka dni (przycisk *Restore* przy zamkniętym PR) albo z lokalnej kopii koordynatora; `music-src` **nie usuwać** (jedyna kopia oryginałów muzyki).
