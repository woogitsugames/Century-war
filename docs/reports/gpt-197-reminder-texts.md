# 197 — teksty przypomnienia

**NIEUKOŃCZONE: AKTUALNE TEKSTY SĄ W L.KT, KTÓREGO ZLECENIE NIE MOŻE ZMIENIAĆ.**

Baza `origin/main` 7d847576e27bc5411bfd93877c1ba6df18af05cd (6.11.2026), gałąź `gpt/197-reminder-texts`. `DailyReminderTexts.kt` nie zawiera tekstów powiadomienia: pobiera istniejące wpisy `daily`, `notification_title`, `notification_channel_desc`, `notification_body` i `rem193_goal` z `L.kt`. Ograniczenie użytkownika nie pozwala zmieniać `L.kt` w 197, a zakres 197 zezwala wyłącznie na treść napisów, nie na zastąpienie logiki nowymi tabelami. Pozostawiono kod bez zmian.

Poniższy przegląd ma charakter redakcyjny i nie zastępuje kontroli przez rodzimych użytkowników ani pomiaru powiadomienia na urządzeniu. Długość szablonu nie uwzględnia dynamicznego celu lub nazw modyfikatorów, więc nie gwarantuje braku ucięcia. We wszystkich językach szablony zawierają po jednym `%s`, nazwa trybu w tytule odpowiada `daily`; nie znaleziono oczywistej presji na powrót do gry.

| Język | Tytuł | Długość tytułu | Treść szablonu | Długość szablonu | Zmiana |
|---|---|---:|---|---:|---|
| pl | Century War — wyzwanie dnia | 27 | Nowe wyzwanie dnia czeka! Modyfikatory: %s | 42 | Brak: źródło w L.kt poza zakresem |
| en | Century War — daily challenge | 29 | A new daily challenge awaits! Modifiers: %s | 43 | Brak: źródło w L.kt poza zakresem |
| de | Century War — Tageschallenge | 28 | Eine neue Tageschallenge wartet! Modifikatoren: %s | 50 | Brak: źródło w L.kt poza zakresem |
| es | Century War — desafío diario | 28 | ¡Te espera un nuevo desafío diario! Modificadores: %s | 53 | Brak: źródło w L.kt poza zakresem |
| fr | Century War — défi du jour | 26 | Un nouveau défi du jour vous attend ! Modificateurs : %s | 56 | Brak: źródło w L.kt poza zakresem |
| it | Century War — sfida del giorno | 30 | Una nuova sfida del giorno ti aspetta! Modificatori: %s | 55 | Brak: źródło w L.kt poza zakresem |
| pt | Century War — desafio diário | 28 | Um novo desafio diário espera por ti! Modificadores: %s | 55 | Brak: źródło w L.kt poza zakresem |
| uk | Century War — виклик дня | 24 | Новий виклик дня чекає! Модифікатори: %s | 40 | Brak: źródło w L.kt poza zakresem |
| nl | Century War — daguitdaging | 26 | Er wacht een nieuwe daguitdaging! Modificatoren: %s | 51 | Brak: źródło w L.kt poza zakresem |
| tr | Century War — günlük mücadele | 29 | Yeni bir günlük mücadele seni bekliyor! Değiştiriciler: %s | 58 | Brak: źródło w L.kt poza zakresem |

## Dalsza praca

- Zmierzyć pełne komunikaty z długim celem i modyfikatorami na urządzeniach; sam limit znaków nie gwarantuje szerokości.
- Wszelkie zmiany sformułowań zaplanować w uprawnionym zleceniu dotyczącym L.kt. Zweryfikować naturalność przez rodzimych użytkowników.

## Testy

Uruchomiono Gradle w Windows z JDK 17 i SDK 36:

```text
:app:testDebugUnitTest --tests *DailyReminder*: BUILD SUCCESSFUL in 23s
:app:testDebugUnitTest --tests *SettingsNotificationsTest*: BUILD SUCCESSFUL in 17s
```

Kod nie został zmieniony; wymagany zakres zmian tekstów pozostaje otwarty.

## Decyzja koordynatora (7.11.2026)

Przegląd nie wykazał błędów ani presji w tekstach powiadomień we wszystkich 10 językach; zmiany nie są potrzebne. Zlecenie zamknięte bez zmian w `L.kt`.
