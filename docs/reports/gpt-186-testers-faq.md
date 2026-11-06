# Raport 186 — FAQ testerów

Data: 2026-11-06. Baza: `origin/main` `f17c6e25abf7b3c1b679a77a0a1fbe457df7a3f7`. Gałąź: `gpt/186-testers-faq`.

**GRADLE NIE URUCHOMIONO — ZLECENIE ZMIENIA WYŁĄCZNIE DOKUMENTY.**

## Wynik i pliki

- `docs/testers/FAQ.md`: 18 pytań po polsku, instrukcje i ograniczenia.
- `docs/testers/FAQ_en.md`: odpowiadające im 18 pytań po angielsku.
- `docs/reports/gpt-186-testers-faq.md`: ten raport i mapa źródeł.

Końcowy hash commita gałęzi jest wynikiem `git rev-parse HEAD`; umieszczenie własnego hasha w treści tego samego commita nie jest możliwe. Hash i wynik push przekazano również w wiadomości końcowej.

## Podstawa odpowiedzi (numery są identyczne w obu wersjach)

Ścieżki kodu są względne wobec repozytorium. `L.kt` to `app/src/main/java/com/centurywar/engine/L.kt`.

1. Instalacja i konto: `docs/RELEASE.md:180,907,914`.
2. Co testować: `docs/RELEASE.md:167` i scenariusz 179–237; wskazówki redakcyjne, nie twierdzenie o wyniku testów.
3. Ścieżka i przewijanie: `screens/SettingsScreen.kt:427`, `screens/AboutScreen.kt:27,59,80,202`; etykiety `L.kt:523,1017,2006` (ścieżki `screens/` w `app/src/main/java/com/centurywar/`).
4. Przycisk, opcjonalny formularz i wybór: `MainActivity.kt:910–959`; etykiety `L.kt:362,2105`.
5. Dni i warunki banera: `docs/RELEASE.md:172`, `meta/TesterReportReminder.kt:3–24`; menu i brak automatycznej wysyłki `MainActivity.kt:927–959`.
6. Pole kodu i format: `crash/PlaytestReportText.kt:168–180`, `meta/PlaytestLog.kt:76–78`.
7. Generowanie i lokalne utrwalenie: `meta/PlaytestLog.kt:462–471,597`, `docs/reports/d48-raport-v2.md:29–32`; ponowna instalacja może zmienić kod, bez gwarancji ciągłości.
8. Zawartość: `crash/PlaytestReportText.kt:168–180,185–225,237–296`, `L.kt:360,486`; sekcje testowe zależą od flagi.
9. Zakres i udostępnianie: `crash/PlaytestReportText.kt:13–20,175–180`, `MainActivity.kt:927–959`, `docs/store/privacy_policy_pl.md:49`.
10. Awaria i ręczne zgłoszenie: `docs/store/privacy_policy_pl.md:45–47`, `screens/AboutScreen.kt:201`; nazwy `L.kt:2101–2105`.
11. Dane raportu awarii: `docs/store/privacy_policy_pl.md:45–47`, `crash/CrashStore.kt:6,42`.
12. Odtwarzanie błędu i zrzut: zalecenie redakcyjne; wersja w `screens/AboutScreen.kt:32,65`, ręczne udostępnianie tekstu `MainActivity.kt:894–905`. Zrzut przekazuje się osobno, aplikacja nie dołącza go do tekstowego raportu automatycznie.
13. Dobrowolność i lokalność: `meta/TesterSurvey.kt:3–8,20–24`, `docs/reports/d57-ankieta-koncowa.md:35,118`.
14. Eksport i wyłączenia: `screens/SettingsScreen.kt:54,68–70,85,434`, `MainActivity.kt:664–679`, `meta/ProfileBackup.kt:26`; `docs/store/privacy_policy_pl.md:102` opisuje osobne pliki i wyłączenia.
15. Import i kopia: `screens/SettingsScreen.kt:54,68–70,85–86,435–436`, `MainActivity.kt:682–690,729–748`, `L.kt:2314,2321–2327`.
16. Przełącznik i różnica banera: `screens/SettingsScreen.kt:241,405–410`, `MainActivity.kt:596`, `meta/TesterReportReminder.kt:3–9`; etykiety `L.kt:1017,1041–1042,1649`.
17. Offline a usługi: `docs/store/privacy_policy_pl.md:39,47,73`; instalacja i przesyłanie przez sklep/usługę zależą od połączenia i zewnętrznej konfiguracji.
18. Polityka i usuwanie: `screens/AboutScreen.kt:198–201`, `docs/store/privacy_policy_pl.md:100–102,111–126`, `L.kt:2010`.

## Kontrola

Na bazie nie ma `tools/check_doc_links.py`; zgodnie z instrukcją zadania komendę `python3 tools/check_doc_links.py docs/testers` pominięto. Sprawdzono liczbę i kolejność pytań osobnym skryptem, bez dodawania narzędzi do repozytorium:

```text
FAQ.md 18 pytań, kolejność 1–18: OK
FAQ_en.md 18 pytań, kolejność 1–18: OK
check_doc_links.py: POMINIĘTO — pliku nie ma na origin/main
```

`git diff --check` bez komunikatów. Budowanie i testy Gradle nie były wymagane ani uruchamiane; nie zmieniono kodu ani plików testowanych przez `tests/`. Nie przeprowadzono instalacji na urządzeniu ani weryfikacji tekstu angielskiego przez native speakera.

## Ryzyka i dalsze kroki

- Polityka prywatności zawiera nieuzupełniony kontakt wydawcy (`docs/store/privacy_policy_pl.md:17–23`); nie podano wymyślonego adresu ani formularza. Zaproszenie i kanał przekazania uwag musi zapewnić prowadzący.
- W polityce zdanie, że raport nie zawiera identyfikatorów, wymaga czytania wraz ze zdaniem o losowym kodzie testera (`docs/store/privacy_policy_pl.md:49`). FAQ nazywa go osobnym identyfikatorem i nie obiecuje anonimowości; sekcja 9 wspomina ręczne udostępnienie raportu technicznego, ale pomija w tym zdaniu raport testera (`:132`). Nie zmieniano polityki w tym zleceniu.
- Weryfikacja odbioru i redakcja EN na rzeczywistej wersji testowej: `docs/testers/FAQ*.md`, `docs/RELEASE.md`; sprawdzić ścieżki, konfigurację formularza i jakość językową przed wysyłką.
- Uzupełnienie i przegląd polityki oraz kanału kontaktu: `docs/store/privacy_policy_pl.md`, `docs/store/OWNER_INPUT.md`; osobne zlecenie właściciela.
