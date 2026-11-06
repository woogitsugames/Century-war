# 201 — wydajność ekranu Badań

Data: 2026-11-06. Baza `origin/main`: `7d847576e27bc5411bfd93877c1ba6df18af05cd`; gałąź `gpt/201-research-screen-perf`. Ostatni commit kodu przed raportem: `b1e4841c`. Gradle uruchomiono.

## Zmiany

W `ResearchScreen.kt` zastąpiono dwie pętle `withIndex()` w układaniu i rysowaniu kart pętlami indeksowymi. Kolejność, geometria i rysowanie kart pozostają takie same. Nowy `app/src/test/java/com/centurywar/screens/ResearchScreenPerformanceTest.kt` mierzy 600 klatek po rozgrzewce 300 klatek, przy bezczynności i naprzemiennym przewijaniu, na LOW i HIGH. Zmiany obejmują te dwa pliki i niniejszy raport; L.kt i wzorce zrzutów nie były zmieniane.

## Wyniki pomiaru

ThreadMXBean mierzy alokacje bieżącego wątku JVM, a `nanoTime` czas update+render w Robolectric native graphics; to nie pomiar telefonu ani GPU. Jednorazowe przebiegi są podatne na JIT i zakłócenia — szczególnie wartości przed zmianą zależą od kolejności scenariuszy, dlatego nie należy traktować różnic jako niezależnego benchmarku przyspieszenia.

| Scenariusz | Przed B/600 | Po B/600 | Średnia przed → po ms | p95 przed → po ms |
|---|---:|---:|---:|---:|
| LOW bezczynność | 38496 | 96 | 0,450 → 0,517 | 0,543 → 0,710 |
| LOW przewijanie | 19920 | 48 | 0,349 → 0,383 | 0,465 → 0,558 |
| HIGH bezczynność | 304 | 304 | 0,268 → 0,297 | 0,328 → 0,420 |
| HIGH przewijanie | 48 | 48 | 0,282 → 0,335 | 0,377 → 0,489 |

Przed zmianą test nie przeszedł dla dwóch scenariuszy LOW; po zmianie każdy pomiar jest poniżej 16384 B. Czas klatki nie wykazał poprawy w tym pojedynczym przebiegu.

## Testy

```text
:app:testDebugUnitTest --tests '*ResearchScreenPerformanceTest*' --console=plain --no-configuration-cache --no-daemon
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 29s
37 actionable tasks: 4 executed, 33 up-to-date
```

```text
:app:testDebugUnitTest --tests '*LateEraPerformanceTest*' --tests '*ScreenshotTestMenus*' --tests '*LTest*' --console=plain --no-configuration-cache --no-daemon
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 1m 20s
37 actionable tasks: 1 executed, 36 up-to-date
```

Test zrzutów porównuje `phone720_reg100_research` i `foldOpen_reg100_research` z wersjonowanymi wzorcami z tolerancją kanału 20 i maksymalnie 0,1% pikseli; wynik zielony nie dowodzi identyczności każdego piksela. Wzorców nie aktualizowano.

```text
:app:assembleDebug --console=plain --no-daemon
BUILD SUCCESSFUL in 29s
43 actionable tasks: 10 executed, 5 from cache, 28 up-to-date
```

```text
fastTest --console=plain --no-configuration-cache --no-daemon
ReplayShareProviderTest > onlyReadOnlyReplayFilesAreExposed FAILED
    java.nio.file.FileSystemException at ReplayShareProviderTest.kt:63
```

`fastTest` przerwano po błędzie; pełny zestaw nie ma zielonego wyniku. Nie wykonano pomiaru na urządzeniu. Dalsza praca: powtórzyć profilowanie na rzeczywistym słabszym urządzeniu (`ResearchScreen.kt`); osobno zbadać błąd `ReplayShareProviderTest.kt:63` w Windows.
