# Play Games: z arkusza do konsoli i z powrotem

Stan: 3.11.2026. Pliki:
- `docs/store/play_games_sheet.csv`: arkusz do wpisywania ręcznie, 5 rankingów i 83 osiągnięcia (6.11: ranking i 4 osiągnięcia Rozkazów sztabu);
- `docs/store/pgs_import/`: pliki do **importu wszystkich osiągnięć naraz**;
- `tools/pgs_sheet.py`: generuje oba z kodu gry (`python3 tools/pgs_sheet.py`, a samo sprawdzenie
  to `--check`). Po każdej zmianie osiągnięć albo tekstów uruchom go ponownie.

## Limity Google i nasz podział punktów

Źródła: [Achievements](https://developer.android.com/games/pgs/achievements),
[Integrate achievements](https://developer.android.com/games/pgs/integrate-achievements),
[Leaderboards](https://developer.android.com/games/pgs/leaderboards).

| Co | Limit Google | U nas |
|---|---|---|
| Nazwa osiągnięcia / rankingu | do 100 znaków, unikalna w języku | najdłuższa pl: 25 |
| Opis osiągnięcia | do 500 znaków | najdłuższy pl: 62 |
| Punkty jednego osiągnięcia | wielokrotność 5, od 5 do 200 | 5–40 |
| Suma punktów gry | najwyżej 2000 (XP gracza = 100 × punkty) | **1605** (160 500 XP), zapas 395 na przyszłe osiągnięcia (własny budżet 1650; d67: Rozkazy 18/54★) |
| Liczba osiągnięć | najwyżej 400 przez całe życie gry | 83 |
| Rankingi | najwyżej 70 | 4 |
| Ikona | 512×512 PNG/JPEG; osiągnięcie: kolorowe (szarą wersję robi Google), w toaście przycięte do koła; bez tekstu | brak (zlecenie `docs/tasks/R9-achievement-icons.md`) |
| Kroki osiągnięcia przyrostowego | do 10 000 | żadne nie jest przyrostowe (patrz niżej) |

**Podział punktów** według nagrody w medalach w grze: do 30 medali 5 pkt, do 60 10 pkt,
do 150 15 pkt, do 200 20 pkt, do 300 25 pkt, do 500 30 pkt, powyżej 40 pkt.

**Kolejność wyświetlania** idzie od najłatwiejszych (najmniej medali). Google i tak pokazuje
odblokowane osiągnięcia na górze. Ukryte jest 6 osiągnięć, tak jak w grze.

## Ważne przed publikacją osiągnięć

- **Przyrostowe czy standardowe?** Gra wysyła tylko „odblokuj” (`unlock`), więc w arkuszu
  wszystkie są standardowe. Google zaleca przyrostowe, bo gracz widzi pasek postępu. **Typ
  i stan ukrycia nie dają się zmienić po publikacji**, a opublikowanego osiągnięcia nie można
  usunąć. Jeśli chcemy przyrostowe, np. dla `win_*`, `battles_*`, `kills_*`, `research_*`,
  `daily_7/30` czy `turrets_50`, kod musi to obsłużyć **przed** publikacją. `kills_100000`
  przekracza limit 10 000 kroków, więc trzeba by go skalować. To decyzja koordynatora.
- **4 osiągnięcia z R6-01** (`first_evolve`, `first_turret`, `first_research`, `stages_3`) są
  już w grze; arkusz i import mają ich końcowe teksty w 10 językach z `part185()` w L.kt.
- **Ranking dzienny:** Google zeruje dzienne rankingi o północy czasu UTC−7, a wyzwanie dnia
  w grze zmienia się o północy UTC. Wyniki z godzin 00:00–07:00 UTC trafiają więc do
  „wczorajszej” tablicy Google. Do oceny przez koordynatora: zostawić i opisać albo pokazywać
  okno tygodniowe lub ogólne.

## Krok 1: ustawienia gry w Play Games

W Play Console wejdź w **Grow users → Play Games Services → Setup and management →
Configuration**. W „Szczegółach gry” ustaw **język domyślny: polski** (pliki importu mają
polskie teksty jako domyślne) i **dodaj 9 tłumaczeń**: en-US, de-DE, es-ES, fr-FR, it-IT,
pt-PT, uk, nl-NL, tr-TR. Zrób to **przed importem**, bo plik tłumaczeń działa tylko dla języków
już dodanych do gry. [Tłumaczenia](https://developer.android.com/games/pgs/console/enable-features#add_translations)

Skopiuj **Game services project ID** (same cyfry) do kolumny `id_z_konsoli` albo od razu
jako `app_id`.

## Krok 2: rankingi (5, ręcznie)

**Leaderboards → Create leaderboard**, po jednym na wiersz `ranking` arkusza:
- nazwa z `nazwa_pl`;
- format **Numeric**, bez miejsc po przecinku i bez własnej jednostki (własne jednostki
  wymagają tłumaczeń w każdym języku);
- **Larger is better** (tego nie da się zmienić po publikacji);
- kolejność z `kolejnosc`;
- ochrona przed oszustwami zostaje włączona (domyślnie);
- ikona 512×512 z `app/build/pgs-icons/lb_<tryb>.png` (generator w kroku 3).

Po zapisaniu kliknij kartę każdego języka i wpisz tłumaczenie nazwy z L.kt
(`board_endless`, `board_campaign`, `board_skirmish`, `board_daily`, `board_orders`; kolumny w kolejności pl,
en, de, es, fr, it, pt, uk, nl, tr). Skopiuj ID każdego rankingu do `id_z_konsoli`.

## Krok 3: osiągnięcia (83, najlepiej importem)

**Szybko (ok. 10 minut):**
1. Wygeneruj ikony 512×512 (R9, rysowane kodem; w repo jest tylko stykówka
   `docs/store/assets/pgs/contact.png` i mapowanie) i spakuj ZIP importu, bez podkatalogów
   (`AchievementsMetadata.csv`, `AchievementsLocalizations.csv`, `AchievementsIconsMappings.csv`
   i 83 PNG `ach_*.png`; razem 86 plików, limit 403):

   ```
   PGS_ICONS=true flock /tmp/claude-0/gradle.lock ./gradlew :app:testDebugUnitTest --tests '*PgsIconsTest*' --rerun
   rm -f build/pgs-achievements.zip
   zip -j build/pgs-achievements.zip docs/store/pgs_import/AchievementsMetadata.csv \
       docs/store/pgs_import/AchievementsLocalizations.csv \
       app/build/pgs-icons/AchievementsIconsMappings.csv app/build/pgs-icons/ach_*.png
   ```

   Ikony rankingów (`app/build/pgs-icons/lb_*.png`) wgraj ręcznie w kroku 2.
2. Wybierz **Achievements → Import achievements → Upload**, wskaż ZIP i kliknij **Save as draft**.

Pliki są w formacie Google: bez nagłówka, 7 pól, `False` = standardowe, `Hidden`/`Revealed`,
punkty i kolejność. Google **nie pozwala na przecinki** w nazwie i opisie, więc w 21 tekstach
przecinek zamieniono na „ –”. Popraw je ręcznie w formularzu konsoli, jeśli przeszkadzają.
**Tłumaczenia trzeba dodać w tym samym imporcie**: późniejszy import nie doda tłumaczeń do
istniejących osiągnięć, zostaje wtedy ręczne wpisywanie po jednym.

**Ręcznie (3–4 godziny):** **Create achievement** dla każdego wiersza `osiągnięcie`: nazwa,
opis, punkty (`punkty_pgs`), stan początkowy (`ukryte`), kolejność (`kolejnosc`). Tłumaczenia
wpisuje się na kartach języków; teksty są w L.kt pod `ach_<id>` i `ach_<id>_d`.

**Czy dodawać 8 pozostałych języków od razu?** Tak. Wszystkie 10 języków jest już
przetłumaczonych w L.kt (ta sama gra), import dodaje je bez dodatkowej pracy, a później
każde tłumaczenie to ręczne wpisywanie (83 × 9 pól).

## Krok 4: ID z konsoli do `docs/store/play_games_ids.properties`

Po imporcie lub ręcznym dodaniu każdy element ma w konsoli ID (np. `CgkI…`). Wpisz je
w arkuszu w kolumnie `id_z_konsoli`, a potem do pliku properties. Kluczem jest
`klucz_w_properties`:

```
app_id=123456789012
leaderboard_endless=CgkIxxxxxxxxxxxxEAIQAQ
achievement_win_1=CgkIxxxxxxxxxxxxEAIQBQ
```

Jak build czyta ten plik (`buildSrc/.../PlayGamesIdsGenerator.kt`):
- to zwykły plik `klucz=wartość`. Gradle generuje z niego `games_ids.xml`; nie edytuj XML;
- `app_id` to same cyfry. Pozostałe ID zaczynają się literą i mają 12–128 znaków
  (litery, cyfry, `_`, `-`). Zły format oznacza błąd builda;
- **nieznany klucz to błąd builda**: plik ma dokładnie 89 kluczy (projekt, 5 rankingów,
  83 osiągnięcia, w tym 4 z R6-01, 1 po D6 i 4 za Rozkazy sztabu z d65);
- ID nie mogą się powtarzać;
- jeśli brakuje choć jednego ID albo zostaje `REPLACE_ME`, wersja debug działa tylko lokalnie,
  a wersja do sklepu (release) się nie zbuduje.

ID nie są tajne. Wystarczy wysłać koordynatorowi wypełniony arkusz albo listę
`klucz=wartość`, a on wpisze ją do pliku i uruchomi `./gradlew -p buildSrc test`.

## Krok 5: test i publikacja

Dodaj konta testerów w **Play Games Services → Testers**, zainstaluj grę z toru testów
wewnętrznych i sprawdź logowanie, rankingi i odblokowanie osiągnięcia. W testach toast pokazuje
0 XP, i tak ma być. Na koniec **Review and publish** na stronie Achievements i **Publish**
na stronie Publishing.
