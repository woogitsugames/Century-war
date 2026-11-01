# Century War — własny backend rankingów

Kotlin/JVM + **Ktor 3** + **SQLite**. Wybór zamiast Cloudflare Workers/D1:
kompilujemy ten sam kod deterministycznej symulacji w `:sim` dla Androida i backendu.
D1 nie uruchamia natywnego Kotlin/JVM, więc weryfikacja wymagałaby dwóch niezależnych
implementacji reguł walki — większe ryzyko różnic oraz błędów anty-cheatu.

## Architektura

- `:sim`: pojedyncze źródło prawdy — `Battle.kt`, `Ai.kt`, `Defs.kt`, `Modes.kt`, `BattleSnapshot.kt`,
  przeniesione z aplikacji **bez zmiany pakietu `com.centurywar.game` ani logiki**.
  Android importuje moduł przez `implementation(project(":sim"))`. Kod w `:sim` musi
  pozostać czystym Kotlin/JVM (bez `android.*`); renderer i ekrany zostają w `:app`.
  Testy symulacji nadal są w `app/src/test` (BattleSimTest, BattleRulesTest, BattleSnapshotTest).
- `:server`: Ktor/Netty, weryfikator headless, podpisy żądań, SQLite, testy JUnit 5.
  Aplikacja ma opcjonalny klient HTTPS w `meta/ScoreClient.kt`, aktywowany
  tylko po zgodzie użytkownika i podaniu poprawnej konfiguracji.
- Baza: `players` (UUID i pseudonim), `scores` (wynik i skrót SHA-256 zgłoszenia),
  `used_nonces` (przeciw ponownemu przesłaniu POST) oraz `blocked_players` (blokady UUID).
  **Nie zapisujemy logu bitwy,
  emaila ani adresu IP**. Nick i UUID są jednak danymi pseudonimowymi, mogą podlegać RODO.

## Lokalnie

Wymagane JDK 17+ oraz dostęp do Maven Central. Aplikacja Android dodatkowo wymaga
SDK 36. Aby kompilować sam serwer bez instalowania SDK Android:

```bash
export CW_HMAC_KEY="$(openssl rand -hex 32)"
export CW_PUBLIC_ID_KEY="$(openssl rand -hex 32)" # niezależny, tylko na serwerze
export CW_DATABASE_PATH="./data/leaderboards.db"
./gradlew :server:test -PserverOnly=true --console=plain
./gradlew :server:run -PserverOnly=true --console=plain
```

Standardowe zadanie akceptacyjne `./gradlew :server:test` również istnieje
(przy normalnej konfiguracji projektu Android SDK może być wymagany).
Testy kompatybilności aplikacji:

```bash
./gradlew :app:assembleDebug --console=plain
./gradlew :app:testDebugUnitTest --tests '*BattleSimTest*' --console=plain
```

### API v1

Podpisy wymagane dla `/v1/score`, `/v1/player/delete`, `/v1/top`, `/v1/around`.
`/v1/report`, `/health` i `/healthz` są bez HMAC; pierwszy jest limitowany per-IP.

Każde podpisane żądanie musi mieć nagłówki:

| Nagłówek | Wartość |
|---|---|
| `X-CW-Timestamp` | czas UTC, sekundy Unix, dopuszczalna różnica ±300 s |
| `X-CW-Nonce` | nowe UUID na każde żądanie |
| `X-CW-Signature` | HMAC-SHA256 lowercase hex (64 znaki) |
| `X-CW-Player-Secret` | 64 małe znaki hex = 32 losowe bajty; dla score/delete/around |

Klucz: `CW_HMAC_KEY` (co najmniej 32 bajty UTF-8, podawany przez środowisko).
Podpis powstaje z **dokładnych bajtów tekstu UTF-8**:

```text
timestamp\nnonce\nMETHOD\n/uri?query=...\nbody
```

Elementy oddzielone pojedynczym LF, bez dodatkowego LF na końcu; body jest
pustym ciągiem dla GET. URI zawiera query string w takiej kolejności, w jakiej
zostanie wysłany. Metoda to `GET` albo `POST`. W `score`, `player/delete`
oraz `around` podpis zawiera dodatkowy składnik na końcu:
`timestamp\\nnonce\\nMETHOD\\nURI\\nbody\\ninstallationSecret`.

#### POST /v1/player/delete

Ten sam podpis HMAC, znacznik czasu i jednorazowy nonce co przy POST wyniku.
Podpis obejmuje **dokładny** URI /v1/player/delete i body (UTF-8):

~~~json
{"player":"a16036d5-800a-4898-aea3-11b6548de7b4"}
~~~

Odpowiedź HTTP 200: {"deleted":true}, również gdy UUID nie był w bazie.
Błędny sekret właściciela: HTTP 403. Stary gracz bez zapisanego sekretu
musi najpierw go przypisać pierwszym zweryfikowanym wynikiem.
Jedna transakcja usuwa wszystkie wyniki (wszystkie plansze i dni) i rekord
gracza z pseudonimem, a limiter w pamięci zapomina licznik tego UUID.
Błędny podpis / ponowny nonce: 401; nieprawidłowy UUID v4: 400.
Nonce i limit IP nie są przypisane do UUID; nonce wygasa po 10 minutach.

Klient może zachować żądanie w lokalnej kolejce podczas braku internetu.
Callback klienta potwierdza **trwałe zapisanie żądania**, nie usunięcie na serwerze.
Żądanie zostanie ponowione po uruchomieniu gry, również po wyłączeniu rankingu.
Przed usunięciem danych na serwerze stare wyniki mogą nadal być publiczne.
Odinstalowanie gry przed przetworzeniem kolejki może usunąć samo żądanie.

#### GET /health i /healthz

Bez podpisu HMAC; oba adresy sprawdzają dostęp do SQLite. HTTP 200 i tekst ok lub
HTTP 503 i unavailable, bez informacji o użytkownikach.

#### POST /v1/score

Przykładowy format (wynik poniżej jest **wyłącznie przykładem schematu**,
nie jest poprawną, zweryfikowaną powtórką):

```json
{
  "board": "endless",
  "player": "a16036d5-800a-4898-aea3-11b6548de7b4",
  "nick": "Gracz_12",
  "gameVersion": "0.4.0",
  "seed": 12345,
  "replay": "AoQH...",
  "score": 257,
  "research": [0,0,0,0,0,0,0,0,0,0,0,0,0]
}
```

Pozostałe pola: `day` (`daily` — dzień UTC od epoki Unix; `weekly` — numer tygodnia
z `Weekly.weekNumber(day)`), `stage` (tylko `campaign`, indeks 0–35). Gracz to losowy **UUID v4**, utrwalany
lokalnie na urządzeniu; `nick` ma 1–24 znaki Unicode: litery, cyfry,
spację oraz `._-`, bez białych znaków na końcach. Jeżeli nick zawiera
słowo ze słownika moderacji (10 języków gry) albo nie spełnia reguł,
serwer **przyjmuje poprawną powtórkę**, ale zapisuje nick zastępczy
`Dowódca-XXXX` (4 znaki z SHA-256 UUID). Filtr normalizuje akcenty,
wielkość liter, niektóre podobne znaki i rozdzielanie liter. Wcześniej
zapisane obraźliwe nicki są maskowane przy odczycie.

Serwer **nie przyjmuje** modyfikacji rozgrywki od klienta. Konfiguracje:
- `daily`: `day` = dzisiejszy dzień UTC albo dzień poprzedni do 02:00 UTC
  (od północy do 01:59:59.999 UTC), `seed=day`, `Daily.config()`
  (badania wyłączone). Zgłoszenie rozpoczęte przed północą otrzymuje 2 h
  na wysyłkę; nadal tylko **pierwszy poprawny wynik dziennie**.
- `weekly`: `day` oznacza bieżący lub poprzedni numer tygodnia UTC, a `seed` musi
  odpowiadać `Weekly.config(day).seed`. Serwer odtwarza zamrożone reguły i kartę
  startową; przyjmuje kolejne próby i zapisuje najlepszy wynik na `weekly_<day>`.
- `endless`: `BattleConfig(Mode.ENDLESS, seed, 1f, playerBonuses=Research.bonuses(research))`.
- `skirmish`: `BattleConfig(Mode.SKIRMISH, seed, Skirmish.power(difficulty), playerBonuses=Research.bonuses(research))`.
- `campaign`: `Campaign.config(stages[stage], Research.bonuses(research), seed)`;
  ranking oddzielny dla każdego etapu, np. `campaign_7`. **Nie** jest to
  globalna suma gwiazdek kampanii: jej weryfikacja wymagałaby dziennika 36 etapów.

**Weryfikacja badań i normalizacja rankingu** dla Endless, potyczki i kampanii:
1. Najpierw serwer odtwarza dokładnie zgłoszony przebieg z deklarowanymi
   bonusami badań; wszystkie rozkazy i `score` muszą się zgadzać.
2. Następnie powtarza seed i rozkazy **bez ulepszeń** (poziomy badań = 0).
   Rozkazy niewykonalne bez ulepszeń pomija, a brakujący wybór rozstrzyga
   pierwszą opcją. Wynik z **drugiej symulacji** zapisuje w rankingu globalnym.
   Może różnić się od wyniku lokalnego i oryginalnego `score`. Gra wyświetla
   tę informację w rankingu globalnym.

Dzienna i tygodniowa rozgrywka mają już stałe zasady, bez przeliczenia.
`research_sum` pozostaje informacją pomocniczą, nigdy premią rankingową.

**Przed wdrożeniem:** wcześniejszych wyników liczonych z badaniami nie da
się poprawnie przeliczyć, bo serwer przechowuje skrót, a nie powtórkę.
Należy wyczyścić albo zarchiwizować stare wyniki w tabeli `scores`,
żeby nie mieszać wartości z różnych reguł.

`replay` to zapis `Replay.encode()` z modułu `:sim` (Base64 URL bez dopełnienia),
czyli dokładnie to, co nagrywa w grze `BattleController`. Serwer odtwarza go
funkcją `Replay.verify()`, więc obsługuje wszystkie komendy gry (jednostki,
wieże, ostrzał zwykły i celowany, Zryw, postawa, doktryny, karty Endless,
kapitulacja). Odrzuca kapitulację przed 60. krokiem, każdy wynik samouczka
(`tutorial=true`) i każdą komendę `TUTORIAL_GOLD`. W podstawowej kontroli
każda komenda musi się udać, bitwa musi zakończyć się w zadanym kroku,
a zgłoszone `score` musi odpowiadać faktycznemu wynikowi.

Niepoprawne logi nie zajmują miejsca w rankingu.

Ograniczenia: 1 MiB żądania, 32 768 komend, wspólny
`Replay.MAX_STEPS = 432000` (120 minut przy 60 Hz) dla wszystkich trybów.
Bitwa automatycznie kończy się na granicy, a Endless zachowuje osiągniętą falę.
Klient pokazuje komunikat, gdy starsza powtórka przekracza limit.
Wynik musi być dodatni. Błędy: `400` (format), `401` (podpis / nonce),
`409` (drugie zgłoszenie daily), `413` (rozmiar),
`422` (niezgodna powtórka), `429` (limit),
`403` (UUID zablokowane przez moderatora).

Odpowiedź `200`:
```json
{"board":"endless","verifiedScore":257,"bestScore":257,"accepted":true}
```

Dla rekordów poza daily zapisujemy najlepszy wynik gracza na danej tablicy
w danym dniu; dla okresu tygodnia/całości wybieramy najlepszy wynik gracza
ze wszystkich zakwalifikowanych dni. Przesłanie słabszego wyniku daje
`accepted:false`, a drugi poprawny daily daje `409`.

#### GET /v1/top?board=endless&period=week

`period`: `day` (dzisiaj UTC), `week` (poniedziałek UTC–niedziela),
`all` (bez daty). `board`: `daily`, `endless`, `skirmish`,
`campaign_0` ... `campaign_35`, `weekly_<numer_tygodnia>`. Odpowiedź zawiera pierwsze 50 pozycji.
Remisy rozstrzyga leksykograficznie UUID (stabilna kolejność).
Odpowiedź nigdy nie zawiera UUID: `entries[].player` to 24-znakowy
HMAC-SHA256 z UUID i prywatnego `CW_PUBLIC_ID_KEY` (12 bajtów).

#### GET /v1/around?board=endless&period=all&player=<uuid>

Zwraca do 3 miejsc przed i 3 za wskazanym graczem, wraz z nim samym.
Wymaga `X-CW-Player-Secret` i jego uwzględnienia w HMAC; cudzy/niezarejestrowany
sekret daje HTTP 403. Odpowiedź podaje wyłącznie publiczne ID (`self` i `player`).
Brak wyniku gracza daje pustą listę. JSON:

```json
{"board":"endless","period":"all","entries":[{"rank":1,"player":"57a837e65366cc8821abf429","nick":"Gracz_12","score":257}],"self":"57a837e65366cc8821abf429"}
```

#### POST /v1/report

Zgłoszenie obraźliwego pseudonimu: `{"publicId":"57a837e65366cc8821abf429","reason":"offensive"}`.
Body maks. 512 B; powód: `offensive`, `impersonation`, `other`. Odpowiedź:
202 `{"accepted":true}`, 404 jeśli brak ID, 429 po przekroczeniu limitu.
W SQLite zapisywane są UUID, publiczne ID, publiczny nick, powód i czas raportu.
Wgląd tylko administracyjnie: `server admin reports [limit]`.

### Rozszerzenie protokołu 0.2.0

- \`research\`: **wymagana** lista dokładnie 13 poziomów zgodnych z kolejnością
  \`Research.defs\`; każdy poziom w zakresie \`0..max\`. Badania są liczone na
  serwerze w tym samym module \`:sim\` co na telefonie.
- \`difficulty\`: **wymagane** \`0..3\` wyłącznie przy \`skirmish\`, pozostałe
  tryby pomijają pole.
- `tutorial`: pole zgodności wstecznej. `true` jest zawsze odrzucane
  (`invalid_tutorial`), a komendy `TUTORIAL_GOLD` są nielegalne w rankingu.
- Daily musi mieć 13 zer; \`Daily.config\` normalizuje bonusy.
- Nieobsługiwana \`gameVersion\`/błędne poziomy i konfiguracja to HTTP 400
  (\`unsupported_version\`, \`invalid_research\`, \`invalid_difficulty\`...),
  niezgodna powtórka to HTTP 422. Wersja \`0.1.0\` nie jest akceptowana.
- Baza przechowuje pomocniczo sumę deklarowanych poziomów `research_sum`.
  Nie zmienia to globalnego wyniku: zapisuje się wynik z symulacji bez badań.
- Po stronie Androida wymagane są \`CW_SERVER_URL\` (HTTPS),
  \`CW_HMAC_KEY\` (min. 32 bajty) i \`CW_PRIVACY_URL\` (HTTPS): zmienne CI lub
  prywatny \`local.properties\`. Wszystkie puste domyślnie. Brak choć jednego
  oznacza brak własnego rankingu w UI. Włączenie jest dobrowolne.
- Klucz HMAC osadzony w APK nie jest sekretem; **nie** powinien być kluczem
  uprzywilejowanych operacji administracyjnych backendu. Więcej w
  \`docs/replay-integration.md\`.

### Bezpieczeństwo i ograniczenia

- Sam klucz HMAC w APK nie stanowi uwierzytelnienia właściciela UUID.
  Operacje na kontach wymagają dodatkowego sekretu instalacji (32 bajty),
  którego skrót SHA-256 zapisuje serwer. Znajomość klucza z APK nie wystarcza
  do przejęcia powiązanego wpisu. Sekret może jednak zostać skradziony
  z urządzenia, a historyczny UUID może zostać przejęty przed pierwszym
  przypisaniem sekretu. Rozważ Play Integrity i powiązanie z kontem.
- Dowolny klient może generować wiele legalnych powtórek offline. Serwer
  nie wykryje automatycznego wyszukiwania optymalnych komend ani botów.
  Dla daily i weekly seed jest stały; dla pozostałych trybów klient deklaruje seed.
- Wspólna wersja protokołu jest zdefiniowana jako `SimVersion.PROTOCOL`
  w `:sim` (obecnie `0.4.0`). Używa jej klient oraz domyślny serwer.
  `CW_GAME_VERSION` nie jest już konfigurowana w Docker Compose.
  Jej podbicie po zmianach przebiegu symulacji wykonuje koordynator.
  `Replay.VERSION` (obecnie 5) wersjonuje tylko format powtórek.
  Przed wydaniem sprawdź identyczne odtwarzanie Android/JVM (Float).
- Komenda `Replay.Kind.VARIANT` pozostaje częścią formatu; `Daily.config`
  utrzymuje historyczne reguły przed `Daily.RELEASE_DAY`.
- Ograniczenia: POST 20/min/IP i 6/min/UUID; GET 120/min/IP,
  maks. 2 równoczesne weryfikacje; nonce POST trwały w SQLite
  (10 minut). Rate limiter IP jest **w pamięci jednej instancji**.
  Nie ufamy nagłówkowi `X-Forwarded-For`; stosujemy adres połączenia.
  **Uwaga:** za reverse proxy wszyscy klienci mają adres proxy, więc limit
  per-IP serwera staje się limitem globalnym — właściwy limit per-IP ustaw na proxy.
- Konieczny jest HTTPS/TLS na reverse proxy oraz limit body 1 MiB także w proxy
  (serwer czyta najwyżej 1 MiB + 1 bajt, również przy `Transfer-Encoding: chunked`),
  per-IP limit również na proxy, nadzór logów, kopie bazy.
- Jedna instancja SQLite z trwałym wolumenem. **Nie skaluj** równolegle
  wielu kontenerów z jedną lokalną bazą, bez zmiany warstwy danych.
- Dane wyników i pseudonimy wymagają polityki retencji, informacji o
  przetwarzaniu i funkcji usunięcia/zmiany identyfikatora przed publikacją.

## Zdrowie, metryki i kopie zapasowe

**Dostępność:** `GET /health` to lekki, publiczny test SQLite (200 `ok`, a przy błędzie
503 `unavailable`). Starszy adres `/healthz` nadal działa. Healthcheck Compose wywołuje
`/health` co 30 s; można sprawdzić go przez `docker compose ps` i:

~~~bash
curl -fsS http://127.0.0.1:8080/health
~~~

**Metryki (opcjonalne):** wygeneruj osobny klucz `openssl rand -hex 32` i ustaw
`CW_METRICS_TOKEN` w prywatnym pliku `.env`, a potem wykonaj `docker compose up -d`.
Pusta wartość wyłącza `/metrics` (404); skonfigurowana wymaga nagłówka
`Authorization: Bearer <token>` (brak lub zły token: 401). Przykład z serwera VPS:

~~~bash
# Docker Compose czyta .env, ale nie eksportuje z niego zmiennych do powłoki.
export CW_METRICS_TOKEN="$(sed -n 's/^CW_METRICS_TOKEN=//p' .env)"
curl -fsS -H "Authorization: Bearer $CW_METRICS_TOKEN" \
  http://127.0.0.1:8080/metrics
~~~

Odpowiedź JSON zawiera `scoreCount` (wszystkie wiersze `scores` w SQLite, także
zablokowane), `verificationAccepted`, `verificationRejected` (HTTP 422 po
niepoprawnej powtórce), `verificationUnavailable` (kolejka pełna/timeout),
`verificationSamples`, `verificationMeanMs`, `verificationMaxMs` i
`uptimeSeconds`. Czasy mierzą samą funkcję weryfikacji, nie oczekiwanie
w kolejce. Liczniki weryfikacji są pamięciowe i zerują się przy restarcie;
`scoreCount` jest odczytywane bezpośrednio z bazy. Nie udostępniaj tokenu w APK,
logach ani repozytorium; endpointu nie wystawiaj publicznie przez reverse proxy
bez kontroli dostępu.

**Kopia online:** nie zatrzymuj działającego serwera. Skrypt wykonuje SQLite
`VACUUM INTO` na osobnym połączeniu, sprawdza integralność kopii, przenosi plik
poza wolumen Dockera i dopiero po udanym zapisie usuwa kopie starsze niż
domyślne 14 dni. Nazwy zawierają czas UTC i PID, uprawnienia plików: 0600.

~~~bash
sudo env CW_BACKUP_DIR=/var/backups/century-war \
  CW_BACKUP_RETENTION_DAYS=14 bash server/backup-sqlite.sh
# Alternatywnie, bez Dockera (przy ustawionych CW_PUBLIC_ID_KEY i CW_DATABASE_PATH):
./gradlew :server:run -PserverOnly=true --args="admin backup /tmp/ranking.sqlite"
~~~

Zaplanuj skrypt w cron/systemd timer, codziennie po godzinach szczytu.
Przykładowy wpis cron po dostosowaniu `/srv/century-war` do katalogu wdrożenia:

~~~cron
0 3 * * * cd /srv/century-war && /usr/bin/bash server/backup-sqlite.sh >> /var/log/cw-backup.log 2>&1
~~~

Utrzymuj też **kopię poza VPS**: backup na tym samym dysku nie zabezpiecza
przed awarią dysku. Rotacja dotyczy tylko plików `cw-backup-*.sqlite`
w katalogu `CW_BACKUP_DIR`; katalog `cw-data` nie jest miejscem docelowym
trwałych kopii. Kopię można wykonać także przy aktywnym trybie WAL.

**Przywracanie:** jest operacją offline. Zatrzymaj usługę, przekaż plik
ze standardowego wejścia (bez dodatkowych mountów i zmiany praw własności
pliku na hoście), a następnie uruchom serwer:

~~~bash
docker compose stop leaderboard
docker compose run --rm --no-deps -T leaderboard admin restore - \
  < /var/backups/century-war/cw-backup-YYYYMMDD-HHMMSS-PID.sqlite
docker compose up -d leaderboard
curl -fsS http://127.0.0.1:8080/health
~~~

Podmień nazwę na faktyczną kopię; polecenie ze znakiem `<` czyta
lokalny plik na serwerze, więc konto użytkownika musi mieć prawo jego odczytu.
Alternatywa bez strumienia: `server admin restore /ścieżka/kopia.sqlite`,
jeśli plik jest dostępny wewnątrz uruchomionego kontenera administracyjnego.
Tryb `restore` wchodzi do programu **przed** otwarciem połączenia SQLite.
Blokada pliku `leaderboards.db.server.lock` uniemożliwia przywrócenie,
gdy główny proces serwera działa (plik blokady może pozostać po restarcie,
ale blokada systemowa zostaje zwolniona). Uszkodzona lub obca baza jest
odrzucana przez `PRAGMA integrity_check` i sprawdzenie schematu.

Przy przywróceniu poprzedni plik bazy oraz ewentualne pliki WAL/SHM
zostają zachowane w `cw-pre-restore-*` na wolumenie jako kopia ratunkowa.
Po weryfikacji działania nowej bazy można je ręcznie usunąć. Przywrócenie
cofa wszystkie operacje wykonane po momencie utworzenia kopii, w tym
usunięcia danych przez użytkowników. Taką operację należy odnotować
i sprawdzić skutki dla retencji i żądań usunięcia danych.

## Moderacja administracyjna

Bez panelu WWW: polecenia w tym samym programie `server admin ...` to
`recent [limit]`, `reports [limit]`, `delete-score UUID board day`, `block UUID`,
`unblock UUID` i `export-csv` (stdout). Na VPS uruchom je przez
`docker compose run --rm --no-deps -T leaderboard admin ...`.
Blokady ukrywają istniejące wyniki i zatrzymują nowe zgłoszenia, bez
zmiany schematu API. Usunięcie danych z `/v1/player/delete` usuwa
wyniki i nick, **nie usuwa rekordu blokady UUID**. Pełne przykłady, kopie i uprawnienia
w instrukcji dla właściciela poniżej.

## Wdrożenie: Docker Compose / VPS

**Instrukcja krok po kroku dla właściciela**: [docs/SERVER_DEPLOY.md](../docs/SERVER_DEPLOY.md).

Po zainstalowaniu Docker Compose v2, w katalogu głównym projektu:

~~~bash
cp .env.example .env
# Wygeneruj osobno CW_HMAC_KEY i CW_PUBLIC_ID_KEY: openssl rand -hex 32
chmod 600 .env
docker compose up -d --build
docker compose ps
curl -f http://127.0.0.1:8080/health
~~~

Przykładowy Caddyfile: server/Caddyfile.example (HTTPS na domenie).
Kopia bazy: bash server/backup-sqlite.sh (poza wolumenem, domyślnie
/var/backups/century-war). **Nigdy nie uruchamiaj docker compose down -v**:
usuwa trwały wolumen z bazą. Wdrażaj tylko jedną instancję serwera.

Przy pierwszym starcie ze starą bazą migracja v1 uzupełnia plansze, a v2
wprowadza sekret właściciela, publiczne ID i zgłoszenia. Migracja v1
i przenosi campaign_8..31 na campaign_12..35. Przenumerowanie jest
transakcyjne i **nie wykonuje się ponownie** po restarcie. Zrób kopię starej
bazy przed uruchomieniem i sprawdź, czy używa starych numerów etapów.
Konflikt z istniejącym rekordem na nowej planszy powoduje wycofanie migracji
i błąd startu, a nie ciche nadpisanie.

## Kryteria testowe

`./gradlew :server:test` uruchamia testy prawdziwej powtórki,
odrzucenia sfałszowanego wyniku i niedozwolonej komendy,
HMAC/nonce, endpointów oraz polityki jednego zgłoszenia daily.
Workflow w `.github/workflows/server.yml` uruchamia testy serwera i budowę obrazu.
Testy Androida i symulacji uruchamia osobny workflow `android.yml`.


## Uwierzytelnianie, proxy i limity (zlecenie 80)

- `CW_HMAC_KEY` może być wyodrębniony z APK. Operacje na własnym koncie wymagają
  niezależnego, wygenerowanego przez `SecureRandom` sekretu 32 B. Aplikacja
  zapisuje go lokalnie w profilu, nigdy w chmurowym zapisie PGS. W żądaniu
  `/v1/score`, `/v1/player/delete` i `/v1/around` dodatkowo przesyła nagłówek
  `X-CW-Player-Secret` (64 małe znaki hex).
- Dla tych trzech endpointów treść podpisywana HMAC kończy się dodatkowym LF
  i dokładną wartością nagłówka sekretu: `timestamp\\nnonce\\nMETHOD\\nURI\\nbody\\nsecretHex`.
  `GET /v1/top` nadal używa starego podpisu bez sekretu. Serwer zapisuje
  wyłącznie SHA-256 z **surowych 32 bajtów** sekretu, nie nagłówka ASCII.
  Próba nadpisania nicka lub wyniku innym sekretem kończy się HTTP 403.
- Pierwszy zweryfikowany wynik z UUID o niezapisanym sekrecie przypisuje sekret
  do konta (migracja). Historyczny UUID **może zostać przejęty przed pierwszym
  przypisaniem**, jeżeli ktoś znał go ze starych odpowiedzi API. Nie istnieje
  możliwość wiarygodnego potwierdzenia historycznej własności bez wcześniejszego
  poświadczenia. Po migracji samo poznanie UUID i klucza APK nie wystarcza.
- `CW_PUBLIC_ID_KEY` to osobny 32-bajtowy klucz serwera, **nigdy nie trafia
  do aplikacji**. Używamy HMAC-SHA256(UUID), pierwszych 12 bajtów zapisanych
  jako 24-znakowy hex. Nie rotuj tego klucza bez planu migracji bazy.
- `CW_TRUSTED_PROXY_IPS` (lista dokładnych adresów po przecinku, domyślnie
  `127.0.0.1`) określa, skąd akceptować X-Forwarded-For. Caddy musi
  **nadpisywać** XFF wartością `{remote_host}`. Docker może przedstawiać
  Caddy pod adresem bramy bridge; w takim przypadku dodaj **wyłącznie**
  zweryfikowany IP bramy do tej listy.
- Limity na minutę: `CW_RATE_POST_IP=20`, `CW_RATE_DELETE_IP=20`,
  `CW_RATE_READ_IP=120`, `CW_RATE_REPORT_IP=5`, `CW_RATE_POST_PLAYER=6`.
  Limity są w pamięci instancji; pozostaw również ochronę na brzegu proxy.
- Weryfikacja powtórek: 2 pracowników, maksymalnie 16 oczekujących.
  Brak miejsca albo timeout: HTTP 503, `Retry-After: 30`.
  Nie zmieniano zasad `Replay.verify`.
