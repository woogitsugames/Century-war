# Wydanie Android / Google Play

## Kandydat do testów zamkniętych

Stan: **4 listopada 2026 r., wieczór (gałąź `integ/tura10`, PROTOCOL 0.8.8: etapy 2–18 krótsze,
ankieta testera, pętla testowa Play; przegląd R12 w `docs/research/R12-backlog.md`).**
Lista dla właściciela — bez wiedzy programistycznej.
„Zrobione” znaczy: poprawka jest w kodzie i ma automatyczny test. „Zostało” znaczy:
bez tego nie wysyłamy gry testerom w Google Play. Numery w nawiasach (np. A156-10)
to pozycje z audytu przed premierą (`docs/reports/audit-156.md`).

### Zrobione (gra i kod)

- [x] **Wznawianie przerwanej bitwy** z wieżami ulepszonymi w każdym miejscu i z falą „bliźniaczy
      najazd” w trybie Endless — wcześniej taki zapis był odrzucany (A156-01, A156-02).
- [x] **Ranking przyjmuje tylko powtórki według obecnych zasad gry** — stara powtórka nie trafi
      do bieżącej tabeli (A156-04).
- [x] **Usunięcie danych gracza z serwera jest ostateczne** — wynik wysłany w tej samej chwili
      nie przywróci nicku ani wyniku (A156-05).
- [x] **Postęp nie cofa się przy wolnym zapisie** (np. przeciążony telefon) — nowy odczyt czeka
      na zaległy zapis (A156-06).
- [x] **Gra nie rusza sama w tle** po wygaszeniu ekranu lub zmianie okna (A156-07); Wstecz
      podczas ekranu startowego nie zamyka gry z błędem (A156-17).
- [x] **Opcja „mniej błysków”** działa tak samo przy prędkości ×1, ×3 i ×5, także dla nocnych
      trafień w bazę (A156-08).
- [x] **Przyciski wież na niskim ekranie** są większe i nie nachodzą na siebie; cena jednostki
      mieści się w przycisku w ostatniej epoce (A156-09, A156-24, A156-25).
- [x] **Nagroda za bitwę liczona dokładnie raz** — także gdy Android zamknie grę w trakcie
      zapisu. Najpierw trwale zapisujemy profil (medale, gwiazdki, misje) razem z numerem
      bitwy, dopiero potem kasujemy zapis przerwanej bitwy. Po ponownym uruchomieniu gra
      rozpoznaje już rozliczoną bitwę i nie da za nią drugiej nagrody; nierozliczoną można
      wznowić i rozliczyć raz (A156-10).
- [x] **Reset postępu** kasuje też zapis przerwanej bitwy (A156-18); profil z chmury Play
      Games od razu stosuje wszystkie ustawienia (A156-19); zmiana rozmiaru wyświetlania
      i Galaxy Fold przeliczają układ (A156-20).
- [x] **Czytnik ekranu TalkBack** rozpoznaje przyciski menu, ustawień i pauzy (A156-21), ekrany
      poza bitwą (mapa, tryby, Badania, Misje, listy; R11-01) i przyciski paska bitwy (dzień 5) —
      wymaga jeszcze próby na telefonie, patrz niżej. **Decyzja właściciela 4.11:** koniec prac
      specjalnie dla niewidomych; to, co jest, zostaje, ale w sklepie nie obiecujemy gry dla
      niewidomych (tylko palety, kontrast, większy tekst, tempo ×½, czytnik ekranu w menu).
- [x] **Muzyka** nie zawiesza się po błędzie dźwięku (A156-22); nagrane utwory gry (21 plików) są w pakiecie.
- [x] **Balans wariantów epok** (A156-13): Para i Krzem wyrównane (różnica 3 i 2 pp zamiast
      17,5 i 11; `docs/BALANCE.md`, sekcja „A156-13”), gałąź `gpt/163-variant-balance` scalona.
- [x] **Polityka prywatności i Data safety** opisują to, co gra robi naprawdę (A156-16);
      opis gry bez porównań do innych gier (A156-23).
- [x] **Kontrole automatyczne wydania**: testy konfiguracji, osobne testy debug i release,
      wydanie na tagu dopiero po zielonych testach tego samego commita, obrazy zrzutów
      w artefakcie CI (A156-11, 12, 14, 15, 26, 27).
- [x] Pełne testy (`:app:testDebugUnitTest :sim:test :server:test`) zielone na gałęzi
      integracyjnej — ok. 13 min (szczegóły w [TEST_SPEED.md](TEST_SPEED.md)).
- [x] **Próba wersji release (3 listopada 2026 r.)** — APK i AAB z R8 i shrinkResources
      zbudowane lokalnie dokładnie jak smoke CI (syntetyczne ID Play Games, podpis debug
      tylko do próby): build OK, bez ostrzeżeń R8; kontrola nazw i pól zapisów po R8, kontrola
      rzeczywistego DEX, manifest (`com.centurywar.game`, `MainActivity`) i profil ART w obu
      paczkach — wszystko zaliczone; bez klucza upload build release jest nadal
      blokowany (`Brak prawidłowego podpisu release`). Rozmiar: **APK 20,0 MiB, AAB 20,6 MiB**, z czego 19,0 MiB
      to muzyka (tabela w „Rozmiar APK/AAB i czas uruchomienia”). Symulacja po R8 (bez telefonu):
      10 złotych powtórek, zrzuty przerwanej bitwy v16–v18 i rekord powtórki zgodne bajt w bajt
      z wersją bez minifikacji. Poprawka: typ misji (`MissionType`, zapisywany w profilu po
      nazwie) chroniony regułą R8 jak metryki osiągnięć; kontrola obejmuje teraz pola
      rozliczenia bitwy (A156-10).

- [x] **Nowości tur 9–10 i dni 1–4** (sprawdzone automatycznie, do obejrzenia przez testerów
      według scenariusza niżej):
  - **Wódz** od 8. etapu kampanii (przycisk w dolnym pasku bitwy) i **stopnie wież** 1–3;
  - **nagrana muzyka**: 21 utworów, spokojny i bojowy motyw każdej epoki, płynne przejścia,
    muzyka ciszej pod efektami;
  - **krótsze etapy 2–18**: 2–7 ok. 7–9 min, 8–12 ok. 9–9,5 min, 13–18 ok. 10–11 min (zamiast
    12–14), gwiazdki bez kary za przyspieszoną presję (`docs/BALANCE.md`: dzień 1, R11-02,
    „Etapy 13–18”); etapy 19+ bez zmian;
  - **pierwsza bitwa** spokojniejsza (dymki co 50 s, pełnoekranowe zapowiedzi epok tylko
    w kampanii);
  - **wyniki bitwy**: medale „Za bitwę” i „Za osiągnięcia” osobno;
  - **„Co nowego”** jako przycisk w menu (z kropką po aktualizacji) i strzałka przewijania listy;
  - **osiągnięcia i misje** dowódcy oraz stopni wież (razem 79 osiągnięć), wpisy w Kodeksie;
  - **zewnętrzny ekran Folda**: zegar bitwy pod minimapą także przy presji oblężniczej,
    górny pasek bez nachodzenia napisów.
- [x] **Nowości z 3–4.11 (kolejne dni i R11):**
  - **powitanie po przerwie**: po 3+ dniach bez gry baner w menu i premia za powrót (+30 medali);
  - **kamienie milowe w Oblężeniu bez końca** i Oblężeniu tygodnia: brąz, srebro, złoto
    (premia po bitwie) i nowy rekord fali;
  - **„oddech” muzyki w bitwie**: co ok. 2,5 min muzyka na ok. 30 s cichnie do tła;
  - **samouczek wraca**, jeśli Android zamknie grę w jego trakcie; utracona bitwa po
    aktualizacji z komunikatem i zwrotem próby.
- [x] **Pętla gry dla raportu przedpremierowego Play (R11-04)**: 4 scenariusze grają same
      (menu, bitwa AI ×3, późny Endless, przerwanie i wznowienie), wynik w JSON, osobny profil
      pętli i osobny proces `:testloop`; instrukcja w „Raport przedpremierowy i pętla gry”.
- [x] **Ankieta testera** (tylko wersje testowe, flaga `TESTER_SURVEY`): jednorazowo w menu po
      samouczku i 5 bitwach albo w 3. dniu gry; odpowiedź tylko w „Raporcie dla testera”
      (sekcja „Ankieta testera” niżej).
- [ ] **W drodze (gałąź `claude/dzien-28-nowosc`, jeszcze nie na `integ/tura10`):** „Nowość na
      tym etapie” w panelu etapu i iskra na mapie kampanii (R11 poz. 6). Do scalenia scenariusz
      testera o niej nie wspomina.

### Zostało — decyzje i dane właściciela

Te pozycje może dostarczyć tylko właściciel. Szczegóły i instrukcje:
[docs/store/OWNER_INPUT.md](store/OWNER_INPUT.md).

- [ ] **Pięć pól wydawcy** w `OWNER_INPUT.md` (dziś wszystkie mają `WYMAGA_DANYCH_WLASCICIELA`):
  - [ ] pełna **nazwa prawna wydawcy** (taka jak na koncie Google Play),
  - [ ] **e-mail** do spraw prywatności i usuwania danych,
  - [ ] **adres korespondencyjny** wydawcy,
  - [ ] **publiczny adres HTTPS polityki prywatności** (wersja PL i EN),
  - [ ] **data premiery** (RRRR-MM-DD).
- [ ] **Dzień premiery w grze** (`Daily.RELEASE_DAY`, dziś zastępczo 2027-12-01) — koordynator
      wpisze go po decyzji o dacie; po premierze już się go nie zmienia.
- [ ] **Serwer rankingu globalnego:** adres `https://` (`CW_SERVER_URL`), hosting i czas
      przechowywania danych — albo decyzja „na start bez rankingu globalnego”.
- [ ] **Sekrety tylko w CI** (nigdy w repozytorium): klucz upload do podpisu, `CW_HMAC_KEY`,
      `CW_SERVER_URL`, `CW_PRIVACY_URL`.
- [ ] **Google Play Games:** 84 identyfikatory (1 projekt, 4 rankingi, 79 osiągnięć) z Play
      Console do `docs/store/play_games_ids.properties` (dziś `REPLACE_ME`).
- [ ] **Play Console:** konto dewelopera z tymi samymi danymi, kraje, grupa wiekowa (ankieta
      IARC), zatwierdzenie Data safety dla prawdziwego pakietu AAB.
- [ ] **Numer wersji** pierwszego wydania (`releaseVersionCode`, `releaseVersionName` w
      `gradle.properties`; dziś `1` i `0.1.0-dev`).
- [ ] **Status handlowca w Play Console** (trader / non-trader, termin **1.02.2027**) — decyzja
      właściciela, bo od niej zależy, co sklep pokaże publicznie. Szczegóły:
      [„Formalności Play 2026–2027”](#formalności-play-20262027).
- [ ] **Weryfikacja tożsamości dewelopera** (Android Developer Verification) zrobiona przed
      wysyłaniem testerom plików APK spoza Play — patrz ta sama sekcja.

### Zostało — próby na prawdziwych urządzeniach

Automatyczne testy tego nie zastąpią. Wystarczy telefon i Galaxy Fold właściciela.

- [ ] Zbudowany **podpisany pakiet AAB** (release, R8) instaluje się i uruchamia; pomiar
      **czasu startu** (sekcje „AAB, R8 i Baseline Profile” i „Pomiar wydania” niżej). Rozmiar
      i poprawność R8 sprawdzone w próbie 3 listopada; brakuje: podpisu kluczem upload
      (sekret CI), prawdziwych ID Play Games zamiast syntetycznych, pięciu pól wydawcy
      (`ownerInput`) i `Daily.RELEASE_DAY` — bez nich `releaseChecklist` słusznie blokuje build.
- [ ] **Przerwanie bitwy**: w trakcie bitwy przełącz na inną aplikację, wymuś zamknięcie gry
      w ustawieniach Androida, uruchom ponownie — bitwa do wznowienia, nagroda raz.
      Powtórz zamknięcie **zaraz po** ekranie wyniku — po starcie nie ma oferty wznowienia,
      medale bez zmian.
- [ ] **TalkBack** wg sekcji „Testy ręczne czytnika ekranu (164)”.
- [ ] **Aktualizacja ze starszej wersji** wg sekcji „Testy ręczne aktualizacji i odporności zapisu (154)”
      (przerwana bitwa ze starej wersji nadal się wznawia).
- [ ] Logowanie Play Games, rankingi, osiągnięcia i zapis w chmurze — po wpisaniu prawdziwych ID.
- [ ] Muzyka: głośność względem efektów, pętle i przenikanie (uwagi z odsłuchu właściciela).
- [ ] **Brak pamięci (R2-05):** w trakcie kampanii i bitwy (debug) wykonaj
      `adb shell am send-trim-memory com.centurywar.game RUNNING_LOW` (Android 13 i starsze)
      albo przełącz grę w tło i `adb shell am send-trim-memory com.centurywar.game BACKGROUND`.
      Po powrocie mapa kampanii, tło bitwy i błysk bazy są na miejscu, muzyka wraca, bitwa
      trwa od tego samego miejsca, w logcat brak błędów.
- [ ] **ANR → pytanie o raport (R2-05, Android 11+):** w buildzie debug wywołaj brak odpowiedzi
      (`adb shell am hang` na kilkanaście sekund w trakcie gry, potem „Zamknij aplikację”),
      uruchom grę ponownie: pojawia się jednorazowe pytanie o raport, a raport zawiera
      „Przyczyna: ANR” i stos wątku `main`. Ślad nie może zawierać ścieżek z nazwą konta.

### Scenariusz dla testerów: telefon i Galaxy Fold, krok po kroku

Tę listę dostają testerzy (i właściciel przed wysyłką). Każdy krok to jedno zadanie i jedno
pytanie „czy wyszło?”. Jeśli coś nie działa: zrób zrzut ekranu, zanotuj, co robiłeś tuż przed,
i wyślij raport z gry (**Ustawienia → O grze → Zgłoś problem**). Raport nie zawiera nicku.
W **3. i 10. dniu** testu wyślij też „Raport dla testera” (**Ustawienia → O grze**), nawet
jeśli wszystko działa. W menu raz pojawi się krótka **ankieta** (ocena 1–5 i „Co najbardziej
przeszkadza?”); odpowiedz albo dotknij „Pomiń”. Odpowiedź trafia tylko do tego raportu.

**A. Pierwsze uruchomienie (ok. 15 min, nowa instalacja)**
1. Zainstaluj grę ze sklepu Play (link testowy), uruchom. Menu ma się pojawić w kilka sekund.
2. Dotknij **Graj** i przejdź samouczek. Dymki mają wskazywać przyciski, nic nie może zasłaniać
   przycisku, którego dymek dotyczy.
3. Wygraj pierwszą bitwę. Na wynikach: gwiazdki, medale „Za bitwę” i ewentualnie
   „Za osiągnięcia”. Dotknij **Dalej**.
4. Na mapie kampanii zagraj etap 2. Bitwa ma trwać mniej więcej 7–9 minut.
5. Wejdź w **Badania** i kup coś za medale. Wróć przyciskiem Wstecz telefonu.

**B. Nowości (ok. 30–45 min, w kolejnych dniach)**
6. Dojdź do **etapu 8**: w dolnym pasku bitwy pojawia się **Wódz**. Przywołaj go i sprawdź,
   czy pobliscy żołnierze świecą aurą. Bitwy etapów 8–12 trwają ok. 9–10 min, 13–18
   ok. 10–11 min. Od etapu 19 bitwy znów są dłuższe; napisz, jeśli etap 19 wyda się nagle
   dużo trudniejszy od 18.
7. Kup wieżę, a potem podnieś jej **stopień** (panel wież, przycisk ulepszenia). Przy wieży
   ma być widać jej stopień (2, potem 3), a panel pokazuje koszt kolejnego.
8. **Muzyka:** w menu gra spokojny motyw, w bitwie muzyka narasta, gdy armie się starły.
   Zgłoś trzaski, przerwy na złączeniu pętli albo zbyt głośną muzykę względem efektów.
9. **Misje** (menu): odbierz nagrodę za ukończoną misję dnia. Zajrzyj w **Osiągnięcia**.
10. **Co nowego** (przycisk w menu): lista otwiera się, da się ją przewinąć do końca.
11. Zagraj **Oblężenie bez końca** do pierwszego wyboru kart (fala 5) i do bossa (fala 10).
    Po drodze pojawia się wstęga **brąz / srebro / złoto** (premia po bitwie), a w kolejnych
    próbach licznik „do rekordu”.
12. Zagraj **Wyzwanie dnia** i zakładkę **Oblężenie tygodnia**. Do dnia premiery obowiązują
    tu zasady sprzed premiery (bez dowódcy, pogody i wydarzeń dnia; oblężenie tygodnia bez
    nowych zestawów). To zasada, nie błąd.
12a. Jeśli wrócisz do gry po **3 lub więcej dniach przerwy**, w menu ma się pojawić powitanie
    z premią za powrót.

**C. Przerwy i zapis (ok. 10 min)**
13. W trakcie bitwy przełącz się na inną aplikację na minutę i wróć. Gra ma być w pauzie,
    sama nie gra dalej.
14. W trakcie bitwy zamknij grę z listy ostatnich aplikacji. Uruchom ponownie: gra proponuje
    **Wznów**. Dokończ bitwę i sprawdź, że nagroda przyszła raz.
15. Wyłącz internet (tryb samolotowy) i zagraj bitwę. Wszystko poza Play Games ma działać.

**D. Galaxy Fold i duże ekrany (tylko kto ma składany telefon albo tablet)**
16. Na **zewnętrznym ekranie** zagraj bitwę do presji oblężniczej (napis o presji). Zegar
    bitwy ma być widoczny pod minimapą, napisy górnego paska nie mogą na siebie nachodzić.
17. **Rozłóż** telefon w trakcie bitwy i złóż go z powrotem. Bitwa trwa dalej, przyciski
    reagują tam, gdzie je widać (bez przesunięcia dotyku).
18. Na wewnętrznym ekranie przejrzyj menu, Kodeks i Ustawienia. Nic nie może być ucięte.

**E. Dostępność i ustawienia (ok. 10 min)**
19. **Ustawienia → Dostępność**: zmień paletę kolorów, rozmiar tekstu na 130%, włącz
    „mniej błysków”. Zagraj chwilę bitwy i sprawdź, czy wszystko jest czytelne.
20. Włącz **tryb leworęczny** i sprawdź, czy przyciski bitwy są po drugiej stronie.
21. (Kto używa czytnika ekranu) włącz **TalkBack** i przejdź menu, ustawienia i mapę
    kampanii. Przy włączonym TalkBack bitwa startuje w tempie ×½ (opcja **Tempo bitwy ×½**
    w Ustawienia → Dostępność). Gra nie jest przygotowana dla osób niewidomych i dalszej
    obsługi bitwy czytnikiem ekranu nie planujemy.
22. Zmień **język** gry w Ustawieniach i wróć do polskiego.

**F. Co odesłać po teście**
23. Model telefonu i wersja Androida (Ustawienia telefonu → Informacje o telefonie).
24. Co się podobało, co było niejasne, gdzie gra się zacinała lub grzała telefon.
25. Ile dni i mniej więcej ile minut dziennie grałeś. Google pyta o to przy wniosku o produkcję.
26. „Raport dla testera” z 3. i 10. dnia (jeśli jeszcze nie poszedł).

### Kiedy wysyłamy testerom

Gdy wszystkie pola w obu sekcjach „Zostało” są odhaczone, koordynator buduje podpisany AAB,
uruchamia `./gradlew :app:releaseChecklist` (zatrzyma wydanie, jeśli czegoś brakuje)
i postępuje według sekcji „Testy zamknięte w Play Console” poniżej
(12 testerów przez 14 dni dla nowego konta osobistego).

## Formalności Play 2026–2027

Stan: **3 listopada 2026 r.** Lista uzupełnia „Zostało” powyżej i
[`store/PLAY_CONSOLE.md`](store/PLAY_CONSOLE.md) (sekcja 8). Oznaczenia:
**[oficjalne]** — strona Google; **[nieoficjalne]** — artykuł lub blog, do sprawdzenia
w konsoli przed decyzją.

| Co | Termin / próg | Co zrobić u nas | Źródło |
|---|---|---|---|
| **Status handlowca** (EOG i UK; każde aktywne konto, bez względu na kraje dystrybucji) | do **1.02.2027** [oficjalne] | Właściciel deklaruje w Play Console. Handlowiec: w sklepie publicznie nazwa, adres, telefon, e-mail (organizacja także D-U-N-S). Nie-handlowiec: tylko e-mail i kraj. Planowana monetyzacja (`store/monetization_plan.md`) zwykle oznacza handlowca | [Google: trader status](https://support.google.com/googleplay/android-developer/answer/13628312) |
| **Weryfikacja tożsamości dewelopera** | od **30.09.2026** w Indonezji, Singapurze i Tajlandii; globalnie **w 2027** (bez dokładnej daty) [oficjalne]. Wcześniejsze artykuły wymieniały też Brazylię i wrzesień jako miesiąc — oficjalna strona tego nie potwierdza | Konto zweryfikować przed globalnym wdrożeniem. APK debug przez `adb` zostaje dozwolone [oficjalne]. Wysyłka APK testerom poza Play: po wdrożeniu w Polsce przejść na tor testów wewnętrznych Play (do 100 osób) albo konto „limited distribution” (do 20 urządzeń, bez dowodu tożsamości) [oficjalne] | [Google: developer verification](https://support.google.com/android-developer-console/answer/16561738) |
| **16 KB page size** | aplikacje z targetSdk 35+ muszą je wspierać; od **1.02.2027** nie da się wydać aktualizacji bez wsparcia [oficjalne] | Gra nie ma bibliotek natywnych (sprawdzone 3.11.2026 dla PGS 22.1.0, `play-services-base`, `basement`), więc jest zgodna. Kontrola przed każdym AAB: `unzip -l app-release.aab \| grep '\.so$'` ma zwrócić pusto; jeśli nowe SDK wniesie `.so` — `zipalign -c -P 16 -v 4` na APK z bundletool | [Google: 16 KB](https://developer.android.com/guide/practices/page-sizes) |
| **Android vitals** | próg złego działania: **1,09%** awarii i **0,47%** ANR zauważalnych przez użytkownika (średnio), **8%** na model telefonu [oficjalne] | Przez cały test zamknięty co tydzień: Play Console → Jakość → Android vitals (awarie, ANR, wolne sesje, pamięć). Przekroczenie = niższa widoczność i ostrzeżenie w karcie sklepu | [Android vitals](https://developer.android.com/games/optimize/vitals) |
| **Progi pamięci** (gry, p90 z 28 dni, anon RSS + swap) | od **02.2027** [oficjalne]; np. telefon 4 GB: 2,25 GB na pierwszym planie, 2,0 GB w tle; bitmapy w tle > 200 MB = źle; DEX ≥ 25% optymalizacji tylko gdy DEX gry > 50 MB | Spodziewamy się dużego zapasu (budżet bitmap 2–8 MiB). Zmierzyć `adb shell dumpsys meminfo com.centurywar.game` w Endless po fali 40 i dopisać do `PERFORMANCE.md` | [Google: progi pamięci](https://support.google.com/googleplay/android-developer/answer/17492799), [blog 08.2026](https://android-developers.googleblog.com/2026/08/app-quality-memory-optimization-secure-onboarding.html) |
| **targetSdk 36** | nowe aplikacje i aktualizacje od **31.08.2026** [oficjalne] | Spełnione (`targetSdk = 36`) | [Google: target API](https://developer.android.com/google/play/requirements/target-sdk) |
| **targetSdk 37 (Android 17)** | prawdopodobnie **31.08.2027** — tak wynika z rytmu Google, oficjalna tabela jeszcze tego nie podaje [nieoficjalne] | Gry są zwolnione z ignorowania orientacji na dużych ekranach (`android:appCategory="game"`) [oficjalne]. Przed podniesieniem sprawdzić na Foldzie wymuszone przez użytkownika proporcje | [Android 17: ograniczenia ignorowane](https://developer.android.com/about/versions/17/changes/ff-restrictions-ignored), [ecorpit (nieoficjalne)](https://ecorpit.com/android-17-targetsdk-37-adaptive-layout-migration-2026/) |
| **Wniosek o produkcję** (konto osobiste po 13.11.2023) | 12 testerów z ciągłym opt-in przez 14 dni; ocena zwykle do 7 dni [oficjalne] | Google pisze wprost, że powodem dalszego testowania może być **„insufficient tester engagement during the testing period”** [oficjalne] — testerzy muszą grać, nie tylko zainstalować. Raport testera (zlecenie `tasks/R2-04-tester-report.md`) daje dane do odpowiedzi | [Google: wymagania testów](https://support.google.com/googleplay/android-developer/answer/14151465) |
| **Raport przedpremierowy** | automatycznie po wgraniu AAB na tor testowy [oficjalne] | Przejrzeć awarie, zrzuty, ostrzeżenia dostępności po każdym AAB | [Google: pre-launch report](https://support.google.com/googleplay/android-developer/answer/9842757) |
| **Firebase Test Lab** (opcjonalnie) | plan Spark bez opłat: **5 testów dziennie na fizycznych i 10 na wirtualnych urządzeniach**; plan Blaze: 30 / 60 min dziennie bez opłat, potem 5 / 1 USD za godzinę [oficjalne] | Tylko po decyzji właściciela (projekt Firebase). `gcloud firebase test android run --type robo` | [Firebase: limity Test Lab](https://firebase.google.com/docs/test-lab/usage-quotas-pricing) |

## Testy ręczne czytnika ekranu (164)

Zakres przed premierą: menu główne, Ustawienia, Dostępność, Podpowiedzi,
panel pauzy oraz (Dzień 5) cała bitwa: HUD (złoto, doświadczenie, bazy,
fala w Endless, armia, jednostki, akcje, awans, pauza), wybór doktryny,
karty Endless i wariantu epoki, panel wież, panel ulepszenia, zapowiedź
epoki, samouczek i wyniki. Ostrzał i ulepszenie jednostki działają przez
akcje TalkBack (bez przeciągania), a ważne zdarzenia czyta żywy region
„Komunikaty bitwy”. Kampania (mapa etapów), Kodeks i pozostałe ekrany
nadal nie mają osobnych kontrolek; suwaki głośności w pauzie wymagają
dotyku — nie traktować tego zakresu jako pełnej dostępności gry.

- [ ] Android 8/9 i Android 15+: włączyć **TalkBack**, uruchomić aplikację.
  Eksploracja palcem i przesuwanie fokusu pokazują przyciski menu,
  bez pustych kafelków i niewidocznych elementów.
- [ ] W trybie poziomym sprawdzić ikonę Ustawienia i Kodeks:
  odczytywane są nazwy, nie puste przyciski. Przejść do Ustawień
  dwukrotnym dotknięciem, wrócić i ponownie otworzyć.
- [ ] W Ustawieniach przejść po wszystkich widocznych wierszach;
  TalkBack czyta nazwę opcji razem z bieżącą wartością. Zmienić język,
  dźwięk i powiadomienia, sprawdzić stan zaznaczenia. Wyłączony
  przycisk Play Games nie może się aktywować.
- [ ] Otworzyć Dostępność: sprawdzić wybór palety, rozmiar tekstu,
  stan znaczników i redukcji błysków. Przewinąć stronę; poza kadrem
  nie mogą pozostawać węzły, a nowe przyciski powinny dostać fokus.
- [ ] Otworzyć Podpowiedzi: włączyć/wyłączyć podpowiedzi, skontrolować
  ostrzegające potwierdzenia i wrócić do Ustawień.
- [ ] Uruchomić bitwę: przesuwanie fokusu idzie kolejno: Złoto,
  Doświadczenie, bazy, (fala), armia, cztery jednostki z ceną i stanem,
  akcje, awans, pauza, „Komunikaty bitwy”. Po aktywacji pauzy widać
  tylko przyciski pauzy (Wznów, Restart, Wyjdź, wibracje, tempo).
  Wznowić i sprawdzić, że HUD wraca z tym samym fokusem.
- [ ] Telefon 720p i Fold (okładka, trzy rzędy HUD): eksploracja HUD
  palcem — każdy przycisk ma własny prostokąt, bez nachodzenia, a
  strzałka ulepszenia nie jest osobnym elementem.
- [ ] Na jednostce otworzyć menu akcji TalkBack (gest, np. trzy palce
  albo „w dół, potem w prawo”) i wybrać **Ulepsz: …**: otwiera się panel
  ulepszenia z opisem, Ulepsz i Wstecz.
- [ ] Na Ostrzale: podwójne dotknięcie strzela w najbliższą grupę wrogów
  (bez celowania palcem); z menu akcji sprawdzić „czoło wroga” i „przed
  moją bazą”. Ostrzał nie spada na własnych żołnierzy; stan pokazuje
  „odnowienie: N s” (co 5 s).
- [ ] Endless do pierwszej oferty kart: TalkBack ogłasza okno „Wybierz
  kartę”, każda karta czyta nazwę, rzadkość i pełny opis; podwójne
  dotknięcie wybiera kartę.
- [ ] Awansować epokę: „Komunikaty bitwy” czytają „Nowa epoka: …”;
  kolejne komunikaty co najmniej co 3 s, a „Twoja baza w krytycznym
  stanie” i koniec bitwy od razu.
- [ ] Wywołać okno wznowienia zapisanej bitwy z menu: fokus ma
  obejmować Wznów/Porzuć, a nie zasłonięte kafelki menu.
- [ ] Powtórzyć na telefonie 720p oraz Foldzie po zmianie rozmiaru
  i orientacji, sprawdzając pozycje prostokątów eksploracji dotykiem.
- [ ] Sprawdzić **Switch Access**: skanowanie kontrolek i aktywację
  kliknięć menu, ustawień, pauzy oraz jednostek w bitwie (szkolenie
  jednostki przełącznikiem) bez zwykłych gestów.
- [ ] Porównać płynność bitwy (licznik FPS z Ustawień) z włączonym
  TalkBack i bez niego: z usługą drzewo przebudowuje się średnio
  najwyżej dwa razy na sekundę, bez widocznych zacięć.
- [ ] Wyłączyć wszystkie usługi dostępności i porównać wydajność
  oraz liczbę alokacji pętli gry z bazą; nie może powstawać drzewo
  wirtualnych kontrolek w kolejnych klatkach.

## Testy ręczne aktualizacji i odporności zapisu (154)

Przeprowadzić na urządzeniu z Androidem 8+ i na aktualnym emulatorze. Do aktualizacji
używać tego samego identyfikatora pakietu i certyfikatu podpisu; **nie odinstalowywać**
poprzedniego APK (odinstalowanie usuwa dane aplikacji).

- [ ] Zainstalować **poprzednią wydaną wersję**, rozegrać co najmniej dwa etapy,
  zdobyć medale, kupić badanie, zmienić ustawienia, ukończyć bitwę i zapisać powtórkę.
- [ ] Zanotować medale, badania, gwiazdki, serię misji, liczbę wpisów historii i
  stan przypiętych powtórek. Przerwać kolejną bitwę w połowie, wysłać aplikację w tło.
- [ ] Zaktualizować APK poleceniem `adb install -r nowa-wersja.apk`. Potwierdzić,
  że **profil i historia pozostały**, a ustawienia i odblokowania nie zostały wyzerowane.
- [ ] Zweryfikować okno wznowienia starej bitwy. Zgodny snapshot musi wznowić
  symulację; snapshot z niezgodnym formatem lub balansem wolno bezpiecznie odrzucić,
  ale menu musi pokazać **jedno ostrzeżenie**, a profil i historia muszą pozostać.
  Powtórzyć na zapisie sprzed dowódcy oraz sprzed stopni wież.
- [ ] Sprawdzić powtórki ze starszych wersji 5 i 6 oraz wersji 7.
  Nieodtwarzalne wersje mają pozostawić metadane na liście i wyraźnie sygnalizować
  brak zgodności; zgodna powtórka 7 powinna się uruchamiać.
- [ ] Na kopii danych testowych uszkodzić osobno `profile.json`, `history.json`,
  `battle.snap` i jeden plik `replays/*.cwr` (obcięcie oraz losowe bajty).
  Po restarcie gra nie może się wysypać; poprawne lokalne pliki `.bak` lub `.tmp`
  muszą umożliwić odzyskanie danych. Uszkodzona powtórka nie może być uruchomiona.
- [ ] W debug buildzie zasymulować zabicie procesu podczas zapisu (pozostawiony
  pełny lub ucięty `.tmp`). Po uruchomieniu ma zostać wybrany ostatni **poprawny**
  zapis, nigdy częściowy JSON lub snapshot.
- [ ] Zasymulować brak miejsca i brak zapisu do katalogu danych (na urządzeniu
  testowym lub przez wstrzyknięcie błędu I/O): gra nie kończy się awarią,
  w menu pojawia się **jedno powiadomienie**, a poprzedni zapis pozostaje poprawny.
- [ ] Po ukończeniu bitwy sprawdzić, że usunięty `battle.snap` nie wraca z `.bak`
  i że wyczyszczenie historii nie odtwarza starych wpisów.
- [ ] Wykonać próbę na urządzeniu 2–3 GB RAM oraz po obrocie/złożeniu Folda.
  Sprawdzić, że I/O pozostaje poza pętlą gry.

Pliki `.bak` są lokalnymi kopiami awaryjnymi; systemowy backup Androida nadal
obejmuje tylko `profile.json` i `history.json` według reguł poniżej.

## Numer wersji

- `releaseVersionCode` w `gradle.properties` to dodatni, rosnący numer publikacji w Google Play.
  **Zwiększ go przed każdym nowym wydaniem**, także gdy nie zmienia się `versionName`.
- `versionName` jest pobierane z tagu SemVer wskazującego **bieżący commit**
  (np. `v1.2.3` daje `1.2.3`). Bez tagu (lub bez gita) stosowane jest
  `releaseVersionName` z `gradle.properties` (`0.1.0-dev`).
- Wersjonowanie korzysta z `providers.exec`, zgodnego z cache konfiguracji Gradle.
  Przy budowaniu z tagu pobierz pełną historię i tagi (`git fetch --tags`);
  job `release` w CI robi checkout z `fetch-depth: 0`.

## Ankieta testera (tylko wersje testowe)

Jednorazowa ankieta w menu (ocena 1–5 i pytanie „Co najbardziej przeszkadza?”, zawsze z „Pomiń”)
pojawia się po samouczku i po 5 bitwach albo w 3. dniu gry. Odpowiedź zostaje w
`files/playtest.json` i trafia tylko do „Raportu dla testera”; nic nie jest wysyłane, więc
Data safety bez zmian. Włącza ją pole `BuildConfig.TESTER_SURVEY`:

- **debug:** zawsze włączona (wyłącza ją tylko `-PtesterSurvey=false`);
- **release bez flagi:** włączona dla wersji przedpremierowej — `versionName` zaczyna się od
  `0.` (np. tag `v0.9.0` dla testów zamkniętych) albo ma sufiks (`1.0.0-rc1`, `0.1.0-dev`);
  wyłączona dla wersji produkcyjnej `1.0.0` i nowszych bez sufiksu;
- **wymuszenie:** `./gradlew bundleRelease -PtesterSurvey=false` (produkcja bez ankiety mimo
  numeru 0.x) albo `-PtesterSurvey=true` (np. test zamknięty po premierze). Inne wartości
  kończą konfigurację błędem.

Przed wysłaniem produkcyjnego AAB sprawdź w `app/build/generated/source/buildConfig/release/`
wartość `TESTER_SURVEY = false`.

## Podpisywanie

Jeden schemat dla CI i lokalnie (szczegóły i generowanie klucza: [`docs/CI.md`](CI.md)):

| Zmienna środowiskowa | Wartość |
|---|---|
| `RELEASE_KEYSTORE_FILE` | ścieżka do pliku `.jks` (klucz upload) |
| `RELEASE_STORE_PASSWORD` | hasło keystore |
| `RELEASE_KEY_ALIAS` | alias klucza |
| `RELEASE_KEY_PASSWORD` | hasło klucza |

- **Wszystkie cztery ustawione i poprawne:** release jest podpisywany kluczem upload.
  Wskazany plik JKS musi istnieć i nie może być standardowym plikiem debug.keystore;
  alias androiddebugkey jest odrzucany.
- **Żadna nieustawiona:** polecenia assembleRelease, bundleRelease i releaseChecklist
  **kończą się błędem**. Nie ma automatycznego przełączenia na podpis debug.
  Nie można obejść tego warunku przez -PskipCheck.
- **Część ustawiona:** Gradle kończy konfigurację błędem.
- **Wyjątek CI:** -PciTestDebugSigning=true jest dostępne wyłącznie dla jawnie
  oznaczonego smoke-testu GitHub Actions (zdarzenie pull_request albo
  workflow_dispatch, GITHUB_ACTIONS=true i CW_RELEASE_SMOKE=true).
  Taki artefakt jest podpisany debugowo i **NIE NADAJE SIĘ DO GOOGLE PLAY**.
  Lokalnie ani w produkcyjnym workflow z taga nie można używać tej flagi.

W CI job `release` (push taga `v*`) odtwarza keystore z sekretów GitHub i przerywa pracę,
jeśli któregoś brakuje. Skonfiguruj **Play App Signing** w Play Console i wgraj
`app/build/outputs/bundle/release/app-release.aab`.

## AAB, R8 i Baseline Profile

- `bundleRelease` jest dostępne w `com.android.application` bez dodatkowych wtyczek.
  Brak kodu natywnego, więc podziały ABI nie mają zastosowania.
- Release: `isMinifyEnabled` + `isShrinkResources`; AGP 8.x domyślnie używa R8 full mode.
  Profil i zrzut bitwy mają ręczny JSON / format binarny. Reguły
  `proguard-rules.pro` zachowują komponenty manifestu oraz enumy o trwałych
  ordinalach i nazwach zapisywanych do profilu (zlecenie 128).
- `app/src/main/baseline-prof.txt` zawiera ręcznie prześledzone ścieżki
  startu (`CenturyWarApp`, `MainActivity`, `Profile`), menu
  (`GameView`, `Game`, `MenuScreen`, `MenuBg`), wyboru trybu
  i bitwy (`BattleScreen`, `BattleRenderer`, `Battle`, `Ai`, `Rng`).
  Lista została przygotowana na podstawie bieżących wywołań w kodzie,
  **nie** z pomiaru Macrobenchmark na urządzeniu; realny zysk w czasie startu
  wymaga odrębnego pomiaru na telefonie. AGP kompiluje go do `assets/dexopt/baseline.prof` (APK)
  i `BUNDLE-METADATA` (AAB) oraz przepisuje deskryptory po R8. Instalacje z Google Play
  (Android 9+) korzystają z niego przy instalacji.
- Nie dodano `androidx.profileinstaller` (gra nie ma zależności bibliotecznych). Bez niego
  profil nie jest instalowany przy sideloadzie ani na Androidzie 8/8.1 — korzyść jest
  marginalna dla tak małej aplikacji, a zależność łamałaby zasadę „bez bibliotek”.

## Języki i backup

**Języki.** `android:localeConfig` (`res/xml/locales_config.xml`) deklaruje 10 języków gry:
`pl, en, de, es, fr, it, pt, uk, nl, tr` — dokładnie `L.CODES` z `engine/L.kt`
(sprawdza to `ReleaseConfigTest`). Dodając język, dopisz go w obu miejscach i uzupełnij
wszystkie klucze tabeli (`LTest` wymaga kompletu 10 języków i zgodnych placeholderów).
Dzięki `localeConfig` Android 13+ pokazuje grę w ustawieniach języka aplikacji.

**Backup.** `android:allowBackup="true"`; kopia Androida obejmuje **wyłącznie**
`filesDir/profile.json` (postęp, ustawienia, sekret rankingu) i `filesDir/history.json`
(historia bitew). Reguły `include` wyłączają całą resztę: kolejkę wyników
(`score-queue.json*`), `crash/`, `battle.snap` (przerwana bitwa — przejściowa),
`replays/` (rozmiar), `profile-backups/`, pliki `.tmp`/`.corrupt` i SharedPreferences.

- Android 12+ (`android:dataExtractionRules` → `res/xml/data_extraction_rules.xml`):
  `<cloud-backup disableIfNoEncryptionCapabilities="true">` — kopia w chmurze powstaje
  tylko, gdy urządzenie potrafi ją zaszyfrować end-to-end (ustawiona blokada ekranu);
  `<device-transfer>` (przeniesienie kablem/Wi-Fi na nowy telefon) obejmuje te same dwa
  pliki bez wymogu szyfrowania.
- Android 6–11 (`android:fullBackupContent` → `res/xml/backup_rules.xml`): oba pliki mają
  `requireFlags="clientSideEncryption"` (respektowane od Androida 9) — bez szyfrowania po
  stronie klienta nie trafiają do kopii.
- Wymóg szyfrowania wynika z tego, że `profile.json` zawiera sekret rankingu
  (`docs/SECURITY.md`); przywrócenie go na nowym telefonie tego samego konta Google jest
  zamierzone. `adb backup` dla aplikacji niedebugowalnej z targetSdk 31+ i tak pomija dane.
- Konfigurację pilnuje `ReleaseConfigTest` (manifest, reguły obu formatów). Dodając nowy
  plik z postępem, dopisz go w obu XML-ach i w tym opisie.

## Pomiar wydania i R8 (zlecenie 66)

- `.github/workflows/release-check.yml` działa dla PR do `main` i `claude/**`
  oraz uruchomienia ręcznego. Buduje **APK i AAB z R8 i shrinkResources**
  z jawną flagą `-PciTestDebugSigning=true` i dodatkowymi ograniczeniami środowiska CI.
  Tylko ten smoke-test może pominąć pola właściciela przez
  `-PskipCheck=ownerInput`. Artefakt debugowo podpisany **nie nadaje się do sklepu**.
- Przy PR workflow kompiluje również commit wspólny z gałęzią docelową
  (`git merge-base`) na tym samym runnerze i SDK. Skrypt
  `scripts/release_size_report.py` porównuje wielkość plików APK/AAB
  **przed / po**, podaje największe spakowane składniki i grupy:
  DEX, zasoby, profil ART, assets i metadane. Są to rozmiary paczek,
  a nie prognoza transferu przy instalacji z Google Play.
- Raport `build/release-metrics/rozmiary.md` jest dostępny w Job Summary
  i artefakcie `release-walidacja`. Bez PR (uruchomienie ręczne)
  wartości **przed** są oznaczone jako `n/d`; nie są zgadywane.
- `mapping.txt`, `usage.txt` i `configuration.txt` są zachowywane
  jako artefakt `release-r8-mapping`; produkcyjny workflow z taga
  `v*` również archiwizuje `mapping.txt` (`century-war-release-mapping`).
  Skrypt blokuje job, gdy w mapowaniu brakuje klas krytycznych startu
  i bitwy. Zawartość `usage.txt` jest raportowana do ręcznej analizy.
- Przegląd produkcyjnych źródeł `:app`/`:sim`: nie stwierdzono
  refleksyjnego ładowania klas/metod/pól ani serializacji przez refleksję.
  Po kontroli zlecenia 128 dodano wąskie reguły keep: komponenty manifestu,
  enumy z ordinalami w snapshotach/powtórkach oraz `AchievementMetric`
  zapisywany po nazwie w JSON. Nie dodano szerokiego `-keep com.centurywar.**`.
- `shrinkResources = true` jest włączone. Zasoby statyczne to głównie
  ikona, konfiguracja, manifest, kopia zapasowa i tekst licencji Apache
  odczytywany przez `MainActivity`; brak zasobów z pewnością zbędnych
  do bezpiecznego ręcznego usunięcia. Rzeczywiste składniki i udział
  zasobów pokazuje raport CI.
- Release APK jest także instalowany i uruchamiany na emulatorze w tym
  workflow. Test Robolectric zwykle korzysta z klas JVM bez obfuskacji,
  dlatego **nie jest dowodem zgodności po R8**. Niezależny smoke-test
  release sprawdza manifest, start Activity, istnienie procesu i brak
  `AndroidRuntime FATAL EXCEPTION`; nie zastępuje testów wszystkich
  scenariuszy na urządzeniach.

Test skryptu bez Android SDK:

```bash
python3 -m unittest discover -s tests -p 'test_release_size_report.py' -v
```

## Kontrola rzeczywistego DEX i rozruchu (zlecenie 128)

**Zakres.** Konfiguracja release używa `minifyEnabled=true` (R8) i
`shrinkResources=true`; nie dodajemy bibliotek ani nie modyfikujemy
`versionCode` lub danych wydawcy. `ReplayShareProvider`, `MainActivity`,
`CenturyWarApp` i odbiornik przypomnienia są komponentami manifestu,
dlatego ich nazwy muszą być zachowane. Play Games jest wywoływane bezpośrednio
przez SDK (jego reguły pochodzą z biblioteki); w projekcie nie ma `BackupAgent`.
`AchievementMetric.name` jest utrwalane w JSON profilu, a ordinale
`Role`, `Mode`, `Doctrine`, `Modifier`, `Weather`,
`AiPersonality`, `DailyEvent`, `EndlessCard`,
`EndlessWaveTheme`, `EraArt` i `Replay.Kind` są używane
w zapisach bitwy, definicjach lub powtórkach. Zachowanie klas enumów
jest celowe i może nieco zwiększyć APK względem agresywnego unboxingu R8.

**Weryfikacja po minifikacji.** `scripts/verify_release_dex.py` uruchamia
`dexdump -f` na *każdym* `classes*.dex` wyjętym z gotowego APK.
Uwzględnia nazwy po obfuskacji z `mapping.txt`; wymaga klas startu,
bitwy, profilu i Play Games, obecności 12 enumów z nadklasą
`java.lang.Enum` i niezmienionych nazw komponentów Androida.
W przeciwieństwie do samego `mapping.txt`, wykrywa brak klasy
w wynikowym APK. Nie sprawdza zachowania rozgrywki na urządzeniu.
Zamknięty zestaw 7 testów bez Android SDK: `tests/test_verify_release_dex.py`.

```bash
python3 -m unittest discover -s tests -p 'test_verify_release_dex.py' -v
python3 scripts/verify_release_dex.py \
  --apk app/build/outputs/apk/release/app-release.apk \
  --mapping app/build/outputs/mapping/release/mapping.txt \
  --dexdump "$ANDROID_HOME/build-tools/36.0.0/dexdump"
```

**Rozmiar APK/AAB.** Workflow `release-check.yml` buduje oba artefakty
z testowym podpisem oraz porównuje je z commitem bazowym tego samego PR.
Wynik to `build/release-metrics/rozmiary.md` w artefakcie
`release-walidacja` i w GitHub Actions Job Summary. Raport podaje
rzeczywiste wielkości ZIP osobno dla APK i AAB, w tym skompresowany
DEX (`classes*.dex`), zasoby (`res/*`, `resources.arsc`,
`resources.pb`), manifest, profil ART i pozostałe elementy.
Bez wykonanego buildu **nie podajemy hipotetycznych wartości**.
`shrinkResources` był już włączony; bez danych z narzędzia
nie usuwamy ręcznie zasobów, których użycie może być pośrednie.
Do dalszej diagnostyki trafiają `mapping.txt`, `usage.txt`,
`configuration.txt`, `dex-audit.txt`, `r8-warnings.txt`
i pełny `release.log`.

**Start na emulatorze i telefonie.** CI buduje również APK debug,
wykonuje *czystą instalację* i start przez `adb shell am start -W`.
W debug log `CWStartup` podaje czas od `Application.onCreate`
do pierwszej klatki menu, wraz z fazami
`Application`, profil, snapshot, wykrywanie jakości, tłumaczenia,
`Game`, Play Games, muzyka i `SurfaceView`.
Następnie CI usuwa aplikację, instaluje APK release i zbiera
`am start -W` oraz log błędów AndroidRuntime. Wyniki:
`start-debug.txt`, `startup-debug.log`, `start.txt` w
`release-walidacja` (i podsumowaniu workflow).

Na fizycznym urządzeniu zmierz release po instalacji **testowej**
(nie produkcyjny upload key w CI) i każdorazowym `force-stop`:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am force-stop com.centurywar.game
adb shell am start -W -n com.centurywar.game/com.centurywar.MainActivity
# Dla debug: ten sam pomiar + fazy StartupTimings w logcat
adb logcat -d -s CWStartup:I '*:S'
```

Zapisz osobno `TotalTime` i `WaitTime` z `am start -W`
(dla obu wariantów) oraz `CWStartup` z debug. To **inne miary**:
`am start -W` mierzy zgłoszenie uruchomienia Activity
(może zakończyć się już na ekranie startowym), a `CWStartup`
mierzy *pierwszą klatkę menu*. Release nie loguje
`StartupTimings` (warunek `BuildConfig.DEBUG`), więc do uczciwego
porównania wariantów służy ten sam pomiar `am start -W`,
a fazy debug są diagnozą opóźnień. Dla wiarygodnych wyników
powtórz co najmniej 5 zimnych startów na tym samym telefonie,
bez aktywnego procesu aplikacji, i podaj medianę.
Zwykły emulator jest kontrolą regresji, nie benchmarkiem taniego telefonu.

**Warunki publikacji.** Testowe ID i podpis CI nie nadają się do wydania;
normalny release wymaga prawdziwego klucza upload i kompletnej
`releaseChecklist`. Po R8 nadal trzeba na telefonie sprawdzić
ścieżki zewnętrznych providerów, osiągnięcia i rankingi Play Games,
import/eksport powtórek i wznawianie zapisów.

## Audyt trwałych danych po R8 (zlecenie 148)

Smoke-test `.github/workflows/release-check.yml` buduje `:app:assembleRelease`
i `:app:bundleRelease` z **syntetycznymi** identyfikatorami Play Games
(`scripts/ci_synthetic_play_games_ids.py`) i podpisem debugowym dozwolonym
**wyłącznie** w CI. Te paczki nie są przeznaczone do Play Console.

`app/proguard-rules.pro` chroni nazwy klas i pól odpowiedzialnych za:
JSON profilu (`Profile`, `ScoreEntry`), zrzut bitwy (`BattleSnapshot`,
`RestoredBattle`, `SnapshotStore`, `BattleConfig`, `Bonuses`) oraz
powtórki (`Replay`, `Replay.Command`, `Replay.Log`, `ReplayRecord`
i jego modele). Dodano też `CampaignDifficulty` do listy enumów o
trwałych ordinalach, a `MissionType` (zapisywany po **nazwie** w `missionState`
profilu, odczyt przez `valueOf`) do listy enumów chronionych w całości. Kontrola
obejmuje także pola rozliczenia bitwy (A156-10): `Profile.lastSettledBattle`,
`RestoredBattle.battleId`, `SnapshotStore.owner`. Osobne reguły Play Games dostarcza SDK.

**Uwaga o kompatybilności:** zapis JSON używa jawnych kluczy tekstowych,
a snapshoty i powtórki zapisują jawne bajty, nie serializują pól przez
refleksję. Nazwy JVM nie są częścią formatu plików. Zachowanie nazw pól
jest dodatkową barierą, a nie zamiennikiem testów `ProfileTest`,
`BattleSnapshotTest`, `ReplayTest` i testu na rzeczywistym urządzeniu.
Nie zmieniaj kolejności enumów ani wersji formatów bez osobnej migracji.

Po kompilacji release skrypt `scripts/verify_release_mapping.py`
weryfikuje `mapping.txt`, `usage.txt` i aktywne `configuration.txt`: wymagane klasy istnieją
pod niezmienionymi nazwami, krytyczne pola nie są usunięte ani
przemianowane, a wybrane stałe enumów zachowują nazwy. Niezależny
`verify_release_dex.py` potwierdza obecność klas w rzeczywistym DEX.
Nie wystarczy sam test debug.

```bash
python3 -m unittest discover -s tests -p 'test_verify_release_mapping.py' -v
python3 scripts/verify_release_mapping.py \
  --mapping app/build/outputs/mapping/release/mapping.txt \
  --usage app/build/outputs/mapping/release/usage.txt \
  --configuration app/build/outputs/mapping/release/configuration.txt
```

Wynik trafia do `build/release-metrics/mapping-audit.txt` w artefakcie
`release-walidacja`. Błąd mapowania przerywa workflow przed testem
na emulatorze.

### Zachowanie po R8 bez urządzenia (`tools/r8-sim-smoke`)

Testy JUnit działają na klasach debug. Żeby sprawdzić **kod po minifikacji**,
`tools/r8-sim-smoke/run.sh` uruchamia R8 (z Android SDK, tryb `--classfile`) na module
`sim` z tymi samymi regułami (`proguard-android-optimize` + `app/proguard-rules.pro`)
i jedynym punktem wejścia `R8Smoke`. Najpierw wersja niezminifikowana zapisuje bajty
zrzutów bitwy w formatach **v16, v17 i v18** oraz rekordu powtórki, potem wersja po R8:

- odtwarza wszystkie 10 złotych powtórek (`GoldenFixtures.render`: kodek komend,
  weryfikator serwera, odtwarzacz, SHA-256 zrzutu końcowego) zgodnie z wzorcami,
- wczytuje zrzuty v16–v18 z bajtów wersji niezminifikowanej (`battleId` 0 dla v16,
  zachowany dla v17/v18), zapisuje v18 identycznie i dalej symuluje tak samo jak oryginał,
- odczytuje `ReplayRecord` (konfiguracja, komendy, metadane) i zapisuje go identycznie.

```bash
./gradlew :sim:testClasses   # oraz build release jak w smoke CI (sekcja wyżej)
tools/r8-sim-smoke/run.sh    # wynik: „WYNIK: OK” dwa razy
```

Wynik 3 listopada 2026 r.: **60/60 kontroli OK**, R8 przemianował 61 klas `com.centurywar`.
Profilu (`Profile`, Android `org.json`) nie da się tak uruchomić bez urządzenia — sprawdzono
kod: zapis i odczyt profilu, misji, historii bitew i kopii zapasowej używa wyłącznie jawnych
kluczy JSON (`put`/`opt*`), zrzuty i powtórki — jawnych bajtów `DataOutputStream`/varint;
w `app` i `sim` nie ma `Class.forName`, `getDeclared*`, `Serializable` ani serializacji
refleksyjnej. Enumy zapisywane po nazwie (`AchievementMetric`, `MissionType`, `Modifier`
w misjach) mają `-keep enum`. Muzyka jest wczytywana po nazwie **pliku** z `assets/music`
(nie dotyczy R8; `noCompress += "ogg"` dla `openFd`), a dostawca węzłów TalkBack (164) to
anonimowa podklasa `AccessibilityNodeProvider` — R8 zachowuje nadpisane metody frameworka.

### Rozmiar APK/AAB i czas uruchomienia

**Nie wpisuj rozmiarów szacunkowych.** Wartości dla bieżącego commita
należy odczytać po udanym przebiegu `release-check.yml` z
`build/release-metrics/rozmiary.md` (APK i AAB oddzielnie).
Ostatni zweryfikowany build R8 (próba wersji release, **3 listopada 2026 r.**,
gałąź `integ/tura10` + poprawki nocy 6, lokalnie jak smoke CI, podpis debug CI,
21 nagranych utworów w `assets/music`):

| Pomiar | Wynik | Źródło |
| --- | --- | --- |
| APK release / R8 / podpis CI | **20 998 247 B** (20 506,1 KiB, ok. 20,0 MiB) | `scripts/release_size_report.py` |
| AAB release / R8 / podpis CI | **21 614 758 B** (21 108,2 KiB, ok. 20,6 MiB) | `scripts/release_size_report.py` |
| — w tym muzyka (21 × `.ogg`, bez kompresji w APK) | 19 927 585 B (ok. 19,0 MiB, 95 % APK) | `unzip -v` |
| — w tym kod DEX (1 plik, 695 klas) | 1 702 560 B surowo, 827,8 KiB w ZIP | `unzip -v`, `verify_release_dex.py` |
| Start debug / release (median z 5 prób) | n/d do pomiaru na telefonie | `adb shell am start -W` |

`n/d` oznacza **brak wykonanego pomiaru**, nie 0 B lub 0 ms. Rozmiar pobrania z Google Play
(podział AAB na APK dla urządzenia) nie był mierzony; przy tej paczce prawie całość to muzyka
OGG, której Play nie skompresuje już bardziej.
Raport `rozmiary.md` zawiera też zmianę względem commita bazowego PR,
największe składniki ZIP i liczbę zmapowanych klas.
Rozmiar paczki nie odpowiada rozmiarowi instalacji z Google Play.

### Test ręczny: ten sam telefon, debug i release

Uruchom test na Androidzie 8+ (minimum API 26), najlepiej również na
niedrogim telefonie z 2–3 GB RAM i na dużym ekranie. Używaj tej samej
wersji danych gry w debug i release. Zapisz model telefonu, Androida,
wersję aplikacji, datę UTC i wynik każdego punktu (OK / BŁĄD).

- [ ] **Instalacja:** zainstaluj APK release z CI lokalnie przez `adb install`.
  Sprawdź uruchomienie bez crasha. Nie próbuj aktualizować APK o innym
  certyfikacie bez wcześniejszej deinstalacji; osobno przetestuj aktualizację
  podpisaną *tym samym* kluczem i zachowanie danych.
- [ ] **Zimny start:** wymuś zatrzymanie procesu i uruchom aplikację
  przez `adb shell am start -W -n com.centurywar.game/com.centurywar.MainActivity`.
  Powtórz 5 razy dla debug i release na tym samym urządzeniu, zanotuj
  medianę `TotalTime` / `WaitTime`. `CWStartup` w debug mierzy osobno
  pierwszą klatkę menu, więc nie porównuj tych różnych metryk.
- [ ] **Bitwa:** rozegraj i zakończ etap kampanii, potyczkę oraz
  wyzwanie dnia; sprawdź wyniki, medale, statystyki i ekran po walce.
- [ ] **Zapis i wznowienie bitwy:** rozpocznij bitwę, zmień stan armii,
  przejdź do tła, ubij proces bez czyszczenia danych, uruchom ponownie
  i wybierz wznowienie. Sprawdź epokę, HP, złoto, jednostki i ostateczny
  wynik. Powtórz przy zmianie orientacji / złożeniu Folda.
- [ ] **Powtórka:** zapisz zakończoną bitwę, odtwórz ją do końca,
  sprawdź wynik; przetestuj eksport, import i odrzucenie uszkodzonego
  pliku powtórki bez crasha.
- [ ] **Język:** zmień język w ustawieniach (w tym PL, EN i drugi
  wybrany język), przejdź przez menu i HUD oraz uruchom ponownie.
  Teksty nie mogą znikać ani mieszać języków.
- [ ] **Kopia profilu:** w Ustawieniach wyeksportuj plik `.cwprofile`,
  zmień postęp lub ustawienia i zaimportuj kopię. Sprawdź zgodę
  na zastąpienie profilu oraz możliwość przywrócenia kopii sprzed importu.
  Eksport nie powinien zawierać `playerSecret`; nie zawiera też
  `battle.snap`, historii i powtórek. Osobno sprawdź systemowy backup
  Androida, jeśli scenariusz testu dotyczy przenosin urządzenia.
- [ ] **Powiadomienie:** włącz przypomnienie o wyzwaniu dnia, udziel
  uprawnienia do powiadomień na Androidzie 13+, ustaw godzinę,
  zminimalizuj grę i sprawdź nadejście przypomnienia oraz reakcję
  po kliknięciu. Przetestuj też brak zgody na powiadomienia.
- [ ] **Offline / online:** wyłącz sieć i Play Games, potwierdź
  działanie lokalnego profilu i bitew, następnie przywróć połączenie.
  Przy rzeczywistych ID i podpisie z Play Console sprawdź logowanie,
  rankingi, osiągnięcia oraz synchronizację chmurową.

**Warunek odbioru:** brak crasha i utraty danych, poprawne wznowienie,
identyczny wynik powtórki oraz porównywalny (w granicach zmienności
urządzenia) start w debug i release. Każdą różnicę zgłoś z logcat,
wersją Androida i krokami reprodukcji.

## Raport przedpremierowy i pętla gry (R11-04)

**Po co.** Po każdym wgraniu AAB na tor testowy Google uruchamia grę na kilku swoich telefonach
(raport przedpremierowy: awarie, ANR, wydajność, dostępność, zrzuty). Gra rysowana na własnym
płótnie jest dla robota Google jednym obrazkiem: bez pętli gry klika losowo i do bitwy prawie
nie dociera. Dlatego gra ma **pętlę gry** (standard Firebase Test Lab „Game Loop”, którego
używa też Play): 4 scenariusze, które grają same i same się kończą.

**Decyzja właściciela (4.11.2026):** pętla jest włączona w raporcie przedpremierowym dla każdego
wydania testowego.

### Co ma kliknąć właściciel w Play Console

Nic nie trzeba włączać osobno — Play sam wykrywa pętlę w AAB. Wystarczy:

1. **Testowanie i publikacja → Testowanie → Testy wewnętrzne** (albo **Testy zamknięte**) →
   **Utwórz nową wersję** → wgraj podpisany `app-release.aab` → **Zapisz** → **Sprawdź wersję** →
   **Rozpocznij wdrażanie**. (Po angielsku: *Test and release → Testing → Internal testing →
   Create new release*.) Raport startuje sam po wgraniu paczki.
2. Po ok. godzinie (czasem kilku) otwórz **Testowanie i publikacja → Testowanie → Raport
   przedpremierowy** (*Pre-launch report*) → **Przegląd**. Karty **Stabilność**, **Wydajność**,
   **Dostępność**; w **Wyświetl szczegóły** są zrzuty i film z telefonów. Bitwa AI kontra AI
   na zrzutach oznacza, że pętla zadziałała.
3. **Raport przedpremierowy → Ustawienia** (*Settings*): niczego nie zmieniaj. Nie podawaj
   danych logowania (gra nie ma konta), nie wgrywaj skryptu Robo. Raport jest domyślnie włączony;
   gdyby ktoś go kiedyś wyłączył, w tych ustawieniach włącz go z powrotem.
4. Jeśli raport nie pojawi się w ciągu 2 dni, wgraj tę samą wersję ponownie z nowym
   `versionCode` (Google uruchamia raport, gdy ma wolne telefony).

Konto Firebase **nie jest potrzebne**.

### Scenariusze (manifest: `com.google.test.loops` = 4)

| Nr | Etykieta | Co robi | Czas |
|---:|---|---|---|
| 1 | `player_experience` | zimny start, 20 s menu, wejście i wyjście: Kampania, Badania, Kodeks, Ustawienia | ok. 50 s |
| 2 | `performance` | etap 8 kampanii, stałe ziarno, AI po obu stronach, tempo ×3; 3 min albo do końca bitwy, potem wyniki | do 3 min 10 s |
| 3 | `performance` | Endless od 4. epoki (jak fala 20–30), AI gracza, ×3, 3 min (bossy co 10 fal) | do 3 min 10 s |
| 4 | `player_experience` | bitwa ×1 30 s → przerwanie (zapis zrzutu jak przy wyjściu do tła) → wczytanie zrzutu → 30 s | ok. 1 min |

Każdy scenariusz kończy się sam (`finish()`); twardy limit 4 minuty (wynik `timeout`).

### Jak to działa i dlaczego nie psuje gry gracza

- Osobna `TestLoopActivity` w osobnym procesie `:testloop` (`excludeFromRecents`), z filtrem
  `com.google.intent.action.TEST_LOOP` + `application/javascript`. `MainActivity` i zwykły start
  są bez zmian (test `ordinaryStartNeverEntersTestLoop`).
- Profil pętli (z ukończonym samouczkiem, bez dymków i zapowiedzi epok) i zrzut bitwy leżą
  **tylko** w `cacheDir/testloop/`, czyszczonym na starcie pętli. `profile.json`, `history.json`,
  `battle.snap`, powtórki gracza: nietknięte (test bajt w bajt). Bez Play Games, rankingu online
  (`Game.testLoop`), przypomnień i dziennika awarii gracza (awarie i tak widzi raport Google).
  Jedyna wspólna rzecz: pamięć podręczna syntezowanych dźwięków w `cache/sfx` (te same pliki).
- Filtr jest eksportowany (każda aplikacja może uruchomić pętlę), dlatego plik wyników trafia
  tylko pod adres z intencji i nigdy do własnych plików gry (`content://` naszych dostawców
  i `file://` w katalogach danych gry są odrzucane). Brak adresu = wynik tylko w logcat.
- Bez nowych uprawnień i bibliotek; gra niczego nie wysyła (Data safety bez zmian).
- R8: `-keep class com.centurywar.testloop.TestLoopActivity { public <init>(); }` w
  `app/proguard-rules.pro`; reszta pętli jest wywoływana bezpośrednio z tej klasy.
  Próba 4.11.2026 (`assembleRelease` jak smoke CI, syntetyczne ID, podpis debug tylko do próby):
  build OK bez ostrzeżeń R8, `TestLoopActivity` z niezmienioną nazwą w DEX, w manifeście APK
  filtr `TEST_LOOP` + `application/javascript`, `com.google.test.loops` = 4 i etykiety;
  `verify_release_mapping.py` i `verify_release_dex.py` zaliczone. Uruchomienia na telefonie po
  R8 (adb) jeszcze nie było — pierwszy raport przedpremierowy to pokaże.

### Wynik (JSON, wersja pola `v` = 1)

Plik pod adresem z intencji i linia w `adb logcat -s CWTestLoop`. Przykład (liczby poglądowe,
nie pomiar):

```json
{"v":1,"scenario":2,"result":"ok","versionName":"0.1.0-dev","versionCode":1,"protocol":"0.8.6",
 "device":{"model":"Google Pixel 6a","sdk":35},"quality":2,"qualityMin":2,"reductionMax":1,
 "frames":{"count":10750,"p50":16.8,"p95":19.5,"max":61.3},"discardedSteps":0,
 "pssStartKb":151000,"pssEndKb":168000,"battleTimeS":540.0,"winner":-1,"elapsedS":185.1}
```

`result`: `ok` / `timeout` / `error` (z polem `reason`). `frames` to odstępy między klatkami
w ms (p50, p95, max) w fazie pomiaru; scenariusz 1 ma też `startupMs` (od startu procesu do
pierwszej klatki menu) i `screens` (p50/p95 na każdym ekranie); 2 i 3: `battleTimeS`, `winner`
(−1 = bitwa trwa), `wave` (Endless), `discardedSteps`; 4: `snapshotSaved`, `snapshotLoaded`,
`snapshotLoadMs`, `resumedAtS`. `quality`/`qualityMin` to poziom grafiki (1 niski … 3 wysoki),
`reductionMax` — ile stopni obniżył regulator. Progi do porównania: `docs/PERFORMANCE.md`.
Raport Play nie pokazuje tego pliku (pokazuje własne wykresy klatek, CPU i pamięci); plik
czyta się w Test Lab albo lokalnie.

### Uruchomienie lokalne i w Test Lab

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -a com.google.intent.action.TEST_LOOP \
  -n com.centurywar.game/com.centurywar.testloop.TestLoopActivity \
  -t application/javascript --ei scenario 2
adb logcat -s CWTestLoop    # linia z JSON po zakończeniu scenariusza
```

Firebase Test Lab (tylko jeśli właściciel kiedyś założy projekt Firebase; **niewymagane**):

```bash
gcloud firebase test android run --type=game-loop --app=app-release.apk \
  --scenario-numbers=1,2,3,4 --device model=<model>,version=<api>
```

**Źródła (sprawdzone 4.11.2026):**
[Pomoc Play: raport przedpremierowy i pętle gry](https://support.google.com/googleplay/android-developer/answer/9842757)
— „gra lub aplikacja OpenGL potrzebuje pętli gry”, robot sam ją wykrywa i uruchamia, bez konta
Firebase; [Pomoc Play: korzystanie z raportów przedpremierowych](https://support.google.com/googleplay/android-developer/answer/7002270)
— ścieżka *Test and release → Testing → Pre-launch report*, raporty automatycznie po wgraniu
paczki, wyniki zwykle w ciągu godziny;
[Firebase Test Lab: Game Loop](https://firebase.google.com/docs/test-lab/android/game-loop)
— filtr intencji, `com.google.test.loops`, etykiety, `scenario`, plik wyników z `getData()`,
`finish()` po każdej pętli, `--scenario-numbers`.

## Testy zamknięte w Play Console

Poniższa instrukcja dotyczy **prawdziwie podpisanego AAB** i realnej
konfiguracji Play Games. Wygenerowane w CI syntetyczne ID i podpis
debug **nie mogą być wykorzystane do dystrybucji w Google Play**.

1. Wejdź na [Google Play Console](https://play.google.com/console),
   wybierz aplikację **Century War**. Uzupełnij wymagane informacje:
   stronę sklepu, politykę prywatności, Data safety, grupę wiekową,
   deklaracje i kraje dostępności. Zweryfikuj numer wersji oraz Play App
   Signing (patrz wcześniejsze sekcje).
2. Otwórz **Test and release → Testing → Closed testing**
   (w polskim interfejsie: **Testowanie i publikacja → Testowanie →
   Testy zamknięte**). Wybierz istniejącą ścieżkę testową lub utwórz nową.
3. Na karcie **Testers** wybierz **Email lists → Create email list**.
   Nadaj listę, wpisz adresy kont Google testerów (możesz też użyć
   Google Groups), zapisz i przypisz ją do ścieżki. Ustaw adres
   e-mail albo URL do zgłaszania opinii. Skopiuj link zapisu
   **opt-in**. Samo dodanie adresu do listy **nie zapisuje testera**.
4. Otwórz kartę **Releases → Create new release**. Wgraj
   `app/build/outputs/bundle/release/app-release.aab` podpisany
   prawdziwym kluczem upload. Uzupełnij notatki wydania, przejrzyj
   ostrzeżenia i wybierz **Review release / Start rollout to closed testing**.
   Po publikacji i ewentualnej weryfikacji link może aktywować się z
   opóźnieniem, więc nie zakładaj natychmiastowej dostępności.
5. Wyślij testerom link opt-in, poproś, by **zalogowali się tym samym
   kontem Google, które widnieje na liście**, zaakceptowali udział i
   zainstalowali grę przez Google Play. Sprawdzaj aktywnych uczestników
   w konsoli, a nie tylko listę zaproszonych.
6. Przekaż testerom **„Scenariusz dla testerów: telefon i Galaxy Fold”**
   (sekcja wyżej) oraz dodatkowo: powtórka, eksport/import kopii
   profilu, powiadomienie, logowanie Play Games, ranking i osiągnięcia
   (po wpisaniu prawdziwych ID). Poproś o datę, model telefonu, wersję
   Androida, zrzut i opis kroków dla każdego błędu. Zbieraj także
   informację, co działało. **Poproś testerów o wysłanie „Raportu dla
   testera” po 3. i po 10. dniu testu** (Ustawienia → O grze → Raport dla
   testera): postęp, dni i sesje gry, tryby, wydajność telefonu i ustawienia.
   To dane do odpowiedzi na pytania we wniosku o produkcję (pkt 9).

   Raport jest **lokalny**: liczniki leżą w `files/playtest.json` (poza
   `profile.json`, kopią Androida i Saved Games), gra niczego nie wysyła
   sama ani przez sieć. Przycisk pokazuje opis zawartości, a potem
   systemowe okno udostępniania; tester sam wybiera aplikację i odbiorcę,
   a anulowanie nic nie wysyła. Raport nie zawiera nicku, UUID ani sekretu
   rankingu, ani konta Play Games (tak jak „Zgłoś problem”).
7. **Minimum Google (stan: 2.11.2026):** dla **osobistych kont
   deweloperskich utworzonych po 13 listopada 2023 r.** co najmniej
   **12 testerów musi mieć nieprzerwanie aktywny opt-in przez 14 dni**
   **przed złożeniem wniosku o dostęp do produkcji**. Nie wystarczy
   12 wpisanych e-maili ani samo pobranie gry. Rezygnacja z testu
   i ponowny zapis restartują 14-dniowy okres danej osoby.
   To wymaganie dotyczy wskazanej kategorii kont; nie jest
   uniwersalnym wymogiem dla wszystkich istniejących kont firmowych.
8. W trakcie dwóch tygodni reaguj na problemy testerów, publikuj
   poprawione AAB na tym samym torze i zachowaj ciągłość ich udziału.
   Sprawdzaj raport przedpremierowy, stabilność i powtarzalność
   zgłaszanych błędów. W dokumentacji testu zanotuj użycie kluczowych
   funkcji i konkretne poprawki.
9. Po spełnieniu wymagań wejdź na **Dashboard → Apply for production**
   i odpowiedz na pytania o przebieg testów, opinię testerów, aplikację
   oraz gotowość do wydania. Dostęp produkcyjny wymaga zatwierdzenia
   przez Google; nie jest przyznawany automatycznie po 14 dniach.
   Google może poprosić o dalsze testy.

**Oficjalne źródła Google (sprawdzone 2.11.2026):**
- [App testing requirements for new personal developer accounts](https://support.google.com/googleplay/android-developer/answer/14151465)
  — kwalifikacja konta, 12 testerów, nieprzerwany okres 14 dni i wniosek.
- [Set up an open, closed, or internal test](https://support.google.com/googleplay/android-developer/answer/9845334)
  — tory testowania, lista e-mail, link opt-in, wydanie i opinie.

### Scalanie raportów testerów (koordynator, `tools/playtest_merge.py`)

Narzędzie z R13 (TOP 5 poz. 4). Czyta „Raporty dla testera” i liczy bramkę G2
(R13 §1) bez ręcznego liczenia median. Tylko Python 3, bez instalowania pakietów.

1. Od właściciela weź CSV z Arkusza odpowiedzi formularza (**Plik → Pobierz → CSV**)
   i/lub raporty z e-maili zapisane jako `.txt` albo `.eml` w jednym katalogu.
   W nazwie pliku możesz dać dzień, np. `kasia-dzien3.txt`, `kasia-d10.eml`.
2. Uruchom w katalogu repozytorium:

   ```
   python3 tools/playtest_merge.py odpowiedzi.csv raporty/ \
       -o docs/reports/test-fala1.md --fala 1 --csv-out fala1.csv \
       --active-testers 14 --crash-groups 0 --prelaunch-crashes 0
   ```

   - `--active-testers`, `--crash-groups`, `--prelaunch-crashes`: liczby z Play Console
     (kryteria 2.1–2.3). Bez nich bramka wychodzi „NIEROZSTRZYGNIĘTE”.
   - Kolumny formularza są wykrywane po nagłówkach z R13 §3 (pseudonim, „Który raport”,
     model telefonu, uwagi, wklejony raport). Inne nazwy: `--column which="Dzień"`,
     `--column tester="Kod"` itd.
   - Bez pola „Który raport” i bez dnia w nazwie pliku dzień wynika z dat raportu
     (rozpiętość < 7 dni → dzień 3, inaczej 10; zmiana: `--day-split`). W tabeli ma gwiazdkę.
3. Przeczytaj w wyniku:
   - tabelę G2 (twarde/miękkie: wartość, próg, OK/NIE/nie do policzenia);
   - sekcję „Czego nie da się policzyć”: raport w formacie 1 nie ma kodu testera, więc
     dzień 3 i 10 tej samej osoby łączy się tylko po pseudonimie i modelu telefonu;
     nie ma też awarii/ANR ani lejka etapów (Raport v2, TOP 5 poz. 1–2);
   - „Uwagi parsera”: obcięte i uszkodzone raporty (ostatnia ucięta linia nie jest
     liczona) oraz pominięte duplikaty.
4. Wklejone „Zgłoś problem” (raport techniczny) narzędzie tylko liczy. Przejrzyj je
   ręcznie jako kandydatów na P0.
5. Plik `--csv-out` (tester × dzień) zostaw poza repozytorium. Pseudonimy z formularza
   trafiają do Markdown, więc przed commitem sprawdź, czy nie ma tam imion i nazwisk.

Progi są stałymi na górze `tools/playtest_merge.py` (`G2`). Zmiana progu w R13 oznacza
zmianę w obu miejscach. Test `tests/test_playtest_merge.py` sprawdza, czy etykiety narzędzia
zgadzają się z `PlaytestReportText.kt`. Po wdrożeniu Raportu v2 trzeba poprawić wzorce
`FUTURE_FIELDS`, które są dziś tylko propozycją z R13.

## Walidacja

Lokalnie (po wprowadzeniu prawdziwej konfiguracji publikacji):

```bash
./gradlew -p buildSrc test --console=plain
./gradlew :app:testDebugUnitTest --tests '*ReleaseConfigTest*' --tests '*DailyTest*' --console=plain
./gradlew :app:renderStorePolicies --console=plain
./gradlew :app:releaseChecklist --console=plain
./gradlew :app:assembleRelease :app:bundleRelease --console=plain
```

Workflow `.github/workflows/release-check.yml` (PR do `main` lub `claude/**`, i ręcznie) buduje release,
sprawdza obecność `baseline.prof` w APK/AAB, manifest przez `aapt`, raportuje rozmiary
w Job Summary i uruchamia aplikację na emulatorze API 35
(`com.centurywar.game/com.centurywar.MainActivity`, kontrola procesu i `AndroidRuntime`).
Logi trafiają do artefaktu `release-walidacja`.

Po podpisaniu upload key pozostają kroki w Play Console: polityka prywatności,
Data safety, listing (`docs/store/`) i wdrożenie w torze testowym.


## Play Games: konfiguracja premierowa z jednego pliku

**Jedynym źródłem identyfikatorów jest `docs/store/play_games_ids.properties`.**
Nie edytuj ręcznie XML. Gradle generuje `games_ids.xml` osobno dla każdego wariantu
w `app/build/generated/res/playGames/<wariant>/values/games_ids.xml`.
Plik zawiera 84 klucze: ID projektu, 4 rankingi i 79 osiągnięć.

1. Otwórz Google Play Console → **Century War** (`com.centurywar.game`) →
   **Play Games Services → Setup and management → Configuration**. Utwórz usługę
   i odczytaj numeryczny **Game services project ID** (nie OAuth Client ID).
   Wpisz go jako `app_id` w `docs/store/play_games_ids.properties`.
2. W **Play Games Services → Leaderboards** utwórz cztery rankingi
   z sortowaniem malejącym i skopiuj ich identyfikatory z Play Console do
   `leaderboard_endless` (oblężenie), `leaderboard_campaign` (gwiazdki),
   `leaderboard_skirmish` (potyczka) i `leaderboard_daily` (wyzwanie dnia).
   Gra wyświetla ranking dzienny w oknie `TIME_SPAN_DAILY`.
3. W **Play Games Services → Achievements** utwórz 79 osiągnięć standardowych.
   Wykorzystaj listę `Achievements.all` z `meta/Achievements.kt`. Dla każdego
   identyfikatora katalogu `id` skopiuj ID przyznane przez Play Console do
   `achievement_<id>`, np. `achievement_win_1`. Wszystkie klucze z szablonu
   muszą mieć prawdziwe ID. Mapowanie `cw_<id>` na zasoby jest w
   `meta/PlayGamesIds.kt`; nie zmieniaj tego mapowania przy wpisywaniu ID.
4. W **Play Games Services → Configuration → Credentials** utwórz
   poświadczenia OAuth dla Androida (`com.centurywar.game`). Dodaj SHA-1
   certyfikatu debug oraz certyfikatu **App signing key certificate** z
   Play Console. Klucz upload może mieć inny SHA-1. Włącz **Saved Games**,
   dodaj konta testerów i opublikuj konfigurację PGS do testów.
5. Uruchom `./gradlew :app:generateDebugPlayGamesIds :app:assembleDebug` oraz
   `./gradlew -p buildSrc test`. Debug bez konfiguracji lub z `REPLACE_ME`
   uruchamia wyłącznie funkcje lokalne (`play_games_configured=false`).
   Brak choć jednego ID lub placeholder blokuje **release** niezależnie od
   `-PskipCheck=playGames`. ID o złym formacie powoduje błąd walidacji.
6. Na urządzeniu testera z Google Play Services zainstaluj APK debug
   z kompletem ID. Otwórz **Ustawienia → Play Games**: sprawdź logowanie
   i synchronizację. Otwórz cztery tablice, wyślij wyniki z czterech trybów,
   odblokuj osiągnięcie i potwierdź je w PGS. Na drugim urządzeniu sprawdź
   pobranie profilu z chmury. Powtórz próbę offline i bez usług Google;
   profil i gra muszą działać lokalnie. Zainstaluj również AAB z toru
   **Internal testing** i potwierdź logowanie przy podpisie produkcyjnym.
7. Przed premierą wykonaj `:app:releaseChecklist`, build wydania, testy
   jednostkowe i testy na rzeczywistych urządzeniach. Sam build nie
   potwierdza dostępności usług po stronie Play Console.

Szczegóły i znaczenie osiągnięć: [PLAY_GAMES_SETUP.md](PLAY_GAMES_SETUP.md).

## Lista kontrolna dnia wydania — dla właściciela

Przed pierwszą publikacją i przed **każdą kolejną** aktualizacją przejdź kroki w podanej kolejności.

**Krok 0 — dane wydawcy (obowiązkowy):** otwórz
[docs/store/OWNER_INPUT.md](store/OWNER_INPUT.md), uzupełnij pięć pól:
pełna nazwa prawna wydawcy, e-mail kontaktowy, adres korespondencyjny,
publiczny URL polityki HTTPS i rzeczywista data pierwszego wydania UTC. Nie wpisuj tutaj sekretów.
Następnie uruchom `./gradlew :app:renderStorePolicies` i opublikuj
**wygenerowane** pliki PL/EN z `build/store-policies/` pod wskazanym adresem.
Nie publikuj szablonów z nierozstrzygniętymi znacznikami właściciela.

1. **Ustal dzień pierwszej publicznej publikacji w UTC.** W
   `sim/src/main/kotlin/com/centurywar/game/Modes.kt` ustaw jedną stałą
   `Daily.RELEASE_DAY`: liczbę dni od 1970-01-01 00:00 UTC. Obecne **21153**
   (2027-12-01) jest wyłącznie wartością zastępczą, **nie jest zatwierdzoną datą**.
   Po pierwszym wydaniu **nigdy jej nie zmieniaj**: dotyczy jednocześnie Epoki żelaza,
   wariantów epok i pogody w wyzwaniu dnia. Starsze dni korzystają z `legacyConfig`
   i muszą odtwarzać się identycznie. Datę w UTC przelicz np.:
   `python3 -c "from datetime import date; print((date.fromisoformat('RRRR-MM-DD')-date(1970,1,1)).days)"`
   (zastąp `RRRR-MM-DD` prawdziwą datą).
2. **Zweryfikuj Play Console.** Utwórz aplikację `com.centurywar.game`,
   uzupełnij stronę sklepu i wymagane deklaracje, upewnij się, że dostępne są
   właściwe konta właścicielskie i dostęp do torów testowania.
3. **Podpis produkcyjny.** Włącz Play App Signing, przygotuj klucz upload,
   kopię zapasową i cztery zmienne `RELEASE_*` lokalnie lub sekrety CI z
   [CI.md](CI.md). Zweryfikuj SHA-1 certyfikatu podpisu aplikacji i upload.
   Nie publikuj APK/AAB podpisanych kluczem debug.
4. **Play Games i osiągnięcia.** Utwórz usługę, cztery rankingi i 79
   osiągnięć w Play Console. Ich ID wpisz wyłącznie w
   `docs/store/play_games_ids.properties` (bez ręcznego XML).
   Skonfiguruj testerów, OAuth i Saved Games; zweryfikuj logowanie,
   chmurę, rankingi i osiągnięcia na urządzeniu.
   Szczegóły w sekcji wyżej i [PLAY_GAMES_SETUP.md](PLAY_GAMES_SETUP.md).
5. **Backend rankingu i podpis wyników.** Uruchom serwer z HTTPS, sprawdź
   `/health`, dostępną wersję protokołu i wysyłkę zweryfikowanej powtórki.
   Ustaw `CW_SERVER_URL`, `CW_HMAC_KEY` i `CW_PRIVACY_URL` w
   `local.properties` albo zmiennych środowiskowych na etapie kompilacji.
   Adres serwera oraz adres opublikowanej polityki prywatności muszą być HTTPS.
   Nie wprowadzaj wartości produkcyjnych ani sekretów do repozytorium.
6. **Polityka prywatności i Data safety.** Sprawdź, że wygenerowane z
   `OWNER_INPUT.md` polityki PL i EN są publicznie dostępne pod HTTPS,
   a `CW_PRIVACY_URL` prowadzi do właściwej strony. W Play Console uwzględnij
   dane PGS, chmurę, publiczne pseudonimy w rankingach PGS i własnych,
   moderowanie nicków po wdrożeniu zlecenia 80, ręcznie udostępniane
   raporty awarii ze zlecenia 65, dane techniczne i usuwanie danych.
   Potwierdź faktyczne działanie własnego backendu oraz aktualny formularz Data safety.
7. **Materiały sklepu.** Sprawdź nazwy, opisy i tłumaczenia, ikonę, grafikę
   promocyjną i prawdziwe zrzuty z telefonu 720p oraz dużego ekranu/Folda.
   Zrzuty aktualnej wersji i zawartość sklepu muszą być zgodne z grą.
8. **Numer wersji.** Podnieś `releaseVersionCode` w `gradle.properties`
   ponad liczbę w `release/last_version_code.txt` (początkowo `0`).
   Sprawdź `releaseVersionName` lub tag SemVer; nie używaj ponownie kodu
   już opublikowanego w Google Play.
9. **Testy przed publikacją.** Uruchom `./gradlew -p buildSrc test`,
   `./gradlew :app:assembleDebug`, testy `Daily*`, `Replay*`,
   `BattleSimTest*`, `LTest*` oraz `./gradlew :server:test -PserverOnly=true`.
   Przetestuj debug i AAB na rzeczywistym tanim telefonie oraz Foldzie,
   online i offline; sprawdź wyzwanie UTC po obu stronach północy.
10. **Zbuduj release bez pominięć.** Uruchom
    `./gradlew :app:releaseChecklist :app:bundleRelease --console=plain`.
    `assembleRelease` uruchamia tę samą checklistę; **debug jej nie uruchamia**.
    Zbadaj podpis i przeskanuj artefakt; nie ignoruj czerwonej checklisty.
11. **Wdrożenie i kontrola.** Wgraj AAB na tor *internal testing*,
    sprawdź instalację z Google Play, logowanie, rankingi, chmurę i crash logi.
    Dopiero po zatwierdzeniu wyników przejdź do docelowego toru publikacji.
    Po **faktycznym opublikowaniu** wpisz użyty kod do
    `release/last_version_code.txt` i zatwierdź zmianę — kolejny release
    będzie wymagał większego kodu.

### Automatyczna blokada Gradle i świadome wyjątki

Zadanie `:app:releaseChecklist` odczytuje `Daily.RELEASE_DAY` bezpośrednio
z `Modes.kt` oraz dzisiejszy dzień **UTC**. Zgłasza wszystkie problemy
jednym polskim komunikatem i blokuje `:app:assembleRelease` oraz
`:app:bundleRelease`, jeśli:

- `RELEASE_DAY` jest dawniej niż 30 dni temu lub dalej niż 60 dni w przyszłości;
- `play_games_ids.properties` nie zawiera kompletu prawidłowych ID (generator release blokuje wydanie);
- `CW_SERVER_URL` lub `CW_PRIVACY_URL` nie jest poprawnym adresem HTTPS;
- `CW_HMAC_KEY` jest pusty;
- `releaseVersionCode` nie przewyższa `release/last_version_code.txt`
  albo ten plik ma niepoprawną zawartość;
- nie istnieje kompletny i poprawnie wskazany keystore upload lub podano
  standardowy podpis debug (ta kontrola jest **niepomijalna** przy normalnym release);
- brakuje `docs/store/OWNER_INPUT.md`, któreś z jego pięciu pól pozostaje
  niewypełnione albo ma nieprawidłowy format (e-mail, URL, data, ID PGS);
- brakuje dokumentów `privacy_policy_pl.md`, `privacy_policy_en.md`,
  `data_safety.md` lub `content_rating.md`, lub jakikolwiek dokument
  `docs/store/*.md` zawiera znacznik do uzupełnienia typu INSERT,
  UZUPEŁNIJ albo DO DECYZJI. Dozwolone są tylko znane pola szablonu
  OWNER, podstawiane przez `renderStorePolicies`.

Przy **świadomej decyzji** można pominąć pojedyncze kontrole zewnętrznej
konfiguracji przez `-PskipCheck=releaseDay`, `playGames`, `serverUrl`,
`privacyUrl`, `hmacKey` lub `versionCode`; wiele wartości oddziel przecinkami.
**Nie umożliwia to pominięcia podpisu ani znaczników dokumentów.**
`ownerInput` można pominąć jedynie w oznaczonym smoke-teście CI
z `-PciTestDebugSigning=true`; nie jest to opcja publikacyjna. Przykład:

```bash
./gradlew :app:assembleRelease -PskipCheck=playGames,releaseDay
```

Wyjątek `playGames` nie wyłącza walidacji generatora zasobów release.
Wyjątek **nie konfiguruje** brakującej usługi ani nie naprawia podpisu.
Ostateczna publikacja wymaga zaliczenia całej listy kontrolnej bez flag pomijających.
Workflow `release-check.yml` świadomie pomija konfigurację niezbędną
wyłącznie do publikacji; produkcyjny workflow z taga nie pomija niczego.

### Kontrola rzeczywistego podpisu

Po zbudowaniu sprawdź certyfikat APK i porównaj jego SHA-256 z kluczem upload
właściciela (certyfikat Play App Signing w Google Play może być inny):

```bash
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs app/build/outputs/apk/release/app-release.apk
keytool -list -v -keystore /bezpieczna/sciezka/upload-key.jks -alias TWOJ_ALIAS
jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab
```

Na potrzeby publikacji uruchom oba polecenia build bez `ciTestDebugSigning`
i bez `skipCheck`. W Play Console wybierz najpierw tor testów wewnętrznych,
potwierdź działanie rankingu, chmury, usuwania danych i polityki, dopiero potem
uruchom publikację.
