# Zlecenie 187 — scenariusze testów ręcznych

Data: 2026-11-06. Baza `origin/main`: `7d847576e27bc5411bfd93877c1ba6df18af05cd`. Gałąź `gpt/187-testers-scenarios`; commit scenariuszy `a13484677fed3328e517ca31ddf0e9abc3c8b5b7`. Hash ostatniego commita z raportem: `git rev-parse HEAD` (własnego hasha nie można wpisać do jego zawartości bez zmiany hasha).

**NIE URUCHAMIANO GRADLE** — zmiana dokumentowa, w zleceniu określono „bez budowania”. Nie wykonano ręcznych prób na urządzeniach.

## Zmiany

Dodano 18 scenariuszy S-01–S-18 w `docs/testers/SCENARIUSZE.md`, przypisanych do dni fali i ograniczonych do 10 minut aktywnej pracy każdy. Każdy ma cel, urządzenie/ustawienia, kroki, oczekiwany wynik i dane do zgłoszenia. Ujęto pierwsze uruchomienie, samouczek, kampanię 1–4, tło, wznowienie, Fold i obrót, język, wyzwanie dnia i tygodnia, Endless, potyczkę, powtórkę, kopię profilu, jeden ogólny scenariusz Rozkazów, raport w dniach 3 i 10, przypomnienia oraz baterię.

Zmienione pliki: `docs/testers/SCENARIUSZE.md` i ten raport. Punkt odniesienia harmonogramu: `docs/research/R13-testy.md`; nazwy etykiet i ścieżki przejrzano w `L.kt`, ekranach menu, ustawień, trybów, powtórek, rozkazów oraz w kodzie przypomnień i udostępniania raportu.

## Kontrola

Sprawdzono skryptem Python kolejność i spójność ID w tabeli oraz obecność czterech pól w 18 sekcjach:

```text
18 scenarios, 18 table rows, required fields: OK
```

`git diff --check` przed commitem scenariuszy nie wykazał błędów. Testów Gradle nie uruchamiano, bo dokumentacja nie zmienia aplikacji. Scenariuszy nie wykonywano z testerami ani na telefonie; konieczna jest próba operacyjna przed rozdaniem.

## Ograniczenia i dalsze zadania

- Czas dotyczy aktywnych czynności, nie wymusza zakończenia długiej bitwy w 10 minut. Oczekiwanie na zaplanowane przypomnienie może trwać do następnego dnia; jego otrzymanie zależy od zgody i systemu.
- Kanał zbierania raportów i odbiorcę musi wskazać organizator; dokument nie zawiera wymyślonego adresu. Eksport/import profilu wymaga świadomego potwierdzenia i prywatnego pliku; dane mogą zostać zastąpione.
- Scenariusz rozkazów wymaga odblokowanego rozkazu, a powtórka zakończonej bitwy; jeśli warunek nie jest spełniony, wynik oznacza się jako niewykonany. Zachowanie przypomnień i interfejs systemowego udostępniania może różnić się zależnie od telefonu.
- Proponowane dalsze zadania: **Próba scenariuszy na telefonie i Foldzie** (`docs/testers/SCENARIUSZE.md`, wnioski w raporcie fali); **Uzupełnienie kanału kontaktu dla testerów** (instrukcja przekazywana przez organizatora). Nie wprowadzano zmian w kodzie ani pozostałych dokumentach.
