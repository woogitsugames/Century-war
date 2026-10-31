# Zapis i odtwarzanie komend Century War

W ekranie bitwy oraz samouczku używany jest jeden `BattleController` na bitwę.
Wszystkie akcje gracza (szkolenie, anulowanie, awans, wieże, zbrojownia,
ostrzał, zryw, postawa, doktryna, poddanie) powinny wywoływać kontroler,
nie metody `Battle` bezpośrednio.

## Model czasu

`Battle.stepCount` liczy zakończone kroki symulacji. Komenda oznaczona
krokiem N zostaje wykonana przed kolejnym `Battle.step()` (krokiem N).
`Battle.time = stepCount * Battle.STEP`, bez dryfu od dodawania Float.
Generator decyzji AI jest odseparowany od losowości samej symulacji.

## Powtórka wyniku

Po zakończeniu bitwy można poza wątkiem renderującym wywołać:

```kotlin
val log = battle.commandController?.log() ?: error("Brak rejestratora")
val encoded = Replay.encode(log)
val result = Replay.verify(cfg, encoded)
```

`Replay.Result` zwraca `score`, `winner`, `steps`, `kills`,
`enemyKills`, `time` i `wave`. Nieakceptowane komendy, uszkodzone
dane oraz log z brakującym zakończeniem bitwy powodują odrzucenie.
Poddanie ma własną komendę, również gdy następuje przed kolejnym krokiem.
Automatyczne dofinansowanie samouczka jest logowane osobnym typem
`TUTORIAL_GOLD` i dozwolone wyłącznie w konfiguracji samouczka.

## Snapshoty

Format `BattleSnapshot` v7 zapisuje licznik kroków, oba strumienie RNG AI
oraz dziennik kontrolera. Po wznowieniu `BattleScreen` używa odtworzonego
kontrolera, a kolejne kliknięcia są dopisywane do tego samego dziennika.
Snapshoty o innych wersjach są odrzucane jako niezgodne.

## Bezpieczeństwo i ograniczenia

- Serwer ustala autorytatywny `BattleConfig`: seed, bonusy, AI,
  modyfikatory, wersję zasad i `Defs`. Nie ufa konfiguracji z telefonu.
- Powtórka odtwarza wynik, ale nie uwierzytelnia gracza; wynik i tryb
  muszą być porównane z tym, co deklarował klient.
- Limit formatu: 432 000 kroków (120 minut) i 32 768 komend;
  przekroczenie oznacza wynik nieweryfikowalny.
- Wszelka zmiana balansu lub algorytmów wymaga identyfikacji wersji
  silnika po stronie usługi weryfikującej.
- Samouczek jest dopuszczony w rankingu tylko dla `campaign` z `stage=0` i `tutorial=true`;
  `TUTORIAL_GOLD` nie może zostać zaakceptowane w innej konfiguracji.


## Klient rankingu globalnego (zlecenie 23)

**Wybór: wariant B — wspólne badania.** Obliczenia 13 badań znajdują się teraz w
\`:sim/Research.kt\`. Androidowy \`Profile.bonuses()\` wywołuje ten sam wzór co
\`ReplayVerifier\` na serwerze. Klucze i format JSON profilu nie zostały zmienione.
Wspólna tabela \`Skirmish.power(0..3)\` zapewnia identyczną siłę AI potyczki.
Kampania odtwarza także wersję etapu 0 z samouczkiem, kiedy zgłoszenie ma
\`tutorial=true\`. Daily nadal normalizuje premie i zawsze wysyła 13 zer.

Kontrakt POST /v1/score od wersji **0.2.0** wymaga \`research\` (13 liczb
w kolejności \`Research.defs\`, każda w \`0..max\`), \`difficulty\`
(\`0..3\` tylko dla potyczki) i opcjonalnego \`tutorial\` (jedynie \`campaign_0\`).
Niedozwolony parametr lub starsza wersja to HTTP 400, niezgodne wykonanie
powtórki to 422. Serwer zapisuje \`research_sum\` w SQLite (automatyczna migracja
poprzedniej bazy). Format Replay v3 oraz Snapshot v7 pozostają bez zmian:
nie dodano stanu bitwy, tylko metadane transportowe. Zmiana dotyczy
\`CW_GAME_VERSION\`, a endpointy nadal należą do \`/v1\`.

Przesyłanie jest **domyślnie wyłączone**. Gdy brakuje poprawnej konfiguracji
\`CW_SERVER_URL\`, \`CW_HMAC_KEY\` albo HTTPS \`CW_PRIVACY_URL\`, własnego rankingu
nie ma w UI. Podczas pierwszego włączenia użytkownik widzi informację
o pseudonimie, losowym UUID v4, wyniku, badaniach/trudności i zapisie komend
bitwy oraz odnośnik do polityki. UUID powstaje po zaakceptowaniu zgody;
reset postępu zachowuje go, akcja „Nowy ID” tworzy inny i czyści kolejkę.
Nie są wysyłane treningowe podejścia daily.

\`ScoreClient\` koduje \`Replay.encode\`, czyta/zapisuje kolejkę i wykonuje
podpisane HTTPS poza wątkiem gry (jeden executor). Ma timeouty 10 s, próby
ponowienia z opóźnieniem wykładniczym do 1 h, limit 20 zgłoszeń i retencję
7 dni. Wyłączenie zgody czyści kolejkę, a trwałe błędy HTTP usuwają wpis.
Własne rankingi GET top/around obsługują dzień/tydzień/całość, pamięć podręczną
120 s i widok offline. Są niezależne od Play Games.

**Zagrożenie:** Klucz HMAC w APK **nie stanowi tajemnicy**. Replay.verify
uniemożliwia przyjęcie niemożliwego przebiegu, ale nie zapewnia tożsamości
gracza ani ochrony przed botami. Produkcyjnie potrzebne są dodatkowe
limity/atestacja oraz procedura usuwania danych. Wyświetlane dziś rankingi
łączą wyniki „z badaniami” i „bez badań”; \`research_sum\` umożliwi później
osobny filtr (zero/więcej niż zero). Najbardziej porównywalne pozostaje daily.

**Zmiany do zlecenia 20 – bez edycji docs/store:** polityka prywatności i
Google Play Data safety muszą opisać pseudonim, UUID, wynik, poziomy badań,
replay komend, zgodę opt-in, okres przechowywania, sposób wycofania zgody
i żądanie usunięcia danych. „Nowy ID” nie usuwa starych danych na serwerze;
administrator musi zapewnić odrębny proces usunięcia.

Testy: \`:server:test\` (4 tryby, serializacja JSON, snapshot, tamper poziomów,
400/422, HMAC) i \`:app:testDebugUnitTest\` (Profil, kolejka, HMAC, konfiguracja).
\`ScreenshotTest\` zapisuje widoki 720p/Fold bez połączeń sieciowych.
