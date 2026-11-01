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

## Stan

31.10.2026 (noc): wszystkie paczki 19–38 scalone do `main` przez koordynatora. Szczegóły i poprawki po scaleniu: `docs/Century-war.md`.

---

# Wysyłka zleceń 39–52 (tura 3)

Stan na 31.10.2026 (noc). Ta sama gałąź bazowa i to samo polecenie startowe co wyżej. **Wysyłaj tylko jedną falę naraz.** Następną falę wysyłaj dopiero, gdy koordynator scali poprzednią i wypchnie ją na `claude/bold-sagan-vwz5gm` (w turze 2 wszystkie paczki powstały z tego samego punktu i scalanie trwało wiele godzin).

| Fala | Paczki | Pliki (rozłączne w obrębie fali) |
|---|---|---|
| 1 | **39** licencje i „O grze”, **41** auto-jakość GPU, **42** balans końca kampanii, **45** odtwarzacz powtórek, **46** poprawki Endless, **52** lepsze dźwięki | Settings+About+docs/store / engine pętli / `Modes.kt` / `ReplayScreen` / `Battle.kt`+snapshot / `SoundSynth` |
| 2 | **40** audyt układu ekranów, **43** wdrożenie serwera, **47** HUD bez alokacji, **48** miks dźwięku, **49** stabilność I/O | ekrany menu / `server/` / `BattleScreen` / `Sound`+`Music` / `MainActivity`+`meta` |
| 3 | **44** przegląd tłumaczeń, **50** grafiki do sklepu, **51** dzień wydania | `L.kt` / testy grafik / `Daily` + Gradle |

Po fali 1 (42, 46) koordynator mierzy balans i podbija wersję gry rankingu (`0.4.0`).

---

# Wysyłka zleceń 53–67 (tura 4)

1.11.2026 (noc). Zlecenia 39–52 scalone. Tura 4 jest pomyślana do wysłania **całej naraz** (GPT i tak pracuje równolegle): każde zlecenie ma rozłączny zakres plików, a nowe teksty trafiają do własnej funkcji `partNN()` w `L.kt`.

| Grupa | Paczki |
|---|---|
| Wrażenia i nauka gry | 53 opisy przycisków, 54 samouczek nowości, 67 pauza z ustawieniami |
| Oprawa | 55 muzyka, 56 tła epok, 57 menu główne, 58 ikona i splash, 59 mapa kampanii |
| Treść i motywacja | 60 osiągnięcia, 61 misje, 62 kodeks |
| Jakość i wydanie | 63 haptyka, 64 moderacja rankingu, 65 dziennik błędów, 66 wersja release |

---

# Wysyłka zleceń 68–79 (tura 5)

1.11.2026. Zlecenia 53–67 scalone. Paczki 68–77 i 79 można wysłać naraz (rozłączne pliki; 69, 70 i 72 dotykają `Modes.kt` w różnych miejscach). **78 (przegląd tłumaczeń) wyślij na końcu**, po scaleniu reszty.

| Grupa | Paczki |
|---|---|
| Treść | 68 zapowiedzi epok, 69 doktryny, 70 modyfikatory, 71 karty Endless, 72 dni specjalne |
| Funkcje | 73 udostępnianie powtórek |
| Jakość | 74 przegląd układu, 76 szybsze CI, 77 wydajność oprawy, 79 grafika wariantów |
| Sklep i teksty | 75 karta sklepu, 78 tłumaczenia (na końcu) |

---

# Wysyłka zleceń 80–91 (tura 6, po audycie)

1.11.2026. Źródło: dwa audyty (symulacja/serwer/wydanie oraz ekrany/UX/tłumaczenia). Wszystkie paczki można wysłać naraz; pary 80/81 (serwer) i 84/88 (teksty w `L.kt`) dotykają częściowo tych samych plików, ale w różnych miejscach.

| Grupa | Paczki |
|---|---|
| Serwer i uczciwość rankingu | 80 tożsamość i limity, 81 reguły weryfikacji |
| Symulacja | 82 limity i kumulacja kart/doktryn, 91 złote powtórki i testy `:sim` |
| Wydanie | 83 podpis i dokumenty |
| Ekrany i wygoda | 84 bitwa i wyniki, 85 samouczek, 86 podpowiedzi, 87 menu i rankingi, 89 ustawienia/kodeks, 90 nagrody i drobna wydajność |
| Teksty | 88 poprawki tłumaczeń |

---

# Wysyłka zleceń 92–117 (tura 6)

1.11.2026. Zlecenia 80–91 scalone. Trzy fale; **następną falę wysyłaj dopiero po scaleniu poprzedniej** przez koordynatora.

| Fala | Paczki |
|---|---|
| 1 (20 naraz) | 92 Play Games konfiguracja, 93 link do polityki i usuwanie danych, 94 przewodnik Play Console, 96 pomiar kampanii, 97 AI po limitach, 99 szybsze testy, 100 regresja zrzutów, 101 tanie telefony, 102 dźwięki, 103 muzyka, 104 wykres po bitwie, 105 wykresy statystyk, 106 „Co nowego”, 107 kopia profilu, 108 nowe karty Endless, 109 nowe wyzwania dnia, 111 daltonizm, 112 serwer: kopie i zdrowie, 113 serwer: limity weryfikacji, 114 audyt bezpieczeństwa |
| 2 | 95 balans późnego Endless (po 108), 98 audyt ekranów Folda, 110 HUD dla leworęcznych |
| 3 (na końcu) | 115 podział `BattleScreen.kt`, 116 przegląd tłumaczeń, 117 grafiki sklepu |
