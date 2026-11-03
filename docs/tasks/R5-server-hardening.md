# R5: Serwer rankingu: retencja, usuwanie przez operatora, kopie poza serwerem, dokumentacja

**Gałąź:** `claude/r5-server-hardening` z `origin/main`. **Gradle:** wyłącznie
`flock /tmp/claude-0/gradle.lock ./gradlew :server:test -PserverOnly=true --rerun --console=plain`
(moduł serwera; pełne testy aplikacji niepotrzebne, chyba że ruszasz `:sim`, a nie powinieneś).
**Bez zmian w aplikacji Android, `:sim`, PROTOCOL i formacie API** (klient ma działać bez nowego
wydania). Bez workflowów CI. Podstawa: `docs/research/R5-server.md` (luki L1–L6, L10, L3, L7,
L9, L11). Commity po polsku, wypychaj często.

## Cel

Serwer gotowy do włączenia przy premierze pod kątem RODO i utrzymania:
- dane nie są trzymane bez końca;
- operator może usunąć gracza na prośbę e-mailową;
- po odtworzeniu kopii da się powtórzyć usunięcia;
- kopia trafia poza serwer, a o awarii ktoś się dowie.

## Stan w kodzie

- `server/src/main/kotlin/com/centurywar/server/SqliteStore.kt` (730 wierszy):
  - tabele `players` (`player_id`, `nick`, `updated_at`, `secret_hash`, `public_id`),
    `blocked_players`, `scores` (`board`, `day`, `verified_at`, …), `used_nonces`,
    `score_submit_quotas`, `schema_version`, `nick_reports` (ok. 67–185; wersja schematu 2);
  - `deleteOwnedPlayer(player, hash)` (253; wymaga sekretu), usuwanie w `admin` (ok. 600–650:
    `deleteScore`, `block`/`unblock`), czyszczenie `used_nonces` (309) i limitów dziennych (334).
- `server/src/main/kotlin/com/centurywar/server/AdminCli.kt`: komendy `help`, `recent`,
  `reports`, `delete-score`, `block`/`unblock`, `backup`, `restore`, `export-csv`.
- `server/src/main/kotlin/com/centurywar/server/DatabaseMaintenance.kt`: blokada procesu,
  kopia `VACUUM INTO`, odtwarzanie.
- `server/src/main/kotlin/com/centurywar/server/Main.kt`: konfiguracja z env (`CW_*`).
- `server/backup-sqlite.sh`, `docker-compose.yml`, `.env.example`, `docs/SERVER_DEPLOY.md`
  (§6 kopie, §8 usuwanie, §9 moderacja, §10 obsługa).
- Testy: `server/src/test/kotlin/com/centurywar/server/` (`AdminCliTest`, `PlayerDeletionTest`,
  `InFlightDeletionTest`, `SqliteMaintenanceTest`, `ModerationStoreTest`, …).

## Zmiany

### 1. Retencja (L1)
- Nowe zmienne env z wartościami domyślnymi (**do zatwierdzenia przez właściciela; te są
  propozycją**): `CW_RETENTION_PERIOD_DAYS=90` (tablice `daily*` i `weekly_*`, liczone
  od `day`/tygodnia), `CW_RETENTION_REPORT_DAYS=180` (`nick_reports.reported_at`),
  `CW_RETENTION_INACTIVE_DAYS=730` (gracz, którego `players.updated_at` i ostatni
  `scores.verified_at` są starsze, usuwany w całości jak przy `deleteOwnedPlayer`, bez blokady).
  Walidacja zakresów jak `limit()` w `Main.kt`; `0` = wyłączone.
- `SqliteStore.purgeExpired(nowMillis): PurgeResult` w jednej transakcji. Tablice stałe
  (`endless`, `skirmish`, `campaign_*`) **nie** są czyszczone wiekiem, tylko przez nieaktywność gracza.
- Wywołanie: przy starcie serwera i raz na dobę (koroutina w `leaderboardModule` albo
  `ScheduledExecutor` w `Main`, zamykany przy `ApplicationStopped`) oraz komenda
  `admin purge [--dry-run]` wypisująca liczby usuniętych wierszy.
- Tablice tygodnia: dzień końca tygodnia z numeru `weekly_<n>` (wzór `Weekly.weekNumber`;
  czytaj z `:sim`, nie kopiuj wzoru).

### 2. Usuwanie gracza przez operatora (L2) i dziennik usunięć (L4)
- `admin find-nick <tekst>`: wypisuje UUID, publiczne ID, nick, liczbę wyników i ostatnią
  aktywność. Dopasowanie bez rozróżniania wielkości liter, także po nicku znormalizowanym
  filtrem `NickModeration`.
- `admin delete-player <UUID> [--keep-block]`: usuwa wyniki, gracza i zgłoszenia o nim, jak
  `deleteOwnedPlayer`, ale bez sekretu. Domyślnie usuwa też blokadę, chyba że `--keep-block`.
  Wypisuje `deleted`/`not_found` i kod wyjścia jak `delete-score`.
- Nowa tabela `deletion_log(player_id_hash TEXT, deleted_at INTEGER, source TEXT)`: skrót
  UUID HMAC kluczem `CW_PUBLIC_ID_KEY`, **nie surowy UUID**. Wpis przy każdym usunięciu
  (API, `delete-player`, retencja nieaktywnych). Migracja schematu 2 → 3 w istniejącym wzorcu
  `schema_version`.
- `admin replay-deletions --since <RRRR-MM-DD>`: po odtworzeniu kopii usuwa ponownie graczy
  z dziennika od tej daty. Porównuje skrót `publicIds.of(player_id)` z wpisami, więc nie trzyma
  surowych UUID. Dziennik też ma retencję (np. 400 dni) i trafia do kopii, bo jest w tej samej bazie.

### 3. Moderacja (L5, L6)
- `nick_reports` + kolumna `resolved_at`. `admin resolve-report <id>` i `admin reports` pokazuje
  domyślnie nierozpatrzone.
- Tabela `admin_log(at, action, target_hash, detail)` dla `block`, `unblock`, `delete-score`,
  `delete-player`, `purge`, `replay-deletions`. Komenda `admin log [n]`.

### 4. Kopie poza serwerem i alerty (L3, L11)
- `server/backup-sqlite.sh`: opcjonalne kroki po udanej kopii, sterowane zmiennymi:
  - `CW_BACKUP_RCLONE_REMOTE` (np. `b2crypt:century-war`): `rclone copy` pliku i
    `rclone delete --min-age <dni>d`, gdy ustawione;
  - `CW_BACKUP_PING_URL` (np. Healthchecks.io): `curl -fsS -m 10` po sukcesie, a przy błędzie
    `…/fail` (trap).
  Bez nowych zależności w obrazie: `rclone` i `curl` działają na hoście, nie w kontenerze.
- `docs/SERVER_DEPLOY.md` §6: konfiguracja `rclone` z szyfrowaniem (`rclone config`, remote
  `crypt` nad B2 lub Storage Box), test odtworzenia z kopii zdalnej, retencja 30 dni.
- §10: zewnętrzny monitor `https://<domena>/health` co 5 min (UptimeRobot lub Healthchecks.io)
  i ping kopii. Opis prostym językiem, co zrobić, gdy przyjdzie e-mail z alertem.

### 5. Budowa obrazu i aktualizacje (L7, L9), tylko dokumentacja i skrypt
- `docs/SERVER_DEPLOY.md`: wariant „zbuduj obraz u siebie i wyślij na serwer”
  (`docker build` + `docker save | ssh … docker load`), więc VPS z 2 GB RAM wystarczy.
  Plus harmonogram: raz w miesiącu `apt upgrade`, przebudowa obrazu (nowy `eclipse-temurin`),
  przegląd wersji Ktor i sqlite-jdbc.
- **Nie** podnoś wersji zależności w tym zadaniu bez pełnych testów serwera. Jeśli podnosisz,
  osobnym commitem.

### 6. Polityka prywatności i Data safety (L10)
- `docs/store/privacy_policy_pl.md` i `_en.md`, sekcje własnego rankingu i §8:
  - IP: „adres IP jest używany do limitów, a do dziennego limitu zapisywany jest jego
    jednokierunkowy skrót powiązany z dniem, usuwany następnego dnia”;
  - usunąć „po wdrożeniu (zlecenie 80)”, bo zgłaszanie nicków działa;
  - terminy przechowywania z punktu 1, jako `{{…}}` do potwierdzenia przez właściciela
    albo z wartościami domyślnymi wyraźnie oznaczonymi;
  - ścieżka e-mailowa: prośba z nickiem, operator usuwa w ciągu 30 dni (`delete-player`);
  - kopie: usunięte dane znikają z kopii po upływie ich retencji.
- `docs/store/data_safety.md`: tabela przepływów (skrót IP, retencja, usuwanie przez operatora).
- Szablony polityki są renderowane przez `:app:renderStorePolicies`. Nie zmieniaj pól
  `OWNER_INPUT`. Sprawdź, że test/zadanie renderowania nie wymaga nowych pól.

## Testy do dopisania (`server/src/test/kotlin/com/centurywar/server/`)

- `RetentionTest`: wyniki `daily` starsze niż 90 dni znikają, nowsze zostają; `weekly_<n>`
  liczone od końca tygodnia; `endless` zostaje; gracz nieaktywny 731 dni usunięty w całości
  z wpisem w `deletion_log`; `0` wyłącza; `--dry-run` nic nie usuwa.
- `AdminCliTest`: `find-nick` (wielkość liter, nick zastąpiony przez filtr), `delete-player`
  (`deleted`/`not_found`, kody wyjścia, blokada domyślnie usuwana, z `--keep-block` zostaje),
  `resolve-report`, `log`.
- `DeletionLogTest`: usunięcie przez API, CLI i retencję zapisuje skrót, nie UUID (asercja, że
  surowy UUID nie występuje w żadnej kolumnie `deletion_log`); `replay-deletions` po
  „odtworzeniu” starszej bazy usuwa właściwych graczy i nie rusza innych.
- Migracja schematu 2 → 3 na bazie utworzonej starym kodem (wzór istniejących testów migracji
  w `SqliteMaintenanceTest`).
- `backup-sqlite.sh`: test skryptu nie jest wymagany. Sprawdź ręcznie `bash -n` i `shellcheck`
  (jeśli dostępny) i opisz w raporcie.
- Istniejące testy API, usuwania i moderacji bez zmian (zgodność klienta).

## Kryteria odbioru

- `./gradlew :server:test -PserverOnly=true --rerun` zielone; `:app` i `:sim` bez zmian.
- API (`/v1/*`) bez zmian formatu; `ServerClientE2ETest` zielony.
- `docs/SERVER_DEPLOY.md` opisuje: retencję i jej zmienne, `find-nick`/`delete-player`
  (procedura prośby e-mailowej krok po kroku), `replay-deletions` po odtworzeniu kopii,
  kopie zdalne i alerty, budowę obrazu poza VPS.
- Polityki PL/EN i Data safety zgodne z kodem (IP, retencja, usuwanie przez operatora).
- Krótki raport `docs/reports/r5-server-hardening.md` z listą komend i decyzjami do
  potwierdzenia (terminy retencji).

## Ryzyka

- Retencja usuwa dane nieodwracalnie: domyślnie włączona, ale z `--dry-run` i jasnym opisem.
  Pierwsze uruchomienie na produkcji poprzedza kopia.
- Migracja schematu na żywej bazie: kopia przed aktualizacją (istniejąca procedura §7).
- `delete-player` bez sekretu to mocne narzędzie: tylko CLI na serwerze, z wpisem w `admin_log`.

## Czego nie ruszać

- Format API i podpisów, `RequestAuthenticator`, `ReplayVerifier`, `VerificationGate`, limity.
- Aplikacja Android (`ScoreClient`), `:sim`, PROTOCOL, `RANKED_REPLAY_FORMATS`.
- Klucze i sekrety, w tym przykłady w `.env.example` (tylko nowe zmienne z komentarzem).
