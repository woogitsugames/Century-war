# Century War — plan monetyzacji przyjaznej graczom

**Status:** plan na przyszłą wersję, **nic z poniższych mechanizmów nie jest wdrożone w v1**. Obecna aplikacja jest darmową grą offline bez reklam, IAP i integracji sieciowych. Stan przeglądu wymagań: 31.10.2026.

## Założenia produktu

- Brak reklam pełnoekranowych wymuszanych przed bitwą, w trakcie rozgrywki lub przy powrocie do menu.
- Reklama wyłącznie **dobrowolna i z nagrodą**, po zakończonej bitwie. Podstawowa nagroda jest zawsze dostępna bez reklamy.
- Jednorazowy zakup zamiast abonamentu. Bez sprzedaży siły jednostek, premii bojowych, odblokowywania jednostek za pieniądze i płatnego losowania.
- Dodatki kosmetyczne (skórki epok, sztandary, ramki profilu) nie zmieniają mechaniki.
- Uczciwy ranking: **płatności i oglądanie reklam nie mogą zwiększać wyniku rankingowego ani dawać szybszego dostępu do bonusów wpływających na rywalizację**.

## 1. Reklama z nagrodą: podwojenie medali

| Element | Propozycja |
|---|---|
| Miejsce | Ekran wyników **po zakończeniu** kwalifikującej się bitwy PvE. |
| Komunikat | PL: „Obejrzyj reklamę i odbierz 2× medale”; EN: „Watch an ad to earn 2× medals”. |
| Mechanika | Podstawowe medale naliczają się normalnie. Po zdarzeniu potwierdzającym obejrzenie reklamy dopisz **tylko różnicę** do 2× nagrody bazowej. |
| Limit | Maksymalnie **jedna** nagroda reklamowa na identyfikator zakończonej bitwy; dodatkowy limit dzienny dopiero po analizie nadużyć. |
| Błąd/brak internetu | Nie zabieraj podstawowej nagrody; przy niedostępności reklamy nie obiecuj bonusu. Gra pozostaje offline-first. |
| UI | Przycisk można pominąć bez opóźnienia, karania gracza ani blokowania następnej bitwy. |

Żadnych reklam w środku bitwy, reklam „za życie”, ograniczania energii ani wymuszania filmu po porażce. Użytkownik ma świadomie wybrać reklamę.

## 2. Jednorazowy zakup: „Pakiet Wspierającego”

**Proponowany produkt niewyczerpywalny:** supporter_no_ads (Google Play: produkt jednorazowy, bez abonamentu).

- **„Bez reklam” oznacza brak ofert filmów z nagrodą.** Gra i tak nie ma obowiązkowych reklam — nie wolno sugerować, że kupujący usuwa reklamy przerywające rozgrywkę.
- Pakiet automatycznie przyznaje równowartość bonusu 2× medali po kwalifikujących się bitwach bez oglądania filmu, a **w wyzwaniach dnia** daje 2× medalową nagrodę za ukończenie wyzwania.
- Współczynniki **nie kumulują się**: maksimum wynosi 2× nagrody bazowej, nigdy 4×.
- Zakup nie dodaje punktów, fal, gwiazdek ani statystyk jednostek bezpośrednio; widoczny opis zakupu musi wyliczać rzeczywiste korzyści.
- Przywracanie zakupu na nowym urządzeniu po połączeniu z Google Play; aktualizacja uprawnień po zwrocie płatności.

**Uwaga produktowa:** nazwa „bez reklam” jest mało atrakcyjna, gdy wszystkie filmy są dobrowolne. W sklepie gry lepiej eksponować „Pakiet Wspierającego — bonus medali bez filmów” i jasno przedstawiać warunki.

## 3. Kosmetyka

| Przykład | Zmiana widoczna dla gracza | Wpływ na rozgrywkę |
|---|---|---|
| Skórki epok | Inne kolory/wygląd armii i budowli | Brak |
| Sztandary | Wygląd flagi profilu/armii | Brak |
| Ramki profilu | Ozdoba widoku profilu i wyników | Brak |

Wyłącznie jednoznaczne, stałe ceny w walucie lokalnej Google Play. Bez loot boxów, obowiązkowej wirtualnej waluty i ukrytych kosztów. Cenę ustalić dopiero na podstawie badań rynku; nie wpisywać wymyślonych kwot do kodu ani materiałów sklepu.

## 4. Twarda blokada: brak pay-to-win w rankingach

**Stan kodu:** wyzwanie dnia stosuje ujednolicone bonusy, ale kampania, oblężenie bez końca i potyczka korzystają z badań zakupionych za medale (Profile.bonuses). Dlatego **2× medale przyspiesza rozwój i pośrednio może polepszać wyniki** na pozostałych tablicach. Sam brak płatnych jednostek nie gwarantuje uczciwych rankingów.

**Warunek uruchomienia płatnych mnożników (obowiązkowy):**

1. Każdy **publiczny ranking rywalizacyjny** ma bazować na konfiguracji bez wpływu płatnych lub reklamowych przyspieszeń: ujednolicone badania/bonusy albo oddzielny tryb rankingowy z identycznym startem.
2. Jeśli nie da się ujednolicić kampanii, potyczki lub oblężenia, **nie publikuj ich globalnych rankingów** jako uczciwej rywalizacji; pozostaw wyniki lokalne i wyraźnie je oznacz.
3. Wynik dzienny licz z jednego rankingowego podejścia; wszelkie filmy/pakiety zwiększają tylko medalową nagrodę po bitwie, **nie wynik**.
4. Testy regresji: zakup pakietu/reklama kontra profil bez monetyzacji, ten sam seed i komendy => identyczny wynik na wszystkich udostępnionych tablicach globalnych.
5. Jeśli warunek nie przejdzie, **odłóż płatne mnożniki medali** i monetyzuj tylko kosmetykę do czasu poprawy logiki rankingów.

## 5. Integracja techniczna — osobne zlecenie, bez zmian w tym PR

### AdMob z mediacją

- Dodaj Google Mobile Ads SDK, a w razie mediacji wybrane adaptery sieci, zgodne ze wsparciem aktualnej wersji i polityką prywatności; nie dodawaj zależności do bazowej wersji bez potrzeby.
- RewardedAd / zdarzenie zdobycia nagrody tylko po świadomym kliknięciu. Deduplication po identyfikatorze bitwy, idempotentny zapis medali. Nie nadawaj nagrody za samo otwarcie reklamy.
- Konfiguracja testowych identyfikatorów reklam, kontrola frequency cap, filtr treści reklamy według ratingu gry.
- Przed publikacją przeprowadź audyt rzeczywistych transferów każdego SDK i partnera mediacji; zaktualizuj „Zawiera reklamy”, Data safety, politykę prywatności i wymagane zgody dla EOG (CMP/TCF, o ile ma zastosowanie).
- Jeśli gra ma kierować reklamy do dzieci lub użytkowników w nieznanym wieku w ramach Families, używaj wyłącznie odpowiednich **Families Self-Certified Ads SDKs**, bez reklam behawioralnych; stosuj wymagane formaty i zamykanie reklam. Nie deklaruj Families bez audytu.

### Google Play Billing

- **Na 31.10.2026 nie wystarczy „Play Billing 7+” z pierwotnego zlecenia.** Standardowa możliwość dostarczania nowych aplikacji/aktualizacji z Billing 7 zakończyła się 31.08.2026 (odroczenie do 01.11.2026 wyłącznie po uzyskaniu przedłużenia). Zalecana aktualna linia: **Google Play Billing Library 9.1.0 lub nowsza obsługiwana**; linia 8 pozostaje obsługiwana do 31.08.2027.
- Produkty jednorazowe, odczyt ProductDetails i statusów transakcji, obsługa pending purchases, potwierdzanie (acknowledge), zwrotów i przywracania uprawnień.
- Nie przyznawaj uprawnień wielokrotnie po ponownych callbackach. Dla weryfikacji serwerowej stosuj bezpieczne tokeny i uprawnienia konta usługowego Google Play; nigdy sekretów w APK.
- Zachowaj możliwość gry offline bez zakupów; pobieranie statusu IAP wymaga połączenia. Rozdziel przyznawanie bonusów od symulacji bitwy, aby zachować deterministyczność.

## 6. Proponowana kolejność wdrożenia

1. **Etap A:** bezpłatna v1, pomiar jakości i retencji bez inwazyjnej analityki; bez reklam/IAP.
2. **Etap B:** kosmetyka i test procesu zakupowego, ale dopiero z poprawną deklaracją danych i regulaminem zwrotów.
3. **Etap C:** znormalizowane tryby rankingowe, testy anty-cheat i dopiero potem reklamy z nagrodą / pakiet medali.
4. **Etap D:** ewentualny własny serwer wyników z identyfikatorem UUID i jawnie opisaną retencją.

**Kryteria akceptacji:** brak reklam podczas bitwy; brak blokad offline; brak podwójnych grantów; zakup da się przywrócić; wszystkie rankingi rywalizacyjne pozostają niezależne od zakupów; zgody i deklaracje odpowiadają finalnemu AAB.

## Oficjalne źródła

- https://developer.android.com/google/play/billing/release-notes
- https://developer.android.com/google/play/billing/deprecation-faq
- https://support.google.com/googleplay/android-developer/answer/9893335
- https://support.google.com/googleplay/android-developer/answer/10787469
