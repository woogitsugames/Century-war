# 175 — testy ScoreWire

**NIEUKOŃCZONE: NIE URUCHOMIONO GRADLE ANI TESTÓW ZLECENIA.**

Baza: `origin/main` 7d847576e27bc5411bfd93877c1ba6df18af05cd, 6.11.2026. Gałąź `gpt/175-sim-scorewire-tests`.

Zlecenie wymaga dodania pliku Kotlin w `sim/src/test`, natomiast bieżąca instrukcja użytkownika zabrania zmiany kodu `:sim`. Zachowano bardziej restrykcyjne ograniczenie: nie dodano testu ani nie zmieniono żadnego kodu modułu. Przeczytano `ScoreWire.kt`; `sign` buduje HMAC-SHA256 z pól rozdzielonych LF, a `submission` cytuje znaki kontrolne w JSON. Są to ustalenia z kodu, nie wyniki nowych testów.

Do wykonania po rozstrzygnięciu zakresu: test stabilnego JSON, znaków specjalnych i Unicode, wektora podpisu oraz nagłówków, a następnie filtrowany i pełny `:sim:test` wraz z `GoldenReplayTest`. Zmieniono jedynie ten raport. Nie otwarto PR.

## Dokończenie przez koordynatora (7.11.2026)

Ograniczenie w prompcie zbiorczym było błędem koordynatora (test w `sim/src/test` jest w zakresie zlecenia). Dodano `sim/src/test/kotlin/com/centurywar/game/ScoreWireTest.kt`: wektory HMAC-SHA256 (bez i z sekretem instalacji, treść UTF-8) liczone niezależnie w Pythonie, nagłówki, stabilny JSON Endless, pola daily/weekly/campaign/skirmish (trudność przycięta do 0..3), wyjątek dla Rozkazów, cytowanie znaków sterujących i Unicode w nicku. `:sim:test --tests '*ScoreWireTest*'` zielony; pełne testy przed scaleniem na main.
