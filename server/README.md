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
  `used_nonces` (przeciw ponownemu przesłaniu POST). **Nie zapisujemy logu bitwy,
  emaila ani adresu IP**. Nick i UUID są jednak danymi pseudonimowymi, mogą podlegać RODO.

## Lokalnie

Wymagane JDK 17+ oraz dostęp do Maven Central. Aplikacja Android dodatkowo wymaga
SDK 36. Aby kompilować sam serwer bez instalowania SDK Android:

```bash
export CW_HMAC_KEY="$(openssl rand -hex 32)"
export CW_DATABASE_PATH="./data/leaderboards.db"
export CW_GAME_VERSION="0.2.0"
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

Każde żądanie, **również GET**, musi mieć nagłówki:

| Nagłówek | Wartość |
|---|---|
| `X-CW-Timestamp` | czas UTC, sekundy Unix, dopuszczalna różnica ±300 s |
| `X-CW-Nonce` | nowe UUID na każde żądanie |
| `X-CW-Signature` | HMAC-SHA256 lowercase hex (64 znaki) |

Klucz: `CW_HMAC_KEY` (co najmniej 32 bajty UTF-8, podawany przez środowisko).
Podpis powstaje z **dokładnych bajtów tekstu UTF-8**:

```text
timestamp\nnonce\nMETHOD\n/uri?query=...\nbody
```

Elementy oddzielone pojedynczym LF, bez dodatkowego LF na końcu; body jest
pustym ciągiem dla GET. URI zawiera query string w takiej kolejności, w jakiej
zostanie wysłany. Metoda to `GET` albo `POST`.

#### POST /v1/score

Przykładowy format (wynik poniżej jest **wyłącznie przykładem schematu**,
nie jest poprawną, zweryfikowaną powtórką):

```json
{
  "board": "endless",
  "player": "a16036d5-800a-4898-aea3-11b6548de7b4",
  "nick": "Gracz_12",
  "gameVersion": "0.2.0",
  "seed": 12345,
  "replay": "AoQH...",
  "score": 257,
  "research": [0,0,0,0,0,0,0,0,0,0,0,0,0]
}
```

Pozostałe pola: `day` (tylko `daily`, dzień UTC od epoki Unix), `stage`
(tylko `campaign`, indeks 0–31). Gracz to losowy **UUID v4**, utrwalany
lokalnie na urządzeniu; `nick` ma 1–24 znaki Unicode: litery, cyfry,
spację oraz `._-`, bez białych znaków na końcach.

Serwer **nie przyjmuje** modyfikacji rozgrywki od klienta. Konfiguracje:
- `daily`: `day` = dzisiejszy dzień UTC, `seed=day`, `Daily.config()`
  (badania wyłączone), **pierwszy poprawny wynik dziennie**.
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
maks. 90 000 kroków (pozostałe tryby) lub 216 000 (endless). Wynik musi być dodatni. Błędy: `400` (format),
`401` (podpis / nonce), `409` (drugie zgłoszenie daily),
`413` (rozmiar), `422` (niezgodna powtórka), `429` (limit).

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
`campaign_0` ... `campaign_31`. Odpowiedź zawiera pierwsze 50 pozycji.
Remisy rozstrzyga leksykograficznie UUID (stabilna kolejność).

#### GET /v1/around?board=endless&period=all&player=<uuid>

Zwraca do 3 miejsc przed i 3 za wskazanym graczem, wraz z nim samym.
Brak wyniku gracza daje pustą listę. JSON:

```json
{"board":"endless","period":"all","entries":[{"rank":1,"player":"a16036d5-800a-4898-aea3-11b6548de7b4","nick":"Gracz_12","score":257}]}
```

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
  Dla daily seed jest stały; dla pozostałych trybów klient deklaruje seed.
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

## Wdrożenie: Docker / VPS

Build z **katalogu głównego repozytorium**:

```bash
docker build -f server/Dockerfile -t century-war-score .
docker volume create century-war-data
docker run -d --restart unless-stopped --name century-war-score \
    -p 127.0.0.1:8080:8080 \
    -v century-war-data:/var/lib/cw \
    -e CW_HMAC_KEY="$CW_HMAC_KEY" \
    -e CW_DATABASE_PATH=/var/lib/cw/leaderboards.db \
    century-war-score
```

Postaw Nginx/Caddy z TLS przed portem 8080, ustaw `client_max_body_size 1m`,
limit zapytań i czas odczytu żądania. Zrób backup SQLite przez
`sqlite3 /var/lib/cw/leaderboards.db '.backup /.../backup.db'`.

**Fly.io:** stwórz aplikację Docker, `fly volumes create data --size 1`
w regionie aplikacji, zamontuj do `/var/lib/cw`, ustaw
`fly secrets set CW_HMAC_KEY=...` i uruchom jedną maszynę.
Dane na wolumenie lokalnym nie replikują się automatycznie.

**Render:** wybierz Docker Web Service i **płatny persistent disk**
zamontowany pod `/var/lib/cw`, `CW_HMAC_KEY` ustaw jako secret
environment variable, `PORT=8080`. Na usługach z nietrwałym FS
rankingi znikną po redeployu.

**VPS:** Docker Compose lub systemd z pojedynczą instancją i
stałym wolumenem, reverse proxy TLS i backupami.

## Kryteria testowe

`./gradlew :server:test` uruchamia testy prawdziwej powtórki,
odrzucenia sfałszowanego wyniku i niedozwolonej komendy,
HMAC/nonce, endpointów oraz polityki jednego zgłoszenia daily.
Workflow w `.github/workflows/server.yml` uruchamia również budowę
aplikacji i test deterministyczności `BattleSimTest`.
