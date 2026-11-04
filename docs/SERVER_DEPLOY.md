# Century War — wdrożenie serwera rankingów na VPS

Instrukcja dla właściciela gry. Własny ranking jest **opcjonalny**. VPS jest
niezbędny tylko do funkcji rankingu online, nie do grania offline.
Potrzebujesz domeny, serwera VPS, konta z możliwością SSH oraz kodu repozytorium.

**Kiedy:** decyzja właściciela (04.11.2026) — testy zamknięte idą **bez serwera**
(w APK pusty `CW_SERVER_URL`, ranking globalny wyłączony), serwer włączamy dopiero
przy premierze, na VPS w UE. Przed włączeniem przejdź listę z §13 („Przed premierą”).

## 1. Zakup serwera i domena

### Ile to kosztuje? (orientacyjnie, 02.11.2026)

| Oferta | Rozmiar VPS | Cena orientacyjna | Na co uważać |
|---|---|---|---|
| [OVHcloud VPS-1](https://www.ovhcloud.com/fr/vps/) | 2 vCore, 4 GB RAM, 40 GB NVMe | od **3,81 EUR netto/mies.** | Cena „od”, zależna od konfiguracji i okresu. |
| [netcup VPS Lite 1 G12.5s](https://www.netcup.com/en/server/vps/vps-lite-1-g12.5s-iv-6m) | 2 vCore, 4 GB RAM, 80 GB SSD | około **5,86 EUR brutto/mies.** | Pokazana oferta z zobowiązaniem na 6 miesięcy. |
| [Hetzner Cloud CX23](https://www.hetzner.com/cloud/cost-optimized/) | 2 vCPU, 4 GB RAM, 40 GB NVMe | około **5,49 EUR netto + 0,50 EUR za IPv4/mies.** | Limitowana dostępność; sprawdź panel przed zakupem. |

**Wybór na start:** OVHcloud VPS-1 albo netcup z 4 GB RAM i architekturą x86_64.
Wybierz serwer w UE i Ubuntu 24.04 LTS. Nie wybieraj 1 GB RAM — budowanie
obrazu na VPS może zabraknąć pamięci. Do ceny dolicz domenę, VAT (zależnie
od kraju i fakturowania) i osobną kopię danych poza VPS. To orientacyjne
stawki, a współdzielony vCPU nie gwarantuje stałej wydajności.


1. Wybierz VPS z **Ubuntu 24.04 LTS**, publicznym adresem IPv4, około 2 vCPU,
   **4 GB RAM do kompilacji obrazu Docker** i przynajmniej 20 GB SSD.
   Po zbudowaniu obrazu sam serwer potrzebuje znacznie mniej zasobów.
   Jeśli obraz budujesz u siebie i wysyłasz na serwer (§7, „Budowa obrazu poza VPS”),
   wystarczy VPS z **2 GB RAM**.
2. Wybierz subdomenę, np. scores.twojadomena.pl. W panelu DNS dodaj rekord
   **A** wskazujący publiczny adres IPv4 VPS. Rekord AAAA dodaj tylko, jeżeli
   faktycznie działa IPv6. Na propagację DNS może być potrzebny czas.
3. Otwórz na firewallu dostawcy i VPS porty **22/TCP (SSH)**, **80/TCP** i **443/TCP**.
   Nie otwieraj publicznie portu **8080**. Ogranicz SSH kluczem, włącz aktualizacje
   bezpieczeństwa i zaporę, zanim uruchomisz usługę.
4. Zaloguj się przez SSH na konto administratora z uprawnieniami sudo.
   Przed publikacją uzupełnij dane wydawcy i e-mail kontaktowy w polityce prywatności.

## 2. Docker, narzędzia i kod

Na Ubuntu zainstaluj **Docker Engine + wtyczkę Compose v2** według oficjalnej
instrukcji Docker dla Ubuntu: https://docs.docker.com/engine/install/ubuntu/
(zainstaluj też docker-compose-plugin).
Następnie:

~~~bash
sudo apt update
sudo apt install -y git curl openssl sqlite3
docker --version
docker compose version
sudo mkdir -p /opt/century-war
sudo chown "$USER":"$USER" /opt/century-war
git clone https://github.com/woogitsugames/Century-war.git /opt/century-war
cd /opt/century-war
~~~

Użyj **stabilnej, przetestowanej** gałęzi lub tagu zawierającego aktualny serwer.
Przed startem sprawdź numer commita poleceniem `git rev-parse --short HEAD`
i upewnij się, że `git status --short` nie pokazuje lokalnych zmian.
Nie wdrażaj w ciemno równolegle rozwijanej gałęzi.
Prawa do Dockera dają w praktyce uprawnienia administratora — nie udostępniaj
ich przypadkowym użytkownikom.

## 3. Sekret i uruchomienie (jedno polecenie)

W katalogu /opt/century-war przygotuj konfigurację:

~~~bash
cp .env.example .env
openssl rand -hex 32
nano .env
chmod 600 .env
~~~

Wygeneruj dwa **niezależne** klucze (`openssl rand -hex 32` dwa razy).
Pierwszy 64-znakowy ciąg wklej w `CW_HMAC_KEY`; drugi w `CW_PUBLIC_ID_KEY`.
**CW_PUBLIC_ID_KEY jest wyłącznie serwerowy** — nie trafia do APK.
Zachowaj zaszyfrowaną kopię obu kluczy poza VPS. Rotacja `CW_PUBLIC_ID_KEY`
bez migracji zmieni publiczne identyfikatory graczy. Opcjonalny
`CW_METRICS_TOKEN` wygeneruj trzecim `openssl rand -hex 32`; puste pole
wyłącza `/metrics`. W `.env.example` są tylko przykłady, nie sekrety.

**Nie ustawiaj `CW_GAME_VERSION`.** Serwer automatycznie używa
`SimVersion.PROTOCOL` z modułu `:sim`, identycznego jak
`ScoreClient.GAME_VERSION` w aplikacji. Nie zapisuj `.env` do Git ani
nie publikuj kluczy. Dla najmniejszego VPS zacznij z
`CW_JAVA_TOOL_OPTIONS=-Xmx512m -XX:ActiveProcessorCount=1`:
jedna weryfikacja jednocześnie, ograniczona sterta JVM.
Klucz ten jest również w APK, a więc **nie zapewnia mocnego uwierzytelnienia**.
Żądanie usunięcia obcego UUID jest odrzucane bez powiązanego z nim
sekretu instalacji. Istniejące konta wiążą sekret przy pierwszym nowym wyniku.

~~~bash
docker compose up -d --build
docker compose ps
curl -fsS http://127.0.0.1:8080/health
curl -fsS http://127.0.0.1:8080/healthz
~~~

Oba adresy powinny odpowiedzieć HTTP **200**, tekstem **ok**.
HTTP 503 sygnalizuje problem z SQLite; sprawdź też status
`healthy` w `docker compose ps`. Kontener ma restart: unless-stopped,
bazę w trwałym wolumenie cw-data oraz port 8080 dostępny **tylko lokalnie**.
Błąd konfiguracji? Zobacz logi: docker compose logs --tail=100 leaderboard.
Nie przesyłaj do osób trzecich pełnego pliku .env ani danych graczy z backupów.

## 4. HTTPS: Caddy

Zainstaluj Caddy zgodnie z https://caddyserver.com/docs/install
i włącz usługę. Skopiuj server/Caddyfile.example do /etc/caddy/Caddyfile,
zamień **scores.example.com** na własną subdomenę i sprawdź konfigurację:

~~~bash
sudo cp server/Caddyfile.example /etc/caddy/Caddyfile
sudo nano /etc/caddy/Caddyfile
sudo caddy validate --config /etc/caddy/Caddyfile
sudo systemctl enable --now caddy
sudo systemctl reload caddy
curl -fsS https://scores.twojadomena.pl/health
curl -fsS https://scores.twojadomena.pl/healthz
~~~

Caddy automatycznie wystawia i odnawia certyfikat TLS przy poprawnym DNS
i otwartych portach 80/443. Port 8080 pozostaje niewidoczny z Internetu.
Caddy ogranicza body do 1 MB i **nadpisuje X-Forwarded-For** rzeczywistym IP
połączenia TLS. Backend ufa XFF wyłącznie od adresów `CW_TRUSTED_PROXY_IPS`.
Domyślnie zaufany jest tylko `127.0.0.1`, ale przy Docker Compose backend
może widzieć Caddy jako IP bramy bridge. Sprawdź rzeczywistą bramę:

```bash
docker inspect -f '{{range .NetworkSettings.Networks}}{{.Gateway}}{{end}}' "$(docker compose ps -q leaderboard)"
# Jeżeli adres to np. 172.19.0.1, dopisz w .env:
# CW_TRUSTED_PROXY_IPS=127.0.0.1,172.19.0.1
docker compose up -d --force-recreate
```

Dodaj **wyłącznie zweryfikowany adres proxy**, nigdy publiczny IP ani CIDR,
a port 8080 pozostaw przypięty do loopback hosta. Przetestuj niezależne limity
dwóch adresów klienta i odporność na podrobiony XFF. Stockowy Caddy nie
ma tu osobnego rate limitera; dla ochrony DDoS dodaj ograniczenie na brzegu.

## 5. Podłączenie Androida

Na komputerze budującym Androida wpisz do **nieśledzonego** pliku
`local.properties` (w katalogu głównym projektu), albo jako zmienne
środowiskowe/secrets w CI podczas budowania APK/AAB:

~~~properties
CW_SERVER_URL=https://scores.example.com
CW_PRIVACY_URL=https://example.com/privacy
CW_HMAC_KEY=TU_WKLEJ_DOKLADNIE_WARTOSC_Z_SERWERA
~~~

Adresy `example.com` są **tylko przykładami**: użyj własnych publicznych
adresów HTTPS. `CW_HMAC_KEY` musi być identyczny z kluczem w serwerowym
`.env`. **Nie wpisuj `CW_PUBLIC_ID_KEY` ani `CW_METRICS_TOKEN` w aplikacji.**
W GitHub Actions użyj **Secrets**, nie jawnych wartości w YAML, commitach lub
logach. Wartości są kompilowane do `BuildConfig`: zmiana domeny lub HMAC
wymaga wydania nowej aplikacji. Klucz HMAC obecny w APK można wyodrębnić,
więc nie służy jako samodzielne poświadczenie tożsamości gracza. W Google Play i samej aplikacji podaj działający
link do polityki prywatności. Użytkownik musi sam wyrazić zgodę na ranking.
Bez kompletu konfiguracji własny ranking jest wyłączony.

Po wdrożeniu sprawdź podpisane żądania GET /v1/top oraz POST /v1/score,
a także usuwanie tożsamości. GET `/health` i `/healthz` nie wymagają podpisu,
tak samo jak POST `/v1/report`; żądania wyników i odczytu rankingu są
podpisywane zgodnie z `server/README.md`.
Wersje klienta, serwera i symulacji muszą się zgadzać.

## 6. Kopie zapasowe i cron

Baza SQLite pracuje w trybie WAL. **Nie kopiuj otwartego pliku .db zwykłym cp**,
bo pomija to zapisane jeszcze rekordy w pliku -wal. Skrypt wykonuje
spójną kopię komendą serwerową `admin backup` (`VACUUM INTO` SQLite)
i przenosi ją **poza wolumen Dockera**:

~~~bash
sudo mkdir -p /var/backups/century-war
sudo bash /opt/century-war/server/backup-sqlite.sh
sudo ls -lh /var/backups/century-war/
sudo crontab -e
~~~

Dodaj w crontab jedną linię (codziennie 03:00 czasu serwera):

~~~cron
0 3 * * * /usr/bin/bash /opt/century-war/server/backup-sqlite.sh >> /var/log/cw-backup.log 2>&1
~~~

Skrypt używa serwerowej komendy `admin backup` (`VACUUM INTO`) do
spójnego zrzutu SQLite w trybie WAL, następnie `docker cp` do katalogu
poza wolumenem. Zatrzymuje się przy błędzie i usuwa **lokalne**
kopie starsze niż 14 dni. Co jakiś czas sprawdź kopię poleceniem
`sqlite3 /sciezka/do/kopii.sqlite 'PRAGMA integrity_check;'` (wynik `ok`)
i przetestuj przywrócenie na **osobnym testowym serwerze**.
Przechowuj także szyfrowaną kopię **poza VPS** (inna maszyna / zewnętrzny storage), z ograniczonym dostępem,
retencją i procedurą usuwania danych. W przeciwnym razie awaria dysku VPS
zniszczy bazę i kopie razem. Monitoruj nieudane zadania cron i miejsce na dysku.
Odtworzenie starej kopii może przywrócić rekordy graczy, którzy później
żądali usunięcia; po odtworzeniu wykonaj `admin replay-deletions` (§7).

### 6a. Kopia poza serwerem (rclone, szyfrowana)

Skrypt wysyła kopię dalej, gdy ustawisz `CW_BACKUP_RCLONE_REMOTE`. `rclone`
i `curl` działają **na hoście**, nie w kontenerze (obraz bez zmian).
Rekomendacja: Backblaze B2 (10 GB za darmo, baza ma kilka MB) albo Hetzner
Storage Box. U dostawcy zaakceptuj umowę powierzenia danych (DPA).

1. Na VPS: `sudo apt install -y rclone` (albo instalator z rclone.org).
2. U dostawcy utwórz **osobny kubełek** (np. `century-war-backup`) i klucz
   dostępu ograniczony do tego jednego kubełka.
3. `sudo rclone config` (konfiguracja trafi do `/root/.config/rclone/rclone.conf`):
   - `n` → nazwa `b2` → typ `b2` (albo `sftp` dla Storage Box) → podaj klucz;
   - `n` → nazwa `b2crypt` → typ `crypt` → remote `b2:century-war-backup`
     → szyfrowanie nazw plików `standard` → **wygeneruj dwa hasła**.
   Oba hasła `crypt` zapisz w menedżerze haseł właściciela. **Bez nich kopii
   zdalnej nie da się odczytać.**
4. Próba: `sudo rclone lsd b2crypt:` (bez błędu) i ręczne uruchomienie skryptu
   z ustawioną zmienną:
   ~~~bash
   sudo env CW_BACKUP_RCLONE_REMOTE=b2crypt:century-war bash /opt/century-war/server/backup-sqlite.sh
   sudo rclone ls b2crypt:century-war
   ~~~
5. Wpisz zmienne w `sudo crontab -e` **nad** linią z kopią (cron nie czyta `.env`):
   ~~~cron
   CW_BACKUP_RCLONE_REMOTE=b2crypt:century-war
   CW_BACKUP_REMOTE_RETENTION_DAYS=30
   CW_BACKUP_PING_URL=https://hc-ping.com/WSTAW_UUID_Z_HEALTHCHECKS
   0 3 * * * /usr/bin/bash /opt/century-war/server/backup-sqlite.sh >> /var/log/cw-backup.log 2>&1
   ~~~

Po udanej kopii lokalnej skrypt robi `rclone copy`, potem usuwa zdalne kopie
starsze niż `CW_BACKUP_REMOTE_RETENTION_DAYS` (domyślnie **30 dni**). Każdy
błąd (także `rclone`) kończy skrypt kodem ≠ 0 i wysyła `…/fail` na
`CW_BACKUP_PING_URL` (§10). Lokalnie zostaje 14 dni (`CW_BACKUP_RETENTION_DAYS`).

**Test odtworzenia z kopii zdalnej (raz w miesiącu, ok. 10 minut):**

~~~bash
mkdir -p /tmp/cw-restore-test && cd /tmp/cw-restore-test
sudo rclone copy --max-age 2d b2crypt:century-war .
ls -l
sqlite3 cw-backup-*.sqlite 'PRAGMA integrity_check;'          # oczekiwane: ok
sqlite3 cw-backup-*.sqlite 'SELECT COUNT(*) FROM scores;'     # liczba wyników
cd / && sudo rm -rf /tmp/cw-restore-test
~~~

Gdy pobrano kilka plików, sprawdź jeden (najnowszy). Kopie zawierają dane
osobowe: nie kopiuj ich na prywatny komputer i usuń katalog testowy.

## 7. Aktualizacja i bezpieczne przywracanie

Przed aktualizacją wykonaj kopię według kroku 6 i zapisz działający
commit obrazu. W pliku `sim/src/main/kotlin/com/centurywar/game/SimVersion.kt`
sprawdź stałą `SimVersion.PROTOCOL` — korzystają z niej i serwer,
i `ScoreClient.GAME_VERSION`. Zmiany zasad bitwy wymagają skoordynowania
wdrożenia serwera i nowego APK/AAB. Starsze aplikacje mogą dostać
`unsupported_version` (HTTP 400); najpierw testuj na kanale wewnętrznym.
**Nie podnoś tej stałej samodzielnie w ramach prac wdrożeniowych.**
Następnie:

~~~bash
cd /opt/century-war
git fetch origin
# Sprawdź i wybierz stabilne wydanie/gałąź zawierające nowe zmiany.
git pull --ff-only
docker compose up -d --build
docker compose ps
curl -f http://127.0.0.1:8080/healthz
~~~

Przy **pierwszym** uruchomieniu wersji ze zleceniem 43 serwer tworzy tabelę
schema_version i w jednej transakcji zmienia historyczne plansze
campaign_8..31 na campaign_12..35. Migracja działa **tylko raz**.
Baza musi pochodzić ze starej numeracji, a nie z już poprawionego schematu.
Konflikt danych zatrzymuje start bez częściowej migracji. Przy niepewności
najpierw testuj na kopii produkcyjnej.

**Schemat 3 (zadanie R5):** pierwsze uruchomienie tej wersji dodaje tabele
`deletion_log`, `admin_log` i kolumnę `nick_reports.resolved_at`. Starszy obraz
serwera **nie otworzy** bazy w schemacie 3 (`unsupported_schema_version`), więc
wycofanie wersji oznacza odtworzenie kopii sprzed aktualizacji.

### Budowa obrazu poza VPS (VPS z 2 GB RAM)

Gradle potrzebuje ok. 4 GB RAM. Obraz możesz zbudować na swoim komputerze
(Docker, architektura x86_64 jak na VPS) i wysłać go przez SSH:

~~~bash
# na swoim komputerze, w katalogu repozytorium (ta sama gałąź/tag co na VPS)
git rev-parse --short HEAD
docker build --platform linux/amd64 -f server/Dockerfile -t century-war-score:local .
docker save century-war-score:local | gzip | ssh admin@scores.twojadomena.pl 'gunzip | docker load'
# na VPS
cd /opt/century-war
git pull --ff-only                       # ten sam commit co obraz (compose i skrypty)
docker compose up -d --no-build
docker compose ps
~~~

`--no-build` zapobiega budowie na VPS. Nazwa `century-war-score:local` musi
zgadzać się z `image:` w `docker-compose.yml`. Zapisz commit, z którego
zbudowano obraz, żeby w razie problemu wrócić do poprzedniego.

### Harmonogram aktualizacji (raz w miesiącu, ok. 30 minut)

1. Kopia (§6) i sprawdzenie, że skrypt wysłał ją poza serwer.
2. System: `sudo apt update && sudo apt upgrade -y`, a po aktualizacji jądra
   `sudo reboot` (w Ubuntu włącz też `unattended-upgrades` dla poprawek
   bezpieczeństwa).
3. Przebudowa obrazu bez cache, żeby pobrać nowy `eclipse-temurin` z poprawkami:
   `docker build --pull --no-cache ...` (u siebie) albo
   `docker compose build --pull --no-cache` (na VPS z 4 GB RAM), potem `up -d`.
4. Przegląd wersji zależności w `server/build.gradle.kts` (Ktor, sqlite-jdbc,
   kotlinx-serialization): komunikaty bezpieczeństwa na stronach projektów.
   **Podnoszenie wersji to osobne zlecenie z pełnymi testami serwera**
   (`./gradlew :server:test -PserverOnly=true`), nie część rutynowej obsługi.
5. `curl -fsS https://scores.twojadomena.pl/health` i `docker compose logs --tail=50 leaderboard`.

**Przywracanie z backupu (przerwa serwisowa):** zatrzymaj serwer (**bez -v**)
i przekaż kopię komendą `admin restore -`. Poprzednia baza nie znika: trafia do
katalogu ratunkowego `cw-pre-restore-*` na wolumenie, a jej dziennik usunięć
posłuży do powtórzenia usunięć.

~~~bash
cd /opt/century-war
docker compose stop leaderboard
docker compose run --rm --no-deps -T leaderboard admin restore - \
  < /var/backups/century-war/WSTAW_NAZWE_KOPII.sqlite
# zanotuj wypisaną linię previous-files: /var/lib/cw/cw-pre-restore-…
~~~

**Powtórzenie usunięć (zanim ranking znów będzie publiczny).** Kopia nie zna
usunięć wykonanych po jej utworzeniu (w grze, przez `delete-player` i retencję).
Serwer zapisuje je w `deletion_log` jako skrót HMAC identyfikatora (nie surowy
UUID). Podaj datę kopii (dzień UTC z nazwy pliku `cw-backup-RRRRMMDD-…`) i bazę
sprzed odtworzenia:

~~~bash
docker compose run --rm --no-deps -T leaderboard admin replay-deletions \
  --since 2027-01-14 --from /var/lib/cw/cw-pre-restore-WSTAW/leaderboards.db
# entries: N           – ile usunięć od tej daty zna nowsza baza
# deleted_players: M   – ilu z tych graczy było w odtworzonej kopii i zostało usuniętych
docker compose up -d leaderboard
curl -f http://127.0.0.1:8080/healthz
~~~

Komenda jest bezpieczna do powtórzenia (drugi raz usuwa 0 graczy) i dopisuje
wpisy do dziennika odtworzonej bazy. Jeśli **poprzednia baza przepadła** (np.
awaria dysku i odtworzenie z kopii zdalnej), usunięć z ostatniej doby nie da się
odzyskać z dziennika: wykonaj ponownie `delete-player` dla próśb e-mailowych
z tego okresu (§8) i odnotuj zdarzenie w rejestrze incydentów. Blokady zostają
takie, jak w kopii; ponów `admin block` nadane po dacie kopii (widać je w
`admin log` starej bazy). Jeśli obraz nie może zapisać pliku, sprawdź uprawnienia
wolumenu (kontener działa jako `century`).

## 8. Żądanie usunięcia danych gracza

W grze: **Ustawienia → Nowa tożsamość → Potwierdź**. Aplikacja zapisuje
żądanie usunięcia starego UUID trwale na urządzeniu i nadaje nowy UUID.
Gdy sieć nie działa, próbuje ponownie również po restarcie, niezależnie
od zgody na przesyłanie nowych wyników. Zmiana identyfikatora **nie jest
potwierdzeniem usunięcia na serwerze**; stare dane mogą być widoczne
do zakończenia wysyłki. Nie usuwaj pamięci aplikacji ani jej nie odinstalowuj,
jeśli żądanie jest jeszcze niewysłane.

Endpoint POST /v1/player/delete usuwa ze wszystkich plansz wyniki i pseudonim,
idempotentnie, **tylko gdy podano sekret instalacji przypisany do UUID**.
Nowa tożsamość nadaje nowy UUID i sekret, a w kolejce usunięć zachowuje
poprzedni sekret. Stare rekordy bez sekretu wymagają pierwszego zweryfikowanego
wyniku przed usunięciem. Po stronie serwera nie ma odrębnego konta ani adresu e-mail.
Wynik weryfikowany w chwili usunięcia nie odtwarza danych: serwer odrzuca go
kodem 409 `player_deleted` (aplikacja nie ponawia odrzuconych zgłoszeń 4xx).
Dane Google Play Games, lokalny profil i systemowe kopie Androida **nie są
usuwane przez tę operację**. Wnioski i prośby o potwierdzenie usunięcia można
kierować na adres kontaktowy wydawcy z opublikowanej polityki prywatności.
Przed publikacją uzupełnij ten adres i przetestuj rzeczywisty proces.

### 8a. Prośba e-mailowa (gracz zgubił telefon albo nie ma dostępu do gry)

Termin: **do 30 dni** od otrzymania prośby (polityka prywatności). Operator
zna tylko nick, więc:

1. Odpowiedz z prośbą o: nick **dokładnie jak w rankingu** (także „Dowódca-XXXX”,
   jeśli filtr zamienił nick), przybliżoną datę ostatniej gry i tryb (np. Endless).
   Nie proś o dane, których serwer nie ma (e-mail, imię, numer telefonu).
2. Kopia przed usunięciem: `sudo bash /opt/century-war/server/backup-sqlite.sh`.
3. Znajdź gracza (bez rozróżniania wielkości liter, także bez polskich znaków):
   ~~~bash
   docker compose run --rm --no-deps -T leaderboard admin find-nick Kowalski
   # player  public_id  nick  scores  last_activity
   ~~~
   Kilka trafień: wybierz wiersz zgodny z datą ostatniej aktywności i trybem
   (`admin export-csv | grep '^"UUID"'` pokaże wyniki danego UUID). Brak pewności
   → dopytaj gracza; **nie usuwaj na ślepo cudzych danych**. Komunikat `hint:`
   oznacza, że taki nick zamienia filtr — poproś o nick „Dowódca-XXXX”.
4. Usuń wszystko (wyniki, nick, zgłoszenia, domyślnie też blokadę):
   ~~~bash
   docker compose run --rm --no-deps -T leaderboard admin delete-player UUID_TUTAJ
   # deleted (kod 0) albo not_found (kod 1)
   ~~~
   `--keep-block` zostawia blokadę, gdy prośba przychodzi od zablokowanego gracza
   i blokada ma dalej działać (zapisany zostaje wtedy tylko UUID z datą blokady).
5. Odpowiedz graczowi, że dane usunięto, z datą. Usunięcie trafia do
   `deletion_log` (skrót, nie UUID) i `admin log`; zniknie z kopii po upływie
   ich retencji (lokalnie 14 dni, poza serwerem 30 dni).
6. Dostęp do danych (art. 15 RODO): zamiast usuwać, wyślij graczowi wiersze
   `admin export-csv` dla jego UUID (zaszyfrowany plik, bez innych graczy).

## 9. Moderowanie pseudonimów i wyników (CLI)

**Nie ma panelu WWW ani administracyjnego endpointu API.** Dostęp do komend
ma wyłącznie operator z dostępem SSH i uprawnieniami do Docker Compose oraz
wolumenu SQLite. Nie udostępniaj takich uprawnień graczom.

Serwer czyści pseudonimy na podstawie pliku
server/src/main/resources/forbidden-nicks.txt (10 języków: pl, en, de, es,
fr, it, pt, uk, nl, tr). Filtr ignoruje wielkość liter, akcenty, część
zamienników cyfr/znaków (0→o, 1→i, @→a) i rozdzielanie liter znakami.
Zakazany albo niepoprawny nick jest zamieniany na **Dowódca-XXXX**, gdzie
XXXX to pierwsze 4 znaki szesnastkowe skrótu SHA-256 UUID gracza. Wynik gry
nadal jest zapisywany, a API dla Androida pozostaje bez zmian. Stare nicki
są filtrowane także podczas odczytu rankingu. Słownik można rozszerzać
w repozytorium i wdrażać kolejną wersją obrazu. Może wystąpić nadmierne
blokowanie lub przeoczenie nowych form zapisu; sprawdzaj zgłoszenia ręcznie.

**Krok 1.** Zaloguj się na VPS, sprawdź usługę i wykonaj spójną kopię bazy
(zgodnie z krokiem 6), zanim cokolwiek usuniesz:

~~~bash
cd /opt/century-war
docker compose ps
sudo bash /opt/century-war/server/backup-sqlite.sh
docker compose run --rm --no-deps -T leaderboard admin help
~~~

**Krok 2.** Sprawdź zgłoszenia pseudonimów (`POST /v1/report` z publicznym ID).
Administrator widzi UUID, publiczne ID, nick, powód i datę; gracze nie otrzymują UUID.

~~~bash
docker compose run --rm --no-deps -T leaderboard admin reports 50
docker compose run --rm --no-deps -T leaderboard admin reports --all 50   # także rozpatrzone
~~~

Domyślnie widać tylko **nierozpatrzone** zgłoszenia. Po decyzji (blokada,
usunięcie albo brak działania) oznacz zgłoszenie numerem z kolumny `id`:

~~~bash
docker compose run --rm --no-deps -T leaderboard admin resolve-report 12
# resolved / unchanged (już rozpatrzone) / not_found (kod 1)
~~~

**Krok 2a.** Obejrzyj ostatnie wyniki. Zwrócone kolumny: UUID, plansza,
day (liczba dni UTC od 1970-01-01), wynik, czas weryfikacji Unix,
stan blokady i publiczny nick. Zapisz UUID i planszę dokładnie z listy:

~~~bash
docker compose run --rm --no-deps -T leaderboard admin recent 20
docker compose run --rm --no-deps -T leaderboard admin recent 100
~~~

**Krok 3.** Zablokuj gracza na wszystkich planszach albo odblokuj po
weryfikacji zgłoszenia. Blokada działa bez restartowania serwera: istniejące
wyniki znikają z /v1/top i /v1/around, a nowe zgłoszenia otrzymują HTTP
403 blocked_player. Odblokowanie przywraca poprzednie wpisy (o ile ich
wcześniej nie usunięto):

~~~bash
docker compose run --rm --no-deps -T leaderboard admin block UUID_TUTAJ
docker compose run --rm --no-deps -T leaderboard admin unblock UUID_TUTAJ
~~~

**Krok 4.** Aby usunąć jeden wynik, podaj UUID, nazwę planszy i day
**z konkretnego wiersza** listy (nie numer tygodnia ani aktualną datę).
deleted oznacza sukces; not_found (kod zakończenia 1) — brak takiego
rekordu. Usunięcia nie można cofnąć inaczej niż przez kontrolowany backup:

~~~bash
docker compose run --rm --no-deps -T leaderboard admin delete-score UUID_TUTAJ endless 20000
~~~

**Krok 5.** Wyeksportuj wszystkie wyniki, również zablokowane, do CSV.
CSV jest wypisywane na stdout i zawiera UUID, pseudonim, planszę, wynik,
dane weryfikacji oraz kolumnę blocked. Dane mogą podlegać RODO, więc
ogranicz dostęp do pliku i ustal termin usunięcia. Eksport jest odpowiednio
cytowany i zabezpieczony przed interpretacją niebezpiecznych wartości jako
formuł w arkuszu:

~~~bash
umask 077
docker compose run --rm --no-deps -T leaderboard admin export-csv > ranking-moderacja.csv
chmod 600 ranking-moderacja.csv
~~~

Komendy administracyjne działają na **tym samym wolumenie** co kontener
serwera; nie wymagają klucza HMAC i nie uruchamiają serwera HTTP. Nie
wykonuj ich na kopii .db pobranej zwykłym cp przy aktywnym WAL.
Błędny UUID albo plansza nie wykonują operacji.

**Dziennik operatora:** `block`, `unblock`, `delete-score`, `delete-player`,
`purge` i `replay-deletions` zapisują wpis w `admin_log` (czas, akcja, skrót
gracza, szczegóły; bez surowego UUID). Podgląd: `admin log 50`.

**Blokada jest zachowywana:** /v1/player/delete usuwa wyniki i nick,
ale nie usuwa rekordu blokady UUID. Historyczny UUID może być przejęty
przed pierwszym przypisaniem sekretu (migracja ze starej bazy);
operator rozstrzyga spory ręcznie.

## 10. Codzienna obsługa

- docker compose ps — czy kontener ma status healthy.
- docker compose logs --tail=100 leaderboard — błędy aplikacji (nie publikuj logów z danymi).
- df -h — wolne miejsce.
- sudo ls -lt /var/backups/century-war/ — sprawdź, czy powstają nowe kopie.
- sudo systemctl status caddy — certyfikat i proxy.

- docker compose logs leaderboard | grep retention — dobowe czyszczenie danych (§13), same liczby.

### Monitoring zewnętrzny i alerty (darmowe konta)

Healthcheck Dockera tylko restartuje kontener; o awarii nikt by się nie
dowiedział. Załóż na **e-mail właściciela**:

1. **Dostępność** (UptimeRobot albo Healthchecks.io): monitor HTTP(S) na
   `https://scores.twojadomena.pl/health`, co **5 minut**, oczekiwana odpowiedź
   200 i treść `ok`.
2. **Kopie** (Healthchecks.io): check „cw-backup”, okres 1 dzień, tolerancja
   2 godziny; jego adres ping wpisz jako `CW_BACKUP_PING_URL` (§6a). Brak pingu
   albo ping `/fail` = e-mail.

**Co zrobić, gdy przyjdzie e-mail z alertem (prostym językiem):**

- **„Serwer nie odpowiada” (health down):** gracze nie wyślą wyników, ale gra
  działa dalej offline, a wyniki czekają w aplikacji. Zaloguj się (albo poproś
  koordynatora) i przejdź „Awaria — diagnostyka” poniżej. Jeśli alert sam
  zniknie po 5–10 minutach, wystarczy sprawdzić `docker compose logs`.
- **„Kopia nie przyszła” albo „fail” (backup):** dane są bezpieczne, dopóki
  działa serwer, ale nie ma świeżej kopii. Sprawdź `tail -50 /var/log/cw-backup.log`:
  `Brak uruchomionego kontenera` → najpierw serwer; błąd `rclone` → klucz/kubełek
  u dostawcy kopii (ważność klucza, opłaty); `No space left` → `df -h`.
  Napraw tego samego dnia i uruchom skrypt ręcznie; drugi dzień bez kopii to
  priorytet.

**Ograniczenia produkcyjne:** jedna instancja SQLite, ochrona DDoS na proxy,
ryzyko utraty sekretu instalacji wraz z profilem, możliwość przejęcia znanego
historycznego UUID przed przypisaniem sekretu i retencja backupów.
Przed startem wykonaj testy /score, /around, /report i /player/delete.


### Awaria — diagnostyka w kolejności

1. Sprawdź **DNS i TLS**: czy rekord A wskazuje VPS, a `curl -fsS https://scores.twojadomena.pl/health` zwraca `ok`.
2. Sprawdź **kontener i bazę**: `docker compose ps`, `docker compose logs --tail=200 leaderboard`, `curl -i http://127.0.0.1:8080/health`.
3. Sprawdź **brak pamięci/dysku**: `free -m`, `df -h`, `docker stats --no-stream` oraz status Caddy: `sudo systemctl status caddy`.
4. Gdy kontener jest zatrzymany i logi nie pokazują błędu migracji, użyj `docker compose up -d`. Gdy TLS nie działa, sprawdź `sudo journalctl -u caddy -n 100 --no-pager`.
5. Jeśli nowe wydanie psuje bazę lub odtwarzanie, **nie usuwaj wolumenu**. Zachowaj logi i aktualną bazę, wycofaj obraz do znanego commita, a kopię odtwarzaj wyłącznie zgodnie z krokiem 7 (z rejestracją ponownych żądań usunięcia danych).

**Nie wykonuj `docker compose down -v`**: usuwa trwały wolumen SQLite.
Jeżeli widzisz HTTP 503 `verification_busy`, ogranicz tempo zgłoszeń/obciążenie
i sprawdź CPU, czasy z `/metrics` (tylko z poprawnym prywatnym tokenem).
Nie zwiększaj limitu 15 s bez nowego pomiaru.
## 11. Wydajność weryfikacji powtórek (zlecenie 121)

**Pomiar 01.11.2026, [GitHub Actions — benchmark 121](https://github.com/woogitsugames/Century-war/actions/runs/41341222738):**
Ubuntu Linux (`ubuntu-latest`), AMD EPYC 7763 64-Core Processor, Java 21.0.13,
jeden logiczny procesor (afinityzacja `taskset`, dodatkowo
`-XX:ActiveProcessorCount=1`), `-Xmx512m` (raportowana maksymalna
sterta 494 MiB). Dwie rozgrzewki JIT, następnie pięć osobnych weryfikacji.

Syntetyczna powtórka ma **432 000 kroków**, **30 945 komend** i 123 791 bajtów
zakodowanego Base64 URL; Endless osiągnął falę 432. Wynik `Replay.verify`
zgadzał się z wynikiem nagrywania we wszystkich siedmiu przebiegach.

| Pomiar | Pięć próbek (ms) | Mediana | Maksimum |
|---|---|---:|---:|
| `Replay.verify` | 1911, 1924, 1911, 1893, 1933 | **1911 ms** | **1933 ms** |
| `Replay.decode + verify` | 1915, 1928, 1912, 1893, 1934 | **1915 ms** | **1934 ms** |

**Ograniczenie metody:** po fali 120 dyrektor Endless zadaje bazie
obrażenia proporcjonalne do jej maksymalnego HP; w normalnej rozgrywce
testowa baza była niszczona już po 159 869 krokach. Do pomiaru *górnego
limitu liczby kroków* użyto więc wyłącznie w syntetycznym teście
`Bonuses(baseHpMul = Float.POSITIVE_INFINITY)`. Taka niefizyczna
konfiguracja **nie przejdzie walidacji normalnego zgłoszenia rankingu**
i nie mierzy czasu pojedynczej legalnej gry ani najgorszego możliwego
obciążenia przez wszystkie komendy. Nie należy na tej podstawie
deklarować gwarancji czasu na VPS. Na serwerze rankingowym zgłoszenie
z badaniami może wymagać **drugiej** symulacji do normalizacji wyniku.

**Wniosek:** przy tym scenariuszu maksimum 1,933 s daje zapas do
aktualnego deadline'u 15 s; nie trzeba zmieniać `Replay.MAX_STEPS`,
wersji protokołu ani zasad gry. `VerificationGate` już ogranicza
weryfikacje do maksymalnie jednej na wykrywany vCPU (do 8 wątków),
ma ograniczoną kolejkę 16, przerywa po 15 s i zwraca HTTP **503**
z `Retry-After: 30` po przeciążeniu lub przekroczeniu budżetu.
Nie używać nieograniczonej puli ani kolejki.

**Zalecenia dla taniego VPS:** na 1 vCPU zacząć od jednej równoległej
weryfikacji i sterty JVM `-Xmx512m` (np. `JAVA_TOOL_OPTIONS=-Xmx512m
-XX:ActiveProcessorCount=1`). Zapewnić zapas RAM na SQLite, Ktor,
system i proxy, monitorować `verification_busy`, czasy z `/metrics`
oraz GC. Na 2 vCPU nie zakładać automatycznie liniowego przyspieszenia;
powtórzyć benchmark przy realnym obciążeniu, szczególnie z
normalizacją badań i równoległymi żądaniami. Przed zmianą timeoutu lub
limitów ponowić pomiary na **rzeczywistym VPS**.

**Powtórzenie pomiaru** (na Linux, uruchomienie poza zwykłym `server:test`):

~~~bash
CPU=$(awk '/^Cpus_allowed_list:/ {split($2, n, /[,-]/); print n[1]}' /proc/self/status)
SERVER_BENCH=true taskset -c "$CPU" ./gradlew :server:test -PserverOnly=true \
  --tests '*ServerReplayBenchmarkTest*' --console=plain --no-daemon --no-configuration-cache
~~~

Zwykłe `./gradlew :server:test -PserverOnly=true` pomija benchmark.

## 12. Ponowny benchmark z dowódcą i stopniami wież (zlecenie 149)

Test `ServerReplayBenchmarkTest` jest wyłączony w zwykłym CI i uruchamia się
wyłącznie przy `SERVER_BENCH=true`. W scenariuszu ma 432 000 kroków,
przywołanie dowódcy (`SUMMON_COMMANDER`) i dwa ulepszenia stopni jednej wieży
(`TURRET_TIER`, stopień 3); rejestruje je wyłącznie `BattleController`.
Sprawdza zgodność wyniku nagrania z siedmioma weryfikacjami, z czego
dwie rozgrzewają JIT, a pięć jest mierzonych. CI izoluje **jeden vCPU**
przez `taskset` oraz `-XX:ActiveProcessorCount=1`, z `-Xmx512m`.

**Pomiar 02.11.2026 — [GitHub Actions, benchmark 149](https://github.com/woogitsugames/Century-war/actions/runs/41447513413):**
Ubuntu Linux, AMD EPYC 7763 (procesor współdzielonego runnera), Java 21.0.13,
`taskset` ograniczony do jednego procesora logicznego, `-XX:ActiveProcessorCount=1`,
`-Xmx512m` (raportowana maksymalna sterta 494 MiB).
Syntetyczne Endless: **432 000 kroków**, **30 952 komendy**, 123 818 bajtów
Base64 URL, fala 432, `Replay.VERSION=7`; w logu dokładnie jedno
`SUMMON_COMMANDER`, jedno `BUY_TURRET` i dwa `TURRET_TIER`.
Wszystkie siedem weryfikacji odtworzyło tę samą liczbę kroków i wynik co nagranie.

| Operacja | Pięć pomiarów (ms) | Mediana | Maksimum |
|---|---|---:|---:|
| `Replay.verify` | 925, 929, 906, 917, 904 | **917 ms** | **929 ms** |
| `Replay.decode + verify` | 932, 933, 907, 918, 905 | **918 ms** | **933 ms** |

**Wniosek:** w tej próbce pojedyncze `Replay.verify` trwało maksymalnie
**0,929 s**, znacznie poniżej budżetu **15 s**. Nie ma podstaw do zmiany
limitu kroków, timeoutu ani protokołu. To pomiar pojedynczego syntetycznego
przebiegu, **nie** pomiar najgorszego legalnego zgłoszenia na 1-vCPU VPS.
Po doliczeniu czasu oczekiwania w kolejce, ewentualnego drugiego przebiegu
normalizacji badań i zmiennej wydajności współdzielonego CPU margines może
się zmniejszyć. Przed udostępnieniem serwera zmierz maksymalny czas na
wybranym VPS przy równoległych żądaniach i obserwuj HTTP 503 oraz `/metrics`.

```bash
cpu=$(awk '/^Cpus_allowed_list:/ {split($2, a, /[,-]/); print a[1]}' /proc/self/status)
SERVER_BENCH=true taskset -c "$cpu" ./gradlew :server:test -PserverOnly=true \
  --tests '*ServerReplayBenchmarkTest*' --console=plain --no-daemon --no-configuration-cache
```

Wynik jest testem **syntetycznym**: `baseHpMul = Float.POSITIVE_INFINITY`
zapobiega zakończeniu Endless przez presję po fali 120. Taka konfiguracja
nie przechodzi walidacji API. Pojedyncza weryfikacja nie obejmuje
drugiego przebiegu normalizacji badań, oczekiwania w kolejce ani działania
innych klientów. Limit **15 s** dotyczy całego budżetu od wejścia do kolejki.

## 13. Retencja danych (RODO) i lista przed premierą

Serwer sam usuwa dane po okresie przechowywania: **przy starcie i raz na dobę**
(wątek w procesie serwera, w logach linia `retention: …` z samymi liczbami).
Okresy ustawiasz w `.env` (dni, `0` = krok wyłączony). **Wartości domyślne są
propozycją do potwierdzenia przez właściciela** (najlepiej z prawnikiem):

| Zmienna | Domyślnie | Co usuwa |
|---|---|---|
| `CW_RETENTION_PERIOD_DAYS` | 90 | wyniki tablicy dnia (`daily`, liczone od dnia wyzwania) i tygodnia (`weekly_<n>`, liczone od **końca** tygodnia) |
| `CW_RETENTION_REPORT_DAYS` | 180 | zgłoszenia nicków (także rozpatrzone) |
| `CW_RETENTION_INACTIVE_DAYS` | 730 | gracza bez nowego wyniku przez 24 miesiące — w całości (wszystkie tablice, nick, zgłoszenia), z wpisem w dzienniku usunięć; blokada bez konta starsza niż ten okres też wygasa |
| `CW_RETENTION_LOG_DAYS` | 400 | wpisy dziennika usunięć i dziennika operatora |

Tablice stałe (`endless`, `skirmish`, `campaign_*`) **nie** są czyszczone wiekiem,
tylko razem z nieaktywnym graczem. Przy każdym przebiegu znikają też skróty IP
do dziennego limitu z poprzednich dni i zużyte `nonce`.

Podgląd bez usuwania i ręczne uruchomienie:

~~~bash
docker compose run --rm --no-deps -T leaderboard admin purge --dry-run
# dry-run daily_scores=… weekly_scores=… nick_reports=… inactive_players=… …
docker compose run --rm --no-deps -T leaderboard admin purge
~~~

Usunięcie jest **nieodwracalne** (poza odtworzeniem kopii). Przed pierwszym
uruchomieniem na produkcji zrób kopię (§6) i obejrzyj `--dry-run`. Po zmianie
wartości w `.env`: `docker compose up -d` (restart wykona przebieg od razu).
Usunięte dane znikają z kopii po upływie retencji kopii (14 dni lokalnie,
30 dni poza serwerem).

### Przed premierą (kolejność)

1. Właściciel: VPS w UE (Hetzner albo OVH), domena, DPA u hostingu i dostawcy kopii.
2. Instalacja §2–§4, sekrety w menedżerze haseł, `CW_TRUSTED_PROXY_IPS` (§4).
3. Kopia lokalna + zdalna (§6, §6a) i jeden test odtworzenia z kopii zdalnej.
4. Monitory dostępności i kopii (§10) z e-mailem właściciela; test: zatrzymaj
   kontener na 10 minut i sprawdź, czy przyszedł e-mail.
5. Potwierdzone okresy retencji w `.env` i w polityce prywatności (te same liczby),
   adres e-mail do próśb o usunięcie w polityce; próba procedury §8a na testowym nicku.
6. Dopiero potem `CW_SERVER_URL` w buildzie wydania.
