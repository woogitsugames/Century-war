# D7 — grafiki sklepu Google Play: zestaw premierowy

Zadanie: `docs/tasks/D7-store-graphics.md`. Generator: `StoreGraphicsTest.generateD7`
(`STORE_GRAPHICS_D7=true`), walidator `scripts/validate_store_d7.py`, opis i polecenia w
`docs/store/STORE_GRAPHICS.md` („Zestaw premierowy D7”). Eksport 10 języków × 6 urządzeń trwa
ok. 2 min (Robolectric NATIVE). Walidator: **240 zrzutów do Play w 10 językach, OK**.

## Kadry

| Nr | Plik | Co widać |
| --- | --- | --- |
| 01 | `01-commander.png` | Bitwa z Wodzem (korona, aura), pełny HUD z przyciskiem „Wódz — w bitwie” |
| 02 | `02-turret-tiers.png` | Otwarty panel wież: wieża na stopniu 2 i na stopniu 3 (Maks.), pusty slot |
| 03 | `03-campaign.png` | Mapa kampanii po 10 etapach, przewinięta do bieżącego etapu 11; gwiazdki w epokach 2–3, epoka 4 zablokowana |
| 04 | `04-endless-cards.png` | Wybór 3 kart (zwykła / niepospolita / legendarna) przy fali 10 z bossem, zegar 9:23 |
| 05 | `05-results.png` | Zwycięstwo etapu 11: 3 gwiazdki, „Za bitwę” i „Za osiągnięcia” w osobnych wierszach, „Nowy rekord”, cel (badanie Doktryny elit) |
| 06 | `06-missions.png` | Misje: seria 4/7, misje dzienne — jedna do odebrania, jedna odebrana, jedna w toku |
| 07 | `07-accessibility.png` | Bitwa w palecie „wysoki kontrast”, znaczniki drużyn nad oddziałami, tekst 115% |
| 08 | `08-weekly-siege.png` | Oblężenie tygodnia: zasady, karta startowa, progi Brąz/Srebro/Złoto, „Graj” |

Pełne PNG do przeglądu: `docs/store/assets/D7/{pl,en}/{phone,tablet10}/`; stykówki i manifesty
wszystkich 10 języków: `docs/store/assets/D7/<język>/`.

## Przegląd stykówek i pełnych PNG

Obejrzane: stykówki 10 języków, pełne PNG pl/en/de (telefon, tablet 7″, tablet 10″, Fold),
stykówki pionu Folda pl/en. Poprawione w trakcie:

- **06 misje**: na 16:9 lista misji tygodniowych jest pod krawędzią ekranu (przewijana), a podpis
  na dole zasłaniał misję dzienną. Podpis zmieniony na „Misje dzienne” (i odpowiedniki) i
  przeniesiony w lewy górny róg nad przycisk „Wstecz”; test pilnuje, że nie sięga wstęgi tytułu.
  Alt opisuje to, co faktycznie widać.
- **03 kampania**: pierwszy kadr stał na początku mapy (animacja przewijania nie zdążyła ruszyć);
  teraz kadr po 150 klatkach pokazuje bieżący etap i gwiazdki z dwóch epok.
- **04 karty**: zegar pokazywał 0:00 przy fali 10 — scena ma realny czas i stan baz.
- **05 wyniki**: podpis przesunięty w róg, żeby nie nachodził na wstęgę „Zwycięstwo”.
- **Fold (rozłożony)**: pasek HUD ma tam dwa rzędy, więc podpis bitwy schodzi niżej (21,5%).

Bez uwag: brak samouczka, dymków i banerów; HUD i `compactTop` bez zmian; wiersze medali osobno
we wszystkich językach (test sprawdza też `resultRowsClipped == 0` i narysowany cel); najdłuższe
podpisy (uk/nl/tr, 32 znaki) mieszczą się bez zmniejszania czcionki. Tablet 7″ (d=1,8):
elementy nieco większe niż na telefonie, wszystko czytelne.

Znane drobiazgi (do decyzji):
- Pion zewnętrznego Folda (tylko QA): w kadrze 04 podpis nachodzi na wstęgę „Wybierz kartę”, w
  05 na wyszarzony HUD. Nie idzie do Play.
- Kadr 08 zasłania podpisem licznik medali w prawym górnym rogu (jak w zestawie 117).
- Kadr 08 pokazuje odliczanie i zestaw zasad bieżącego tygodnia, więc przed uploadem trzeba
  wygenerować go ponownie (zmienia się z datą). Kadr 06 ma stały dzień (zegar ekranu misji).
- Kadr 05 (dzień 19): poprawiony, patrz niżej.

## Propozycja wyboru do Play

- **Telefon**: wszystkie 8 w kolejności 01–08 (01 i 04 najmocniejsze na start listingu).
- **Tablet 7″ i 10″**: te same 8; na tablecie 10″ można zamienić 06 (misje) na 03, jeśli chcemy
  mniej ekranów menu, ale zestaw jest spójny i nie wymaga różnic.
- **Fold** (`fold/`, `play_upload=false`): wygląda poprawnie (HUD dwurzędowy, podpisy pod
  paskiem); można wgrać jako tablet po akceptacji właściciela.

## Zmiana w kodzie gry

`MissionsScreen.clock` (internal, domyślnie `System.currentTimeMillis()`): stały zegar dla
powtarzalnego kadru 06. Zachowanie gry bez zmian.

## Odświeżenie po D6 (dzień 19)

- **Kadr 04**: karty z D6 na stałe — Mur tarcz (zwykła), Saperzy (niepospolita), Zakład
  (rzadka). Nazwy i opisy mieszczą się w kartach we wszystkich 10 językach (najdłuższy opis
  „Zakładu” w es/fr/it/uk: 4 wiersze, z zapasem w karcie). Wybór automatyczny (najnowsza karta każdej rzadkości)
  dawał ten sam zestaw, ale jest zastąpiony stałą listą, żeby przyszłe karty nie zmieniały kadru.
- **Kadr 05**: wcześniej niemożliwa bitwa (3 gwiazdki w 0:40, 9 zabitych, +620 medali za
  osiągnięcia z pustego profilu). Teraz etap 11 po prawdziwej symulacji 7:45 (przed presją
  oblężniczą), 3 gwiazdki, kilkadziesiąt zabitych, +131 za bitwę (wzór kampanii) i +130 za
  dwa osiągnięcia (`battles_25` 35 + `kills_1000` 95) na profilu po 24 bitwach; „Następny cel”
  widoczny. Test pilnuje przedziałów (czas 7–10 min, 30–99 zabitych, suma osiągnięć).
- **Sygnały R10-03**: sceny bitwy sprawdzają, że znak „baza atakowana” jest niewidoczny; w
  żadnym kadrze go nie ma. Zewnętrzny Fold (QA) pokazuje przybliżone pole bitwy — zgodnie z D17.

## Karty Endless po D6 (archiwalne)

Kadr 04 bierze najnowszą kartę każdej rzadkości z końca `EndlessCard`. D6 dopisuje karty na
końcu enumu, więc po scaleniu wystarczy ponowić eksport (ewentualnie
`STORE_D7_CARDS=A,B,C` do ręcznego wyboru) i podmienić stykówki/PNG w `docs/store/assets/D7/`.
