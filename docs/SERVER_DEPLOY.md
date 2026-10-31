# Century War — wdrożenie serwera rankingów na VPS

Instrukcja dla właściciela gry. Własny ranking jest **opcjonalny**. VPS jest
niezbędny tylko do funkcji rankingu online, nie do grania offline.
Potrzebujesz domeny, serwera VPS, konta z możliwością SSH oraz kodu repozytorium.

## 1. Zakup serwera i domena

1. Wybierz VPS z **Ubuntu 24.04 LTS**, publicznym adresem IPv4, około 2 vCPU,
   **4 GB RAM do kompilacji obrazu Docker** i przynajmniej 20 GB SSD.
   Po zbudowaniu obrazu sam serwer potrzebuje znacznie mniej zasobów.
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

Użyj gałęzi lub wydania, do którego scalono zlecenie 43.
**Przed pierwszym uruchomieniem upewnij się, że ten kod jest wdrożony**.
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

Wklej wygenerowane 64 znaki w polu CW_HMAC_KEY. Ustaw
CW_GAME_VERSION zgodnie z **ScoreClient.GAME_VERSION w Androidzie**
(obecnie 0.3.0). Nie zapisuj .env do repozytorium, nie wysyłaj go w zgłoszeniach.
Klucz ten jest również w APK, a więc **nie zapewnia mocnego uwierzytelnienia**.
Ta wersja API pozwala każdemu posiadającemu klucz na żądanie usunięcia
dowolnego UUID; przed dużą skalą konieczny będzie silniejszy dowód własności.

~~~bash
docker compose up -d --build
docker compose ps
curl -f http://127.0.0.1:8080/healthz
~~~

Odpowiedź healthz powinna brzmieć **ok**. Kontener ma restart: unless-stopped,
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
curl -f https://scores.twojadomena.pl/healthz
~~~

Caddy automatycznie wystawia i odnawia certyfikat TLS przy poprawnym DNS
i otwartych portach 80/443. Port 8080 pozostaje niewidoczny z Internetu.
Caddy ogranicza rozmiar body do 1 MB. **Stockowy Caddy nie ma w tym
przykładzie per-IP rate limit**. Serwer przechowuje limity IP w pamięci,
ale za reverse proxy widzi adres proxy, nie oryginalnego klienta, więc przy
większym ruchu limit będzie współdzielony. Przed wzrostem liczby graczy
wprowadź na zaufanej warstwie proxy realny limit per-IP i bezpieczną konfigurację
źródłowego adresu; nie ufaj dowolnemu X-Forwarded-For.

## 5. Podłączenie Androida

W prywatnym local.properties albo w zmiennych CI dla budowy aplikacji ustaw:
CW_SERVER_URL=https://scores.twojadomena.pl,
CW_PRIVACY_URL=https://twojadomena.pl/store/privacy-policy-pl/
(oraz faktyczny publiczny adres polityki prywatności) i CW_HMAC_KEY,
**taki sam jak na serwerze**. W Google Play i samej aplikacji podaj działający
link do polityki prywatności. Użytkownik musi sam wyrazić zgodę na ranking.
Bez kompletu konfiguracji własny ranking jest wyłączony.

Po wdrożeniu sprawdź podpisane żądania GET /v1/top oraz POST /v1/score,
a także usuwanie tożsamości. GET /healthz nie wymaga podpisu; pozostałe
wymagają nagłówków opisanych w server/README.md.
Wersje klienta, serwera i symulacji muszą się zgadzać.

## 6. Kopie zapasowe i cron

Baza SQLite pracuje w trybie WAL. **Nie kopiuj otwartego pliku .db zwykłym cp**,
bo pomija to zapisane jeszcze rekordy w pliku -wal. Skrypt wykonuje
spójną kopię poleceniem sqlite3 .backup i przenosi ją **poza wolumen Dockera**:

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

Skrypt zatrzymuje się przy błędzie, robi jedną spójną kopię i usuwa
**lokalne** pliki starsze niż 14 dni. Przechowuj także szyfrowaną kopię
**poza VPS** (inna maszyna / zewnętrzny storage), z ograniczonym dostępem,
retencją i procedurą usuwania danych. W przeciwnym razie awaria dysku VPS
zniszczy bazę i kopie razem. Monitoruj nieudane zadania cron i miejsce na dysku.
Odtworzenie starej kopii może przywrócić rekordy graczy, którzy później
żądali usunięcia; prowadź procedurę ponownego wykonania takich żądań.

## 7. Aktualizacja i bezpieczne przywracanie

Przed aktualizacją wykonaj kopię według kroku 6. Następnie:

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

**Przywracanie z backupu (przerwa serwisowa):** zamknij zapis do bazy
poleceniem docker compose down (**bez -v**). Uruchom jednorazowy kontener
z tym samym wolumenem i podmontowaną kopią tylko do odczytu:

~~~bash
cd /opt/century-war
docker compose down
docker compose run --rm --no-deps \
  -v /var/backups/century-war:/restore:ro \
  --entrypoint /bin/sh leaderboard \
  -c 'rm -f /var/lib/cw/leaderboards.db-wal /var/lib/cw/leaderboards.db-shm && cp /restore/WSTAW_NAZWE_KOPII.sqlite /var/lib/cw/leaderboards.db'
docker compose up -d
curl -f http://127.0.0.1:8080/healthz
~~~

Wstaw rzeczywistą nazwę kopii. Jeśli obraz nie może zapisać pliku, sprawdź
uprawnienia właściciela katalogu wolumenu (kontener działa jako century).
Po przywróceniu **powtórz wszystkie żądania usunięcia danych wykonane od
daty kopii** przed ponownym publicznym udostępnieniem rankingu.

## 8. Żądanie usunięcia danych gracza

W grze: **Ustawienia → Nowa tożsamość → Potwierdź**. Aplikacja zapisuje
żądanie usunięcia starego UUID trwale na urządzeniu i nadaje nowy UUID.
Gdy sieć nie działa, próbuje ponownie również po restarcie, niezależnie
od zgody na przesyłanie nowych wyników. Zmiana identyfikatora **nie jest
potwierdzeniem usunięcia na serwerze**; stare dane mogą być widoczne
do zakończenia wysyłki. Nie usuwaj pamięci aplikacji ani jej nie odinstalowuj,
jeśli żądanie jest jeszcze niewysłane.

Endpoint POST /v1/player/delete usuwa ze wszystkich plansz wyniki i pseudonim,
idempotentnie. Po stronie serwera nie ma odrębnego konta ani adresu e-mail.
Dane Google Play Games, lokalny profil i systemowe kopie Androida **nie są
usuwane przez tę operację**. Wnioski i prośby o potwierdzenie usunięcia można
kierować na adres kontaktowy wydawcy z opublikowanej polityki prywatności.
Przed publikacją uzupełnij ten adres i przetestuj rzeczywisty proces.

## 9. Codzienna obsługa

- docker compose ps — czy kontener ma status healthy.
- docker compose logs --tail=100 leaderboard — błędy aplikacji (nie publikuj logów z danymi).
- df -h — wolne miejsce.
- sudo ls -lt /var/backups/century-war/ — sprawdź, czy powstają nowe kopie.
- sudo systemctl status caddy — certyfikat i proxy.

**Ograniczenia produkcyjne:** jedna instancja SQLite, rate limit proxy,
sekret wyodrębnialny z APK (także przy usuwaniu danych), brak pełnego
uwierzytelniania własności UUID i konieczność zarządzania retencją backupów.
