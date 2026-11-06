# Zlecenie 174 — opis API serwera

GRADLE NIE URUCHOMIONO — TEST OCZEKUJE NA KOLEJKĘ; ZLECENIE NIE JEST GOTOWE.

Data 2026-11-06, baza `origin/main` 7d847576, gałąź `gpt/174-server-api-docs`.

Zaktualizowano `server/README.md`, dodano `server/src/test/kotlin/com/centurywar/server/ApiRoutesDocTest.kt` i niniejszy raport. Tabela opisuje metody, trasy, uwierzytelnianie, limity, pola i błędy według `Api.kt` i `Models.kt`; poprawiono wzmiankę o Replay i uściślono długość nicku. Test porównuje literalne trasy z tabelą i sprawdza wykrywanie nowej trasy na syntetycznym tekście. Test nie rozpoznaje tras tworzonych dynamicznie albo zdefiniowanych w innym pliku; jest to ograniczenie do utrzymania przy refaktoryzacji API.

Test Gradle i sprawdzenie czerwonego przypadku oczekują na kolejkę. `docs/SERVER_DEPLOY.md` nie zmieniano.
