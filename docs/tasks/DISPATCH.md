# Wysyłka zleceń 19–38 do GPT

Stan na 31.10.2026. Gałąź bazowa: `claude/bold-sagan-vwz5gm` (identyczna z `main`; koordynator wypycha obie). Każde zlecenie to osobna rozmowa z GPT i osobna gałąź `gpt/NN-*`.

## Polecenie startowe (to samo dla każdej paczki)

Wklej do GPT całą treść pliku zlecenia, a nad nią:

```
Wykonaj poniższe zlecenie w repozytorium woogitsugames/century-war.
Pracuj wyłącznie na gałęzi podanej w zleceniu, utworzonej z najnowszej origin/claude/bold-sagan-vwz5gm.
Przed oddaniem uruchom wszystkie testy wymienione w kryteriach akceptacji i dołącz ich wynik.
Nie scalaj do gałęzi bazowej i nie twórz PR; scalanie robi koordynator.
```

## Fale (paczki w jednej fali można wysłać równolegle)

| Fala | Paczki | Dlaczego razem |
|---|---|---|
| 1 | **19** liczba mnoga, **20** sklep i prywatność, **24** configChanges i Wstecz, **27** ruch maszyn | Rozłączne pliki (L.kt / docs / MainActivity / art) |
| 2 | **21** celowany ostrzał, **22** osobowości AI, **25** trafienia i śmierć | 21 po 19 (L.kt), 25 po 27 (Machines, drawUnit) |
| 3 | **26** pociski i efekty, **29** odtwarzacz powtórek, **31** dostępność | 26 po 21 i 25; 31 po 25–27 |
| 4 | **28** warianty epok, **30** historia bitew, **32** podpowiedzi | 28 po 19/21/22/25–27; 30 po 29; 32 po 21 i 31 |
| 5 | **23** klient rankingu, **33** pogoda, **38** dźwięki epok | 23 po 22 i 28; 33 po 26 i 28; 38 po 26/29/31/32 |
| 6 | **34** bossowie Endless | po 25, 27 i 33 |
| 7 | **36** oblężenie tygodnia, **35** potyczka konfigurowalna | 36 po 33/34; 35 po 22/23/33 |
| 8 | **37** wydajność | na końcu: mierzy gotową symulację i renderer |

Po fali 4 (28) i po każdej zmianie symulacji (33, 34, 36) koordynator powtarza pomiar balansu (`docs/BALANCE.md`) i podbija `CW_GAME_VERSION` serwera, jeśli 23 jest scalone.

## Odrzucone

- **gpt/18** (rozgałęzienia epok): nie scalać, zastąpione przez 28.
