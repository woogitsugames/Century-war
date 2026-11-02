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

Wygeneruj dwa niezależne 64-znakowe klucze (`openssl rand -hex 32` dwa razy).
Jeden wklej do `CW_HMAC_KEY`, drugi do `CW_PUBLIC_ID_KEY`.
**Drugi klucz pozostaje wyłącznie na serwerze**; nie umieszczaj go w APK
ani nie zmieniaj bez migracji danych. Ustaw
CW_GAME_VERSION zgodnie z **ScoreClient.GAME_VERSION w Androidzie**
(obecnie 0.6.0). Nie zapisuj .env do repozytorium, nie wysyłaj go w zgłoszeniach.
Klucz ten jest również w APK, a więc **nie zapewnia mocnego uwierzytelnienia**.
Żądanie usunięcia obcego UUID jest odrzucane bez powiązanego z nim
sekretu instalacji. Istniejące konta wiążą sekret przy pierwszym nowym wyniku.

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
idempotentnie, **tylko gdy podano sekret instalacji przypisany do UUID**.
Nowa tożsamość nadaje nowy UUID i sekret, a w kolejce usunięć zachowuje
poprzedni sekret. Stare rekordy bez sekretu wymagają pierwszego zweryfikowanego
wyniku przed usunięciem. Po stronie serwera nie ma odrębnego konta ani adresu e-mail.
Dane Google Play Games, lokalny profil i systemowe kopie Androida **nie są
usuwane przez tę operację**. Wnioski i prośby o potwierdzenie usunięcia można
kierować na adres kontaktowy wydawcy z opublikowanej polityki prywatności.
Przed publikacją uzupełnij ten adres i przetestuj rzeczywisty proces.

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

**Ograniczenia produkcyjne:** jedna instancja SQLite, ochrona DDoS na proxy,
ryzyko utraty sekretu instalacji wraz z profilem, możliwość przejęcia znanego
historycznego UUID przed przypisaniem sekretu i retencja backupów.
Przed startem wykonaj testy /score, /around, /report i /player/delete.

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
