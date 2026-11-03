# Ściąga właściciela: co trzeba zrobić, żeby ruszyły testy zamknięte w Google Play

Stan na 3.11.2026. Ta ściąga jest dla właściciela, bez wiedzy programistycznej. Każdy krok podaje:
ile trwa, ile kosztuje, co przekazać koordynatorowi i czy **blokuje testy** (bez niego testów
nie da się zacząć). Szczegóły są w `docs/store/PLAY_CONSOLE.md` i `docs/RELEASE.md`. Linki
prowadzą tylko do stron Google.

**Nigdy nie wysyłaj nikomu (także koordynatorowi) haseł, pliku klucza `.jks` ani skanów
dokumentów.** Nie wpisuj ich też do repozytorium.

## Zacznij od tych 5 rzeczy (najdłużej trwają)

1. **Numer D-U-N-S firmy.** Sprawdź, czy firma go ma, a jeśli nie, złóż wniosek od razu.
   Jest bezpłatny, ale czekanie może trwać **do 30 dni**. Bez niego nie założysz konta organizacji.
2. **Konto Google Play Console i weryfikacja tożsamości.** Załóż konto (25 USD) i od razu
   przejdź weryfikację osoby, firmy, e-maili i telefonów. Google sprawdza to zwykle kilka dni,
   czasem dłużej.
3. **Strona WWW firmy na własnej domenie, z polityką prywatności PL i EN.** Konto organizacji
   wymaga zweryfikowanej witryny, a polityka musi być pod publicznym adresem `https://`.
4. **Lista testerów.** Zbierz co najmniej **12 osób** z kontem Google (Gmail) i telefonem
   z Androidem 8 lub nowszym, gotowych grać przez 14 dni. Zbieranie ludzi trwa najdłużej
   ze wszystkiego, co nie zależy od Google.
5. **Play Games: 83 identyfikatory.** Trzeba ręcznie utworzyć w konsoli projekt, 4 rankingi
   i 78 osiągnięć. To 3–4 godziny klikania. Można zacząć zaraz po utworzeniu aplikacji w konsoli
   (krok 2), bo bez tych ID nie zbudujemy wersji do sklepu.

## Krok po kroku

### 1. Konto deweloperskie
- **Osobiste czy organizacja?** Wcześniej ustaliłeś: **organizacja** (firma/JDG). Różnice:
  - konto osobiste (nowe): przed wydaniem dla wszystkich **obowiązkowo 12 testerów przez 14 dni**
    bez przerwy, potem wniosek do Google;
  - konto organizacji: tego wymogu nie ma, ale potrzebny jest **D-U-N-S**, a nazwa, adres,
    e-mail i telefon firmy będą publiczne w sklepie.

  [Typ konta](https://support.google.com/googleplay/android-developer/answer/13634885)
- **D-U-N-S:** nazwa i adres muszą być identyczne w D&B, w rejestrze firmy i w profilu płatności
  Google. [Wymagane dane](https://support.google.com/googleplay/android-developer/answer/13628312)
- **Rejestracja:** [play.google.com/console/signup](https://play.google.com/console/signup).
  Wybierz „Organization”, zapłać 25 USD (jednorazowo).
  [Opłata](https://support.google.com/googleplay/android-developer/answer/6112435)
- **Weryfikacja tożsamości** osoby i firmy, potwierdzenie e-maili i telefonów kodami, powiązanie
  witryny przez [Google Search Console](https://search.google.com/search-console).
  [Weryfikacja](https://support.google.com/googleplay/android-developer/answer/10841920),
  [witryna](https://support.google.com/googleplay/android-developer/answer/13205715)
- **Status handlowca** (trader / non-trader). Termin: 1.02.2027, ale wygodniej od razu.
  Przy planowanych zarobkach z gry zwykle wybiera się „handlowiec”; wtedy sklep pokaże adres
  i telefon firmy. [Status handlowca](https://support.google.com/googleplay/android-developer/answer/13628312)
- **Android Developer Verification** (nowa weryfikacja dewelopera, globalnie w 2027): zarejestruj
  konto i grę, gdy konsola to zaproponuje.
  [Weryfikacja dewelopera](https://support.google.com/android-developer-console/answer/16561738)

| Czas | Koszt | Przekaż koordynatorowi | Blokuje testy |
|---|---|---|---|
| D-U-N-S do 30 dni; weryfikacja zwykle kilka dni | 25 USD; D-U-N-S 0 zł; domena ok. 10–15 EUR/rok | typ konta, pełna nazwa prawna z konta, „weryfikacja zakończona” | **TAK** |

### 2. Utworzenie gry w konsoli
„Utwórz aplikację”: nazwa **Century War**, typ **Gra**, **Bezpłatna**. Identyfikator gry
(`com.centurywar.game`) pojawi się przy pierwszym wgraniu pliku i potem nie da się go zmienić.
[Tworzenie aplikacji](https://support.google.com/googleplay/android-developer/answer/9859152)

| Czas | Koszt | Przekaż koordynatorowi | Blokuje testy |
|---|---|---|---|
| 15 min | 0 | „aplikacja utworzona” | **TAK** |

### 3. Klucz podpisu (upload)
To prywatny klucz, którym podpisujemy każdą wersję wysyłaną do Google. Koordynator poda jedną
komendę do utworzenia klucza (`docs/CI.md`). Wymyśl hasło i zapisz je w menedżerze haseł.
Zrób **dwie kopie pliku `.jks`** (np. pendrive i chmura z hasłem). W ustawieniach repozytorium
na GitHubie wpisz 4 sekrety: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
Przy pierwszym wydaniu w konsoli zostaw włączone **Play App Signing**: Google przechowuje
właściwy klucz aplikacji, a zgubiony klucz upload można wtedy odnowić.
[Podpisywanie aplikacji](https://developer.android.com/studio/publish/app-signing)

| Czas | Koszt | Przekaż koordynatorowi | Blokuje testy |
|---|---|---|---|
| 30 min | 0 | „sekrety wpisane” + odcisk **SHA-1** klucza (publiczny, bezpieczny) | **TAK** |

### 4. Dane wydawcy i polityka prywatności
Podaj 5 danych do `docs/store/OWNER_INPUT.md`:
1. pełna nazwa prawna firmy;
2. e-mail do spraw prywatności (musi ktoś go czytać);
3. adres korespondencyjny (będzie publiczny);
4. adres strony z polityką (`https://…`);
5. planowana data premiery.

Koordynator wygeneruje z tego gotowe polityki PL i EN. Ty je **przeczytasz** i wstawisz na
stronę firmy (zwykła strona, nie PDF, bez logowania). Nowy dopisek, który zatwierdziłeś
3.11: „Raport dla testera” zawiera też ustawienia dostępności, w tym informację, czy działa
TalkBack. Raport wysyła zawsze sam gracz, gra nic nie wysyła sama. Sprawdź adres w oknie
incognito i wpisz go w konsoli (Zawartość aplikacji → Polityka prywatności).
[Polityka i dane użytkowników](https://support.google.com/googleplay/android-developer/answer/10144311)

| Czas | Koszt | Przekaż koordynatorowi | Blokuje testy |
|---|---|---|---|
| 1–2 h (po założeniu strony) | 0 (poza domeną z kroku 1) | 5 danych + działający link do polityki | **TAK** |

### 5. Data safety i pozostałe deklaracje
W konsoli: **Zawartość aplikacji**. Koordynator przygotuje odpowiedzi do przeklikania
(`docs/store/data_safety.md`, `content_rating.md`). Najważniejsze:
- **Data safety:** „Tak, gra zbiera dane”, bo robi to Play Games. Nie zaznaczaj „brak danych”.
- **Reklamy:** nie.
- **Grupa docelowa:** proponowane 16–17 i 18+ (decyzja Twoja).
- **Ankieta wiekowa IARC:** przemoc kreskówkowa: tak.
- **Dostęp recenzenta:** gra działa bez logowania.

[Data safety](https://support.google.com/googleplay/android-developer/answer/10787469),
[Zawartość aplikacji](https://support.google.com/googleplay/android-developer/answer/9859455),
[IARC](https://support.google.com/googleplay/android-developer/answer/9859655),
[grupa docelowa](https://support.google.com/googleplay/android-developer/answer/9867159)

| Czas | Koszt | Przekaż koordynatorowi | Blokuje testy |
|---|---|---|---|
| 1–2 h | 0 | zrzut podsumowania Data safety i wynik IARC; wybrana grupa wiekowa | **TAK** |

### 6. Play Games: projekt i 83 identyfikatory
W konsoli: **Play Games Services → Konfiguracja**. Utwórz:
- **projekt** (1 ID, same cyfry);
- **4 rankingi**, sortowane od największego wyniku: oblężenie (Endless), kampania, potyczka,
  wyzwanie dnia;
- **78 osiągnięć**: 74 obecne + 4 nowe zatwierdzone 3.11 (`first_evolve`, `first_turret`,
  `first_research`, `stages_3`).

Razem **83 ID**. Nazwy i opisy osiągnięć dostaniesz od koordynatora w tabeli. Potem dodaj:
- poświadczenia OAuth dla Androida (SHA-1 z kroku 3 i SHA-1 „App signing” z Konfiguracja →
  Integralność aplikacji, widoczny po pierwszym wgraniu);
- swoje konto i konta testerów jako testerów Play Games;
- publikację konfiguracji do testów.

ID **nie są tajne**: skopiuj je do pliku albo tabeli i wyślij koordynatorowi, on wpisze je
do `docs/store/play_games_ids.properties`. Bez kompletu ID wersja do sklepu się nie zbuduje.
[Konfiguracja Play Games](https://developer.android.com/games/pgs/console/enable-features)

| Czas | Koszt | Przekaż koordynatorowi | Blokuje testy |
|---|---|---|---|
| 3–4 h | 0 | 83 ID (projekt, 4 rankingi, 78 osiągnięć) z nazwami | **TAK** |

### 7. Karta sklepu
Teksty w 10 językach są gotowe: `docs/store/listing_*.md`. Koordynator poda je bez nagłówków
roboczych, do wklejenia. Grafiki (ikona, grafika promocyjna, zrzuty telefonu i tabletu)
przygotuje zespół w zadaniu D7 (`docs/store/assets/D7/`). Twoja praca: wkleić teksty, wgrać
grafiki, wybrać kategorię „Strategia”, e-mail kontaktowy i kraje.
[Grafiki sklepu](https://support.google.com/googleplay/android-developer/answer/9866151)

| Czas | Koszt | Przekaż koordynatorowi | Blokuje testy |
|---|---|---|---|
| 1–2 h (po D7) | 0 | lista krajów, uwagi do tekstów i grafik | **TAK** |

### 8. Testerzy
W konsoli: **Testowanie → Testy zamknięte → Testerzy → lista e-mail**. Wpisz adresy testerów
i wyślij im **link zapisu** („opt-in”). Samo wpisanie adresu nie zapisuje testera: każdy musi
kliknąć link tym samym kontem Google i zainstalować grę ze Sklepu Play.
- **Ile:** przy koncie osobistym **co najmniej 12 osób przez 14 dni bez przerwy**, a Google
  sprawdza, czy naprawdę grali. Przy koncie organizacji to nie jest wymóg, ale zalecamy tyle samo.
- **Co dostają:** „Scenariusz dla testerów” z `docs/RELEASE.md`.
- **O co prosić:** w **3. i 10. dniu** o „Raport dla testera” (Ustawienia → O grze → Raport
  dla testera). Tester sam wybiera, komu go wysyła.

[Testy zamknięte](https://support.google.com/googleplay/android-developer/answer/9845334),
[12 testerów / 14 dni](https://support.google.com/googleplay/android-developer/answer/14151465)

| Czas | Koszt | Przekaż koordynatorowi | Blokuje testy |
|---|---|---|---|
| 14 dni testu + zbieranie ludzi | 0 | liczba zapisanych testerów (nie ich adresy), raporty z 3. i 10. dnia | **TAK** (ktoś musi testować) |

### 9. Serwer rankingu globalnego
Na testach **można go pominąć**. Gra działa bez niego, a rankingi zapewnia Play Games.
Włączymy go później, przed premierą: koszt ok. 5–7 EUR/mies. (`docs/research/R5-server.md`).

| Czas | Koszt | Przekaż koordynatorowi | Blokuje testy |
|---|---|---|---|
| 0 teraz | 0 teraz | „testy bez serwera: zgoda” | **NIE** |

### 10. Dzień premiery w grze (`Daily.RELEASE_DAY`)
Od tej daty liczą się wyzwania dnia. **Po premierze nie można jej zmienić.** Wystarczy podać
koordynatorowi datę premiery (RRRR-MM-DD), resztę zrobi on. Uwaga: kontrola wydania przyjmuje
tylko datę od 30 dni wstecz do 60 dni naprzód od dnia budowania. Jeśli premiera jest dalej,
wersję testową zbudujemy ze świadomym wyjątkiem i ustawimy datę bliżej premiery.

| Czas | Koszt | Przekaż koordynatorowi | Blokuje testy |
|---|---|---|---|
| 5 min (decyzja) | 0 | data premiery albo „jeszcze nieznana” | **NIE** |

## Kolejność w skrócie

D-U-N-S → konto i weryfikacja → strona firmy z polityką → utworzenie gry → klucz podpisu →
Play Games (83 ID) → deklaracje i karta sklepu → koordynator buduje wersję i wgrywa ją na
testy wewnętrzne → sprawdzenie na Twoim telefonie → testy zamknięte z testerami.
Realnie: **2–5 tygodni** od zera, głównie przez D-U-N-S i weryfikację w Google.
