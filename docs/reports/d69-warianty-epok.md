# D69 — warianty epok jednej roli (R171)

Gałąź `claude/dzien-69-warianty-epok` (od `origin/main` e752f182). Decyzja właściciela 7.11:
cztery nowe jednostki z `docs/research/R171-warianty-epok.md`, na początek tylko w potyczce.

## Co zbudowano

| Epoka | Nowa jednostka | Zastępuje | Rola | Cecha (jedna, deterministyczna) |
|---|---|---|---|---|
| Plemiona | Tarczownik (`u_shieldbearer`) | Osiłek | HEAVY | po pierwszym otrzymanym trafieniu przez 2 s obrażenia ×0,8 (jednorazowo) |
| Brąz | Lekki wóz (`u_light_cart`) | Rydwan | HEAVY | do pierwszego strzału albo pierwszego trafienia prędkość ×1,25 |
| Żelazo | Taran (`u_iron_ram`) | Skorpion polowy | ELITE | pierwsze uderzenie obejmujące bazę ×1,25 (nie jednostki, nie wieże) |
| Proch | Strzelec z osłoną (`u_mantlet_gunner`) | Muszkieter | RANGED | po 2 s bez ruchu następny strzał ×1,15; ruch i strzał zerują licznik |

- **Sim** (`Defs.kt`): `EraRecruit` w `EraSpec.recruit`, obiekt `Trait` (stałe cech), `UnitDef.trait`.
  Nowe jednostki mają identyfikatory za wszystkimi dotychczasowymi (stare ID bez zmian).
  `Side.eraVariant` = 0 oznacza nową jednostkę, -1 podstawowy skład; wieża, ostrzał, wygląd
  i dowódca pozostają podstawowe (`Defs.styleVariant`). `Defs.variantChoices(era)` daje karty:
  w epoce jednej roli karta 0 = jawny „Podstawowy skład” (-1), karta 1 = nowa jednostka.
- **Tryby**: `BattleConfig.eraRecruits` (domyślnie false) włączają tylko ekrany potyczki
  (`ModeScreen`, `SkirmishSetup.toConfig`, powtórka „jeszcze raz”) i serwer dla tablicy `skirmish`.
  Kampania, Endless, wyzwania dnia/tygodnia i Rozkazy bez zmian (`Battle.variantsOffered`).
- **AI** (`Ai.chooseVariant` → `chooseRecruit`): jedno losowanie ze strumienia AI strony, 50%
  z lekkim przesunięciem osobowości (SIEGER +10 dla tarana, ASSAULT +6 dla ciężkich, ENGINEER +6
  dla strzelca). Pozostała logika AI bez zmian.
- **Stan**: `Fighter.traitUsed`, `Fighter.traitT`, `Side.traitUses` (pomiar). Zrzut **v25**
  (odczyt v16–v23; v24 zostawiony dla równoległej zmiany), odcisk definicji obejmuje cechy,
  ciała jednostek i karty wyboru. `ReplayRecord` **v4** (bajt `eraRecruits` tylko dla potyczki).
  Format komend `Replay` bez zmian (VARIANT 0..1). **PROTOCOL 0.8.15**.
- **Ekran**: te same dwie karty `variantBtn`; nazwa/opis z `variantCardName`/`variantCardDesc`,
  na karcie nowej jednostki złota kreska pod zmienioną rolą. Na wąskim, wysokim ekranie
  (zewnętrzny Fold) karty mogą być wyższe (do 430 dp), żeby opis 130% nie wchodził na sylwetki.
- **Teksty**: `part217()` w `L.kt` (10 języków): nazwy, stopnie `_t2/_t3`, `_fl`, karty `v_*`,
  `v_base`, `v_base_d` z `%1$s` (nazwa zastępowanej jednostki). Twarde spacje w „2 s”.
- **Sylwetki**: `Soldiers.towerShield` (owal z ciemnym rantem i pasem drużyny),
  `Soldiers.mantlet` (wysoka osłona w barwie drużyny, długa lufa), `Machines.lightCart`
  (dwa duże koła, skośna platforma, trójkątna płachta drużyny, mały łucznik), `Machines.ram`
  (niski dach z pasem drużyny, dwa koła, żelazny klin wysuwany przy ciosie).

## Strojenie (mnożniki względem zastępowanej jednostki)

| Jednostka | Start (R171) | Po strojeniu |
|---|---|---|
| Tarczownik | HP 1,10, obr. 0,85, prędk. 0,88 | HP 1,02, obr. 0,85, prędk. 0,88 |
| Lekki wóz | koszt 0,9, HP 0,8 | koszt 0,85, HP 0,85 |
| Taran | HP 1,2, obr. 1,1, prędk. 0,85, bez rozprysku | koszt 0,9, HP 2,2, obr. 1,7, prędk. 0,85, rozprysk 40, oblężenie ×2,2 |
| Strzelec z osłoną | HP 1,2, obr. 0,9, prędk. 0,9, szkolenie ×1,25 | HP 1,03, obr. 0,85, prędk. 0,9, szkolenie ×1,25 |

Przebieg: Tarczownik 46/80 → 104/200 → 224/400 → 205/400; Lekki wóz 31/80 → 108/200 →
234/400 → 216/400; Taran 7/80 (cecha 0 użyć) → 42/200 → 60/200 → 141/200 → 210/400;
Strzelec 51/80 → 106/200 → 227/400 → 220/400 → 210/400. Taran początkowo nie dochodził do bazy:
walcząc wręcz bez rozprysku uderza bazę tylko, gdy przed nim nie ma wroga — dlatego cecha działa
też, gdy rozprysk ciosu obejmuje bazę.

## Pomiar końcowy (`BalanceReport.recruitReport`, BALANCE_RUN=true)

Podstawowy (-1) przeciw nowemu (0), 200 seedów × zamiana stron, BALANCED × BALANCED, limit 30 min.

| Nowa jednostka | Wygrane nowej / podstawowej (na 400) | Paty | Mediana [min] | Użycia cechy / wystawione jednostki |
|---|---:|---:|---:|---:|
| Tarczownik | **205** / 195 | 0 | 14,04 | 20072 / 23434 (0,86 na sztukę) |
| Lekki wóz | **216** / 184 | 0 | 13,93 | 23090 / 26386 (0,88) |
| Taran | **210** / 190 | 0 | 12,77 | 130 / 1474 (0,09) |
| Strzelec z osłoną | **210** / 190 | 0 | 13,24 | 12548 / 28955 (0,43) |

Wszystkie w celu 180–220, zero patów. Uwaga: Taran rzadko trafia bazę (9% sztuk), więc jego
wartość wynika głównie ze statystyk; cecha jest widoczna, ale rzadka. Niezmierzone (R171 sugeruje
na później): inne osobowości, poziomy zasobów, kampania.

## Zrzuty sylwetek (obejrzane)

- `app/build/screenshots/gallery_recruits69.png` — duże kafle: podstawowa / nowa (gracz) / nowa (wróg) / podstawowa (wróg).
- `app/build/screenshots/gallery_recruits69_720p.png`, `gallery_recruits69_fold_outer.png` — rozmiar z pola bitwy, niska jakość, powiększenie ×4.
- `app/build/screenshots/gallery_recruits69_animation.png` — marsz, przygotowanie i atak.
- `app/build/screenshots/recruit69_{phone720,foldOuter}_{pl,de,fr,pt,tr,uk}_era{0,1,2,4}_choice.png` — okno wyboru, tekst 130% (z asercją `assert74Variants`).
- `app/build/screenshots/recruit69_{phone720,foldOuter}_era{0,1,2,4}_battle.png` — nowe jednostki obu stron na polu.

Po obejrzeniu: poszerzona osłona strzelca, większa płachta wozu, złota kreska zamiast koła
na karcie, wyższe karty na zewnętrznym Foldzie (wcześniej opis 130% nachodził na sylwetki).

## Testy

- `sim/.../EraRecruit69Test.kt`: ID i karty, tylko potyczka (także bez flagi), karta 0/1, wieża
  i dowódca podstawowe, AI wybiera obie karty, każda cecha osobno, wznowienie zrzutu w trakcie
  osłony tarczownika oraz z wozem/taranem/strzelcem, odczyt v23, odrzucenie v24, determinizm
  i weryfikacja powtórki z komendą VARIANT.
- Serwer: testy rankingu potyczki grają z `eraRecruits = true` (wybór wariantu, wznowienie zrzutu).
- Złote powtórki zregenerowane: wyniki identyczne, zmienił się tylko `snapshotSha256` (zrzut v25).
- Wzorce PNG regresji (`src/test/resources/screens`) bez zmian — potyczki w testach bez flagi.

WYNIK_PELNYCH_TESTOW

## Poza zakresem / do decyzji

- Kodeks nie pokazuje jeszcze nowych jednostek (strona wariantów zna tylko pełne warianty).
- Dymek `hint_variant54` mówi o „innych jednostkach i wieżach”; w epokach jednej roli wieża się nie zmienia.
- Kampania, Endless, wyzwania i Rozkazy — po osobnej decyzji (wystarczy ustawić `eraRecruits`).
