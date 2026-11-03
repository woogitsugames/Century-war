# R5: wdrożenie serwera rankingu globalnego (3.11.2026)

Baza: `main` = 13f98b7. Przejrzane: `server/` (Ktor 3.2.3 + Netty, SQLite `sqlite-jdbc` 3.50.3.0,
2152 wiersze kodu, 21 plików testów), `docker-compose.yml`, `.env.example`, `server/Dockerfile`,
`server/Caddyfile.example`, `server/backup-sqlite.sh`, `server/README.md`, `docs/SERVER_DEPLOY.md`,
`ScoreClient` w aplikacji i polityki prywatności. Kodu nie zmieniałem.

## 1. Gotowość do wdrożenia: **technicznie tak, organizacyjnie jeszcze nie**

**Co jest gotowe (i dobrze zrobione):**
- **Weryfikacja wyników:** serwer odtwarza powtórkę tym samym kodem `:sim` (`ReplayVerifier`).
  Przyjmuje tylko bieżący format powtórek (`RANKED_REPLAY_FORMATS`) i wersję `PROTOCOL`.
  Bramka `VerificationGate` pilnuje 1 weryfikacji na vCPU, kolejki 16 i limitu 15 s. Benchmark:
  ok. 0,93 s na najdłuższą syntetyczną bitwę na 1 vCPU.
- **Podpisy i nadużycia:** HMAC żądań z czasem ±300 s i jednorazowym `nonce` w SQLite. Sekret
  instalacji (tylko skrót SHA-256 w bazie) chroni usuwanie i wyniki gracza. Limity na minutę
  w pamięci (IP, gracz, zgłoszenia), dzienne limity w SQLite (instalacja, IP jako skrót HMAC
  z dniem, kasowany następnego dnia). Limit rozmiaru treści 1 MB w Caddy i w kodzie.
- **Prywatność:** publiczne ID to HMAC z UUID kluczem tylko serwerowym. Surowy UUID nie jest
  pokazywany. Brak logów żądań w Ktor (bez `CallLogging`). Usuwanie gracza przez API
  (`/v1/player/delete`) jest idempotentne i odporne na wynik w locie (409 `player_deleted`).
- **Moderacja:** filtr nicków w 10 językach, zgłoszenia `/v1/report`, CLI `admin`: `recent`,
  `reports`, `block`/`unblock`, `delete-score`, `export-csv`, `backup`, `restore`.
- **Eksploatacja:** Docker Compose (port 8080 tylko na loopback, `restart: unless-stopped`,
  healthcheck, użytkownik bez roota), Caddy z automatycznym HTTPS, skrypt kopii `VACUUM INTO`
  z rotacją 14 dni, `/metrics` za tokenem, instrukcja krok po kroku w `docs/SERVER_DEPLOY.md`.
- **Testy:** API, podpisy (wektory), nadużycia, usuwanie, moderacja, CLI, migracje, E2E klient–serwer.

**Czego brakuje przed włączeniem** (szczegóły w sekcjach 4–5): decyzje właściciela (hosting,
domena, czas przechowywania danych, kontakt), kopia **poza serwerem**, zewnętrzny monitoring
dostępności, retencja danych w kodzie, komenda usuwania gracza po nicku (prośby e-mailowe)
i poprawki zdań w polityce prywatności.

**Rekomendacja:** testy zamknięte można **zacząć bez serwera**. Ranking globalny w aplikacji
jest wyłączony, gdy `CW_SERVER_URL` jest pusty, a Play Games ma własne rankingi. Serwer włączyć
w drugiej połowie testów albo przy premierze, po zadaniu `docs/tasks/R5-server-hardening.md`.

## 2. Hosting na start (setki graczy)

**Obciążenie:** setki graczy to najwyżej kilkadziesiąt zgłoszeń wyników na godzinę i ok.
1 s CPU na weryfikację. Wystarczy 1–2 vCPU i 1–2 GB RAM w pracy. 4 GB RAM jest potrzebne tylko do
**budowania** obrazu Dockera na samym serwerze (Gradle). Baza SQLite przez pierwszy rok to
pojedyncze MB.

| | A. Hetzner Cloud CX23 (Niemcy/Finlandia) | B. OVHcloud VPS-1 (Francja/Polska) | C. Railway (PaaS, bez administrowania serwerem) |
|---|---|---|---|
| Zasoby | 2 vCPU, 4 GB RAM, 40 GB NVMe | 2 vCore, 4 GB RAM, 40 GB NVMe (konfiguracja zmieniała się w 2026) | płatne za użycie; serwer z `-Xmx512m` ≈ 0,6 GB RAM, małe CPU |
| Cena | ok. **4–6 EUR/mies. netto + 0,50 EUR IPv4**. Źródła podają 3,99 € (przed 15.06.2026), 5,49 € i 5,99 € (08.2026), więc sprawdzić w panelu | ok. **4–6 EUR/mies.** (źródła: od 4,54 USD przy płatności z góry za 12 mies.; ceny podnoszone 04.2026) | plan Hobby **5 USD/mies.** (w cenie 5 USD użycia) + RAM 10 USD/GB·mies., CPU 20 USD/vCPU·mies., wolumen 0,15 USD/GB. Szacunek **ok. 7–10 USD/mies.** |
| HTTPS | Caddy (instrukcja jest) | Caddy (instrukcja jest) | automatyczny, także dla własnej domeny |
| Kopie | skrypt + kopia poza serwerem (sekcja 3) | jak A; VPS-1 ma dzienny backup dostawcy w cenie (wg źródeł) | wolumen; kopie trzeba zrobić skryptem (`admin backup`) i wysłać na zewnątrz |
| Administracja | właściciel/koordynator: SSH, aktualizacje systemu, Docker | jak A | brak systemu do aktualizowania; CLI `admin` przez `railway ssh`/jednorazowe polecenie |
| RODO | UE, umowa powierzenia (DPA) w panelu | UE (także Polska), DPA w panelu | firma z USA, regiony UE dostępne; DPA i transfer poza UE trzeba ocenić |
| Ryzyko | ceny Hetzner rosły w 2026; ograniczona dostępność CX | niejasny cennik 2026 | koszt rośnie z użyciem; uśpienie i limity Hobby; mniej kontroli nad danymi |

**Wybór:** **A albo B (VPS w UE) za ok. 5–7 EUR/mies. łącznie z IPv4**. Instrukcja
`SERVER_DEPLOY.md` jest napisana dokładnie pod VPS z Caddy, a dane zostają w UE. Railway (C) ma
sens tylko wtedy, gdy nikt nie chce administrować Linuksem, i kosztuje podobnie lub więcej.
Dodatkowo: **domena** ok. 10–15 EUR/rok (jeśli właściciel jej nie ma), **kopia poza serwerem**:
Backblaze B2 ma 10 GB za darmo (baza ma MB) albo Hetzner Storage Box (od ok. 3–4 EUR/mies.).

Źródła cen (sprawdzone 3.11.2026, ceny zmieniały się w 2026, potwierdzić w panelu przed zakupem):
[Hetzner Cloud](https://www.hetzner.com/cloud/), [VPSBenchmarks: CX23 08.2026](https://www.vpsbenchmarks.com/trials/hetzner_performance_trial_28Aug2026),
[privatedevops: zmiana cen Hetzner 06.2026](https://privatedevops.com/news/hetzner-june-2026-cloud-price-increase-what-to-do),
[OVHcloud VPS](https://www.ovhcloud.com/pl/vps/), [VPSBenchmarks: OVH VPS-1](https://www.vpsbenchmarks.com/hosters/ovhcloud_us/plans/vps-1),
[Railway: cennik](https://docs.railway.com/pricing), [Backblaze B2: cennik](https://www.backblaze.com/cloud-storage/pricing).

## 3. HTTPS, sekrety, kopie, monitoring, limity, RODO

**HTTPS:** Caddy z `Caddyfile.example` (certyfikat Let's Encrypt, HSTS, nadpisanie
`X-Forwarded-For`). Do zrobienia: sprawdzić IP bramy Dockera i wpisać do
`CW_TRUSTED_PROXY_IPS` (krok opisany w `SERVER_DEPLOY.md` §4). Bez tego wszyscy gracze mają
ten sam adres IP, więc limity na IP blokują wszystkich naraz.

**Sekrety:** trzy wartości, każda `openssl rand -hex 32`:
- `CW_HMAC_KEY`: serwer **i** APK (sekret CI do budowania wydania);
- `CW_PUBLIC_ID_KEY`: **tylko serwer**; jego zmiana zmienia publiczne ID wszystkich graczy;
- `CW_METRICS_TOKEN`: tylko serwer i narzędzie monitorujące.

Przechowywać w menedżerze haseł właściciela + `.env` z `chmod 600`. Nigdy w repo. HMAC w APK
**da się wydobyć**. Chroni przed przypadkowymi nadużyciami, nie przed zdeterminowanym oszustem.
Oszustwa ogranicza weryfikacja powtórek (wynik musi wynikać z gry).

**Kopie zapasowe:** codziennie `backup-sqlite.sh` (cron 03:00) + **wysyłka poza VPS**
(brakuje w skrypcie). Na przykład `rclone copy` do B2 lub Storage Box, szyfrowane
(`rclone crypt`), retencja 14–30 dni. Raz w miesiącu próba odtworzenia na kopii
(`PRAGMA integrity_check`). Po odtworzeniu trzeba powtórzyć usunięcia z okresu po kopii,
a dziś nie ma ich dziennika (luka L4).

**Monitoring:**
- dostępność: darmowy zewnętrzny monitor HTTP co 5 min na `https://<domena>/health` z e-mailem
  do właściciela (np. UptimeRobot lub Healthchecks.io);
- kopie: cron po udanej kopii „pinguje” Healthchecks.io; brak pingu oznacza e-mail;
- `/metrics` (z tokenem): liczba wyników, czasy weryfikacji, odrzucenia `verification_busy`.
  Przegląd ręczny raz w tygodniu w czasie testów;
- dysk i aktualizacje: `unattended-upgrades` w Ubuntu, `df -h` w cotygodniowym przeglądzie.

**Limity:** domyślne z `.env.example` wystarczą dla setek graczy (np. 20 zgłoszeń wyników
na minutę z IP, 6 na gracza, 100 na instalację dziennie). Limity są w pamięci jednego procesu,
a Caddy ich nie dubluje. Przy ataku pomoże darmowy proxy z ochroną DDoS przed serwerem
(np. Cloudflare), ale wtedy trzeba zmienić odczyt IP klienta (`CF-Connecting-IP`) i
`CW_TRUSTED_PROXY_IPS`. Na start niepotrzebne.

**RODO (dane rankingu):**
- **Dane:** losowe UUID instalacji, pseudonim, wyniki z datą i parametrami bitwy, skrót
  sekretu instalacji, publiczne ID, zgłoszenia nicków (UUID zgłoszonego, nick, powód, czas),
  skróty IP+dzień do limitów (kasowane po dniu), jednorazowe `nonce` (wygasają). To dane osobowe
  w postaci pseudonimowej: da się je powiązać z osobą przez urządzenie i nick.
- **Podstawa prawna:** do decyzji właściciela (z prawnikiem). Gra pyta dziś o **zgodę** na ranking.
  Przy zgodzie dziecka poniżej 16 lat (Polska; w innych krajach UE 13–16) potrzebna jest zgoda
  rodzica (art. 8 RODO). Prostsza alternatywa: **wykonanie usługi**, o którą gracz prosi
  (art. 6 ust. 1 lit. b), z możliwością wyłączenia w każdej chwili. Gra ma ocenę IARC
  i nie jest kierowana do dzieci, ale to nie wyłącza problemu.
- **Okres przechowywania:** dziś „do usunięcia na żądanie lub zamknięcia usługi”. RODO wymaga
  ograniczenia, a polityka powinna podać konkretny termin. Propozycja: tablice dnia
  i tygodnia 90 dni, zgłoszenia nicków 180 dni, gracz bez nowego wyniku przez 24 miesiące
  usuwany w całości, kopie 14–30 dni. Wymaga kodu (luka L1).
- **Umowa powierzenia (DPA)** z dostawcą hostingu (Hetzner i OVH mają ją w panelu do
  zaakceptowania) i z dostawcą kopii (B2/Storage Box). Serwer w UE.
- **Rejestr czynności przetwarzania** (art. 30): krótki dokument dla jednej czynności
  „ranking globalny”. Mały podmiot często jest zwolniony, ale przy regularnym przetwarzaniu
  zwolnienie nie działa, więc lepiej mieć.
- **Prawa graczy:** usuwanie w grze (Nowa tożsamość) działa. **Brakuje ścieżki dla prośby
  e-mailowej**, gdy gracz zgubił telefon: operator zna tylko nick, a CLI nie ma „znajdź po
  nicku i usuń wszystko” (luka L2). Dostęp do danych (art. 15): `export-csv` + filtr po nicku.
- **Polityka prywatności do poprawki:**
  - zdanie „Serwer nie przechowuje … IP w SQLite” jest nieścisłe, bo zapisuje skrót HMAC
    z IP i dniem, kasowany po dobie. Trzeba to opisać;
  - wzmianki „po wdrożeniu (zlecenie 80)” są nieaktualne, bo zgłaszanie nicków już działa;
  - trzeba podać konkretne terminy przechowywania.
  Te zmiany trzeba wprowadzić w `privacy_policy_pl.md`/`_en.md` i `data_safety.md`.

## 4. Luki w kodzie i konfiguracji (bez poprawiania)

| # | Luka | Waga | Gdzie |
|---|---|---|---|
| L1 | **Brak retencji danych**: wyniki tablic dnia i tygodnia, zgłoszenia nicków i nieaktywni gracze nie wygasają; brak zadania czyszczącego | wysoka (RODO) | `SqliteStore.kt`, `AdminCli.kt` |
| L2 | **Brak usuwania gracza przez operatora** (po UUID lub nicku) dla próśb e-mailowych; jest tylko `delete-score` (jeden wiersz) i `block` | wysoka (RODO) | `AdminCli.kt`, `SqliteStore.deleteOwnedPlayer` wymaga sekretu |
| L3 | **Kopia tylko na tym samym VPS**; brak wysyłki poza serwer i powiadomienia o nieudanej kopii | wysoka | `server/backup-sqlite.sh` |
| L4 | **Brak dziennika usunięć**, czyli listy UUID usuniętych od daty kopii. Po odtworzeniu kopii operator nie wie, kogo usunąć ponownie (instrukcja tego wymaga) | średnia | `SqliteStore`, `AdminCli` |
| L5 | Brak dziennika działań moderatora (kto i kiedy zablokował lub usunął) | niska | `AdminCli.kt` |
| L6 | Zgłoszenia nicków nie mają statusu „rozpatrzone” ani komendy czyszczenia | niska | `AdminCli.kt` (`reports`) |
| L7 | Budowa obrazu na VPS wymaga 4 GB RAM (Gradle); brak gotowego obrazu, a CI z zasady nie buduje przy push | średnia (koszt) | `server/Dockerfile` |
| L8 | Limity tylko w pamięci jednej instancji i bez limitu na proxy | niska na start | `Security.kt`, Caddy |
| L9 | Aktualizacje zależności (Ktor 3.2.3, sqlite-jdbc, obraz `eclipse-temurin`) bez harmonogramu | średnia | `server/build.gradle.kts`, `Dockerfile` |
| L10 | Polityka prywatności nieścisła o IP i „zleceniu 80”, bez konkretnych terminów | wysoka (zgodność) | `docs/store/privacy_policy_*.md`, `data_safety.md` |
| L11 | Brak zewnętrznego monitoringu i alertów (healthcheck Dockera tylko restartuje lokalnie) | średnia | dokumentacja |
| L12 | `/v1/report` bez podpisu: spam zgłoszeń ograniczony tylko limitem 5/min na IP i 15/min na cel | niska | `Api.kt` |

Zadanie dla programisty z L1–L6, L10 i dokumentacją L3/L7/L9/L11:
`docs/tasks/R5-server-hardening.md`.

## 5. Co musi dać lub kliknąć właściciel (krok po kroku)

1. **Zdecydować**, czy ranking globalny ma ruszyć na testach zamkniętych, czy dopiero przy
   premierze. Rekomendacja: przy premierze albo w drugim tygodniu testów.
2. **Domena:** kupić (albo wskazać istniejącą) i wybrać subdomenę, np. `ranking.twojadomena.pl`.
3. **Serwer:** założyć konto u Hetznera albo OVH. Kupić najmniejszy VPS z 4 GB RAM w UE,
   system Ubuntu 24.04, logowanie kluczem SSH (koordynator przygotuje klucz i instrukcję).
   Zaakceptować w panelu **umowę powierzenia danych (DPA)**.
4. **DNS:** w panelu domeny dodać rekord **A** z adresem IP serwera (koordynator poda wartość).
5. **Dostęp:** przekazać koordynatorowi (bezpiecznie, nie przez repozytorium) adres serwera
   i dostęp SSH albo samemu wykonać kroki z `docs/SERVER_DEPLOY.md` na podstawie instrukcji
   koordynatora.
6. **Sekrety:** zapisać w swoim menedżerze haseł trzy klucze wygenerowane przy instalacji.
   `CW_HMAC_KEY` wpisać także jako sekret CI do budowania wydania.
7. **Kopia poza serwerem:** założyć darmowe konto Backblaze B2 (albo Storage Box u Hetznera),
   zaakceptować DPA i przekazać klucz dostępu (tylko do jednego „kubełka”).
8. **Monitoring:** założyć darmowe konto UptimeRobot lub Healthchecks.io na swój e-mail.
   Koordynator wpisze adresy do sprawdzania.
9. **Prywatność:** zdecydować o podstawie prawnej (zgoda czy wykonanie usługi; sekcja 3),
   zatwierdzić okresy przechowywania (propozycja: 90 dni tablice dnia i tygodnia, 180 dni
   zgłoszenia, 24 mies. nieaktywni gracze, 30 dni kopie) i podać **e-mail do próśb o usunięcie**
   (`contact_email` w `OWNER_INPUT.md`). Najlepiej skonsultować z prawnikiem.
10. **Koszt:** zaakceptować ok. **5–7 EUR/mies.** (VPS + IPv4) + domena + (opcjonalnie) kopia
    3–4 EUR/mies.

## 6. Decyzje właściciela (zebrane)

1. Kiedy włączyć ranking globalny: testy zamknięte czy premiera.
2. Hosting: VPS w UE (Hetzner albo OVH) czy PaaS (Railway) bez administrowania serwerem.
3. Podstawa prawna rankingu (zgoda czy wykonanie usługi) i zgoda rodzica dla młodszych graczy.
4. Okresy przechowywania danych rankingu i kopii.
5. Czy dodać darmowy proxy z ochroną DDoS (np. Cloudflare). Rekomendacja: nie na start.
