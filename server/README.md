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
export CW_GAME_VERSION="0.4.0"
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
`/v1/report` i `/healthz` są bez HMAC; pierwszy jest limitowany per-IP.

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
zostanie wysłany. Metoda to `GET` albo `POST`.

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

#### GET /healthz

Bez podpisu HMAC; sprawdza dostęp do SQLite. HTTP 200 i tekst ok lub
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
- `daily`: `day` = dzisiejszy dzień UTC, `seed=day`, `Daily.config()`
  (badania wyłączone), **pierwszy poprawny wynik dziennie**.
- `weekly`: `day` oznacza bieżący lub poprzedni numer tygodnia UTC, a `seed` musi
  odpowiadać `Weekly.config(day).seed`. Serwer odtwarza zamrożone reguły i kartę
  startową; przyjmuje kolejne próby i zapisuje najlepszy wynik na `weekly_<day>`.
- `endless`: `BattleConfig(Mode.ENDLESS, seed, 1f, playerBonuses=Research.bonuses(research))`.
- `skirmish`: `BattleConfig(Mode.SKIRMISH, seed, Skirmish.power(difficulty), playerBonuses=Research.bonuses(research))`.
- `campaign`: `Campaign.config(stages[stage], Research.bonuses(research), seed, tutorial)`;
  ranking oddzielny dla każdego etapu, np. `campaign_7`. **Nie** jest to
  globalna suma gwiazdek kampanii: jej weryfikacja wymagałaby dziennika 36 etapów.

`replay` to zapis `Replay.encode()` z modułu `:sim` (Base64 URL bez dopełnienia),
czyli dokładnie to, co nagrywa w grze `BattleController`. Serwer odtwarza go
funkcją `Replay.verify()`, więc obsługuje wszystkie komendy gry (jednostki,
wieże, ostrzał zwykły i celowany, Zryw, postawa, doktryny, karty Endless,
kapitulacja). Dodatkowo odrzuca kapitulację przed 60. krokiem i złoto samouczka
poza etapem `campaign_0`. Każda komenda musi się udać, bitwa musi być
zakończona w zadeklarowanym kroku, a `score` musi być równe `Battle.score()`.
Niepoprawne logi nie zajmują miejsca w rankingu.

Ograniczenia: 1 MiB żądania, limity formatu `Replay` (32 768 komend),
maks. 90 000 kroków (pozostałe tryby) lub 216 000 (endless i weekly). Wynik musi być dodatni. Błędy: `400` (format),
`401` (podpis / nonce), `409` (drugie zgłoszenie daily),
`413` (rozmiar), `422` (niezgodna powtórka), `429` (limit),
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
- \`tutorial\`: opcjonalne, \`true\` tylko przy \`board=campaign,stage=0\`.
  Weryfikator odtwarza wówczas łagodniejszy \`Campaign.config\`, dopuszczając
  legalne komendy złota samouczka.
- Daily musi mieć 13 zer; \`Daily.config\` normalizuje bonusy.
- Nieobsługiwana \`gameVersion\`/błędne poziomy i konfiguracja to HTTP 400
  (\`unsupported_version\`, \`invalid_research\`, \`invalid_difficulty\`...),
  niezgodna powtórka to HTTP 422. Wersja \`0.1.0\` nie jest akceptowana.
- Baza przechowuje dodatkowo sumę poziomów \`research_sum\`; migracja dodaje
  tę kolumnę z domyślną wartością 0, bez usuwania starych wyników.
- Rankingi nadal obejmują wyniki z badaniami i bez badań. Osobny filtr na
  \`research_sum=0\` oraz \`research_sum>0\` można dodać później.
- Po stronie Androida wymagane są \`CW_SERVER_URL\` (HTTPS),
  \`CW_HMAC_KEY\` (min. 32 bajty) i \`CW_PRIVACY_URL\` (HTTPS): zmienne CI lub
  prywatny \`local.properties\`. Wszystkie puste domyślnie. Brak choć jednego
  oznacza brak własnego rankingu w UI. Włączenie jest dobrowolne.
- Klucz HMAC osadzony w APK nie jest sekretem; **nie** powinien być kluczem
  uprzywilejowanych operacji administracyjnych backendu. Więcej w
  \`docs/replay-integration.md\`.

### Bezpieczeństwo i ograniczenia

- HMAC **nie jest silnym uwierzytelnieniem** w aplikacji mobilnej: klucz da
  się wyodrębnić z APK. Znajomość sekretu pozwala podszyć się pod cudzy UUID.
  To **weryfikacja możliwego przebiegu**, a nie dowód uczciwego gracza.
  Do produkcji rozważ Google Play Integrity i token wydawany przez serwer
  per instalacja / sesja, wraz z serwerowym seedem i ograniczeniem prób.
- Dowolny klient może generować wiele legalnych powtórek offline. Serwer
  nie wykryje automatycznego wyszukiwania optymalnych komend ani botów.
  Dla daily i weekly seed jest stały; dla pozostałych trybów klient deklaruje seed.
- Weryfikator jest ściśle związany z wersją symulacji. Podbij
  `CW_GAME_VERSION` (i `Replay.VERSION` w `:sim`) przy zmianach balansu/protokołu.
  Przed wdrożeniem sprawdź **te same** powtórki na Android/JVM (Float).
- **Warianty epok (format powtórki v4):** komenda `Replay.Kind.VARIANT`
  jest zapisywana po awansie (przed doktryną) albo w kroku 0 na starcie
  wariantowej epoki. Przed wdrożeniem tej wersji klienta ustaw nową wartość
  `CW_GAME_VERSION` na serwerze, uruchom testy `:server:test` i zsynchronizuj
  kod `:sim` klienta/serwera. Starsze powtórki z wersją 3 nie przejdą
  dekodera v4. `Daily.config` sprzed `VARIANTS_DAY` działa bez wariantów;
  nie zmieniaj tej daty wstecz i nie modyfikuj historycznej puli modyfikatorów.
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
curl -f http://127.0.0.1:8080/healthz
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
