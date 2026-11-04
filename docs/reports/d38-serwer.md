# Dzień 38: serwer rankingu gotowy na premierę (zlecenie R5-server-hardening)

Gałąź `claude/dzien-38-serwer` z `origin/integ/tura10`. Podstawa: `docs/tasks/R5-server-hardening.md`
i `docs/research/R5-server.md`. Właściciel zdecydował: **testy zamknięte bez serwera, serwer dopiero
przy premierze** (rekomendacja: VPS w UE). Nic nie zostało wdrożone. Bez zmian w aplikacji Android,
`:sim`, `PROTOCOL`, formacie API i workflowach CI.

## Co zrobiono

### Kod serwera (`server/src/main/kotlin/com/centurywar/server/`)

- **Retencja (L1)**, `Retention.kt` + `SqliteStore.purgeExpired` (jedna transakcja):
  - `CW_RETENTION_PERIOD_DAYS=90`: wyniki `daily` (od dnia wyzwania) i `weekly_<n>` (od **końca**
    tygodnia; numer tygodnia z `Weekly.weekNumber` z `:sim`, wzoru nie skopiowano);
  - `CW_RETENTION_REPORT_DAYS=180`: zgłoszenia nicków;
  - `CW_RETENTION_INACTIVE_DAYS=730`: gracz bez wyniku i aktualizacji przez 24 mies. jest usuwany
    w całości, z wpisem w dzienniku usunięć;
  - `CW_RETENTION_LOG_DAYS=400` (nowa zmienna): dziennik usunięć i dziennik operatora;
  - `0` wyłącza krok, poza zakresem 0..36500 serwer nie startuje (jak przy `limit()`);
  - tablice `endless`, `skirmish`, `campaign_*` znikają tylko z nieaktywnym graczem;
  - przy okazji czyszczone są skróty IP z poprzednich dni i zużyte `nonce`. Wcześniej znikały
    dopiero przy następnym zgłoszeniu, a polityka mówi „usuwany następnego dnia”;
  - uruchamianie: przy starcie i co 24 h (`RetentionScheduler`, wątek zamykany przy
    `ApplicationStopped` i w shutdown hooku). Do logów trafiają tylko liczby. Ręcznie:
    `admin purge [--dry-run]`. `--dry-run` wykonuje te same polecenia i wycofuje transakcję,
    więc liczby są dokładne.
- **Usuwanie przez operatora (L2)**: `admin find-nick <tekst>` szuka bez rozróżniania wielkości
  liter, także po normalizacji `NickModeration` (np. „lukasz” znajdzie „Łukasz 99”). Porównuje nick
  zapisany i pokazywany. Gdy szukany nick zamieniłby filtr, wypisuje podpowiedź, żeby zapytać
  o „Dowódca-XXXX”. `admin delete-player <UUID> [--keep-block]` usuwa wyniki, nick i zgłoszenia.
  Domyślnie zdejmuje też blokadę. Zwraca `deleted` (0) albo `not_found` (1).
- **Dziennik usunięć (L4)**: tabela `deletion_log(player_id_hash, deleted_at, source)`. Wpis
  powstaje przy usunięciu przez API, `delete-player` i retencję (`api`/`operator`/`retention`),
  tylko gdy coś faktycznie usunięto. Komenda `admin replay-deletions --since RRRR-MM-DD [--from plik]`.
- **Moderacja (L5, L6)**: `nick_reports.resolved_at`, `admin resolve-report <id>`.
  `admin reports` pokazuje domyślnie nierozpatrzone, `--all` pokazuje wszystkie. Tabela `admin_log`
  i komenda `admin log [n]` obejmują `block`, `unblock`, `delete-score`, `delete-player`, `purge`
  (także automatyczny, gdy coś usunął) i `replay-deletions`.
- **Migracja schematu 2 → 3** w istniejącym wzorcu `schema_version`: nowe tabele i kolumna. Starszy
  serwer odmówi otwarcia bazy w wersji 3 (`unsupported_schema_version`), opisane w §7.
- `Api.kt`: jedyna zmiana to przekazanie zegara do `deleteOwnedPlayer`. Format odpowiedzi bez zmian.

### Skrypt i konfiguracja

- `server/backup-sqlite.sh`: `CW_BACKUP_RCLONE_REMOTE` uruchamia `rclone copy` i rotację zdalną
  `rclone delete --min-age` (`CW_BACKUP_REMOTE_RETENTION_DAYS`, domyślnie 30). `CW_BACKUP_PING_URL`
  wysyła ping po sukcesie i `…/fail` przy każdym błędzie (trap `EXIT`). Skrypt sprawdza zmienne:
  zdalna nazwa musi mieć `:` i nie może zaczynać się od `-`, ping musi być `https://`, a gdy
  ustawiono zdalną nazwę, na hoście musi być `rclone`. Obraz Dockera bez zmian.
- `.env.example` i `docker-compose.yml`: nowe `CW_RETENTION_*` z komentarzem. Klucze i limity
  bez zmian.

### Dokumentacja

- `docs/SERVER_DEPLOY.md`:
  - wstęp: decyzja „serwer przy premierze”;
  - §1: wystarczy VPS z 2 GB RAM przy budowie obrazu poza VPS;
  - **§6a**: rclone `crypt` nad B2 albo Storage Box w regionie UE, wpisy w crontab
    (cron nie czyta `.env`), miesięczny test odtworzenia z kopii zdalnej;
  - **§7**: odtwarzanie przez `admin restore -` (zostawia katalog ratunkowy) i `replay-deletions`,
    schemat 3, budowa obrazu u siebie (`docker save | ssh … docker load`, `up -d --no-build`),
    comiesięczny harmonogram aktualizacji;
  - **§8a**: prośba e-mailowa krok po kroku (30 dni);
  - §9: `resolve-report` i `admin log`;
  - §10: monitory UptimeRobot/Healthchecks.io i opis, co zrobić po e-mailu z alertem;
  - **§13**: retencja i lista „przed premierą”.
- `server/README.md`: zaktualizowane sekcje o kopiach, odtwarzaniu (wcześniej tekst mówił, że
  serwer „nie prowadzi dziennika usunięć”) i moderacji.
- `docs/store/privacy_policy_pl.md` i `_en.md`:
  - IP: dobowy jednokierunkowy skrót do limitu, usuwany następnego dnia;
  - usunięto wzmianki o „zleceniu 80”;
  - w §8 dodano prośbę e-mailową (30 dni) i listę okresów przechowywania (także kopie i dzienniki
    skrótów);
  - pola `OWNER_INPUT` bez zmian (po 5 znaczników `{{OWNER.…}}`, brak znaczników
    `[DO DECYZJI]`/`[INSERT]`). Sprawdziłem to ręcznie według reguł `ReleaseChecklist.storeDocumentProblems`.
- `docs/store/data_safety.md`: przepływy „skrót IP” i „dzienniki”, tabela retencji i usuwania.

## Testy i jak je uruchomiono

Gradle nie działa (AGP z dl.google.com), więc testy uruchomiono **poza Gradle**. Nic z tego nie
trafiło do repo:
- zależności `:server` (Ktor 3.2.3, sqlite-jdbc 3.50.3.0, kotlinx-serialization-json 1.9.0, JUnit
  5.13.3 + console launcher 1.13.3) pobrał Maven (`/opt/maven`) z mirrora
  `maven-central.storage-download.googleapis.com` do `/tmp/claude-0/d38/lib`;
- kompilator `kotlin-compiler-embeddable` **2.2.20** (ta sama wersja co w projekcie) z wtyczką
  serializacji pobrano tak samo, do `/tmp/claude-0/d38/kc`. Kompilator 2.0.21 z
  `/opt/gradle-8.14.3/lib` nie był potrzebny;
- kompilacja `:sim` (main), `:server` (main + test) i uruchomienie wszystkich testów serwera
  JUnit Console Launcherem: skrypty `/tmp/claude-0/d38/build.sh` i `test.sh`.

Wyniki:
- **przed zmianami:** 77 testów zielonych, 1 pominięty;
- **po zmianach:** **93 zielone, 1 pominięty (ten sam), 0 błędów**, w tym `ServerClientE2ETest`,
  `ApiTest`, `PlayerDeletionTest`, `InFlightDeletionTest`, `ModerationApiTest`.
  Jedyna zmiana w istniejącym teście: `PlayerDeletionTest` oczekuje teraz schematu w wersji 3
  zamiast 2.
- **nowe testy:**
  - `RetentionTest` (8): daily 91/90 dni, granica tygodnia, `endless` zostaje, nieaktywny 731 dni
    usunięty z wpisem w dzienniku, aktywny 729 dni zostaje, wygasanie starej blokady, zgłoszenia,
    dzienniki, limity i nonce, `0` wyłącza, `--dry-run` niczego nie usuwa, walidacja env,
    harmonogram przeżywa błąd;
  - `DeletionLogTest` (3): API, CLI i retencja zapisują skrót, a surowy UUID nie występuje w żadnej
    kolumnie `deletion_log`/`admin_log`; `replay-deletions` po „odtworzeniu” starszej kopii usuwa
    właściwych graczy, innych nie rusza i powtórzone działa idempotentnie; pełna ścieżka
    `admin restore` → katalog ratunkowy → `--from`;
  - `AdminCliTest` (+4): `find-nick` (wielkość liter, normalizacja, nick zastąpiony przez filtr),
    `delete-player` (`deleted`/`not_found`, kody 0/1/2, blokada domyślnie usuwana, z
    `--keep-block` zostaje), `resolve-report`/`reports --all`, `log` bez UUID;
  - `SqliteMaintenanceTest` (+1): migracja 2 → 3 na bazie utworzonej DDL-em starego kodu i odmowa
    otwarcia schematu 4.
- **Różnice względem Gradle:** wersje przechodnie wybrał Maven, nie Gradle (np. kotlin-reflect,
  coroutines), a ustawienia `tasks.test` z `build.gradle.kts` nie były użyte. Przed scaleniem
  warto raz uruchomić `./gradlew :server:test -PserverOnly=true --rerun` tam, gdzie Gradle działa.
  `:app:renderStorePolicies` nie był uruchomiony; reguły sprawdziłem ręcznie, jak wyżej.
- **`backup-sqlite.sh`:** `bash -n` przeszedł. **shellcheck nie został uruchomiony**: nie ma go
  w systemie, a uruchomienie binarki pobranej z PyPI zablokowały uprawnienia sesji. Zamiast tego
  zrobiłem próbę z atrapami `docker`/`rclone`/`curl` (7 scenariuszy):
  - kopia lokalna;
  - kopia + zdalna + ping;
  - błąd rclone daje kod 5 i `…/fail`;
  - błąd kopii daje kod 1 i `…/fail`;
  - zła zdalna nazwa, zły ping i złe dni dają kod 2.

## Odstępstwa od zlecenia (świadome)

1. **Skrót w dzienniku ≠ publiczne ID.** Zlecenie mówi „`publicIds.of(player_id)`”. Używam tego
   samego HMAC i klucza `CW_PUBLIC_ID_KEY`, ale z osobną domeną (`subject:` + UUID). Gdyby skrót
   był równy publicznemu ID, ktoś, kto zapisał rankingi, mógłby z dziennika odczytać, który nick
   się usunął i kiedy.
2. **`replay-deletions --from <plik>`.** Dziennik jest w tej samej bazie, więc odtworzona kopia
   go nie zawiera, a po odtworzeniu własny dziennik bazy nic nie wnosi. Komenda czyta więc
   dziennik z bazy sprzed odtworzenia (katalog `cw-pre-restore-*`, który zostawia `admin restore`).
   Bez `--from` czyta bieżącą bazę. Gdy stara baza przepadła z dyskiem, usunięć od ostatniej kopii
   zdalnej nie da się odtworzyć automatycznie. Opisałem to w §7 i README.
3. **Nowa zmienna `CW_RETENTION_LOG_DAYS`** (400): jedna dla obu dzienników.
4. Raport nazywa się `d38-serwer.md` (zgodnie z poleceniem koordynatora), a nie
   `r5-server-hardening.md`.
5. §7 instrukcji używa teraz `admin restore -` (jak w README) zamiast ręcznego `cp`, bo tylko ta
   ścieżka zostawia starą bazę do powtórzenia usunięć.

## Do potwierdzenia przez właściciela (prostym językiem)

Wszystkie poniższe ustawienia działają już w kodzie z wartością domyślną i można je zmienić bez
programisty: w pliku `.env` na serwerze albo w crontab. **Jeśli zmienisz liczbę dni, trzeba
zmienić ją też w polityce prywatności i w `data_safety.md`.** W tych plikach terminy oznaczono
komentarzem „do potwierdzenia”.

1. **Jak długo trzymamy dane rankingu** (pytanie 4 z R5). Proponowane:
   - wyniki dnia i tygodnia: **90 dni**;
   - zgłoszenia nicków: **180 dni**;
   - gracz, który nie grał w ranking **2 lata**, znika w całości;
   - dzienniki usunięć i działań moderatora: **400 dni** (same skróty, bez UUID i nicków);
   - kopie zapasowe: **14 dni** na serwerze i **30 dni** poza nim.

   Najlepiej potwierdzić z prawnikiem.
2. **Blokady.** Po prośbie e-mailowej operator domyślnie usuwa także blokadę gracza (przełącznik
   `--keep-block` ją zostawia). Blokada gracza, którego konta już nie ma, wygasa po 2 latach.
   Jeśli wolisz trzymać blokady dłużej, wystarczy to powiedzieć.
3. **Termin odpowiedzi na prośbę e-mailową:** w polityce wpisano „do 30 dni”. Potrzebny jest też
   adres e-mail kontaktowy (`contact_email` w `OWNER_INPUT.md`).
4. **Hosting** (pytanie 2 z R5): instrukcja zakłada VPS w UE (Hetzner albo OVH, ok. 5–7 EUR/mies.).
   Kopia poza serwerem w Backblaze B2 (region UE, darmowe 10 GB) albo Hetzner Storage Box.
   Monitoring: darmowe UptimeRobot lub Healthchecks.io na Twój e-mail. W panelach trzeba
   zaakceptować umowę powierzenia danych (DPA).
5. **Podstawa prawna rankingu** (zgoda czy „wykonanie usługi”) i zgoda rodzica dla młodszych
   graczy (pytanie 3 z R5): **nie zmieniałem tego**. Gra dalej pyta o zgodę. Do decyzji z prawnikiem.
6. **Ochrona DDoS (Cloudflare)** (pytanie 5 z R5): zgodnie z rekomendacją **nie na start**.
   Niczego nie dodano.
7. **Logi adresów IP u hostingu i w Caddy.** Przykładowa konfiguracja Caddy nie zapisuje logów
   dostępu. Po wdrożeniu trzeba sprawdzić, co loguje dostawca, i wpisać to w `data_safety.md`.

## Lista komend (nowe)

```
admin purge [--dry-run]
admin find-nick <tekst>
admin delete-player <UUID> [--keep-block]
admin replay-deletions --since <RRRR-MM-DD> [--from <plik.sqlite>]
admin reports [--all] [limit]
admin resolve-report <id>
admin log [limit]
```

Zmienne: `CW_RETENTION_PERIOD_DAYS`, `CW_RETENTION_REPORT_DAYS`, `CW_RETENTION_INACTIVE_DAYS`,
`CW_RETENTION_LOG_DAYS` (`.env`), `CW_BACKUP_RCLONE_REMOTE`, `CW_BACKUP_REMOTE_RETENTION_DAYS`,
`CW_BACKUP_PING_URL` (crontab).

## Ryzyka i uwagi

- Retencja usuwa dane nieodwracalnie i jest **domyślnie włączona**. Przed pierwszym startem na
  produkcji: kopia i `admin purge --dry-run` (§13).
- `delete-player`, `purge` i `replay-deletions` uruchamiane w osobnym kontenerze CLI nie znają
  biletów zgłoszeń w toku procesu serwera. Wynik weryfikowany w tej chwili i tak zostanie
  odrzucony (`player_deleted`), bo serwer sprawdza w bazie, czy właściciel nadal istnieje.
  Automatyczna retencja działa w procesie serwera i korzysta z tego samego mechanizmu co usuwanie przez API.
- Gracz, który wraca po ponad 2 latach w chwili dobowego czyszczenia, może raz dostać 409
  `player_deleted` (to samo zachowanie co przy usunięciu z gry).
- Po aktualizacji do schematu 3 nie da się wrócić do starszego obrazu bez kopii sprzed aktualizacji.
